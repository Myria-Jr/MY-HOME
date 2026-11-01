package de.myria.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.SweepGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Zeichnet die komplette Island selbst (Form + Inhalt + Bubble) und animiert
 * Größe, Rundung, Transparenz und Inhalt mit Federn – wie SwiftUI-Springs.
 */
@SuppressLint("ViewConstructor")
class IslandView(context: Context, private val topInset: Float = 0f) : View(context) {

    enum class Mode { HIDDEN, IDLE, COMPACT, EXPANDED }

    interface Listener {
        fun onIslandTap()
        fun onIslandLongPress()
        fun onOutside()
        fun onSwipeUp()
        fun onSwipeDown()
        fun onBubbleTap()
        fun onHit(run: () -> Unit)
        fun onSeek(ms: Long)
    }

    companion object {
        const val GREEN = 0xFF30D158.toInt()
        const val RED = 0xFFFF453A.toInt()
        const val ORANGE = 0xFFFF9F0A.toInt()
        const val BLUE = 0xFF0A84FF.toInt()
        const val GRAY = 0xFF8E8E93.toInt()
        const val LIGHT = 0xFFD1D1D6.toInt()
    }

    private val d = resources.displayMetrics.density
    private fun spx(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics) * cfg.textScale

    var listener: Listener? = null

    /** w, h, settled → Fenstergröße für das Overlay (nur im Service). */
    var onWindowNeed: ((Int, Int, Boolean) -> Unit)? = null

    /** Verfügbare Breite für die erweiterte Ansicht; 0 = eigene Breite */
    var hostWidthPx = 0
        set(v) {
            field = v
            retarget()
            kick()
        }

    var cfg = IslandConfig()
        set(v) {
            val fontChanged = v.fontStyle != field.fontStyle
            field = v
            if (fontChanged) applyFonts()
            applySpringParams()
            retarget()
            kick()
        }

    /** Akkustand für die leere Pille */
    var idleBattery = -1
        set(v) {
            if (field != v) {
                field = v
                kick()
            }
        }

    var privacy = Privacy()
        set(v) {
            field = v
            kick()
        }

    var mode = Mode.HIDDEN
        private set

    /** Inhalt, der gerade gezeichnet wird */
    private var drawn: Content? = null
    /** Inhalt, zu dem wir hin animieren */
    var target: Content? = null
        private set
    var secondary: Content? = null
        private set
    private var bubbleDrawn: Content? = null
    private var swapping = false

    private val sW = Spring(0f)
    private val sH = Spring(0f)
    private val sR = Spring(0f)
    private val sAlpha = Spring(0f, precision = 0.003f)
    private val sContent = Spring(0f, precision = 0.003f)
    private val sPress = Spring(1f, precision = 0.001f)
    private val sBubble = Spring(0f, precision = 0.003f)
    /** Akku-Füllstand beim Laden (animiert hochzählen) */
    private val sFill = Spring(0f, stiffness = 40f, dampingRatio = 1f, precision = 0.002f)
    /** Cover-„Pop“ bei neuem Song */
    private val sArtPop = Spring(1f, stiffness = 260f, dampingRatio = 0.5f, precision = 0.002f)
    private val springs = arrayOf(sW, sH, sR, sAlpha, sContent, sPress, sBubble, sFill, sArtPop)
    private var chargeStart = 0L
    private var lastMusicId: String? = null

    private var lastFrame = 0L
    private var initialized = false

    // ---- Zeichen-Werkzeuge ----
    private val island = RectF()
    private val bubble = RectF()
    private val islandPath = Path()
    private val linePath = Path()
    private val tmp = RectF()
    private val tmp2 = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font(500) }
    private val boldPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font(650) }
    private val numPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = font(600)
        fontFeatureSettings = "tnum"
    }
    private val shaderMatrix = Matrix()
    private val whiteFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)

    private val shaderCache = HashMap<Bitmap, BitmapShader>()
    private val artMatrix = Matrix()

    private class Hit(val rect: RectF, val run: () -> Unit)
    private val hits = ArrayList<Hit>()
    private val seekRect = RectF()
    private var seekActive = false

    private var notifLayoutKey: String? = null
    private var notifLayout: StaticLayout? = null

    // ---- Touch ----
    private var downX = 0f
    private var downY = 0f
    private var pressed = false
    private var bubblePressed = false
    private var longFired = false
    private var swiped = false
    private var seeking = false
    private var seekFrac = 0f
    private val longPress = Runnable {
        if (pressed && !swiped && !seeking) {
            longFired = true
            if (cfg.squish) sPress.target = 1.035f
            kick()
            listener?.onIslandLongPress()
        }
    }

    init {
        applySpringParams()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun font(weight: Int): Typeface {
        val family = when (cfg.fontStyle) {
            IslandConfig.FONT_GOOGLE -> "google-sans"
            IslandConfig.FONT_CONDENSED -> "sans-serif-condensed"
            IslandConfig.FONT_MONO -> "monospace"
            IslandConfig.FONT_SERIF -> "serif"
            else -> "sans-serif"
        }
        val base = Typeface.create(family, Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
        else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun applyFonts() {
        textPaint.typeface = font(500)
        boldPaint.typeface = font(650)
        numPaint.typeface = font(600)
        notifLayoutKey = null
    }

    private fun applySpringParams() {
        val k = cfg.stiffness
        val z = cfg.dampingRatio
        for (s in arrayOf(sW, sH, sR)) {
            s.stiffness = k; s.dampingRatio = z
        }
        sAlpha.stiffness = k * 1.4f; sAlpha.dampingRatio = 1f
        sContent.stiffness = k * 2.2f; sContent.dampingRatio = 1f
        sPress.stiffness = 600f; sPress.dampingRatio = if (cfg.reduceMotion) 1f else 0.55f
        sBubble.stiffness = k * 0.9f; sBubble.dampingRatio = if (cfg.reduceMotion) 1f else 0.7f
    }

    // =====================================================================
    // Zustand
    // =====================================================================

    fun show(m: Mode, c: Content?, second: Content? = null) {
        val kindChanged = target?.key != c?.key
        if (c is Content.Charging) {
            if (target !is Content.Charging || (c.plugged && !(target as Content.Charging).plugged)) {
                sFill.snap(0f)
                chargeStart = System.nanoTime()
            }
            sFill.target = c.level / 100f
        }
        if (c is Content.Music && c.info.id != lastMusicId) {
            if (lastMusicId != null && !cfg.reduceMotion) {
                sArtPop.value = 0.55f
                sArtPop.target = 1f
            }
            lastMusicId = c.info.id
        }
        mode = m
        target = c
        secondary = second
        if (second != null) bubbleDrawn = second
        if (kindChanged) {
            if (drawn == null || sAlpha.value < 0.05f) {
                drawn = c
                swapping = false
                sContent.value = 0f
                sContent.target = 1f
            } else {
                swapping = true
                sContent.target = 0f
            }
        } else if (!swapping) {
            drawn = c
        }
        retarget()
        kick()
    }

    private fun bubbleWanted(): Boolean {
        val t = target
        return secondary != null && mode == Mode.COMPACT && t !is Content.Notif &&
            !(t is Content.Call && t.incoming)
    }

    private fun expandedWidth(): Float {
        val host = if (hostWidthPx > 0) hostWidthPx.toFloat() else width.toFloat()
        val w = if (host > 0) host - 20 * d else 360 * d
        return min(w, 410 * d) * (cfg.expandedWidthPct.coerceIn(60, 100) / 100f)
    }

    private data class Size(val w: Float, val h: Float, val r: Float)

    private fun sizeFor(m: Mode, c: Content?): Size {
        val w = cfg.widthDp * d
        val h = cfg.heightDp * d
        val extra = cfg.compactExtraDp * d
        val full = expandedWidth()
        val big = cfg.expandedRadiusDp * d
        fun compact(e: Float) = Size(min(w + e * d + extra, full), h, h / 2)
        fun banner(hh: Float) = Size(full, hh, min(hh / 2, big))
        fun exp(hh: Float) = Size(full, hh, min(big, hh / 2))
        return when (m) {
            Mode.HIDDEN -> Size(h * 1.1f, h * 0.8f, h * 0.4f)
            Mode.IDLE -> Size(if (cfg.idleContent != IslandConfig.IDLE_NONE) w + 58 * d else w, h, h / 2)
            Mode.COMPACT -> when (c) {
                is Content.Notif -> banner(max(72 * d, h + 32 * d))
                is Content.Call -> if (c.incoming) banner(max(80 * d, h + 40 * d)) else compact(124f)
                is Content.Ringer -> compact(132f)
                is Content.Charging -> compact(112f)
                is Content.Timer -> compact(126f)
                is Content.Nav -> compact(140f)
                is Content.Progress -> compact(100f)
                is Content.Device -> compact(128f)
                is UnlockContent -> Size(w + h * 1.5f, h, h / 2)
                is VolumeContent -> compact(150f)
                is TorchContent -> compact(96f)
                is FocusContent -> compact(130f)
                is AirplaneContent -> compact(110f)
                else -> compact(116f)
            }
            Mode.EXPANDED -> when (c) {
                is Content.Music -> exp(202 * d)
                is Content.Notif -> exp(notifExpandedHeight(c, full))
                is Content.Call -> if (c.incoming) sizeFor(Mode.COMPACT, c) else exp(92 * d)
                is Content.Timer -> exp(92 * d)
                is Content.Nav -> exp(112 * d)
                is Content.Progress -> exp(96 * d)
                else -> sizeFor(Mode.COMPACT, c)
            }
        }
    }

    private fun retarget() {
        val s = sizeFor(mode, target)
        if (!initialized) {
            initialized = true
            sW.snap(s.w); sH.snap(s.h); sR.snap(min(s.r, s.h / 2 * roundness()))
            sAlpha.snap(if (mode == Mode.HIDDEN) 0f else 1f)
        }
        sW.target = s.w
        sH.target = s.h
        sR.target = min(s.r, s.h / 2 * roundness())
        sAlpha.target = if (mode == Mode.HIDDEN) 0f else 1f
        sBubble.target = if (bubbleWanted()) 1f else 0f
    }

    fun kick() {
        invalidate()
    }

    private fun roundness() = (cfg.compactRoundPct.coerceIn(20, 100) / 100f)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (hostWidthPx == 0) retarget()
    }

    // =====================================================================
    // Zeichnen
    // =====================================================================

    override fun onDraw(canvas: Canvas) {
        val now = System.nanoTime()
        val dt = if (lastFrame == 0L) 1f / 60f else ((now - lastFrame) / 1e9f).coerceIn(0.001f, 1f / 24f)
        lastFrame = now

        var moving = false
        for (s in springs) {
            if (s.step(dt)) moving = true
        }
        if (swapping && sContent.value <= 0.04f) {
            drawn = target
            swapping = false
            sContent.target = 1f
            moving = true
        }
        if (sBubble.value <= 0.003f && sBubble.target == 0f) bubbleDrawn = secondary

        reportWindowNeed(moving)

        hits.clear()
        seekActive = false
        val alpha = sAlpha.value.coerceIn(0f, 1f)
        if (alpha > 0.003f) drawIsland(canvas, alpha)

        val delay = continuousDelay(alpha)
        when {
            moving || seeking -> postInvalidateOnAnimation()
            delay > 0 -> postInvalidateDelayed(delay)
            else -> lastFrame = 0L
        }
    }

    /** Wie oft neu zeichnen, obwohl sich nichts bewegt (Wellenform, Uhr …) */
    private fun continuousDelay(alpha: Float): Long {
        if (alpha <= 0.003f) return 0
        var best = 0L
        fun want(ms: Long) { if (best == 0L || ms < best) best = ms }
        if (mode == Mode.IDLE && (cfg.idleContent == IslandConfig.IDLE_TIME || cfg.idleContent == IslandConfig.IDLE_BOTH)) {
            want(60_000L - System.currentTimeMillis() % 60_000L + 50)
        }
        for (c in listOf(drawn, if (sBubble.value > 0.01f) bubbleDrawn else null)) {
            when (c) {
                is Content.Music -> {
                    if (mode == Mode.EXPANDED && c === drawn) want(33)
                    else if (c.info.playing && cfg.waveform) want(33)
                }
                is Content.Timer -> want(250)
                is Content.Call -> want(if (c.incoming) 33 else 250)
                is Content.Progress -> if (c.progress < 0) want(33)
                is UnlockContent -> if (c === drawn) want(16)
                is VolumeContent -> {}
                is Content.Charging -> if (c.plugged && c === drawn &&
                    System.nanoTime() - chargeStart < 4_500_000_000L) want(33)
                else -> {}
            }
        }
        return best
    }

    private fun reportWindowNeed(moving: Boolean) {
        val cb = onWindowNeed ?: return
        val pad = 28 * d
        val press = max(1f, sPress.value)
        val bubbleExtra = (cfg.heightDp * d + 10 * d) * max(sBubble.value, sBubble.target).coerceIn(0f, 1.2f)
        val w = (max(sW.value, sW.target) * press + bubbleExtra * 2) + pad * 2
        val h = max(sH.value, sH.target) * press + pad + topInset
        cb(ceil(w).toInt(), ceil(h).toInt(), !moving && !seeking)
    }

    private fun accentOf(c: Content?): Int = when (cfg.accentMode) {
        IslandConfig.ACCENT_CUSTOM -> cfg.accentColor
        IslandConfig.ACCENT_WHITE -> Color.WHITE
        IslandConfig.ACCENT_SYSTEM -> systemColor(android.R.color.system_accent1_200, cfg.accentColor)
        else -> (c as? Content.Music)?.info?.accent ?: cfg.accentColor
    }

    private fun waveColor(c: Content.Music): Int =
        if (cfg.waveformCover) accentOf(c) else Color.WHITE

    private fun drawIsland(canvas: Canvas, alpha: Float) {
        val w = sW.value
        val h = max(sH.value, 1f)
        val r = min(sR.value, h / 2).coerceAtLeast(0f)
        val cx = width / 2f
        val top = topInset
        island.set(cx - w / 2, top, cx + w / 2, top + h)

        val layered = alpha < 0.999f
        if (layered) canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (alpha * 255).toInt())
        canvas.save()
        canvas.scale(sPress.value, sPress.value, cx, top)

        val c = drawn
        val ca = sContent.value.coerceIn(0f, 1f)

        // --- Bubble (zweite Aktivität) ---
        val bp = sBubble.value
        if (bp > 0.01f && bubbleDrawn != null) drawBubble(canvas, bp.coerceIn(0f, 1.15f))

        // --- Glow / Schatten ---
        bgPaint.clearShadowLayer()
        if (Build.VERSION.SDK_INT >= 28) {
            if (cfg.glow && c is Content.Music && c.info.playing) {
                val s = cfg.glowStrength / 100f
                bgPaint.setShadowLayer((8 + 18 * s) * d, 0f, 3 * d, withAlpha(accentOf(c), (0.25f + 0.6f * s) * ca))
            } else if (cfg.shadow) {
                bgPaint.setShadowLayer(14 * d, 0f, 4 * d, 0x66000000)
            }
        }

        // --- Form ---
        paintShape(canvas, island, r, c)

        // --- Grüne Welle beim Einstecken (außerhalb der Form) ---
        if (c is Content.Charging && c.plugged && !cfg.reduceMotion) {
            val age = (System.nanoTime() - chargeStart) / 1e9f
            if (age < 1.4f) {
                val t = age / 1.4f
                val grow = 18 * d * t
                tmp.set(island.left - grow, island.top - grow * 0.5f, island.right + grow, island.bottom + grow)
                strokePaint.shader = null
                strokePaint.strokeWidth = 2.5f * d * (1f - t)
                strokePaint.color = withAlpha(GREEN, ca * (1f - t) * 0.9f)
                canvas.drawRoundRect(tmp, tmp.height() / 2, tmp.height() / 2, strokePaint)
            }
        }

        // --- Inhalt ---
        if (mode == Mode.IDLE) {
            drawIdleContent(canvas)
            if (privacy.any) drawPrivacy(canvas)
        }
        if (c != null && ca > 0.01f) {
            islandPath.reset()
            islandPath.addRoundRect(island, r, r, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(islandPath)
            if (!cfg.reduceMotion) {
                val scale = 0.9f + 0.1f * ca
                canvas.scale(scale, scale, island.centerX(), island.centerY())
            }
            val big = mode == Mode.EXPANDED && sH.value > cfg.heightDp * d * 1.8f
            val interactive = ca > 0.6f
            when (c) {
                is Content.Music -> if (big) drawMusicExpanded(canvas, c, ca, interactive) else drawMusicCompact(canvas, c, ca)
                is Content.Charging -> drawCharging(canvas, c, ca)
                is Content.Ringer -> drawRinger(canvas, c, ca)
                is Content.Notif -> if (big) drawNotifExpanded(canvas, c, ca, interactive) else drawNotif(canvas, c, ca)
                is Content.Timer -> if (big) drawTimerExpanded(canvas, c, ca, interactive) else drawTimerCompact(canvas, c, ca)
                is Content.Call -> when {
                    c.incoming -> drawCallIncoming(canvas, c, ca, interactive)
                    big -> drawCallExpanded(canvas, c, ca, interactive)
                    else -> drawCallCompact(canvas, c, ca)
                }
                is Content.Nav -> if (big) drawNavExpanded(canvas, c, ca) else drawNavCompact(canvas, c, ca)
                is Content.Progress -> if (big) drawProgressExpanded(canvas, c, ca) else drawProgressCompact(canvas, c, ca)
                is Content.Device -> drawDevice(canvas, c, ca)
                is UnlockContent -> drawUnlock(canvas, ca)
                is VolumeContent -> drawVolume(canvas, c, ca)
                is TorchContent -> drawTorch(canvas, ca)
                is FocusContent -> drawFocus(canvas, c, ca)
                is AirplaneContent -> drawAirplane(canvas, c, ca)
            }
            canvas.restore()
        }

        canvas.restore()
        if (layered) canvas.restore()
    }

    private fun systemColor(id: Int, fallback: Int): Int =
        if (Build.VERSION.SDK_INT >= 31) try { context.getColor(id) } catch (_: Exception) { fallback } else fallback

    private fun paintShape(canvas: Canvas, rect: RectF, r: Float, c: Content?) {
        bgPaint.shader = null
        when (cfg.style) {
            IslandConfig.STYLE_GLASS -> paintGlass(canvas, rect, r, c)
            IslandConfig.STYLE_TINT -> {
                val a = accentOf(c)
                bgPaint.shader = LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    intArrayOf(mix(a, Color.BLACK, 0.62f), mix(a, Color.BLACK, 0.86f)), null, Shader.TileMode.CLAMP,
                )
                bgPaint.color = Color.WHITE
                canvas.drawRoundRect(rect, r, r, bgPaint)
                gapPaint.color = mix(a, Color.BLACK, 0.75f)
            }
            IslandConfig.STYLE_MATERIAL -> {
                // Material You: dunkle, leicht eingefärbte Fläche
                val base = systemColor(android.R.color.system_neutral1_900, 0xFF1D1B20.toInt())
                val col = mix(base, accentOf(c), 0.08f)
                bgPaint.color = col
                canvas.drawRoundRect(rect, r, r, bgPaint)
                gapPaint.color = col
            }
            IslandConfig.STYLE_GRAPHITE -> {
                bgPaint.color = 0xFF1B1B1D.toInt()
                canvas.drawRoundRect(rect, r, r, bgPaint)
                strokePaint.shader = null
                strokePaint.strokeWidth = 0.8f * d
                strokePaint.color = 0x22FFFFFF
                tmp2.set(rect); tmp2.inset(0.4f * d, 0.4f * d)
                canvas.drawRoundRect(tmp2, r, r, strokePaint)
                gapPaint.color = 0xFF1B1B1D.toInt()
            }
            IslandConfig.STYLE_OUTLINE -> {
                bgPaint.color = Color.BLACK
                canvas.drawRoundRect(rect, r, r, bgPaint)
                strokePaint.shader = null
                strokePaint.strokeWidth = 1.2f * d
                strokePaint.color = 0x40FFFFFF
                tmp2.set(rect); tmp2.inset(0.6f * d, 0.6f * d)
                canvas.drawRoundRect(tmp2, r, r, strokePaint)
                gapPaint.color = Color.BLACK
            }
            else -> {
                bgPaint.color = Color.BLACK
                canvas.drawRoundRect(rect, r, r, bgPaint)
                gapPaint.color = Color.BLACK
            }
        }
        bgPaint.shader = null
    }

    /**
     * Liquid Glass: durchscheinende Fläche + Glanz oben + Lichtkante
     * (oben hell, unten Reflex) + optionaler Regenbogen-Farbsaum.
     */
    private fun paintGlass(canvas: Canvas, rect: RectF, r: Float, c: Content?) {
        val opacity = cfg.glassOpacity.coerceIn(0, 100) / 100f
        val shine = cfg.glassShine.coerceIn(0, 100) / 100f
        val tint = cfg.glassTint.coerceIn(0, 100) / 100f
        val accent = accentOf(c)
        val baseTop = mix(0xFF3A3A40.toInt(), accent, tint * 0.6f)
        val baseBottom = mix(0xFF101012.toInt(), accent, tint * 0.35f)
        val aTop = 0.18f + 0.72f * opacity
        val aBottom = 0.30f + 0.65f * opacity

        // 1) Körper
        bgPaint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            intArrayOf(withAlpha(baseTop, aTop), withAlpha(baseBottom, aBottom)), null, Shader.TileMode.CLAMP)
        bgPaint.color = Color.WHITE
        canvas.drawRoundRect(rect, r, r, bgPaint)
        bgPaint.shader = null

        // 2) Glanz (obere Hälfte)
        if (shine > 0.01f) {
            p.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * 0.55f,
                intArrayOf(withAlpha(Color.WHITE, 0.22f * shine), 0x00FFFFFF), null, Shader.TileMode.CLAMP)
            tmp2.set(rect.left + r * 0.15f, rect.top + 1.2f * d, rect.right - r * 0.15f, rect.top + rect.height() * 0.6f)
            canvas.drawRoundRect(tmp2, r * 0.9f, r * 0.9f, p)
            p.shader = null
        }

        // 3) Lichtkante
        if (cfg.glassBorder) {
            val bw = (cfg.glassBorderWidth.coerceIn(3, 30) / 10f) * d
            strokePaint.strokeWidth = bw
            strokePaint.shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(
                    withAlpha(Color.WHITE, 0.25f + 0.65f * shine),
                    withAlpha(Color.WHITE, 0.06f),
                    withAlpha(Color.WHITE, 0.10f + 0.35f * shine),
                ), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            tmp2.set(rect); tmp2.inset(bw / 2, bw / 2)
            canvas.drawRoundRect(tmp2, r, r, strokePaint)
            // 4) Farbsaum (Lichtbrechung)
            if (cfg.glassRainbow) {
                strokePaint.strokeWidth = bw * 0.9f
                strokePaint.shader = SweepGradient(rect.centerX(), rect.centerY(),
                    intArrayOf(0x5564D2FF, 0x40FF6AD5, 0x45FFD60A, 0x4030D158, 0x5564D2FF), null)
                tmp2.set(rect); tmp2.inset(bw * 1.4f, bw * 1.4f)
                val rr = (r - bw).coerceAtLeast(0f)
                canvas.drawRoundRect(tmp2, rr, rr, strokePaint)
            }
            strokePaint.shader = null
        }
        gapPaint.color = mix(baseBottom, Color.BLACK, 0.3f)
    }

    // ---- Bubble ----

    private fun drawBubble(canvas: Canvas, bp: Float) {
        val c = bubbleDrawn ?: return
        val bs = cfg.heightDp * d
        val cy = island.centerY()
        val startX = island.right - bs / 2
        val endX = island.right + 7 * d + bs / 2
        val bx = startX + (endX - startX) * bp
        val rad = bs / 2 * (0.55f + 0.45f * bp.coerceAtMost(1f))
        // „Gooey“-Brücke beim Abtrennen
        if (bp < 0.75f) {
            val bh = bs * 0.8f * (1f - bp / 0.75f)
            tmp.set(island.right - bs / 2, cy - bh / 2, bx, cy + bh / 2)
            paintShape(canvas, tmp, bh / 2, c)
        }
        bubble.set(bx - rad, cy - rad, bx + rad, cy + rad)
        paintShape(canvas, bubble, rad * roundness(), c)
        val a = bp.coerceIn(0f, 1f) * bp.coerceIn(0f, 1f)
        if (a > 0.05f) drawMini(canvas, c, bx, cy, rad * 2, a)
    }

    private fun drawMini(canvas: Canvas, c: Content, cx: Float, cy: Float, s: Float, a: Float) {
        when (c) {
            is Content.Music -> {
                val inner = s * 0.62f
                tmp.set(cx - inner / 2, cy - inner / 2, cx + inner / 2, cy + inner / 2)
                if (c.info.art != null) drawBitmapRound(canvas, RectF(tmp), c.info.art, inner / 2, a)
                else drawWave(canvas, cx + waveWidth() / 2 * 0.8f, cy, s * 0.45f, c, a, bars = 3)
            }
            is Content.Timer -> { p.color = withAlpha(ORANGE, a); Icons.timer(canvas, cx, cy, s * 0.5f, p) }
            is Content.Call -> { p.color = withAlpha(GREEN, a); Icons.phone(canvas, cx, cy, s * 0.46f, p) }
            is Content.Nav -> drawNavArrow(canvas, c, cx, cy, s * 0.56f, a)
            is Content.Progress -> drawRing(canvas, cx, cy, s * 0.5f, c.progress, if (c.done) GREEN else BLUE, a)
            is Content.Charging -> { p.color = withAlpha(GREEN, a); Icons.bolt(canvas, cx, cy, s * 0.5f, p) }
            is Content.Device -> { p.color = withAlpha(Color.WHITE, a); Icons.headphones(canvas, cx, cy, s * 0.5f, p) }
            else -> {}
        }
    }

    // ---- Privacy-Punkte (Kamera grün, Mikro orange, Standort blau) ----

    private fun drawPrivacy(canvas: Canvas) {
        val h = island.height()
        var x = island.right - h * 0.5f
        val cy = island.centerY()
        val r = 3.6f * d
        if (privacy.camera && cfg.privacyCamera) {
            p.color = GREEN; canvas.drawCircle(x, cy, r, p); x -= 12 * d
        }
        if (privacy.mic && cfg.privacyMic) {
            p.color = ORANGE; canvas.drawCircle(x, cy, r, p); x -= 13 * d
        }
        if (privacy.location && cfg.privacyLocation) {
            p.color = BLUE; Icons.location(canvas, x, cy, 9.5f * d, p)
        }
    }

    // ---- Gemeinsame Bausteine ----

    private fun leftX(h: Float) = island.left + h * 0.56f
    private fun rightEdge(h: Float) = island.right - h * 0.44f

    private fun shaderFor(bmp: Bitmap): BitmapShader {
        if (shaderCache.size > 12) shaderCache.clear()
        return shaderCache.getOrPut(bmp) { BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
    }

    private fun drawBitmapRound(canvas: Canvas, rect: RectF, bmp: Bitmap?, radius: Float, a: Float): Boolean {
        if (bmp == null || bmp.isRecycled) return false
        val sh = shaderFor(bmp)
        val s = max(rect.width() / bmp.width, rect.height() / bmp.height)
        artMatrix.reset()
        artMatrix.setScale(s, s)
        artMatrix.postTranslate(rect.centerX() - bmp.width * s / 2, rect.centerY() - bmp.height * s / 2)
        sh.setLocalMatrix(artMatrix)
        artPaint.shader = sh
        artPaint.alpha = (a * 255).toInt()
        canvas.drawRoundRect(rect, radius, radius, artPaint)
        artPaint.shader = null
        return true
    }

    private inline fun popScaled(canvas: Canvas, r: RectF, block: () -> Unit) {
        val sc = sArtPop.value
        if (abs(sc - 1f) < 0.002f) { block(); return }
        canvas.save()
        canvas.scale(sc, sc, r.centerX(), r.centerY())
        block()
        canvas.restore()
    }

    private fun drawArt(canvas: Canvas, rect: RectF, c: Content.Music, radius: Float, a: Float) {
        if (!drawBitmapRound(canvas, rect, c.info.art, radius, a)) {
            p.color = withAlpha(accentOf(c), a)
            canvas.drawRoundRect(rect, radius, radius, p)
            p.color = withAlpha(Color.WHITE, a * 0.9f)
            Icons.note(canvas, rect.centerX(), rect.centerY(), rect.width() * 0.5f, p)
        }
    }

    private fun drawWave(canvas: Canvas, right: Float, cy: Float, maxH: Float, c: Content.Music, a: Float, bars: Int = 5, color: Int? = null) {
        val info = c.info
        val bw = 2.6f * d
        val gap = 2.3f * d
        val t = System.nanoTime() / 1e9f
        p.color = withAlpha(color ?: waveColor(c), a)
        val animated = info.playing && cfg.waveform
        if (cfg.waveStyle == IslandConfig.WAVE_LINE) {
            // Sinus-Linie (Material-Look)
            val w = bars * bw + (bars - 1) * gap
            ringPaint.strokeWidth = 2.2f * d
            ringPaint.color = p.color
            linePath.reset()
            val steps = 24
            for (i in 0..steps) {
                val f = i / steps.toFloat()
                val env = sin(f * Math.PI.toFloat())
                val amp = if (animated) maxH * 0.42f * (0.55f + 0.45f * sin(t * 3.1f)) else if (info.playing) maxH * 0.3f else maxH * 0.05f
                val y = cy + sin(f * 12.5f + (if (animated) t * 9f else 0f)) * amp * env
                val x = right - w + w * f
                if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
            }
            canvas.drawPath(linePath, ringPaint)
            return
        }
        val freq = floatArrayOf(5.1f, 7.3f, 6.2f, 8.4f, 5.7f)
        val phase = floatArrayOf(0f, 1.3f, 2.1f, 0.7f, 2.9f)
        val still = floatArrayOf(0.5f, 0.85f, 0.6f, 1f, 0.45f)
        for (i in 0 until bars) {
            val amp = when {
                animated -> {
                    val s1 = 0.5f + 0.5f * sin(t * freq[i] + phase[i])
                    val s2 = 0.65f + 0.35f * sin(t * freq[(i + 2) % 5] * 0.53f + phase[i] * 1.7f)
                    0.22f + 0.78f * s1 * s2
                }
                info.playing -> still[i]
                else -> 0.16f
            }
            val bh = max(bw, maxH * amp)
            val x = right - (bars - i) * bw - (bars - 1 - i) * gap
            if (cfg.waveStyle == IslandConfig.WAVE_DOTS) {
                // Punkt-Matrix
                val step = bw + 1.4f * d
                val n = max(1, (bh / step).toInt())
                val start = cy - (n - 1) * step / 2
                for (k in 0 until n) canvas.drawCircle(x + bw / 2, start + k * step, bw / 2, p)
            } else {
                tmp.set(x, cy - bh / 2, x + bw, cy + bh / 2)
                canvas.drawRoundRect(tmp, bw / 2, bw / 2, p)
            }
        }
    }

    /** Fortschrittsbalken in drei Stilen: Linie, Welle (Material), Punkte (Nothing) */
    private fun drawBar(canvas: Canvas, l: Float, r: Float, cy: Float, frac: Float, color: Int, a: Float, thick: Float, moving: Boolean) {
        val f = frac.coerceIn(0f, 1f)
        val split = l + (r - l) * f
        when (cfg.progressStyle) {
            IslandConfig.PROGRESS_SQUIGGLE -> {
                ringPaint.strokeWidth = max(2.5f * d, thick * 0.55f)
                ringPaint.color = withAlpha(Color.WHITE, a * 0.25f)
                if (r > split + 6 * d) canvas.drawLine(split + 6 * d, cy, r, cy, ringPaint)
                ringPaint.color = withAlpha(color, a)
                linePath.reset()
                val t = if (moving && !cfg.reduceMotion) System.nanoTime() / 1e9f * 6f else 0f
                val amp = 3f * d
                val wl = 22 * d
                var x = l
                linePath.moveTo(l, cy)
                while (x <= split) {
                    linePath.lineTo(x, cy + sin((x - l) / wl * 6.283f - t) * amp)
                    x += 2 * d
                }
                if (split > l) canvas.drawPath(linePath, ringPaint)
                p.color = withAlpha(color, a)
                tmp.set(split - 2.5f * d, cy - 9 * d, split + 2.5f * d, cy + 9 * d)
                canvas.drawRoundRect(tmp, 2.5f * d, 2.5f * d, p)
            }
            IslandConfig.PROGRESS_DOTS -> {
                val step = 6.5f * d
                val rad = max(1.4f * d, thick * 0.32f)
                var x = l
                while (x <= r) {
                    p.color = withAlpha(color, if (x <= split) a else a * 0.25f)
                    canvas.drawCircle(x, cy, rad, p)
                    x += step
                }
            }
            else -> {
                tmp.set(l, cy - thick / 2, r, cy + thick / 2)
                p.color = withAlpha(Color.WHITE, a * 0.22f)
                canvas.drawRoundRect(tmp, thick / 2, thick / 2, p)
                tmp.set(l, cy - thick / 2, split, cy + thick / 2)
                p.color = withAlpha(color, a)
                canvas.drawRoundRect(tmp, thick / 2, thick / 2, p)
            }
        }
    }

    private fun waveWidth() = 5 * 2.6f * d + 4 * 2.3f * d

    private fun drawRing(canvas: Canvas, cx: Float, cy: Float, s: Float, progress: Float, color: Int, a: Float) {
        val stroke = max(2.6f * d, s * 0.16f)
        ringPaint.strokeWidth = stroke
        val r = s / 2 - stroke / 2
        tmp.set(cx - r, cy - r, cx + r, cy + r)
        ringPaint.color = withAlpha(color, a * 0.25f)
        canvas.drawOval(tmp, ringPaint)
        ringPaint.color = withAlpha(color, a)
        if (progress < 0) {
            val start = (System.nanoTime() / 1e6f * 0.36f) % 360f
            canvas.drawArc(tmp, start, 90f, false, ringPaint)
        } else if (progress >= 0.999f) {
            canvas.drawOval(tmp, ringPaint)
        } else {
            canvas.drawArc(tmp, -90f, 360f * progress, false, ringPaint)
        }
    }

    private fun circleButton(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, a: Float, interactive: Boolean, run: (() -> Unit)?, icon: (Float, Float, Float) -> Unit) {
        p.color = withAlpha(color, a)
        canvas.drawCircle(cx, cy, r, p)
        p.color = withAlpha(Color.WHITE, a)
        icon(cx, cy, r)
        if (interactive && run != null) hits.add(Hit(RectF(cx - r - 6 * d, cy - r - 6 * d, cx + r + 6 * d, cy + r + 6 * d), run))
    }

    /** Pillen-Buttons (Benachrichtigungs-/Timer-Aktionen). Gibt die Gesamtbreite zurück. */
    private fun pillRow(canvas: Canvas, actions: List<NotifAction>, left: Float, right: Float, cy: Float, a: Float, interactive: Boolean, tint: Int = Color.WHITE, equal: Boolean = true) {
        if (actions.isEmpty()) return
        val gap = 8 * d
        val ph = 36 * d
        textPaint.textSize = spx(14f)
        val widths = if (equal) {
            val each = (right - left - gap * (actions.size - 1)) / actions.size
            List(actions.size) { each }
        } else actions.map { textPaint.measureText(it.title) + 30 * d }
        var x = if (equal) left else right - widths.sum() - gap * (actions.size - 1)
        actions.forEachIndexed { i, act ->
            val w = widths[i]
            tmp.set(x, cy - ph / 2, x + w, cy + ph / 2)
            p.color = withAlpha(tint, a * if (tint == Color.WHITE) 0.16f else 0.24f)
            canvas.drawRoundRect(tmp, ph / 2, ph / 2, p)
            textPaint.color = withAlpha(if (tint == Color.WHITE) Color.WHITE else tint, a)
            val label = ellipsize(act.title, textPaint, w - 16 * d)
            canvas.drawText(label, tmp.centerX() - textPaint.measureText(label) / 2, cy + textPaint.textSize * 0.36f, textPaint)
            if (interactive) hits.add(Hit(RectF(tmp), act.run))
            x += w + gap
        }
    }

    private fun rightText(canvas: Canvas, text: String, paint: Paint, color: Int, a: Float) {
        val h = island.height()
        paint.color = withAlpha(color, a)
        val tw = paint.measureText(text)
        canvas.drawText(text, rightEdge(h) - tw, island.centerY() + paint.textSize * 0.36f, paint)
    }

    // ---- Musik ----

    private fun drawMusicCompact(canvas: Canvas, c: Content.Music, a: Float) {
        val h = island.height()
        val art = h * 0.62f
        val pad = (h - art) / 2
        val rect = RectF(island.left + pad * 1.35f, island.top + pad, island.left + pad * 1.35f + art, island.top + pad + art)
        popScaled(canvas, rect) { drawArt(canvas, rect, c, art * 0.26f, a) }
        drawWave(canvas, island.right - pad * 1.5f, island.centerY(), art * 0.82f, c, a)
    }

    private fun drawMusicExpanded(canvas: Canvas, c: Content.Music, a: Float, interactive: Boolean) {
        val info = c.info
        val pad = 22 * d
        val artS = 60 * d
        val artRect = RectF(island.left + pad, island.top + pad, island.left + pad + artS, island.top + pad + artS)
        popScaled(canvas, artRect) { drawArt(canvas, artRect, c, 14 * d, a) }

        var right = island.right - pad
        if (cfg.musicAppIcon && info.appIcon != null) {
            val s = 20 * d
            drawBitmapRound(canvas, RectF(right - s, artRect.top, right, artRect.top + s), info.appIcon, 5 * d, a)
            right -= s + 10 * d
        }
        drawWave(canvas, right, artRect.centerY(), 22 * d, c, a)

        val tx = artRect.right + 14 * d
        val avail = right - waveWidth() - 12 * d - tx
        boldPaint.textSize = spx(17f)
        boldPaint.color = withAlpha(Color.WHITE, a)
        canvas.drawText(ellipsize(info.title, boldPaint, avail), tx, artRect.top + 26 * d, boldPaint)
        textPaint.textSize = spx(15f)
        textPaint.color = withAlpha(GRAY, a)
        canvas.drawText(ellipsize(info.artist, textPaint, avail), tx, artRect.top + 48 * d, textPaint)

        // Fortschritt (zieh zum Spulen)
        val py = artRect.bottom + 28 * d
        val pos = if (seeking && info.durationMs > 0) (seekFrac * info.durationMs).toLong() else info.positionNow()
        numPaint.textSize = spx(11.5f)
        numPaint.color = withAlpha(if (seeking) Color.WHITE else GRAY, a)
        val elapsed = formatClock(pos)
        val remain = if (info.durationMs > 0) "-" + formatClock(info.durationMs - pos) else ""
        val tw = 42 * d
        canvas.drawText(elapsed, island.left + pad, py + 4 * d, numPaint)
        canvas.drawText(remain, island.right - pad - numPaint.measureText(remain), py + 4 * d, numPaint)
        val bl = island.left + pad + tw
        val br = island.right - pad - tw
        val bh = if (seeking) 9 * d else 5.5f * d
        if (info.durationMs > 0) {
            val f = (pos.toFloat() / info.durationMs).coerceIn(0f, 1f)
            val barColor = if (cfg.progressStyle == IslandConfig.PROGRESS_LINE) Color.WHITE else accentOf(c)
            drawBar(canvas, bl, br, py, f, barColor, a, bh, info.playing)
            if (interactive && cfg.musicSeek && info.canSeek) {
                seekRect.set(bl, py - 16 * d, br, py + 16 * d)
                seekActive = true
            }
        } else {
            drawBar(canvas, bl, br, py, 0f, Color.WHITE, a, bh, false)
        }

        // Steuerung
        val cy = py + 46 * d
        val cx = island.centerX()
        val spacing = 80 * d
        drawControls(canvas, info, accentOf(c), cx, cy, spacing, a)
        if (interactive) {
            val hit = 28 * d
            info.actions?.let { act ->
                hits.add(Hit(RectF(cx - spacing - hit, cy - hit, cx - spacing + hit, cy + hit)) { act.previous() })
                hits.add(Hit(RectF(cx - hit, cy - hit, cx + hit, cy + hit)) { act.playPause() })
                hits.add(Hit(RectF(cx + spacing - hit, cy - hit, cx + spacing + hit, cy + hit)) { act.next() })
            }
        }
    }

    private fun isLight(c: Int): Boolean =
        (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f) > 150f

    /** ⏮ ⏯ ⏭ in vier Stilen */
    private fun drawControls(canvas: Canvas, info: MusicInfo, accent: Int, cx: Float, cy: Float, spacing: Float, a: Float) {
        val s = 30 * d
        when (cfg.controlStyle) {
            IslandConfig.CONTROL_FILLED -> {
                // Material: Play in gefüllter, abgerundeter Fläche
                val bw = 64 * d
                val bh = 48 * d
                tmp.set(cx - bw / 2, cy - bh / 2, cx + bw / 2, cy + bh / 2)
                p.color = withAlpha(accent, a)
                val rad = if (info.playing) 16 * d else bh / 2
                canvas.drawRoundRect(tmp, rad, rad, p)
                p.color = withAlpha(if (isLight(accent)) 0xFF1C1B1F.toInt() else Color.WHITE, a)
                if (info.playing) Icons.pause(canvas, cx, cy, s * 0.8f, p) else Icons.play(canvas, cx, cy, s * 0.8f, p)
            }
            IslandConfig.CONTROL_CIRCLE -> {
                p.color = withAlpha(Color.WHITE, a)
                canvas.drawCircle(cx, cy, 26 * d, p)
                p.color = withAlpha(Color.BLACK, a)
                if (info.playing) Icons.pause(canvas, cx, cy, s * 0.75f, p) else Icons.play(canvas, cx, cy, s * 0.75f, p)
            }
            IslandConfig.CONTROL_OUTLINE -> {
                strokePaint.shader = null
                strokePaint.strokeWidth = 1.2f * d
                strokePaint.color = withAlpha(Color.WHITE, a * 0.45f)
                for (x in floatArrayOf(cx - spacing, cx, cx + spacing)) canvas.drawCircle(x, cy, 23 * d, strokePaint)
                p.color = withAlpha(Color.WHITE, a)
                if (info.playing) Icons.pause(canvas, cx, cy, s * 0.62f, p) else Icons.play(canvas, cx, cy, s * 0.62f, p)
                Icons.skip(canvas, cx - spacing, cy, s * 0.7f, -1, p)
                Icons.skip(canvas, cx + spacing, cy, s * 0.7f, 1, p)
                return
            }
            else -> {
                p.color = withAlpha(Color.WHITE, a)
                if (info.playing) Icons.pause(canvas, cx, cy, s, p) else Icons.play(canvas, cx, cy, s, p)
            }
        }
        p.color = withAlpha(Color.WHITE, a)
        Icons.skip(canvas, cx - spacing, cy, s, -1, p)
        Icons.skip(canvas, cx + spacing, cy, s, 1, p)
    }

    // ---- Laden ----

    private fun drawCharging(canvas: Canvas, c: Content.Charging, a: Float) {
        val h = island.height()
        val color = if (c.level <= 20 && !c.plugged) RED else GREEN
        val fill = if (c.plugged) sFill.value.coerceIn(0f, 1f) else c.level / 100f
        val age = (System.nanoTime() - chargeStart) / 1e9f

        // Akku links
        val bw = 27 * d
        val bx = island.left + h * 0.55f + 13 * d
        p.color = withAlpha(color, a)
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = withAlpha(Color.WHITE, a * 0.45f) }
        val pulse = if (c.plugged && !cfg.reduceMotion) 0.85f + 0.15f * sin(age * 4f) else 1f
        val bolt = if (c.plugged) Paint(p).apply { this.color = withAlpha(Color.WHITE, a * pulse) } else null
        Icons.battery(canvas, bx, island.centerY(), bw, fill, body, p, bolt, d)

        // Glanz, der über die Füllung läuft
        if (c.plugged && age < 4.5f && !cfg.reduceMotion) {
            val bh = bw * 0.48f
            val l = bx - bw / 2
            val sweep = ((age * 0.9f) % 1f)
            val sx = l + bw * 0.9f * fill * sweep
            p.shader = LinearGradient(sx - 6 * d, 0f, sx + 6 * d, 0f,
                intArrayOf(0, withAlpha(Color.WHITE, a * 0.75f), 0), null, Shader.TileMode.CLAMP)
            tmp.set(l + 2.4f * d, island.centerY() - bh / 2 + 2.4f * d,
                l + 2.4f * d + (bw * 0.9f - 4.8f * d) * fill.coerceAtLeast(0.06f), island.centerY() + bh / 2 - 2.4f * d)
            canvas.drawRoundRect(tmp, bh * 0.18f, bh * 0.18f, p)
            p.shader = null
        }

        // Prozent rechts (zählt beim Einstecken hoch)
        numPaint.textSize = h * 0.42f * cfg.textScale
        val shown = if (c.plugged) (fill * 100).toInt().coerceAtMost(c.level) else c.level
        rightText(canvas, "$shown%", numPaint, color, a)
    }

    // ---- Lautlos ----

    private fun drawRinger(canvas: Canvas, c: Content.Ringer, a: Float) {
        val h = island.height()
        val pillH = h * 0.66f
        val pillW = pillH * 1.55f
        val left = island.left + (h - pillH) / 2 + 2 * d
        tmp.set(left, island.centerY() - pillH / 2, left + pillW, island.centerY() + pillH / 2)
        if (c.silent) {
            p.color = withAlpha(RED, a)
            canvas.drawRoundRect(tmp, pillH / 2, pillH / 2, p)
            gapPaint.color = RED
            gapPaint.alpha = (a * 255).toInt()
        } else {
            p.color = withAlpha(0xFF3A3A3C.toInt(), a)
            canvas.drawRoundRect(tmp, pillH / 2, pillH / 2, p)
        }
        p.color = withAlpha(Color.WHITE, a)
        val bellCx = tmp.centerX()
        val bellCy = tmp.centerY()
        Icons.bell(canvas, bellCx, bellCy, pillH * 0.62f, p, c.silent, if (c.silent) gapPaint else null)
        val txt = when {
            c.silent -> "Lautlos"
            c.vibrate -> "Vibrieren"
            else -> "Klingeln"
        }
        boldPaint.textSize = h * 0.4f * cfg.textScale
        rightText(canvas, txt, boldPaint, if (c.silent) RED else Color.WHITE, a)
    }

    // ---- Timer ----

    private fun drawTimerCompact(canvas: Canvas, c: Content.Timer, a: Float) {
        val h = island.height()
        p.color = withAlpha(ORANGE, a)
        Icons.timer(canvas, leftX(h), island.centerY(), h * 0.52f, p)
        numPaint.textSize = h * 0.44f * cfg.textScale
        rightText(canvas, c.display(), numPaint, ORANGE, a)
    }

    private fun drawTimerExpanded(canvas: Canvas, c: Content.Timer, a: Float, interactive: Boolean) {
        val pad = 20 * d
        val cy = island.centerY()
        val s = 52 * d
        val cx0 = island.left + pad + s / 2
        p.color = withAlpha(ORANGE, a * 0.22f)
        canvas.drawCircle(cx0, cy, s / 2, p)
        p.color = withAlpha(ORANGE, a)
        Icons.timer(canvas, cx0, cy, s * 0.5f, p)
        val tx = island.left + pad + s + 14 * d
        textPaint.textSize = spx(13f)
        textPaint.color = withAlpha(GRAY, a)
        canvas.drawText(ellipsize(c.title, textPaint, 140 * d), tx, cy - 9 * d, textPaint)
        numPaint.textSize = spx(30f)
        numPaint.color = withAlpha(ORANGE, a)
        canvas.drawText(c.display(), tx, cy + 22 * d, numPaint)
        val left = tx + numPaint.measureText("00:00") + 12 * d
        pillRow(canvas, c.actions, left, island.right - pad, cy, a, interactive, tint = ORANGE, equal = false)
        if (interactive && c.open != null) { /* Tipp auf Rest öffnet */ }
    }

    // ---- Anruf ----

    private fun avatar(canvas: Canvas, bmp: Bitmap?, cx: Float, cy: Float, s: Float, name: String, a: Float) {
        tmp.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
        if (!drawBitmapRound(canvas, RectF(tmp), bmp, s / 2, a)) {
            p.color = withAlpha(0xFF636366.toInt(), a)
            canvas.drawCircle(cx, cy, s / 2, p)
            boldPaint.textSize = s * 0.42f
            boldPaint.color = withAlpha(Color.WHITE, a)
            val ch = name.trim().take(1).uppercase()
            canvas.drawText(ch, cx - boldPaint.measureText(ch) / 2, cy + boldPaint.textSize * 0.36f, boldPaint)
        }
    }

    private fun callDuration(c: Content.Call): String =
        if (c.since > 0) formatClock((System.currentTimeMillis() - c.since).coerceAtLeast(0)) else "Verbunden"

    private fun drawCallCompact(canvas: Canvas, c: Content.Call, a: Float) {
        val h = island.height()
        p.color = withAlpha(GREEN, a)
        Icons.phone(canvas, leftX(h), island.centerY(), h * 0.5f, p)
        numPaint.textSize = h * 0.42f * cfg.textScale
        rightText(canvas, callDuration(c), numPaint, GREEN, a)
    }

    private fun drawCallIncoming(canvas: Canvas, c: Content.Call, a: Float, interactive: Boolean) {
        val h = island.height()
        val s = min(50 * d, h - 26 * d)
        val pad = (h - s) / 2
        val cy = island.centerY()
        avatar(canvas, c.avatar, island.left + pad + s / 2, cy, s, c.name, a)
        val br = 23 * d
        val acceptX = island.right - pad - br
        val declineX = acceptX - br * 2 - 14 * d
        val tx = island.left + pad + s + 12 * d
        val avail = declineX - br - 10 * d - tx
        textPaint.textSize = spx(13f)
        textPaint.color = withAlpha(GRAY, a)
        canvas.drawText(ellipsize(c.app, textPaint, avail), tx, cy - 6 * d, textPaint)
        boldPaint.textSize = spx(17f)
        boldPaint.color = withAlpha(Color.WHITE, a)
        canvas.drawText(ellipsize(c.name, boldPaint, avail), tx, cy + 16 * d, boldPaint)
        circleButton(canvas, declineX, cy, br, RED, a, interactive, c.decline) { x, y, r ->
            Icons.phone(canvas, x, y, r * 1.0f, p, 135f)
        }
        // sanftes Pulsieren beim Annehmen-Knopf
        val pulse = 1f + 0.06f * (0.5f + 0.5f * sin(System.nanoTime() / 1e9f * 5f))
        circleButton(canvas, acceptX, cy, br * pulse, GREEN, a, interactive, c.accept) { x, y, r ->
            Icons.phone(canvas, x, y, r * 0.95f, p)
        }
    }

    private fun drawCallExpanded(canvas: Canvas, c: Content.Call, a: Float, interactive: Boolean) {
        val pad = 20 * d
        val cy = island.centerY()
        val s = 52 * d
        avatar(canvas, c.avatar, island.left + pad + s / 2, cy, s, c.name, a)
        val br = 25 * d
        val hx = island.right - pad - br
        val tx = island.left + pad + s + 14 * d
        val avail = hx - br - 12 * d - tx
        boldPaint.textSize = spx(17f)
        boldPaint.color = withAlpha(Color.WHITE, a)
        canvas.drawText(ellipsize(c.name, boldPaint, avail), tx, cy - 3 * d, boldPaint)
        numPaint.textSize = spx(15f)
        numPaint.color = withAlpha(GREEN, a)
        canvas.drawText(callDuration(c), tx, cy + 19 * d, numPaint)
        circleButton(canvas, hx, cy, br, RED, a, interactive, c.decline) { x, y, r ->
            Icons.phone(canvas, x, y, r, p, 135f)
        }
    }

    // ---- Navigation ----

    private fun drawNavArrow(canvas: Canvas, c: Content.Nav, cx: Float, cy: Float, s: Float, a: Float) {
        val bmp = c.arrow
        if (bmp != null && !bmp.isRecycled) {
            tmp.set(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2)
            artPaint.alpha = (a * 255).toInt()
            // Pfeil-Bilder sind meist einfarbig → weiß einfärben, damit sie auf Schwarz sichtbar sind
            artPaint.colorFilter = whiteFilter
            canvas.drawBitmap(bmp, null, tmp, artPaint)
            artPaint.colorFilter = null
        } else {
            p.color = withAlpha(Color.WHITE, a)
            Icons.turnArrow(canvas, cx, cy, s, p)
        }
    }

    private fun drawNavCompact(canvas: Canvas, c: Content.Nav, a: Float) {
        val h = island.height()
        drawNavArrow(canvas, c, leftX(h), island.centerY(), h * 0.62f, a)
        boldPaint.textSize = h * 0.42f * cfg.textScale
        val maxW = island.width() / 2 - cfg.widthDp * d / 2 - 4 * d
        rightText(canvas, ellipsize(c.primary, boldPaint, maxW), boldPaint, Color.WHITE, a)
    }

    private fun drawNavExpanded(canvas: Canvas, c: Content.Nav, a: Float) {
        val pad = 22 * d
        val cy = island.centerY()
        val s = 58 * d
        drawNavArrow(canvas, c, island.left + pad + s / 2, cy, s, a)
        val tx = island.left + pad + s + 16 * d
        val avail = island.right - pad - tx
        boldPaint.textSize = spx(26f)
        boldPaint.color = withAlpha(Color.WHITE, a)
        canvas.drawText(ellipsize(c.primary, boldPaint, avail), tx, cy - 2 * d, boldPaint)
        textPaint.textSize = spx(15f)
        textPaint.color = withAlpha(LIGHT, a)
        canvas.drawText(ellipsize(c.secondary, textPaint, avail), tx, cy + 22 * d, textPaint)
    }

    // ---- Fortschritt (Upload / Download) ----

    private fun drawProgressCompact(canvas: Canvas, c: Content.Progress, a: Float) {
        val h = island.height()
        val s = h * 0.6f
        val lx = leftX(h)
        tmp.set(lx - s / 2, island.centerY() - s / 2, lx + s / 2, island.centerY() + s / 2)
        drawBitmapRound(canvas, RectF(tmp), c.icon, s * 0.24f, a)
        val rs = h * 0.56f
        val rx = rightEdge(h) - rs / 2
        if (c.done) {
            p.color = withAlpha(GREEN, a)
            canvas.drawCircle(rx, island.centerY(), rs / 2, p)
            p.color = withAlpha(Color.BLACK, a)
            Icons.check(canvas, rx, island.centerY(), rs * 0.55f, p)
        } else {
            drawRing(canvas, rx, island.centerY(), rs, c.progress, BLUE, a)
        }
    }

    private fun drawProgressExpanded(canvas: Canvas, c: Content.Progress, a: Float) {
        val pad = 20 * d
        val s = 44 * d
        val top = island.top + pad
        drawBitmapRound(canvas, RectF(island.left + pad, top, island.left + pad + s, top + s), c.icon, 11 * d, a)
        val tx = island.left + pad + s + 12 * d
        val pct = when {
            c.done -> "Fertig"
            c.progress < 0 -> "…"
            else -> "${(c.progress * 100).toInt()} %"
        }
        numPaint.textSize = spx(17f)
        val pw = numPaint.measureText(pct)
        numPaint.color = withAlpha(if (c.done) GREEN else BLUE, a)
        canvas.drawText(pct, island.right - pad - pw, top + 28 * d, numPaint)
        val avail = island.right - pad - pw - 12 * d - tx
        boldPaint.textSize = spx(15.5f)
        boldPaint.color = withAlpha(Color.WHITE, a)
        canvas.drawText(ellipsize(c.title, boldPaint, avail), tx, top + 18 * d, boldPaint)
        textPaint.textSize = spx(13f)
        textPaint.color = withAlpha(GRAY, a)
        canvas.drawText(ellipsize(c.app, textPaint, avail), tx, top + 37 * d, textPaint)
        val by = island.bottom - pad - 3 * d
        val f = if (c.done) 1f else if (c.progress < 0) {
            0.25f + 0.25f * sin(System.nanoTime() / 1e9f * 3f)
        } else c.progress
        drawBar(canvas, island.left + pad, island.right - pad, by, f, if (c.done) GREEN else BLUE, a, 6 * d, !c.done)
    }

    // ---- Entsperren ----

    private var unlockStart = 0L
    private var unlockFor: Content? = null

    private fun drawUnlock(canvas: Canvas, a: Float) {
        if (unlockFor !== drawn) {
            unlockFor = drawn
            unlockStart = System.nanoTime()
        }
        val t = ((System.nanoTime() - unlockStart) / 1e9f)
        // 0.0–0.35 s: zu, kurzer „Wackler“; danach springt der Bügel auf
        val open = if (cfg.reduceMotion) 1f else ((t - 0.35f) / 0.3f).coerceIn(0f, 1f)
        val wobble = if (t < 0.35f && !cfg.reduceMotion) sin(t * 40f) * 2f * d * (1f - t / 0.35f) else 0f
        val h = island.height()
        p.color = withAlpha(Color.WHITE, a)
        Icons.lock(canvas, island.right - h * 0.55f + wobble, island.centerY() + h * 0.04f, h * 0.5f, springOut(open), p)
    }

    /** kleiner Überschwinger für den Schloss-Bügel */
    private fun springOut(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val inv = 1f - x
        return 1f - inv * inv * inv * kotlin.math.cos(x * Math.PI.toFloat() * 1.5f)
    }

    // ---- Lautstärke / Taschenlampe / Fokus / Flugmodus ----

    private fun drawVolume(canvas: Canvas, c: VolumeContent, a: Float) {
        val h = island.height()
        val lvl = if (c.muted) 0f else c.level.coerceIn(0f, 1f)
        p.color = withAlpha(Color.WHITE, a)
        val waves = if (c.muted || lvl == 0f) 0 else if (lvl < 0.34f) 1 else if (lvl < 0.67f) 2 else 3
        Icons.speaker(canvas, leftX(h) + 2 * d, island.centerY(), h * 0.58f, waves, c.muted, p)
        val r = rightEdge(h)
        val bwid = min(78 * d, island.width() / 2 - cfg.widthDp * d / 2 + 4 * d)
        drawBar(canvas, r - bwid, r, island.centerY(), lvl, Color.WHITE, a, 6 * d, false)
    }

    private fun drawTorch(canvas: Canvas, a: Float) {
        val h = island.height()
        val y = 0xFFFFD60A.toInt()
        p.color = withAlpha(y, a)
        Icons.flashlight(canvas, leftX(h), island.centerY(), h * 0.56f, p)
        boldPaint.textSize = h * 0.4f * cfg.textScale
        rightText(canvas, "An", boldPaint, y, a)
    }

    private fun drawFocus(canvas: Canvas, c: FocusContent, a: Float) {
        val h = island.height()
        val col = if (c.on) 0xFF7D7AFF.toInt() else GRAY
        p.color = withAlpha(col, a)
        Icons.moon(canvas, leftX(h), island.centerY(), h * 0.56f, p)
        boldPaint.textSize = h * 0.4f * cfg.textScale
        rightText(canvas, if (c.on) "Fokus an" else "Fokus aus", boldPaint, col, a)
    }

    private fun drawAirplane(canvas: Canvas, c: AirplaneContent, a: Float) {
        val h = island.height()
        val col = if (c.on) ORANGE else GRAY
        p.color = withAlpha(col, a)
        Icons.airplane(canvas, leftX(h), island.centerY(), h * 0.56f, p)
        boldPaint.textSize = h * 0.4f * cfg.textScale
        rightText(canvas, if (c.on) "An" else "Aus", boldPaint, col, a)
    }

    // ---- Leere Pille: Uhrzeit / Akku ----

    private fun drawIdleContent(canvas: Canvas) {
        val ic = cfg.idleContent
        if (ic == IslandConfig.IDLE_NONE) return
        val h = island.height()
        val a = (sW.value / max(1f, sW.target)).coerceIn(0f, 1f).let { it * it }
        if (a < 0.2f) return
        numPaint.textSize = h * 0.38f * cfg.textScale
        if (ic == IslandConfig.IDLE_TIME || ic == IslandConfig.IDLE_BOTH) {
            numPaint.color = withAlpha(Color.WHITE, a)
            val t = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())
            canvas.drawText(t, island.left + h * 0.45f, island.centerY() + numPaint.textSize * 0.36f, numPaint)
        }
        if ((ic == IslandConfig.IDLE_BATTERY || ic == IslandConfig.IDLE_BOTH) && idleBattery >= 0 && !privacy.any) {
            val col = if (idleBattery <= 20) RED else Color.WHITE
            val txt = "$idleBattery%"
            numPaint.color = withAlpha(col, a)
            canvas.drawText(txt, island.right - h * 0.45f - numPaint.measureText(txt), island.centerY() + numPaint.textSize * 0.36f, numPaint)
        }
    }

    // ---- Kopfhörer ----

    private fun drawDevice(canvas: Canvas, c: Content.Device, a: Float) {
        val h = island.height()
        p.color = withAlpha(Color.WHITE, a)
        Icons.headphones(canvas, leftX(h), island.centerY(), h * 0.52f, p)
        if (c.connected && c.battery in 0..100) {
            val rs = h * 0.56f
            val rx = rightEdge(h) - rs / 2
            val color = if (c.battery <= 20) RED else GREEN
            drawRing(canvas, rx, island.centerY(), rs, c.battery / 100f, color, a)
            numPaint.textSize = h * 0.36f * cfg.textScale
            numPaint.color = withAlpha(color, a)
            val t = "${c.battery}%"
            canvas.drawText(t, rx - rs / 2 - 6 * d - numPaint.measureText(t), island.centerY() + numPaint.textSize * 0.36f, numPaint)
        } else {
            boldPaint.textSize = h * 0.38f * cfg.textScale
            rightText(canvas, if (c.connected) "Verbunden" else "Getrennt", boldPaint, if (c.connected) GREEN else GRAY, a)
        }
    }

    // ---- Benachrichtigung ----

    private fun drawNotif(canvas: Canvas, n: Content.Notif, a: Float) {
        val h = island.height()
        val icon = min(46 * d, h - 22 * d)
        val left = island.left + (h - icon) / 2
        val iconRect = RectF(left, island.centerY() - icon / 2, left + icon, island.centerY() + icon / 2)
        if (!drawBitmapRound(canvas, iconRect, n.icon, icon * 0.24f, a)) {
            p.color = withAlpha(0xFF3A3A3C.toInt(), a)
            canvas.drawRoundRect(iconRect, icon * 0.24f, icon * 0.24f, p)
        }

        val tx = iconRect.right + 12 * d
        textPaint.textSize = spx(12f)
        val time = "jetzt"
        val timeW = textPaint.measureText(time)
        val right = island.right - h * 0.42f
        val avail = right - tx - timeW - 8 * d

        boldPaint.textSize = spx(15f)
        boldPaint.color = withAlpha(Color.WHITE, a)
        val titleY = island.centerY() - 3 * d
        canvas.drawText(ellipsize(n.title.ifBlank { n.app }, boldPaint, avail), tx, titleY, boldPaint)

        textPaint.color = withAlpha(GRAY, a)
        canvas.drawText(time, right - timeW, titleY, textPaint)

        textPaint.textSize = spx(13.5f)
        textPaint.color = withAlpha(LIGHT, a)
        canvas.drawText(ellipsize(n.text.ifBlank { n.app }, textPaint, right - tx), tx, island.centerY() + 17 * d, textPaint)
    }

    private fun bodyLayout(n: Content.Notif, width: Float): StaticLayout {
        val w = max(1, width.toInt())
        val key = n.id + "|" + w + "|" + cfg.textScale
        notifLayout?.let { if (notifLayoutKey == key) return it }
        val tp = TextPaint(textPaint).apply { textSize = spx(15f); color = Color.WHITE }
        val text = n.text.ifBlank { n.app }
        val l = StaticLayout.Builder.obtain(text, 0, text.length, tp, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(1.5f * d, 1f)
            .setMaxLines(6)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        notifLayout = l
        notifLayoutKey = key
        return l
    }

    private fun notifExpandedHeight(n: Content.Notif, full: Float): Float {
        val pad = 20 * d
        val body = bodyLayout(n, full - pad * 2)
        var h = pad + 24 * d + 10 * d + 22 * d + body.height + pad
        if (n.actions.isNotEmpty()) h += 14 * d + 36 * d
        return h
    }

    private fun drawNotifExpanded(canvas: Canvas, n: Content.Notif, a: Float, interactive: Boolean) {
        val pad = 20 * d
        var y = island.top + pad
        val iconS = 24 * d
        drawBitmapRound(canvas, RectF(island.left + pad, y, island.left + pad + iconS, y + iconS), n.icon, 6 * d, a)
        textPaint.textSize = spx(13f)
        textPaint.color = withAlpha(GRAY, a)
        canvas.drawText(n.app.uppercase(), island.left + pad + iconS + 8 * d, y + iconS * 0.68f, textPaint)
        val time = "jetzt"
        canvas.drawText(time, island.right - pad - textPaint.measureText(time), y + iconS * 0.68f, textPaint)
        y += iconS + 10 * d
        boldPaint.textSize = spx(16f)
        boldPaint.color = withAlpha(Color.WHITE, a)
        canvas.drawText(ellipsize(n.title.ifBlank { n.app }, boldPaint, island.width() - pad * 2), island.left + pad, y + 16 * d, boldPaint)
        y += 22 * d
        val body = bodyLayout(n, island.width() - pad * 2)
        canvas.save()
        canvas.translate(island.left + pad, y)
        body.paint.alpha = (a * 235).toInt()
        body.draw(canvas)
        canvas.restore()
        y += body.height
        if (n.actions.isNotEmpty()) {
            pillRow(canvas, n.actions, island.left + pad, island.right - pad, y + 14 * d + 18 * d, a, interactive)
        }
    }

    // ---- Helfer ----

    private fun ellipsize(s: String, paint: Paint, w: Float): String {
        if (w <= 0) return ""
        val tp = if (paint is TextPaint) paint else TextPaint(paint)
        return TextUtils.ellipsize(s.replace('\n', ' '), tp, w, TextUtils.TruncateAt.END).toString()
    }

    private fun withAlpha(color: Int, a: Float): Int {
        val base = Color.alpha(color) / 255f
        return Color.argb((base * a.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    // =====================================================================
    // Touch
    // =====================================================================

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_OUTSIDE -> {
                listener?.onOutside()
                return false
            }
            MotionEvent.ACTION_DOWN -> {
                if (sAlpha.target == 0f) {
                    listener?.onOutside()
                    return false
                }
                val bubbleHit = sBubble.value > 0.6f && RectF(bubble).apply { inset(-8 * d, -8 * d) }.contains(e.x, e.y)
                val hitRect = RectF(island).apply { inset(-6 * d, -6 * d) }
                if (!bubbleHit && !hitRect.contains(e.x, e.y)) {
                    listener?.onOutside()
                    return false
                }
                downX = e.x; downY = e.y
                longFired = false; swiped = false
                if (bubbleHit) {
                    bubblePressed = true
                    return true
                }
                pressed = true
                val m = drawn as? Content.Music
                if (seekActive && m != null && seekRect.contains(e.x, e.y)) {
                    seeking = true
                    seekFrac = ((e.x - seekRect.left) / seekRect.width()).coerceIn(0f, 1f)
                    kick()
                    return true
                }
                if (mode != Mode.EXPANDED && cfg.squish) sPress.target = 0.95f
                kick()
                postDelayed(longPress, 420)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (seeking) {
                    seekFrac = ((e.x - seekRect.left) / seekRect.width()).coerceIn(0f, 1f)
                    kick()
                    return true
                }
                if (!pressed) return bubblePressed
                val dy = e.y - downY
                if (abs(e.x - downX) > 12 * d || abs(dy) > 12 * d) removeCallbacks(longPress)
                if (!swiped && dy < -28 * d) {
                    swiped = true
                    sPress.target = 1f
                    kick()
                    listener?.onSwipeUp()
                } else if (!swiped && dy > 28 * d && mode != Mode.EXPANDED) {
                    swiped = true
                    sPress.target = 1f
                    kick()
                    listener?.onSwipeDown()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                if (bubblePressed) {
                    bubblePressed = false
                    if (RectF(bubble).apply { inset(-12 * d, -12 * d) }.contains(e.x, e.y)) listener?.onBubbleTap()
                    return true
                }
                if (seeking) {
                    seeking = false
                    val m = drawn as? Content.Music
                    if (m != null && m.info.durationMs > 0) listener?.onSeek((seekFrac * m.info.durationMs).toLong())
                    kick()
                    return true
                }
                val wasPressed = pressed
                pressed = false
                sPress.target = 1f
                kick()
                if (wasPressed && !longFired && !swiped) {
                    val h = hits.lastOrNull { it.rect.contains(e.x, e.y) }
                    if (h != null) listener?.onHit(h.run) else listener?.onIslandTap()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                pressed = false
                bubblePressed = false
                seeking = false
                sPress.target = 1f
                kick()
                return true
            }
        }
        return false
    }
}
