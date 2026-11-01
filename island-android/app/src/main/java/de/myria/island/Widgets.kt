package de.myria.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

/** Farbpalette im iOS-Stil, hell + dunkel. */
class Palette(val dark: Boolean) {
    val bg = if (dark) 0xFF000000.toInt() else 0xFFF2F2F7.toInt()
    val card = if (dark) 0xFF1C1C1E.toInt() else Color.WHITE
    val label = if (dark) Color.WHITE else Color.BLACK
    val secondary = if (dark) 0x99EBEBF5.toInt() else 0x993C3C43.toInt()
    val separator = if (dark) 0x5C545458 else 0x4A3C3C43
    val blue = if (dark) 0xFF0A84FF.toInt() else 0xFF007AFF.toInt()
    val green = if (dark) 0xFF30D158.toInt() else 0xFF34C759.toInt()
    val fill = if (dark) 0x5C787880 else 0x29787880
    val segBg = if (dark) 0x3D767680 else 0x1F767680
    val segSel = if (dark) 0xFF636366.toInt() else Color.WHITE
}

private fun Context.dp(v: Float) = v * resources.displayMetrics.density

/** iOS-Toggle mit Feder-Animation. */
@SuppressLint("ViewConstructor")
class IosSwitch(ctx: Context, private val pal: Palette) : View(ctx) {
    var checked = false
        private set
    var onChange: ((Boolean) -> Unit)? = null

    private val knob = Spring(0f, stiffness = 520f, dampingRatio = 0.72f, precision = 0.002f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
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
        setMeasuredDimension(context.dp(51f).toInt(), context.dp(31f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
        last = now
        val moving = knob.step(dt)
        val t = knob.value.coerceIn(0f, 1f)
        val w = width.toFloat()
        val h = height.toFloat()
        r.set(0f, 0f, w, h)
        p.color = blend(pal.fill, pal.green, t)
        c.drawRoundRect(r, h / 2, h / 2, p)
        val pad = context.dp(2f)
        val kr = h / 2 - pad
        val x = pad + kr + (w - 2 * pad - 2 * kr) * knob.value
        p.color = 0x26000000
        c.drawCircle(x, h / 2 + context.dp(1.5f), kr, p)
        p.color = Color.WHITE
        c.drawCircle(x, h / 2, kr, p)
        if (moving) postInvalidateOnAnimation() else last = 0L
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt()
        return Color.argb(
            ch(Color.alpha(a), Color.alpha(b)), ch(Color.red(a), Color.red(b)),
            ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)),
        )
    }
}

/** iOS-Slider: dünne Spur, großer weißer Knopf. */
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
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(34f).toInt())
    }

    private val knobR get() = context.dp(13.5f)

    override fun onDraw(c: Canvas) {
        val cy = height / 2f
        val l = knobR
        val rr = width - knobR
        val f = (value - min).toFloat() / (max - min)
        val tr = context.dp(2f)
        r.set(l, cy - tr, rr, cy + tr)
        p.color = pal.fill
        c.drawRoundRect(r, tr, tr, p)
        r.set(l, cy - tr, l + (rr - l) * f, cy + tr)
        p.color = pal.blue
        c.drawRoundRect(r, tr, tr, p)
        val x = l + (rr - l) * f
        p.color = 0x22000000
        c.drawCircle(x, cy + context.dp(2f), knobR, p)
        p.color = if (pal.dark) 0xFFF2F2F7.toInt() else Color.WHITE
        c.drawCircle(x, cy, knobR, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = context.dp(0.5f)
        p.color = 0x1F000000
        c.drawCircle(x, cy, knobR, p)
        p.style = Paint.Style.FILL
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

/** iOS-Segmented-Control. */
@SuppressLint("ViewConstructor")
class Segmented(ctx: Context, private val pal: Palette, labels: List<String>) : View(ctx) {
    private val labels = labels
    var selected = 0
        private set
    var onChange: ((Int) -> Unit)? = null

    private val pos = Spring(0f, stiffness = 500f, dampingRatio = 0.8f, precision = 0.002f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val r = RectF()
    private var last = 0L

    fun set(i: Int, animate: Boolean = false) {
        selected = i
        if (animate) pos.target = i.toFloat() else pos.snap(i.toFloat())
        invalidate()
    }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), context.dp(34f).toInt())
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
        last = now
        val moving = pos.step(dt)
        val w = width.toFloat()
        val h = height.toFloat()
        val rad = context.dp(9f)
        r.set(0f, 0f, w, h)
        p.color = pal.segBg
        c.drawRoundRect(r, rad, rad, p)
        val segW = (w - context.dp(4f)) / labels.size
        val inset = context.dp(2f)
        val x = inset + segW * pos.value
        r.set(x, inset, x + segW, h - inset)
        p.color = 0x14000000
        c.drawRoundRect(r.left, r.top + context.dp(1f), r.right, r.bottom + context.dp(1f), rad - inset, rad - inset, p)
        p.color = pal.segSel
        c.drawRoundRect(r, rad - inset, rad - inset, p)
        tp.textSize = context.dp(13.5f)
        tp.color = pal.label
        for (i in labels.indices) {
            val cx = inset + segW * i + segW / 2
            tp.typeface = Typeface.create("sans-serif-medium", if (i == selected) Typeface.BOLD else Typeface.NORMAL)
            c.drawText(labels[i], cx, h / 2 + tp.textSize * 0.36f, tp)
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


enum class Glyph { GEAR, PAINT, RULER, SPARKLE, ISLAND, NOTE, BELL, GRID, HAND, INFO, LOCK, LOCATION, BOLT }

/** Farbiges iOS-Settings-Icon (abgerundetes Quadrat mit weißem Symbol). */
@SuppressLint("ViewConstructor")
class GlyphIcon(ctx: Context, private val color: Int, private val glyph: Glyph) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = android.graphics.Path()

    override fun onMeasure(w: Int, h: Int) {
        val s = context.dp(29f).toInt()
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        r.set(0f, 0f, s, s)
        p.style = Paint.Style.FILL
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, s,
            lighten(color), color, android.graphics.Shader.TileMode.CLAMP)
        c.drawRoundRect(r, s * 0.23f, s * 0.23f, p)
        p.shader = null
        p.color = Color.WHITE
        val cx = s / 2
        val cy = s / 2
        val g = s * 0.62f
        when (glyph) {
            Glyph.GEAR -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.09f
                c.drawCircle(cx, cy, g * 0.28f, p)
                p.strokeCap = Paint.Cap.ROUND
                p.strokeWidth = s * 0.1f
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
                p.strokeWidth = s * 0.08f
                c.drawCircle(cx, cy, g * 0.42f, p)
                p.style = Paint.Style.FILL
                r.set(cx - g * 0.42f, cy - g * 0.42f, cx + g * 0.42f, cy + g * 0.42f)
                c.drawArc(r, 90f, 180f, true, p)
            }
            Glyph.RULER -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = s * 0.08f
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
                p.color = Color.WHITE
                r.set(cx - g * 0.5f, cy - g * 0.17f, cx + g * 0.5f, cy + g * 0.17f)
                c.drawRoundRect(r, g * 0.17f, g * 0.17f, p)
                p.color = color
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
                p.strokeWidth = s * 0.07f
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
        }
    }

    private fun lighten(c: Int): Int {
        fun ch(v: Int) = (v + (255 - v) * 0.18f).toInt()
        return Color.rgb(ch(Color.red(c)), ch(Color.green(c)), ch(Color.blue(c)))
    }
}
