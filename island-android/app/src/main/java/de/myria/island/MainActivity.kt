package de.myria.island

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
    private val stackNames = ArrayList<String>()
    private val prefs by lazy { Prefs.sp(this) }
    private val D = IslandConfig()
    private val main = Handler(Looper.getMainLooper())

    /** Alle Vorschau-Islands (Hauptseite + Unterseiten) */
    private val previews = ArrayList<IslandController>()
    /** Werden in onResume / nach Berechtigungen aufgerufen */
    private val refreshers = ArrayList<() -> Unit>()
    private val pageRefreshers = mutableMapOf<View, List<() -> Unit>>()

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k != Prefs.CUSTOM_SNAPSHOT && (k in Prefs.LOOK_INT_KEYS || k in Prefs.LOOK_BOOL_KEYS)) Prefs.rememberCustom(this)
        val c = previewCfg()
        previews.forEach { it.cfg = c }
        if (k == Prefs.REDUCE_MOTION) {
            stack.forEach { (it.background as? LiquidGlassBackdrop)?.invalidateSelf() }
        }
    }

    private fun previewCfg() = Prefs.load(this).copy(hideLandscape = false, enabled = true)

    private fun dp(v: Float) = (v * resources.displayMetrics.density)
    private fun dpi(v: Float) = dp(v).toInt()
    private val reduceMotion get() = prefs.getBoolean(Prefs.REDUCE_MOTION, D.reduceMotion)
    private fun motionFactor(): Float = if (reduceMotion) 0f else
        1.3f - 0.75f * (prefs.getInt(Prefs.SPEED, D.speed).coerceIn(0, 100) / 100f)
    private fun motionDuration(base: Long): Long = if (reduceMotion) 0L else
        (base * motionFactor()).toLong().coerceAtLeast(80L)
    private fun motionDelay(base: Long): Long = (base * motionFactor()).toLong().coerceAtLeast(0L)

    // ==================================================================
    // Lebenszyklus
    // ==================================================================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val cfg0 = Prefs.load(this)
        pal = Palette(cfg0.uiLook, dark, this, cfg0.appFont)
        styleWindow(dark)

        host = FrameLayout(this).apply { setBackgroundColor(pal.bg) }
        setContentView(host)
        val first = mainPage()
        stack.add(first)
        stackNames.add("main")
        host.addView(first, FrameLayout.LayoutParams(-1, -1))

        // Nach Design-Wechsel (recreate) die offenen Seiten wiederherstellen
        savedInstanceState?.getStringArrayList(STATE_PAGES)?.forEach { open(it, animate = false) }
        // Erster Start: Begrüßung
        if (savedInstanceState == null && !prefs.getBoolean(Prefs.ONBOARDED, false)) open("welcome", animate = false)

        prefs.registerOnSharedPreferenceChangeListener(prefListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(STATE_PAGES, ArrayList(stackNames.drop(1)))
    }

    override fun onResume() {
        super.onResume()
        val currentPreviewConfig = previewCfg()
        previews.forEach { it.cfg = currentPreviewConfig }
        refreshers.forEach { it() }
        if (stackNames.lastOrNull() == "geometry") IslandBus.calibrate(true)
    }

    override fun onPause() {
        IslandBus.calibrate(false)
        super.onPause()
    }

    private fun it_haptic() {
        host.performHapticFeedback(HapticFeedbackConstants.CONFIRM.takeIf { Build.VERSION.SDK_INT >= 30 } ?: HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * Smart-Anpassung: misst die Kamera-Aussparung und berechnet daraus die perfekte Island.
     * Punch-Hole → Höhe ≈ 1,4 × Kamera-Durchmesser, Breite ≈ 3,1 × Höhe; Notch → umschließt die Notch.
     */
    @Suppress("DEPRECATION")
    private fun smartFit(): String {
        val dm = resources.displayMetrics
        val d = dm.density
        val rect: android.graphics.Rect? = try {
            if (Build.VERSION.SDK_INT >= 29) windowManager.defaultDisplay.cutout?.boundingRectTop?.takeIf { !it.isEmpty }
            else window.decorView.rootWindowInsets?.displayCutout?.boundingRectTop?.takeIf { !it.isEmpty }
        } catch (_: Exception) { null }
        val model = Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL
        val e = prefs.edit()
        val text: String
        if (rect == null) {
            e.putBoolean(Prefs.AUTO_CAMERA, false).putInt(Prefs.WIDTH, 112).putInt(Prefs.HEIGHT, 34)
                .putInt(Prefs.OFFSET_X, 0).putInt(Prefs.OFFSET_Y, 6).putInt(Prefs.COMPACT_ROUND, 100)
            text = "📱 $model\nKeine Kamera-Aussparung gefunden → Island sitzt oben mittig (112 × 34 dp)."
        } else {
            val wDp = rect.width() / d
            val hDp = rect.height() / d
            val mm = rect.height() / dm.ydpi * 25.4f
            val notch = wDp > hDp * 1.8f
            val w: Int
            val h: Int
            if (notch) {
                w = (wDp + 18).toInt().coerceIn(80, 240)
                h = hDp.toInt().coerceIn(26, 52)
            } else {
                val dia = minOf(wDp, hDp)
                h = (dia * 1.4f + 6).toInt().coerceIn(26, 46)
                w = maxOf((h * 3.1f).toInt(), (dia + 72).toInt()).coerceIn(90, 170)
            }
            val off = rect.centerX() / d - dm.widthPixels / d / 2
            val pos = when { kotlin.math.abs(off) < 8 -> "oben mittig"; off < 0 -> "oben links"; else -> "oben rechts" }
            e.putBoolean(Prefs.AUTO_CAMERA, true).putInt(Prefs.WIDTH, w).putInt(Prefs.HEIGHT, h)
                .putInt(Prefs.OFFSET_X, 0).putInt(Prefs.OFFSET_Y, 0).putInt(Prefs.COMPACT_ROUND, 100)
            text = "📱 $model\n" + (if (notch) "Notch" else "Kamera-Loch") +
                " · %.1f mm · $pos\n→ Island $w × $h dp, genau auf der Kamera zentriert".format(mm)
        }
        e.putString(PREF_SMART, text).apply()
        return text
    }

    override fun onDestroy() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        previews.forEach { it.release() }
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stackNames.lastOrNull() == "welcome") prefs.edit().putBoolean(Prefs.ONBOARDED, true).apply()
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
    // Navigation zwischen Seiten
    // ==================================================================

    private val ease = PathInterpolator(0.2f, 0.9f, 0.1f, 1f)

    private fun pageFor(name: String): View? = when (name) {
        "design" -> designPage()
        "appearance" -> appearancePage()
        "glass" -> glassPage()
        "geometry" -> geometryPage()
        "animation" -> animationPage()
        "live" -> livePage()
        "system" -> systemPage()
        "music" -> musicPage()
        "notif" -> notifPage()
        "apps" -> appsPage()
        "behavior" -> behaviorPage()
        "permissions" -> permissionsPage()
        "info" -> infoPage()
        "icons" -> iconsPage()
        "fonts" -> fontsPage()
        "timer" -> timerPage()
        "history" -> historyPage()
        "hidein" -> appsPage(hideMode = true)
        "welcome" -> welcomePage()
        "style" -> stylePage()
        "appshub" -> appsHubPage()
        else -> null
    }

    private fun open(name: String, animate: Boolean = true) {
        val page = pageFor(name) ?: return
        val prev = stack.lastOrNull()
        stack.add(page)
        stackNames.add(name)
        host.addView(page, FrameLayout.LayoutParams(-1, -1))
        if (!animate) {
            prev?.visibility = View.INVISIBLE
            return
        }
        val w = host.width.toFloat().takeIf { it > 0 } ?: resources.displayMetrics.widthPixels.toFloat()
        val duration = motionDuration(when {
            pal.isNothing -> 220L
            pal.isGlass -> 440L
            pal.isAndroid -> 300L
            else -> 420L
        })
        if (duration == 0L) {
            page.alpha = 1f
            page.translationX = 0f
            page.scaleX = 1f
            page.scaleY = 1f
            prev?.visibility = View.INVISIBLE
            return
        }
        staggerIn(page)
        if (pal.isNothing) {
            page.alpha = 0f
            page.animate().alpha(1f).setDuration(duration).withEndAction { prev?.visibility = View.INVISIBLE }.start()
            return
        }
        val glass = pal.isGlass
        page.translationX = if (pal.isAndroid) w * 0.25f else if (glass) w * 0.14f else w
        if (pal.isAndroid || glass) page.alpha = 0f
        if (glass) { page.scaleX = 0.97f; page.scaleY = 0.97f }
        val enter = page.animate().translationX(0f).alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(duration)
            .withEndAction { prev?.visibility = View.INVISIBLE } // Seiten dahinter nicht mehr zeichnen (spart Akku)
        enter.setInterpolator(if (glass) android.view.animation.OvershootInterpolator(0.35f) else ease).start()
        prev?.animate()?.translationX(-w * (if (pal.isAndroid) 0.1f else if (glass) 0.06f else 0.3f))
            ?.alpha(if (pal.isAndroid || glass) 0f else 0.6f)
            ?.setDuration(duration)?.setInterpolator(ease)?.start()
    }

    /** Inhalte gleiten nacheinander hoch (Material, One UI, Glas) – iOS macht das nicht. */
    private fun staggerIn(page: View) {
        if (pal.isApple || pal.isNothing || reduceMotion) return
        val content = ((page as? FrameLayout)?.getChildAt(0) as? ScrollView)?.getChildAt(0) as? LinearLayout ?: return
        for (i in 0 until minOf(content.childCount, 10)) {
            val v = content.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(18f)
            v.animate().alpha(1f).translationY(0f).setStartDelay(motionDelay(28L + i * 26L))
                .setDuration(motionDuration(340L)).setInterpolator(ease).start()
        }
    }

    private fun pop(animate: Boolean = true): Boolean {
        if (stack.size <= 1) return false
        val top = stack.removeAt(stack.size - 1)
        val name = stackNames.removeAt(stackNames.size - 1)
        pageRefreshers.remove(top)?.let { refreshers.removeAll(it.toSet()) }
        if (name == "geometry") IslandBus.calibrate(false)
        val prev = stack.last()
        prev.visibility = View.VISIBLE
        val w = host.width.toFloat()
        val end = Runnable {
            host.removeView(top)
            (top.getTag(TAG_PREVIEW) as? IslandController)?.let { previews.remove(it); it.release() }
        }
        if (!animate || reduceMotion) {
            top.animate().cancel()
            prev.animate().cancel()
            prev.translationX = 0f
            prev.alpha = 1f
            prev.scaleX = 1f
            prev.scaleY = 1f
            end.run()
        } else if (pal.isNothing) {
            top.animate().alpha(0f).setDuration(motionDuration(180L)).withEndAction(end).start()
            prev.translationX = 0f
            prev.alpha = 1f
        } else {
            val duration = motionDuration(if (pal.isGlass) 400L else 320L)
            top.animate().translationX(if (pal.isAndroid) w * 0.25f else if (pal.isGlass) w * 0.14f else w)
                .alpha(if (pal.isAndroid || pal.isGlass) 0f else 1f)
                .scaleX(if (pal.isGlass) 0.97f else 1f).scaleY(if (pal.isGlass) 0.97f else 1f)
                .setDuration(duration).setInterpolator(ease).withEndAction(end).start()
            prev.animate().translationX(0f).alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(duration).setInterpolator(ease).start()
        }
        refreshers.forEach { it() }
        return true
    }

    /** Seite mit großem Titel, der beim Scrollen in die Leiste wandert – je nach Look. */
    private fun page(
        title: String,
        back: Boolean,
        dockDestination: GlassDestination? = null,
        build: (LinearLayout) -> Unit,
    ): FrameLayout {
        val root = FrameLayout(this).apply {
            setBackgroundColor(pal.bg)
            elevation = dp(10f)
            isClickable = true
            // Fix: Suchfeld bekommt beim Öffnen nicht automatisch den Fokus (keine Tastatur)
            isFocusableInTouchMode = true
            descendantFocusability = android.view.ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        val backdrop = if (pal.isGlass) LiquidGlassBackdrop(root, pal) { reduceMotion } else null
        if (backdrop != null) root.background = backdrop
        val hasDock = backdrop != null && dockDestination != null
        val navH = dp(54f)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16f), navH.toInt(), dpi(16f), dpi(if (hasDock) 24f else 48f))
        }
        val bigTitle = TextView(this).apply {
            text = if (pal.isNothing) title.uppercase() else title
            setTextColor(pal.label)
            when {
                pal.isOneUi -> {
                    textSize = 32f
                    typeface = pal.font(400)
                    gravity = Gravity.CENTER
                    setPadding(0, dpi(60f), 0, dpi(56f))
                }
                pal.isAndroid -> {
                    textSize = 36f
                    typeface = pal.font(400)
                    setPadding(dpi(4f), dpi(18f), 0, dpi(14f))
                }
                pal.isNothing -> {
                    textSize = 30f
                    typeface = pal.font(500)
                    letterSpacing = 0.06f
                    setPadding(dpi(4f), dpi(8f), 0, dpi(10f))
                }
                else -> {
                    textSize = 34f
                    typeface = pal.font(700)
                    letterSpacing = -0.01f
                    setPadding(dpi(4f), 0, 0, dpi(6f))
                }
            }
        }
        content.addView(bigTitle, LinearLayout.LayoutParams(-1, -2))
        val firstRefresher = refreshers.size
        build(content)
        if (refreshers.size > firstRefresher) pageRefreshers[root] = refreshers.drop(firstRefresher)
        val scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(content)
        }
        root.addView(scroll, FrameLayout.LayoutParams(-1, -1).apply {
            if (hasDock) bottomMargin = dpi(78f)
        })

        val bar = FrameLayout(this)
        val barBg = View(this).apply {
            setBackgroundColor(when {
                pal.isApple -> if (pal.dark) 0xF01C1C1E.toInt() else 0xF0F9F9F9.toInt()
                pal.isGlass -> Palette.mixC(pal.bg, 0x00000000, 0.12f)
                else -> pal.bg
            })
            alpha = 0f
        }
        bar.addView(barBg, FrameLayout.LayoutParams(-1, -1))
        val hair = View(this).apply { setBackgroundColor(if (pal.isAndroid) 0 else pal.separator); alpha = 0f }
        bar.addView(hair, FrameLayout.LayoutParams(-1, maxOf(1, dpi(0.5f)), Gravity.BOTTOM))
        val small = TextView(this).apply {
            text = if (pal.isNothing) title.uppercase() else title
            textSize = if (pal.isAndroid) 22f else 17f
            typeface = pal.font(if (pal.isAndroid) 400 else 600)
            setTextColor(pal.label)
            alpha = 0f
            setSingleLine()
            // Fix: lange Titel überlappen nicht mehr den Zurück-Knopf
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = resources.displayMetrics.widthPixels - dpi(if (pal.iosLike) 220f else 90f)
        }
        if (pal.iosLike) {
            bar.addView(small, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        } else {
            bar.addView(small, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START).apply {
                leftMargin = dpi(if (back) 60f else 20f)
            })
        }
        if (back) {
            val b = TextView(this).apply {
                text = if (pal.iosLike) "‹ Zurück" else "←"
                textSize = if (pal.iosLike) 17f else 24f
                setTextColor(if (pal.iosLike) pal.accent else pal.label)
                gravity = Gravity.CENTER
                setPadding(dpi(12f), dpi(6f), dpi(12f), dpi(6f))
                setOnClickListener { pop() }
                background = pressable()
            }
            bar.addView(b, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL or Gravity.START).apply {
                leftMargin = dpi(6f)
            })
        }
        root.addView(bar, FrameLayout.LayoutParams(-1, navH.toInt(), Gravity.TOP))
        if (backdrop != null && dockDestination != null) {
            root.addView(glassDock(backdrop, dockDestination),
                FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
                    bottomMargin = dpi(12f)
                })
        }

        scroll.setOnScrollChangeListener { _, _, y, _, _ ->
            val threshold = bigTitle.bottom - navH * 0.6f
            val t = ((y - threshold + dp(20f)) / dp(24f)).coerceIn(0f, 1f)
            small.alpha = t
            barBg.alpha = (y / dp(12f)).coerceIn(0f, 1f)
            hair.alpha = t
        }
        return root
    }

    // ==================================================================
    // Hauptseite
    // ==================================================================

    private fun designName(d: Int) = when (d) {
        IslandConfig.DESIGN_ANDROID -> "Android"
        IslandConfig.DESIGN_ONEUI -> "One UI"
        IslandConfig.DESIGN_NOTHING -> "Nothing"
        IslandConfig.DESIGN_CUSTOM -> "Custom"
        IslandConfig.DESIGN_GLASS -> "Liquid Glass"
        IslandConfig.DESIGN_CAPSULE -> "Kapsel"
        IslandConfig.DESIGN_PIXEL -> "Pixel 11"
        IslandConfig.DESIGN_COLOROS -> "ColorOS"
        else -> "Apple"
    }

    private enum class GlassDestination(val page: String, val label: String, val glyph: Glyph) {
        START("main", "Start", Glyph.HOME),
        DESIGN("style", "Design & Stil", Glyph.SPARKLE),
        SETUP("permissions", "Berechtigungen", Glyph.GEAR),
    }

    private fun glassDock(backdrop: LiquidGlassBackdrop, current: GlassDestination): View {
        val dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpi(6f), dpi(5f), dpi(6f), dpi(5f))
            elevation = dp(4f)
        }
        dock.background = backdrop.dockBackground(dock)
        for (destination in GlassDestination.entries) {
            val button = object : FrameLayout(this) {
                override fun getAccessibilityClassName(): CharSequence = android.widget.Button::class.java.name
            }.apply {
                isSelected = destination == current
                isFocusable = true
                contentDescription = destination.label
                val shape = GradientDrawable().apply {
                    cornerRadius = dp(24f)
                    setColor(if (destination == current) pal.fill else Color.TRANSPARENT)
                }
                val mask = GradientDrawable().apply {
                    cornerRadius = dp(24f)
                    setColor(Color.WHITE)
                }
                background = RippleDrawable(ColorStateList.valueOf(pal.fill), shape, mask)
                addView(GlyphIcon(this@MainActivity, pal.label, destination.glyph, pal, bare = true).apply {
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                }, FrameLayout.LayoutParams(dpi(29f), dpi(29f), Gravity.CENTER))
                setOnClickListener {
                    if (stackNames.lastOrNull() != current.page || destination == current) return@setOnClickListener
                    while (stack.size > 1) pop(animate = false)
                    if (destination != GlassDestination.START) open(destination.page, animate = false)
                }
            }
            dock.addView(button, LinearLayout.LayoutParams(dpi(58f), dpi(48f)))
        }
        return dock
    }

    private fun mainPage(): View = page(APP_NAME, false, GlassDestination.START) { c ->
        c.addView(TextView(this).apply {
            text = "Deine Dynamic Island – in jedem Design."
            textSize = 15f
            setTextColor(pal.secondary)
            typeface = pal.font(400)
            gravity = if (pal.isOneUi) Gravity.CENTER else Gravity.START
            setPadding(dpi(4f), 0, dpi(4f), dpi(14f))
        })

        // --- Suche ---
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val normal = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        c.addView(searchField { q ->
            results.removeAllViews()
            if (q.isBlank()) {
                normal.visibility = View.VISIBLE
                return@searchField
            }
            normal.visibility = View.GONE
            val words = q.split(' ').filter { it.isNotBlank() }
            val hits = SEARCH_INDEX.filter { (terms, _) -> words.all { terms.contains(it, ignoreCase = true) } }
            if (hits.isEmpty()) {
                results.addView(TextView(this).apply {
                    text = "Nichts gefunden für „$q“"
                    setTextColor(pal.secondary)
                    gravity = Gravity.CENTER
                    setPadding(0, dpi(30f), 0, 0)
                })
            } else {
                results.addView(section("Ergebnisse", hits.distinctBy { it.second }.map { (terms, page) ->
                    val match = terms.split(' ').firstOrNull { t -> words.any { t.contains(it, ignoreCase = true) } } ?: pageTitle(page)
                    navRow(match, null, value = pageTitle(page)) { open(page) }
                }))
            }
        })
        c.addView(results)
        c.addView(normal)

        normal.addView(previewCard(big = true, root = null))
        normal.addView(setupBanner())

        normal.addView(section(null, listOf(
            switchRow("$APP_NAME aktiv", Prefs.ENABLED, D.enabled, icon = GlyphIcon(this, 0xFF1C1C1E.toInt(), Glyph.ISLAND, pal)),
            segmentRow(listOf("Nur bei Bedarf", "Immer sichtbar"), if (prefs.getBoolean(Prefs.ALWAYS, D.alwaysVisible)) 1 else 0) {
                prefs.edit().putBoolean(Prefs.ALWAYS, it == 1).apply()
            },
        ), footer = "„Nur bei Bedarf“ = wie beim iPhone. „Immer sichtbar“ = die Pille bleibt dauerhaft um die Kamera."))

        // Design-Schnellwahl
        normal.addView(sectionHeader("Design"))
        normal.addView(designStrip())

        // Schnellschalter (wie Kontrollzentrum) – das Wichtigste auf einen Blick
        normal.addView(sectionHeader("Schnellschalter"))
        normal.addView(quickTiles(listOf(
            Triple("Musik", Prefs.MUSIC, Glyph.NOTE) to 0xFFFF375F.toInt(),
            Triple("Mitteilungen", Prefs.NOTIFS, Glyph.BELL) to 0xFFFF453A.toInt(),
            Triple("Anrufe", Prefs.CALLS, Glyph.PHONE) to 0xFF30D158.toInt(),
            Triple("Laden", Prefs.CHARGING, Glyph.BOLT) to 0xFF34C759.toInt(),
            Triple("Timer", Prefs.TIMER, Glyph.TIMER) to 0xFFFF9F0A.toInt(),
            Triple("Navi", Prefs.NAV, Glyph.LOCATION) to 0xFF0A84FF.toInt(),
            Triple("Lampe", Prefs.TORCH, Glyph.TORCH) to 0xFFFFCC00.toInt(),
            Triple("Lautstärke", Prefs.VOLUME, Glyph.SPEAKER) to 0xFF5E5CE6.toInt(),
        )))

        normal.addView(section("Aussehen", listOf(
            navRow("Design & Stil", GlyphIcon(this, 0xFFFF2D55.toInt(), Glyph.PAINT, pal),
                sub = "Vorlage, Farben, Glas, Schrift, Animation, App-Symbol") { open("style") },
            navRow("Größe & Position", GlyphIcon(this, 0xFF30B0C7.toInt(), Glyph.RULER, pal),
                sub = "Smart-Anpassung an deine Kamera") { open("geometry") },
        )))
        normal.addView(section("Inhalte", listOf(
            navRow("Live-Aktivitäten", GlyphIcon(this, 0xFFFF9F0A.toInt(), Glyph.ISLAND, pal),
                sub = "Anrufe, Timer, Navi, Downloads, Akku") { open("live") },
            navRow("Musik", GlyphIcon(this, 0xFFFF375F.toInt(), Glyph.NOTE, pal),
                sub = "Player, Wellenform, Vinyl, Spulen") { open("music") },
            navRow("Mitteilungen", GlyphIcon(this, 0xFFFF453A.toInt(), Glyph.BELL, pal),
                sub = "Aussehen, Größe, Ruhezeiten, Verlauf") { open("notif") },
            navRow("System-Anzeigen", GlyphIcon(this, 0xFF5E5CE6.toInt(), Glyph.SPEAKER, pal),
                sub = "Lautstärke, Fokus, WLAN, Wecker, Punkte") { open("system") },
        )))
        normal.addView(section("Extras", listOf(
            navRow("Timer & Stoppuhr", GlyphIcon(this, 0xFFFF9F0A.toInt(), Glyph.TIMER, pal),
                sub = "Direkt in der Island starten") { open("timer") },
            navRow("Mitteilungs-Verlauf", GlyphIcon(this, 0xFF8E8E93.toInt(), Glyph.CLOCK, pal),
                sub = "Was zuletzt reinkam") { open("history") },
        )))
        normal.addView(section("Bedienung", listOf(
            navRow("Gesten & Haptik", GlyphIcon(this, 0xFF32D74B.toInt(), Glyph.HAND, pal),
                sub = "Tippen, Wischen, Doppeltipp, Kontrollzentrum") { open("behavior") },
            navRow("Apps", GlyphIcon(this, 0xFF5856D6.toInt(), Glyph.GRID, pal),
                sub = "Welche Apps, wo ausblenden") { open("appshub") },
        )))
        normal.addView(section("App", listOf(
            navRow("Berechtigungen", GlyphIcon(this, 0xFF8E8E93.toInt(), Glyph.GEAR, pal),
                sub = "Was Lumora braucht – und warum") { open("permissions") },
            navRow("Info & Fehlerbericht", GlyphIcon(this, 0xFF636366.toInt(), Glyph.INFO, pal),
                sub = "Version, Export, Absturz-Bericht") { open("info") },
        )))
        normal.addView(TextView(this).apply {
            text = "$APP_NAME · made for Myria ✦ Ahoj! 🇨🇿"
            textSize = 13f
            setTextColor(pal.secondary)
            gravity = Gravity.CENTER
            setPadding(0, dpi(28f), 0, 0)
        })
    }

    private fun pageTitle(name: String) = when (name) {
        "design" -> "Design"
        "appearance" -> "Erscheinungsbild"
        "glass" -> "Liquid Glass"
        "geometry" -> "Größe & Position"
        "animation" -> "Animation"
        "live" -> "Live-Aktivitäten"
        "system" -> "System-Anzeigen"
        "music" -> "Musik"
        "notif" -> "Mitteilungen"
        "apps" -> "Apps"
        "behavior" -> "Gesten & Haptik"
        "permissions" -> "Berechtigungen"
        "info" -> "Info"
        "icons" -> "App-Symbol"
        "fonts" -> "Schriften"
        "timer" -> "Timer & Stoppuhr"
        "history" -> "Mitteilungs-Verlauf"
        "hidein" -> "In Apps ausblenden"
        "style" -> "Design & Stil"
        "appshub" -> "Apps"
        else -> ""
    }

    private fun searchField(onQuery: (String) -> Unit): View {
        val et = EditText(this).apply {
            hint = if (pal.isAndroid) "Einstellungen durchsuchen" else "Suchen"
            textSize = 17f
            setSingleLine()
            setTextColor(pal.label)
            setHintTextColor(pal.secondary)
            typeface = pal.font(400)
            setPadding(dpi(40f), dpi(6f), dpi(14f), dpi(6f))
            background = GradientDrawable().apply {
                cornerRadius = dp(when {
                    pal.isApple -> 10f
                    pal.isGlass -> 14f
                    else -> 28f
                })
                setColor(if (pal.iosLike) pal.fill else pal.card)
                if (pal.isNothing) setStroke(dpi(1f), pal.outline)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = onQuery(s?.toString()?.trim().orEmpty())
            })
        }
        val box = FrameLayout(this)
        box.addView(et, FrameLayout.LayoutParams(-1, dpi(if (pal.isApple) 38f else if (pal.isGlass) 44f else 50f)))
        box.addView(object : View(this) {
            private val pp = Paint(Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(cv: Canvas) {
                pp.style = Paint.Style.STROKE
                pp.strokeWidth = dp(1.8f)
                pp.strokeCap = Paint.Cap.ROUND
                pp.color = pal.secondary
                val s = width.toFloat()
                cv.drawCircle(s * 0.42f, s * 0.42f, s * 0.26f, pp)
                cv.drawLine(s * 0.62f, s * 0.62f, s * 0.86f, s * 0.86f, pp)
            }
        }, FrameLayout.LayoutParams(dpi(18f), dpi(18f), Gravity.CENTER_VERTICAL or Gravity.START).apply { leftMargin = dpi(14f) })
        box.setPadding(0, 0, 0, dpi(10f))
        return box
    }

    /** „Einrichten“-Karte, solange Berechtigungen fehlen. */
    private fun setupBanner(): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(section("Einrichten", listOf(
            statusRow("1. Bedienungshilfe", "Zeichnet die Island über der Statusleiste", ::a11yEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            statusRow("2. Mitteilungen & Musik", "Für Benachrichtigungen, Musik, Timer …", ::notifEnabled) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        ), footer = "Android 13+: „$APP_NAME“ ist in den Bedienungshilfen ausgegraut? → App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“."))
        val update = { box.visibility = if (a11yEnabled() && notifEnabled()) View.GONE else View.VISIBLE }
        refreshers.add(update)
        update()
        return box
    }

    private val designList = listOf(
        IslandConfig.DESIGN_APPLE to "Apple", IslandConfig.DESIGN_PIXEL to "Pixel 11",
        IslandConfig.DESIGN_ANDROID to "Android",
        IslandConfig.DESIGN_GLASS to "Liquid Glass", IslandConfig.DESIGN_CAPSULE to "Kapsel",
        IslandConfig.DESIGN_ONEUI to "One UI",
        IslandConfig.DESIGN_NOTHING to "Nothing", IslandConfig.DESIGN_COLOROS to "ColorOS",
        IslandConfig.DESIGN_CUSTOM to "Custom",
    )

    private fun designStrip(): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cur = prefs.getInt(Prefs.DESIGN, D.design)
        for ((d, name) in designList) {
            val t = DesignTile(this, pal, d, name)
            t.chosen = d == cur
            t.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                chooseDesign(d)
            }
            row.addView(t, LinearLayout.LayoutParams(dpi(138f), -2).apply { rightMargin = dpi(10f) })
        }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun chooseDesign(d: Int) {
        Prefs.applyDesign(this, d)
        if (d == IslandConfig.DESIGN_CUSTOM)
            Toast.makeText(this, "Custom: dein eigener Look – alles frei einstellbar ✨", Toast.LENGTH_SHORT).show()
        main.postDelayed({ recreate() }, 120)
    }

    // ==================================================================
    // Unterseiten
    // ==================================================================

    private fun designPage() = page("Design", true) { c ->
        c.addView(previewCard(big = false, root = c))
        val cur = prefs.getInt(Prefs.DESIGN, D.design)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        designList.chunked(2).forEach { pair ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, (d, name) ->
                val t = DesignTile(this, pal, d, name)
                t.chosen = d == cur
                t.setOnClickListener { chooseDesign(d) }
                r.addView(t, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (i == 0) rightMargin = dpi(6f) else leftMargin = dpi(6f)
                })
            }
            if (pair.size == 1) r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply { leftMargin = dpi(6f) })
            grid.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(12f) })
        }
        c.addView(sectionHeader("Vorlage"))
        c.addView(grid)
        c.addView(footerText("Eine Vorlage setzt Form, Farben, Schrift, Animation, Wellenform, Tasten und den Look dieser App. Danach kannst du trotzdem alles einzeln ändern. „Custom“ ist dein eigener Look und merkt sich deine Werte."))

        c.addView(section("Symbole", listOf(
            segmentRow(listOf("Passend", "Apple", "Pixel", "One UI", "Nothing"), prefs.getInt(Prefs.ICON_FAMILY, -1) + 1) {
                prefs.edit().putInt(Prefs.ICON_FAMILY, it - 1).apply()
            },
        ), footer = "Akku, Laden, Standort, WLAN, Musik-Tasten & Co. sehen aus wie im jeweiligen System. „Passend“ folgt dem Design – Nothing zeichnet alles als Punktraster."))

        c.addView(section("Look dieser App", listOf(
            segmentRow(listOf("iOS", "Material", "One UI", "Nothing", "Glas"), prefs.getInt(Prefs.UI_LOOK, D.uiLook)) {
                prefs.edit().putInt(Prefs.UI_LOOK, it).apply()
                main.postDelayed({ recreate() }, 180)
            },
        ), footer = "„Glas“ zeigt einen animierten Farbverlauf mit schwebender Navigation. Diese Einstellung betrifft das Aussehen der App."))
    }

    private fun appearancePage() = page("Erscheinungsbild", true) { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Form & Fläche", choiceRows(Prefs.STYLE, D.style, listOf(
            "Schwarz" to "Verschmilzt mit der Kamera – wie beim iPhone",
            "Liquid Glass" to "Durchscheinend mit Glanz & Lichtkante",
            "Farbig" to "Getönt in der Akzentfarbe",
            "Material" to "Dunkle Material-You-Fläche",
            "Graphit" to "Dunkelgrau mit feinem Rand",
            "Umriss" to "Schwarz mit hellem Rand",
            "Kapsel" to "Tonal in deinen Material-You-Farben",
            "Pixel 11" to "Kräftiger Chip in der Systemfarbe (Material 3 Expressive)",
        )) {
            if (it == IslandConfig.STYLE_GLASS) {
                Toast.makeText(this, "Tipp: Unter „Liquid Glass“ kannst du das Glas fein einstellen 💧", Toast.LENGTH_SHORT).show()
            }
        }))

        c.addView(section("Akzentfarbe", choiceRows(Prefs.ACCENT_MODE, D.accentMode, listOf(
            "Aus dem Cover" to "Farbe vom Album-Cover",
            "Eigene Farbe" to null,
            "Weiß" to null,
            "Systemfarbe" to "Material You (Android 12+)",
        )) + listOf(colorRow())))

        c.addView(section("Musik-Elemente", listOf(
            labeled("Wellenform", segmentRow(listOf("Balken", "Punkte", "Linie"), prefs.getInt(Prefs.WAVE_STYLE, D.waveStyle)) {
                setLook(Prefs.WAVE_STYLE, it)
            }),
            labeled("Fortschritt", segmentRow(listOf("Linie", "Welle", "Punkte"), prefs.getInt(Prefs.PROGRESS_STYLE, D.progressStyle)) {
                setLook(Prefs.PROGRESS_STYLE, it)
            }),
            labeled("Tasten", segmentRow(listOf("Schlicht", "Gefüllt", "Kreis", "Umriss"), prefs.getInt(Prefs.CONTROL_STYLE, D.controlStyle)) {
                setLook(Prefs.CONTROL_STYLE, it)
            }),
        ), footer = "In der Vorschau auf die Musik gedrückt halten, um den großen Player zu sehen."))

        c.addView(section("Schrift", listOf(
            navRow("Schriftart", null, value = Fonts.ALL[prefs.getInt(Prefs.FONT_STYLE, D.fontStyle).coerceIn(0, Fonts.ALL.size - 1)].name) { open("fonts") },
            labeled("Größe", segmentRow(listOf("Klein", "Normal", "Groß"), prefs.getInt(Prefs.FONT_SCALE, D.fontScale)) {
                prefs.edit().putInt(Prefs.FONT_SCALE, it).apply()
            }),
        )))

        c.addView(section("Leuchten & Schatten", listOf(
            switchRow("Farbiges Leuchten", Prefs.GLOW, D.glow, "Glow um die Island, wenn Musik läuft"),
            sliderRow("Stärke", Prefs.GLOW_STRENGTH, D.glowStrength, 0, 100) { "$it %" },
            switchRow("Schatten", Prefs.SHADOW, D.shadow),
        )))
    }

    private fun glassPage() = page("Liquid Glass", true) { c ->
        c.addView(previewCard(big = false, root = c))
        if (prefs.getInt(Prefs.STYLE, D.style) != IslandConfig.STYLE_GLASS) {
            val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            holder.addView(section(null, listOf(
                actionRow("Liquid Glass einschalten") {
                    prefs.edit().putInt(Prefs.STYLE, IslandConfig.STYLE_GLASS).apply()
                    holder.visibility = View.GONE
                },
            ), footer = "Gerade ist ein anderer Stil aktiv."))
            c.addView(holder)
        }
        c.addView(section("Glas", listOf(
            sliderRow("Milchigkeit", Prefs.GLASS_OPACITY, D.glassOpacity, 0, 100) {
                when { it < 20 -> "Klar"; it > 80 -> "Milchig"; else -> "$it %" }
            },
            sliderRow("Glanz", Prefs.GLASS_SHINE, D.glassShine, 0, 100) { "$it %" },
            sliderRow("Tönung", Prefs.GLASS_TINT, D.glassTint, 0, 100) { if (it == 0) "Neutral" else "$it %" },
        ), footer = "Milchigkeit = wie stark das Glas den Hintergrund verdeckt. Tönung färbt das Glas in der Akzentfarbe."))
        c.addView(section("Kante", listOf(
            switchRow("Lichtkante", Prefs.GLASS_BORDER, D.glassBorder, "Heller Rand oben, Reflex unten"),
            sliderRow("Kanten-Stärke", Prefs.GLASS_BORDER_WIDTH, D.glassBorderWidth, 3, 30) { "%.1f dp".format(it / 10f) },
            switchRow("Farbsaum", Prefs.GLASS_RAINBOW, D.glassRainbow, "Regenbogen-Lichtbrechung am Rand"),
        )))
        c.addView(section(null, listOf(
            actionRow("Glas zurücksetzen", destructive = true) { Prefs.resetGlass(this); pop() },
        )))
    }

    private fun geometryPage() = page("Größe & Position", true) { c ->
        c.addView(previewCard(big = false, root = c))
        IslandBus.calibrate(true)
        val result = TextView(this).apply {
            text = prefs.getString(PREF_SMART, null) ?: "Noch nicht angepasst."
            textSize = 14f
            typeface = pal.font(400)
            setTextColor(pal.secondary)
            setLineSpacing(0f, 1.2f)
            setPadding(dpi(16f), dpi(12f), dpi(16f), dpi(12f))
        }
        c.addView(section("Smart-Anpassung ✨", listOf(
            actionRow("Automatisch an mein Handy anpassen") {
                result.text = smartFit()
                it_haptic()
                Toast.makeText(this, "Angepasst – schau mal oben 👀", Toast.LENGTH_SHORT).show()
            },
            result,
        ), footer = "Erkennt dein Handy und misst die Kamera-Aussparung (Form, Größe, Lage). Daraus wird die perfekte Größe berechnet. Solange diese Seite offen ist, bleibt die echte Island oben sichtbar – zum Feinjustieren."))
        c.addView(section("Position", listOf(
            switchRow("An Kamera ausrichten", Prefs.AUTO_CAMERA, D.autoCamera, "Findet die Kamera-Aussparung automatisch"),
            sliderRow("Vertikal", Prefs.OFFSET_Y, D.offsetYDp, -30, 80) { signed(it) },
            sliderRow("Horizontal", Prefs.OFFSET_X, D.offsetXDp, -160, 160) { signed(it) },
        ), footer = "Mit „An Kamera ausrichten“ verschieben die Regler nur noch fein."))
        c.addView(section("Pille", listOf(
            sliderRow("Breite", Prefs.WIDTH, D.widthDp, 60, 220) { "$it dp" },
            sliderRow("Höhe", Prefs.HEIGHT, D.heightDp, 22, 52) { "$it dp" },
            sliderRow("Rundung", Prefs.COMPACT_ROUND, D.compactRoundPct, 20, 100) { if (it == 100) "Pille" else "$it %" },
        )))
        c.addView(section("Aktivitäten", listOf(
            sliderRow("Kompakt-Breite", Prefs.COMPACT_EXTRA, D.compactExtraDp, -40, 80) { signed(it) },
            sliderRow("Erweiterte Breite", Prefs.EXPANDED_WIDTH, D.expandedWidthPct, 60, 100) { "$it %" },
            sliderRow("Ecken (groß)", Prefs.EXPANDED_RADIUS, D.expandedRadiusDp, 12, 60) { "$it dp" },
        )))
        c.addView(section(null, listOf(
            actionRow("Auf Standard zurücksetzen", destructive = true) {
                Prefs.resetGeometry(this)
                Toast.makeText(this, "Zurückgesetzt", Toast.LENGTH_SHORT).show()
                pop()
            },
        )))
    }

    private fun animationPage() = page("Animation", true) { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Animationsstil", choiceRows(Prefs.ANIM_STYLE, D.animStyle, listOf(
            "Apple" to "Federnd, Inhalt zoomt weich rein",
            "Android" to "Material Motion: ruhig, Inhalt gleitet hoch",
            "One UI" to "Sanft mit leichtem Nachschwingen",
            "Nothing" to "Knackig & schnell, reines Einblenden",
            "Jelly" to "Wackelpudding – extra verspielt",
        ))))
        c.addView(section("Federung", listOf(
            sliderRow("Bounce", Prefs.BOUNCE, D.bounce, 0, 100) { "$it %" },
            sliderRow("Tempo", Prefs.SPEED, D.speed, 0, 100) { "$it %" },
        ), footer = "Bounce 45 % + Tempo 50 % kommt dem iPhone am nächsten."))
        c.addView(section(null, listOf(
            switchRow("Drück-Effekt", Prefs.SQUISH, D.squish, "Island gibt beim Antippen leicht nach"),
            switchRow("Bewegung reduzieren", Prefs.REDUCE_MOTION, D.reduceMotion, "Kein Nachschwingen, ruhigere Übergänge"),
            actionRow("Animation testen") { demoMusicToggle(); main.postDelayed({ demoNotif() }, 900) },
        )))
    }

    private fun livePage() = page("Live-Aktivitäten", true) { c ->
        c.addView(section("Aktivitäten", listOf(
            switchRow("Musik", Prefs.MUSIC, D.showMusic, "Cover, Wellenform, Steuerung"),
            switchRow("Anrufe", Prefs.CALLS, D.showCalls, "Annehmen / Ablehnen in der Island"),
            switchRow("Timer & Stoppuhr", Prefs.TIMER, D.showTimer, "Auch Sprachaufnahmen"),
            switchRow("Navigation", Prefs.NAV, D.showNav, "Google Maps, Waze, OsmAnd …"),
            switchRow("Uploads & Downloads", Prefs.PROGRESS, D.showProgress, "Fortschrittsring + Häkchen"),
            switchRow("Taschenlampe", Prefs.TORCH, D.showTorch, "Antippen schaltet sie aus"),
        )))
        c.addView(section("Akku", listOf(
            switchRow("Laden", Prefs.CHARGING, D.showCharging, "Animation beim Einstecken"),
            switchRow("Laden dauerhaft anzeigen", Prefs.CHARGING_PERSISTENT, D.chargingPersistent),
            switchRow("Akku schwach", Prefs.LOW_BATTERY, D.showLowBattery, "Warnung bei 20 % und 10 %"),
            switchRow("Lade-Details", Prefs.CHARGE_DETAILS, D.chargeDetails, "Antippen: Restzeit, Schnellladen, Temperatur"),
            switchRow("Akku-Rand", Prefs.BATTERY_RING, D.batteryRing, "Ladestand als Linie um die Island"),
            switchRow("Hitze-Warnung", Prefs.TEMP_WARNING, D.tempWarning, "Ab 42 °C Akkutemperatur"),
        )))
        c.addView(section("Mehrere gleichzeitig", listOf(
            switchRow("Zweite Aktivität als Bubble", Prefs.SPLIT, D.splitBubble),
            switchRow("In der eigenen App ausblenden", Prefs.HIDE_OWNER, D.hideInOwnerApp,
                "Anruf in der Telefon-App, Musik im Player … wie beim iPhone"),
        ), footer = "Tippe auf die Bubble, um sie zur Haupt-Aktivität zu machen. Hochwischen blendet eine Aktivität kurz aus."))
    }

    private fun systemPage() = page("System-Anzeigen", true) { c ->
        c.addView(section("Kurz einblenden bei …", listOf(
            switchRow("Lautstärke", Prefs.VOLUME, D.showVolume, "Medien-Lautstärke als Balken"),
            switchRow("Lautlos / Klingeln", Prefs.RINGER, D.showRinger),
            switchRow("Nicht stören / Fokus", Prefs.FOCUS, D.showFocus),
            switchRow("Flugmodus", Prefs.AIRPLANE, D.showAirplane),
            switchRow("Kopfhörer verbunden", Prefs.DEVICES, D.showDevices, "Mit Akkustand"),
            switchRow("Entsperren", Prefs.UNLOCK, D.showUnlock, "Schloss springt auf – wie Face ID"),
            switchRow("Internet", Prefs.NETWORK, D.showNetwork, "WLAN verbunden / offline"),
            switchRow("Wecker gestellt", Prefs.ALARM, D.showAlarm, "Zeigt kurz die Weckzeit"),
        )))
        c.addView(section("Noch mehr Meldungen", StatusKinds.all.map { k ->
            switchRow(k.label, "st_" + k.id, k.def, k.sub.takeIf { it.isNotEmpty() })
        }, footer = "Jede Meldung erscheint kurz in der Island – mit Symbol im Stil deines Designs."))
        c.addView(section(null, listOf(
            permRow("Kalender-Zugriff", "Für Termin-Erinnerungen", Manifest.permission.READ_CALENDAR),
        )))
        c.addView(section("Akku sparen", listOf(
            switchRow("Im Energiesparmodus reduzieren", Prefs.POWER_SAVE, D.powerSaveReduce,
                "Keine Wellenform, kein Glow, ruhige Animationen"),
        )))
        c.addView(section("Leere Pille zeigt", choiceRows(Prefs.IDLE_CONTENT, D.idleContent, listOf(
            "Nichts" to "Wie beim iPhone", "Uhrzeit" to null, "Akku" to null, "Uhrzeit + Akku" to null,
            "Datum" to null, "Nächster Wecker" to null, "Datum + Akku" to null,
        )), footer = "Nur sichtbar bei „Immer sichtbar“."))
        c.addView(section("Datenschutz-Punkte", listOf(
            switchRow("Kamera", Prefs.PRIV_CAMERA, D.privacyCamera, "Grüner Punkt, wenn eine App filmt"),
            switchRow("Mikrofon", Prefs.PRIV_MIC, D.privacyMic, "Oranger Punkt bei Aufnahme"),
            switchRow("Standort", Prefs.PRIV_LOCATION, D.privacyLocation, "Blauer Pfeil, wenn GPS läuft"),
            permRow("Standort erlauben", "Nötig für den Standort-Pfeil", Manifest.permission.ACCESS_FINE_LOCATION),
        )))
    }

    private fun musicPage() = page("Musik", true) { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Anzeige", listOf(
            switchRow("Wellenform animieren", Prefs.WAVEFORM, D.waveform),
            switchRow("Wellenform in Akzentfarbe", Prefs.WAVEFORM_COVER, D.waveformCover),
            switchRow("App-Icon zeigen", Prefs.MUSIC_APP_ICON, D.musicAppIcon, "Spotify, YouTube Music … im großen Player"),
            switchRow("Vinyl-Modus", Prefs.VINYL, D.vinyl, "Cover dreht sich wie eine Schallplatte"),
        )))
        c.addView(section("Player", listOf(
            switchRow("Spulen per Ziehen", Prefs.MUSIC_SEEK, D.musicSeek, "Fortschrittsbalken im großen Player ziehen"),
            switchRow("Neuen Song kurz groß zeigen", Prefs.AUTO_EXPAND_TRACK, D.autoExpandTrack),
            sliderRow("Pausiert noch zeigen", Prefs.PAUSED_LINGER, D.pausedLingerSec, 0, 30) { if (it == 0) "Aus" else "$it s" },
        )))
    }

    private fun notifPage() = page("Mitteilungen", true) { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Aussehen", choiceRows(Prefs.NOTIF_STYLE, D.notifStyle, listOf(
            "Banner" to "Breit mit Icon, Titel und Text",
            "Kompakt" to "Schmale Kapsel: Icon + App-Name",
            "Groß" to "Sofort mit ganzem Text und Buttons",
        )) + listOf(
            labeled("Größe", segmentRow(listOf("Klein", "Normal", "Groß"), prefs.getInt(Prefs.NOTIF_SIZE, D.notifSize)) {
                prefs.edit().putInt(Prefs.NOTIF_SIZE, it).apply()
            }),
            labeled("Textzeilen", segmentRow(listOf("1", "2", "3"), prefs.getInt(Prefs.NOTIF_LINES, D.notifLines) - 1) {
                prefs.edit().putInt(Prefs.NOTIF_LINES, it + 1).apply()
            }),
            sliderRow("Breite", Prefs.NOTIF_WIDTH, D.notifWidthPct, 60, 100) { "$it %" },
            switchRow("App-Name zeigen", Prefs.NOTIF_SHOW_APP, D.notifShowApp),
            switchRow("Uhrzeit zeigen", Prefs.NOTIF_SHOW_TIME, D.notifShowTime),
            switchRow("Runde Icons", Prefs.NOTIF_ICON_ROUND, D.notifIconRound),
        ), footer = "Tippe in der Vorschau auf „Nachricht“, um's direkt zu sehen."))
        c.addView(section("Größe im Detail", listOf(
            sliderRow("Höhe", Prefs.NOTIF_HEIGHT, D.notifHeightDp, 50, 130) { if (it <= 50) "Auto" else "$it dp" },
            sliderRow("Textgröße", Prefs.NOTIF_TEXT, D.notifTextPct, 70, 150) { "$it %" },
            sliderRow("Zeilen im großen Modus", Prefs.NOTIF_EXP_LINES, D.notifExpandedLines, 2, 12) { "$it" },
        ), footer = "Höhe ganz links = automatisch passend zur gewählten Größe."))
        c.addView(section("Leuchten", choiceRows(Prefs.NOTIF_GLOW_MODE, if (prefs.getBoolean(Prefs.NOTIF_APP_GLOW, false)) 1 else D.notifGlowMode, listOf(
            "Aus" to null,
            "App-Farbe" to "Glow in der Farbe des App-Icons",
            "Akzentfarbe" to "Deine Akzentfarbe aus „Erscheinungsbild“",
            "Weiß" to "Dezenter heller Schein",
        )) + listOf(
            sliderRow("Stärke", Prefs.NOTIF_GLOW_STRENGTH, D.notifGlowStrength, 0, 100) { "$it %" },
        )))
        c.addView(section(null, listOf(
            switchRow("Mitteilungen in der Island", Prefs.NOTIFS, D.showNotifications),
            sliderRow("Anzeigedauer", Prefs.NOTIF_SECONDS, D.notifSeconds, 2, 12) { "$it s" },
        )))
        c.addView(section("Welche", listOf(
            switchRow("Auch stille Mitteilungen", Prefs.NOTIF_SILENT, D.notifSilent),
            switchRow("Auch bei „Nicht stören“", Prefs.NOTIF_DND, D.notifDnd),
            navRow("Apps auswählen", null) { open("apps") },
        )))
        c.addView(section("Darstellung", listOf(
            switchRow("Automatisch groß zeigen", Prefs.NOTIF_AUTO_EXPAND, D.notifAutoExpand, "Ganzer Text + Buttons sofort"),
            switchRow("Aktions-Buttons", Prefs.NOTIF_ACTIONS, D.notifActions, "z.B. „Als gelesen markieren“"),
            switchRow("Inhalt verbergen", Prefs.NOTIF_HIDE_CONTENT, D.notifHideContent, "Nur App-Name, kein Text"),
            switchRow("Nacheinander zeigen", Prefs.NOTIF_QUEUE, D.notifQueue, "Mehrere Mitteilungen in einer Warteschlange („+2“)"),
        ), footer = "Runterziehen auf einer Mitteilung zeigt den ganzen Text und die Buttons."))
        c.addView(section("Ruhezeiten", listOf(
            switchRow("Ruhezeiten", Prefs.QUIET, D.quietHours, "Keine Mitteilungen in der Island"),
            sliderRow("Von", Prefs.QUIET_START, D.quietStart, 0, 23) { "%02d:00".format(it) },
            sliderRow("Bis", Prefs.QUIET_END, D.quietEnd, 0, 23) { "%02d:00".format(it) },
        )))
        c.addView(section("Verlauf", listOf(
            switchRow("Verlauf speichern", Prefs.NOTIF_HISTORY, D.notifHistory, "Nur auf deinem Handy"),
            navRow("Verlauf ansehen", null) { open("history") },
        )))
    }

    private fun behaviorPage() = page("Gesten & Haptik", true) { c ->
        c.addView(section("Gesten", listOf(
            switchRow("Tippen erweitert", Prefs.TAP_EXPANDS, D.tapExpands, "Aus = wie iPhone: Tippen öffnet die App"),
            switchRow("Runterziehen erweitert", Prefs.SWIPE_DOWN, D.swipeDownExpands),
            sliderRow("Automatisch einklappen", Prefs.AUTO_COLLAPSE, D.autoCollapseSec, 3, 30) { "$it s" },
        ), footer = "Gedrückt halten erweitert • Hochwischen klappt ein • Außerhalb tippen schließt."))
        c.addView(section("Doppeltippen", choiceRows(Prefs.DOUBLE_TAP, D.doubleTapAction, listOf(
            "Nichts" to null, "Play / Pause" to null, "App öffnen" to null, "Erweitern" to null, "Taschenlampe" to null,
        ))))
        c.addView(section("Seitlich wischen", choiceRows(Prefs.SWIPE_H, D.swipeHorizontal, listOf(
            "Aus" to null,
            "Aktivität wechseln" to "Zwischen Musik, Timer, Navi … blättern",
            "Song wechseln" to "Bei Musik: nächster / vorheriger Song",
        ))))
        c.addView(section("Leere Pille antippen", choiceRows(Prefs.IDLE_TAP, D.idleTap, listOf(
            "Nichts" to null,
            "Kontrollzentrum" to "Lautstärke, Helligkeit, Lampe, Timer, Kamera",
        ))))
        c.addView(section("Leere Pille gedrückt halten", choiceRows(Prefs.IDLE_LP, D.idleLongPress, listOf(
            "Nichts" to null, "Timer starten" to null, "Stoppuhr" to null, "Taschenlampe" to null, "Lumora öffnen" to null,
        )), footer = "Funktioniert bei „Immer sichtbar“, wenn „Leere Pille durchklickbar“ aus ist."))
        c.addView(section("Haptik", listOf(
            switchRow("Haptisches Feedback", Prefs.HAPTICS, D.haptics),
            labeled("Stärke", segmentRow(listOf("Leicht", "Mittel", "Stark"), prefs.getInt(Prefs.HAPTIC_STRENGTH, D.hapticStrength)) {
                prefs.edit().putInt(Prefs.HAPTIC_STRENGTH, it).apply()
            }),
        )))
        c.addView(section("Sichtbarkeit", listOf(
            switchRow("Auf dem Sperrbildschirm", Prefs.LOCKSCREEN, D.showOnLockscreen),
            switchRow("Im Querformat ausblenden", Prefs.HIDE_LANDSCAPE, D.hideLandscape, "Videos & Spiele bleiben frei"),
            switchRow("Leere Pille durchklickbar", Prefs.IDLE_TOUCH_THROUGH, D.idleTouchThrough, "Bei „Immer sichtbar“"),
            navRow("In bestimmten Apps ausblenden", null) { open("hidein") },
        )))
    }

    private fun permissionsPage() = page("Berechtigungen", true, GlassDestination.SETUP) { c ->
        c.addView(section("Pflicht", listOf(
            statusRow("Bedienungshilfe", "Zeichnet die Island über allem", ::a11yEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            statusRow("Mitteilungen & Musik", "Benachrichtigungen, Musik, Live-Aktivitäten", ::notifEnabled) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        ), footer = "Android 13+: Ausgegraut? → App-Info öffnen → ⋮ oben rechts → „Eingeschränkte Einstellungen zulassen“."))
        val optional = ArrayList<View>()
        optional.add(permRow("Standort", "Für den Standort-Pfeil", Manifest.permission.ACCESS_FINE_LOCATION))
        if (Build.VERSION.SDK_INT >= 31) {
            optional.add(permRow("Bluetooth", "Für Kopfhörer + Akkustand", Manifest.permission.BLUETOOTH_CONNECT))
        }
        optional.add(statusRow("Helligkeit ändern", "Für den Regler im Kontrollzentrum", { Settings.System.canWrite(this) }) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {}
        })
        c.addView(section("Optional", optional))
        c.addView(section(null, listOf(
            actionRow("App-Info öffnen") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            },
        )))
    }

    private fun infoPage() = page("Info", true) { c ->
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (_: Exception) { "?" }
        c.addView(section(null, listOf(
            valueRow("App", APP_NAME),
            valueRow("Version", ver ?: "?"),
            valueRow("Gerät", "${Build.MANUFACTURER} ${Build.MODEL}"),
            valueRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"),
            valueRow("Island läuft", if (IslandBus.serviceRunning) "Ja" else "Nein"),
        )))

        val file = IslandApp.crashFile(this)
        val crash = try { if (file.exists()) file.readText() else null } catch (_: Exception) { null }
        val crashText = TextView(this).apply {
            text = crash ?: "Keine Abstürze gespeichert 🎉"
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(pal.secondary)
            setPadding(dpi(16f), dpi(12f), dpi(16f), dpi(12f))
            setTextIsSelectable(true)
            maxLines = 40
        }
        c.addView(section("Fehlerbericht", listOf(
            crashText,
            actionRow("Kopieren") {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("$APP_NAME Fehlerbericht", crashText.text))
                Toast.makeText(this, "Kopiert – schick ihn mir einfach 💌", Toast.LENGTH_SHORT).show()
            },
            actionRow("Löschen", destructive = true) {
                file.delete()
                crashText.text = "Keine Abstürze gespeichert 🎉"
            },
        ), footer = "Stürzt $APP_NAME ab, landet hier der genaue Grund. Kopieren und an mich schicken → ich fixe es."))

        c.addView(section("Sichern", listOf(
            actionRow("Einstellungen exportieren") {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("Lumora Einstellungen", Prefs.export(this)))
                Toast.makeText(this, "In die Zwischenablage kopiert 📋", Toast.LENGTH_SHORT).show()
            },
            actionRow("Einstellungen importieren") {
                val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val txt = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
                AlertDialog.Builder(this)
                    .setTitle("Aus Zwischenablage importieren?")
                    .setMessage("Deine aktuellen Einstellungen werden ersetzt.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Importieren") { _, _ ->
                        if (Prefs.import(this, txt)) recreate()
                        else Toast.makeText(this, "Keine gültigen Lumora-Einstellungen in der Zwischenablage", Toast.LENGTH_LONG).show()
                    }
                    .show()
            },
        ), footer = "Exportieren kopiert alles als Text – z.B. für ein neues Handy."))

        c.addView(section(null, listOf(
            actionRow("Alle Einstellungen zurücksetzen", destructive = true) {
                AlertDialog.Builder(this)
                    .setTitle("Alles zurücksetzen?")
                    .setMessage("Alle Einstellungen gehen zurück auf Standard (Apple-Design).")
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

    private fun appsPage(hideMode: Boolean = false) = page(if (hideMode) "In Apps ausblenden" else "Apps", true) { c ->
        val setKey = if (hideMode) Prefs.HIDDEN_IN_APPS else Prefs.EXCLUDED
        val listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var rows: List<View> = emptyList()
        c.addView(searchField { q ->
            val ql = q.lowercase()
            rows.forEach { r ->
                r.visibility = if (ql.isEmpty() || (r.tag as? String)?.contains(ql) == true) View.VISIBLE else View.GONE
            }
        })
        c.addView(footerText(if (hideMode)
            "Ist eine eingeschaltete App im Vordergrund (z.B. Spiele, Videos), verschwindet die Island komplett."
        else "Ausgeschaltete Apps erscheinen nicht in der Island – weder Mitteilungen noch Live-Aktivitäten."))
        c.addView(listBox)
        listBox.addView(TextView(this).apply {
            text = "Lädt Apps …"
            setTextColor(pal.secondary)
            gravity = Gravity.CENTER
            setPadding(0, dpi(30f), 0, 0)
        })

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = try {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
                    .map { it.activityInfo.applicationInfo }
                    .distinctBy { it.packageName }
                    .filter { it.packageName != packageName }
                    .map { Triple(it.packageName, pm.getApplicationLabel(it).toString(), it) }
                    .sortedBy { it.second.lowercase() }
            } catch (_: Exception) { emptyList() }
            main.post {
                if (isFinishing || isDestroyed) return@post
                listBox.removeAllViews()
                val cfgNow = Prefs.load(this)
                val chosen = if (hideMode) cfgNow.hiddenInApps else cfgNow.excludedApps
                rows = apps.map { (pkg, label, info) ->
                    val row = baseRow()
                    val iv = ImageView(this)
                    try { iv.setImageDrawable(info.loadIcon(pm)) } catch (_: Exception) {}
                    row.addView(iv, LinearLayout.LayoutParams(dpi(30f), dpi(30f)).apply { rightMargin = dpi(14f) })
                    row.addView(rowTitle(label), LinearLayout.LayoutParams(0, -2, 1f))
                    val sw = IosSwitch(this, pal)
                    // Mitteilungen: an = erlaubt · Ausblenden: an = hier verstecken
                    sw.set(if (hideMode) pkg in chosen else pkg !in chosen)
                    sw.onChange = { on -> Prefs.setInSet(this, setKey, pkg, if (hideMode) on else !on) }
                    row.addView(sw)
                    row.setOnClickListener { sw.performClick() }
                    row.tag = label.lowercase()
                    row
                }
                listBox.addView(section(null, rows))
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
                blob(width * 0.55f, height * 0.12f, width * 0.22f, 0x55FFFFFF)
            }
        }, FrameLayout.LayoutParams(-1, -1))
        card.addView(TextView(this).apply {
            text = "9:41"
            textSize = 15f
            typeface = pal.font(700)
            setTextColor(Color.WHITE)
        }, FrameLayout.LayoutParams(-2, -2).apply { leftMargin = dpi(26f); topMargin = dpi(18f) })

        val view = IslandView(this, topInset = dp(12f))
        val ctl = IslandController(view, object : IslandController.Host {
            override fun haptic(strong: Boolean) {
                view.performHapticFeedback(if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK)
            }
            override fun volume() = demoPanelVol
            override fun setVolume(v: Float) { demoPanelVol = v }
            override fun brightness() = demoPanelBright
            override fun canBrightness() = true
            override fun setBrightness(v: Float) { demoPanelBright = v }
            override fun setTorchLevel(level: Float) {
                demoTorchLevel = level
                if (demoLive.containsKey("torch")) {
                    demoLive["torch"] = demoTorchContent()
                    previews.forEach { it.setLive(demoLive.values.toList()) }
                }
            }
        })
        card.addView(view, FrameLayout.LayoutParams(-1, -1))
        ctl.cfg = previewCfg()
        ctl.setCharging(Content.Charging(batteryLevel(), false))
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
        chip("🎛  Kontrollzentrum") { previews.forEach { it.showPanel() } }
        chip("♫  Musik") { demoMusicToggle() }
        chip("💬  Nachricht") { demoNotif() }
        chip("📞  Anruf") { demoCall() }
        chip("⏱  Timer") { demoTimer() }
        chip("⚡  Laden") { demoCharging() }
        chip("🔊  Lautstärke") { demoVolume() }
        chip("🔦  Lampe") { demoTorch() }
        chip("🧭  Navi") { demoNav() }
        chip("⬆  Upload") { demoUpload() }
        chip("🎧  AirPods") { demoTransient(Content.Device("AirPods Pro", true, 82)) }
        chip("🌙  Fokus") { demoFocus() }
        chip("✈  Flugmodus") { demoTransient(AirplaneContent(true)) }
        chip("🍃  Energiesparen") { demoTransient(StatusContent("powersave", "Energiesparen an", SGlyph.LEAF, 0xFFFFD60A.toInt(), widthDp = 190f)) }
        chip("📶  Hotspot") { demoTransient(StatusContent("hotspot", "Hotspot an", SGlyph.HOTSPOT, 0xFF30D158.toInt())) }
        chip("📅  Termin") { demoTransient(StatusContent("calendar", "Zahnarzt · in 10 min", SGlyph.CALENDAR, 0xFFFF453A.toInt(), widthDp = 230f)) }
        chip("ᛒ  Bluetooth") { demoTransient(StatusContent("bluetooth", "Bluetooth an", SGlyph.BLUETOOTH, 0xFF0A84FF.toInt(), widthDp = 160f)) }
        chip("🔓  Entsperren") { demoTransient(UnlockContent()) }
        chip("🔕  Lautlos") { demoRinger() }
        chip("📍  Punkte") { demoPrivacy() }
        chip("🌡  Hitze") { demoTransient(HeatContent(44f)) }
        chip("📶  WLAN") { demoTransient(NetworkContent(1)) }
        chip("⏰  Wecker") { demoTransient(AlarmContent("07:00")) }
        val hs = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        card.addView(hs, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { bottomMargin = dpi(16f) })

        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(card, LinearLayout.LayoutParams(-1, dpi(if (big) 310f else 300f)))
        if (big) box.addView(TextView(this).apply {
            text = "Chips antippen → testet die Vorschau UND die echte Island. Gedrückt halten = groß."
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
    private var focusOn = false
    private var demoVol = 0.5f
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
            app = APP_NAME,
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
        val hangUp: () -> Unit = {
            demoLive.remove(key)
            pushDemoLive()
        }
        val accept: () -> Unit = {
            demoLive[key] = Content.Call(key, "Mama 💛", "Telefon", null, false, System.currentTimeMillis(), null, hangUp, toast("Würde die Telefon-App öffnen"))
            pushDemoLive()
        }
        demoLive[key] = Content.Call(key, "Mama 💛", "Telefon · Eingehender Anruf", null, true, 0L, accept, hangUp, toast("Würde die Telefon-App öffnen"))
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

    private var demoTorchLevel = 0.8f
    private var demoPanelVol = 0.6f
    private var demoPanelBright = 0.7f

    private fun demoTorchContent(): TorchContent =
        TorchContent({ demoLive.remove("torch"); pushDemoLive() }, demoTorchLevel, true)

    private fun demoTorch() {
        val key = "torch"
        if (demoLive.remove(key) == null) demoLive[key] = demoTorchContent()
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

    private fun batteryLevel(): Int = try {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: 76
    } catch (_: Exception) { 76 }

    private fun demoCharging() = demoTransient(Content.Charging(batteryLevel(), true))

    private fun demoVolume() {
        demoVol = if (demoVol >= 0.95f) 0.15f else (demoVol + 0.2f).coerceAtMost(1f)
        demoTransient(VolumeContent(demoVol, false))
    }

    private fun demoFocus() {
        focusOn = !focusOn
        demoTransient(FocusContent(focusOn))
    }

    private fun demoRinger() {
        ringerSilent = !ringerSilent
        demoTransient(Content.Ringer(silent = ringerSilent, vibrate = false))
    }

    private fun demoPrivacy() {
        val on = Privacy(camera = true, mic = true, location = true)
        previews.forEach { it.privacy = on }
        main.postDelayed({ previews.forEach { it.privacy = Privacy() } }, 4000)
        Toast.makeText(this, "Erscheinen in der leeren Pille – Musik & Co. kurz aus", Toast.LENGTH_SHORT).show()
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
    // Listen-Bausteine (passen sich dem Look an)
    // ==================================================================

    private fun sectionHeader(title: String): View = TextView(this).apply {
        text = if (pal.iosLike || pal.isNothing) title.uppercase() else title
        textSize = if (pal.isAndroid) 14f else 13f
        typeface = pal.font(if (pal.isAndroid || pal.isOneUi) 600 else 400)
        setTextColor(if (pal.isAndroid) pal.accent else pal.secondary)
        letterSpacing = if (pal.isNothing) 0.12f else 0.02f
        setPadding(dpi(16f), dpi(26f), dpi(16f), dpi(8f))
    }

    private fun footerText(t: String): View = TextView(this).apply {
        text = t
        textSize = 13f
        setTextColor(pal.secondary)
        typeface = pal.font(400)
        setLineSpacing(0f, 1.15f)
        setPadding(dpi(16f), dpi(7f), dpi(16f), 0)
    }

    private fun section(title: String?, rows: List<View>, footer: String? = null): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (title != null) box.addView(sectionHeader(title))
        else box.addView(View(this), LinearLayout.LayoutParams(-1, dpi(18f)))
        val r = dp(pal.cardRadius)
        if (pal.isAndroid) {
            // Material 3 (Android 16): einzelne Kacheln mit kleinen Lücken
            rows.forEachIndexed { i, v ->
                val first = i == 0
                val last = i == rows.size - 1
                val small = dp(5f)
                val tl = if (first) r else small
                val bl = if (last) r else small
                v.background = layered(GradientDrawable().apply {
                    cornerRadii = floatArrayOf(tl, tl, tl, tl, bl, bl, bl, bl)
                    setColor(pal.card)
                }, v.background)
                v.clipToOutline = true
                box.addView(v, LinearLayout.LayoutParams(-1, -2).apply { if (!last) bottomMargin = dpi(2f) })
            }
        } else {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    cornerRadius = r
                    setColor(pal.card)
                    if (pal.isNothing) setStroke(dpi(1f), pal.separator)
                }
                clipToOutline = true
            }
            rows.forEachIndexed { i, v ->
                if (i > 0) {
                    val inset = if ((rows[i - 1].getTag(TAG_HAS_ICON) as? Boolean) == true) 59f else 16f
                    card.addView(View(this).apply { setBackgroundColor(pal.separator) },
                        LinearLayout.LayoutParams(-1, maxOf(1, dpi(0.5f))).apply {
                            leftMargin = dpi(if (pal.isOneUi) 20f else inset)
                            if (pal.isOneUi) rightMargin = dpi(20f)
                        })
                }
                card.addView(v)
            }
            box.addView(card)
        }
        if (footer != null) box.addView(footerText(footer))
        return box
    }

    /** Hintergrund + Klick-Effekt kombinieren */
    private fun layered(bg: android.graphics.drawable.Drawable, top: android.graphics.drawable.Drawable?): android.graphics.drawable.Drawable =
        if (top == null) bg else android.graphics.drawable.LayerDrawable(arrayOf(bg, top))

    private fun baseRow(): LinearLayout = hRow(this).apply {
        minimumHeight = dpi(if (pal.isAndroid) 60f else if (pal.isOneUi) 56f else 48f)
        val h = if (pal.isOneUi) 20f else 16f
        setPadding(dpi(h), dpi(8f), dpi(h - 2f), dpi(8f))
    }

    private fun rowTitle(t: String): TextView = TextView(this).apply {
        text = t
        textSize = when { pal.isAndroid -> 16.5f; pal.isNothing -> 15f; else -> 17f }
        typeface = pal.font(if (pal.isOneUi) 500 else 400)
        setTextColor(pal.label)
    }

    private fun withIcon(row: LinearLayout, icon: View?) {
        if (icon == null) return
        row.addView(icon, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(if (pal.isAndroid) 18f else 14f) })
        row.setTag(TAG_HAS_ICON, true)
    }

    private fun labelBlock(label: String, sub: String?): View {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(rowTitle(label))
        if (sub != null) col.addView(TextView(this).apply {
            text = sub
            textSize = if (pal.isAndroid) 14f else 13f
            typeface = pal.font(400)
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
        sw.onChange = { prefs.edit().putBoolean(key, it).putInt(Prefs.DESIGN, customIfLook(key)).apply() }
        row.addView(sw)
        row.setOnClickListener { sw.performClick() }
        return row
    }

    private fun sliderRow(label: String, key: String, def: Int, min: Int, max: Int, fmt: (Int) -> String): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val h = if (pal.isOneUi) 20f else 16f
            setPadding(dpi(h), dpi(10f), dpi(h - 2f), dpi(6f))
        }
        val top = hRow(this)
        top.addView(rowTitle(label), LinearLayout.LayoutParams(0, -2, 1f))
        val value = TextView(this).apply {
            textSize = 15f
            setTextColor(if (pal.isOneUi || pal.isAndroid) pal.accent else pal.secondary)
            fontFeatureSettings = "tnum"
            typeface = pal.font(500)
        }
        top.addView(value)
        col.addView(top)
        val s = IosSlider(this, pal, min, max)
        val cur = (try { prefs.getInt(key, def) } catch (_: ClassCastException) { def }).coerceIn(min, max)
        s.set(cur)
        value.text = fmt(cur)
        s.onChange = {
            value.text = fmt(it)
            prefs.edit().putInt(key, it).putInt(Prefs.DESIGN, customIfLook(key)).apply()
        }
        col.addView(s, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4f) })
        return col
    }

    /** Design bleibt, wie es ist (Nothing bleibt Nothing). Custom merkt sich Änderungen selbst. */
    @Suppress("UNUSED_PARAMETER")
    private fun customIfLook(key: String): Int = prefs.getInt(Prefs.DESIGN, D.design)

    private fun setLook(key: String, v: Int) {
        prefs.edit().putInt(key, v).putInt(Prefs.DESIGN, customIfLook(key)).apply()
    }

    private fun segmentRow(labels: List<String>, current: Int, onChange: (Int) -> Unit): View {
        val box = FrameLayout(this).apply { setPadding(dpi(12f), dpi(10f), dpi(12f), dpi(10f)) }
        val seg = Segmented(this, pal, labels)
        seg.set(current.coerceIn(0, labels.size - 1))
        seg.onChange = onChange
        box.addView(seg, FrameLayout.LayoutParams(-1, -2))
        return box
    }

    /** Segment mit kleiner Überschrift darüber. */
    private fun labeled(label: String, inner: View): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dpi(8f), 0, 0)
        }
        col.addView(TextView(this).apply {
            text = label
            textSize = 13f
            typeface = pal.font(500)
            setTextColor(pal.secondary)
            setPadding(dpi(16f), 0, dpi(16f), 0)
        })
        col.addView(inner)
        return col
    }

    /** Auswahlliste mit Häkchen (iOS), Radio-Buttons (Material / One UI) oder Punkt (Nothing). */
    private fun choiceRows(
        key: String, def: Int, options: List<Pair<String, String?>>,
        fontPreview: Boolean = false, offset: Int = 0, onPick: ((Int) -> Unit)? = null,
    ): List<View> {
        val indicators = ArrayList<ChoiceMark>()
        val rows = options.mapIndexed { idx, (label, sub) ->
            val i = idx + offset
            val row = baseRow()
            val mark = ChoiceMark(this, pal)
            indicators.add(mark)
            if (pal.isAndroid || pal.isOneUi) {
                row.addView(mark, LinearLayout.LayoutParams(dpi(24f), dpi(24f)).apply { rightMargin = dpi(16f) })
            }
            val lb = labelBlock(label, sub)
            if (fontPreview && i >= 0) ((lb as LinearLayout).getChildAt(0) as TextView).typeface = Fonts.get(this, i, 500)
            row.addView(lb, LinearLayout.LayoutParams(0, -2, 1f))
            if (pal.iosLike || pal.isNothing) {
                row.addView(mark, LinearLayout.LayoutParams(dpi(22f), dpi(22f)))
            }
            row.setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                setLook(key, i)
                indicators.forEachIndexed { j, m -> m.setOn(j + offset == i, true) }
                onPick?.invoke(i)
            }
            row.background = pressable()
            row
        }
        val cur = try { prefs.getInt(key, def) } catch (_: ClassCastException) { def }
        indicators.forEachIndexed { j, m -> m.setOn(j + offset == cur, false) }
        return rows
    }

    private fun colorRow(): View {
        val row = baseRow()
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
            typeface = pal.font(600)
        }
        row.addView(st)
        val update = {
            val on = check()
            st.text = if (on) "Aktiv ✓" else "Einschalten ›"
            st.setTextColor(if (on) pal.green else pal.accent)
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

    private fun navRow(label: String, icon: View?, value: String? = null, sub: String? = null, onClick: () -> Unit): View {
        val row = baseRow()
        withIcon(row, icon)
        row.addView(if (sub != null) labelBlock(label, sub) else rowTitle(label), LinearLayout.LayoutParams(0, -2, 1f))
        if (value != null) row.addView(TextView(this).apply {
            text = value
            textSize = 15f
            typeface = pal.font(400)
            setTextColor(if (pal.isOneUi) pal.accent else pal.secondary)
            setPadding(0, 0, dpi(6f), 0)
        })
        if (pal.iosLike || pal.isNothing) row.addView(TextView(this).apply {
            text = if (pal.isNothing) "→" else "›"
            textSize = if (pal.isNothing) 17f else 22f
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
            text = if (pal.isNothing) label.uppercase() else label
            textSize = if (pal.isNothing) 14f else 17f
            typeface = pal.font(if (pal.iosLike) 400 else 500)
            setTextColor(if (destructive) pal.red else pal.accent)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.setOnClickListener { onClick() }
        row.background = pressable()
        return row
    }

    private fun valueRow(label: String, value: String): View {
        val row = baseRow()
        row.addView(rowTitle(label), LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = value
            textSize = 16f
            typeface = pal.font(400)
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

    // ==================================================================
    // Übersichts-Seiten
    // ==================================================================

    private fun stylePage() = page("Design & Stil", true, GlassDestination.DESIGN) { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(sectionHeader("Vorlage"))
        c.addView(designStrip())
        c.addView(section("Anpassen", listOf(
            navRow("Design-Vorlagen", GlyphIcon(this, 0xFFFF2D55.toInt(), Glyph.ISLAND, pal),
                value = designName(prefs.getInt(Prefs.DESIGN, D.design))) { open("design") },
            navRow("Erscheinungsbild", GlyphIcon(this, 0xFF0A84FF.toInt(), Glyph.PAINT, pal),
                sub = "Form, Akzentfarbe, Musik-Elemente, Leuchten") { open("appearance") },
            navRow("Liquid Glass", GlyphIcon(this, 0xFF32ADE6.toInt(), Glyph.DROP, pal),
                sub = "Milchigkeit, Glanz, Kante, Farbsaum") { open("glass") },
            navRow("Schriften", GlyphIcon(this, 0xFFFF9500.toInt(), Glyph.FONT, pal),
                value = Fonts.ALL[prefs.getInt(Prefs.FONT_STYLE, D.fontStyle).coerceIn(0, Fonts.ALL.size - 1)].name) { open("fonts") },
            navRow("Animation", GlyphIcon(this, 0xFFBF5AF2.toInt(), Glyph.SPARKLE, pal),
                sub = "Apple, Android, One UI, Nothing, Jelly") { open("animation") },
            navRow("App-Symbol", GlyphIcon(this, 0xFF1C1C1E.toInt(), Glyph.APPICON, pal),
                value = iconName(prefs.getString(PREF_ICON, "classic") ?: "classic")) { open("icons") },
        )))
    }

    private fun appsHubPage() = page("Apps", true) { c ->
        c.addView(section(null, listOf(
            navRow("Apps für Mitteilungen", GlyphIcon(this, 0xFF5856D6.toInt(), Glyph.GRID, pal),
                sub = "Welche Apps in der Island auftauchen dürfen") { open("apps") },
            navRow("In Apps ausblenden", GlyphIcon(this, 0xFF636366.toInt(), Glyph.EYE, pal),
                sub = "Island verschwindet z.B. in Spielen oder Videos") { open("hidein") },
        )))
    }

    /** Kontrollzentrum-artige Schnellschalter (4 pro Reihe). */
    private fun quickTiles(items: List<Pair<Triple<String, String, Glyph>, Int>>): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.chunked(4).forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (t, color) ->
                val (label, key, glyph) = t
                val def = when (key) { Prefs.VOLUME -> D.showVolume; else -> true }
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding(dpi(4f), dpi(12f), dpi(4f), dpi(10f))
                }
                val icon = GlyphIcon(this, color, glyph, pal)
                tile.addView(icon)
                val tv = TextView(this).apply {
                    text = label
                    textSize = 12f
                    typeface = pal.font(500)
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dpi(6f), 0, 0)
                }
                tile.addView(tv)
                fun paint() {
                    val on = prefs.getBoolean(key, def)
                    tile.background = GradientDrawable().apply {
                        cornerRadius = dp(18f)
                        setColor(when {
                            pal.isGlass -> if (on) pal.fill else Color.TRANSPARENT
                            on -> Palette.mixC(pal.card, color, if (pal.dark) 0.28f else 0.16f)
                            else -> pal.card
                        })
                        if (pal.isNothing) setStroke(dpi(1f), if (on) pal.accent else pal.separator)
                    }
                    tv.setTextColor(if (on) pal.label else pal.secondary)
                    icon.alpha = if (on) 1f else 0.35f
                }
                paint()
                tile.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    prefs.edit().putBoolean(key, !prefs.getBoolean(key, def)).apply()
                    paint()
                    it.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).withEndAction {
                        it.animate().scaleX(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(android.view.animation.OvershootInterpolator(2.5f)).start()
                    }.start()
                }
                r.addView(tile, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dpi(4f); rightMargin = dpi(4f) })
            }
            box.addView(r, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(8f) })
        }
        return box
    }

    // ==================================================================
    // Schriften, Timer, Verlauf, Begrüßung
    // ==================================================================

    private fun fontsPage() = page("Schriften", true) { c ->
        c.addView(previewCard(big = false, root = c))
        c.addView(section("Island-Schrift", choiceRows(Prefs.FONT_STYLE, D.fontStyle,
            Fonts.ALL.map { it.name to it.note }, fontPreview = true)))
        c.addView(section("App-Schrift",
            choiceRows(Prefs.APP_FONT, D.appFont, listOf("Automatisch" to "Passend zum Look") + Fonts.ALL.map { it.name to it.note },
                fontPreview = true, offset = -1) { main.postDelayed({ recreate() }, 250) },
            footer = "Ändert die Schrift dieser Einstellungen. Alle Schriften sind frei (SIL Open Font License)."))
    }

    private fun timerPage() = page("Timer & Stoppuhr", true) { c ->
        val status = TextView(this).apply {
            textSize = 15f
            typeface = pal.font(500)
            setTextColor(pal.secondary)
            setPadding(dpi(16f), dpi(4f), dpi(16f), 0)
        }
        val update = {
            status.text = buildString {
                append(if (OwnTimers.timerRunning) "⏱ Timer läuft" else "Kein Timer aktiv")
                append("   ·   ")
                append(if (OwnTimers.stopwatchRunning) "⏲ Stoppuhr läuft" else "Stoppuhr aus")
            }
        }
        update()
        refreshers.add(update)
        c.addView(status)
        fun needService() {
            if (!IslandBus.serviceRunning) Toast.makeText(this, "Lumora ist noch nicht aktiv – erst die Bedienungshilfe einschalten", Toast.LENGTH_LONG).show()
        }
        c.addView(section("Timer", listOf(
            sliderRow("Dauer", Prefs.TIMER_MINUTES, D.timerMinutes, 1, 120) { "$it min" },
            actionRow("Timer starten") {
                OwnTimers.startTimer(prefs.getInt(Prefs.TIMER_MINUTES, D.timerMinutes))
                needService(); update()
            },
            actionRow("+1 Minute") { OwnTimers.addMinute(); update() },
            actionRow("Timer stoppen", destructive = true) { OwnTimers.stopTimer(); update() },
        ), footer = "Der Timer läuft in der Island. Am Ende vibriert dein Handy."))
        c.addView(section("Stoppuhr", listOf(
            actionRow("Stoppuhr starten / stoppen") {
                if (OwnTimers.stopwatchRunning) OwnTimers.stopStopwatch() else { OwnTimers.startStopwatch(); needService() }
                update()
            },
        ), footer = "Tipp: Bei „Immer sichtbar“ die leere Pille gedrückt halten startet sofort einen Timer (einstellbar unter Gesten)."))
    }

    private fun historyPage() = page("Mitteilungs-Verlauf", true) { c ->
        val list = NotifHistory.all(this)
        if (list.isEmpty()) {
            c.addView(footerText("Noch nichts hier. Sobald Mitteilungen in der Island erscheinen, landen sie hier – nur auf deinem Handy."))
        } else {
            c.addView(section("Zuletzt", list.map { e ->
                LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpi(16f), dpi(10f), dpi(16f), dpi(10f))
                    addView(TextView(this@MainActivity).apply {
                        text = e.app + "  ·  " + android.text.format.DateUtils.getRelativeTimeSpanString(e.time)
                        textSize = 12f
                        typeface = pal.font(500)
                        setTextColor(pal.secondary)
                    })
                    addView(rowTitle(e.title.ifBlank { e.app }).apply { typeface = pal.font(600) })
                    if (e.text.isNotBlank()) addView(TextView(this@MainActivity).apply {
                        text = e.text
                        textSize = 14f
                        typeface = pal.font(400)
                        setTextColor(pal.secondary)
                        maxLines = 3
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                }
            }))
            c.addView(section(null, listOf(
                actionRow("Verlauf löschen", destructive = true) { NotifHistory.clear(this); pop() },
            )))
        }
    }

    private fun welcomePage() = page("Willkommen", false) { c ->
        val iv = ImageView(this).apply {
            val res = resources.getIdentifier("ic_launcher_" + (prefs.getString(PREF_ICON, "classic") ?: "classic"), "mipmap", packageName)
            if (res != 0) try { setImageDrawable(getDrawable(res)) } catch (_: Exception) {}
            background = GradientDrawable().apply { cornerRadius = dp(26f); setColor(Color.TRANSPARENT) }
            clipToOutline = true
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpi(8f), 0, dpi(4f))
        }
        top.addView(iv, LinearLayout.LayoutParams(dpi(96f), dpi(96f)))
        top.addView(TextView(this).apply {
            text = "Deine Dynamic Island – in jedem Design.\nDrei kurze Schritte, dann geht's los ✨"
            textSize = 16f
            typeface = pal.font(400)
            gravity = Gravity.CENTER
            setTextColor(pal.secondary)
            setPadding(0, dpi(14f), 0, 0)
        })
        c.addView(top)
        c.addView(section("1 · Island anzeigen", listOf(
            statusRow("Bedienungshilfe", "Damit Lumora über der Statusleiste zeichnen darf", ::a11yEnabled) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        ), footer = "Ausgegraut? App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“."))
        c.addView(section("2 · Musik & Mitteilungen", listOf(
            statusRow("Mitteilungszugriff", "Für Musik, Timer, Anrufe, Navigation …", ::notifEnabled) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            },
        )))
        c.addView(sectionHeader("3 · Design wählen"))
        c.addView(designStrip())
        c.addView(section(null, listOf(
            actionRow("Los geht's ✨") {
                smartFit()
                prefs.edit().putBoolean(Prefs.ONBOARDED, true).apply()
                pop()
            },
        )))
    }

    // ==================================================================
    // App-Symbol
    // ==================================================================

    private fun iconName(id: String) = ICONS.firstOrNull { it.first == id }?.second ?: "Klassisch"

    private fun iconsPage() = page("App-Symbol", true) { c ->
        val tiles = ArrayList<Pair<String, View>>()
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun mark() {
            val now = prefs.getString(PREF_ICON, "classic") ?: "classic"
            tiles.forEach { (id, v) ->
                (v.background as GradientDrawable).setStroke(dpi(3f), if (id == now) pal.accent else Color.TRANSPARENT)
            }
        }
        ICONS.chunked(4).forEach { row ->
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (id, name) ->
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                val frame = FrameLayout(this).apply {
                    background = GradientDrawable().apply { cornerRadius = dp(22f); setColor(Color.TRANSPARENT) }
                    setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(4f))
                }
                val iv = ImageView(this).apply {
                    val res = resources.getIdentifier("ic_launcher_$id", "mipmap", packageName)
                    if (res != 0) try { setImageDrawable(getDrawable(res)) } catch (_: Exception) {}
                    background = GradientDrawable().apply { cornerRadius = dp(18f); setColor(Color.TRANSPARENT) }
                    clipToOutline = true
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                frame.addView(iv, FrameLayout.LayoutParams(dpi(64f), dpi(64f)))
                col.addView(frame)
                col.addView(TextView(this).apply {
                    text = name
                    textSize = 12f
                    typeface = pal.font(500)
                    setTextColor(pal.label)
                    gravity = Gravity.CENTER
                    setPadding(0, dpi(6f), 0, 0)
                })
                col.setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    setAppIcon(id)
                    mark()
                }
                tiles.add(id to frame)
                r.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(4 - row.size) { r.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            grid.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(16f) })
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(pal.cardRadius); setColor(pal.card) }
            setPadding(dpi(8f), dpi(2f), dpi(8f), dpi(18f))
            addView(grid)
        }
        c.addView(sectionHeader("Wähle dein Symbol"))
        c.addView(card)
        c.addView(footerText("Das Symbol auf dem Homescreen ändert sich nach ein paar Sekunden. Manche Launcher brauchen einen Neustart dafür. Mit „Themen-Symbolen“ (Android 13+) passt sich jedes Symbol deinen Farben an."))
        mark()
    }

    private fun setAppIcon(id: String) {
        if ((prefs.getString(PREF_ICON, "classic") ?: "classic") == id) return
        val pm = packageManager
        // Erst das neue einschalten, dann die anderen aus – so ist nie gar keins aktiv
        for ((iid, _) in ICONS.sortedBy { if (it.first == id) 0 else 1 }) {
            val cn = android.content.ComponentName(this, "$packageName.Icon" + iid.replaceFirstChar { it.uppercase() })
            try {
                pm.setComponentEnabledSetting(
                    cn,
                    if (iid == id) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            } catch (_: Exception) {}
        }
        prefs.edit().putString(PREF_ICON, id).apply()
        Toast.makeText(this, "Symbol geändert ✨ – kann ein paar Sekunden dauern", Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val APP_NAME = "Lumora"
        private const val PREF_ICON = "app_icon"
        private const val PREF_SMART = "smart_result"
        private val ICONS = listOf(
            "classic" to "Klassisch", "light" to "Hell", "lagoon" to "Lagune", "sunset" to "Sunset",
            "midnight" to "Mitternacht", "glass" to "Glas", "neon" to "Neon", "dots" to "Punkte",
            "aurora" to "Aurora", "rose" to "Rosé", "gold" to "Gold", "rainbow" to "Regenbogen", "trans" to "Trans Pride",
            "pride" to "Pride", "bi" to "Bi Pride", "enby" to "Nonbinary",
        )
        private const val STATE_PAGES = "pages"
        /** Eigene Tag-Keys (müssen wie Resource-IDs aussehen → 0x7f…) */
        private const val TAG_PREVIEW = 0x7f0f0001
        private const val TAG_HAS_ICON = 0x7f0f0002

        /** Suchindex: Begriffe → Seite */
        private val SEARCH_INDEX = listOf(
            "Design Apple Android One-UI Nothing Custom Vorlage" to "design",
            "Look App Material iOS" to "design",
            "Stil Schwarz Farbig Material Graphit Umriss Form Fläche" to "appearance",
            "Akzentfarbe Farbe Cover Systemfarbe" to "appearance",
            "Wellenform Fortschritt Tasten Buttons" to "appearance",
            "Schrift Schriftart Google-Sans Mono Serif Textgröße" to "appearance",
            "Leuchten Glow Schatten" to "appearance",
            "Liquid-Glass Glas Milchigkeit Glanz Tönung Kante Farbsaum Regenbogen" to "glass",
            "Position Kamera Vertikal Horizontal" to "geometry",
            "Breite Höhe Rundung Pille Kompakt Erweitert Ecken" to "geometry",
            "Animation Bounce Tempo Federung Drück-Effekt Bewegung" to "animation",
            "Live-Aktivitäten Anrufe Timer Stoppuhr Navigation Upload Download Taschenlampe" to "live",
            "Laden Akku schwach Bubble" to "live",
            "Lautstärke Lautlos Klingeln Fokus Flugmodus Kopfhörer AirPods Entsperren" to "system",
            "Leere Pille Uhrzeit Akku" to "system",
            "Datenschutz Kamera Mikrofon Standort Punkte" to "system",
            "Musik Wellenform App-Icon Spulen Pausiert Song Player" to "music",
            "Mitteilungen Benachrichtigungen Anzeigedauer still Nicht-stören Inhalt Aktionen" to "notif",
            "Apps auswählen ausschließen" to "apps",
            "Gesten Tippen erweitert Runterziehen einklappen Haptik Vibration" to "behavior",
            "Sperrbildschirm Querformat durchklickbar Sichtbarkeit" to "behavior",
            "Berechtigungen Bedienungshilfe Bluetooth Standort" to "permissions",
            "Info Version Fehlerbericht Absturz zurücksetzen" to "info",
            "App-Symbol Icon Logo Symbol Homescreen" to "icons",
            "Schrift Schriftart Font Inter Outfit Nunito Doto Pacifico App-Schrift" to "fonts",
            "Timer Stoppuhr Countdown eigener" to "timer",
            "Verlauf Mitteilungen History" to "history",
            "Ausblenden Spiele Videos Vordergrund Apps" to "hidein",
            "Animationsstil Jelly Material Android Nothing" to "animation",
            "Doppeltippen Wischen seitlich Song wechseln gedrückt halten" to "behavior",
            "Ruhezeiten Warteschlange nacheinander" to "notif",
            "Vinyl Schallplatte" to "music",
            "Akku-Rand Hitze Temperatur Schnellladen Restzeit" to "live",
            "Internet WLAN Wecker Energiesparmodus Datum" to "system",
            "Export Import Sichern Backup" to "info",
            "Smart-Anpassung KI automatisch Kamera kalibrieren erkennen" to "geometry",
            "Design Stil Übersicht" to "style",
            "Mitteilungen Größe Stil Banner Kompakt Zeilen Breite App-Name runde Icons" to "notif",
            "Kontrollzentrum Regler Helligkeit Lautstärke Kapsel" to "behavior",
            "Kapsel MT Capsule Material You" to "design",
            "Pixel 11 Google Expressive" to "design",
        )
    }
}

/** Auswahl-Markierung: ✓ (iOS), Radio (Material/One UI), Punkt (Nothing). */
@SuppressLint("ViewConstructor")
private class ChoiceMark(ctx: Context, private val pal: Palette) : View(ctx) {
    private val s = Spring(0f, stiffness = 600f, dampingRatio = 0.7f, precision = 0.002f)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var last = 0L

    fun setOn(v: Boolean, animate: Boolean) {
        if (animate) s.target = if (v) 1f else 0f else s.snap(if (v) 1f else 0f)
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime()
        val dt = if (last == 0L) 1 / 60f else ((now - last) / 1e9f).coerceIn(0.001f, 0.05f)
        last = now
        val moving = s.step(dt)
        val t = s.value.coerceIn(0f, 1.2f)
        val w = width.toFloat()
        val h = height.toFloat()
        val d1 = resources.displayMetrics.density
        when {
            pal.iosLike -> {
                if (t > 0.01f) {
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = 2.4f * d1
                    p.strokeCap = Paint.Cap.ROUND
                    p.strokeJoin = Paint.Join.ROUND
                    p.color = pal.accent
                    p.alpha = (t.coerceAtMost(1f) * 255).toInt()
                    c.save()
                    c.scale(t, t, w / 2, h / 2)
                    c.drawLine(w * 0.2f, h * 0.52f, w * 0.42f, h * 0.74f, p)
                    c.drawLine(w * 0.42f, h * 0.74f, w * 0.82f, h * 0.28f, p)
                    c.restore()
                }
            }
            pal.isNothing -> {
                if (t > 0.01f) {
                    p.style = Paint.Style.FILL
                    p.color = pal.accent
                    c.drawCircle(w / 2, h / 2, 5 * d1 * t, p)
                }
            }
            else -> {
                p.style = Paint.Style.STROKE
                p.strokeWidth = 2 * d1
                p.color = Palette.mixC(pal.outline, pal.accent, t.coerceAtMost(1f))
                c.drawCircle(w / 2, h / 2, w / 2 - 2 * d1, p)
                if (t > 0.01f) {
                    p.style = Paint.Style.FILL
                    p.color = pal.accent
                    c.drawCircle(w / 2, h / 2, (w / 2 - 6.5f * d1) * t, p)
                }
            }
        }
        if (moving) postInvalidateOnAnimation() else last = 0L
    }
}
