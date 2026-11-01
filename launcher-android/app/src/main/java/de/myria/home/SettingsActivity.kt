package de.myria.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/** Einstellungen von Lumora Home – schlicht, modern, hell/dunkel. */
class SettingsActivity : Activity() {

    private lateinit var prefs: HomePrefs
    private var dark = false
    private val bg get() = if (dark) 0xFF0E0E10.toInt() else 0xFFF3F3F6.toInt()
    private val card get() = if (dark) 0xFF1C1C1F.toInt() else Color.WHITE
    private val label get() = if (dark) 0xFFF2F2F5.toInt() else 0xFF15151A.toInt()
    private val secondary get() = if (dark) 0x99F2F2F5.toInt() else 0x9915151A.toInt()
    private val accent = 0xFF6C5CE7.toInt()

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
    }

    private fun build() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(18f), dpi(20f), dpi(18f), dpi(40f))
        }
        col.addView(TextView(this).apply {
            text = "Lumora Home"
            textSize = 32f
            typeface = Looks.font(this@SettingsActivity, Look.APPLE, 700)
            setTextColor(label)
        })
        col.addView(TextView(this).apply {
            text = "Dein Startbildschirm – in jedem Look."
            textSize = 15f
            setTextColor(secondary)
            setPadding(0, dpi(2f), 0, dpi(14f))
        })

        // Als Standard festlegen
        col.addView(card(listOf(
            action("Als Standard-Startbildschirm festlegen", accent) {
                try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            action("Hintergrundbild ändern", accent) {
                try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild")) } catch (_: Exception) {}
            },
        )))

        header(col, "Look")
        val tiles = ArrayList<LookTile>()
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (l in Look.values()) {
            val t = LookTile(this, l, dark)
            t.chosen = l == prefs.look
            t.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                prefs.look = l
                Looks.clearCache()
                tiles.forEach { x -> x.chosen = x.look == l }
                desc.text = l.subtitle
            }
            tiles.add(t)
            row.addView(t, LinearLayout.LayoutParams(dpi(120f), dpi(210f)).apply { rightMargin = dpi(10f) })
        }
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) })
        desc = TextView(this).apply {
            text = prefs.look.subtitle
            textSize = 14f
            setTextColor(secondary)
            setPadding(dpi(4f), dpi(8f), 0, 0)
        }
        col.addView(desc)

        header(col, "Startbildschirm")
        col.addView(card(listOf(
            segment("Spalten", listOf("4", "5"), if (prefs.columns == 5) 1 else 0) { prefs.columns = if (it == 1) 5 else 4 },
            slider("Icon-Größe", 40, 68, prefs.iconDp) { prefs.iconDp = it; Looks.clearCache() },
            switch("App-Namen zeigen", prefs.labels) { prefs.labels = it },
            switch("Uhr-Widget", prefs.clock) { prefs.clock = it },
            switch("Dock", prefs.dock) { prefs.dock = it },
            switch("Suchleiste", prefs.search) { prefs.search = it },
            switch("Monochrome Icons", prefs.monoIcons) { prefs.monoIcons = it; Looks.clearCache() },
            switch("Alle Apps auf Startseiten (wie iOS)", prefs.allOnHome) { prefs.allOnHome = it },
            switch("Dunkle Schrift (für helle Hintergründe)", prefs.darkText) { prefs.darkText = it },
            switch("Nach unten wischen = Suche", prefs.swipeDownSearch) { prefs.swipeDownSearch = it },
        )))

        header(col, "Ausgeblendete Apps")
        val hiddenBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun fillHidden() {
            hiddenBox.removeAllViews()
            val pm = packageManager
            val keys = prefs.hidden.toList()
            if (keys.isEmpty()) {
                hiddenBox.addView(TextView(this).apply {
                    text = "Keine. Apps blendest du aus, indem du sie gedrückt hältst → „App ausblenden“."
                    textSize = 14f
                    setTextColor(secondary)
                    setPadding(dpi(4f), 0, 0, 0)
                })
                return
            }
            hiddenBox.addView(card(keys.map { k ->
                val label = try {
                    val cn = android.content.ComponentName.unflattenFromString(k)
                    pm.getApplicationLabel(pm.getApplicationInfo(cn!!.packageName, 0)).toString()
                } catch (_: Exception) { k }
                action("$label  –  wieder zeigen", accent) {
                    prefs.hidden = prefs.hidden - k
                    fillHidden()
                }
            }))
        }
        fillHidden()
        col.addView(hiddenBox)

        header(col, "Bedienung")
        col.addView(TextView(this).apply {
            text = "• Nach oben wischen: alle Apps\n• Nach unten wischen: Suche oder Benachrichtigungen\n• Seitlich wischen: nächste Startseite\n• App gedrückt halten: Startseite / Dock / Ausblenden / Info / Deinstallieren\n• Freie Fläche gedrückt halten: Bearbeiten (Wackel-Modus), Hintergrund, Einstellungen"
            textSize = 14f
            setLineSpacing(0f, 1.3f)
            setTextColor(secondary)
            setPadding(dpi(4f), 0, 0, 0)
        })

        col.addView(card(listOf(
            action("Startbildschirm zurücksetzen", 0xFFFF453A.toInt()) {
                prefs.sp.edit().remove("favorites").remove("dock_apps").apply()
                Toast.makeText(this, "Zurückgesetzt", Toast.LENGTH_SHORT).show()
            },
        )), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(18f) })

        setContentView(ScrollView(this).apply { setBackgroundColor(bg); addView(col) })
    }

    private lateinit var desc: TextView

    private fun header(col: LinearLayout, t: String) {
        col.addView(TextView(this).apply {
            text = t.uppercase()
            textSize = 13f
            letterSpacing = 0.04f
            setTextColor(secondary)
            setPadding(dpi(4f), dpi(24f), 0, dpi(8f))
        })
    }

    private fun card(rows: List<View>): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply { cornerRadius = dp(18f); setColor(card) }
        rows.forEachIndexed { i, v ->
            if (i > 0) addView(View(this@SettingsActivity).apply { setBackgroundColor(if (dark) 0x1FFFFFFF else 0x14000000) },
                LinearLayout.LayoutParams(-1, maxOf(1, dpi(0.6f))).apply { leftMargin = dpi(16f) })
            addView(v)
        }
    }

    private fun rowBase(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dpi(52f)
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
            thumbTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(Color.WHITE, Color.WHITE))
            trackTintList = android.content.res.ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(accent, if (dark) 0xFF48484C.toInt() else 0xFFCFCFD4.toInt()))
            setOnCheckedChangeListener { _, b -> onChange(b) }
        }
        addView(sw)
        setOnClickListener { sw.toggle() }
    }

    private fun slider(t: String, min: Int, max: Int, value: Int, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(10f), dpi(16f), dpi(6f))
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(title(t), LinearLayout.LayoutParams(0, -2, 1f))
        val v = TextView(this).apply { text = "$value dp"; textSize = 15f; setTextColor(accent) }
        top.addView(v)
        box.addView(top)
        box.addView(SeekBar(this).apply {
            this.max = max - min
            progress = value - min
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    v.text = "${p + min} dp"
                    if (fromUser) onChange(p + min)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return box
    }

    private fun segment(t: String, options: List<String>, current: Int, onChange: (Int) -> Unit): View = rowBase().apply {
        addView(title(t), LinearLayout.LayoutParams(0, -2, 1f))
        val group = LinearLayout(this@SettingsActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = dp(18f); setColor(if (dark) 0xFF2C2C30.toInt() else 0xFFEDEDF1.toInt()) }
            setPadding(dpi(3f), dpi(3f), dpi(3f), dpi(3f))
        }
        val views = ArrayList<TextView>()
        fun mark(sel: Int) = views.forEachIndexed { i, tv ->
            tv.background = if (i == sel) GradientDrawable().apply { cornerRadius = dp(15f); setColor(accent) } else null
            tv.setTextColor(if (i == sel) Color.WHITE else label)
        }
        options.forEachIndexed { i, o ->
            val tv = TextView(this@SettingsActivity).apply {
                text = o
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dpi(16f), dpi(6f), dpi(16f), dpi(6f))
                setOnClickListener { mark(i); onChange(i) }
            }
            views.add(tv)
            group.addView(tv)
        }
        mark(current)
        addView(group)
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
        }
        p.shader = LinearGradient(0f, 0f, w, h, c1, c2, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18 * d, 18 * d, p)
        p.shader = null
        // Mini-Uhr
        p.color = Color.WHITE
        p.textAlign = Paint.Align.CENTER
        p.textSize = 22 * d
        p.typeface = Looks.font(context, look, if (look == Look.ONEUI || look == Look.COLOROS) 250 else 700, display = true)
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
        // Dock
        val dy = h - 52 * d
        if (look == Look.APPLE || look == Look.GALAXY || look == Look.COLOROS) {
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
        // Name
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
