package com.example.livetranslate

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Filter
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import org.json.JSONArray

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var etKey: EditText
    private lateinit var etLang: MaterialAutoCompleteTextView
    private lateinit var rbMic: RadioButton
    private lateinit var rbSystem: RadioButton
    private lateinit var cardModeMic: LinearLayout
    private lateinit var cardModeSystem: LinearLayout
    private lateinit var btnStart: MaterialButton
    private lateinit var btnPause: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var tvIn: TextView
    private lateinit var tvOut: TextView
    private lateinit var orbView: OrbVisualizerView
    private lateinit var llApiKeys: LinearLayout
    private lateinit var tvKeysCount: TextView
    private lateinit var tvNoKeysHint: TextView

    private val apiKeysList = mutableListOf<String>()
    private var selectedKeyIndex = 0
    private var selectedLangCode = "fa"

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            if (res[Manifest.permission.RECORD_AUDIO] == true ||
                hasPerm(Manifest.permission.RECORD_AUDIO)
            ) proceed() else tvStatus.text = getString(R.string.err_perm_denied)
        }

    private val projLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) launchService("system", r.resultCode, data)
            else tvStatus.text = getString(R.string.err_capture_denied)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = getSharedPreferences("lt", Context.MODE_PRIVATE)
        val isNight = prefs.getBoolean("night_mode", true)
        if (!prefs.contains("night_mode")) {
            prefs.edit().putBoolean("night_mode", true).apply()
        }
        AppCompatDelegate.setDefaultNightMode(
            if (isNight) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        volumeControlStream = AudioManager.STREAM_MUSIC

        val dp = resources.displayMetrics.density
        val contentContainer = findViewById<View>(R.id.contentContainer)
        ViewCompat.setOnApplyWindowInsetsListener(contentContainer) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                (16 * dp).toInt() + insets.left,
                (14 * dp).toInt() + insets.top,
                (16 * dp).toInt() + insets.right,
                (28 * dp).toInt() + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        etKey = findViewById(R.id.etKey)
        etLang = findViewById(R.id.etLang)
        rbMic = findViewById(R.id.rbMic)
        rbSystem = findViewById(R.id.rbSystem)
        cardModeMic = findViewById(R.id.cardModeMic)
        cardModeSystem = findViewById(R.id.cardModeSystem)
        btnStart = findViewById(R.id.btnStart)
        btnPause = findViewById(R.id.btnPause)
        btnStop = findViewById(R.id.btnStop)
        tvStatus = findViewById(R.id.tvStatus)
        tvIn = findViewById(R.id.tvIn)
        tvOut = findViewById(R.id.tvOut)
        orbView = findViewById(R.id.orbView)
        llApiKeys = findViewById(R.id.llApiKeys)
        tvKeysCount = findViewById(R.id.tvKeysCount)
        tvNoKeysHint = findViewById(R.id.tvNoKeysHint)

        val sbOrig = findViewById<SeekBar>(R.id.sbOrig)
        val sbGain = findViewById<SeekBar>(R.id.sbGain)
        val tvOrigVol = findViewById<TextView>(R.id.tvOrigVol)
        val tvTrVol = findViewById<TextView>(R.id.tvTrVol)

        // 1. Setup Target Language Dropdown (Persian, English, German first, then A-Z)
        setupLanguageDropdown()

        // 2. Load and Render Multi-API Keys List
        loadSavedApiKeys()
        renderApiKeysList()

        findViewById<Button>(R.id.btnAddKey).setOnClickListener {
            addKeysFromInput()
            animate3DPop(it)
        }

        // 3. Setup Volume Sliders (Original Video Volume + Translation Volume: 0% to 100%)
        val initialOrig = prefs.getInt("orig", 100).coerceIn(0, 100)
        val initialGain = prefs.getInt("gain", 100).coerceIn(0, 100)
        sbOrig.progress = initialOrig
        sbGain.progress = initialGain
        TranslateService.origGain = initialOrig / 100f
        TranslateService.applyTranslationVolume(this@MainActivity, initialGain)
        tvOrigVol.text = getString(R.string.orig_vol) + " — $initialOrig%"
        tvTrVol.text = getString(R.string.tr_vol) + " — $initialGain%"

        sbOrig.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val clamped = p.coerceIn(0, 100)
                prefs.edit().putInt("orig", clamped).apply()
                tvOrigVol.text = getString(R.string.orig_vol) + " — $clamped%"
                if (fromUser || TranslateService.running) {
                    TranslateService.applyOriginalVolume(this@MainActivity, clamped)
                } else {
                    TranslateService.origGain = clamped / 100f
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        sbGain.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) {
                val clamped = p.coerceIn(0, 100)
                prefs.edit().putInt("gain", clamped).apply()
                tvTrVol.text = getString(R.string.tr_vol) + " — $clamped%"
                TranslateService.applyTranslationVolume(this@MainActivity, clamped)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // 4. 3D Mode Selection Cards
        val savedSystemMode = prefs.getBoolean("system_mode", false)
        selectMode(isSystem = savedSystemMode, animate = false)

        cardModeMic.setOnClickListener {
            prefs.edit().putBoolean("system_mode", false).apply()
            selectMode(isSystem = false, animate = true)
        }
        cardModeSystem.setOnClickListener {
            prefs.edit().putBoolean("system_mode", true).apply()
            selectMode(isSystem = true, animate = true)
        }

        attach3DButtonSpring(btnStart)
        attach3DButtonSpring(btnPause)
        attach3DButtonSpring(btnStop)

        // Copy & Clear Transcript Actions
        findViewById<ImageButton>(R.id.btnCopyOut).setOnClickListener {
            copyText(tvOut.text.toString())
            animate3DPop(it)
        }
        findViewById<ImageButton>(R.id.btnCopyIn).setOnClickListener {
            copyText(tvIn.text.toString())
            animate3DPop(it)
        }
        findViewById<ImageButton>(R.id.btnClearAll).setOnClickListener {
            tvIn.text = ""
            tvOut.text = ""
            animate3DPop(it)
        }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            animate3DPop(it)
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings)
                .setItems(arrayOf(
                    getString(R.string.language),
                    getString(R.string.theme)
                )) { _, which ->
                    when (which) {
                        0 -> chooseLanguage()
                        1 -> chooseTheme()
                    }
                }.show()
        }

        btnStart.setOnClickListener {
            if (etKey.text.toString().trim().isNotEmpty()) {
                addKeysFromInput()
            }
            if (apiKeysList.isEmpty()) {
                tvStatus.text = getString(R.string.err_api_key_required)
                animate3DShake(findViewById(R.id.cardEngineConfig))
                return@setOnClickListener
            }
            prefs.edit().putString("lang", selectedLangCode).apply()
            tvIn.text = ""
            tvOut.text = ""
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
            permLauncher.launch(perms.toTypedArray())
        }

        btnPause.setOnClickListener {
            if (!TranslateService.running) return@setOnClickListener
            val action = if (TranslateService.paused) {
                TranslateService.ACTION_RESUME
            } else {
                TranslateService.ACTION_PAUSE
            }
            startService(Intent(this, TranslateService::class.java).setAction(action))
        }

        btnStop.setOnClickListener {
            startService(Intent(this, TranslateService::class.java).setAction(TranslateService.ACTION_STOP))
        }
    }

    private fun currentUiLang(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        if (!locales.isEmpty) {
            locales[0]?.language?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return prefs.getString("app_lang", null)?.takeIf { it.isNotBlank() }
            ?: resources.configuration.locales[0].language
    }

    private fun setupLanguageDropdown() {
        val languages = SupportedLanguages.all
        val uiLang = currentUiLang()
        selectedLangCode = prefs.getString("lang", "fa") ?: "fa"
        val currentItem = SupportedLanguages.findByCode(selectedLangCode)
        selectedLangCode = currentItem.code

        val adapter = object : ArrayAdapter<LanguageItem>(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            languages
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getView(position, convertView, parent)
                (v as? TextView)?.text = languages[position].getLocalizedLabel(uiLang)
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = super.getDropDownView(position, convertView, parent)
                (v as? TextView)?.text = languages[position].getLocalizedLabel(uiLang)
                return v
            }

            private val noFilter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    return FilterResults().apply {
                        values = languages
                        count = languages.size
                    }
                }
                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
            override fun getFilter(): Filter = noFilter
        }

        etLang.setAdapter(adapter)
        etLang.setText(currentItem.getLocalizedLabel(uiLang), false)

        etLang.setOnClickListener {
            etLang.showDropDown()
        }
        etLang.setOnItemClickListener { _, _, position, _ ->
            val item = languages[position]
            selectedLangCode = item.code
            etLang.setText(item.getLocalizedLabel(uiLang), false)
            prefs.edit().putString("lang", selectedLangCode).apply()
        }
    }

    private fun loadSavedApiKeys() {
        apiKeysList.clear()
        val jsonStr = prefs.getString("api_keys_json", null)
        if (!jsonStr.isNullOrEmpty()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val k = arr.optString(i).trim()
                    if (k.isNotEmpty() && !apiKeysList.contains(k)) {
                        apiKeysList.add(k)
                    }
                }
            } catch (_: Exception) {
            }
        }
        val legacyKey = prefs.getString("key", "")?.trim().orEmpty()
        if (legacyKey.isNotEmpty() && !apiKeysList.contains(legacyKey)) {
            apiKeysList.add(legacyKey)
            saveApiKeys()
        }
        selectedKeyIndex = prefs.getInt("selected_key_index", 0)
            .coerceIn(0, (apiKeysList.size - 1).coerceAtLeast(0))
    }

    private fun saveApiKeys() {
        val arr = JSONArray()
        apiKeysList.forEach { arr.put(it) }
        prefs.edit()
            .putString("api_keys_json", arr.toString())
            .putString("key", apiKeysList.getOrNull(selectedKeyIndex) ?: "")
            .putInt("selected_key_index", selectedKeyIndex)
            .apply()
    }

    private fun addKeysFromInput() {
        val raw = etKey.text.toString().trim()
        if (raw.isEmpty()) return
        val parts = raw.split('\n', ',', ';', ' ').map { it.trim() }.filter { it.length >= 10 }
        val toAdd = if (parts.isNotEmpty()) parts else listOf(raw)
        var added = false
        for (k in toAdd) {
            if (k.isNotEmpty() && !apiKeysList.contains(k)) {
                apiKeysList.add(k)
                added = true
            }
        }
        etKey.setText("")
        if (added) {
            if (apiKeysList.size == 1) selectedKeyIndex = 0
            saveApiKeys()
            renderApiKeysList()
        }
    }

    @SuppressLint("SetTextI18n")
    private fun renderApiKeysList() {
        llApiKeys.removeAllViews()
        tvKeysCount.text = apiKeysList.size.toString()
        tvNoKeysHint.visibility = if (apiKeysList.isEmpty()) View.VISIBLE else View.GONE

        if (selectedKeyIndex >= apiKeysList.size) {
            selectedKeyIndex = (apiKeysList.size - 1).coerceAtLeast(0)
        }

        val dp = resources.displayMetrics.density
        for (i in apiKeysList.indices) {
            val key = apiKeysList[i]
            val isSelected = (i == selectedKeyIndex)
            val isFailed = TranslateService.failedKeyIndices.contains(i)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * dp).toInt(), (9 * dp).toInt(), (10 * dp).toInt(), (9 * dp).toInt())
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.topMargin = (6 * dp).toInt()
                layoutParams = lp
                setBackgroundResource(
                    if (isSelected) R.drawable.bg_mode_selected_3d else R.drawable.bg_mode_unselected_3d
                )
                elevation = if (isSelected) 6 * dp else 2 * dp
                setOnClickListener {
                    selectedKeyIndex = i
                    TranslateService.activeKeyIndex = i
                    saveApiKeys()
                    renderApiKeysList()
                }
            }

            val masked = maskApiKey(key)
            val tvKeyLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = "#${i + 1}  •  $masked"
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
                textSize = 13f
                setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            }

            val badgeBg = GradientDrawable().apply {
                cornerRadius = 50 * dp
                setColor(
                    when {
                        isFailed -> Color.parseColor("#33EF4444")
                        isSelected -> Color.parseColor("#3310B981")
                        else -> Color.parseColor("#2264748B")
                    }
                )
                setStroke(
                    (1 * dp).toInt(),
                    when {
                        isFailed -> Color.parseColor("#EF4444")
                        isSelected -> Color.parseColor("#10B981")
                        else -> Color.parseColor("#64748B")
                    }
                )
            }
            val tvBadge = TextView(this).apply {
                background = badgeBg
                setPadding((8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt())
                text = when {
                    isFailed -> getString(R.string.key_failed)
                    isSelected -> getString(R.string.key_active)
                    else -> getString(R.string.key_standby)
                }
                setTextColor(
                    when {
                        isFailed -> Color.parseColor("#FB7185")
                        isSelected -> Color.parseColor("#34D399")
                        else -> ContextCompat.getColor(this@MainActivity, R.color.text_secondary)
                    }
                )
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
            }

            val btnRemove = ImageButton(this).apply {
                val size = (28 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginStart = (6 * dp).toInt()
                }
                setBackgroundResource(android.R.color.transparent)
                setImageResource(R.drawable.ic_clear_3d)
                setColorFilter(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
                setPadding((5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt(), (5 * dp).toInt())
                setOnClickListener {
                    apiKeysList.removeAt(i)
                    TranslateService.failedKeyIndices.clear()
                    if (selectedKeyIndex >= apiKeysList.size) {
                        selectedKeyIndex = (apiKeysList.size - 1).coerceAtLeast(0)
                    }
                    saveApiKeys()
                    renderApiKeysList()
                }
            }

            row.addView(tvKeyLabel)
            row.addView(tvBadge)
            row.addView(btnRemove)
            llApiKeys.addView(row)
        }
    }

    private fun maskApiKey(key: String): String {
        val clean = key.trim()
        return if (clean.length <= 10) {
            clean.take(3) + "••••"
        } else {
            clean.take(6) + "••••" + clean.takeLast(4)
        }
    }

    override fun onStart() {
        super.onStart()
        TranslateService.onEvent = { type, text ->
            when (type) {
                "status" -> {
                    tvStatus.text = text
                    syncRunningState()
                }
                "state" -> {
                    syncRunningState()
                }
                "in" -> tvIn.append(text)
                "out" -> tvOut.append(text)
                "key_active" -> {
                    val idx = text.toIntOrNull() ?: 0
                    selectedKeyIndex = idx
                    saveApiKeys()
                    renderApiKeysList()
                }
                "key_failed" -> {
                    renderApiKeysList()
                }
                "key_switch" -> {
                    val idx = text.toIntOrNull() ?: 0
                    selectedKeyIndex = idx
                    saveApiKeys()
                    renderApiKeysList()
                    Toast.makeText(
                        this,
                        getString(R.string.key_switched, idx + 1),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        TranslateService.onAudioLevel = { inRms, outRms ->
            orbView.updateAudioLevels(inRms, outRms)
        }
        if (TranslateService.running) {
            selectedKeyIndex = TranslateService.activeKeyIndex.coerceIn(0, (apiKeysList.size - 1).coerceAtLeast(0))
            if (TranslateService.lastStatusText.isNotEmpty()) {
                tvStatus.text = TranslateService.lastStatusText
            }
            // Refresh service status/notification in case UI locale just changed
            startService(
                Intent(this, TranslateService::class.java)
                    .setAction(TranslateService.ACTION_LOCALE_CHANGED)
            )
        } else {
            tvStatus.text = getString(R.string.ready)
        }
        renderApiKeysList()
        syncRunningState()
    }

    override fun onStop() {
        super.onStop()
        TranslateService.onEvent = null
        TranslateService.onAudioLevel = null
    }

    private fun syncRunningState() {
        val active = TranslateService.running
        val isPaused = TranslateService.paused

        btnStart.isEnabled = !active
        btnPause.isEnabled = active
        btnStop.isEnabled = active

        btnStart.alpha = if (active) 0.45f else 1.0f
        btnPause.alpha = if (active) 1.0f else 0.45f
        btnStop.alpha = if (active) 1.0f else 0.45f

        if (isPaused) {
            btnPause.text = getString(R.string.resume)
            btnPause.setIconResource(R.drawable.ic_play_3d)
        } else {
            btnPause.text = getString(R.string.pause)
            btnPause.setIconResource(R.drawable.ic_pause_3d)
        }

        orbView.isActive = active && !isPaused
    }

    private fun selectMode(isSystem: Boolean, animate: Boolean) {
        rbMic.isChecked = !isSystem
        rbSystem.isChecked = isSystem

        cardModeMic.setBackgroundResource(
            if (!isSystem) R.drawable.bg_mode_selected_3d else R.drawable.bg_mode_unselected_3d
        )
        cardModeSystem.setBackgroundResource(
            if (isSystem) R.drawable.bg_mode_selected_3d else R.drawable.bg_mode_unselected_3d
        )

        val activeCard = if (isSystem) cardModeSystem else cardModeMic
        val inactiveCard = if (isSystem) cardModeMic else cardModeSystem

        if (animate) {
            activeCard.animate()
                .scaleX(1.03f)
                .scaleY(1.03f)
                .translationY(-4f)
                .translationZ(12f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(2.0f))
                .start()
            inactiveCard.animate()
                .scaleX(0.98f)
                .scaleY(0.98f)
                .translationY(0f)
                .translationZ(2f)
                .setDuration(200)
                .start()
        } else {
            activeCard.scaleX = 1.02f
            activeCard.scaleY = 1.02f
            inactiveCard.scaleX = 0.98f
            inactiveCard.scaleY = 0.98f
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach3DTilt(view: View?, maxTiltDeg: Float) {
        if (view == null) return
        view.cameraDistance = 9000f * resources.displayMetrics.density
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    if (v.width > 0 && v.height > 0) {
                        val nx = ((event.x / v.width) - 0.5f) * 2f
                        val ny = ((event.y / v.height) - 0.5f) * 2f
                        v.rotationY = (nx * maxTiltDeg).coerceIn(-maxTiltDeg, maxTiltDeg)
                        v.rotationX = (-ny * maxTiltDeg).coerceIn(-maxTiltDeg, maxTiltDeg)
                        v.translationZ = 8f
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .rotationX(0f)
                        .rotationY(0f)
                        .translationZ(0f)
                        .setDuration(280)
                        .setInterpolator(OvershootInterpolator(1.6f))
                        .start()
                }
            }
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attach3DButtonSpring(button: View) {
        button.cameraDistance = 8000f * resources.displayMetrics.density
        button.setOnTouchListener { v, event ->
            if (!v.isEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .rotationX(6f)
                        .translationY(3f)
                        .setDuration(90)
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .rotationX(0f)
                        .translationY(0f)
                        .setDuration(240)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .start()
                }
            }
            false
        }
    }

    private fun animate3DPop(view: View) {
        view.scaleX = 0.86f
        view.scaleY = 0.86f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(2.5f))
            .start()
    }

    private fun animate3DShake(view: View?) {
        if (view == null) return
        view.animate()
            .rotationY(7f)
            .setDuration(70)
            .withEndAction {
                view.animate()
                    .rotationY(-7f)
                    .setDuration(70)
                    .withEndAction {
                        view.animate().rotationY(0f).setDuration(100).start()
                    }.start()
            }.start()
    }

    private fun copyText(text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("LiveTranslate", text))
        Toast.makeText(this, getString(R.string.copied), Toast.LENGTH_SHORT).show()
    }

    private fun chooseLanguage() {
        val items = arrayOf("Deutsch", "English", "فارسی")
        val codes = arrayOf("de", "en", "fa")
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.language)
            .setItems(items) { _, i ->
                val chosen = codes[i]
                prefs.edit().putString("app_lang", chosen).apply()
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(chosen))
                if (TranslateService.running) {
                    startService(
                        Intent(this, TranslateService::class.java)
                            .setAction(TranslateService.ACTION_LOCALE_CHANGED)
                    )
                }
            }.show()
    }

    private fun chooseTheme() {
        val items = arrayOf(getString(R.string.light), getString(R.string.dark))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.theme)
            .setItems(items) { _, i ->
                val isDark = (i == 1)
                prefs.edit().putBoolean("night_mode", isDark).apply()
                AppCompatDelegate.setDefaultNightMode(
                    if (!isDark) AppCompatDelegate.MODE_NIGHT_NO
                    else AppCompatDelegate.MODE_NIGHT_YES
                )
            }.show()
    }

    private fun hasPerm(p: String) =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun proceed() {
        if (rbSystem.isChecked) {
            val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projLauncher.launch(mpm.createScreenCaptureIntent())
        } else launchService("mic", 0, null)
    }

    private fun launchService(mode: String, code: Int, data: Intent?) {
        val i = Intent(this, TranslateService::class.java)
            .setAction(TranslateService.ACTION_START)
            .putStringArrayListExtra("keys", ArrayList(apiKeysList))
            .putExtra("keyIndex", selectedKeyIndex)
            .putExtra("key", apiKeysList.getOrNull(selectedKeyIndex) ?: "")
            .putExtra("lang", selectedLangCode)
            .putExtra("mode", mode)
            .putExtra("code", code)
        if (data != null) i.putExtra("data", data)
        ContextCompat.startForegroundService(this, i)
        btnStart.isEnabled = false
        btnPause.isEnabled = true
        btnStop.isEnabled = true
        orbView.isActive = true
    }
}
