package de.myria.island

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), IslandController.Host {

    private lateinit var pal: Palette
    private lateinit var preview: IslandView
    private lateinit var previewCtl: IslandController
    private lateinit var a11yStatus: TextView
    private lateinit var notifStatus: TextView
    private lateinit var runningBadge: TextView
    private val prefs by lazy { Prefs.sp(this) }
    private val D = IslandConfig()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        previewCtl.cfg = Prefs.load(this).copy(hideLandscape = false)
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density)
    private fun dpi(v: Float) = dp(v).toInt()

    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        pal = Palette(dark)
        styleWindow(dark)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(40f))
        }

        root.addView(header())
        root.addView(previewCard())

        root.addView(section("Berechtigungen", footer =
            "Android 13+: Ist „Island“ in den Bedienungshilfen ausgegraut? → App-Info öffnen → ⋮ oben rechts → " +
                "„Eingeschränkte Einstellungen zulassen“. Danach geht's.",
            rows = listOf(
                statusRow("Bedienungshilfe", "Zeichnet die Island über der Statusleiste") {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }.also { a11yStatus = it.second }.first,
                statusRow("Mitteilungen & Musik", "Für Benachrichtigungen und Now Playing") {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }.also { notifStatus = it.second }.first,
                navRow("App-Info öffnen") {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                },
            )))

        root.addView(section("Anzeige", footer =
            "„Nur bei Bedarf“ = die Island taucht nur auf, wenn Musik läuft, eine Nachricht kommt usw. – " +
                "wie bei Apple. „Immer“ = die schwarze Pille bleibt dauerhaft um die Kamera.",
            rows = listOf(
                switchRow("Island aktiv", Prefs.ENABLED, D.enabled),
                segmentRow(listOf("Nur bei Bedarf", "Immer sichtbar"),
                    if (prefs.getBoolean(Prefs.ALWAYS, D.alwaysVisible)) 1 else 0) {
                    prefs.edit().putBoolean(Prefs.ALWAYS, it == 1).apply()
                },
                switchRow("Ruhezustand durchklickbar", Prefs.IDLE_TOUCH_THROUGH, D.idleTouchThrough,
                    "Berührungen gehen durch die leere Pille durch"),
                switchRow("Im Querformat ausblenden", Prefs.HIDE_LANDSCAPE, D.hideLandscape),
            )))

        root.addView(section("Größe & Position", footer =
            "Mit „An Kamera ausrichten“ sucht Island die Kamera-Aussparung automatisch. Die Regler verschieben dann nur noch fein.",
            rows = listOf(
                switchRow("An Kamera ausrichten", Prefs.AUTO_CAMERA, D.autoCamera),
                sliderRow("Breite", Prefs.WIDTH, D.widthDp, 60, 220) { "$it dp" },
                sliderRow("Höhe", Prefs.HEIGHT, D.heightDp, 22, 52) { "$it dp" },
                sliderRow("Vertikal", Prefs.OFFSET_Y, D.offsetYDp, -30, 80) { signed(it) },
                sliderRow("Horizontal", Prefs.OFFSET_X, D.offsetXDp, -160, 160) { signed(it) },
                navRow("Auf Standard zurücksetzen", destructive = true) {
                    Prefs.resetGeometry(this)
                    recreate()
                },
            )))

        root.addView(section("Stil", rows = listOf(
            segmentRow(listOf("Schwarz", "Liquid Glass"), prefs.getInt(Prefs.STYLE, D.style)) {
                prefs.edit().putInt(Prefs.STYLE, it).apply()
            },
            switchRow("Farbiges Leuchten", Prefs.GLOW, D.glow, "Glow in der Farbe des Album-Covers"),
            switchRow("Animierte Wellenform", Prefs.WAVEFORM, D.waveform),
        )))

        root.addView(section("Animation", footer = "Tipp: Bounce hoch + Tempo hoch = extra iPhone-Feeling.", rows = listOf(
            sliderRow("Bounce", Prefs.BOUNCE, D.bounce, 0, 100) { "$it %" },
            sliderRow("Tempo", Prefs.SPEED, D.speed, 0, 100) { "$it %" },
        )))

        root.addView(section("Live-Aktivitäten", rows = listOf(
            switchRow("Musik", Prefs.MUSIC, D.showMusic, "Cover, Wellenform, Steuerung"),
            switchRow("Benachrichtigungen", Prefs.NOTIFS, D.showNotifications),
            sliderRow("Benachrichtigung zeigen", Prefs.NOTIF_SECONDS, D.notifSeconds, 2, 10) { "$it s" },
            switchRow("Laden", Prefs.CHARGING, D.showCharging),
            switchRow("Laden dauerhaft anzeigen", Prefs.CHARGING_PERSISTENT, D.chargingPersistent,
                "Solange das Kabel steckt"),
            switchRow("Lautlos / Klingeln", Prefs.RINGER, D.showRinger),
        )))

        root.addView(section("Verhalten", footer =
            "Ist „Tippen erweitert“ aus, öffnet ein Tipp direkt die App – langes Drücken erweitert dann. " +
                "Wischen nach oben schließt die Island.",
            rows = listOf(
                switchRow("Tippen erweitert", Prefs.TAP_EXPANDS, D.tapExpands),
                switchRow("Neuen Song kurz groß zeigen", Prefs.AUTO_EXPAND_TRACK, D.autoExpandTrack),
                switchRow("Haptisches Feedback", Prefs.HAPTICS, D.haptics),
            )))

        root.addView(TextView(this).apply {
            text = "Island · made for Myria ✦ Ahoj! 🇨🇿"
            textSize = 13f
            setTextColor(pal.secondary)
            gravity = Gravity.CENTER
            setPadding(0, dpi(28f), 0, 0)
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(pal.bg)
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(root)
        }
        setContentView(scroll)

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        previewCtl.cfg = Prefs.load(this).copy(hideLandscape = false)
        previewCtl.setMusic(demoMusic())
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        previewCtl.release()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun styleWindow(dark: Boolean) {
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        var flags = 0
        if (!dark) {
            flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = flags
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    private fun a11yEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.split(':').any { it.startsWith("$packageName/") }
    }

    private fun notifEnabled(): Boolean =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun updateStatus() {
        fun apply(tv: TextView, on: Boolean) {
            tv.text = if (on) "Aktiv" else "Einschalten ›"
            tv.setTextColor(if (on) pal.green else pal.blue)
        }
        apply(a11yStatus, a11yEnabled())
        apply(notifStatus, notifEnabled())
        val running = a11yEnabled()
        runningBadge.text = if (running) "● Läuft" else "○ Aus"
        runningBadge.setTextColor(if (running) pal.green else pal.secondary)
    }

    // ------------------------------------------------------------------
    // Kopf + Vorschau
    // ------------------------------------------------------------------

    private fun header(): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(4f), dpi(18f), dpi(4f), dpi(14f))
        }
        val titleRow = hRow(this)
        titleRow.addView(TextView(this).apply {
            text = "Island"
            textSize = 34f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(pal.label)
            letterSpacing = -0.01f
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        runningBadge = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dpi(10f), dpi(5f), dpi(10f), dpi(5f))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(pal.card)
            }
        }
        titleRow.addView(runningBadge)
        box.addView(titleRow)
        box.addView(TextView(this).apply {
            text = "Deine Dynamic Island für Android"
            textSize = 15f
            setTextColor(pal.secondary)
        })
        return box
    }

    private fun previewCard(): View {
        val card = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF2B1B5E.toInt(), 0xFF7B3FA0.toInt(), 0xFFE0688F.toInt(), 0xFFF7A86B.toInt()),
            ).apply { cornerRadius = dp(28f) }
            clipToOutline = true
        }
        // Liquid-Glass-Blobs im Hintergrund
        card.addView(object : View(this) {
            private val pp = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: Canvas) {
                fun blob(x: Float, y: Float, r: Float, color: Int) {
                    pp.shader = RadialGradient(x, y, r, color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
                    c.drawCircle(x, y, r, pp)
                }
                blob(width * 0.15f, height * 0.85f, width * 0.5f, 0x664FC3F7)
                blob(width * 0.9f, height * 0.2f, width * 0.45f, 0x55FFD1E8)
            }
        }, FrameLayout.LayoutParams(-1, -1))

        card.addView(TextView(this).apply {
            text = "9:41"
            textSize = 15f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
        }, FrameLayout.LayoutParams(-2, -2).apply {
            leftMargin = dpi(26f); topMargin = dpi(18f)
        })

        preview = IslandView(this, topInset = dp(12f))
        previewCtl = IslandController(preview, this)
        card.addView(preview, FrameLayout.LayoutParams(-1, -1))

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpi(14f), 0, dpi(14f), 0)
        }
        fun chip(t: String, f: () -> Unit) {
            chips.addView(glassChip(this, t, f), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(8f) })
        }
        chip("♫  Musik") { toggleDemoMusic() }
        chip("💬  Nachricht") { demoNotif() }
        chip("⚡  Laden") { demoCharging() }
        chip("🔕  Lautlos") { demoRinger() }
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        card.addView(hs, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dpi(16f) })

        val hint = TextView(this).apply {
            text = "Tipp auf die Chips → testet die Vorschau UND die echte Island oben."
            textSize = 13f
            setTextColor(pal.secondary)
            setPadding(dpi(6f), dpi(8f), dpi(6f), 0)
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(card, LinearLayout.LayoutParams(-1, dpi(290f)))
            addView(hint)
        }
    }

    // ------------------------------------------------------------------
    // Demo-Inhalte
    // ------------------------------------------------------------------

    private data class Track(val title: String, val artist: String, val c1: Int, val c2: Int, val dur: Long)

    private val tracks = listOf(
        Track("Neon Herz", "Myria", 0xFFFF4F9A.toInt(), 0xFF6A3DF0.toInt(), 201_000),
        Track("Praha v noci", "Myria", 0xFF2EC5FF.toInt(), 0xFF2B2E83.toInt(), 187_000),
        Track("Glass Hearts", "Myria & Friends", 0xFFFFB13B.toInt(), 0xFFE5446D.toInt(), 224_000),
    )
    private var trackIndex = 0
    private var demoPlaying = true
    private var demoOn = true
    private var demoPos = 42_000L
    private var demoPosTime = SystemClock.elapsedRealtime()
    private val artCache = HashMap<Int, Bitmap>()
    private var ringerSilent = false

    private fun art(i: Int): Bitmap = artCache.getOrPut(i) {
        val t = tracks[i]
        val s = 240
        val b = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val pp = Paint(Paint.ANTI_ALIAS_FLAG)
        pp.shader = LinearGradient(0f, 0f, s.toFloat(), s.toFloat(), t.c1, t.c2, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s.toFloat(), s.toFloat(), pp)
        pp.shader = RadialGradient(s * 0.7f, s * 0.3f, s * 0.45f, 0x88FFFFFF.toInt(), 0, Shader.TileMode.CLAMP)
        c.drawCircle(s * 0.7f, s * 0.3f, s * 0.45f, pp)
        b
    }

    private fun demoPosNow(): Long =
        if (demoPlaying) demoPos + (SystemClock.elapsedRealtime() - demoPosTime) else demoPos

    private val demoActions = object : MediaActions {
        override fun playPause() {
            demoPos = demoPosNow()
            demoPosTime = SystemClock.elapsedRealtime()
            demoPlaying = !demoPlaying
            pushDemo()
        }
        override fun next() = switchTrack(1)
        override fun previous() = switchTrack(-1)
        override fun open() {
            Toast.makeText(this@MainActivity, "Hier würde jetzt deine Musik-App aufgehen 🎧", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchTrack(dir: Int) {
        trackIndex = (trackIndex + dir + tracks.size) % tracks.size
        demoPos = 0
        demoPosTime = SystemClock.elapsedRealtime()
        demoPlaying = true
        pushDemo()
    }

    private fun demoMusic(): MusicInfo {
        val t = tracks[trackIndex]
        val a = art(trackIndex)
        return MusicInfo(
            id = "demo$trackIndex", title = t.title, artist = t.artist, art = a,
            accent = Colors.accentOf(a), playing = demoPlaying, durationMs = t.dur,
            positionMs = demoPosNow() % t.dur, positionTime = SystemClock.elapsedRealtime(),
            speed = 1f, actions = demoActions,
        )
    }

    private fun pushDemo() {
        if (demoOn) previewCtl.setMusic(demoMusic())
    }

    private fun toggleDemoMusic() {
        demoOn = !demoOn || !demoPlaying
        demoPlaying = true
        demoPosTime = SystemClock.elapsedRealtime()
        if (demoOn) {
            val m = demoMusic()
            previewCtl.setMusic(m)
            IslandBus.testMusic(m)
        } else {
            previewCtl.setMusic(null)
        }
    }

    private fun demoNotif() {
        val icon = try {
            NotificationService.drawableToBitmap(applicationInfo.loadIcon(packageManager), dpi(48f))
        } catch (_: Exception) { null }
        val n = Content.Notif(
            icon = icon,
            app = "Island",
            title = "Anna 💜",
            text = "Ahoj! Jak se máš? Kaffee später? ☕",
        ) {
            Toast.makeText(applicationContext, "Würde jetzt den Chat öffnen 💬", Toast.LENGTH_SHORT).show()
        }
        previewCtl.showTransient(n)
        IslandBus.event(n)
    }

    private fun demoCharging() {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: 76
        val c = Content.Charging(lvl, true)
        previewCtl.showTransient(c)
        IslandBus.event(c)
    }

    private fun demoRinger() {
        ringerSilent = !ringerSilent
        val c = Content.Ringer(silent = ringerSilent, vibrate = false)
        previewCtl.showTransient(c)
        IslandBus.event(c)
    }

    // ------------------------------------------------------------------
    // Host (Vorschau)
    // ------------------------------------------------------------------

    override fun haptic(strong: Boolean) {
        preview.performHapticFeedback(
            if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK
        )
    }

    // ------------------------------------------------------------------
    // iOS-Listen-Bausteine
    // ------------------------------------------------------------------

    private fun section(title: String?, rows: List<View>, footer: String? = null): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (title != null) {
            box.addView(TextView(this).apply {
                text = title.uppercase()
                textSize = 13f
                setTextColor(pal.secondary)
                letterSpacing = 0.02f
                setPadding(dpi(16f), dpi(26f), dpi(16f), dpi(7f))
            })
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14f)
                setColor(pal.card)
            }
            clipToOutline = true
        }
        rows.forEachIndexed { i, v ->
            if (i > 0) {
                card.addView(View(this).apply { setBackgroundColor(pal.separator) },
                    LinearLayout.LayoutParams(-1, maxOf(1, dpi(0.5f))).apply { leftMargin = dpi(16f) })
            }
            card.addView(v)
        }
        box.addView(card)
        if (footer != null) {
            box.addView(TextView(this).apply {
                text = footer
                textSize = 13f
                setTextColor(pal.secondary)
                setLineSpacing(0f, 1.15f)
                setPadding(dpi(16f), dpi(7f), dpi(16f), 0)
            })
        }
        return box
    }

    private fun baseRow(): LinearLayout = hRow(this).apply {
        minimumHeight = dpi(50f)
        setPadding(dpi(16f), dpi(8f), dpi(14f), dpi(8f))
    }

    private fun labelBlock(label: String, sub: String?): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply {
            text = label
            textSize = 17f
            setTextColor(pal.label)
        })
        if (sub != null) col.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(pal.secondary)
        })
        return col
    }

    private fun switchRow(label: String, key: String, def: Boolean, sub: String? = null): View {
        val row = baseRow()
        row.addView(labelBlock(label, sub), LinearLayout.LayoutParams(0, -2, 1f))
        val sw = IosSwitch(this, pal)
        sw.set(prefs.getBoolean(key, def))
        sw.onChange = { prefs.edit().putBoolean(key, it).apply() }
        row.addView(sw)
        row.setOnClickListener { sw.performClick() }
        return row
    }

    private fun sliderRow(label: String, key: String, def: Int, min: Int, max: Int, fmt: (Int) -> String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), dpi(10f), dpi(14f), dpi(6f))
        }
        val top = hRow(this)
        top.addView(TextView(this).apply {
            text = label
            textSize = 17f
            setTextColor(pal.label)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val value = TextView(this).apply {
            textSize = 15f
            setTextColor(pal.secondary)
        }
        top.addView(value)
        col.addView(top)
        val s = IosSlider(this, pal, min, max)
        val cur = prefs.getInt(key, def)
        s.set(cur)
        value.text = fmt(cur)
        s.onChange = {
            value.text = fmt(it)
            prefs.edit().putInt(key, it).apply()
        }
        col.addView(s, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4f) })
        return col
    }

    private fun segmentRow(labels: List<String>, current: Int, onChange: (Int) -> Unit): View {
        val box = FrameLayout(this).apply { setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(10f)) }
        val seg = Segmented(this, pal, labels)
        seg.set(current)
        seg.onChange = onChange
        box.addView(seg, FrameLayout.LayoutParams(-1, -2))
        return box
    }

    private fun statusRow(label: String, sub: String, onClick: () -> Unit): Pair<View, TextView> {
        val row = baseRow()
        row.addView(labelBlock(label, sub), LinearLayout.LayoutParams(0, -2, 1f))
        val st = TextView(this).apply {
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        row.addView(st)
        row.setOnClickListener { onClick() }
        row.background = pressable()
        return row to st
    }

    private fun navRow(label: String, destructive: Boolean = false, onClick: () -> Unit): View {
        val row = baseRow()
        row.addView(TextView(this).apply {
            text = label
            textSize = 17f
            setTextColor(if (destructive) 0xFFFF453A.toInt() else pal.blue)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { onClick() }
        row.background = pressable()
        return row
    }

    private fun pressable(): android.graphics.drawable.Drawable {
        val attrs = intArrayOf(android.R.attr.selectableItemBackground)
        val ta = obtainStyledAttributes(attrs)
        val d = ta.getDrawable(0)
        ta.recycle()
        return d ?: GradientDrawable()
    }

    private fun signed(v: Int) = if (v > 0) "+$v dp" else "$v dp"

    @Suppress("unused")
    private val sdk = Build.VERSION.SDK_INT
}
