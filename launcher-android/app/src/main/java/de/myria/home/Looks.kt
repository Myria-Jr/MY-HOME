package de.myria.home

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Typeface
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Die sechs Looks des Launchers. */
enum class Look(val title: String, val subtitle: String) {
    APPLE("Apple", "iOS: Squircle-Icons, Glas-Dock, Suchen-Pille"),
    ANDROID("Android", "Pixel: runde Icons, Datum oben, Suchleiste unten"),
    ONEUI("One UI 9", "Samsung: große Uhr, weiche Squircles"),
    GALAXY("Galaxy Klassik", "Samsung klassisch: gestapelte Uhr, Dock-Leiste"),
    NOTHING("Nothing", "Monochrom, Punkt-Uhr, runde Icons"),
    COLOROS("ColorOS", "OPPO: runde Quadrate, dünne Uhr, helles Dock"),
}

/** Einstellungen des Launchers */
class HomePrefs(ctx: Context) {
    val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    var look: Look
        get() = Look.values().getOrElse(sp.getInt("look", 0)) { Look.APPLE }
        set(v) = sp.edit().putInt("look", v.ordinal).apply()
    var columns: Int
        get() = sp.getInt("columns", 4)
        set(v) = sp.edit().putInt("columns", v).apply()
    var iconDp: Int
        get() = sp.getInt("icon_dp", 56)
        set(v) = sp.edit().putInt("icon_dp", v).apply()
    var labels: Boolean
        get() = sp.getBoolean("labels", true)
        set(v) = sp.edit().putBoolean("labels", v).apply()
    var dock: Boolean
        get() = sp.getBoolean("dock", true)
        set(v) = sp.edit().putBoolean("dock", v).apply()
    var clock: Boolean
        get() = sp.getBoolean("clock", true)
        set(v) = sp.edit().putBoolean("clock", v).apply()
    var search: Boolean
        get() = sp.getBoolean("search", true)
        set(v) = sp.edit().putBoolean("search", v).apply()
    var monoIcons: Boolean
        get() = sp.getBoolean("mono", false)
        set(v) = sp.edit().putBoolean("mono", v).apply()
    var favorites: List<String>
        get() = sp.getString("favorites", null)?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit().putString("favorites", v.joinToString("|")).apply()
    var dockApps: List<String>
        get() = sp.getString("dock_apps", null)?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit().putString("dock_apps", v.joinToString("|")).apply()
    val initialized get() = sp.contains("favorites")
}

object Looks {
    private val cache = LruCache<String, Bitmap>(240)
    private val fonts = HashMap<String, Typeface>()

    fun clearCache() = cache.evictAll()

    /** Schrift je Look (Inter ≈ SF, Doto = Nothing-Punktschrift) */
    fun font(ctx: Context, look: Look, weight: Int, display: Boolean = false): Typeface {
        val key = "${look.name}/$weight/$display"
        fonts[key]?.let { return it }
        val file = when (look) {
            Look.APPLE -> "inter.ttf"
            Look.NOTHING -> if (display) "doto.ttf" else "spacegrotesk.ttf"
            Look.COLOROS -> "outfit.ttf"
            else -> null
        }
        val tf = try {
            if (file != null) Typeface.Builder(ctx.assets, "fonts/$file").setFontVariationSettings("'wght' $weight").build()
            else {
                val fam = if (look == Look.ANDROID) "google-sans" else "sans-serif"
                val base = Typeface.create(fam, Typeface.NORMAL)
                if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else base
            }
        } catch (_: Exception) { Typeface.DEFAULT }
        fonts[key] = tf
        return tf
    }

    /** Icon-Form je Look, im Quadrat der Größe s. */
    fun maskPath(look: Look, s: Float): Path {
        val p = Path()
        when (look) {
            Look.APPLE -> superellipse(p, s, 5.0)
            Look.GALAXY -> superellipse(p, s, 3.6)
            Look.ANDROID, Look.NOTHING -> p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
            Look.ONEUI -> p.addRoundRect(0f, 0f, s, s, s * 0.34f, s * 0.34f, Path.Direction.CW)
            Look.COLOROS -> p.addRoundRect(0f, 0f, s, s, s * 0.27f, s * 0.27f, Path.Direction.CW)
        }
        return p
    }

    /** Superellipse |x|^n + |y|^n = 1 – Apples „Squircle“ */
    private fun superellipse(p: Path, s: Float, n: Double) {
        val r = s / 2
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t)
            val sn = sin(t)
            val x = r + r * (abs(c).pow(2.0 / n) * sign(c)).toFloat()
            val y = r + r * (abs(sn).pow(2.0 / n) * sign(sn)).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
    }

    /** App-Icon in die Form des Looks bringen (mit weichen Kanten). */
    fun icon(key: String, dr: Drawable, sizePx: Int, look: Look, mono: Boolean): Bitmap {
        val ck = "$key/${look.name}/$sizePx/$mono"
        cache.get(ck)?.let { return it }
        val s = sizePx.coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val layer = c.saveLayer(0f, 0f, s.toFloat(), s.toFloat(), null)
        if (dr is AdaptiveIconDrawable) {
            // Ebenen sind 108 dp groß, sichtbar sind die mittleren 72 dp
            val pad = (s * 0.25f).toInt()
            dr.background?.let { it.setBounds(-pad, -pad, s + pad, s + pad); it.draw(c) }
            dr.foreground?.let { it.setBounds(-pad, -pad, s + pad, s + pad); it.draw(c) }
        } else {
            c.drawColor(if (look == Look.NOTHING) 0xFF1A1A1A.toInt() else Color.WHITE)
            val inset = (s * 0.16f).toInt()
            dr.setBounds(inset, inset, s - inset, s - inset)
            dr.draw(c)
        }
        // Maske
        val mp = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
        c.drawPath(maskPath(look, s.toFloat()), mp)
        c.restoreToCount(layer)
        val out = if (mono || look == Look.NOTHING && mono) monochrome(bmp) else bmp
        cache.put(ck, out)
        return out
    }

    /** Graustufen + mehr Kontrast (Nothing-Look) */
    private fun monochrome(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        val contrast = ColorMatrix(floatArrayOf(
            1.3f, 0f, 0f, 0f, -30f,
            0f, 1.3f, 0f, 0f, -30f,
            0f, 0f, 1.3f, 0f, -30f,
            0f, 0f, 0f, 1f, 0f,
        ))
        cm.postConcat(contrast)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }
}
