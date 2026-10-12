package com.example.livetranslate

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.util.Base64
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class TranslateService : Service() {

    companion object {
        const val ACTION_START = "start"
        const val ACTION_PAUSE = "pause"
        const val ACTION_RESUME = "resume"
        const val ACTION_STOP = "stop"
        const val ACTION_LOCALE_CHANGED = "locale_changed"

        private const val CHANNEL_ID = "live_translate_ctrl"
        private const val NOTIF_ID = 1
        const val MODEL = "gemini-3.5-live-translate-preview"

        @Volatile var gain = 1.0f
        @Volatile var origGain = 1.0f
        @Volatile private var hwTrackGain = 1.0f
        @Volatile private var pcmSampleGain = 1.0f
        @Volatile private var activeTrack: AudioTrack? = null

        @Volatile var running = false
        @Volatile var paused = false
        @Volatile var activeKeyIndex = 0
        @Volatile var lastStatusText = ""
        val failedKeyIndices = mutableSetOf<Int>()

        var onEvent: ((String, String) -> Unit)? = null
        var onAudioLevel: ((Float, Float) -> Unit)? = null

        private var savedMusicVol: Int = -1
        private var savedAlarmVol: Int = -1
        private var focusRequest: AudioFocusRequest? = null
        private var hasDuckFocus = false

        fun getLocalizedContext(base: Context): Context {
            val appLocales = AppCompatDelegate.getApplicationLocales()
            val tagFromDelegate = if (!appLocales.isEmpty) appLocales[0]?.language else null
            val prefs = base.getSharedPreferences("lt", Context.MODE_PRIVATE)
            val langTag = tagFromDelegate?.takeIf { it.isNotBlank() }
                ?: prefs.getString("app_lang", null)?.takeIf { it.isNotBlank() }
                ?: Locale.getDefault().language

            val locale = Locale.forLanguageTag(langTag)
            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            return base.createConfigurationContext(config)
        }

        /**
         * Calibrated 0..100 independent volume control for Translation (Dubbing) Audio:
         * Uses STREAM_ALARM (100% independent from STREAM_MUSIC / Original Video) and adjusts
         * both the STREAM_ALARM hardware step and AudioTrack/PCM gain so 0% is silent,
         * 1% is a whisper, and 0%..100% scales smoothly without affecting original video volume.
         */
        fun applyTranslationVolume(context: Context, percent: Int) {
            val p = percent.coerceIn(0, 100)
            gain = p / 100f
            if (p == 0) {
                hwTrackGain = 0f
                pcmSampleGain = 0f
            } else {
                val x = p / 100f
                hwTrackGain = x.pow(1.35f).coerceIn(0.01f, 1.0f)
                pcmSampleGain = x.pow(1.15f).coerceIn(0.01f, 1.0f)
            }
            try {
                activeTrack?.setVolume(hwTrackGain)
            } catch (_: Exception) {
            }
            if (running && !paused) {
                try {
                    val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxAlarm = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    if (savedAlarmVol < 0) {
                        savedAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
                    }
                    if (p > 0) {
                        val step = (1 + ((p / 100f).pow(1.2f) * (maxAlarm - 1))).roundToInt().coerceIn(1, maxAlarm)
                        am.setStreamVolume(AudioManager.STREAM_ALARM, step, 0)
                    }
                } catch (_: Exception) {
                }
            }
        }

        /**
         * Calibrated 0..100 perceptual volume control for Original Video / System Audio:
         * Maps 0% to complete mute, 1%..30% to minimum stream steps combined with Android
         * AudioFlinger VolumeShaper ducking (USAGE_ASSISTANCE_NAVIGATION_GUIDANCE), and
         * 31%..100% smoothly across STREAM_MUSIC steps.
         */
        fun applyOriginalVolume(context: Context, percent: Int) {
            val p = percent.coerceIn(0, 100)
            origGain = p / 100f
            try {
                val am = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (savedMusicVol < 0) {
                    savedMusicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                }

                if (p == 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
                    requestDuckingFocus(am)
                } else if (p <= 30) {
                    // Combine low stream step with Android hardware ducking (-14 dB) so 1%..30% is genuinely quiet
                    val frac = (p / 30f).pow(1.6f)
                    val lowMax = (maxMusic * 0.25f).roundToInt().coerceAtLeast(1)
                    val step = (frac * lowMax).roundToInt().coerceAtLeast(1)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
                    requestDuckingFocus(am)
                } else {
                    abandonDuckingFocus(am)
                    val norm = (p - 30f) / 70f
                    val curved = norm.pow(1.35f)
                    val minStep = 1
                    val step = (minStep + curved * (maxMusic - minStep)).roundToInt().coerceIn(1, maxMusic)
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
                }
            } catch (_: Exception) {
            }
        }

        private fun requestDuckingFocus(am: AudioManager) {
            if (hasDuckFocus) return
            try {
                // USAGE_ASSISTANCE_NAVIGATION_GUIDANCE triggers Android's built-in AudioFlinger VolumeShaper ducker
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setWillPauseWhenDucked(false)
                    .build()
                if (am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    focusRequest = req
                    hasDuckFocus = true
                }
            } catch (_: Exception) {
            }
        }

        private fun abandonDuckingFocus(am: AudioManager) {
            if (!hasDuckFocus) return
            try {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } catch (_: Exception) {
            }
            focusRequest = null
            hasDuckFocus = false
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var ws: WebSocket? = null
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var projection: MediaProjection? = null
    private var playExec: ExecutorService? = null

    @Volatile private var ready = false
    @Volatile private var captureStarted = false
    private var micMode = true
    private var srcRate = 16000
    private var targetLang = "de"

    private var apiKeys: List<String> = emptyList()
    private var currentKeyIdx = 0
    private var failedAttemptsInRow = 0

    private val setupTimeoutRunnable = Runnable {
        if (running && !paused && !ready) {
            tryFailoverOrStop("Timeout (#${currentKeyIdx + 1})")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun lctx(): Context = getLocalizedContext(this)

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAll()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseTranslation()
                return START_NOT_STICKY
            }
            ACTION_RESUME -> {
                resumeTranslation()
                return START_NOT_STICKY
            }
            ACTION_LOCALE_CHANGED -> {
                if (running) {
                    refreshLocalizedStatusAndNotification()
                }
                return START_NOT_STICKY
            }
        }

        if (intent == null || running) return START_NOT_STICKY

        val passedKeys = intent.getStringArrayListExtra("keys")?.filter { it.isNotBlank() }
        val singleKey = intent.getStringExtra("key")?.trim().orEmpty()
        apiKeys = if (!passedKeys.isNullOrEmpty()) passedKeys else if (singleKey.isNotEmpty()) listOf(singleKey) else emptyList()

        if (apiKeys.isEmpty()) {
            emitStatus(lctx().getString(R.string.err_api_key_required))
            stopSelf()
            return START_NOT_STICKY
        }

        currentKeyIdx = intent.getIntExtra("keyIndex", 0).coerceIn(0, apiKeys.lastIndex)
        activeKeyIndex = currentKeyIdx
        failedKeyIndices.clear()
        failedAttemptsInRow = 0

        targetLang = intent.getStringExtra("lang") ?: "fa"
        micMode = intent.getStringExtra("mode") != "system"
        paused = false

        startAsForeground()

        if (!micMode) {
            val code = intent.getIntExtra("code", 0)
            val data = intent.getParcelableExtra<Intent>("data")
            if (data == null) {
                emitStatus(lctx().getString(R.string.err_no_projection))
                stopAll()
                return START_NOT_STICKY
            }
            val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mpm.getMediaProjection(code, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopAll() }
            }, main)
        }

        running = true
        captureStarted = false

        setupAudioVolumes()
        playExec = Executors.newSingleThreadExecutor()
        track = buildTrack()
        activeTrack = track

        openWebSocket()
        return START_NOT_STICKY
    }

    private fun pauseTranslation() {
        if (!running || paused) return
        paused = true
        main.removeCallbacks(setupTimeoutRunnable)
        try {
            track?.pause()
            track?.flush()
        } catch (_: Exception) {
        }
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            abandonDuckingFocus(am)
            if (savedMusicVol >= 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVol, 0)
            }
        } catch (_: Exception) {
        }
        main.post { onAudioLevel?.invoke(0f, 0f) }
        val msg = lctx().getString(R.string.status_paused, targetLang)
        emitStatus(msg)
        emit("state", "paused")
    }

    private fun resumeTranslation() {
        if (!running || !paused) return
        paused = false
        try {
            track?.play()
        } catch (_: Exception) {
        }
        if (!micMode) {
            applyOriginalVolume(this, (origGain * 100f).roundToInt())
        }
        emit("state", "running")
        if (!ready || ws == null) {
            openWebSocket()
        } else {
            val c = lctx()
            val msg = if (apiKeys.size > 1) {
                c.getString(R.string.status_live_key, targetLang, currentKeyIdx + 1)
            } else {
                c.getString(R.string.status_live, targetLang)
            }
            emitStatus(msg)
        }
    }

    private fun refreshLocalizedStatusAndNotification() {
        val c = lctx()
        val msg = when {
            paused -> c.getString(R.string.status_paused, targetLang)
            ready -> if (apiKeys.size > 1) {
                c.getString(R.string.status_live_key, targetLang, currentKeyIdx + 1)
            } else {
                c.getString(R.string.status_live, targetLang)
            }
            else -> if (apiKeys.size > 1) {
                c.getString(R.string.status_connecting_key, currentKeyIdx + 1, apiKeys.size)
            } else {
                c.getString(R.string.status_connecting)
            }
        }
        emitStatus(msg)
    }

    private fun setupAudioVolumes() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (savedMusicVol < 0) {
                savedMusicVol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            if (savedAlarmVol < 0) {
                savedAlarmVol = am.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            applyTranslationVolume(this, (gain * 100f).roundToInt())
            if (!micMode) {
                applyOriginalVolume(this, (origGain * 100f).roundToInt())
            }
        } catch (_: Exception) {
        }
    }

    private fun restoreAudioVolumes() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            abandonDuckingFocus(am)
            if (savedMusicVol >= 0) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, savedMusicVol, 0)
                savedMusicVol = -1
            }
            if (savedAlarmVol >= 0) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, savedAlarmVol, 0)
                savedAlarmVol = -1
            }
        } catch (_: Exception) {
        }
    }

    private fun ensureNotificationChannel(c: Context) {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            c.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(statusText: String): Notification {
        val c = lctx()
        ensureNotificationChannel(c)

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            this,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, TranslateService::class.java).setAction(ACTION_PAUSE)
        val pausePi = PendingIntent.getService(
            this,
            101,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(this, TranslateService::class.java).setAction(ACTION_RESUME)
        val resumePi = PendingIntent.getService(
            this,
            102,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, TranslateService::class.java).setAction(ACTION_STOP)
        val stopPi = PendingIntent.getService(
            this,
            103,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (paused) {
            c.getString(R.string.notif_title_paused)
        } else {
            c.getString(R.string.notif_title_running)
        }
        val subtitle = c.getString(R.string.notif_text_tap, statusText.ifEmpty { c.getString(R.string.running) })

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic_3d)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setContentIntent(contentPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(R.drawable.ic_pause_3d, c.getString(R.string.pause), pausePi)
            .addAction(R.drawable.ic_play_3d, c.getString(R.string.resume), resumePi)
            .addAction(R.drawable.ic_stop_3d, c.getString(R.string.stop), stopPi)
            .build()
    }

    private fun startAsForeground() {
        val c = lctx()
        val initialStatus = c.getString(R.string.status_connecting)
        lastStatusText = initialStatus
        val n = buildNotification(initialStatus)
        val type = if (micMode) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        startForeground(NOTIF_ID, n, type)
    }

    private fun updateNotification(statusText: String) {
        if (!running) return
        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIF_ID, buildNotification(statusText))
        } catch (_: Exception) {
        }
    }

    private fun openWebSocket() {
        if (!running || apiKeys.isEmpty()) return
        ready = false
        val key = apiKeys[currentKeyIdx]
        activeKeyIndex = currentKeyIdx
        emit("key_active", currentKeyIdx.toString())

        val c = lctx()
        val connectingMsg = if (apiKeys.size > 1) {
            c.getString(R.string.status_connecting_key, currentKeyIdx + 1, apiKeys.size)
        } else {
            c.getString(R.string.status_connecting)
        }
        emitStatus(connectingMsg)

        main.removeCallbacks(setupTimeoutRunnable)
        main.postDelayed(setupTimeoutRunnable, 9000L)

        val url = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=$key"

        val socket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (webSocket !== ws || !running) return
                val setup = """{"setup":{"model":"models/$MODEL","generationConfig":{"responseModalities":["AUDIO"],"translationConfig":{"targetLanguageCode":"$targetLang","echoTargetLanguage":false}},"inputAudioTranscription":{},"outputAudioTranscription":{}}}"""
                webSocket.send(setup)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (webSocket === ws) handle(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                if (webSocket === ws) handle(bytes.utf8())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (webSocket !== ws || !running) return
                if (paused) return
                val codeInfo = response?.code?.let { "HTTP $it: " } ?: ""
                main.post { tryFailoverOrStop("$codeInfo${t.message ?: "Connection failed"}") }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (webSocket !== ws || !running) return
                if (paused) return
                if (code != 1000) {
                    main.post { tryFailoverOrStop("($code) $reason") }
                } else {
                    main.post { stopAll() }
                }
            }
        })
        ws = socket
    }

    private fun tryFailoverOrStop(errorReason: String) {
        if (!running || paused) return
        main.removeCallbacks(setupTimeoutRunnable)
        ready = false
        failedKeyIndices.add(currentKeyIdx)
        emit("key_failed", currentKeyIdx.toString())

        try { ws?.cancel() } catch (_: Exception) {}
        ws = null

        val c = lctx()
        failedAttemptsInRow++
        if (apiKeys.size > 1 && failedAttemptsInRow < apiKeys.size) {
            currentKeyIdx = (currentKeyIdx + 1) % apiKeys.size
            activeKeyIndex = currentKeyIdx
            emit("key_switch", currentKeyIdx.toString())
            emitStatus(c.getString(R.string.status_switching_key, currentKeyIdx + 1))
            main.postDelayed({
                if (running && !paused) openWebSocket()
            }, 400L)
        } else {
            emitStatus(c.getString(R.string.status_error, errorReason))
            stopAll()
        }
    }

    private fun handle(text: String) {
        try {
            val m = JSONObject(text)
            if (m.has("error")) {
                val errObj = m.optJSONObject("error")
                val errMsg = errObj?.optString("message") ?: "API Error"
                main.post { tryFailoverOrStop(errMsg) }
                return
            }
            if (m.has("setupComplete")) {
                main.removeCallbacks(setupTimeoutRunnable)
                failedAttemptsInRow = 0
                failedKeyIndices.remove(currentKeyIdx)
                ready = true
                emit("key_active", currentKeyIdx.toString())
                val c = lctx()
                val liveMsg = if (paused) {
                    c.getString(R.string.status_paused, targetLang)
                } else if (apiKeys.size > 1) {
                    c.getString(R.string.status_live_key, targetLang, currentKeyIdx + 1)
                } else {
                    c.getString(R.string.status_live, targetLang)
                }
                emitStatus(liveMsg)
                if (!captureStarted) {
                    captureStarted = true
                    startCapture()
                }
                return
            }
            if (paused) return
            val c = m.optJSONObject("serverContent") ?: return
            c.optJSONObject("inputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let { emit("in", it) }
            c.optJSONObject("outputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let { emit("out", it) }
            val parts = c.optJSONObject("modelTurn")?.optJSONArray("parts") ?: return
            for (i in 0 until parts.length()) {
                val d = parts.getJSONObject(i).optJSONObject("inlineData")?.optString("data")
                if (d.isNullOrEmpty()) continue
                val pcm = Base64.decode(d, Base64.DEFAULT)
                playExec?.execute { if (!paused) play(pcm) }
            }
        } catch (_: Exception) {
        }
    }

    private fun buildTrack(): AudioTrack {
        val rate = 24000
        val min = AudioTrack.getMinBufferSize(
            rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        // Use USAGE_ALARM (STREAM_ALARM) so lowering STREAM_MUSIC (Original Video Volume) even to 0%
        // never lowers or mutes our translated dubbing audio!
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        routeToHeadphonesIfConnected(t)
        t.setVolume(hwTrackGain)
        t.play()
        return t
    }

    private fun routeToHeadphonesIfConnected(audioTrack: AudioTrack) {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val headset = outputs.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (headset != null) {
                audioTrack.setPreferredDevice(headset)
            }
        } catch (_: Exception) {
        }
    }

    private fun play(pcm: ByteArray) {
        if (paused) return
        val swG = pcmSampleGain
        if (swG <= 0f) return

        var sumSq = 0.0
        var count = 0
        var i = 0
        while (i + 1 < pcm.size) {
            val s = ((pcm[i + 1].toInt() shl 8) or (pcm[i].toInt() and 0xFF)).toShort().toInt()
            val v = (s * swG).toInt().coerceIn(-32768, 32767)
            pcm[i] = (v and 0xFF).toByte()
            pcm[i + 1] = ((v shr 8) and 0xFF).toByte()
            val norm = v / 32768.0
            sumSq += norm * norm
            count++
            i += 2
        }
        if (count > 0 && !paused) {
            val rms = (sqrt(sumSq / count) * 3.8).toFloat().coerceIn(0f, 1f)
            main.post { if (!paused) onAudioLevel?.invoke(0f, rms) }
        }
        track?.write(pcm, 0, pcm.size)
    }

    @SuppressLint("MissingPermission")
    private fun createRecord(): AudioRecord? {
        for (rate in intArrayOf(16000, 48000)) {
            val ratio = rate / 16000
            val min = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val size = maxOf(min, 3200 * ratio * 4)
            val fmt = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build()
            try {
                val rec = if (micMode) {
                    AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                        .setAudioFormat(fmt)
                        .setBufferSizeInBytes(size)
                        .build()
                } else {
                    val cfg = AudioPlaybackCaptureConfiguration.Builder(projection!!)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .excludeUid(Process.myUid())
                        .build()
                    AudioRecord.Builder()
                        .setAudioFormat(fmt)
                        .setBufferSizeInBytes(size)
                        .setAudioPlaybackCaptureConfig(cfg)
                        .build()
                }
                if (rec.state == AudioRecord.STATE_INITIALIZED) { srcRate = rate; return rec }
                rec.release()
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun startCapture() {
        val rec = createRecord()
        if (rec == null) {
            emitStatus(lctx().getString(R.string.err_audio_record))
            stopAll()
            return
        }
        record = rec
        rec.startRecording()
        val ratio = srcRate / 16000

        Thread {
            val raw = ByteArray(3200 * ratio)
            while (running) {
                var off = 0
                while (off < raw.size && running) {
                    val n = rec.read(raw, off, raw.size - off)
                    if (n < 0) {
                        if (running) {
                            emitStatus(lctx().getString(R.string.err_record_read, n))
                            stopAll()
                        }
                        return@Thread
                    }
                    off += n
                }
                if (!running) break
                if (paused) continue

                val out = if (ratio == 1) raw.copyOf() else decimate(raw, ratio)
                val micScale = if (micMode) origGain.pow(2.2f) else 1.0f
                var sumSq = 0.0
                var count = 0
                var idx = 0
                while (idx + 1 < out.size) {
                    val s = ((out[idx + 1].toInt() shl 8) or (out[idx].toInt() and 0xFF)).toShort().toInt()
                    val v = if (micScale != 1.0f) (s * micScale).toInt().coerceIn(-32768, 32767) else s
                    if (micScale != 1.0f) {
                        out[idx] = (v and 0xFF).toByte()
                        out[idx + 1] = ((v shr 8) and 0xFF).toByte()
                    }
                    val norm = v / 32768.0
                    sumSq += norm * norm
                    count++
                    idx += 2
                }
                if (count > 0 && !paused) {
                    val rms = (sqrt(sumSq / count) * 4.2).toFloat().coerceIn(0f, 1f)
                    main.post { if (!paused) onAudioLevel?.invoke(rms, 0f) }
                }
                if (ready && !paused) {
                    val b64 = Base64.encodeToString(out, Base64.NO_WRAP)
                    ws?.send("""{"realtimeInput":{"audio":{"data":"$b64","mimeType":"audio/pcm;rate=16000"}}}""")
                }
            }
        }.start()
    }

    private fun decimate(src: ByteArray, r: Int): ByteArray {
        val n = src.size / 2 / r
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            var sum = 0
            for (k in 0 until r) {
                val idx = (i * r + k) * 2
                sum += ((src[idx + 1].toInt() shl 8) or (src[idx].toInt() and 0xFF)).toShort().toInt()
            }
            val v = sum / r
            out[2 * i] = (v and 0xFF).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun emitStatus(text: String) {
        lastStatusText = text
        updateNotification(text)
        emit("status", text)
    }

    private fun emit(type: String, text: String) {
        main.post { onEvent?.invoke(type, text) }
    }

    private fun stopAll() {
        main.removeCallbacks(setupTimeoutRunnable)
        if (running) {
            running = false
            paused = false
            ready = false
            captureStarted = false
            activeTrack = null
            try { ws?.close(1000, "bye") } catch (_: Exception) {}
            ws = null
            try { record?.stop() } catch (_: Exception) {}
            try { record?.release() } catch (_: Exception) {}
            record = null
            try { track?.stop() } catch (_: Exception) {}
            try { track?.release() } catch (_: Exception) {}
            track = null
            try { projection?.stop() } catch (_: Exception) {}
            projection = null
            playExec?.shutdownNow()
            restoreAudioVolumes()
            val stoppedMsg = lctx().getString(R.string.stopped)
            lastStatusText = stoppedMsg
            emit("status", stoppedMsg)
            emit("state", "stopped")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }
}
