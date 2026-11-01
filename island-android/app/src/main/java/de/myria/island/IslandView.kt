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
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Zeichnet die komplette Island selbst (Form + Inhalt) und animiert
 * Größe, Rundung, Transparenz und Inhalt mit Federn.
 */
@SuppressLint("ViewConstructor")
class IslandView(context: Context, private val topInset: Float = 0f) : View(context) {

    enum class Mode { HIDDEN, IDLE, COMPACT, EXPANDED }

    interface Listener {
        fun onIslandTap()
        fun onIslandLongPress()
        fun onOutside()
        fun onSwipeUp()
        fun onButton(button: Int)
    }

    companion object {
        const val BTN_PREV = 0
        const val BTN_PLAY = 1
        const val BTN_NEXT = 2
        private const val GREEN = 0xFF30D158.toInt()
        private const val RED = 0xFFFF453A.toInt()
        private const val GRAY = 0xFF8E8E93.toInt()
    }

    private val d = resources.displayMetrics.density
    private val sp = resources.displayMetrics.scaledDensity

    var listener: Listener? = null

    /** w, h, settled → Fenstergröße für das Overlay (nur im Service). */
    var onWindowNeed: ((Int, Int, Boolean) -> Unit)? = null

    /** Bildschirmbreite für die erweiterte Ansicht; 0 = eigene Breite */
    var hostWidthPx = 0

    var cfg = IslandConfig()
        set(v) {
            field = v
            applySpringParams()
            retarget()
            kick()
        }

    var mode = Mode.HIDDEN
        private set

    /** Inhalt, der gerade gezeichnet wird */
    private var drawn: Content? = null
    /** Inhalt, zu dem wir hin animieren */
    var target: Content? = null
        private set
    private var swapping = false

    private val sW = Spring(0f)
    private val sH = Spring(0f)
    private val sR = Spring(0f)
    private val sAlpha = Spring(0f, precision = 0.003f)
    private val sContent = Spring(0f, precision = 0.003f)
    private val sPress = Spring(1f, precision = 0.001f)
    private val springs = arrayOf(sW, sH, sR, sAlpha, sContent, sPress)

    private var lastFrame = 0L
    private var initialized = false

    // ---- Zeichen-Werkzeuge ----
    private val island = RectF()
    private val islandPath = Path()
    private val tmp = RectF()
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val artPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val boldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
    }
    private val glassFill = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0xD0303036.toInt(), 0xE0141416.toInt()), null, Shader.TileMode.CLAMP,
    )
    private val glassStroke = LinearGradient(
        0f, 0f, 0f, 1f,
        intArrayOf(0x70FFFFFF, 0x14FFFFFF, 0x30FFFFFF), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP,
    )
    private val shaderMatrix = Matrix()

    private var artShaderFor: Bitmap? = null
    private var artShader: BitmapShader? = null
    private val artMatrix = Matrix()

    private val buttonRects = Array(3) { RectF() }
    private var buttonsVisible = false

    // ---- Touch ----
    private var downX = 0f
    private var downY = 0f
    private var pressed = false
    private var longFired = false
    private var swiped = false
    private val longPress = Runnable {
        if (pressed && !swiped) {
            longFired = true
            sPress.target = 1.03f
            kick()
            listener?.onIslandLongPress()
        }
    }

    init {
        applySpringParams()
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private fun applySpringParams() {
        val k = cfg.stiffness
        val z = cfg.dampingRatio
        sW.stiffness = k; sW.dampingRatio = z
        sH.stiffness = k; sH.dampingRatio = z
        sR.stiffness = k; sR.dampingRatio = z
        sAlpha.stiffness = k * 1.4f; sAlpha.dampingRatio = 1f
        sContent.stiffness = k * 2.2f; sContent.dampingRatio = 1f
        sPress.stiffness = 600f; sPress.dampingRatio = 0.55f
    }

    // =====================================================================
    // Zustand
    // =====================================================================

    fun show(m: Mode, c: Content?) {
        val kindChanged = !sameKind(target, c)
        mode = m
        target = c
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

    private fun sameKind(a: Content?, b: Content?): Boolean {
        if (a == null || b == null) return a == null && b == null
        if (a::class != b::class) return false
        if (a is Content.Notif) return a === b
        return true
    }

    private fun expandedWidth(): Float {
        val host = if (hostWidthPx > 0) hostWidthPx.toFloat() else width.toFloat()
        val w = if (host > 0) host - 20 * d else 360 * d
        return min(w, 410 * d)
    }

    private data class Size(val w: Float, val h: Float, val r: Float)

    private fun sizeFor(m: Mode, c: Content?): Size {
        val w = cfg.widthDp * d
        val h = cfg.heightDp * d
        val full = expandedWidth()
        return when (m) {
            Mode.HIDDEN -> Size(h * 1.1f, h * 0.8f, h * 0.4f)
            Mode.IDLE -> Size(w, h, h / 2)
            Mode.COMPACT -> when (c) {
                is Content.Notif -> {
                    val nh = max(72 * d, h + 32 * d)
                    Size(full, nh, nh / 2)
                }
                is Content.Ringer -> Size(min(w + 132 * d, full), h, h / 2)
                is Content.Charging -> Size(min(w + 112 * d, full), h, h / 2)
                else -> Size(min(w + 116 * d, full), h, h / 2)
            }
            Mode.EXPANDED -> when (c) {
                is Content.Music -> Size(full, 196 * d, 44 * d)
                else -> sizeFor(Mode.COMPACT, c)
            }
        }
    }

    private fun retarget() {
        val s = sizeFor(mode, target)
        if (!initialized) {
            initialized = true
            sW.snap(s.w); sH.snap(s.h); sR.snap(s.r)
            sAlpha.snap(if (mode == Mode.HIDDEN) 0f else 1f)
        }
        sW.target = s.w
        sH.target = s.h
        sR.target = s.r
        sAlpha.target = if (mode == Mode.HIDDEN) 0f else 1f
    }

    fun kick() {
        invalidate()
    }

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

        reportWindowNeed(moving)

        val alpha = sAlpha.value.coerceIn(0f, 1f)
        if (alpha > 0.003f) drawIsland(canvas, alpha)

        val c = drawn
        val continuous = alpha > 0.003f && c is Content.Music &&
            ((c.info.playing && cfg.waveform) || mode == Mode.EXPANDED)
        when {
            moving -> postInvalidateOnAnimation()
            continuous -> postInvalidateDelayed(if (mode == Mode.EXPANDED) 16L else 33L)
            else -> lastFrame = 0L
        }
    }

    private fun reportWindowNeed(moving: Boolean) {
        val cb = onWindowNeed ?: return
        val pad = 26 * d
        val press = max(1f, sPress.value)
        val w = max(sW.value, sW.target) * press + pad * 2
        val h = max(sH.value, sH.target) * press + pad + topInset
        cb(ceil(w).toInt(), ceil(h).toInt(), !moving)
    }

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

        // --- Glow ---
        val glowColor = (c as? Content.Music)?.info?.accent
        if (cfg.glow && glowColor != null && Build.VERSION.SDK_INT >= 28) {
            bgPaint.setShadowLayer(18 * d, 0f, 3 * d, withAlpha(glowColor, 0.55f * ca))
        } else {
            bgPaint.clearShadowLayer()
        }

        // --- Form ---
        if (cfg.style == IslandConfig.STYLE_GLASS) {
            shaderMatrix.reset()
            shaderMatrix.setScale(1f, h)
            shaderMatrix.postTranslate(0f, top)
            glassFill.setLocalMatrix(shaderMatrix)
            glassStroke.setLocalMatrix(shaderMatrix)
            bgPaint.shader = glassFill
            bgPaint.color = Color.WHITE
            canvas.drawRoundRect(island, r, r, bgPaint)
            strokePaint.shader = glassStroke
            strokePaint.strokeWidth = 1f * d
            tmp.set(island)
            tmp.inset(0.5f * d, 0.5f * d)
            canvas.drawRoundRect(tmp, r, r, strokePaint)
        } else {
            bgPaint.shader = null
            bgPaint.color = Color.BLACK
            canvas.drawRoundRect(island, r, r, bgPaint)
        }
        gapPaint.color = if (cfg.style == IslandConfig.STYLE_GLASS) 0xFF1E1E21.toInt() else Color.BLACK

        // --- Inhalt ---
        buttonsVisible = false
        if (c != null && ca > 0.01f) {
            islandPath.reset()
            islandPath.addRoundRect(island, r, r, Path.Direction.CW)
            canvas.save()
            canvas.clipPath(islandPath)
            val scale = 0.9f + 0.1f * ca
            canvas.scale(scale, scale, island.centerX(), island.centerY())
            val expandedish = sH.value > sizeFor(Mode.IDLE, null).h * 1.6f
            when (c) {
                is Content.Music ->
                    if (mode == Mode.EXPANDED && expandedish) drawMusicExpanded(canvas, c.info, ca)
                    else drawMusicCompact(canvas, c.info, ca)
                is Content.Charging -> drawCharging(canvas, c, ca)
                is Content.Ringer -> drawRinger(canvas, c, ca)
                is Content.Notif -> drawNotif(canvas, c, ca)
            }
            canvas.restore()
        }

        canvas.restore()
        if (layered) canvas.restore()
    }

    // ---- Musik ----

    private fun drawArt(canvas: Canvas, rect: RectF, info: MusicInfo, radius: Float, a: Float) {
        val bmp = info.art
        if (bmp != null && !bmp.isRecycled) {
            if (artShaderFor !== bmp) {
                artShaderFor = bmp
                artShader = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
            val sh = artShader!!
            val s = max(rect.width() / bmp.width, rect.height() / bmp.height)
            artMatrix.reset()
            artMatrix.setScale(s, s)
            artMatrix.postTranslate(
                rect.centerX() - bmp.width * s / 2,
                rect.centerY() - bmp.height * s / 2,
            )
            sh.setLocalMatrix(artMatrix)
            artPaint.shader = sh
            artPaint.alpha = (a * 255).toInt()
            canvas.drawRoundRect(rect, radius, radius, artPaint)
            artPaint.shader = null
        } else {
            p.color = withAlpha(info.accent, a)
            canvas.drawRoundRect(rect, radius, radius, p)
            p.color = withAlpha(Color.WHITE, a * 0.9f)
            Icons.note(canvas, rect.centerX(), rect.centerY(), rect.width() * 0.5f, p)
        }
    }

    private fun drawWave(canvas: Canvas, right: Float, cy: Float, maxH: Float, info: MusicInfo, a: Float) {
        val bars = 5
        val bw = 2.6f * d
        val gap = 2.3f * d
        val t = System.nanoTime() / 1e9f
        p.color = withAlpha(info.accent, a)
        val freq = floatArrayOf(5.1f, 7.3f, 6.2f, 8.4f, 5.7f)
        val phase = floatArrayOf(0f, 1.3f, 2.1f, 0.7f, 2.9f)
        for (i in 0 until bars) {
            val amp = if (info.playing && cfg.waveform) {
                val s1 = 0.5f + 0.5f * sin(t * freq[i] + phase[i])
                val s2 = 0.65f + 0.35f * sin(t * freq[(i + 2) % bars] * 0.53f + phase[i] * 1.7f)
                0.22f + 0.78f * s1 * s2
            } else if (info.playing) {
                floatArrayOf(0.5f, 0.85f, 0.6f, 1f, 0.45f)[i]
            } else 0.16f
            val bh = max(bw, maxH * amp)
            val x = right - (bars - i) * bw - (bars - 1 - i) * gap
            tmp.set(x, cy - bh / 2, x + bw, cy + bh / 2)
            canvas.drawRoundRect(tmp, bw / 2, bw / 2, p)
        }
    }

    private fun waveWidth() = 5 * 2.6f * d + 4 * 2.3f * d

    private fun drawMusicCompact(canvas: Canvas, info: MusicInfo, a: Float) {
        val h = island.height()
        val art = h * 0.62f
        val pad = (h - art) / 2
        tmp.set(island.left + pad * 1.35f, island.top + pad, island.left + pad * 1.35f + art, island.top + pad + art)
        drawArt(canvas, RectF(tmp), info, art * 0.26f, a)
        drawWave(canvas, island.right - pad * 1.5f, island.centerY(), art * 0.82f, info, a)
    }

    private fun drawMusicExpanded(canvas: Canvas, info: MusicInfo, a: Float) {
        val pad = 22 * d
        val artS = 58 * d
        val artRect = RectF(island.left + pad, island.top + pad, island.left + pad + artS, island.top + pad + artS)
        drawArt(canvas, artRect, info, 13 * d, a)

        val waveRight = island.right - pad
        drawWave(canvas, waveRight, artRect.centerY(), 22 * d, info, a)

        val tx = artRect.right + 14 * d
        val avail = waveRight - waveWidth() - 12 * d - tx
        boldPaint.textSize = 16.5f * sp
        boldPaint.color = withAlpha(Color.WHITE, a)
        canvas.drawText(ellipsize(info.title, boldPaint, avail), tx, artRect.top + 25 * d, boldPaint)
        textPaint.textSize = 14.5f * sp
        textPaint.color = withAlpha(GRAY, a)
        canvas.drawText(ellipsize(info.artist, textPaint, avail), tx, artRect.top + 47 * d, textPaint)

        // Fortschritt
        val py = artRect.bottom + 26 * d
        val pos = info.positionNow()
        textPaint.textSize = 11.5f * sp
        textPaint.color = withAlpha(GRAY, a)
        val elapsed = fmt(pos)
        val remain = if (info.durationMs > 0) "-" + fmt(info.durationMs - pos) else ""
        val tw = 40 * d
        canvas.drawText(elapsed, island.left + pad, py + 4 * d, textPaint)
        canvas.drawText(remain, island.right - pad - textPaint.measureText(remain), py + 4 * d, textPaint)
        val bl = island.left + pad + tw
        val br = island.right - pad - tw
        val bh = 5.5f * d
        tmp.set(bl, py - bh / 2, br, py + bh / 2)
        p.color = withAlpha(Color.WHITE, a * 0.22f)
        canvas.drawRoundRect(tmp, bh / 2, bh / 2, p)
        if (info.durationMs > 0) {
            val f = (pos.toFloat() / info.durationMs).coerceIn(0f, 1f)
            tmp.set(bl, py - bh / 2, bl + (br - bl) * f, py + bh / 2)
            p.color = withAlpha(Color.WHITE, a)
            canvas.drawRoundRect(tmp, bh / 2, bh / 2, p)
        }

        // Steuerung
        val cy = py + 44 * d
        val cx = island.centerX()
        val spacing = 78 * d
        p.color = withAlpha(Color.WHITE, a)
        Icons.skip(canvas, cx - spacing, cy, 30 * d, -1, p)
        if (info.playing) Icons.pause(canvas, cx, cy, 30 * d, p) else Icons.play(canvas, cx, cy, 30 * d, p)
        Icons.skip(canvas, cx + spacing, cy, 30 * d, 1, p)
        val hit = 28 * d
        // Hit-Boxen in View-Koordinaten (ohne Content-Scale, der ist am Ende 1.0)
        buttonRects[BTN_PREV].set(cx - spacing - hit, cy - hit, cx - spacing + hit, cy + hit)
        buttonRects[BTN_PLAY].set(cx - hit, cy - hit, cx + hit, cy + hit)
        buttonRects[BTN_NEXT].set(cx + spacing - hit, cy - hit, cx + spacing + hit, cy + hit)
        buttonsVisible = a > 0.6f
    }

    // ---- Laden ----

    private fun drawCharging(canvas: Canvas, c: Content.Charging, a: Float) {
        val h = island.height()
        val color = if (c.level <= 20 && !c.plugged) RED else GREEN
        p.color = withAlpha(color, a)
        val body = Paint(p).apply { this.color = withAlpha(Color.WHITE, a * 0.45f) }
        val bolt = if (c.plugged) Paint(p).apply { this.color = withAlpha(Color.WHITE, a) } else null
        Icons.battery(canvas, island.left + h * 0.55f + 13 * d, island.centerY(), 27 * d, c.level / 100f, body, p, bolt, d)
        boldPaint.textSize = h * 0.42f
        boldPaint.color = withAlpha(color, a)
        val txt = "${c.level}%"
        val tw = boldPaint.measureText(txt)
        canvas.drawText(txt, island.right - h * 0.5f - tw, island.centerY() + boldPaint.textSize * 0.36f, boldPaint)
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
        Icons.bell(canvas, tmp.centerX(), tmp.centerY(), pillH * 0.62f, p, c.silent, if (c.silent) gapPaint else null)

        val txt = when {
            c.silent -> "Lautlos"
            c.vibrate -> "Vibrieren"
            else -> "Klingeln"
        }
        boldPaint.textSize = h * 0.4f
        boldPaint.color = withAlpha(if (c.silent) RED else Color.WHITE, a)
        val tw = boldPaint.measureText(txt)
        canvas.drawText(txt, island.right - h * 0.5f - tw, island.centerY() + boldPaint.textSize * 0.36f, boldPaint)
    }

    // ---- Benachrichtigung ----

    private fun drawNotif(canvas: Canvas, n: Content.Notif, a: Float) {
        val h = island.height()
        val icon = min(46 * d, h - 22 * d)
        val left = island.left + (h - icon) / 2
        val iconRect = RectF(left, island.centerY() - icon / 2, left + icon, island.centerY() + icon / 2)
        val bmp = n.icon
        if (bmp != null && !bmp.isRecycled) {
            val sh = BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            val s = max(icon / bmp.width, icon / bmp.height)
            artMatrix.reset()
            artMatrix.setScale(s, s)
            artMatrix.postTranslate(iconRect.centerX() - bmp.width * s / 2, iconRect.centerY() - bmp.height * s / 2)
            sh.setLocalMatrix(artMatrix)
            artPaint.shader = sh
            artPaint.alpha = (a * 255).toInt()
            canvas.drawRoundRect(iconRect, icon * 0.24f, icon * 0.24f, artPaint)
            artPaint.shader = null
        } else {
            p.color = withAlpha(0xFF3A3A3C.toInt(), a)
            canvas.drawRoundRect(iconRect, icon * 0.24f, icon * 0.24f, p)
        }

        val tx = iconRect.right + 12 * d
        textPaint.textSize = 12 * sp
        val time = "jetzt"
        val timeW = textPaint.measureText(time)
        val right = island.right - h * 0.42f
        val avail = right - tx - timeW - 8 * d

        val title = n.title.ifBlank { n.app }
        boldPaint.textSize = 15 * sp
        boldPaint.color = withAlpha(Color.WHITE, a)
        val titleY = island.centerY() - 3 * d
        canvas.drawText(ellipsize(title, boldPaint, avail), tx, titleY, boldPaint)

        textPaint.color = withAlpha(GRAY, a)
        canvas.drawText(time, right - timeW, titleY, textPaint)

        textPaint.textSize = 13.5f * sp
        textPaint.color = withAlpha(0xFFC7C7CC.toInt(), a)
        val body = n.text.ifBlank { n.app }
        canvas.drawText(ellipsize(body, textPaint, right - tx), tx, island.centerY() + 17 * d, textPaint)
    }

    // ---- Helfer ----

    private fun ellipsize(s: String, paint: Paint, w: Float): String {
        if (w <= 0) return ""
        val tp = android.text.TextPaint(paint)
        return TextUtils.ellipsize(s.replace('\n', ' '), tp, w, TextUtils.TruncateAt.END).toString()
    }

    private fun fmt(ms: Long): String {
        val t = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(t / 60, t % 60)
    }

    private fun withAlpha(color: Int, a: Float): Int {
        val base = Color.alpha(color) / 255f
        return Color.argb((base * a.coerceIn(0f, 1f) * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))
    }

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
                val hitRect = RectF(island).apply { inset(-6 * d, -6 * d) }
                if (sAlpha.target == 0f || !hitRect.contains(e.x, e.y)) {
                    listener?.onOutside()
                    return false
                }
                downX = e.x; downY = e.y
                pressed = true; longFired = false; swiped = false
                if (mode != Mode.EXPANDED) sPress.target = 0.95f
                kick()
                postDelayed(longPress, 420)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!pressed) return false
                val dy = e.y - downY
                if (abs(e.x - downX) > 12 * d || abs(dy) > 12 * d) removeCallbacks(longPress)
                if (!swiped && dy < -28 * d) {
                    swiped = true
                    sPress.target = 1f
                    kick()
                    listener?.onSwipeUp()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPress)
                val wasPressed = pressed
                pressed = false
                sPress.target = 1f
                kick()
                if (wasPressed && !longFired && !swiped) {
                    val b = buttonAt(e.x, e.y)
                    if (b >= 0) listener?.onButton(b) else listener?.onIslandTap()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                pressed = false
                sPress.target = 1f
                kick()
                return true
            }
        }
        return false
    }

    private fun buttonAt(x: Float, y: Float): Int {
        if (!buttonsVisible || mode != Mode.EXPANDED) return -1
        for (i in buttonRects.indices) if (buttonRects[i].contains(x, y)) return i
        return -1
    }
}
