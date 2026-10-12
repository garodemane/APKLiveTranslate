package com.example.livetranslate

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class OrbVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private data class Point3D(
        val bx: Float,
        val by: Float,
        val bz: Float,
        val ringId: Int = 0 // 0 = sphere node, 1 = ring A, 2 = ring B
    )

    private data class ProjectedPoint(
        var x: Float = 0f,
        var y: Float = 0f,
        var z: Float = 0f,
        var scale: Float = 1f,
        var ringId: Int = 0
    )

    private val points = ArrayList<Point3D>(240)
    private val projected = ArrayList<ProjectedPoint>(240)

    private val corePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val auraPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 2.2f
        style = Paint.Style.STROKE
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
    }

    private val shadowRect = RectF()
    private val arcRect = RectF()

    private var rotY = 0.4f
    private var rotX = -0.25f
    private var velY = 0.014f
    private var velX = 0.003f
    private var phase = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    @Volatile var isActive: Boolean = false
    @Volatile private var targetInLevel: Float = 0f
    @Volatile private var targetOutLevel: Float = 0f
    private var smoothInLevel: Float = 0f
    private var smoothOutLevel: Float = 0f

    init {
        val sphereCount = 150
        val goldenAngle = PI.toFloat() * (3f - sqrt(5f))
        for (i in 0 until sphereCount) {
            val y = 1f - (i.toFloat() / (sphereCount - 1)) * 2f
            val radius = sqrt(max(0f, 1f - y * y))
            val theta = goldenAngle * i
            val x = cos(theta) * radius
            val z = sin(theta) * radius
            points.add(Point3D(x, y, z, 0))
            projected.add(ProjectedPoint())
        }

        val ringCount = 44
        for (i in 0 until ringCount) {
            val angle = (2.0 * PI * i / ringCount).toFloat()
            val rx = cos(angle) * 1.34f
            val rz = sin(angle) * 1.34f
            // Tilted orbital ring 1
            points.add(Point3D(rx, rz * 0.38f, rz * 0.92f, 1))
            projected.add(ProjectedPoint())
            // Tilted orbital ring 2
            points.add(Point3D(rx * 0.92f, rx * -0.42f, rz, 2))
            projected.add(ProjectedPoint())
        }
    }

    fun updateAudioLevels(inputRms: Float, outputRms: Float) {
        targetInLevel = inputRms.coerceIn(0f, 1f)
        targetOutLevel = outputRms.coerceIn(0f, 1f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                velY = (dx * 0.008f).coerceIn(-0.12f, 0.12f)
                velX = (dy * 0.008f).coerceIn(-0.12f, 0.12f)
                rotY += velY
                rotX = (rotX + velX).coerceIn(-1.2f, 1.2f)
                lastTouchX = event.x
                lastTouchY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = w * 0.5f
        val cy = h * 0.46f
        val baseRadius = min(w, h) * 0.30f

        // Smooth audio reactivity
        smoothInLevel += (targetInLevel - smoothInLevel) * 0.22f
        smoothOutLevel += (targetOutLevel - smoothOutLevel) * 0.22f
        targetInLevel *= 0.88f
        targetOutLevel *= 0.88f

        val totalEnergy = if (isActive) {
            max(0.12f, (smoothInLevel * 0.9f + smoothOutLevel * 1.1f).coerceAtMost(1f))
        } else {
            0.04f * (0.5f + 0.5f * sin(phase * 0.8f))
        }

        // Advance 3D physics & rotation
        phase += if (isActive) 0.07f + totalEnergy * 0.14f else 0.03f
        if (!isDragging) {
            val baseSpeedY = if (isActive) 0.022f + totalEnergy * 0.03f else 0.011f
            velY += (baseSpeedY - velY) * 0.06f
            velX *= 0.94f
            rotY += velY
            rotX += velX
            rotX += (-0.22f - rotX) * 0.02f
        }

        val breathe = 1f + sin(phase) * 0.035f + totalEnergy * 0.20f
        val orbRadius = baseRadius * breathe

        // Color blend: Idle (Indigo/Cyan) -> Input speech (Electric Cyan/Blue) -> Output translation (Neon Violet/Emerald)
        val outBias = if (smoothOutLevel > 0.05f) (smoothOutLevel / (smoothInLevel + smoothOutLevel + 0.01f)).coerceIn(0f, 1f) else 0f
        val primaryColor = blendColors(
            if (isActive) Color.parseColor("#06B6D4") else Color.parseColor("#6366F1"),
            Color.parseColor("#C084FC"),
            outBias
        )
        val accentColor = blendColors(
            Color.parseColor("#38BDF8"),
            Color.parseColor("#34D399"),
            outBias
        )
        val deepColor = Color.parseColor("#0F172A")

        // 1. Draw 3D floating floor shadow beneath the orb
        val shadowHalfW = orbRadius * 0.95f
        val shadowHalfH = orbRadius * 0.17f
        val shadowY = h * 0.89f
        shadowRect.set(cx - shadowHalfW, shadowY - shadowHalfH, cx + shadowHalfW, shadowY + shadowHalfH)
        shadowPaint.shader = RadialGradient(
            cx, shadowY, max(1f, shadowHalfW),
            intArrayOf(
                withAlpha(primaryColor, if (isActive) 130 else 80),
                withAlpha(accentColor, 35),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawOval(shadowRect, shadowPaint)

        // 2. Draw outer volumetric 3D aura
        val auraRadius = orbRadius * (1.58f + totalEnergy * 0.35f)
        auraPaint.shader = RadialGradient(
            cx, cy, max(1f, auraRadius),
            intArrayOf(
                withAlpha(accentColor, if (isActive) 115 else 65),
                withAlpha(primaryColor, if (isActive) 65 else 30),
                Color.TRANSPARENT
            ),
            floatArrayOf(0.15f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, auraRadius, auraPaint)

        // Project 3D points with spherical wave displacement
        val cosY = cos(rotY)
        val sinY = sin(rotY)
        val cosX = cos(rotX)
        val sinX = sin(rotX)
        val fov = 3.2f

        for (i in points.indices) {
            val p = points[i]
            val wave = if (p.ringId == 0) {
                val ripple = sin(p.bx * 4.5f + phase * 1.7f) *
                    cos(p.by * 4.5f - phase * 1.3f) *
                    sin(p.bz * 4.5f + phase)
                1f + ripple * (0.04f + totalEnergy * 0.32f)
            } else {
                val ringWave = sin( (p.bx + p.bz) * 3f + phase * (if (p.ringId == 1) 1.5f else -1.5f) )
                1f + ringWave * (0.02f + totalEnergy * 0.12f)
            }

            val wx = p.bx * wave
            val wy = p.by * wave
            val wz = p.bz * wave

            // Rotate around Y then X
            val rx = wx * cosY - wz * sinY
            val rz1 = wx * sinY + wz * cosY
            val ry = wy * cosX - rz1 * sinX
            val rz = wy * sinX + rz1 * cosX

            val perspective = fov / (fov - rz.coerceIn(-2.2f, 2.2f))
            val proj = projected[i]
            proj.x = cx + rx * orbRadius * perspective
            proj.y = cy + ry * orbRadius * perspective
            proj.z = rz
            proj.scale = perspective
            proj.ringId = p.ringId
        }

        // Sort back-to-front by Z coordinate
        val indices = projected.indices.sortedBy { projected[it].z }

        // 3. Draw back hemisphere points (z < 0)
        drawProjectedLayer(canvas, indices, backLayer = true, primaryColor = primaryColor, accentColor = accentColor, energy = totalEnergy)

        // 4. Draw 3D Volumetric Inner Sphere Core (Lit from top-left for 3D depth)
        val coreRadius = orbRadius * 0.84f
        val lightX = cx - coreRadius * 0.35f
        val lightY = cy - coreRadius * 0.38f
        corePaint.shader = RadialGradient(
            lightX, lightY, max(1f, coreRadius * 1.45f),
            intArrayOf(
                withAlpha(Color.WHITE, 210),
                withAlpha(accentColor, 195),
                withAlpha(primaryColor, 215),
                withAlpha(deepColor, 240)
            ),
            floatArrayOf(0f, 0.24f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius, corePaint)

        // Draw 3D top-left glossy specular reflection oval
        arcRect.set(
            cx - coreRadius * 0.62f,
            cy - coreRadius * 0.72f,
            cx + coreRadius * 0.18f,
            cy - coreRadius * 0.14f
        )
        specularPaint.shader = LinearGradient(
            arcRect.left, arcRect.top, arcRect.right, arcRect.bottom,
            withAlpha(Color.WHITE, 140),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.rotate(-22f, arcRect.centerX(), arcRect.centerY())
        canvas.drawOval(arcRect, specularPaint)
        canvas.restore()

        // Draw 3D rim light around sphere
        rimPaint.shader = LinearGradient(
            cx - coreRadius, cy - coreRadius,
            cx + coreRadius, cy + coreRadius,
            intArrayOf(
                withAlpha(Color.WHITE, 200),
                withAlpha(accentColor, 150),
                withAlpha(primaryColor, 50)
            ),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, coreRadius, rimPaint)

        // 5. Draw front hemisphere points & orbital rings (z >= 0)
        drawProjectedLayer(canvas, indices, backLayer = false, primaryColor = primaryColor, accentColor = accentColor, energy = totalEnergy)

        // Keep 60fps animation alive while attached
        postInvalidateOnAnimation()
    }

    private fun drawProjectedLayer(
        canvas: Canvas,
        sortedIndices: List<Int>,
        backLayer: Boolean,
        primaryColor: Int,
        accentColor: Int,
        energy: Float
    ) {
        var prevRing1: ProjectedPoint? = null
        var prevRing2: ProjectedPoint? = null

        for (idx in sortedIndices) {
            val pt = projected[idx]
            val isBack = pt.z < 0f
            if (isBack != backLayer) continue

            val depthNorm = ((pt.z + 1.4f) / 2.8f).coerceIn(0f, 1f)
            val alpha = (40 + (depthNorm * 215).toInt()).coerceIn(30, 255)

            if (pt.ringId == 0) {
                val dotColor = blendColors(primaryColor, Color.WHITE, depthNorm * 0.65f)
                nodePaint.color = withAlpha(dotColor, alpha)
                val dotRadius = (2.2f + depthNorm * 3.8f + energy * 2.5f) * pt.scale * 0.85f
                canvas.drawCircle(pt.x, pt.y, dotRadius, nodePaint)
            } else {
                val ringColor = if (pt.ringId == 1) accentColor else Color.parseColor("#E879F9")
                nodePaint.color = withAlpha(ringColor, (alpha * 0.9f).toInt())
                val r = (1.6f + depthNorm * 2.2f) * pt.scale * 0.8f
                canvas.drawCircle(pt.x, pt.y, r, nodePaint)

                val prev = if (pt.ringId == 1) prevRing1 else prevRing2
                if (prev != null) {
                    val distSq = (pt.x - prev.x) * (pt.x - prev.x) + (pt.y - prev.y) * (pt.y - prev.y)
                    if (distSq < 2600f) {
                        linePaint.color = withAlpha(ringColor, (alpha * 0.45f).toInt())
                        canvas.drawLine(prev.x, prev.y, pt.x, pt.y, linePaint)
                    }
                }
                if (pt.ringId == 1) prevRing1 = pt else prevRing2 = pt
            }
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (a shl 24)
    }

    private fun blendColors(c1: Int, c2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val ir = 1f - r
        val red = (Color.red(c1) * ir + Color.red(c2) * r).toInt()
        val green = (Color.green(c1) * ir + Color.green(c2) * r).toInt()
        val blue = (Color.blue(c1) * ir + Color.blue(c2) * r).toInt()
        return Color.rgb(red, green, blue)
    }
}
