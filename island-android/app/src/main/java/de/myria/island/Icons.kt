package de.myria.island

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Handgezeichnete SF-Symbol-artige Icons. Alles in einem Quadrat (cx, cy, size). */
object Icons {
    private val path = Path()
    private val rect = RectF()

    fun play(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val h = s * 0.92f
        val w = h * 0.86f
        val left = cx - w * 0.42f
        path.reset()
        path.moveTo(left, cy - h / 2)
        path.lineTo(left + w, cy)
        path.lineTo(left, cy + h / 2)
        path.close()
        roundJoin(p) { c.drawPath(path, p) }
    }

    fun pause(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val h = s * 0.9f
        val bw = s * 0.27f
        val gap = s * 0.2f
        rect.set(cx - gap / 2 - bw, cy - h / 2, cx - gap / 2, cy + h / 2)
        c.drawRoundRect(rect, bw * 0.3f, bw * 0.3f, p)
        rect.set(cx + gap / 2, cy - h / 2, cx + gap / 2 + bw, cy + h / 2)
        c.drawRoundRect(rect, bw * 0.3f, bw * 0.3f, p)
    }

    /** dir = +1 vorwärts, -1 zurück */
    fun skip(c: Canvas, cx: Float, cy: Float, s: Float, dir: Int, p: Paint) {
        val h = s * 0.62f
        val w = s * 0.5f
        for (i in 0..1) {
            val start = cx - w + i * w
            path.reset()
            if (dir > 0) {
                path.moveTo(start, cy - h / 2)
                path.lineTo(start + w, cy)
                path.lineTo(start, cy + h / 2)
            } else {
                val x = cx + w - i * w
                path.moveTo(x, cy - h / 2)
                path.lineTo(x - w, cy)
                path.lineTo(x, cy + h / 2)
            }
            path.close()
            roundJoin(p) { c.drawPath(path, p) }
        }
    }

    fun bolt(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val x = cx - s / 2
        val y = cy - s / 2
        path.reset()
        path.moveTo(x + s * 0.58f, y + s * 0.02f)
        path.lineTo(x + s * 0.18f, y + s * 0.56f)
        path.lineTo(x + s * 0.48f, y + s * 0.56f)
        path.lineTo(x + s * 0.40f, y + s * 0.98f)
        path.lineTo(x + s * 0.82f, y + s * 0.42f)
        path.lineTo(x + s * 0.52f, y + s * 0.42f)
        path.close()
        roundJoin(p) { c.drawPath(path, p) }
    }

    /** Batterie mit Füllstand 0..1, optional mit Blitz. */
    fun battery(
        c: Canvas, cx: Float, cy: Float, w: Float, level: Float,
        body: Paint, fill: Paint, boltPaint: Paint?, density: Float,
    ) {
        val h = w * 0.48f
        val stroke = 1.2f * density
        val l = cx - w / 2
        val t = cy - h / 2
        val bw = w * 0.9f
        val oldStyle = body.style
        body.style = Paint.Style.STROKE
        body.strokeWidth = stroke
        rect.set(l, t, l + bw, t + h)
        c.drawRoundRect(rect, h * 0.3f, h * 0.3f, body)
        body.style = Paint.Style.FILL
        rect.set(l + bw + density, cy - h * 0.18f, l + w, cy + h * 0.18f)
        c.drawRoundRect(rect, density, density, body)
        body.style = oldStyle
        val inset = stroke + 1.2f * density
        val maxW = bw - inset * 2
        rect.set(l + inset, t + inset, l + inset + maxW * level.coerceIn(0.06f, 1f), t + h - inset)
        c.drawRoundRect(rect, h * 0.18f, h * 0.18f, fill)
        if (boltPaint != null) bolt(c, l + bw / 2, cy, h * 0.95f, boltPaint)
    }

    fun bell(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, slashed: Boolean, slashGap: Paint?) {
        val x = cx - s / 2
        val y = cy - s / 2
        path.reset()
        path.moveTo(x + s * 0.2f, y + s * 0.74f)
        path.lineTo(x + s * 0.2f, y + s * 0.46f)
        rect.set(x + s * 0.2f, y + s * 0.12f, x + s * 0.8f, y + s * 0.78f)
        path.arcTo(rect, 180f, 180f, false)
        path.lineTo(x + s * 0.8f, y + s * 0.74f)
        path.lineTo(x + s * 0.9f, y + s * 0.82f)
        path.lineTo(x + s * 0.1f, y + s * 0.82f)
        path.close()
        roundJoin(p) { c.drawPath(path, p) }
        c.drawCircle(x + s * 0.5f, y + s * 0.9f, s * 0.08f, p)
        c.drawCircle(x + s * 0.5f, y + s * 0.11f, s * 0.06f, p)
        if (slashed) {
            val oldStyle = p.style
            val oldW = p.strokeWidth
            val oldCap = p.strokeCap
            if (slashGap != null) {
                slashGap.style = Paint.Style.STROKE
                slashGap.strokeWidth = s * 0.2f
                slashGap.strokeCap = Paint.Cap.ROUND
                c.drawLine(x + s * 0.12f, y + s * 0.06f, x + s * 0.9f, y + s * 0.96f, slashGap)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.09f
            p.strokeCap = Paint.Cap.ROUND
            c.drawLine(x + s * 0.12f, y + s * 0.06f, x + s * 0.9f, y + s * 0.96f, p)
            p.style = oldStyle
            p.strokeWidth = oldW
            p.strokeCap = oldCap
        }
    }

    fun note(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val x = cx - s / 2
        val y = cy - s / 2
        c.drawCircle(x + s * 0.3f, y + s * 0.78f, s * 0.16f, p)
        c.drawCircle(x + s * 0.78f, y + s * 0.66f, s * 0.16f, p)
        path.reset()
        path.moveTo(x + s * 0.4f, y + s * 0.78f)
        path.lineTo(x + s * 0.4f, y + s * 0.2f)
        path.lineTo(x + s * 0.94f, y + s * 0.06f)
        path.lineTo(x + s * 0.94f, y + s * 0.66f)
        path.lineTo(x + s * 0.86f, y + s * 0.66f)
        path.lineTo(x + s * 0.86f, y + s * 0.2f)
        path.lineTo(x + s * 0.48f, y + s * 0.3f)
        path.lineTo(x + s * 0.48f, y + s * 0.78f)
        path.close()
        c.drawPath(path, p)
    }

    private inline fun roundJoin(p: Paint, block: () -> Unit) {
        val oldJoin = p.strokeJoin
        val oldStyle = p.style
        val oldW = p.strokeWidth
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.FILL_AND_STROKE
        p.strokeWidth = 1.5f
        block()
        p.strokeJoin = oldJoin
        p.style = oldStyle
        p.strokeWidth = oldW
    }
}
