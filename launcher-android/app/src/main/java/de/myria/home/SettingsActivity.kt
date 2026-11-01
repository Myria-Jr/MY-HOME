package de.myria.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Einstellungen von Lumora Home – mit Live-Vorschau. */
class SettingsActivity : Activity() {

    private lateinit var prefs: HomePrefs
    private var dark = false
    private val bg get() = if (dark) 0xFF0B0B0D.toInt() else 0xFFF2F2F7.toInt()
    private val card get() = if (dark) 0xFF1C1C1F.toInt() else Color.WHITE
    private val label get() = if (dark) 0xFFF2F2F5.toInt() else 0xFF15151A.toInt()
    private val secondary get() = if (dark) 0x99F2F2F5.toInt() else 0x8A15151A.toInt()
    private val chipBg get() = if (dark) 0xFF2C2C30.toInt() else 0xFFE9E9EE.toInt()
    private val accent = 0xFF6C5CE7.toInt()
    private val main = Handler(Looper.getMainLooper())

    private lateinit var previewRow: LinearLayout
    private lateinit var tintRow: View

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dpi(v: Float) = dp(v).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HomePrefs(this)
        dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        window.statusBarColor = bg
        window.navigationBarColor = bg
        @Suppress("DEPRECATION")
        if (!dark) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        build()
        if (Apps.all.isEmpty()) Thread { Apps.load(this); main.post { if (!isFinishing) updatePreview() } }.start()
    }

    private fun changed() {
        Looks.clearCache()
        updatePreview()
    }

    // ------------------------------------------------------------------

    private fun build() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(18f), dpi(16f), dpi(48f))
        }
        col.addView(TextView(this).apply {
            text = "Lumora Home"
            textSize = 32f
            typeface = Looks.font(this@SettingsActivity, Look.APPLE, 800)
            setTextColor(label)
            setPadding(dpi(4f), 0, 0, 0)
        })
        col.addView(TextView(this).apply {
            text = "Dein Startbildschirm – in jedem Look."
            textSize = 15f
            setTextColor(secondary)
            setPadding(dpi(4f), dpi(2f), 0, dpi(14f))
        })

        col.addView(preview())

        col.addView(card(listOf(
            action("Als Standard-Startbildschirm festlegen", accent) {
                try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            action("Hintergrundbild ändern", accent) {
                try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild")) } catch (_: Exception) {}
            },
        )), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })

        // --- Look ---
        header(col, "Look")
        val tiles = ArrayList<LookTile>()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val desc = TextView(this).apply {
            text = prefs.look.subtitle
            textSize = 14f
            setTextColor(secondary)
            setPadding(dpi(4f), dpi(8f), 0, 0)
        }
        for (l in Look.values()) {
            val t = LookTile(this, l, dark)
            t.chosen = l == prefs.look
            t.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                prefs.look = l
                tiles.forEach { x -> x.chosen = x.look == l }
                desc.text = l.subtitle
                changed()
            }
            tiles.add(t)
            row.addView(t, LinearLayout.LayoutParams(dpi(116f), dpi(206f)).apply { rightMargin = dpi(10f) })
        }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        col.addView(desc)

        // --- App-Symbole ---
        header(col, "App-Symbole")
        val shapeChips = ShapePicker(this, prefs, dark) { changed() }
        tintRow = colorDots()
        tintRow.visibility = if (prefs.iconStyle == IconStyle.TINTED) View.VISIBLE else View.GONE
        col.addView(card(listOf(
            labeled("Form", HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(shapeChips) }),
            labeled("Stil (wie iOS)", chips(IconStyle.names, prefs.iconStyle) {
                prefs.iconStyle = it
                tintRow.visibility = if (it == IconStyle.TINTED) View.VISIBLE else View.GONE
                changed()
            }),
            tintRow,
            slider("Größe", 40, 72, prefs.iconDp, "dp") { prefs.iconDp = it; changed() },
            switch("Monochrom", prefs.monoIcons) { prefs.monoIcons = it; changed() },
        )))
        hint(col, "„Getönt“ färbt alle Icons in deiner Farbe, „Klar“ macht sie zu Glas – wie bei iOS 18/26. Einzelne Apps umbenennen: App gedrückt halten → „Umbenennen“.")

        // --- App-Namen ---
        header(col, "App-Namen")
        col.addView(card(listOf(
            switch("Namen zeigen", prefs.labels) { prefs.labels = it; changed() },
            slider("Schriftgröße", 9, 16, prefs.labelSize, "sp") { prefs.labelSize = it; changed() },
            labeled("Schrift", chips(listOf("Auto") + Looks.labelFonts, prefs.labelFont + 1) { prefs.labelFont = it - 1; changed() }),
        )))

        // --- Startbildschirm ---
        header(col, "Startbildschirm")
        col.addView(card(listOf(
            slider("Spalten", 3, 6, prefs.columns, "") { prefs.columns = it; changed() },
            switch("Uhr-Widget", prefs.clock) { prefs.clock = it },
            labeled("Widget-Stil", chips(listOf("Passend") + Look.values().map { it.title }, prefs.widgetStyle + 1) { prefs.widgetStyle = it - 1 }),
            switch("Dock", prefs.dock) { prefs.dock = it },
            slider("Apps im Dock", 3, 6, prefs.dockCount, "") { prefs.dockCount = it },
            switch("Suchleiste", prefs.search) { prefs.search = it },
            switch("Alle Apps auf Startseiten (wie iOS)", prefs.allOnHome) { prefs.allOnHome = it },
            labeled("Blätter-Effekt", chips(listOf("Schieben", "Zoom", "Würfel", "Stapel"), prefs.pageEffect) { prefs.pageEffect = it }),
        )))

        // --- Hintergrund & Anzeige ---
        header(col, "Hintergrund & Anzeige")
        col.addView(card(listOf(
            slider("Hintergrund abdunkeln", 0, 70, prefs.dim, "%") { prefs.dim = it },
            switch("Dunkle Schrift (helle Hintergründe)", prefs.darkText) { prefs.darkText = it; changed() },
            switch("Statusleiste ausblenden", prefs.hideStatusBar) { prefs.hideStatusBar = it },
        )))

        // --- Gesten ---
        header(col, "Gesten")
        col.addView(card(listOf(
            labeled("Doppeltippen auf freie Fläche", chips(listOf("Nichts", "Benachrichtigungen", "Suche", "Einstellungen", "Alle Apps"), prefs.doubleTap) { prefs.doubleTap = it }),
            switch("Nach unten wischen = Suche", prefs.swipeDownSearch) { prefs.swipeDownSearch = it },
        )))
        hint(col, "Nach oben wischen: alle Apps · Seitlich: nächste Seite · Freie Fläche gedrückt halten: Bearbeiten (Wackel-Modus)")

        // --- App-Übersicht ---
        header(col, "App-Übersicht")
        col.addView(card(listOf(
            labeled("Ansicht", chips(listOf("Raster", "Liste"), if (prefs.drawerList) 1 else 0) { prefs.drawerList = it == 1 }),
            slider("Hintergrund-Deckkraft", 40, 100, prefs.drawerOpacity, "%") { prefs.drawerOpacity = it },
        )))

        // --- Ausgeblendete Apps ---
        header(col, "Ausgeblendete Apps")
        val hiddenBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fillHidden() {
            hiddenBox.removeAllViews()
            val keys = prefs.hidden.toList()
            if (keys.isEmpty()) {
                hint(hiddenBox, "Keine. Apps blendest du aus, indem du sie gedrückt hältst → „App ausblenden“.")
                return
            }
            val pm = packageManager
            hiddenBox.addView(card(keys.map { k ->
                val name = try {
                    val cn = android.content.ComponentName.unflattenFromString(k)
                    pm.getApplicationLabel(pm.getApplicationInfo(cn!!.packageName, 0)).toString()
                } catch (_: Exception) { k }
                action("$name  –  wieder zeigen", accent) {
                    prefs.hidden = prefs.hidden - k
                    fillHidden()
                }
            }))
        }
        fillHidden()
        col.addView(hiddenBox)

        col.addView(card(listOf(
            action("Startbildschirm zurücksetzen", 0xFFFF453A.toInt()) {
                prefs.sp.edit().remove("favorites").remove("dock_apps").apply()
                Toast.makeText(this, "Zurückgesetzt", Toast.LENGTH_SHORT).show()
            },
            action("Alle Design-Einstellungen zurücksetzen", 0xFFFF453A.toInt()) {
                val keep = setOf("favorites", "dock_apps", "hidden")
                val e = prefs.sp.edit()
                prefs.sp.all.keys.filter { it !in keep && !it.startsWith("cnt_") && !it.startsWith("label_") }.forEach { e.remove(it) }
                e.apply()
                recreate()
            },
        )), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(24f) })

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(col) })
        updatePreview()
    }

    // ------------------------------------------------------------------
    // Live-Vorschau mit echten Icons
    // ------------------------------------------------------------------

    private fun preview(): View {
        val frame = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF3B2A8C.toInt(), 0xFF8E4FD6.toInt(), 0xFFFF7AA8.toInt(), 0xFFFFB86B.toInt())).apply { cornerRadius = dp(26f) }
            clipToOutline = true
        }
        frame.addView(object : View(this) {
            private val pp = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                pp.shader = RadialGradient(width * 0.2f, height * 0.9f, width * 0.6f, 0x664FC3F7, 0, Shader.TileMode.CLAMP)
                c.drawCircle(width * 0.2f, height * 0.9f, width * 0.6f, pp)
            }
        }, FrameLayout.LayoutParams(-1, -1))
        previewRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpi(10f), dpi(22f), dpi(10f), dpi(22f))
        }
        frame.addView(previewRow, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        return frame
    }

    private fun updatePreview() {
        if (!::previewRow.isInitialized) return
        previewRow.removeAllViews()
        val apps = (prefs.dockApps + prefs.favorites).mapNotNull { Apps.byKey(it) }.distinctBy { it.key }.take(5)
            .ifEmpty { Apps.all.take(5) }
        if (apps.isEmpty()) {
            previewRow.addView(TextView(this).apply { text = "Lädt Vorschau …"; setTextColor(Color.WHITE) })
            return
        }
        val size = dpi(prefs.iconDp.toFloat()).coerceAtMost(dpi(60f))
        val look = prefs.look
        apps.forEach { a ->
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(ImageView(this).apply {
                setImageBitmap(Looks.icon(a.key, a.icon, size, prefs.effectiveShape, prefs.iconStyle, prefs.tint,
                    prefs.monoIcons || (look == Look.NOTHING && prefs.iconStyle == IconStyle.STANDARD)))
            }, LinearLayout.LayoutParams(size, size))
            if (prefs.labels) col.addView(TextView(this).apply {
                text = prefs.label(a.key, a.label)
                textSize = prefs.labelSize.toFloat()
                setTextColor(if (prefs.darkText) 0xFF111114.toInt() else Color.WHITE)
                typeface = if (prefs.labelFont >= 0) Looks.namedFont(this@SettingsActivity, Looks.labelFonts[prefs.labelFont], 500)
                else Looks.font(this@SettingsActivity, look, 500)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setShadowLayer(dp(3f), 0f, dp(1f), 0x66000000)
                setPadding(dpi(2f), dpi(5f), dpi(2f), 0)
            })
            previewRow.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    // ------------------------------------------------------------------
    // Bausteine
    // ------------------------------------------------------------------

    private fun header(col: LinearLayout, t: String) {
        col.addView(TextView(this).apply {
            text = t
            textSize = 20f
            typeface = Looks.font(this@SettingsActivity, Look.APPLE, 700)
            setTextColor(label)
            setPadding(dpi(4f), dpi(26f), 0, dpi(10f))
        })
    }

    private fun hint(col: LinearLayout, t: String) {
        col.addView(TextView(this).apply {
            text = t
            textSize = 13f
            setLineSpacing(0f, 1.2f)
            setTextColor(secondary)
            setPadding(dpi(6f), dpi(8f), dpi(6f), 0)
        })
    }

    private fun card(rows: List<View>): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(20f); setColor(card) }
        rows.forEachIndexed { i, v ->
            if (i > 0 && v.visibility != View.GONE) addView(View(this@SettingsActivity).apply { setBackgroundColor(if (dark) 0x1AFFFFFF else 0x12000000) },
                LinearLayout.LayoutParams(-1, maxOf(1, dpi(0.6f))).apply { leftMargin = dpi(16f); rightMargin = dpi(16f) })
            addView(v)
        }
    }

    private fun rowBase(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dpi(54f)
        setPadding(dpi(16f), dpi(8f), dpi(14f), dpi(8f))
    }

    private fun title(t: String) = TextView(this).apply { text = t; textSize = 16.5f; setTextColor(label) }

    private fun action(t: String, color: Int, onClick: () -> Unit): View = rowBase().apply {
        addView(TextView(this@SettingsActivity).apply { text = t; textSize = 16.5f; setTextColor(color) })
        setOnClickListener { onClick() }
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun switch(t: String, value: Boolean, onChange: (Boolean) -> Unit): View = rowBase().apply {
        addView(title(t), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = Switch(this@SettingsActivity).apply {
            isChecked = value
            thumbTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(Color.WHITE, Color.WHITE))
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(accent, if (dark) 0xFF48484C.toInt() else 0xFFCFCFD4.toInt()))
            setOnCheckedChangeListener { v, b -> v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onChange(b) }
        }
        addView(sw)
        setOnClickListener { sw.toggle() }
    }

    private fun slider(t: String, min: Int, max: Int, value: Int, unit: String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(12f), dpi(16f), dpi(6f))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(title(t), LinearLayout.LayoutParams(0, -2, 1f))
        val v = TextView(this).apply { text = "$value $unit".trim(); textSize = 15f; setTextColor(accent) }
        top.addView(v)
        box.addView(top)
        box.addView(SeekBar(this).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    v.text = "${p + min} $unit".trim()
                    if (fromUser) onChange(p + min)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return box
    }

    private fun labeled(t: String, inner: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dpi(12f), 0, dpi(10f))
        addView(TextView(this@SettingsActivity).apply {
            text = t
            textSize = 14f
            setTextColor(secondary)
            setPadding(dpi(16f), 0, dpi(16f), dpi(8f))
        })
        addView(inner)
    }

    /** Waagerecht scrollbare Auswahl-Chips */
    private fun chips(options: List<String>, current: Int, onPick: (Int) -> Unit): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dpi(12f), 0, dpi(12f), 0) }
        val views = ArrayList<TextView>()
        fun mark(sel: Int) = views.forEachIndexed { i, tv ->
            tv.background = GradientDrawable().apply { cornerRadius = dp(18f); setColor(if (i == sel) accent else chipBg) }
            tv.setTextColor(if (i == sel) Color.WHITE else label)
        }
        options.forEachIndexed { i, o ->
            val tv = TextView(this).apply {
                text = o
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(8f))
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    mark(i); onPick(i)
                }
            }
            views.add(tv)
            row.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(8f) })
        }
        mark(current)
        return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun colorDots(): View {
        val colors = intArrayOf(0xFFFF9F0A.toInt(), 0xFFFF453A.toInt(), 0xFFFF6AA2.toInt(), 0xFFBF5AF2.toInt(),
            0xFF5E5CE6.toInt(), 0xFF0A84FF.toInt(), 0xFF64D2FF.toInt(), 0xFF30D158.toInt(), 0xFFFFD60A.toInt())
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpi(12f), dpi(12f), dpi(12f), dpi(12f))
        }
        val dots = ArrayList<View>()
        fun mark() = colors.forEachIndexed { i, c ->
            (dots[i].background as GradientDrawable).setStroke(dpi(3f), if (c == prefs.tint) label else Color.TRANSPARENT)
        }
        for (c in colors) {
            val v = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    prefs.tint = c; mark(); changed()
                }
            }
            dots.add(v)
            row.addView(v, LinearLayout.LayoutParams(dpi(28f), dpi(28f)).apply { leftMargin = dpi(4f); rightMargin = dpi(4f) })
        }
        mark()
        return row
    }
}

/** Auswahl der Icon-Form mit kleinen Vorschau-Formen. */
@SuppressLint("ViewConstructor")
class ShapePicker(ctx: Context, private val prefs: HomePrefs, private val dark: Boolean, private val onPick: () -> Unit) : LinearLayout(ctx) {
    private val tiles = ArrayList<ShapeTile>()

    init {
        orientation = HORIZONTAL
        setPadding((12 * resources.displayMetrics.density).toInt(), 0, 0, 0)
        val all = listOf(-1) + Shape.values().map { it.ordinal }
        for (idx in all) {
            val t = ShapeTile(ctx, idx, dark)
            t.chosen = prefs.shape == idx
            t.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                prefs.shape = idx
                tiles.forEach { x -> x.chosen = x.index == idx }
                onPick()
            }
            tiles.add(t)
            addView(t, LayoutParams((70 * resources.displayMetrics.density).toInt(), (84 * resources.displayMetrics.density).toInt()))
        }
    }
}

@SuppressLint("ViewConstructor")
class ShapeTile(ctx: Context, val index: Int, private val dark: Boolean) : View(ctx) {
    var chosen = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = resources.displayMetrics.density
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val s = 44 * d
        val x = (width - s) / 2
        val y = 6 * d
        if (chosen) {
            p.color = 0x336C5CE7
            r.set(2 * d, 0f, width - 2 * d, height.toFloat())
            c.drawRoundRect(r, 16 * d, 16 * d, p)
        }
        val shape = Shape.values().getOrNull(index)
        c.save()
        c.translate(x, y)
        p.shader = LinearGradient(0f, 0f, s, s, 0xFF7C6CF0.toInt(), 0xFFFF7AA8.toInt(), Shader.TileMode.CLAMP)
        if (shape != null) c.drawPath(Looks.maskPath(shape, s), p)
        else {
            // „Auto“: halb Kreis, halb Squircle
            c.drawPath(Looks.maskPath(Shape.SQUIRCLE, s), p)
            p.shader = null
            p.color = Color.WHITE
            p.textAlign = Paint.Align.CENTER
            p.textSize = 13 * d
            c.drawText("A", s / 2, s / 2 + 5 * d, p)
        }
        p.shader = null
        c.restore()
        p.color = if (dark) 0xCCFFFFFF.toInt() else 0xCC15151A.toInt()
        p.textAlign = Paint.Align.CENTER
        p.textSize = 10.5f * d
        c.drawText(shape?.title ?: "Auto", width / 2f, height - 10 * d, p)
    }
}

/** Vorschau-Kachel: Mini-Startbildschirm im jeweiligen Look. */
@SuppressLint("ViewConstructor")
class LookTile(ctx: Context, val look: Look, private val dark: Boolean) : View(ctx) {
    var chosen = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val d = resources.displayMetrics.density

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        r.set(2 * d, 2 * d, w - 2 * d, h - 2 * d)
        val (c1, c2) = when (look) {
            Look.APPLE -> 0xFF5B3FD6.toInt() to 0xFFFF7AA8.toInt()
            Look.ANDROID -> 0xFF2D4A3E.toInt() to 0xFF8FC9A9.toInt()
            Look.ONEUI -> 0xFF0F2A5C.toInt() to 0xFF5DA9FF.toInt()
            Look.GALAXY -> 0xFF1E1B3A.toInt() to 0xFF7C6CF0.toInt()
            Look.NOTHING -> 0xFF0A0A0A.toInt() to 0xFF2B2B2B.toInt()
            Look.COLOROS -> 0xFF1F6F6A.toInt() to 0xFFF3C38B.toInt()
            Look.HYPEROS -> 0xFF1A2A3A.toInt() to 0xFFFF8A3D.toInt()
            Look.MAGICOS -> 0xFF2A1F4A.toInt() to 0xFF7FD6FF.toInt()
            Look.ORIGINOS -> 0xFF0E3B4A.toInt() to 0xFFB5F2D5.toInt()
        }
        p.shader = LinearGradient(0f, 0f, w, h, c1, c2, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18 * d, 18 * d, p)
        p.shader = null
        // Mini-Uhr
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = 22 * d
        p.typeface = Looks.font(context, look, if (look == Look.ONEUI || look == Look.COLOROS || look == Look.ORIGINOS) 250 else 700, display = true)
        c.drawText("9:41", w / 2, 46 * d, p)
        // Mini-Icons (3×2 + Dock)
        val s = 22 * d
        val gap = (w - 3 * s) / 4
        val path = Looks.maskPath(look, s)
        val colors = intArrayOf(0xFFFF453A.toInt(), 0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFFFF9F0A.toInt(), 0xFFBF5AF2.toInt(), 0xFFFFD60A.toInt())
        for (i in 0 until 6) {
            val x = gap + (i % 3) * (s + gap)
            val y = 66 * d + (i / 3) * (s + 14 * d)
            c.save()
            c.translate(x, y)
            p.color = if (look == Look.NOTHING) 0xFF3A3A3A.toInt() else colors[i]
            c.drawPath(path, p)
            c.restore()
        }
        val dy = h - 52 * d
        if (look in listOf(Look.APPLE, Look.GALAXY, Look.COLOROS, Look.MAGICOS, Look.ORIGINOS)) {
            p.color = 0x40FFFFFF
            r.set(8 * d, dy - 6 * d, w - 8 * d, dy + s + 6 * d)
            c.drawRoundRect(r, 12 * d, 12 * d, p)
        }
        for (i in 0 until 3) {
            c.save()
            c.translate(gap + i * (s + gap), dy)
            p.color = if (look == Look.NOTHING) 0xFFEDEDED.toInt() else 0xEEFFFFFF.toInt()
            c.drawPath(path, p)
            c.restore()
        }
        p.textSize = 12.5f * d
        p.typeface = Looks.font(context, look, 600)
        p.color = Color.WHITE
        c.drawText(look.title, w / 2, h - 12 * d, p)
        p.textAlign = Paint.Align.LEFT
        if (chosen) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 3 * d
            p.color = if (dark) Color.WHITE else 0xFF6C5CE7.toInt()
            r.set(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d)
            c.drawRoundRect(r, 19 * d, 19 * d, p)
            p.style = Paint.Style.FILL
        }
    }
}
