package de.myria.island

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var pal: Palette
    private lateinit var host: FrameLayout
    private val stack = ArrayList<View>()
    private val prefs by lazy { Prefs.sp(this) }
    private val D = IslandConfig()
    private val main = Handler(Looper.getMainLooper())

    /** Alle Vorschau-Islands (Hauptseite + Unterseiten) */
    private val previews = ArrayList<IslandController>()
    /** Werden in onResume / nach Berechtigungen aufgerufen */
    private val refreshers = ArrayList<() -> Unit>()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        val c = previewCfg()
        previews.forEach { it.cfg = c }
    }

    private fun previewCfg() = Prefs.load(this).copy(hideLandscape = false, enabled = true)

    private fun dp(v: Float) = (v * resources.displayMetrics.density)
    private fun dpi(v: Float) = dp(v).toInt()

    // ==================================================================
    // Lebenszyklus
    // ==================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        pal = Palette(dark)
        styleWindow(dark)

        host = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        setContentView(host)
        val first = mainPage()
        stack.add(first)
        host.addView(first, FrameLayout.LayoutParams(-1, -1))

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onResume() {
        super.onResume()
        refreshers.forEach { it() }
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        previews.forEach { it.release() }
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!pop()) {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    private fun styleWindow(dark: Boolean) {
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.bg
        var flags = 0
        if (!dark) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        window.decorView.systemUiVisibility = flags
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshers.forEach { it() }
        // Service soll neue Berechtigungen sofort nutzen
        prefs.edit().putLong("perm_tick", System.currentTimeMillis()).apply()
    }

    // ==================================================================
    // iOS-Navigation (Push / Pop mit Parallax)
    // ==================================================================

    private val ease = PathInterpolator(0.2f, 0.9f, 0.1f, 1f)

    private fun push(page: View) {
        val prev = stack.lastOrNull()
        stack.add(page)
        host.addView(page, FrameLayout.LayoutParams(-1, -1))
        val w = host.width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        page.translationX = w
        page.animate().translationX(0f).setDuration(420).setInterpolator(ease).start()
        prev?.animate()?.translationX(-w * 0.3f)?.alpha(0.6f)?.setDuration(420)?.setInterpolator(ease)?.start()
    }

    private fun pop(): Boolean {
        if (stack.size <= 1) return false
        val top = stack.removeAt(stack.size - 1)
        val prev = stack.last()
        val w = host.width.toFloat()
        top.animate().translationX(w).setDuration(340).setInterpolator(ease).withEndAction {
            host.removeView(top)
            (top.getTag(TAG_PREVIEW) as? IslandController)?.let { previews.remove(it); it.release() }
        }.start()
        prev.animate().translationX(0f).alpha(1f).setDuration(340).setInterpolator(ease).start()
        refreshers.forEach { it() }
        return true
    }

    /** Seite mit Large Title, der beim Scrollen in die Navigationsleiste wandert. */
    private fun page(title: String, back: String?, build: (LinearLayout) -> Unit): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(pal.bg)
            elevation = dp(10f)
            isClickable = true
        }
        val navH = dp(50f)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), navH.toInt(), dpi(16f), dpi(48f))
        }
        content.addView(TextView(this).apply {
            text = title
            textSize = 34f
            typeface = font(700)
            setTextColor(pal.label)
            letterSpacing = -0.01f
            setPadding(dpi(4f), 0, 0, dpi(6f))
        })
        build(content)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content)
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1))

        val bar = FrameLayout(this)
        val barBg = View(this).apply {
            setBackgroundColor(if (pal.dark) 0xF01C1C1E.toInt() else 0xF0F9F9F9.toInt())
            alpha = 0f
        }
        bar.addView(barBg, FrameLayout.LayoutParams(-1, -1))
        val hair = View(this).apply { setBackgroundColor(pal.separator); alpha = 0f }
        bar.addView(hair, FrameLayout.LayoutParams(-1, maxOf(1, dpi(0.5f)), Gravity.BOTTOM))
        val small = TextView(this).apply {
            text = title
            textSize = 17f
            typeface = font(600)
            setTextColor(pal.label)
            alpha = 0f
        }
        bar.addView(small, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        if (back != null) {
            val b = TextView(this).apply {
                text = "‹ $back"
                textSize = 17f
                setTextColor(pal.blue)
                setPadding(dpi(10f), dpi(8f), dpi(16f), dpi(8f))
                setOnClickListener { pop() }
            }
            bar.addView(b, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START))
        }
        root.addView(bar, FrameLayout.LayoutParams(-1, navH.toInt(), Gravity.TOP))

        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val t = ((y - dp(18f)) / dp(26f)).coerceIn(0f, 1f)
            small.alpha = t
            barBg.alpha = ((y) / dp(12f)).coerceIn(0f, 1f)
            hair.alpha = t
        }
        return root
    }

    private fun font(weight: Int): Typeface =
        if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.SANS_SERIF, weight, false)
        else Typeface.create("sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)

    // ==================================================================
    // Hauptseite
    // ==================================================================

    private fun mainPage(): View = page("Island", null) { c ->
        c.addView(TextView(this).apply {
            text = "Deine Dynamic Island für Android"
            textSize = 15f
            setTextColor(pal.secondary)
            setPadding(dpi(4f), 0, 0, dpi(14f))
        })
        c.addView(previewCard(big = true, root = null))
        c.addView(setupBanner())

        c.addView(section(null, listOf(
            switchRow("Island", Prefs.ENABLED, D.enabled, icon = GlyphIcon(this, 0xFF000000.toInt(), Glyph.ISLAND)),
            segmentRow(listOf("Nur bei Bedarf", "Immer sichtbar"), if (prefs.getBoolean(Prefs.ALWAYS, D.alwaysVisible)) 1 else 0) {
                prefs.edit().putBoolean(Prefs.ALWAYS, it == 1).apply()
            },
        ), footer = "„Nur bei Bedarf“ = wie beim iPhone: die Island taucht nur auf, wenn etwas passiert. „Immer sichtbar“ = die Pille bleibt dauerhaft um die Kamera."))

        c.addView(section(null, listOf(
            navRow("Erscheinungsbild", GlyphIcon(this, 0xFF0A84FF.toInt(), Glyph.PAINT)) { push(appearancePage()) },
            navRow("Größe & Position", GlyphIcon(this, 0xFF30B0C7.toInt(), Glyph.RULER)) { push(geometryPage()) },
            navRow("Animation", GlyphIcon(this, 0xFFBF5AF2.toInt(), Glyph.SPARKLE)) { push(animationPage()) },
        )))
        c.addView(section(null, listOf(
            navRow("Live-Aktivitäten", GlyphIcon(this, 0xFFFF9F0A.toInt(), Glyph.ISLAND)) { push(livePage()) },
            navRow("Musik", GlyphIcon(this, 0xFFFF375F.toInt(), Glyph.NOTE)) { push(musicPage()) },
            navRow("Mitteilungen", GlyphIcon(this, 0xFFFF453A.toInt(), Glyph.BELL)) { push(notifPage()) },
            navRow("Apps", GlyphIcon(this, 0xFF5E5CE6.toInt(), Glyph.GRID)) { push(appsPage()) },
        )))
        c.addView(section(null, listOf(
            navRow("Gesten & Verhalten", GlyphIcon(this, 0xFF32D74B.toInt(), Glyph.HAND)) { push(behaviorPage()) },
            navRow("Datenschutz-Anzeigen", GlyphIcon(this, 0xFF0A84FF.toInt(), Glyph.LOCATION)) { push(privacyPage()) },
            navRow("Berechtigungen", GlyphIcon(this, 0xFF8E8E93.toInt(), Glyph.GEAR)) { push(permissionsPage()) },
            navRow("Info & Fehlerbericht", GlyphIcon(this, 0xFF636366.toInt(), Glyph.INFO)) { push(infoPage()) },
        )))
        c.addView(TextView(this).apply {
            text = "Island · made for Myria ✦ Ahoj! 🇨🇿"
            textSize = 13f
            setTextColor(pal.secondary)
            gravity = Gravity.CENTER
            setPadding(0, dpi(28f), 0, 0)
        })
    }

    /** „Island einrichten“-Karte, solange Berechtigungen fehlen. */
    private fun setupBanner(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val card = section("Einrichten", listOf(
            statusRow("1. Bedienungshilfe", "Zeichnet die Island über der Statusleiste", ::a11yEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            statusRow("2. Mitteilungen & Musik", "Für Benachrichtigungen, Musik, Timer …", ::notifEnabled) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        ), footer = "Android 13+: „Island“ ist in den Bedienungshilfen ausgegraut? → Berechtigungen → App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.")
        box.addView(card)
        refreshers.add { box.visibility = if (a11yEnabled() && notifEnabled()) View.GONE else View.VISIBLE }
        box.visibility = if (a11yEnabled() && notifEnabled()) View.GONE else View.VISIBLE
        return box
    }

    // ==================================================================
    // Unterseiten
    // ==================================================================

    private fun appearancePage() = page("Erscheinungsbild", "Island") { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Stil", listOf(
            segmentRow(listOf("Schwarz", "Liquid Glass", "Farbig"), prefs.getInt(Prefs.STYLE, D.style)) {
                prefs.edit().putInt(Prefs.STYLE, it).apply()
            },
            switchRow("Glas-Rand", Prefs.GLASS_BORDER, D.glassBorder, "Feine Lichtkante bei Liquid Glass"),
            switchRow("Schatten", Prefs.SHADOW, D.shadow),
        ), footer = "Schwarz verschmilzt mit der Kamera – genau wie beim iPhone."))
        c.addView(section("Akzentfarbe", listOf(
            segmentRow(listOf("Cover", "Eigene", "Weiß"), prefs.getInt(Prefs.ACCENT_MODE, D.accentMode)) {
                prefs.edit().putInt(Prefs.ACCENT_MODE, it).apply()
            },
            colorRow(),
        ), footer = "„Cover“ nimmt die Farbe aus dem Album-Cover – für Wellenform und Leuchten."))
        c.addView(section("Leuchten", listOf(
            switchRow("Farbiges Leuchten", Prefs.GLOW, D.glow, "Glow um die Island, wenn Musik läuft"),
            sliderRow("Stärke", Prefs.GLOW_STRENGTH, D.glowStrength, 0, 100) { "$it %" },
        )))
        c.addView(section("Text", listOf(
            segmentRow(listOf("Klein", "Normal", "Groß"), prefs.getInt(Prefs.FONT_SCALE, D.fontScale)) {
                prefs.edit().putInt(Prefs.FONT_SCALE, it).apply()
            },
        )))
    }

    private fun geometryPage() = page("Größe & Position", "Island") { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Position", listOf(
            switchRow("An Kamera ausrichten", Prefs.AUTO_CAMERA, D.autoCamera, "Findet die Kamera-Aussparung automatisch"),
            sliderRow("Vertikal", Prefs.OFFSET_Y, D.offsetYDp, -30, 80) { signed(it) },
            sliderRow("Horizontal", Prefs.OFFSET_X, D.offsetXDp, -160, 160) { signed(it) },
        ), footer = "Mit „An Kamera ausrichten“ verschieben die Regler nur noch fein."))
        c.addView(section("Ruhezustand", listOf(
            sliderRow("Breite", Prefs.WIDTH, D.widthDp, 60, 220) { "$it dp" },
            sliderRow("Höhe", Prefs.HEIGHT, D.heightDp, 22, 52) { "$it dp" },
        )))
        c.addView(section("Aktivitäten", listOf(
            sliderRow("Kompakt-Breite", Prefs.COMPACT_EXTRA, D.compactExtraDp, -40, 80) { signed(it) },
            sliderRow("Erweiterte Breite", Prefs.EXPANDED_WIDTH, D.expandedWidthPct, 60, 100) { "$it %" },
            sliderRow("Ecken-Rundung", Prefs.EXPANDED_RADIUS, D.expandedRadiusDp, 16, 60) { "$it dp" },
        )))
        c.addView(section(null, listOf(
            actionRow("Auf Standard zurücksetzen", destructive = true) {
                Prefs.resetGeometry(this)
                Toast.makeText(this, "Zurückgesetzt", Toast.LENGTH_SHORT).show()
                pop()
            },
        )))
    }

    private fun animationPage() = page("Animation", "Island") { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Federung", listOf(
            sliderRow("Bounce", Prefs.BOUNCE, D.bounce, 0, 100) { "$it %" },
            sliderRow("Tempo", Prefs.SPEED, D.speed, 0, 100) { "$it %" },
        ), footer = "Tipp: Bounce 45 % + Tempo 50 % kommt dem iPhone am nächsten."))
        c.addView(section(null, listOf(
            switchRow("Drück-Effekt", Prefs.SQUISH, D.squish, "Island gibt beim Antippen leicht nach"),
            switchRow("Bewegung reduzieren", Prefs.REDUCE_MOTION, D.reduceMotion, "Kein Nachschwingen, ruhigere Übergänge"),
            actionRow("Animation testen") { demoMusicToggle(); main.postDelayed({ demoNotif() }, 900) },
        )))
    }

    private fun livePage() = page("Live-Aktivitäten", "Island") { c ->
        c.addView(section("Aktivitäten", listOf(
            switchRow("Musik", Prefs.MUSIC, D.showMusic, "Cover, Wellenform, Steuerung"),
            switchRow("Anrufe", Prefs.CALLS, D.showCalls, "Annehmen / Ablehnen direkt in der Island"),
            switchRow("Timer & Stoppuhr", Prefs.TIMER, D.showTimer, "Auch Sprachaufnahmen"),
            switchRow("Navigation", Prefs.NAV, D.showNav, "Google Maps, Waze, OsmAnd …"),
            switchRow("Uploads & Downloads", Prefs.PROGRESS, D.showProgress, "Fortschrittsring + Häkchen"),
            switchRow("Kopfhörer", Prefs.DEVICES, D.showDevices, "Verbinden + Akkustand"),
            switchRow("Entsperren", Prefs.UNLOCK, D.showUnlock, "Schloss-Animation wie bei Face ID"),
        )))
        c.addView(section("Akku", listOf(
            switchRow("Laden", Prefs.CHARGING, D.showCharging, "Animation beim Einstecken"),
            switchRow("Laden dauerhaft anzeigen", Prefs.CHARGING_PERSISTENT, D.chargingPersistent),
            switchRow("Akku schwach", Prefs.LOW_BATTERY, D.showLowBattery, "Warnung bei 20 % und 10 %"),
        )))
        c.addView(section("System", listOf(
            switchRow("Lautlos / Klingeln", Prefs.RINGER, D.showRinger),
            switchRow("Zwei Aktivitäten gleichzeitig", Prefs.SPLIT, D.splitBubble, "Zweite Aktivität als kleine Bubble"),
        ), footer = "Tippe auf die Bubble, um sie zur Haupt-Aktivität zu machen."))
    }

    private fun musicPage() = page("Musik", "Island") { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Anzeige", listOf(
            switchRow("Wellenform animieren", Prefs.WAVEFORM, D.waveform),
            switchRow("Wellenform in Cover-Farbe", Prefs.WAVEFORM_COVER, D.waveformCover),
            switchRow("App-Icon zeigen", Prefs.MUSIC_APP_ICON, D.musicAppIcon, "Spotify, YouTube Music … im großen Player"),
        )))
        c.addView(section("Player", listOf(
            switchRow("Spulen per Ziehen", Prefs.MUSIC_SEEK, D.musicSeek, "Fortschrittsbalken im großen Player ziehen"),
            switchRow("Neuen Song kurz groß zeigen", Prefs.AUTO_EXPAND_TRACK, D.autoExpandTrack),
            sliderRow("Pausiert noch zeigen", Prefs.PAUSED_LINGER, D.pausedLingerSec, 0, 30) { if (it == 0) "Aus" else "$it s" },
        )))
    }

    private fun notifPage() = page("Mitteilungen", "Island") { c ->
        c.addView(section(null, listOf(
            switchRow("Mitteilungen in der Island", Prefs.NOTIFS, D.showNotifications),
            sliderRow("Anzeigedauer", Prefs.NOTIF_SECONDS, D.notifSeconds, 2, 12) { "$it s" },
        )))
        c.addView(section("Welche", listOf(
            switchRow("Auch stille Mitteilungen", Prefs.NOTIF_SILENT, D.notifSilent),
            switchRow("Auch bei „Nicht stören“", Prefs.NOTIF_DND, D.notifDnd),
            navRow("Apps auswählen", null) { push(appsPage()) },
        )))
        c.addView(section("Darstellung", listOf(
            switchRow("Automatisch groß zeigen", Prefs.NOTIF_AUTO_EXPAND, D.notifAutoExpand, "Ganzer Text + Buttons sofort"),
            switchRow("Aktions-Buttons", Prefs.NOTIF_ACTIONS, D.notifActions, "z.B. „Als gelesen markieren“"),
            switchRow("Inhalt verbergen", Prefs.NOTIF_HIDE_CONTENT, D.notifHideContent, "Nur App-Name, kein Text"),
        ), footer = "Runterziehen auf einer Mitteilung zeigt den ganzen Text und die Buttons."))
    }

    private fun behaviorPage() = page("Gesten & Verhalten", "Island") { c ->
        c.addView(section("Gesten", listOf(
            switchRow("Tippen erweitert", Prefs.TAP_EXPANDS, D.tapExpands, "Aus = wie iPhone: Tippen öffnet die App"),
            switchRow("Runterziehen erweitert", Prefs.SWIPE_DOWN, D.swipeDownExpands),
            sliderRow("Automatisch einklappen", Prefs.AUTO_COLLAPSE, D.autoCollapseSec, 3, 30) { "$it s" },
        ), footer = "Gedrückt halten erweitert • Hochwischen klappt ein oder wischt eine Aktivität kurz weg • Außerhalb tippen schließt."))
        c.addView(section("Haptik", listOf(
            switchRow("Haptisches Feedback", Prefs.HAPTICS, D.haptics),
            segmentRow(listOf("Leicht", "Mittel", "Stark"), prefs.getInt(Prefs.HAPTIC_STRENGTH, D.hapticStrength)) {
                prefs.edit().putInt(Prefs.HAPTIC_STRENGTH, it).apply()
            },
        )))
        c.addView(section("Sichtbarkeit", listOf(
            switchRow("Auf dem Sperrbildschirm", Prefs.LOCKSCREEN, D.showOnLockscreen),
            switchRow("Im Querformat ausblenden", Prefs.HIDE_LANDSCAPE, D.hideLandscape, "Videos & Spiele bleiben frei"),
            switchRow("Leere Pille durchklickbar", Prefs.IDLE_TOUCH_THROUGH, D.idleTouchThrough, "Bei „Immer sichtbar“"),
        )))
    }

    private fun privacyPage() = page("Datenschutz-Anzeigen", "Island") { c ->
        c.addView(section("Anzeigen", listOf(
            switchRow("Kamera", Prefs.PRIV_CAMERA, D.privacyCamera, "Grüner Punkt, wenn eine App filmt"),
            switchRow("Mikrofon", Prefs.PRIV_MIC, D.privacyMic, "Oranger Punkt bei Aufnahme"),
            switchRow("Standort", Prefs.PRIV_LOCATION, D.privacyLocation, "Blauer Pfeil, wenn GPS benutzt wird"),
        ), footer = "Die Punkte erscheinen in der Island, sobald irgendeine App Kamera, Mikrofon oder GPS nutzt."))
        c.addView(section("Berechtigung", listOf(
            permRow("Standort erlauben", "Nötig für den Standort-Pfeil", Manifest.permission.ACCESS_FINE_LOCATION),
        )))
    }

    private fun permissionsPage() = page("Berechtigungen", "Island") { c ->
        c.addView(section("Pflicht", listOf(
            statusRow("Bedienungshilfe", "Zeichnet die Island über allem", ::a11yEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            statusRow("Mitteilungen & Musik", "Benachrichtigungen, Musik, Live-Aktivitäten", ::notifEnabled) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        ), footer = "Android 13+: Ist „Island“ ausgegraut? → App-Info öffnen → ⋮ oben rechts → „Eingeschränkte Einstellungen zulassen“."))
        val optional = ArrayList<View>()
        optional.add(permRow("Standort", "Für den Standort-Pfeil", Manifest.permission.ACCESS_FINE_LOCATION))
        if (Build.VERSION.SDK_INT >= 31) {
            optional.add(permRow("Bluetooth", "Für Kopfhörer + Akkustand", Manifest.permission.BLUETOOTH_CONNECT))
        }
        c.addView(section("Optional", optional))
        c.addView(section(null, listOf(
            actionRow("App-Info öffnen") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            },
        )))
    }

    private fun infoPage() = page("Info", "Island") { c ->
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
        c.addView(section(null, listOf(
            valueRow("Version", ver ?: "?"),
            valueRow("Gerät", "${Build.MANUFACTURER} ${Build.MODEL}"),
            valueRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            valueRow("Island läuft", if (IslandBus.serviceRunning) "Ja" else "Nein"),
        )))

        val file = IslandApp.crashFile(this)
        val crash = if (file.exists()) file.readText() else null
        val crashText = TextView(this).apply {
            text = crash ?: "Keine Abstürze gespeichert 🎉"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(pal.secondary)
            setPadding(dpi(16f), dpi(12f), dpi(16f), dpi(12f))
            setTextIsSelectable(true)
            maxLines = 40
        }
        c.addView(section("Fehlerbericht", listOf(
            crashText,
            actionRow("Kopieren") {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Island Fehlerbericht", crashText.text))
                Toast.makeText(this, "Kopiert – schick ihn mir einfach 💌", Toast.LENGTH_SHORT).show()
            },
            actionRow("Löschen", destructive = true) {
                file.delete()
                crashText.text = "Keine Abstürze gespeichert 🎉"
            },
        ), footer = "Stürzt Island ab, landet hier der genaue Grund. Kopieren und an mich schicken → ich fixe es."))

        c.addView(section(null, listOf(
            actionRow("Alle Einstellungen zurücksetzen", destructive = true) {
                AlertDialog.Builder(this)
                    .setTitle("Alles zurücksetzen?")
                    .setMessage("Alle Island-Einstellungen gehen zurück auf Standard.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Zurücksetzen") { _, _ ->
                        Prefs.resetAll(this)
                        recreate()
                    }
                    .show()
            },
        )))
    }

    // ---- App-Auswahl ----

    private fun appsPage() = page("Apps", "Zurück") { c ->
        val search = EditText(this).apply {
            hint = "Suchen"
            textSize = 17f
            setSingleLine()
            setTextColor(pal.label)
            setHintTextColor(pal.secondary)
            setPadding(dpi(14f), dpi(9f), dpi(14f), dpi(9f))
            background = GradientDrawable().apply {
                cornerRadius = dp(10f)
                setColor(pal.fill)
            }
        }
        c.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(6f) })
        c.addView(TextView(this).apply {
            text = "Ausgeschaltete Apps erscheinen nicht in der Island – weder Mitteilungen noch Live-Aktivitäten."
            textSize = 13f
            setTextColor(pal.secondary)
            setPadding(dpi(16f), dpi(6f), dpi(16f), 0)
        })
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(listBox)
        val loading = TextView(this).apply {
            text = "Lädt Apps …"
            setTextColor(pal.secondary)
            gravity = Gravity.CENTER
            setPadding(0, dpi(30f), 0, 0)
        }
        listBox.addView(loading)

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            val apps = pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.applicationInfo }
                .distinctBy { it.packageName }
                .filter { it.packageName != packageName }
                .map { Triple(it.packageName, pm.getApplicationLabel(it).toString(), it) }
                .sortedBy { it.second.lowercase() }
            main.post {
                if (isFinishing) return@post
                listBox.removeAllViews()
                val excluded = Prefs.load(this).excludedApps
                val rows = apps.map { (pkg, label, info) ->
                    val row = baseRow()
                    val iv = ImageView(this)
                    try { iv.setImageDrawable(info.loadIcon(pm)) } catch (_: Exception) {}
                    row.addView(iv, LinearLayout.LayoutParams(dpi(30f), dpi(30f)).apply { rightMargin = dpi(14f) })
                    row.addView(TextView(this).apply {
                        text = label
                        textSize = 17f
                        setTextColor(pal.label)
                        setSingleLine()
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    val sw = IosSwitch(this, pal)
                    sw.set(pkg !in excluded)
                    sw.onChange = { on -> Prefs.setExcluded(this, pkg, !on) }
                    row.addView(sw)
                    row.setOnClickListener { sw.performClick() }
                    row.tag = label.lowercase()
                    row
                }
                val card = section(null, rows)
                listBox.addView(card)
                search.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                    override fun afterTextChanged(s: Editable?) {
                        val q = s?.toString()?.trim()?.lowercase().orEmpty()
                        rows.forEach { r ->
                            r.visibility = if (q.isEmpty() || (r.tag as String).contains(q)) View.VISIBLE else View.GONE
                        }
                    }
                })
            }
        }.start()
    }

    // ==================================================================
    // Vorschau
    // ==================================================================

    private fun previewCard(big: Boolean, root: View?): View {
        val card = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF2B1B5E.toInt(), 0xFF7B3FA0.toInt(), 0xFFE0688F.toInt(), 0xFFF7A86B.toInt()),
            ).apply { cornerRadius = dp(28f) }
            clipToOutline = true
        }
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
            typeface = font(700)
            setTextColor(Color.WHITE)
        }, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(26f); topMargin = dpi(18f) })

        val view = IslandView(this, topInset = dp(12f))
        val ctl = IslandController(view, object : IslandController.Host {
            override fun haptic(strong: Boolean) {
                view.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK)
            }
        })
        card.addView(view, FrameLayout.LayoutParams(-1, -1))
        ctl.cfg = previewCfg()
        if (demoMusicOn) ctl.setMusic(demoMusic())
        ctl.setLive(demoLive.values.toList())
        previews.add(ctl)
        // beim Schließen der Unterseite wieder aufräumen
        root?.post {
            var v: View? = root
            while (v != null && v.parent !== host) v = v.parent as? View
            v?.setTag(TAG_PREVIEW, ctl)
        }

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpi(14f), 0, dpi(14f), 0)
        }
        fun chip(t: String, f: () -> Unit) {
            chips.addView(glassChip(this, t, f), LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(8f) })
        }
        chip("♫  Musik") { demoMusicToggle() }
        chip("💬  Nachricht") { demoNotif() }
        chip("📞  Anruf") { demoCall() }
        chip("⏱  Timer") { demoTimer() }
        chip("⚡  Laden") { demoCharging() }
        chip("🧭  Navi") { demoNav() }
        chip("⬆  Upload") { demoUpload() }
        chip("🎧  AirPods") { demoDevice() }
        chip("🔓  Entsperren") { demoTransient(UnlockContent()) }
        chip("🔕  Lautlos") { demoRinger() }
        chip("📍  Standort") { demoPrivacy() }
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        card.addView(hs, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dpi(16f) })

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(card, LinearLayout.LayoutParams(-1, dpi(if (big) 300f else 270f)))
        if (big) box.addView(TextView(this).apply {
            text = "Chips antippen → testet die Vorschau UND die echte Island oben."
            textSize = 13f
            setTextColor(pal.secondary)
            setPadding(dpi(6f), dpi(8f), dpi(6f), 0)
        })
        return box
    }

    // ==================================================================
    // Demo-Inhalte
    // ==================================================================

    private data class Track(val title: String, val artist: String, val c1: Int, val c2: Int, val dur: Long)

    private val tracks = listOf(
        Track("Neon Herz", "Myria", 0xFFFF4F9A.toInt(), 0xFF6A3DF0.toInt(), 201_000),
        Track("Praha v noci", "Myria", 0xFF2EC5FF.toInt(), 0xFF2B2E83.toInt(), 187_000),
        Track("Glass Hearts", "Myria & Friends", 0xFFFFB13B.toInt(), 0xFFE5446D.toInt(), 224_000),
    )
    private var trackIndex = 0
    private var demoPlaying = true
    private var demoMusicOn = true
    private var demoPos = 42_000L
    private var demoPosTime = SystemClock.elapsedRealtime()
    private val artCache = HashMap<Int, Bitmap>()
    private var ringerSilent = false
    private val demoLive = LinkedHashMap<String, Content>()

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
            pushDemoMusic()
        }
        override fun next() = switchTrack(1)
        override fun previous() = switchTrack(-1)
        override fun seekTo(ms: Long) {
            demoPos = ms
            demoPosTime = SystemClock.elapsedRealtime()
            pushDemoMusic()
        }
        override fun open() {
            Toast.makeText(applicationContext, "Hier würde jetzt deine Musik-App aufgehen 🎧", Toast.LENGTH_SHORT).show()
        }
    }

    private fun switchTrack(dir: Int) {
        trackIndex = (trackIndex + dir + tracks.size) % tracks.size
        demoPos = 0
        demoPosTime = SystemClock.elapsedRealtime()
        demoPlaying = true
        pushDemoMusic()
    }

    private fun demoMusic(): MusicInfo {
        val t = tracks[trackIndex]
        val a = art(trackIndex)
        return MusicInfo(
            id = "demo$trackIndex", title = t.title, artist = t.artist, art = a,
            accent = Colors.accentOf(a), playing = demoPlaying, durationMs = t.dur,
            positionMs = demoPosNow() % t.dur, positionTime = SystemClock.elapsedRealtime(),
            speed = 1f, actions = demoActions, appIcon = appIconBitmap(), canSeek = true,
        )
    }

    private fun pushDemoMusic() {
        if (!demoMusicOn) return
        val m = demoMusic()
        previews.forEach { it.setMusic(m) }
    }

    private fun demoMusicToggle() {
        // An → aus; aus oder pausiert → läuft wieder
        demoMusicOn = !(demoMusicOn && demoPlaying)
        demoPlaying = true
        demoPosTime = SystemClock.elapsedRealtime()
        if (demoMusicOn) {
            val m = demoMusic()
            previews.forEach { it.setMusic(m) }
            IslandBus.testMusic(m)
        } else {
            previews.forEach { it.setMusic(null) }
        }
    }

    private var appIconCache: Bitmap? = null
    private fun appIconBitmap(): Bitmap? {
        appIconCache?.let { return it }
        return try {
            NotificationService.drawableToBitmap(applicationInfo.loadIcon(packageManager), dpi(48f)).also { appIconCache = it }
        } catch (_: Exception) { null }
    }

    private fun toast(t: String): () -> Unit = {
        Toast.makeText(applicationContext, t, Toast.LENGTH_SHORT).show()
    }

    private fun demoTransient(c: Content) {
        previews.forEach { it.showTransient(c) }
        IslandBus.event(c)
    }

    private fun pushDemoLive() {
        val list = demoLive.values.toList()
        previews.forEach { it.setLive(list) }
        IslandBus.testLive(list)
    }

    private fun demoNotif() {
        demoTransient(Content.Notif(
            icon = appIconBitmap(),
            app = "Island",
            title = "Anna 💜",
            text = "Ahoj! Jak se máš? Lust auf Kaffee später? Ich hab das neue Café an der Karlsbrücke gefunden ☕✨",
            open = toast("Würde jetzt den Chat öffnen 💬"),
            actions = listOf(
                NotifAction("Gefällt mir ❤️", toast("Reaktion gesendet")),
                NotifAction("Gelesen", toast("Als gelesen markiert")),
            ),
        ))
    }

    private fun demoCall() {
        val key = "demo-call"
        if (demoLive.containsKey(key)) {
            demoLive.remove(key)
            pushDemoLive()
            return
        }
        lateinit var incoming: Content.Call
        val hangUp: () -> Unit = {
            demoLive.remove(key)
            pushDemoLive()
        }
        val accept: () -> Unit = {
            demoLive[key] = Content.Call(key, "Mama 💛", "Telefon", null, false, System.currentTimeMillis(), null, hangUp, toast("Würde die Telefon-App öffnen"))
            pushDemoLive()
        }
        incoming = Content.Call(key, "Mama 💛", "Telefon · Eingehender Anruf", null, true, 0L, accept, hangUp, toast("Würde die Telefon-App öffnen"))
        demoLive[key] = incoming
        pushDemoLive()
    }

    private fun demoTimer() {
        val key = "demo-timer"
        if (demoLive.remove(key) == null) {
            demoLive[key] = Content.Timer(
                key, "Uhr", "Pasta 🍝", appIconBitmap(), System.currentTimeMillis() + 5 * 60_000 + 900, true,
                toast("Würde die Uhr-App öffnen"),
                listOf(NotifAction("Pause", toast("Pausiert")), NotifAction("Stopp") { demoLive.remove(key); pushDemoLive() }),
            )
        }
        pushDemoLive()
    }

    private fun demoNav() {
        val key = "demo-nav"
        if (demoLive.remove(key) == null) {
            demoLive[key] = Content.Nav(key, "350 m", "Rechts abbiegen auf Karlův most", null, toast("Würde Maps öffnen"))
        }
        pushDemoLive()
    }

    private fun demoUpload() {
        val key = "demo-up"
        if (demoLive.containsKey(key)) return
        val start = SystemClock.elapsedRealtime()
        val tick = object : Runnable {
            override fun run() {
                val p = ((SystemClock.elapsedRealtime() - start) / 4500f).coerceAtMost(1f)
                if (p >= 1f) {
                    demoLive.remove(key)
                    pushDemoLive()
                    demoTransient(Content.Progress("done-$key", "Fotos", "Urlaub_Prag.mov", appIconBitmap(), 1f, true, null))
                    return
                }
                demoLive[key] = Content.Progress(key, "Fotos", "Urlaub_Prag.mov wird hochgeladen", appIconBitmap(), p, false, null)
                pushDemoLive()
                main.postDelayed(this, 120)
            }
        }
        tick.run()
    }

    private fun demoCharging() {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: 76
        demoTransient(Content.Charging(lvl, true))
    }

    private fun demoDevice() = demoTransient(Content.Device("AirPods Pro", true, 82))

    private fun demoRinger() {
        ringerSilent = !ringerSilent
        demoTransient(Content.Ringer(silent = ringerSilent, vibrate = false))
    }

    private fun demoPrivacy() {
        val on = Privacy(camera = true, mic = true, location = true)
        previews.forEach { it.privacy = on }
        main.postDelayed({ previews.forEach { it.privacy = Privacy() } }, 4000)
        Toast.makeText(this, "Zeigt sich in der leeren Island – Musik & Co. kurz ausblenden", Toast.LENGTH_SHORT).show()
    }

    // ==================================================================
    // Status / Berechtigungen
    // ==================================================================

    private fun a11yEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return s.split(':').any { it.startsWith("$packageName/") }
    }

    private fun notifEnabled(): Boolean =
        Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true

    private fun granted(perm: String) = checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    // ==================================================================
    // iOS-Listen-Bausteine
    // ==================================================================

    private fun section(title: String?, rows: List<View>, footer: String? = null): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply {
            text = title?.uppercase() ?: ""
            textSize = 13f
            setTextColor(pal.secondary)
            letterSpacing = 0.02f
            setPadding(dpi(16f), dpi(if (title == null) 18f else 26f), dpi(16f), dpi(if (title == null) 0f else 7f))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12f)
                setColor(pal.card)
            }
            clipToOutline = true
        }
        rows.forEachIndexed { i, v ->
            if (i > 0) {
                val inset = if ((rows[i - 1].getTag(TAG_HAS_ICON) as? Boolean) == true) 59f else 16f
                card.addView(View(this).apply { setBackgroundColor(pal.separator) },
                    LinearLayout.LayoutParams(-1, maxOf(1, dpi(0.5f))).apply { leftMargin = dpi(inset) })
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
        minimumHeight = dpi(48f)
        setPadding(dpi(16f), dpi(7f), dpi(14f), dpi(7f))
    }

    private fun withIcon(row: LinearLayout, icon: View?) {
        if (icon == null) return
        row.addView(icon, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(14f) })
        row.setTag(TAG_HAS_ICON, true)
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

    private fun switchRow(label: String, key: String, def: Boolean, sub: String? = null, icon: View? = null): View {
        val row = baseRow()
        withIcon(row, icon)
        row.addView(labelBlock(label, sub), LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dpi(10f) })
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
            fontFeatureSettings = "tnum"
        }
        top.addView(value)
        col.addView(top)
        val s = IosSlider(this, pal, min, max)
        val cur = prefs.getInt(key, def).coerceIn(min, max)
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
        seg.set(current.coerceIn(0, labels.size - 1))
        seg.onChange = onChange
        box.addView(seg, FrameLayout.LayoutParams(-1, -2))
        return box
    }

    private fun colorRow(): View {
        val row = baseRow()
        row.gravity = Gravity.CENTER_VERTICAL
        val dots = ArrayList<View>()
        fun refresh() {
            val cur = prefs.getInt(Prefs.ACCENT_COLOR, D.accentColor)
            IslandConfig.ACCENT_CHOICES.forEachIndexed { i, col ->
                (dots[i].background as GradientDrawable).setStroke(dpi(3f), if (col == cur) pal.label else Color.TRANSPARENT)
            }
        }
        for (col in IslandConfig.ACCENT_CHOICES) {
            val v = View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(col)
                }
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    prefs.edit().putInt(Prefs.ACCENT_COLOR, col).putInt(Prefs.ACCENT_MODE, IslandConfig.ACCENT_CUSTOM).apply()
                    refresh()
                }
            }
            dots.add(v)
            row.addView(v, LinearLayout.LayoutParams(dpi(28f), dpi(28f)).apply {
                leftMargin = dpi(4f); rightMargin = dpi(4f)
            })
        }
        row.gravity = Gravity.CENTER
        refresh()
        return row
    }

    private fun statusRow(label: String, sub: String, check: () -> Boolean, onClick: () -> Unit): View {
        val row = baseRow()
        row.addView(labelBlock(label, sub), LinearLayout.LayoutParams(0, -2, 1f))
        val st = TextView(this).apply {
            textSize = 15f
            typeface = font(500)
        }
        row.addView(st)
        val update = {
            val on = check()
            st.text = if (on) "Aktiv ✓" else "Einschalten ›"
            st.setTextColor(if (on) pal.green else pal.blue)
        }
        update()
        refreshers.add(update)
        row.setOnClickListener { onClick() }
        row.background = pressable()
        return row
    }

    private fun permRow(label: String, sub: String, perm: String): View =
        statusRow(label, sub, { granted(perm) }) {
            if (!granted(perm)) {
                val ask = if (perm == Manifest.permission.ACCESS_FINE_LOCATION)
                    arrayOf(perm, Manifest.permission.ACCESS_COARSE_LOCATION) else arrayOf(perm)
                requestPermissions(ask, 7)
            }
        }

    private fun navRow(label: String, icon: View?, onClick: () -> Unit): View {
        val row = baseRow()
        withIcon(row, icon)
        row.addView(TextView(this).apply {
            text = label
            textSize = 17f
            setTextColor(pal.label)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 22f
            setTextColor(if (pal.dark) 0x66EBEBF5 else 0x4D3C3C43)
        })
        row.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            onClick()
        }
        row.background = pressable()
        return row
    }

    private fun actionRow(label: String, destructive: Boolean = false, onClick: () -> Unit): View {
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

    private fun valueRow(label: String, value: String): View {
        val row = baseRow()
        row.addView(TextView(this).apply {
            text = label
            textSize = 17f
            setTextColor(pal.label)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 17f
            setTextColor(pal.secondary)
        })
        return row
    }

    private fun pressable(): android.graphics.drawable.Drawable {
        val ta = obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackground))
        val d = ta.getDrawable(0)
        ta.recycle()
        return d ?: GradientDrawable()
    }

    private fun signed(v: Int) = if (v > 0) "+$v dp" else "$v dp"

    companion object {
        /** Eigene Tag-Keys (müssen wie Resource-IDs aussehen → 0x7f…) */
        private const val TAG_PREVIEW = 0x7f0f0001
        private const val TAG_HAS_ICON = 0x7f0f0002
    }
}
