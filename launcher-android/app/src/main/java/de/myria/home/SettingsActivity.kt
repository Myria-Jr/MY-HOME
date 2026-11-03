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
    private fun motionDuration(base: Long) = if (prefs.motionScale == 0) 0L else (base * prefs.motionScale / 100f).toLong().coerceAtLeast(80L)

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

    /** Aktuelle Unterseite (null = Hauptseite) */
    private var page: String? = null

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page != null) open(null) else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        // z. B. nach dem Erlauben der Benachrichtigungspunkte
        if (page == "badges") open("badges", animate = false)
    }

    private fun build() = open(null, animate = false)

    private fun open(p: String?, animate: Boolean = true) {
        val forward = p != null
        page = p
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(18f), dpi(16f), dpi(48f))
        }
        if (p == null) mainPage(col) else {
            col.addView(TextView(this).apply {
                text = "‹  Lumora Home"
                textSize = 17f
                setTextColor(accent)
                setPadding(dpi(2f), dpi(4f), 0, dpi(10f))
                setOnClickListener { open(null) }
            })
            col.addView(TextView(this).apply {
                text = pages.firstOrNull { it.id == p }?.title ?: ""
                textSize = 32f
                typeface = Looks.font(this@SettingsActivity, Look.APPLE, 800)
                setTextColor(label)
                setPadding(dpi(4f), 0, 0, dpi(8f))
            })
            pages.firstOrNull { it.id == p }?.build?.invoke(col)
        }
        val sv = ScrollView(this).apply { setBackgroundColor(bg); addView(col) }
        setContentView(sv)
        if (animate) {
            sv.translationX = (if (forward) 1 else -1) * dp(60f)
            sv.alpha = 0f
            sv.animate().translationX(0f).alpha(1f).setDuration(260)
                .setDuration(motionDuration(260L))
                .setInterpolator(android.view.animation.PathInterpolator(0.2f, 0.9f, 0.1f, 1f)).start()
        }
        updatePreview()
    }

    private class Page(val id: String, val title: String, val icon: String, val color: Int, val build: (LinearLayout) -> Unit)

    private val pages: List<Page> by lazy { listOf(
        Page("icons", "App-Symbole", "◆", 0xFF5E5CE6.toInt()) { iconsPage(it) },
        Page("home", "Startbildschirm", "▦", 0xFF0A84FF.toInt()) { homePage(it) },
        Page("dock", "Dock & Suche", "▬", 0xFF30B0C7.toInt()) { dockPage(it) },
        Page("drawer", "App-Übersicht", "⋮⋮", 0xFF34C759.toInt()) { drawerPage(it) },
        Page("badges", "Benachrichtigungspunkte", "●", 0xFFFF3B30.toInt()) { badgesPage(it) },
        Page("gestures", "Gesten", "☝", 0xFFFF9F0A.toInt()) { gesturesPage(it) },
        Page("hidden", "Ausgeblendete Apps", "◌", 0xFF8E8E93.toInt()) { hiddenPage(it) },
        Page("reset", "Zurücksetzen", "↺", 0xFF636366.toInt()) { resetPage(it) },
    ) }

    private fun mainPage(col: LinearLayout) {
        col.addView(TextView(this).apply {
            text = "Lumora Home"
            textSize = 34f
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
        header(col, "Startbildschirm")
        col.addView(card(listOf(
            labeled("Modus", chips(listOf("Minimalistisch", "Normal"), if (prefs.minimalMode) 0 else 1) {
                prefs.minimalMode = it == 0
                changed()
            }),
        )))
        hint(col, "Minimalistisch zeigt Uhrzeit und deine Favoriten als schlichte Textliste. Nach oben wischen öffnet weiter die App-Übersicht.")
        header(col, "Schnellstart")
        col.addView(card(listOf(
            action("Apple-Look anwenden", 0xFF55555A.toInt()) { applyLookPreset(Look.APPLE) },
            action("Pixel-/Lawnchair-Look anwenden", 0xFF6750A4.toInt()) { applyLookPreset(Look.ANDROID) },
            action("Nothing-Look anwenden", 0xFF202124.toInt()) { applyLookPreset(Look.NOTHING) },
            action("Liquid-Glass-Look anwenden", 0xFF61758F.toInt()) { applyLookPreset(Look.LIQUID_GLASS) },
        )))
        hint(col, "Jedes Preset richtet Raster, Icons, Dock, Suche und Gesten passend ein. Danach kannst du jede Option einzeln ändern.")

        if (!isDefaultHome()) col.addView(card(listOf(
            action("Als Standard-Startbildschirm festlegen", accent) {
                try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
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

        header(col, "Einstellungen")
        col.addView(card(pages.take(6).map { navRow(it) }))
        col.addView(card(pages.drop(6).map { navRow(it) }), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })
        col.addView(card(listOf(
            action("Hintergrundbild ändern", accent) {
                try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild")) } catch (_: Exception) {}
            },
        )), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })
        hint(col, "Tipps: App gedrückt halten → Shortcuts, Ordner, Umbenennen · Nach links wischen → Heute-Seite mit Widgets · Nach oben wischen → alle Apps")
    }

    private fun applyLookPreset(target: Look) {
        prefs.look = target
        prefs.iconStyle = IconStyle.STANDARD
        prefs.labels = true
        prefs.labelSize = 12
        prefs.labelFont = -1
        prefs.dock = true
        prefs.search = true
        prefs.clock = true
        prefs.todayPage = true
        prefs.drawerList = false
        prefs.drawerSort = 0
        prefs.drawerOpacity = 100
        prefs.doubleTap = 1
        prefs.swipeUpAction = 4
        prefs.pageEffect = 0

        when (target) {
            Look.APPLE -> {
                prefs.shape = Shape.SQUIRCLE.ordinal
                prefs.monoIcons = false
                prefs.columns = 4
                prefs.iconDp = 58
                prefs.allOnHome = true
                prefs.dockCount = 4
                prefs.dockIconDp = 52
                prefs.dockLabels = false
                prefs.dockBackground = true
                prefs.drawerColumns = 4
                prefs.drawerIconDp = 54
                prefs.swipeDownSearch = true
            }
            Look.NOTHING -> {
                prefs.shape = Shape.CIRCLE.ordinal
                prefs.monoIcons = true
                prefs.columns = 4
                prefs.iconDp = 52
                prefs.allOnHome = false
                prefs.dockCount = 5
                prefs.dockIconDp = 48
                prefs.dockLabels = false
                prefs.dockBackground = false
                prefs.drawerColumns = 4
                prefs.drawerIconDp = 50
                prefs.swipeDownSearch = false
            }
            Look.LIQUID_GLASS -> {
                prefs.shape = Shape.SQUIRCLE.ordinal
                prefs.iconStyle = IconStyle.CLEAR
                prefs.monoIcons = false
                prefs.columns = 4
                prefs.iconDp = 56
                prefs.allOnHome = false
                prefs.dockCount = 4
                prefs.dockIconDp = 52
                prefs.dockLabels = false
                prefs.dockBackground = true
                prefs.drawerColumns = 4
                prefs.drawerIconDp = 52
                prefs.drawerOpacity = 100
                prefs.glassStrength = 72
                prefs.motionScale = 100
                prefs.swipeDownSearch = true
            }
            else -> {
                prefs.shape = Shape.SYSTEM.ordinal
                prefs.monoIcons = false
                prefs.columns = 5
                prefs.iconDp = 52
                prefs.allOnHome = false
                prefs.dockCount = 5
                prefs.dockIconDp = 48
                prefs.dockLabels = false
                prefs.dockBackground = true
                prefs.drawerColumns = 5
                prefs.drawerIconDp = 48
                prefs.swipeDownSearch = false
            }
        }

        Looks.clearCache()
        val styleName = when (target) {
            Look.APPLE -> "Apple"
            Look.NOTHING -> "Nothing"
            Look.LIQUID_GLASS -> "Liquid Glass"
            else -> "Pixel-/Lawnchair"
        }
        Toast.makeText(this, "$styleName-Layout aktiviert", Toast.LENGTH_SHORT).show()
        open(null, animate = false)
    }

    private fun isDefaultHome(): Boolean = try {
        val ri = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        ri?.activityInfo?.packageName == packageName
    } catch (_: Exception) { false }

    /** Zeile mit farbigem Symbol-Quadrat wie in den iOS-Einstellungen */
    private fun navRow(pg: Page): View = rowBase().apply {
        addView(TextView(this@SettingsActivity).apply {
            text = pg.icon
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply { cornerRadius = dp(8f); setColor(pg.color) }
        }, LinearLayout.LayoutParams(dpi(30f), dpi(30f)).apply { rightMargin = dpi(14f) })
        addView(title(pg.title), LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@SettingsActivity).apply {
            text = navValue(pg.id)
            textSize = 15f
            setTextColor(secondary)
            setPadding(0, 0, dpi(8f), 0)
        })
        addView(TextView(this@SettingsActivity).apply { text = "›"; textSize = 22f; setTextColor(secondary) })
        setOnClickListener { open(pg.id) }
    }

    private fun navValue(id: String): String = when (id) {
        "icons" -> (Shape.values().getOrNull(prefs.shape)?.title ?: "Auto") + " · " + IconStyle.names[prefs.iconStyle.coerceIn(0, 3)]
        "home" -> "${prefs.columns} Spalten"
        "drawer" -> "${prefs.drawerColumns} Spalten · " + listOf("A–Z", "Meistgenutzt", "Neueste")[prefs.drawerSort.coerceIn(0, 2)]
        "badges" -> if (!prefs.badges) "Aus" else if (BadgeService.granted(this)) "An" else "Erlauben"
        "hidden" -> prefs.hidden.size.takeIf { it > 0 }?.toString() ?: ""
        else -> ""
    }

    private fun iconsPage(col: LinearLayout) {
        col.addView(preview())
        // Icon-Pack
        header(col, "Icon-Pack")
        val packs = IconPacks.installed(this)
        val cur = prefs.iconPack
        val packNames = listOf("Keins") + packs.map { it.label }
        val curIdx = packs.indexOfFirst { it.pkg == cur } + 1
        col.addView(card(listOf(
            labeled("Installierte Icon-Packs", chips(packNames, curIdx) { i ->
                prefs.iconPack = if (i == 0) null else packs[i - 1].pkg
                Thread { Apps.load(this); main.post { changed() } }.start()
            }),
        )))
        hint(col, if (packs.isEmpty()) "Kein Icon-Pack installiert. Lade eins aus dem Play Store (z. B. „Delta“, „Lines“, „Whicons“, „Arcticons“) – es erscheint dann hier."
            else "Icon-Packs funktionieren wie bei Nova & Lawnchair. Apps ohne Pack-Icon behalten ihr Original.")

        header(col, "Form & Stil")
        val shapeChips = ShapePicker(this, prefs, dark) { changed() }
        tintRow = colorDots()
        tintRow.visibility = if (prefs.iconStyle == IconStyle.TINTED) View.VISIBLE else View.GONE
        col.addView(card(listOf(
            labeled("Form", HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(shapeChips) }),
            labeled("Icon-Farbmodus", chips(IconStyle.names, prefs.iconStyle) {
                prefs.iconStyle = it
                tintRow.visibility = if (it == IconStyle.TINTED) View.VISIBLE else View.GONE
                changed()
            }),
            tintRow,
            slider("Größe", 36, 80, prefs.iconDp, "dp") { prefs.iconDp = it; changed() },
            switch("Schwarz-Weiß", prefs.monoIcons) { prefs.monoIcons = it; changed() },
        )))
        hint(col, "„Original“ zeigt die Icons genau so, wie dein Handy sie liefert. „Getönt“ färbt alle Icons in deiner Farbe, „Klar“ macht sie zu Glas – wie bei iOS 18/26.")

        header(col, "App-Namen")
        col.addView(card(listOf(
            switch("Namen zeigen", prefs.labels) { prefs.labels = it; changed() },
            slider("Schriftgröße", 9, 16, prefs.labelSize, "sp") { prefs.labelSize = it; changed() },
            labeled("Schrift", chips(listOf("Auto") + Looks.labelFonts, prefs.labelFont + 1) { prefs.labelFont = it - 1; changed() }),
        )))
        hint(col, "Einzelne Apps umbenennen: App gedrückt halten → „Umbenennen“.")
    }

    private fun homePage(col: LinearLayout) {
        col.addView(card(listOf(
            slider("Spalten", 3, 8, prefs.columns, "") { prefs.columns = it; changed() },
            switch("Uhr-Widget", prefs.clock) { prefs.clock = it },
            labeled("Widget-Stil", chips(listOf("Passend") + Look.values().map { it.title }, prefs.widgetStyle + 1) { prefs.widgetStyle = it - 1 }),
            switch("„Heute“-Seite links (Widgets)", prefs.todayPage) { prefs.todayPage = it },
            switch("Alle Apps auf Startseiten (wie iOS)", prefs.allOnHome) { prefs.allOnHome = it },
            labeled("Blätter-Effekt", chips(listOf("Schieben", "Zoom", "Würfel", "Stapel"), prefs.pageEffect) { prefs.pageEffect = it }),
        )))
        header(col, "Hintergrund & Anzeige")
        col.addView(card(listOf(
            slider("Hintergrund abdunkeln", 0, 70, prefs.dim, "%") { prefs.dim = it },
            switch("Dunkle Schrift (helle Hintergründe)", prefs.darkText) { prefs.darkText = it; changed() },
            switch("Statusleiste ausblenden", prefs.hideStatusBar) { prefs.hideStatusBar = it },
            slider("Animationsgeschwindigkeit", 0, 150, prefs.motionScale, "%") { prefs.motionScale = it },
        )))
        if (prefs.look == Look.LIQUID_GLASS) {
            col.addView(card(listOf(
                slider("Glasintensität", 20, 100, prefs.glassStrength, "%") { prefs.glassStrength = it; changed() },
            )), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })
        }
        hint(col, "Ordner: App gedrückt halten → „In Ordner legen“. Widgets: auf der Heute-Seite „Widget hinzufügen“. Animationen lassen sich abschalten oder beschleunigen.")
    }

    private fun dockPage(col: LinearLayout) {
        col.addView(card(listOf(
            switch("Dock", prefs.dock) { prefs.dock = it },
            slider("Apps im Dock", 3, 8, prefs.dockCount, "") { prefs.dockCount = it },
            slider("Icon-Größe", 36, 80, prefs.dockIconDp, "dp") { prefs.dockIconDp = it },
            switch("App-Namen im Dock", prefs.dockLabels) { prefs.dockLabels = it },
            switch("Dock-Hintergrund", prefs.dockBackground) { prefs.dockBackground = it },
            switch("Suchleiste", prefs.search) { prefs.search = it },
        )))
        hint(col, "Dock-Icon-Größe und Beschriftung lassen sich unabhängig vom Raster einstellen.")
    }

    private fun drawerPage(col: LinearLayout) {
        col.addView(card(listOf(
            labeled("Sortierung", chips(listOf("A–Z", "Meistgenutzt", "Neueste zuerst"), prefs.drawerSort) { prefs.drawerSort = it }),
            labeled("Ansicht", chips(listOf("Raster", "Liste"), if (prefs.drawerList) 1 else 0) { prefs.drawerList = it == 1 }),
            slider("Spalten im Raster", 3, 8, prefs.drawerColumns, "") { prefs.drawerColumns = it },
            slider("Icon-Größe", 36, 80, prefs.drawerIconDp, "dp") { prefs.drawerIconDp = it },
            slider("Hintergrund-Deckkraft", 0, 100, prefs.drawerOpacity, "%") { prefs.drawerOpacity = it },
        )))
        hint(col, "Startbildschirm und App-Übersicht haben eigene Raster- und Icon-Größen. Die A–Z-Schnellwahl erscheint in der Rasteransicht.")
    }

    private fun badgesPage(col: LinearLayout) {
        val ok = BadgeService.granted(this)
        col.addView(card(listOf(
            switch("Punkte auf App-Symbolen", prefs.badges) { prefs.badges = it },
            switch("Anzahl statt Punkt", prefs.badgeCount) { prefs.badgeCount = it },
        )))
        if (!ok) {
            col.addView(card(listOf(
                action("Zugriff auf Mitteilungen erlauben", accent) {
                    try { startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) } catch (_: Exception) {}
                },
            )), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })
            hint(col, "Damit Lumora Home weiß, welche App neue Mitteilungen hat, braucht es Zugriff. Es wird nichts gespeichert oder verschickt.")
        } else hint(col, "✓ Zugriff erteilt.")
    }

    private fun gesturesPage(col: LinearLayout) {
        col.addView(card(listOf(
            labeled("Nach oben wischen", chips(listOf("Nichts", "Suche", "Benachrichtigungen", "Einstellungen", "Apps"), prefs.swipeUpAction) { prefs.swipeUpAction = it }),
            labeled("Doppeltippen auf freie Fläche", chips(listOf("Nichts", "Benachrichtigungen", "Suche", "Einstellungen", "Alle Apps"), prefs.doubleTap) { prefs.doubleTap = it }),
            switch("Nach unten wischen = Suche", prefs.swipeDownSearch) { prefs.swipeDownSearch = it },
        )))
        hint(col, "Nach oben wischen: alle Apps · Seitlich: nächste Seite · Freie Fläche gedrückt halten: Bearbeiten (Wackel-Modus)")
    }

    private fun hiddenPage(col: LinearLayout) {
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
    }

    private fun resetPage(col: LinearLayout) {
        col.addView(card(listOf(
            action("Startbildschirm-Anordnung zurücksetzen", 0xFFFF453A.toInt()) {
                prefs.sp.edit().remove("favorites").remove("dock_apps").apply()
                Toast.makeText(this, "Zurückgesetzt", Toast.LENGTH_SHORT).show()
            },
            action("Alle Design-Einstellungen zurücksetzen", 0xFFFF453A.toInt()) {
                val keep = setOf("favorites", "dock_apps", "hidden", "widgets", "schema")
                val e = prefs.sp.edit()
                prefs.sp.all.keys.filter { it !in keep && !it.startsWith("cnt_") && !it.startsWith("label_") &&
                    !it.startsWith("fname_") && !it.startsWith("fapps_") }.forEach { e.remove(it) }
                e.apply()
                Looks.clearCache()
                open(null, animate = false)
            },
        )))
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
        if (prefs.minimalMode) {
            previewRow.orientation = LinearLayout.VERTICAL
            previewRow.gravity = Gravity.CENTER_VERTICAL
            previewRow.setPadding(dpi(22f), dpi(12f), dpi(22f), dpi(12f))
            previewRow.addView(TextView(this).apply {
                text = "9:41"
                textSize = 34f
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                setTextColor(Color.WHITE)
                includeFontPadding = false
            })
            previewRow.addView(TextView(this).apply {
                text = "Dienstag, 3. November"
                textSize = 11f
                setTextColor(0xCCFFFFFF.toInt())
                setPadding(0, 0, 0, dpi(8f))
            })
            apps.take(4).forEach { app ->
                previewRow.addView(TextView(this).apply {
                    text = prefs.label(app.key, app.label)
                    textSize = 13f
                    typeface = Looks.font(this@SettingsActivity, prefs.look, 500)
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }, LinearLayout.LayoutParams(-1, dpi(26f)))
            }
            return
        }
        previewRow.orientation = LinearLayout.HORIZONTAL
        previewRow.gravity = Gravity.CENTER
        previewRow.setPadding(dpi(10f), dpi(22f), dpi(10f), dpi(22f))
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
                    prefs.monoIcons, a.packIcon))
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
        isFocusable = true
        foreground = android.graphics.drawable.RippleDrawable(
            ColorStateList.valueOf(if (dark) 0x33FFFFFF else 0x22000000),
            null,
            GradientDrawable().apply { setColor(Color.WHITE) },
        )
        setPadding(dpi(16f), dpi(8f), dpi(14f), dpi(8f))
    }

    private fun title(t: String) = TextView(this).apply { text = t; textSize = 16.5f; setTextColor(label) }

    private fun action(t: String, color: Int, onClick: () -> Unit): View = rowBase().apply {
        addView(TextView(this@SettingsActivity).apply { text = t; textSize = 16.5f; setTextColor(color) })
        setOnClickListener { performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onClick() }
    }

    private fun switch(t: String, value: Boolean, onChange: (Boolean) -> Unit): View = rowBase().apply {
        addView(title(t), LinearLayout.LayoutParams(0, -2, 1f))
        // Fix: eigener Schalter – der System-Switch zeichnete auf manchen Handys einen grauen Balken
        val sw = Toggle(this@SettingsActivity, value, accent, if (dark) 0xFF39393D.toInt() else 0xFFE3E3E8.toInt())
        sw.onChange = { v -> sw.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK); onChange(v) }
        addView(sw, LinearLayout.LayoutParams(dpi(51f), dpi(31f)))
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

/** iOS-Schalter (gezeichnet, mit Feder-Animation) */
@SuppressLint("ViewConstructor")
class Toggle(ctx: Context, private var on: Boolean, private val onColor: Int, private val offColor: Int) : View(ctx) {
    var onChange: ((Boolean) -> Unit)? = null
    private var pos = if (on) 1f else 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    init { setOnClickListener { toggle() } }

    fun toggle() {
        on = !on
        android.animation.ValueAnimator.ofFloat(pos, if (on) 1f else 0f).apply {
            duration = 260
            interpolator = android.view.animation.OvershootInterpolator(1.4f)
            addUpdateListener { pos = it.animatedValue as Float; invalidate() }
            start()
        }
        onChange?.invoke(on)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = pos.coerceIn(0f, 1f)
        p.color = android.animation.ArgbEvaluator().evaluate(t, offColor, onColor) as Int
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, h / 2, h / 2, p)
        val pad = h * 0.07f
        val d = h - 2 * pad
        val x = pad + (w - 2 * pad - d) * pos
        p.color = 0x33000000
        c.drawCircle(x + d / 2, h / 2 + h * 0.03f, d / 2, p)
        p.color = Color.WHITE
        c.drawCircle(x + d / 2, h / 2, d / 2, p)
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
        set(v) {
            if (field == v) return
            field = v
            animate().scaleX(if (v) 1.025f else 1f).scaleY(if (v) 1.025f else 1f)
                .setDuration(180).setInterpolator(android.view.animation.OvershootInterpolator(0.7f)).start()
            invalidate()
        }
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
            Look.LIQUID_GLASS -> 0xFF17243A.toInt() to 0xFFB9D8F2.toInt()
            Look.COLOROS -> 0xFF1F6F6A.toInt() to 0xFFF3C38B.toInt()
            Look.HYPEROS -> 0xFF1A2A3A.toInt() to 0xFFFF8A3D.toInt()
            Look.MAGICOS -> 0xFF2A1F4A.toInt() to 0xFF7FD6FF.toInt()
            Look.ORIGINOS -> 0xFF0E3B4A.toInt() to 0xFFB5F2D5.toInt()
        }
        p.shader = LinearGradient(0f, 0f, w, h, c1, c2, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, 18 * d, 18 * d, p)
        p.shader = null
        if (look == Look.LIQUID_GLASS) {
            p.color = 0x36FFFFFF
            r.set(4 * d, 4 * d, w - 4 * d, h * 0.42f)
            c.drawRoundRect(r, 16 * d, 16 * d, p)
            p.color = 0x99FFFFFF.toInt()
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.2f * d
            r.set(2.5f * d, 2.5f * d, w - 2.5f * d, h - 2.5f * d)
            c.drawRoundRect(r, 18 * d, 18 * d, p)
            p.style = Paint.Style.FILL
        }
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
        if (look in listOf(Look.APPLE, Look.GALAXY, Look.COLOROS, Look.MAGICOS, Look.ORIGINOS, Look.LIQUID_GLASS)) {
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
