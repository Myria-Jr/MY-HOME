package de.myria.home

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
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

/** Die Looks des Launchers. Reihenfolge = gespeicherter Index → nur hinten anhängen! */
enum class Look(val title: String, val subtitle: String) {
    APPLE("Apple", "iOS: Squircle-Icons, Widgets, Glas-Dock, Suchen-Pille"),
    ANDROID("Android", "Pixel: runde Icons, Datum oben, Suchleiste unten"),
    ONEUI("One UI 9", "Samsung: große Uhr, weiche Kiesel-Icons"),
    GALAXY("Galaxy Klassik", "Samsung klassisch: gestapelte Uhr, Dock-Leiste"),
    NOTHING("Nothing", "Monochrom, Punkt-Uhr, runde Icons"),
    COLOROS("ColorOS", "OPPO: runde Quadrate, dünne Uhr, helles Dock"),
    HYPEROS("HyperOS", "Xiaomi: kräftige Uhr links, Squircles, klar & ruhig"),
    MAGICOS("MagicOS", "Honor: zentrierte Uhr, weiche Icons, Glas-Dock"),
    ORIGINOS("OriginOS", "vivo: große Icons, dünne Uhr, luftiges Raster"),
}

/** Icon-Formen. Reihenfolge = gespeicherter Index. */
enum class Shape(val title: String) {
    SQUIRCLE("iOS-Squircle"),
    CIRCLE("Kreis"),
    PEBBLE("Kiesel"),
    ROUNDED("Abgerundet"),
    SOFT_SQUARE("Leicht abgerundet"),
    TEARDROP("Tropfen"),
    FLOWER("Blume"),
    HEXAGON("Sechseck"),
    SYSTEM("Original"),
}

/** Icon-Stile wie bei iOS 18/26 */
object IconStyle {
    const val STANDARD = 0
    const val DARK = 1
    const val TINTED = 2
    const val CLEAR = 3
    val names = listOf("Standard", "Dunkel", "Getönt", "Klar")
}

/** Einstellungen des Launchers */
class HomePrefs(ctx: Context) {
    val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    private fun i(k: String, d: Int) = sp.getInt(k, d)
    private fun b(k: String, d: Boolean) = sp.getBoolean(k, d)
    private fun pi(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    private fun pb(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()

    var look: Look
        get() = Look.values().getOrElse(i("look", Look.ANDROID.ordinal)) { Look.ANDROID }
        set(v) = pi("look", v.ordinal)
    var columns: Int
        get() = i("columns", 5).coerceIn(3, 8)
        set(v) = pi("columns", v)
    var iconDp: Int
        get() = i("icon_dp", 52).coerceIn(36, 80)
        set(v) = pi("icon_dp", v)
    var labels: Boolean
        get() = b("labels", true)
        set(v) = pb("labels", v)
    var labelSize: Int
        get() = i("label_size", 12)
        set(v) = pi("label_size", v)
    /** -1 = passend zum Look, sonst Index in [Looks.labelFonts] */
    var labelFont: Int
        get() = i("label_font", -1)
        set(v) = pi("label_font", v)
    var dock: Boolean
        get() = b("dock", true)
        set(v) = pb("dock", v)
    var dockCount: Int
        get() = i("dock_count", 5).coerceIn(3, 8)
        set(v) = pi("dock_count", v)
    var dockIconDp: Int
        get() = i("dock_icon_dp", iconDp).coerceIn(36, 80)
        set(v) = pi("dock_icon_dp", v)
    var dockLabels: Boolean
        get() = b("dock_labels", false)
        set(v) = pb("dock_labels", v)
    var dockBackground: Boolean
        get() = b("dock_background", true)
        set(v) = pb("dock_background", v)
    var clock: Boolean
        get() = b("clock", true)
        set(v) = pb("clock", v)
    /** -1 = Widget passend zum Look, sonst Look-Index */
    var widgetStyle: Int
        get() = i("widget_style", -1)
        set(v) = pi("widget_style", v)
    var search: Boolean
        get() = b("search", true)
        set(v) = pb("search", v)
    var monoIcons: Boolean
        get() = b("mono", false)
        set(v) = pb("mono", v)
    /** -1 = Form passend zum Look */
    var shape: Int
        get() = i("shape", -1)
        set(v) = pi("shape", v)
    var iconStyle: Int
        get() = i("icon_style", IconStyle.STANDARD)
        set(v) = pi("icon_style", v)
    var tint: Int
        get() = i("tint", 0xFFFF9F0A.toInt())
        set(v) = pi("tint", v)
    /** 0 Schieben · 1 Zoom · 2 Würfel · 3 Stapel */
    var pageEffect: Int
        get() = i("page_effect", 0)
        set(v) = pi("page_effect", v)
    var dim: Int
        get() = i("dim", 0)
        set(v) = pi("dim", v)
    var hideStatusBar: Boolean
        get() = b("hide_status", false)
        set(v) = pb("hide_status", v)
    /** 0 nichts · 1 Benachrichtigungen · 2 Suche · 3 Einstellungen · 4 App-Übersicht */
    var doubleTap: Int
        get() = i("double_tap", 1)
        set(v) = pi("double_tap", v)
    var swipeUpAction: Int
        get() = i("swipe_up_action", 4).coerceIn(0, 4)
        set(v) = pi("swipe_up_action", v)
    var drawerColumns: Int
        get() = i("drawer_columns", columns).coerceIn(3, 8)
        set(v) = pi("drawer_columns", v)
    var drawerIconDp: Int
        get() = i("drawer_icon_dp", iconDp).coerceIn(36, 80)
        set(v) = pi("drawer_icon_dp", v)
    var drawerList: Boolean
        get() = b("drawer_list", false)
        set(v) = pb("drawer_list", v)
    var drawerOpacity: Int
        get() = i("drawer_opacity", 90)
        set(v) = pi("drawer_opacity", v)
    var favorites: List<String>
        get() = sp.getString("favorites", null)?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit().putString("favorites", v.joinToString("|")).apply()
    var dockApps: List<String>
        get() = sp.getString("dock_apps", null)?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit().putString("dock_apps", v.joinToString("|")).apply()
    var darkText: Boolean
        get() = b("dark_text", false)
        set(v) = pb("dark_text", v)
    /** Alle Apps auf Startseiten verteilen (wie iOS) */
    var allOnHome: Boolean
        get() = b("all_on_home", look == Look.APPLE)
        set(v) = pb("all_on_home", v)
    /** Nach unten wischen: Suche statt Benachrichtigungen */
    var swipeDownSearch: Boolean
        get() = b("swipe_down_search", look == Look.APPLE)
        set(v) = pb("swipe_down_search", v)
    /** „Heute“-Seite links neben dem Startbildschirm (wie iOS) */
    var todayPage: Boolean
        get() = b("today_page", true)
        set(v) = pb("today_page", v)
    var hidden: Set<String>
        get() = sp.getStringSet("hidden", null)?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("hidden", v).apply()
    /** Paketname des Icon-Packs, null = keins */
    var iconPack: String?
        get() = sp.getString("icon_pack", null)?.takeIf { it.isNotBlank() }
        set(v) = sp.edit().putString("icon_pack", v ?: "").apply()
    /** Benachrichtigungspunkte auf Icons */
    var badges: Boolean
        get() = b("badges", true)
        set(v) = pb("badges", v)
    /** Zahl statt Punkt */
    var badgeCount: Boolean
        get() = b("badge_count", false)
        set(v) = pb("badge_count", v)
    /** App-Übersicht: 0 A–Z · 1 Meistgenutzt · 2 Neueste zuerst */
    var drawerSort: Int
        get() = i("drawer_sort", 0)
        set(v) = pi("drawer_sort", v)
    /** Heute-Seite: Widgets (App-Widget-IDs) */
    var widgets: List<Int>
        get() = sp.getString("widgets", null)?.split('|')?.mapNotNull { it.toIntOrNull() } ?: emptyList()
        set(v) = sp.edit().putString("widgets", v.joinToString("|")).apply()

    // ---- Ordner: Einträge "folder:<id>" in favorites ----
    fun folderName(id: String) = sp.getString("fname_$id", null) ?: "Ordner"
    fun setFolderName(id: String, n: String) = sp.edit().putString("fname_$id", n).apply()
    fun folderApps(id: String): List<String> =
        sp.getString("fapps_$id", null)?.split('|')?.filter { it.isNotBlank() } ?: emptyList()
    fun setFolderApps(id: String, v: List<String>) = sp.edit().putString("fapps_$id", v.joinToString("|")).apply()
    fun deleteFolder(id: String) {
        sp.edit().remove("fname_$id").remove("fapps_$id").apply()
        favorites = favorites - "folder:$id"
    }
    val folderIds: List<String> get() = favorites.filter { it.startsWith("folder:") }.map { it.removePrefix("folder:") }

    /** Einmalige Korrekturen beim Update */
    fun migrate() {
        if (i("schema", 0) < 3) {
            // 3.0: erzwungenes Schwarz-Weiß entfernen, Original-Form für Nicht-Apple-Looks
            sp.edit().putBoolean("mono", false).putInt("schema", 3).apply()
        }
    }

    fun label(key: String, def: String) = sp.getString("label_$key", null)?.takeIf { it.isNotBlank() } ?: def
    fun setLabel(key: String, v: String?) =
        if (v.isNullOrBlank()) sp.edit().remove("label_$key").apply() else sp.edit().putString("label_$key", v).apply()

    fun countLaunch(key: String) = sp.edit().putInt("cnt_$key", sp.getInt("cnt_$key", 0) + 1).apply()
    fun launches(key: String) = sp.getInt("cnt_$key", 0)

    /** Effektive Form (eigene Wahl oder passend zum Look) */
    val effectiveShape: Shape
        get() = Shape.values().getOrNull(shape) ?: Looks.defaultShape(look)

    val initialized get() = sp.contains("favorites")

    /** Für „neu aufbauen?“-Vergleich */
    fun signature(extra: Int) = sp.all.filterKeys { !it.startsWith("cnt_") }.toSortedMap().toString() + extra
}

object Looks {
    private val cache = LruCache<String, Bitmap>(300)
    private val fonts = HashMap<String, Typeface>()

    fun clearCache() = cache.evictAll()

    fun defaultShape(look: Look) = when (look) {
        Look.APPLE, Look.HYPEROS -> Shape.SQUIRCLE
        Look.ANDROID -> Shape.SYSTEM
        Look.NOTHING -> Shape.CIRCLE
        Look.ONEUI -> Shape.PEBBLE
        Look.GALAXY, Look.MAGICOS -> Shape.ROUNDED
        Look.COLOROS, Look.ORIGINOS -> Shape.ROUNDED
    }

    /** Wählbare Schriften für App-Namen */
    val labelFonts = listOf("Inter", "System", "Google Sans", "Outfit", "Space Grotesk", "Doto", "Serif", "Mono")

    private fun fontFile(name: String): String? = when (name) {
        "Inter" -> "inter.ttf"
        "Outfit" -> "outfit.ttf"
        "Space Grotesk" -> "spacegrotesk.ttf"
        "Doto" -> "doto.ttf"
        else -> null
    }

    private fun fontFamily(name: String) = when (name) {
        "Google Sans" -> "google-sans"
        "Serif" -> "serif"
        "Mono" -> "monospace"
        else -> "sans-serif"
    }

    fun namedFont(ctx: Context, name: String, weight: Int): Typeface {
        val key = "n/$name/$weight"
        fonts[key]?.let { return it }
        val file = fontFile(name)
        val tf = try {
            if (file != null) Typeface.Builder(ctx.assets, "fonts/$file").setFontVariationSettings("'wght' $weight").build()
            else {
                val base = Typeface.create(fontFamily(name), Typeface.NORMAL)
                if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight, false) else base
            }
        } catch (_: Exception) { Typeface.DEFAULT }
        fonts[key] = tf
        return tf
    }

    /** Schrift je Look (Inter ≈ SF, Doto = Nothing-Punktschrift) */
    fun font(ctx: Context, look: Look, weight: Int, display: Boolean = false): Typeface = namedFont(ctx, when (look) {
        Look.APPLE, Look.HYPEROS -> "Inter"
        Look.NOTHING -> if (display) "Doto" else "Space Grotesk"
        Look.COLOROS, Look.MAGICOS, Look.ORIGINOS -> "Outfit"
        Look.ANDROID -> "Google Sans"
        else -> "System"
    }, weight)

    /** Icon-Form im Quadrat der Größe s. */
    fun maskPath(shape: Shape, s: Float): Path {
        val p = Path()
        when (shape) {
            Shape.SQUIRCLE -> superellipse(p, s, 5.0)
            Shape.PEBBLE -> superellipse(p, s, 2.9)
            Shape.CIRCLE -> p.addCircle(s / 2, s / 2, s / 2, Path.Direction.CW)
            Shape.ROUNDED -> p.addRoundRect(0f, 0f, s, s, s * 0.3f, s * 0.3f, Path.Direction.CW)
            Shape.SOFT_SQUARE -> p.addRoundRect(0f, 0f, s, s, s * 0.14f, s * 0.14f, Path.Direction.CW)
            Shape.TEARDROP -> {
                val r = s / 2
                val small = s * 0.12f
                p.addRoundRect(0f, 0f, s, s, floatArrayOf(r, r, r, r, small, small, r, r), Path.Direction.CW)
            }
            Shape.FLOWER -> {
                val r = s / 2
                val steps = 120
                for (i in 0..steps) {
                    val t = 2 * Math.PI * i / steps
                    val rr = r * (0.9 + 0.1 * cos(8 * t))
                    val x = (r + rr * cos(t)).toFloat()
                    val y = (r + rr * sin(t)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            Shape.HEXAGON -> {
                val r = s / 2
                for (i in 0 until 6) {
                    val t = Math.toRadians(60.0 * i - 90)
                    val x = (r + r * 0.98 * cos(t)).toFloat()
                    val y = (r + r * 0.98 * sin(t)).toFloat()
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close()
            }
            Shape.SYSTEM -> p.addRoundRect(0f, 0f, s, s, s * 0.32f, s * 0.32f, Path.Direction.CW)
        }
        return p
    }

    /** Für ältere Aufrufe: Form passend zum Look */
    fun maskPath(look: Look, s: Float): Path = maskPath(defaultShape(look), s)

    /** Superellipse |x|^n + |y|^n = 1 – Apples „Squircle“ */
    private fun superellipse(p: Path, s: Float, n: Double) {
        val r = s / 2
        val steps = 120
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

    /** Durchschnittsfarbe am Rand eines alten (nicht-adaptiven) Icons → passender Hintergrund */
    /** Randfarbe eines alten Icons + ob es bis zum Rand gefüllt ist (dann nicht verkleinern) */
    private fun edgeInfo(dr: Drawable): Pair<Int, Boolean> {
        return try {
            val b = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
            dr.setBounds(0, 0, 24, 24)
            dr.draw(Canvas(b))
            var r = 0L; var g = 0L; var bl = 0L; var n = 0
            for (i in 0 until 24) for ((x, y) in listOf(i to 1, i to 22, 1 to i, 22 to i)) {
                val c = b.getPixel(x, y)
                if (Color.alpha(c) > 200) { r += Color.red(c); g += Color.green(c); bl += Color.blue(c); n++ }
            }
            // Ecken transparent → rundes/geformtes Icon → auf hellen Hintergrund setzen
            val corner = Color.alpha(b.getPixel(1, 1)) > 200 && Color.alpha(b.getPixel(22, 22)) > 200
            val col = if (n < 10) Color.WHITE else Color.rgb((r / n).toInt(), (g / n).toInt(), (bl / n).toInt())
            col to (corner && n > 80)
        } catch (_: Exception) { Color.WHITE to false }
    }

    /**
     * App-Icon in Form + Stil bringen (weiche Kanten).
     * Stile: Standard · Dunkel (iOS 18 Dark) · Getönt (iOS 18 Tinted) · Klar (iOS 26 Clear/Glas)
     */
    fun icon(key: String, dr: Drawable, sizePx: Int, shape: Shape, style: Int, tint: Int, mono: Boolean, pack: Boolean = false): Bitmap {
        val ck = "$key/${shape.name}/$sizePx/$style/$tint/$mono/$pack"
        cache.get(ck)?.let { return it }
        val s = sizePx.coerceAtLeast(8)
        // Icon-Pack oder „Original“: Icon so lassen, wie es ist (nur Größe)
        if (style == IconStyle.STANDARD && (pack || shape == Shape.SYSTEM)) {
            val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            val bc = Canvas(b)
            if (dr is AdaptiveIconDrawable && !pack) {
                // System-Maske des Geräts (z. B. Pixel-Kreis, Samsung-Squircle)
                dr.setBounds(0, 0, s, s)
                dr.draw(bc)
            } else {
                dr.setBounds(0, 0, s, s)
                dr.draw(bc)
            }
            val out = if (mono) monochrome(b) else b
            cache.put(ck, out)
            return out
        }
        val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val layer = c.saveLayer(0f, 0f, s.toFloat(), s.toFloat(), null)
        val sf = s.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // Hintergrund je Stil
        fun styledBackground() {
            when (style) {
                IconStyle.DARK -> {
                    p.shader = LinearGradient(0f, 0f, 0f, sf, 0xFF3A3A3C.toInt(), 0xFF141416.toInt(), Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, sf, sf, p); p.shader = null
                }
                IconStyle.TINTED -> {
                    p.shader = LinearGradient(0f, 0f, 0f, sf, mix(tint, Color.BLACK, 0.72f), mix(tint, Color.BLACK, 0.88f), Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, sf, sf, p); p.shader = null
                }
                IconStyle.CLEAR -> {
                    p.shader = LinearGradient(0f, 0f, 0f, sf, 0x59FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, sf, sf, p); p.shader = null
                }
            }
        }

        // Vordergrund-Filter je Stil
        val fgFilter: ColorMatrixColorFilter? = when (style) {
            IconStyle.TINTED -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                val tr = Color.red(tint) / 255f; val tg = Color.green(tint) / 255f; val tb = Color.blue(tint) / 255f
                cm.postConcat(ColorMatrix(floatArrayOf(
                    tr * 1.4f, 0f, 0f, 0f, 20f, 0f, tg * 1.4f, 0f, 0f, 20f, 0f, 0f, tb * 1.4f, 0f, 20f, 0f, 0f, 0f, 1f, 0f,
                )))
                ColorMatrixColorFilter(cm)
            }
            IconStyle.CLEAR -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                cm.postConcat(ColorMatrix(floatArrayOf(
                    1.6f, 0f, 0f, 0f, 60f, 0f, 1.6f, 0f, 0f, 60f, 0f, 0f, 1.6f, 0f, 60f, 0f, 0f, 0f, 1f, 0f,
                )))
                ColorMatrixColorFilter(cm)
            }
            else -> null
        }

        if (dr is AdaptiveIconDrawable) {
            // Ebenen sind 108 dp groß, sichtbar sind die mittleren 72 dp
            val pad = (s * 0.25f).toInt()
            if (style == IconStyle.STANDARD) {
                dr.background?.let { it.setBounds(-pad, -pad, s + pad, s + pad); it.draw(c) }
            } else styledBackground()
            // Für Getönt/Klar ist die einfarbige Ebene (Android 13+) am schönsten
            val fg = if ((style == IconStyle.TINTED || style == IconStyle.CLEAR) && Build.VERSION.SDK_INT >= 33)
                dr.monochrome ?: dr.foreground else dr.foreground
            fg?.let {
                it.setBounds(-pad, -pad, s + pad, s + pad)
                it.colorFilter = fgFilter
                it.draw(c)
                it.colorFilter = null
            }
        } else {
            // Altes Icon: randgefüllte Icons füllen die Form; runde/kleine Icons kommen
            // verkleinert auf einen weißen Hintergrund (wie bei Pixel/One UI) statt verzerrt
            val (edge, full) = edgeInfo(dr)
            if (style == IconStyle.STANDARD) c.drawColor(if (full) edge else Color.WHITE) else styledBackground()
            val inset = (s * if (full) 0.0f else 0.17f).toInt()
            dr.setBounds(inset, inset, s - inset, s - inset)
            dr.colorFilter = fgFilter
            dr.draw(c)
            dr.colorFilter = null
        }
        c.restoreToCount(layer)
        // Maske – Fix: vorher DST_IN mit einem Pfad, das ließ alles AUSSERHALB der Form stehen
        // (→ eckige Kästen hinter den Icons). Jetzt: Form zeichnen, Icon mit SRC_IN hineinlegen.
        val masked = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val mc = Canvas(masked)
        mc.drawPath(maskPath(shape, sf), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK })
        mc.drawBitmap(bmp, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        bmp.recycle()
        // Glanzkante (Klar/Glas) bzw. feiner Rand (Dunkel)
        if (style == IconStyle.CLEAR || style == IconStyle.DARK) {
            val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.style = Paint.Style.STROKE
                strokeWidth = s * 0.025f
                shader = LinearGradient(0f, 0f, 0f, sf,
                    if (style == IconStyle.CLEAR) 0xCCFFFFFF.toInt() else 0x40FFFFFF, 0x1AFFFFFF, Shader.TileMode.CLAMP)
            }
            mc.save()
            val ins = s * 0.0125f
            mc.translate(ins, ins)
            mc.scale((sf - 2 * ins) / sf, (sf - 2 * ins) / sf)
            mc.drawPath(maskPath(shape, sf), rp)
            mc.restore()
        }
        val out = if (mono) monochrome(masked) else masked
        cache.put(ck, out)
        return out
    }

    private fun mix(a: Int, b: Int, t: Float) = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt(),
    )

    /** Graustufen + mehr Kontrast (Nothing-Look) */
    private fun monochrome(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val cm = ColorMatrix().apply { setSaturation(0f) }
        cm.postConcat(ColorMatrix(floatArrayOf(
            1.3f, 0f, 0f, 0f, -30f,
            0f, 1.3f, 0f, 0f, -30f,
            0f, 0f, 1.3f, 0f, -30f,
            0f, 0f, 0f, 1f, 0f,
        )))
        Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
        return out
    }
}
