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

    /** Telefonhörer. rotation 0 = annehmen, 135 = auflegen */
    fun phone(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, rotation: Float = 0f) {
        c.save()
        c.rotate(rotation, cx, cy)
        val x = cx - s / 2
        val y = cy - s / 2
        path.reset()
        path.moveTo(x + s * 0.22f, y + s * 0.08f)
        path.cubicTo(x + s * 0.30f, y + s * 0.06f, x + s * 0.36f, y + s * 0.12f, x + s * 0.40f, y + s * 0.22f)
        path.lineTo(x + s * 0.44f, y + s * 0.32f)
        path.cubicTo(x + s * 0.46f, y + s * 0.38f, x + s * 0.42f, y + s * 0.42f, x + s * 0.36f, y + s * 0.46f)
        path.cubicTo(x + s * 0.42f, y + s * 0.58f, x + s * 0.50f, y + s * 0.66f, x + s * 0.60f, y + s * 0.70f)
        path.cubicTo(x + s * 0.62f, y + s * 0.62f, x + s * 0.68f, y + s * 0.58f, x + s * 0.74f, y + s * 0.60f)
        path.lineTo(x + s * 0.86f, y + s * 0.66f)
        path.cubicTo(x + s * 0.94f, y + s * 0.70f, x + s * 0.96f, y + s * 0.76f, x + s * 0.92f, y + s * 0.82f)
        path.lineTo(x + s * 0.84f, y + s * 0.92f)
        path.cubicTo(x + s * 0.50f, y + s * 0.98f, x + s * 0.02f, y + s * 0.50f, x + s * 0.08f, y + s * 0.16f)
        path.close()
        roundJoin(p) { c.drawPath(path, p) }
        c.restore()
    }

    fun timer(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.11f
        p.strokeCap = Paint.Cap.ROUND
        val r = s * 0.38f
        val ccy = cy + s * 0.06f
        c.drawCircle(cx, ccy, r, p)
        c.drawLine(cx, ccy, cx + r * 0.45f, ccy - r * 0.5f, p)
        c.drawLine(cx - s * 0.12f, cy - s * 0.44f, cx + s * 0.12f, cy - s * 0.44f, p)
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
    }

    fun headphones(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.1f
        p.strokeCap = Paint.Cap.ROUND
        rect.set(cx - s * 0.4f, cy - s * 0.42f, cx + s * 0.4f, cy + s * 0.38f)
        c.drawArc(rect, 180f, 180f, false, p)
        c.drawLine(cx - s * 0.4f, cy, cx - s * 0.4f, cy + s * 0.12f, p)
        c.drawLine(cx + s * 0.4f, cy, cx + s * 0.4f, cy + s * 0.12f, p)
        p.style = Paint.Style.FILL
        rect.set(cx - s * 0.46f, cy + s * 0.04f, cx - s * 0.22f, cy + s * 0.44f)
        c.drawRoundRect(rect, s * 0.08f, s * 0.08f, p)
        rect.set(cx + s * 0.22f, cy + s * 0.04f, cx + s * 0.46f, cy + s * 0.44f)
        c.drawRoundRect(rect, s * 0.08f, s * 0.08f, p)
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
    }

    /** iOS-Standortpfeil ➤ */
    fun location(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val x = cx - s / 2
        val y = cy - s / 2
        path.reset()
        path.moveTo(x + s * 0.92f, y + s * 0.08f)
        path.lineTo(x + s * 0.08f, y + s * 0.44f)
        path.lineTo(x + s * 0.50f, y + s * 0.50f)
        path.lineTo(x + s * 0.56f, y + s * 0.92f)
        path.close()
        roundJoin(p) { c.drawPath(path, p) }
    }

    fun check(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        val oldJoin = p.strokeJoin
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.16f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        path.reset()
        path.moveTo(cx - s * 0.36f, cy + s * 0.02f)
        path.lineTo(cx - s * 0.10f, cy + s * 0.28f)
        path.lineTo(cx + s * 0.38f, cy - s * 0.28f)
        c.drawPath(path, p)
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
        p.strokeJoin = oldJoin
    }

    /** Abbiegepfeil für Navigation (falls die App kein eigenes Bild liefert) */
    fun turnArrow(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        val oldJoin = p.strokeJoin
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.14f
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        path.reset()
        path.moveTo(cx - s * 0.2f, cy + s * 0.42f)
        path.lineTo(cx - s * 0.2f, cy - s * 0.02f)
        path.quadTo(cx - s * 0.2f, cy - s * 0.18f, cx, cy - s * 0.18f)
        path.lineTo(cx + s * 0.3f, cy - s * 0.18f)
        c.drawPath(path, p)
        path.reset()
        path.moveTo(cx + s * 0.12f, cy - s * 0.38f)
        path.lineTo(cx + s * 0.34f, cy - s * 0.18f)
        path.lineTo(cx + s * 0.12f, cy + s * 0.02f)
        c.drawPath(path, p)
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
        p.strokeJoin = oldJoin
    }

    fun mic(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        rect.set(cx - s * 0.16f, cy - s * 0.44f, cx + s * 0.16f, cy + s * 0.12f)
        c.drawRoundRect(rect, s * 0.16f, s * 0.16f, p)
        val oldStyle = p.style
        val oldW = p.strokeWidth
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.08f
        rect.set(cx - s * 0.3f, cy - s * 0.2f, cx + s * 0.3f, cy + s * 0.3f)
        c.drawArc(rect, 0f, 180f, false, p)
        c.drawLine(cx, cy + s * 0.3f, cx, cy + s * 0.44f, p)
        p.style = oldStyle
        p.strokeWidth = oldW
    }

    /** Vorhängeschloss; open 0..1 hebt + dreht den Bügel */
    fun lock(c: Canvas, cx: Float, cy: Float, s: Float, open: Float, p: Paint) {
        rect.set(cx - s * 0.36f, cy - s * 0.04f, cx + s * 0.36f, cy + s * 0.46f)
        c.drawRoundRect(rect, s * 0.1f, s * 0.1f, p)
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.11f
        p.strokeCap = Paint.Cap.ROUND
        c.save()
        val lift = s * 0.14f * open
        c.translate(0f, -lift)
        c.rotate(-28f * open, cx + s * 0.22f, cy - s * 0.02f)
        path.reset()
        path.moveTo(cx - s * 0.22f, cy - s * 0.02f + (if (open > 0.5f) -s * 0.12f * (open - 0.5f) * 2 else 0f))
        path.lineTo(cx - s * 0.22f, cy - s * 0.2f)
        rect.set(cx - s * 0.22f, cy - s * 0.42f, cx + s * 0.22f, cy + s * 0.02f)
        path.arcTo(rect, 180f, 180f, false)
        path.lineTo(cx + s * 0.22f, cy - s * 0.02f)
        c.drawPath(path, p)
        c.restore()
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
    }

    /** Lautsprecher; waves = 0..3 Schallbögen, muted = durchgestrichen */
    fun speaker(c: Canvas, cx: Float, cy: Float, s: Float, waves: Int, muted: Boolean, p: Paint) {
        val x = cx - s / 2
        path.reset()
        path.moveTo(x + s * 0.08f, cy - s * 0.14f)
        path.lineTo(x + s * 0.24f, cy - s * 0.14f)
        path.lineTo(x + s * 0.48f, cy - s * 0.36f)
        path.lineTo(x + s * 0.48f, cy + s * 0.36f)
        path.lineTo(x + s * 0.24f, cy + s * 0.14f)
        path.lineTo(x + s * 0.08f, cy + s * 0.14f)
        path.close()
        roundJoin(p) { c.drawPath(path, p) }
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.08f
        p.strokeCap = Paint.Cap.ROUND
        if (muted) {
            c.drawLine(x + s * 0.62f, cy - s * 0.14f, x + s * 0.9f, cy + s * 0.14f, p)
            c.drawLine(x + s * 0.9f, cy - s * 0.14f, x + s * 0.62f, cy + s * 0.14f, p)
        } else {
            for (i in 1..waves.coerceIn(0, 3)) {
                val r = s * (0.1f + 0.13f * i)
                rect.set(x + s * 0.48f - r, cy - r, x + s * 0.48f + r, cy + r)
                c.drawArc(rect, -45f, 90f, false, p)
            }
        }
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
    }

    /** Mond (Nicht stören / Fokus) */
    fun moon(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val a = Path().apply { addCircle(cx, cy, s * 0.42f, Path.Direction.CW) }
        val b = Path().apply { addCircle(cx + s * 0.24f, cy - s * 0.2f, s * 0.36f, Path.Direction.CW) }
        a.op(b, Path.Op.DIFFERENCE)
        c.drawPath(a, p)
    }

    fun airplane(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        c.save()
        c.rotate(45f, cx, cy)
        val x = cx - s / 2
        val y = cy - s / 2
        path.reset()
        path.moveTo(x + s * 0.5f, y + s * 0.02f)
        path.cubicTo(x + s * 0.56f, y + s * 0.02f, x + s * 0.57f, y + s * 0.12f, x + s * 0.57f, y + s * 0.2f)
        path.lineTo(x + s * 0.57f, y + s * 0.38f)
        path.lineTo(x + s * 0.96f, y + s * 0.6f)
        path.lineTo(x + s * 0.96f, y + s * 0.7f)
        path.lineTo(x + s * 0.57f, y + s * 0.58f)
        path.lineTo(x + s * 0.57f, y + s * 0.8f)
        path.lineTo(x + s * 0.7f, y + s * 0.9f)
        path.lineTo(x + s * 0.7f, y + s * 0.97f)
        path.lineTo(x + s * 0.5f, y + s * 0.92f)
        path.lineTo(x + s * 0.3f, y + s * 0.97f)
        path.lineTo(x + s * 0.3f, y + s * 0.9f)
        path.lineTo(x + s * 0.43f, y + s * 0.8f)
        path.lineTo(x + s * 0.43f, y + s * 0.58f)
        path.lineTo(x + s * 0.04f, y + s * 0.7f)
        path.lineTo(x + s * 0.04f, y + s * 0.6f)
        path.lineTo(x + s * 0.43f, y + s * 0.38f)
        path.lineTo(x + s * 0.43f, y + s * 0.2f)
        path.cubicTo(x + s * 0.43f, y + s * 0.12f, x + s * 0.44f, y + s * 0.02f, x + s * 0.5f, y + s * 0.02f)
        path.close()
        c.drawPath(path, p)
        c.restore()
    }

    fun flashlight(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val x = cx - s / 2
        val y = cy - s / 2
        path.reset()
        path.moveTo(x + s * 0.26f, y + s * 0.06f)
        path.lineTo(x + s * 0.74f, y + s * 0.06f)
        path.lineTo(x + s * 0.74f, y + s * 0.2f)
        path.lineTo(x + s * 0.62f, y + s * 0.4f)
        path.lineTo(x + s * 0.62f, y + s * 0.94f)
        path.lineTo(x + s * 0.38f, y + s * 0.94f)
        path.lineTo(x + s * 0.38f, y + s * 0.4f)
        path.lineTo(x + s * 0.26f, y + s * 0.2f)
        path.close()
        roundJoin(p) { c.drawPath(path, p) }
    }

    fun wifi(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, on: Boolean) {
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.11f
        p.strokeCap = Paint.Cap.ROUND
        val by = cy + s * 0.32f
        for (i in 1..3) {
            val r = s * 0.17f * i
            rect.set(cx - r, by - r, cx + r, by + r)
            c.drawArc(rect, 225f, 90f, false, p)
        }
        p.style = Paint.Style.FILL
        c.drawCircle(cx, by, s * 0.07f, p)
        if (!on) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = s * 0.1f
            c.drawLine(cx - s * 0.42f, cy - s * 0.4f, cx + s * 0.42f, cy + s * 0.42f, p)
        }
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
    }

    fun thermometer(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        rect.set(cx - s * 0.11f, cy - s * 0.46f, cx + s * 0.11f, cy + s * 0.2f)
        c.drawRoundRect(rect, s * 0.11f, s * 0.11f, p)
        c.drawCircle(cx, cy + s * 0.27f, s * 0.2f, p)
    }

    fun alarm(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.1f
        p.strokeCap = Paint.Cap.ROUND
        c.drawCircle(cx, cy + s * 0.05f, s * 0.34f, p)
        c.drawLine(cx, cy + s * 0.05f, cx, cy - s * 0.15f, p)
        c.drawLine(cx, cy + s * 0.05f, cx + s * 0.14f, cy + s * 0.13f, p)
        c.drawLine(cx - s * 0.42f, cy - s * 0.3f, cx - s * 0.26f, cy - s * 0.44f, p)
        c.drawLine(cx + s * 0.42f, cy - s * 0.3f, cx + s * 0.26f, cy - s * 0.44f, p)
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
    }

    fun sun(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        c.drawCircle(cx, cy, s * 0.2f, p)
        val oldStyle = p.style
        val oldW = p.strokeWidth
        val oldCap = p.strokeCap
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.08f
        p.strokeCap = Paint.Cap.ROUND
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            c.drawLine(cx + (s * 0.32f * Math.cos(a)).toFloat(), cy + (s * 0.32f * Math.sin(a)).toFloat(),
                cx + (s * 0.45f * Math.cos(a)).toFloat(), cy + (s * 0.45f * Math.sin(a)).toFloat(), p)
        }
        p.style = oldStyle
        p.strokeWidth = oldW
        p.strokeCap = oldCap
    }

    fun camera(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, hole: Paint) {
        rect.set(cx - s * 0.45f, cy - s * 0.28f, cx + s * 0.45f, cy + s * 0.36f)
        c.drawRoundRect(rect, s * 0.1f, s * 0.1f, p)
        rect.set(cx - s * 0.16f, cy - s * 0.4f, cx + s * 0.16f, cy - s * 0.22f)
        c.drawRoundRect(rect, s * 0.05f, s * 0.05f, p)
        c.drawCircle(cx, cy + s * 0.04f, s * 0.17f, hole)
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
