package de.myria.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * Farben + Formen der Einstellungs-App, je nach Look:
 * Apple (iOS), Android (Material 3 / Material You), One UI, Nothing.
 */
class Palette(val look: Int, val dark: Boolean, ctx: Context) {
    private fun sys(ctx: Context, id: Int, fb: Int): Int =
        if (Build.VERSION.SDK_INT >= 31) try { ctx.getColor(id) } catch (_: Exception) { fb } else fb

    val isApple = look == IslandConfig.LOOK_APPLE
    val isAndroid = look == IslandConfig.LOOK_ANDROID
    val isOneUi = look == IslandConfig.LOOK_ONEUI
    val isNothing = look == IslandConfig.LOOK_NOTHING

    val bg: Int
    val card: Int
    val label: Int
    val secondary: Int
    val separator: Int
    val accent: Int
    val onAccent: Int
    val switchOn: Int
    val fill: Int
    val outline: Int
    val segBg: Int
    val segSel: Int
    val red = if (dark) 0xFFFF453A.toInt() else 0xFFFF3B30.toInt()
    val green = if (dark) 0xFF30D158.toInt() else 0xFF34C759.toInt()

    init {
        when (look) {
            IslandConfig.LOOK_ANDROID -> {
                bg = if (dark) sys(ctx, android.R.color.system_neutral1_900, 0xFF141218.toInt())
                else sys(ctx, android.R.color.system_neutral1_10, 0xFFFEF7FF.toInt())
                card = if (dark) mixC(sys(ctx, android.R.color.system_neutral1_900, 0xFF141218.toInt()),
                    sys(ctx, android.R.color.system_neutral1_800, 0xFF2B2930.toInt()), 0.6f)
                else sys(ctx, android.R.color.system_neutral1_50, 0xFFF3EDF7.toInt())
                label = if (dark) sys(ctx, android.R.color.system_neutral1_100, 0xFFE6E0E9.toInt())
                else sys(ctx, android.R.color.system_neutral1_900, 0xFF1D1B20.toInt())
                secondary = if (dark) sys(ctx, android.R.color.system_neutral2_200, 0xFFCAC4D0.toInt())
                else sys(ctx, android.R.color.system_neutral2_700, 0xFF49454F.toInt())
                accent = if (dark) sys(ctx, android.R.color.system_accent1_200, 0xFFD0BCFF.toInt())
                else sys(ctx, android.R.color.system_accent1_600, 0xFF6750A4.toInt())
                onAccent = if (dark) sys(ctx, android.R.color.system_accent1_800, 0xFF381E72.toInt()) else Color.WHITE
                switchOn = accent
                fill = if (dark) sys(ctx, android.R.color.system_neutral1_800, 0xFF36343B.toInt())
                else sys(ctx, android.R.color.system_neutral1_100, 0xFFE6E0E9.toInt())
                outline = if (dark) sys(ctx, android.R.color.system_neutral2_400, 0xFF938F99.toInt())
                else sys(ctx, android.R.color.system_neutral2_500, 0xFF79747E.toInt())
                separator = 0x00000000
                segBg = 0
                segSel = if (dark) sys(ctx, android.R.color.system_accent2_700, 0xFF4A4458.toInt())
                else sys(ctx, android.R.color.system_accent2_100, 0xFFE8DEF8.toInt())
            }
            IslandConfig.LOOK_ONEUI -> {
                bg = if (dark) 0xFF010101.toInt() else 0xFFF6F6F6.toInt()
                card = if (dark) 0xFF171717.toInt() else 0xFFFCFCFC.toInt()
                label = if (dark) 0xFFFAFAFA.toInt() else 0xFF000000.toInt()
                secondary = if (dark) 0xFF8E8E8E.toInt() else 0xFF7A7A7A.toInt()
                separator = if (dark) 0x26FFFFFF else 0x1A000000
                accent = 0xFF3E91FF.toInt()
                onAccent = Color.WHITE
                switchOn = accent
                fill = if (dark) 0xFF3A3A3A.toInt() else 0xFFD0D0D0.toInt()
                outline = if (dark) 0xFF5A5A5A.toInt() else 0xFFB8B8B8.toInt()
                segBg = if (dark) 0xFF2A2A2A.toInt() else 0xFFE9E9E9.toInt()
                segSel = accent
            }
            IslandConfig.LOOK_NOTHING -> {
                bg = if (dark) 0xFF000000.toInt() else 0xFFF2F2F2.toInt()
                card = if (dark) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
                label = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
                secondary = 0xFF8A8A8A.toInt()
                separator = if (dark) 0x22FFFFFF else 0x18000000
                accent = 0xFFD71921.toInt()
                onAccent = Color.WHITE
                switchOn = accent
                fill = if (dark) 0xFF2A2A2A.toInt() else 0xFFDADADA.toInt()
                outline = if (dark) 0xFF8A8A8A.toInt() else 0xFF6A6A6A.toInt()
                segBg = 0
                segSel = label
            }
            else -> {
                bg = if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
                card = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
                label = if (dark) Color.WHITE else Color.BLACK
                secondary = if (dark) 0x99EBEBF5.toInt() else 0x993C3C43.toInt()
                separator = if (dark) 0x5C545458 else 0x4A3C3C43
                accent = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()
                onAccent = Color.WHITE
                switchOn = green
                fill = if (dark) 0x5C787880 else 0x29787880
                outline = fill
                segBg = if (dark) 0x3D767680 else 0x1F767680
                segSel = if (dark) 0xFF636366.toInt() else Color.WHITE
            }
        }
    }

    /** Karten-Rundung in dp */
    val cardRadius: Float get() = when (look) {
        IslandConfig.LOOK_ANDROID -> 24f
        IslandConfig.LOOK_ONEUI -> 26f
        IslandConfig.LOOK_NOTHING -> 16f
        else -> 12f
    }

    fun font(weight: Int): Typeface {
        val family = when (look) {
            IslandConfig.LOOK_ANDROID -> "google-sans"
            IslandConfig.LOOK_NOTHING -> "monospace"
            else -> "sans-serif"
        }
        val base = Typeface.create(family, Typeface.NORMAL)
        return if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false)
        else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
    }

    companion object {
        fun mixC(a: Int, b: Int, t: Float): Int = Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
        )
    }
}

private fun Context.dp(v: Float) = v * resources.displayMetrics.density

/** Schalter – zeichnet sich je nach Look als iOS-, Material-3-, One-UI- oder Nothing-Switch. */
@SuppressLint("ViewConstructor")
class IosSwitch(ctx: Context, private val pal: Palette) : View(ctx) {
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null

    private val knob = Spring(0f, stiffness = 520f, dampingRatio = 0.72f, precision = 0.002f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()
    private var last = 0L

    fun set(v: Boolean, animate: Boolean = false) {
        checked = v
        if (animate) knob.target = if (v) 1f else 0f else knob.snap(if (v) 1f else 0f)
        invalidate()
    }

    init {
        setOnClickListener {
            set(!checked, true)
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onChange?.invoke(checked)
        }
    }

    override fun onMeasure(w: Int, h: Int) {
        val (ww, hh) = when {
            pal.isAndroid -> 52f to 32f
            pal.isOneUi -> 46f to 28f
            pal.isNothing -> 46f to 26f
            else -> 51f to 31f
        }
        setMeasuredDimension(context.dp(ww).toInt(), context.dp(hh).toInt())
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
        last = now
        val moving = knob.step(dt)
        val t = knob.value.coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()
        val dp1 = context.dp(1f)
        when {
            pal.isAndroid -> {
                // Material 3: Spur mit Rand, Daumen wächst + Häkchen
                r.set(dp1, dp1, w - dp1, h - dp1)
                p.style = Paint.Style.FILL
                p.color = Palette.mixC(pal.fill, pal.switchOn, t)
                c.drawRoundRect(r, h / 2, h / 2, p)
                if (t < 0.99f) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2 * dp1
                    p.color = Palette.mixC(pal.outline, pal.switchOn, t)
                    c.drawRoundRect(r, h / 2, h / 2, p)
                    p.style = Paint.Style.FILL
                }
                val kr = (8f + 4f * t) * dp1
                val x = h / 2 + (w - h) * knob.value
                p.color = Palette.mixC(pal.outline, pal.onAccent, t)
                c.drawCircle(x, h / 2, kr, p)
                if (t > 0.5f) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2 * dp1
                    p.strokeCap = Paint.Cap.ROUND
                    p.color = pal.switchOn
                    p.alpha = ((t - 0.5f) * 2 * 255).toInt()
                    path.reset()
                    path.moveTo(x - 4 * dp1, h / 2)
                    path.lineTo(x - 1 * dp1, h / 2 + 3 * dp1)
                    path.lineTo(x + 4.5f * dp1, h / 2 - 3 * dp1)
                    c.drawPath(path, p)
                    p.style = Paint.Style.FILL
                    p.alpha = 255
                }
            }
            pal.isOneUi -> {
                // One UI: schmale Spur, großer Daumen mit Rand
                val th = h * 0.62f
                r.set(dp1 * 3, (h - th) / 2, w - dp1 * 3, (h + th) / 2)
                p.style = Paint.Style.FILL
                p.color = Palette.mixC(pal.fill, pal.switchOn, t)
                c.drawRoundRect(r, th / 2, th / 2, p)
                val kr = h / 2 - dp1
                val x = kr + dp1 + (w - 2 * kr - 2 * dp1) * knob.value
                p.color = Color.WHITE
                c.drawCircle(x, h / 2, kr, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2.2f * dp1
                p.color = Palette.mixC(pal.outline, pal.switchOn, t)
                c.drawCircle(x, h / 2, kr - dp1, p)
                p.style = Paint.Style.FILL
            }
            pal.isNothing -> {
                // Nothing: monochrom, Punkt-Daumen
                r.set(dp1, dp1, w - dp1, h - dp1)
                p.style = Paint.Style.FILL
                p.color = Palette.mixC(Color.TRANSPARENT, pal.switchOn, t)
                c.drawRoundRect(r, h / 2, h / 2, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.4f * dp1
                p.color = Palette.mixC(pal.outline, pal.switchOn, t)
                c.drawRoundRect(r, h / 2, h / 2, p)
                p.style = Paint.Style.FILL
                val x = h / 2 + (w - h) * knob.value
                p.color = Palette.mixC(pal.label, Color.WHITE, t)
                c.drawCircle(x, h / 2, 5.5f * dp1, p)
            }
            else -> {
                r.set(0f, 0f, w, h)
                p.style = Paint.Style.FILL
                p.color = Palette.mixC(pal.fill, pal.switchOn, t)
                c.drawRoundRect(r, h / 2, h / 2, p)
                val pad = 2 * dp1
                val kr = h / 2 - pad
                val x = pad + kr + (w - 2 * pad - 2 * kr) * knob.value
                p.color = 0x26000000
                c.drawCircle(x, h / 2 + 1.5f * dp1, kr, p)
                p.color = Color.WHITE
                c.drawCircle(x, h / 2, kr, p)
            }
        }
        if (moving) postInvalidateOnAnimation() else last = 0L
    }
}

/** Regler – iOS (dünn + weißer Knopf), Material 3 (dicke Spur + Balken-Griff), One UI, Nothing (Punkte). */
@SuppressLint("ViewConstructor")
class IosSlider(ctx: Context, private val pal: Palette, val min: Int, val max: Int) : View(ctx) {
    var value = min
        private set
    var onChange: ((Int) -> Unit)? = null
    var onCommit: ((Int) -> Unit)? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var dragging = false

    fun set(v: Int) {
        value = v.coerceIn(min, max)
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(if (pal.isAndroid) 44f else 34f).toInt())
    }

    private val knobR get() = context.dp(if (pal.isAndroid) 8f else 13.5f)

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        val l = knobR
        val rr = width - knobR
        val f = if (max > min) (value - min).toFloat() / (max - min) else 0f
        val x = l + (rr - l) * f
        val dp1 = context.dp(1f)
        when {
            pal.isAndroid -> {
                val th = 16 * dp1
                val gap = 6 * dp1
                p.color = pal.accent
                if (x - gap > l) {
                    r.set(l, cy - th / 2, x - gap, cy + th / 2)
                    c.drawRoundRect(r, th / 2, th / 2, p)
                }
                p.color = pal.segSel
                if (x + gap < rr) {
                    r.set(x + gap, cy - th / 2, rr, cy + th / 2)
                    c.drawRoundRect(r, th / 2, th / 2, p)
                }
                p.color = pal.accent
                r.set(x - 2 * dp1, cy - 20 * dp1, x + 2 * dp1, cy + 20 * dp1)
                c.drawRoundRect(r, 2 * dp1, 2 * dp1, p)
            }
            pal.isNothing -> {
                val step = 9 * dp1
                var px = l
                while (px <= rr) {
                    p.color = if (px <= x) pal.accent else pal.fill
                    c.drawCircle(px, cy, 2.2f * dp1, p)
                    px += step
                }
                p.color = pal.card
                c.drawCircle(x, cy, 11 * dp1, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.6f * dp1
                p.color = pal.label
                c.drawCircle(x, cy, 10 * dp1, p)
                p.style = Paint.Style.FILL
            }
            pal.isOneUi -> {
                val tr = 3 * dp1
                r.set(l, cy - tr, rr, cy + tr)
                p.color = pal.fill
                c.drawRoundRect(r, tr, tr, p)
                r.set(l, cy - tr, x, cy + tr)
                p.color = pal.accent
                c.drawRoundRect(r, tr, tr, p)
                c.drawCircle(x, cy, 11 * dp1, p)
                p.color = Color.WHITE
                c.drawCircle(x, cy, 6 * dp1, p)
            }
            else -> {
                val tr = 2 * dp1
                r.set(l, cy - tr, rr, cy + tr)
                p.color = pal.fill
                c.drawRoundRect(r, tr, tr, p)
                r.set(l, cy - tr, x, cy + tr)
                p.color = pal.accent
                c.drawRoundRect(r, tr, tr, p)
                p.color = 0x22000000
                c.drawCircle(x, cy + 2 * dp1, knobR, p)
                p.color = if (pal.dark) 0xFFF2F2F7.toInt() else Color.WHITE
                c.drawCircle(x, cy, knobR, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.5f * dp1
                p.color = 0x1F000000
                c.drawCircle(x, cy, knobR, p)
                p.style = Paint.Style.FILL
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e.x)
            }
            MotionEvent.ACTION_MOVE -> if (dragging) update(e.x)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                onCommit?.invoke(value)
            }
        }
        return true
    }

    private fun update(x: Float) {
        val l = knobR
        val rr = width - knobR
        if (rr <= l) return
        val f = ((x - l) / (rr - l)).coerceIn(0f, 1f)
        val v = (min + f * (max - min)).roundToInt()
        if (v != value) {
            value = v
            if (v == min || v == max) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onChange?.invoke(v)
            invalidate()
        }
    }
}

/** Segment-Auswahl – iOS, Material-3-Segmented-Buttons, One-UI-Pille, Nothing-Umriss. */
@SuppressLint("ViewConstructor")
class Segmented(ctx: Context, private val pal: Palette, private val labels: List<String>) : View(ctx) {
    var selected = 0
        private set
    var onChange: ((Int) -> Unit)? = null

    private val pos = Spring(0f, stiffness = 500f, dampingRatio = 0.8f, precision = 0.002f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val clip = Path()
    private var last = 0L

    fun set(i: Int, animate: Boolean = false) {
        selected = i
        if (animate) pos.target = i.toFloat() else pos.snap(i.toFloat())
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(if (pal.isAndroid) 40f else 34f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
        last = now
        val moving = pos.step(dt)
        val w = width.toFloat()
        val h = height.toFloat()
        val dp1 = context.dp(1f)
        val n = labels.size
        tp.textSize = context.dp(if (n > 3) 12.5f else 13.5f)
        when {
            pal.isAndroid -> {
                val segW = w / n
                r.set(dp1 / 2, dp1 / 2, w - dp1 / 2, h - dp1 / 2)
                p.style = Paint.Style.FILL
                c.save()
                clip.reset()
                clip.addRoundRect(r, h / 2, h / 2, Path.Direction.CW)
                c.clipPath(clip)
                p.color = pal.segSel
                c.drawRect(segW * pos.value, 0f, segW * pos.value + segW, h, p)
                c.restore()
                p.style = Paint.Style.STROKE
                p.strokeWidth = dp1
                p.color = pal.outline
                c.drawRoundRect(r, h / 2, h / 2, p)
                for (i in 1 until n) c.drawLine(segW * i, 0f, segW * i, h, p)
                p.style = Paint.Style.FILL
                for (i in 0 until n) {
                    tp.typeface = pal.font(if (i == selected) 600 else 500)
                    tp.color = pal.label
                    val text = if (i == selected && n <= 3) "✓ " + labels[i] else labels[i]
                    c.drawText(text, segW * i + segW / 2, h / 2 + tp.textSize * 0.36f, tp)
                }
            }
            pal.isNothing -> {
                val segW = w / n
                r.set(dp1 / 2, dp1 / 2, w - dp1 / 2, h - dp1 / 2)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.2f * dp1
                p.color = pal.outline
                c.drawRoundRect(r, h / 2, h / 2, p)
                p.style = Paint.Style.FILL
                p.color = pal.segSel
                r.set(segW * pos.value + 3 * dp1, 3 * dp1, segW * pos.value + segW - 3 * dp1, h - 3 * dp1)
                c.drawRoundRect(r, h / 2, h / 2, p)
                for (i in 0 until n) {
                    tp.typeface = pal.font(500)
                    tp.color = if (i == selected) pal.card else pal.label
                    c.drawText(labels[i].uppercase(), segW * i + segW / 2, h / 2 + tp.textSize * 0.36f, tp)
                }
            }
            else -> {
                val rad = if (pal.isOneUi) h / 2 else 9 * dp1
                r.set(0f, 0f, w, h)
                p.color = pal.segBg
                c.drawRoundRect(r, rad, rad, p)
                val inset = 2 * dp1
                val segW = (w - 2 * inset) / n
                val x = inset + segW * pos.value
                r.set(x, inset, x + segW, h - inset)
                if (pal.isApple) {
                    p.color = 0x14000000
                    c.drawRoundRect(r.left, r.top + dp1, r.right, r.bottom + dp1, rad - inset, rad - inset, p)
                }
                p.color = pal.segSel
                c.drawRoundRect(r, rad - inset, rad - inset, p)
                for (i in 0 until n) {
                    val cx = inset + segW * i + segW / 2
                    tp.typeface = pal.font(if (i == selected) 600 else 400)
                    tp.color = if (pal.isOneUi && i == selected) Color.WHITE else pal.label
                    c.drawText(labels[i], cx, h / 2 + tp.textSize * 0.36f, tp)
                }
            }
        }
        if (moving) postInvalidateOnAnimation() else last = 0L
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            val segW = width.toFloat() / labels.size
            val i = (e.x / segW).toInt().coerceIn(0, labels.size - 1)
            if (i != selected) {
                set(i, true)
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onChange?.invoke(i)
            }
        }
        return true
    }
}

/** Kleine Glas-Chips für die Vorschau. */
fun glassChip(ctx: Context, text: String, onClick: () -> Unit): TextView {
    return TextView(ctx).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 13.5f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        val ph = ctx.dp(14f).toInt()
        val pv = ctx.dp(8f).toInt()
        setPadding(ph, pv, ph, pv)
        background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = ctx.dp(18f)
            setColor(0x33FFFFFF)
            setStroke(ctx.dp(0.8f).toInt(), 0x55FFFFFF)
        }
        setOnClickListener {
            performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(android.view.animation.OvershootInterpolator(3f)).start()
            }.start()
            onClick()
        }
    }
}

fun hRow(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

enum class Glyph { GEAR, PAINT, RULER, SPARKLE, ISLAND, NOTE, BELL, GRID, HAND, INFO, LOCK, LOCATION, BOLT, DROP, SPEAKER, SEARCH, APPICON }

/** Einstellungs-Icon im jeweiligen Look. */
@SuppressLint("ViewConstructor")
class GlyphIcon(ctx: Context, private val color: Int, private val glyph: Glyph, private val pal: Palette) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()

    override fun onMeasure(w: Int, h: Int) {
        val s = context.dp(if (pal.isAndroid) 36f else if (pal.isOneUi) 32f else 29f).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        r.set(0f, 0f, s, s)
        p.style = Paint.Style.FILL
        val glyphColor: Int
        when {
            pal.isAndroid -> {
                p.color = Palette.mixC(color, if (pal.dark) Color.BLACK else Color.WHITE, if (pal.dark) 0.55f else 0.78f)
                c.drawCircle(s / 2, s / 2, s / 2, p)
                glyphColor = if (pal.dark) Palette.mixC(color, Color.WHITE, 0.35f) else Palette.mixC(color, Color.BLACK, 0.25f)
            }
            pal.isOneUi -> {
                p.color = color
                c.drawRoundRect(r, s * 0.36f, s * 0.36f, p)
                glyphColor = Color.WHITE
            }
            pal.isNothing -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = context.dp(1.2f)
                p.color = pal.outline
                c.drawCircle(s / 2, s / 2, s / 2 - p.strokeWidth, p)
                p.style = Paint.Style.FILL
                glyphColor = pal.label
            }
            else -> {
                p.shader = LinearGradient(0f, 0f, 0f, s, lighten(color), color, Shader.TileMode.CLAMP)
                c.drawRoundRect(r, s * 0.23f, s * 0.23f, p)
                p.shader = null
                glyphColor = Color.WHITE
            }
        }
        p.color = glyphColor
        val cx = s / 2
        val cy = s / 2
        val g = s * (if (pal.isAndroid) 0.5f else 0.62f)
        when (glyph) {
            Glyph.GEAR -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.08f
                c.drawCircle(cx, cy, g * 0.28f, p)
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = s * 0.09f
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    val r1 = g * 0.38f
                    val r2 = g * 0.48f
                    c.drawLine(cx + (r1 * Math.cos(a)).toFloat(), cy + (r1 * Math.sin(a)).toFloat(),
                        cx + (r2 * Math.cos(a)).toFloat(), cy + (r2 * Math.sin(a)).toFloat(), p)
                }
            }
            Glyph.PAINT -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.07f
                c.drawCircle(cx, cy, g * 0.42f, p)
                p.style = Paint.Style.FILL
                r.set(cx - g * 0.42f, cy - g * 0.42f, cx + g * 0.42f, cy + g * 0.42f)
                c.drawArc(r, 90f, 180f, true, p)
            }
            Glyph.RULER -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.07f
                r.set(cx - g * 0.48f, cy - g * 0.2f, cx + g * 0.48f, cy + g * 0.2f)
                c.drawRoundRect(r, s * 0.05f, s * 0.05f, p)
                for (i in 1..4) {
                    val x = r.left + r.width() * i / 5f
                    c.drawLine(x, r.top, x, r.top + r.height() * (if (i % 2 == 0) 0.55f else 0.35f), p)
                }
            }
            Glyph.SPARKLE -> {
                fun star(x: Float, y: Float, k: Float) {
                    path.reset()
                    path.moveTo(x, y - k)
                    path.quadTo(x, y, x + k, y)
                    path.quadTo(x, y, x, y + k)
                    path.quadTo(x, y, x - k, y)
                    path.quadTo(x, y, x, y - k)
                    c.drawPath(path, p)
                }
                star(cx - g * 0.08f, cy + g * 0.06f, g * 0.38f)
                star(cx + g * 0.32f, cy - g * 0.3f, g * 0.17f)
            }
            Glyph.ISLAND -> {
                r.set(cx - g * 0.5f, cy - g * 0.17f, cx + g * 0.5f, cy + g * 0.17f)
                c.drawRoundRect(r, g * 0.17f, g * 0.17f, p)
                p.color = if (pal.isNothing || pal.isAndroid) pal.card else color
                c.drawCircle(cx + g * 0.32f, cy, g * 0.07f, p)
            }
            Glyph.NOTE -> Icons.note(c, cx, cy, g * 0.95f, p)
            Glyph.BELL -> Icons.bell(c, cx, cy, g, p, false, null)
            Glyph.GRID -> {
                val k = g * 0.4f
                val gap = g * 0.14f
                for (i in 0..1) for (j in 0..1) {
                    val x = cx - k - gap / 2 + i * (k + gap)
                    val y = cy - k - gap / 2 + j * (k + gap)
                    r.set(x, y, x + k, y + k)
                    c.drawRoundRect(r, k * 0.28f, k * 0.28f, p)
                }
            }
            Glyph.HAND -> {
                c.drawCircle(cx, cy, g * 0.16f, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.06f
                c.drawCircle(cx, cy, g * 0.36f, p)
                p.alpha = 120
                c.drawCircle(cx, cy, g * 0.5f, p)
                p.alpha = 255
            }
            Glyph.INFO -> {
                c.drawCircle(cx, cy - g * 0.32f, g * 0.08f, p)
                r.set(cx - g * 0.07f, cy - g * 0.14f, cx + g * 0.07f, cy + g * 0.42f)
                c.drawRoundRect(r, g * 0.07f, g * 0.07f, p)
            }
            Glyph.LOCK -> Icons.lock(c, cx, cy + g * 0.04f, g * 0.9f, 0f, p)
            Glyph.LOCATION -> Icons.location(c, cx, cy, g * 0.8f, p)
            Glyph.BOLT -> Icons.bolt(c, cx, cy, g * 0.9f, p)
            Glyph.SPEAKER -> Icons.speaker(c, cx, cy, g * 0.95f, 2, false, p)
            Glyph.DROP -> {
                path.reset()
                path.moveTo(cx, cy - g * 0.48f)
                path.cubicTo(cx + g * 0.12f, cy - g * 0.25f, cx + g * 0.38f, cy - g * 0.02f, cx + g * 0.38f, cy + g * 0.12f)
                path.cubicTo(cx + g * 0.38f, cy + g * 0.36f, cx + g * 0.2f, cy + g * 0.5f, cx, cy + g * 0.5f)
                path.cubicTo(cx - g * 0.2f, cy + g * 0.5f, cx - g * 0.38f, cy + g * 0.36f, cx - g * 0.38f, cy + g * 0.12f)
                path.cubicTo(cx - g * 0.38f, cy - g * 0.02f, cx - g * 0.12f, cy - g * 0.25f, cx, cy - g * 0.48f)
                path.close()
                c.drawPath(path, p)
            }
            Glyph.APPICON -> {
                // App-Symbol: abgerundetes Quadrat mit Kapsel
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.07f
                r.set(cx - g * 0.42f, cy - g * 0.42f, cx + g * 0.42f, cy + g * 0.42f)
                c.drawRoundRect(r, g * 0.18f, g * 0.18f, p)
                p.style = Paint.Style.FILL
                r.set(cx - g * 0.24f, cy - g * 0.09f, cx + g * 0.24f, cy + g * 0.09f)
                c.drawRoundRect(r, g * 0.09f, g * 0.09f, p)
            }
            Glyph.SEARCH -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.08f
                p.strokeCap = Paint.Cap.ROUND
                c.drawCircle(cx - g * 0.08f, cy - g * 0.08f, g * 0.3f, p)
                c.drawLine(cx + g * 0.14f, cy + g * 0.14f, cx + g * 0.4f, cy + g * 0.4f, p)
            }
        }
        p.style = Paint.Style.FILL
    }

    private fun lighten(c: Int): Int {
        fun ch(v: Int) = (v + (255 - v) * 0.18f).toInt()
        return Color.rgb(ch(Color.red(c)), ch(Color.green(c)), ch(Color.blue(c)))
    }
}

/** Kachel mit Handy-Oberkante + Island im jeweiligen Design (für die Design-Auswahl). */
@SuppressLint("ViewConstructor")
class DesignTile(ctx: Context, private val pal: Palette, private val design: Int, private val title: String) : View(ctx) {
    var chosen = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val ar = RectF()

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(112f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val dp1 = context.dp(1f)
        val w = width.toFloat()
        val h = height.toFloat()
        r.set(dp1 * 2, dp1 * 2, w - dp1 * 2, h - dp1 * 2)
        val (c1, c2) = when (design) {
            IslandConfig.DESIGN_ANDROID -> 0xFF3B2F63.toInt() to 0xFF8E7CC3.toInt()
            IslandConfig.DESIGN_ONEUI -> 0xFF0E2A55.toInt() to 0xFF3E91FF.toInt()
            IslandConfig.DESIGN_NOTHING -> 0xFF111111.toInt() to 0xFF3A3A3A.toInt()
            IslandConfig.DESIGN_CUSTOM -> 0xFF7B3FA0.toInt() to 0xFFF7A86B.toInt()
            else -> 0xFF2B1B5E.toInt() to 0xFFE0688F.toInt()
        }
        p.shader = LinearGradient(0f, 0f, w, h, c1, c2, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18 * dp1, 18 * dp1, p)
        p.shader = null
        val iw = w * 0.56f
        val ih = 22 * dp1
        val top = 14 * dp1
        r.set(w / 2 - iw / 2, top, w / 2 + iw / 2, top + ih)
        val rad = if (design == IslandConfig.DESIGN_NOTHING) ih * 0.36f else ih / 2
        when (design) {
            IslandConfig.DESIGN_ANDROID -> { p.color = 0xFF1D1B20.toInt(); c.drawRoundRect(r, rad, rad, p) }
            IslandConfig.DESIGN_ONEUI -> {
                p.color = 0xFF1B1B1D.toInt(); c.drawRoundRect(r, rad, rad, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 0.8f * dp1; p.color = 0x33FFFFFF
                c.drawRoundRect(r, rad, rad, p); p.style = Paint.Style.FILL
            }
            IslandConfig.DESIGN_NOTHING -> {
                p.color = Color.BLACK; c.drawRoundRect(r, rad, rad, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * dp1; p.color = 0x55FFFFFF
                c.drawRoundRect(r, rad, rad, p); p.style = Paint.Style.FILL
            }
            IslandConfig.DESIGN_CUSTOM -> {
                p.color = 0x8C2A2A30.toInt(); c.drawRoundRect(r, rad, rad, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * dp1; p.color = 0x99FFFFFF.toInt()
                c.drawRoundRect(r, rad, rad, p); p.style = Paint.Style.FILL
            }
            else -> { p.color = Color.BLACK; c.drawRoundRect(r, rad, rad, p) }
        }
        val accent = when (design) {
            IslandConfig.DESIGN_ANDROID -> 0xFFD0BCFF.toInt()
            IslandConfig.DESIGN_ONEUI -> 0xFF3E91FF.toInt()
            IslandConfig.DESIGN_NOTHING -> 0xFFD71921.toInt()
            else -> 0xFFFF6AA2.toInt()
        }
        p.color = accent
        val a = ih * 0.6f
        ar.set(r.left + ih * 0.3f, r.centerY() - a / 2, r.left + ih * 0.3f + a, r.centerY() + a / 2)
        c.drawRoundRect(ar, a * 0.25f, a * 0.25f, p)
        val heights = floatArrayOf(0.5f, 0.9f, 0.6f, 0.8f)
        for (i in 0 until 4) {
            val bh = a * heights[i]
            val x = r.right - ih * 0.4f - (4 - i) * 4 * dp1
            if (design == IslandConfig.DESIGN_NOTHING) c.drawCircle(x + dp1, r.centerY(), 1.4f * dp1, p)
            else c.drawRoundRect(x, r.centerY() - bh / 2, x + 2.2f * dp1, r.centerY() + bh / 2, dp1, dp1, p)
        }
        p.textAlign = Paint.Align.CENTER
        p.textSize = 14 * dp1
        p.typeface = pal.font(600)
        p.color = Color.WHITE
        c.drawText(title, w / 2, h - 16 * dp1, p)
        p.textAlign = Paint.Align.LEFT
        if (chosen) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3 * dp1
            p.color = pal.accent
            r.set(1.5f * dp1, 1.5f * dp1, w - 1.5f * dp1, h - 1.5f * dp1)
            c.drawRoundRect(r, 19 * dp1, 19 * dp1, p)
            p.style = Paint.Style.FILL
            c.drawCircle(w - 18 * dp1, 18 * dp1, 9 * dp1, p)
            p.color = Color.WHITE
            p.style = Paint.Style.STROKE
            p.strokeWidth = 2 * dp1
            p.strokeCap = Paint.Cap.ROUND
            c.drawLine(w - 22 * dp1, 18 * dp1, w - 19 * dp1, 21 * dp1, p)
            c.drawLine(w - 19 * dp1, 21 * dp1, w - 14 * dp1, 15 * dp1, p)
            p.style = Paint.Style.FILL
        }
    }
}
