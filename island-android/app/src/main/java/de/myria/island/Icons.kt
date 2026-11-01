package de.myria.island

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

/** Handgezeichnete SF-Symbol-artige Icons. Alles in einem Quadrat (cx, cy, size). */
object Icons {
    private val path = Path()
    private val rect = RectF()

    // Symbol-Familien – wie die Symbole des jeweiligen Systems aussehen
    const val APPLE = 0
    const val MATERIAL = 1
    const val ONEUI = 2
    const val NOTHING = 3

    /** Wird von jeder IslandView vor dem Zeichnen gesetzt. */
    var family = APPLE

    // ---- Nothing: Punktmatrix-Rendering (Glyph-Look) ----
    private var inDots = false
    private val dotCache = HashMap<String, BooleanArray>()
    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }

    /** Zeichnet ein Symbol als Punktraster – wie die Nothing-Glyphen. Ergebnis wird gecacht. */
    private fun dots(key: String, c: Canvas, cx: Float, cy: Float, s: Float, p: Paint,
                     draw: (Canvas, Float, Float, Float, Paint) -> Unit) {
        val n = (s / 3.4f).toInt().coerceIn(7, 13)
        val k = "$key/$n"
        val grid = dotCache[k] ?: run {
            val res = 6
            val size = n * res
            val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
            val bc = Canvas(bmp)
            inDots = true
            try { draw(bc, size / 2f, size / 2f, size.toFloat(), maskPaint) } finally { inDots = false }
            val px = IntArray(size * size)
            bmp.getPixels(px, 0, size, 0, 0, size, size)
            bmp.recycle()
            val g = BooleanArray(n * n)
            for (gy in 0 until n) for (gx in 0 until n) {
                var sum = 0
                for (yy in 0 until res) for (xx in 0 until res) sum += (px[(gy * res + yy) * size + gx * res + xx] ushr 24)
                g[gy * n + gx] = sum / (res * res) > 80
            }
            if (dotCache.size > 200) dotCache.clear()
            dotCache[k] = g
            g
        }
        val cell = s / n
        val r = cell * 0.36f
        val l = cx - s / 2
        val t = cy - s / 2
        for (gy in 0 until n) for (gx in 0 until n) if (grid[gy * n + gx])
            c.drawCircle(l + (gx + 0.5f) * cell, t + (gy + 0.5f) * cell, r, p)
    }

    fun play(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == NOTHING && !inDots) { dots("play", c, cx, cy, s, p) { cc, x, y, ss, pp -> play(cc, x, y, ss, pp) }; return }
        val h = s * 0.92f
        val w = h * 0.86f
        val left = cx - w * 0.42f
        path.reset()
        path.moveTo(left, cy - h / 2)
        path.lineTo(left + w, cy)
        path.lineTo(left, cy + h / 2)
        path.close()
        roundJoin(p, s) { c.drawPath(path, p) }
    }

    fun pause(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == NOTHING && !inDots) { dots("pause", c, cx, cy, s, p) { cc, x, y, ss, pp -> pause(cc, x, y, ss, pp) }; return }
        val h = s * (if (family == MATERIAL) 0.8f else 0.9f)
        val bw = s * (if (family == MATERIAL) 0.25f else 0.27f)
        val gap = s * 0.2f
        val rr = bw * when (family) { MATERIAL -> 0.5f; ONEUI -> 0.18f; else -> 0.3f }
        rect.set(cx - gap / 2 - bw, cy - h / 2, cx - gap / 2, cy + h / 2)
        c.drawRoundRect(rect, rr, rr, p)
        rect.set(cx + gap / 2, cy - h / 2, cx + gap / 2 + bw, cy + h / 2)
        c.drawRoundRect(rect, rr, rr, p)
    }

    /** dir = +1 vorwärts, -1 zurück */
    fun skip(c: Canvas, cx: Float, cy: Float, s: Float, dir: Int, p: Paint) {
        if (family == NOTHING && !inDots) { dots("skip$dir", c, cx, cy, s, p) { cc, x, y, ss, pp -> skip(cc, x, y, ss, dir, pp) }; return }
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
            roundJoin(p, s) { c.drawPath(path, p) }
        }
    }

    fun bolt(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == NOTHING && !inDots) { dots("bolt", c, cx, cy, s, p) { cc, x, y, ss, pp -> bolt(cc, x, y, ss, pp) }; return }
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
        roundJoin(p, s) { c.drawPath(path, p) }
    }

    /** Batterie mit Füllstand 0..1, optional mit Blitz – im Stil des Systems. */
    fun battery(
        c: Canvas, cx: Float, cy: Float, w: Float, level: Float,
        body: Paint, fill: Paint, boltPaint: Paint?, density: Float,
    ) {
        val lv = level.coerceIn(0f, 1f)
        when (family) {
            MATERIAL -> {
                // Pixel / Android 16: gefüllte Kapsel, Pegel als Füllung, kleiner runder Knubbel
                val h = w * 0.5f
                val l = cx - w / 2
                val bw = w * 0.9f
                rect.set(l, cy - h / 2, l + bw, cy + h / 2)
                c.drawRoundRect(rect, h * 0.42f, h * 0.42f, body)
                c.save()
                path.reset(); path.addRoundRect(rect, h * 0.42f, h * 0.42f, Path.Direction.CW)
                c.clipPath(path)
                rect.set(l, cy - h / 2, l + bw * lv.coerceAtLeast(0.08f), cy + h / 2)
                c.drawRect(rect, fill)
                c.restore()
                rect.set(l + bw + density * 0.8f, cy - h * 0.2f, l + w, cy + h * 0.2f)
                c.drawRoundRect(rect, h * 0.2f, h * 0.2f, body)
                if (boltPaint != null) bolt(c, l + bw / 2, cy, h * 0.85f, boltPaint)
            }
            ONEUI -> {
                // One UI: dünne Kontur, kantigere Ecken, Füllung mit Abstand
                val h = w * 0.46f
                val stroke = 1f * density
                val l = cx - w / 2
                val bw = w * 0.92f
                val oldStyle = body.style
                body.style = Paint.Style.STROKE
                body.strokeWidth = stroke
                rect.set(l, cy - h / 2, l + bw, cy + h / 2)
                c.drawRoundRect(rect, h * 0.22f, h * 0.22f, body)
                body.style = Paint.Style.FILL
                rect.set(l + bw + density, cy - h * 0.16f, l + w, cy + h * 0.16f)
                c.drawRect(rect, body)
                body.style = oldStyle
                val inset = stroke + 1.4f * density
                rect.set(l + inset, cy - h / 2 + inset, l + inset + (bw - inset * 2) * lv.coerceAtLeast(0.06f), cy + h / 2 - inset)
                c.drawRoundRect(rect, h * 0.1f, h * 0.1f, fill)
                if (boltPaint != null) bolt(c, l + bw / 2, cy, h * 0.9f, boltPaint)
            }
            NOTHING -> {
                // Nothing: Punktraster-Akku, Spalten leuchten je nach Ladestand
                val cols = 10
                val rows = 3
                val cell = w / (cols + 1.2f)
                val r = cell * 0.36f
                val l = cx - (cols * cell) / 2
                val t = cy - (rows * cell) / 2
                val lit = (lv * cols + 0.5f).toInt().coerceIn(1, cols)
                for (x in 0 until cols) for (y in 0 until rows)
                    c.drawCircle(l + (x + 0.5f) * cell, t + (y + 0.5f) * cell, r, if (x < lit) fill else body)
                c.drawCircle(l + (cols + 0.6f) * cell, cy, r, body)
                // Laden: die nächste Spalte blinkt (statt Blitz – sieht im Raster sauberer aus)
                if (boltPaint != null && lit < cols) for (y in 0 until rows)
                    c.drawCircle(l + (lit + 0.5f) * cell, t + (y + 0.5f) * cell, r, boltPaint)
            }
            else -> {
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
                rect.set(l + inset, t + inset, l + inset + maxW * lv.coerceIn(0.06f, 1f), t + h - inset)
                c.drawRoundRect(rect, h * 0.18f, h * 0.18f, fill)
                if (boltPaint != null) bolt(c, l + bw / 2, cy, h * 0.95f, boltPaint)
            }
        }
    }

    fun bell(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, slashed: Boolean, slashGap: Paint?) {
        if (family == NOTHING && !inDots) { dots("bell$slashed", c, cx, cy, s, p) { cc, x, y, ss, pp -> bell(cc, x, y, ss, pp, slashed, null) }; return }
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
        roundJoin(p, s) { c.drawPath(path, p) }
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
        if (family == NOTHING && !inDots) { dots("note", c, cx, cy, s, p) { cc, x, y, ss, pp -> note(cc, x, y, ss, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("phone${rotation.toInt()}", c, cx, cy, s, p) { cc, x, y, ss, pp -> phone(cc, x, y, ss, pp, rotation) }; return }
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
        roundJoin(p, s) { c.drawPath(path, p) }
        c.restore()
    }

    fun timer(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == NOTHING && !inDots) { dots("timer", c, cx, cy, s, p) { cc, x, y, ss, pp -> timer(cc, x, y, ss, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("hp", c, cx, cy, s, p) { cc, x, y, ss, pp -> headphones(cc, x, y, ss, pp) }; return }
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

    /** iOS-Standortpfeil ➤ bzw. Android-Pin */
    fun location(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == MATERIAL || family == ONEUI || inDots && family == NOTHING) {
            // Karten-Pin mit Loch (Android / One UI / Nothing)
            val r = s * 0.3f
            val hy = cy - s * 0.14f
            path.reset()
            path.fillType = Path.FillType.EVEN_ODD
            rect.set(cx - r, hy - r, cx + r, hy + r)
            path.arcTo(rect, 150f, 240f, false)
            path.lineTo(cx, cy + s * 0.46f)
            path.close()
            path.addCircle(cx, hy, r * (if (family == ONEUI) 0.32f else 0.4f), Path.Direction.CW)
            roundJoin(p, s * 0.4f) { c.drawPath(path, p) }
            path.fillType = Path.FillType.WINDING
            return
        }
        if (family == NOTHING && !inDots) { dots("loc", c, cx, cy, s, p) { cc, x, y, ss, pp -> location(cc, x, y, ss, pp) }; return }
        val x = cx - s / 2
        val y = cy - s / 2
        path.reset()
        path.moveTo(x + s * 0.92f, y + s * 0.08f)
        path.lineTo(x + s * 0.08f, y + s * 0.44f)
        path.lineTo(x + s * 0.50f, y + s * 0.50f)
        path.lineTo(x + s * 0.56f, y + s * 0.92f)
        path.close()
        roundJoin(p, s) { c.drawPath(path, p) }
    }

    fun check(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == NOTHING && !inDots) { dots("check", c, cx, cy, s, p) { cc, x, y, ss, pp -> check(cc, x, y, ss, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("turn", c, cx, cy, s, p) { cc, x, y, ss, pp -> turnArrow(cc, x, y, ss, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("mic", c, cx, cy, s, p) { cc, x, y, ss, pp -> mic(cc, x, y, ss, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("lock${(open * 4).toInt()}", c, cx, cy, s, p) { cc, x, y, ss, pp -> lock(cc, x, y, ss, open, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("spk$waves$muted", c, cx, cy, s, p) { cc, x, y, ss, pp -> speaker(cc, x, y, ss, waves, muted, pp) }; return }
        val x = cx - s / 2
        path.reset()
        path.moveTo(x + s * 0.08f, cy - s * 0.14f)
        path.lineTo(x + s * 0.24f, cy - s * 0.14f)
        path.lineTo(x + s * 0.48f, cy - s * 0.36f)
        path.lineTo(x + s * 0.48f, cy + s * 0.36f)
        path.lineTo(x + s * 0.24f, cy + s * 0.14f)
        path.lineTo(x + s * 0.08f, cy + s * 0.14f)
        path.close()
        roundJoin(p, s) { c.drawPath(path, p) }
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
        if (family == NOTHING && !inDots) { dots("moon", c, cx, cy, s, p) { cc, x, y, ss, pp -> moon(cc, x, y, ss, pp) }; return }
        val a = Path().apply { addCircle(cx, cy, s * 0.42f, Path.Direction.CW) }
        val b = Path().apply { addCircle(cx + s * 0.24f, cy - s * 0.2f, s * 0.36f, Path.Direction.CW) }
        a.op(b, Path.Op.DIFFERENCE)
        c.drawPath(a, p)
    }

    fun airplane(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == NOTHING && !inDots) { dots("plane", c, cx, cy, s, p) { cc, x, y, ss, pp -> airplane(cc, x, y, ss, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("torch", c, cx, cy, s, p) { cc, x, y, ss, pp -> flashlight(cc, x, y, ss, pp) }; return }
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
        roundJoin(p, s) { c.drawPath(path, p) }
    }

    fun wifi(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint, on: Boolean) {
        if (family == MATERIAL || family == ONEUI) {
            // Android: gefüllter Fächer
            val by = cy + s * 0.36f
            val r = s * 0.62f
            rect.set(cx - r, by - r, cx + r, by + r)
            path.reset()
            path.moveTo(cx, by)
            path.arcTo(rect, 225f, 90f, false)
            path.close()
            roundJoin(p, s * 0.5f) { c.drawPath(path, p) }
            if (!on) {
                val oldStyle = p.style; val oldW = p.strokeWidth; val oldCap = p.strokeCap
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.1f; p.strokeCap = Paint.Cap.ROUND
                c.drawLine(cx - s * 0.42f, cy - s * 0.4f, cx + s * 0.42f, cy + s * 0.42f, p)
                p.style = oldStyle; p.strokeWidth = oldW; p.strokeCap = oldCap
            }
            return
        }
        if (family == NOTHING && !inDots) { dots("wifi$on", c, cx, cy, s, p) { cc, x, y, ss, pp -> wifi(cc, x, y, ss, pp, on) }; return }
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
        if (family == NOTHING && !inDots) { dots("therm", c, cx, cy, s, p) { cc, x, y, ss, pp -> thermometer(cc, x, y, ss, pp) }; return }
        rect.set(cx - s * 0.11f, cy - s * 0.46f, cx + s * 0.11f, cy + s * 0.2f)
        c.drawRoundRect(rect, s * 0.11f, s * 0.11f, p)
        c.drawCircle(cx, cy + s * 0.27f, s * 0.2f, p)
    }

    fun alarm(c: Canvas, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == NOTHING && !inDots) { dots("alarm", c, cx, cy, s, p) { cc, x, y, ss, pp -> alarm(cc, x, y, ss, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("sun", c, cx, cy, s, p) { cc, x, y, ss, pp -> sun(cc, x, y, ss, pp) }; return }
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
        if (family == NOTHING && !inDots) { dots("cam", c, cx, cy, s, p) { cc, x, y, ss, pp -> camera(cc, x, y, ss, pp, clearPaint) }; return }
        rect.set(cx - s * 0.45f, cy - s * 0.28f, cx + s * 0.45f, cy + s * 0.36f)
        c.drawRoundRect(rect, s * 0.1f, s * 0.1f, p)
        rect.set(cx - s * 0.16f, cy - s * 0.4f, cx + s * 0.16f, cy - s * 0.22f)
        c.drawRoundRect(rect, s * 0.05f, s * 0.05f, p)
        c.drawCircle(cx, cy + s * 0.04f, s * 0.17f, hole)
    }

    /** Glyph für System-Meldungen ([Glyph]) */
    fun glyph(c: Canvas, g: Int, cx: Float, cy: Float, s: Float, p: Paint) {
        if (family == NOTHING && !inDots) { dots("g$g", c, cx, cy, s, p) { cc, x, y, ss, pp -> glyph(cc, g, x, y, ss, pp) }; return }
        when (g) {
            SGlyph.MOON -> moon(c, cx, cy, s, p)
            SGlyph.LOCATION -> location(c, cx, cy, s, p)
            SGlyph.HEADPHONES -> headphones(c, cx, cy, s, p)
            SGlyph.CHECK -> check(c, cx, cy, s, p)
            SGlyph.CLOCK -> timer(c, cx, cy, s, p)
            SGlyph.SUN -> sun(c, cx, cy, s, p)
            SGlyph.BOLT -> bolt(c, cx, cy, s, p)
            else -> stroke(c, p, s * 0.1f) { drawGlyph(c, g, cx, cy, s, p) }
        }
    }

    private fun drawGlyph(c: Canvas, g: Int, cx: Float, cy: Float, s: Float, p: Paint) {
        val x = cx - s / 2
        val y = cy - s / 2
        when (g) {
            SGlyph.LEAF -> {
                path.reset()
                path.moveTo(x + s * 0.18f, y + s * 0.84f)
                path.cubicTo(x + s * 0.1f, y + s * 0.3f, x + s * 0.45f, y + s * 0.12f, x + s * 0.86f, y + s * 0.12f)
                path.cubicTo(x + s * 0.88f, y + s * 0.55f, x + s * 0.65f, y + s * 0.86f, x + s * 0.18f, y + s * 0.84f)
                c.drawPath(path, p)
                c.drawLine(x + s * 0.12f, y + s * 0.92f, x + s * 0.6f, y + s * 0.42f, p)
            }
            SGlyph.HOTSPOT -> {
                val oldStyle = p.style
                p.style = Paint.Style.FILL
                c.drawCircle(cx, cy, s * 0.09f, p)
                p.style = oldStyle
                for (i in 1..2) {
                    val r = s * (0.2f + 0.17f * i)
                    rect.set(cx - r, cy - r, cx + r, cy + r)
                    c.drawArc(rect, -50f, 100f, false, p)
                    c.drawArc(rect, 130f, 100f, false, p)
                }
            }
            SGlyph.NFC -> {
                for (i in 0..2) {
                    val r = s * (0.16f + 0.15f * i)
                    rect.set(cx - s * 0.3f - r, cy - r, cx - s * 0.3f + r, cy + r)
                    c.drawArc(rect, -45f, 90f, false, p)
                }
            }
            SGlyph.DOWNLOAD -> {
                c.drawLine(cx, y + s * 0.1f, cx, y + s * 0.66f, p)
                c.drawLine(cx - s * 0.24f, y + s * 0.44f, cx, y + s * 0.68f, p)
                c.drawLine(cx + s * 0.24f, y + s * 0.44f, cx, y + s * 0.68f, p)
                c.drawLine(x + s * 0.16f, y + s * 0.88f, x + s * 0.84f, y + s * 0.88f, p)
            }
            SGlyph.TRASH -> {
                c.drawLine(x + s * 0.14f, y + s * 0.24f, x + s * 0.86f, y + s * 0.24f, p)
                c.drawLine(x + s * 0.38f, y + s * 0.1f, x + s * 0.62f, y + s * 0.1f, p)
                rect.set(x + s * 0.24f, y + s * 0.24f, x + s * 0.76f, y + s * 0.92f)
                c.drawRoundRect(rect, s * 0.08f, s * 0.08f, p)
            }
            SGlyph.USB -> {
                c.drawLine(cx, y + s * 0.18f, cx, y + s * 0.8f, p)
                c.drawLine(cx, y + s * 0.62f, cx - s * 0.22f, y + s * 0.46f, p)
                c.drawLine(cx - s * 0.22f, y + s * 0.46f, cx - s * 0.22f, y + s * 0.36f, p)
                c.drawLine(cx, y + s * 0.52f, cx + s * 0.22f, y + s * 0.38f, p)
                c.drawLine(cx + s * 0.22f, y + s * 0.38f, cx + s * 0.22f, y + s * 0.28f, p)
                val oldStyle = p.style
                p.style = Paint.Style.FILL
                c.drawCircle(cx, y + s * 0.86f, s * 0.08f, p)
                path.reset()
                path.moveTo(cx, y + s * 0.04f); path.lineTo(cx - s * 0.09f, y + s * 0.2f); path.lineTo(cx + s * 0.09f, y + s * 0.2f); path.close()
                c.drawPath(path, p)
                p.style = oldStyle
            }
            SGlyph.ROTATE -> {
                rect.set(x + s * 0.16f, y + s * 0.16f, x + s * 0.84f, y + s * 0.84f)
                c.drawArc(rect, -60f, 290f, false, p)
                c.drawLine(x + s * 0.68f, y + s * 0.1f, x + s * 0.7f, y + s * 0.26f, p)
                c.drawLine(x + s * 0.7f, y + s * 0.26f, x + s * 0.86f, y + s * 0.22f, p)
            }
            SGlyph.CALENDAR -> {
                rect.set(x + s * 0.12f, y + s * 0.18f, x + s * 0.88f, y + s * 0.88f)
                c.drawRoundRect(rect, s * 0.12f, s * 0.12f, p)
                c.drawLine(x + s * 0.12f, y + s * 0.38f, x + s * 0.88f, y + s * 0.38f, p)
                c.drawLine(x + s * 0.32f, y + s * 0.08f, x + s * 0.32f, y + s * 0.24f, p)
                c.drawLine(x + s * 0.68f, y + s * 0.08f, x + s * 0.68f, y + s * 0.24f, p)
            }
            SGlyph.STORAGE -> {
                for (i in 0..2) {
                    rect.set(x + s * 0.14f, y + s * (0.12f + 0.27f * i), x + s * 0.86f, y + s * (0.34f + 0.27f * i))
                    c.drawRoundRect(rect, s * 0.06f, s * 0.06f, p)
                }
            }
            SGlyph.BLUETOOTH -> {
                path.reset()
                path.moveTo(x + s * 0.24f, y + s * 0.3f)
                path.lineTo(x + s * 0.74f, y + s * 0.7f)
                path.lineTo(cx, y + s * 0.94f)
                path.lineTo(cx, y + s * 0.06f)
                path.lineTo(x + s * 0.74f, y + s * 0.3f)
                path.lineTo(x + s * 0.24f, y + s * 0.7f)
                c.drawPath(path, p)
            }
        }
    }

    private inline fun stroke(c: Canvas, p: Paint, w: Float, block: () -> Unit) {
        val oldStyle = p.style; val oldW = p.strokeWidth; val oldCap = p.strokeCap; val oldJoin = p.strokeJoin
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * (if (family == MATERIAL) 1.15f else 1f)
        p.strokeCap = Paint.Cap.ROUND
        p.strokeJoin = Paint.Join.ROUND
        block()
        p.style = oldStyle; p.strokeWidth = oldW; p.strokeCap = oldCap; p.strokeJoin = oldJoin
    }

    /** Material / One UI: deutlich rundere Ecken als bei SF Symbols */
    private inline fun roundJoin(p: Paint, s: Float, block: () -> Unit) {
        val oldJoin = p.strokeJoin
        val oldStyle = p.style
        val oldW = p.strokeWidth
        p.strokeJoin = Paint.Join.ROUND
        p.style = Paint.Style.FILL_AND_STROKE
        p.strokeWidth = when (family) { MATERIAL -> s * 0.13f; ONEUI -> s * 0.09f; else -> 1.5f }
        block()
        p.strokeJoin = oldJoin
        p.style = oldStyle
        p.strokeWidth = oldW
    }
}
