package de.myria.home

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class HomeActivity : Activity() {

    private lateinit var prefs: HomePrefs
    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var pager: Pager
    private lateinit var dots: LinearLayout
    private lateinit var dockRow: LinearLayout
    private lateinit var drawer: FrameLayout
    private lateinit var drawerSearch: EditText
    private lateinit var drawerGrid: GridView
    private lateinit var suggestRow: LinearLayout
    private lateinit var webRow: TextView
    private lateinit var doneButton: TextView
    private var clockView: ClockView? = null
    private var todayClock: ClockView? = null
    private var minimalClock: TextView? = null
    private var minimalDate: TextView? = null
    private val main = Handler(Looper.getMainLooper())
    private val ease = PathInterpolator(0.2f, 0.9f, 0.1f, 1f)
    private var drawerOpen = false
    private var editMode = false
    private var insetTop = 0
    private var insetBottom = 0
    private var builtFor = ""
    private val wiggles = ArrayList<ValueAnimator>()
    private val homeIcons = ArrayList<FrameLayout>()
    /** Benachrichtigungspunkte: Paket → Punkt-Views */
    private val badgeViews = java.util.WeakHashMap<TextView, String>()
    private lateinit var widgetHost: android.appwidget.AppWidgetHost
    private var loadedPack: String? = "\u0000"
    private var folderOverlay: View? = null

    // Fix: GestureDetector braucht einen fertigen Context → erst in onCreate anlegen
    // (vorher stürzte der Launcher direkt beim Start ab)
    private lateinit var gestures: GestureDetector
    private lateinit var doubleTapDetector: GestureDetector

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dpi(v: Float) = dp(v).toInt()
    private val look get() = prefs.look
    private val systemDark get() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val androidLightDrawer get() = look == Look.ANDROID && !systemDark
    private fun animationDuration(base: Long) = if (prefs.motionScale == 0) 0L else (base * prefs.motionScale / 100f).roundToInt().toLong().coerceAtLeast(80L)
    private val drawerTextColor get() = if (androidLightDrawer) 0xFF1B1B1F.toInt() else Color.WHITE
    private val textColor get() = if (prefs.darkText) 0xFF111114.toInt() else Color.WHITE
    private val shadowColor get() = if (prefs.darkText) 0x33FFFFFF else 0x88000000.toInt()

    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HomePrefs(this)
        prefs.migrate()
        widgetHost = android.appwidget.AppWidgetHost(this, 0x4C55)
        BadgeService.onChange = { updateBadges() }
        gestures = GestureDetector(this, gestureListener).apply { setIsLongpressEnabled(false) }
        doubleTapDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (editMode) return false
                when (prefs.doubleTap) {
                    1 -> expandNotifications()
                    2 -> openDrawer(focusSearch = true)
                    3 -> startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
                    4 -> openDrawer()
                    else -> return false
                }
                return true
            }
        }).apply { setIsLongpressEnabled(false) }
        applySystemBars()

        root = FrameLayout(this)
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, ins ->
            @Suppress("DEPRECATION")
            insetTop = ins.systemWindowInsetTop
            @Suppress("DEPRECATION")
            insetBottom = ins.systemWindowInsetBottom
            if (::home.isInitialized) home.setPadding(0, insetTop + dpi(8f), 0, insetBottom + dpi(8f))
            if (::drawer.isInitialized) drawer.setPadding(0, insetTop, 0, insetBottom)
            if (::doneButton.isInitialized) (doneButton.layoutParams as? FrameLayout.LayoutParams)?.topMargin = insetTop + dpi(10f)
            ins
        }
        build()
        loadApps()

        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED) else registerReceiver(pkgReceiver, f)
        val t = IntentFilter(Intent.ACTION_TIME_TICK).apply {
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(timeReceiver, t, Context.RECEIVER_EXPORTED) else registerReceiver(timeReceiver, t)
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        if (prefs.darkText) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (prefs.hideStatusBar) flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN
        window.decorView.systemUiVisibility = flags
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!::prefs.isInitialized || !::root.isInitialized) return
        val reopenDrawer = drawerOpen
        drawerOpen = false
        editMode = false
        Looks.clearCache()
        applySystemBars()
        build()
        fillHome()
        builtFor = signature()
        if (reopenDrawer) openDrawer()
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        try { widgetHost.stopListening() } catch (_: Exception) {}
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (prefs.iconPack != loadedPack && loadedPack != "\u0000") { Looks.clearCache(); loadApps() }
        updateBadges()
        if (signature() != builtFor) {
            Looks.clearCache()
            applySystemBars()
            build()
            fillHome()
        }
        clockView?.invalidate(); todayClock?.invalidate()
        updateMinimalClock()
    }

    override fun onPause() {
        if (editMode) setEditMode(false)
        super.onPause()
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(timeReceiver) } catch (_: Exception) {}
        wiggles.forEach { it.cancel() }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            drawerOpen -> closeDrawer()
            folderOverlay != null -> closeFolder()
            editMode -> setEditMode(false)
            else -> pager.toPage(pager.home)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home-Taste: alles schließen, zurück zur ersten Seite
        if (drawerOpen) closeDrawer()
        closeFolder()
        if (editMode) setEditMode(false)
        if (::pager.isInitialized) pager.toPage(pager.home)
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = loadApps()
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_BATTERY_CHANGED) {
                ClockView.battery = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 /
                    i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                ClockView.charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
            clockView?.invalidate(); todayClock?.invalidate()
            updateMinimalClock()
        }
    }

    private fun signature() = prefs.signature(Apps.all.size)

    private fun loadApps() {
        Thread {
            val pack = prefs.iconPack
            Apps.load(this)
            main.post {
                loadedPack = pack
                if (isFinishing || isDestroyed) return@post
                if (!prefs.initialized) {
                    val (fav, dock) = Apps.defaults(this)
                    prefs.favorites = fav
                    prefs.dockApps = dock
                }
                fillHome()
                builtFor = signature()
                refreshDrawer()
            }
        }.start()
    }

    private fun visibleApps() = Apps.all.filter { it.key !in prefs.hidden && it.component.packageName != packageName }

    // ------------------------------------------------------------------
    // Aufbau
    // ------------------------------------------------------------------

    private fun build() {
        root.removeAllViews()
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, insetTop + dpi(8f), 0, insetBottom + dpi(8f))
        }
        if (prefs.dim > 0) root.addView(View(this).apply {
            setBackgroundColor(Color.argb((prefs.dim * 2.55f).toInt().coerceIn(0, 200), 0, 0, 0))
        }, FrameLayout.LayoutParams(-1, -1))
        root.addView(home, FrameLayout.LayoutParams(-1, -1))

        pager = Pager(this, prefs.pageEffect, prefs.motionScale) { updateDots(it) }
        home.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))

        dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        if (!prefs.minimalMode && look == Look.APPLE && prefs.search) {
            home.addView(searchPill(), LinearLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpi(12f); topMargin = dpi(4f)
            })
        } else if (!prefs.minimalMode) {
            home.addView(dots, LinearLayout.LayoutParams(-1, dpi(22f)))
        }

        dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = if (prefs.dockBackground) dockSurface() else null
            val pv = if (background != null) dpi(12f) else dpi(4f)
            setPadding(dpi(8f), pv, dpi(8f), pv)
        }
        if (prefs.dock && !prefs.minimalMode) home.addView(dockRow, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dpi(12f); rightMargin = dpi(12f)
        })
        if (!prefs.minimalMode && prefs.search && look in listOf(Look.ANDROID, Look.COLOROS, Look.LIQUID_GLASS)) {
            home.addView(searchPill(), LinearLayout.LayoutParams(-1, dpi(52f)).apply {
                topMargin = dpi(12f); leftMargin = dpi(16f); rightMargin = dpi(16f)
            })
        }

        buildDrawer()

        // „Fertig“-Knopf für den Bearbeiten-Modus
        doneButton = TextView(this).apply {
            text = "Fertig"
            textSize = 15f
            typeface = Looks.font(this@HomeActivity, look, 600)
            setTextColor(Color.BLACK)
            setPadding(dpi(18f), dpi(8f), dpi(18f), dpi(8f))
            background = GradientDrawable().apply { cornerRadius = dp(20f); setColor(0xF2FFFFFF.toInt()) }
            visibility = View.GONE
            setOnClickListener { setEditMode(false) }
        }
        root.addView(doneButton, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = insetTop + dpi(10f); rightMargin = dpi(16f)
        })
        builtFor = signature()
    }

    private fun glassSurface(radiusDp: Float, strength: Int): GradientDrawable {
        val amount = strength.coerceIn(20, 100) / 100f
        val top = Color.argb((48 + 88 * amount).roundToInt(), 255, 255, 255)
        val middle = Color.argb((28 + 54 * amount).roundToInt(), 210, 224, 255)
        val bottom = Color.argb((18 + 38 * amount).roundToInt(), 255, 255, 255)
        val outline = Color.argb((72 + 108 * amount).roundToInt(), 255, 255, 255)
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(top, middle, bottom)).apply {
            cornerRadius = dp(radiusDp)
            setStroke(dpi(1f), outline)
        }
    }

    private fun dockSurface(): android.graphics.drawable.Drawable? = when (look) {
        Look.APPLE -> GradientDrawable().apply { cornerRadius = dp(34f); setColor(0x40FFFFFF); setStroke(dpi(1f), 0x33FFFFFF) }
        Look.GALAXY -> GradientDrawable().apply { cornerRadius = dp(22f); setColor(0x26FFFFFF) }
        Look.COLOROS -> GradientDrawable().apply { cornerRadius = dp(30f); setColor(0x30FFFFFF) }
        Look.ONEUI -> GradientDrawable().apply { cornerRadius = dp(28f); setColor(0x1AFFFFFF) }
        Look.MAGICOS -> GradientDrawable().apply { cornerRadius = dp(30f); setColor(0x33FFFFFF); setStroke(dpi(1f), 0x22FFFFFF) }
        Look.ORIGINOS -> GradientDrawable().apply { cornerRadius = dp(32f); setColor(0x2BFFFFFF) }
        Look.LIQUID_GLASS -> glassSurface(34f, prefs.glassStrength)
        else -> null
    }

    private fun searchPill(): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val androidLook = look == Look.ANDROID
        val foreground = if (androidLook) 0xFF303136.toInt() else Color.WHITE
        setPadding(dpi(16f), 0, dpi(18f), 0)
        background = if (look == Look.LIQUID_GLASS) glassSurface(28f, prefs.glassStrength) else GradientDrawable().apply {
            cornerRadius = dp(28f)
            setColor(when (look) {
                Look.ANDROID -> 0xFFE9E9ED.toInt()
                Look.APPLE -> 0x40FFFFFF
                else -> 0x33FFFFFF
            })
        }
        contentDescription = "Apps und Web durchsuchen"
        isClickable = true
        isFocusable = true
        setOnClickListener { openDrawer(focusSearch = true) }
        addView(ImageView(this@HomeActivity).apply {
            setImageResource(android.R.drawable.ic_menu_search)
            setColorFilter(foreground)
            contentDescription = null
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dpi(24f), dpi(24f)))
        addView(TextView(this@HomeActivity).apply {
            text = if (look == Look.APPLE) "Suchen" else "Apps & Web durchsuchen"
            typeface = Looks.font(this@HomeActivity, look, 500)
            textSize = if (look == Look.APPLE) 14f else 15f
            setTextColor(foreground)
            gravity = Gravity.CENTER_VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setPadding(dpi(12f), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -1, 1f))
    }

    private fun updateDots(page: Int) {
        if (!::dots.isInitialized) return
        val n = pager.pageCount
        dots.removeAllViews()
        if (n <= 1) return
        for (i in 0 until n) {
            dots.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = if (look == Look.NOTHING) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
                    if (look == Look.ANDROID) cornerRadius = dp(4f)
                    setColor(if (i == page) textColor else (textColor and 0x00FFFFFF) or 0x66000000)
                }
            }, LinearLayout.LayoutParams(dpi(if (i == page && look == Look.ANDROID) 16f else 7f), dpi(7f)).apply {
                leftMargin = dpi(4f); rightMargin = dpi(4f)
            })
        }
    }

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    private fun iconView(app: AppInfo, sizeDp: Int, showLabel: Boolean, inDock: Boolean, onHome: Boolean): FrameLayout {
        val frame = FrameLayout(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpi(6f), 0, dpi(6f))
        }
        frame.addView(col, FrameLayout.LayoutParams(-1, -2))
        val px = dpi(sizeDp.toFloat())
        val iv = ImageView(this).apply {
            setImageBitmap(appIcon(app, px))
        }
        // Icon + Benachrichtigungspunkt
        val iconBox = FrameLayout(this)
        iconBox.addView(iv, FrameLayout.LayoutParams(px, px))
        val dotSize = if (prefs.badgeCount) dpi(18f) else dpi(11f)
        val dot = TextView(this).apply {
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Looks.font(this@HomeActivity, look, 700)
            background = GradientDrawable().apply {
                cornerRadius = dp(9f)
                setColor(if (look == Look.NOTHING) 0xFFD71921.toInt() else if (look == Look.APPLE) 0xFFFF3B30.toInt() else accentColor())
                setStroke(dpi(1.5f), 0x33000000)
            }
            visibility = View.GONE
            minWidth = dotSize
        }
        iconBox.addView(dot, FrameLayout.LayoutParams(-2, dotSize, Gravity.TOP or Gravity.END).apply {
            topMargin = -dotSize / 4; rightMargin = -dotSize / 4
        })
        iconBox.clipChildren = false
        badgeViews[dot] = app.component.packageName
        applyBadge(app.component.packageName, dot)
        col.clipChildren = false
        col.addView(iconBox, LinearLayout.LayoutParams(px, px))
        if (showLabel) col.addView(TextView(this).apply {
            text = prefs.label(app.key, app.label)
            setTextColor(if (inDock) Color.WHITE else if (!onHome) drawerTextColor else textColor)
            textSize = prefs.labelSize.toFloat() - (if (look == Look.NOTHING) 1f else 0f)
            typeface = if (prefs.labelFont >= 0) Looks.namedFont(this@HomeActivity, Looks.labelFonts[prefs.labelFont.coerceIn(0, Looks.labelFonts.size - 1)], 500)
                else Looks.font(this@HomeActivity, look, if (look == Look.ONEUI || look == Look.GALAXY) 500 else 400)
            if (look == Look.NOTHING) letterSpacing = 0.04f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(dp(3f), 0f, dp(1f), if (onHome) shadowColor else 0x88000000.toInt())
            setPadding(dpi(2f), dpi(5f), dpi(2f), 0)
        }, LinearLayout.LayoutParams(-1, -2))

        // „−“-Abzeichen für den Bearbeiten-Modus
        if (onHome) {
            val badge = TextView(this).apply {
                text = "−"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xF2E5E5EA.toInt()) }
                visibility = View.GONE
                tag = "badge"
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    if (app.key in prefs.favorites) prefs.favorites = prefs.favorites - app.key
                    else prefs.hidden = prefs.hidden + app.key
                    fillHome()
                    builtFor = signature()
                    setEditMode(true)
                }
            }
            frame.addView(badge, FrameLayout.LayoutParams(dpi(22f), dpi(22f), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                leftMargin = -px / 2
            })
            homeIcons.add(frame)
        }

        col.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> view.animate().scaleX(0.94f).scaleY(0.94f)
                    .setDuration(animationDuration(100L)).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f)
                    .setDuration(animationDuration(220L))
                    .setInterpolator(android.view.animation.OvershootInterpolator(0.8f)).start()
            }
            false
        }
        frame.contentDescription = prefs.label(app.key, app.label)
        frame.isFocusable = true
        frame.setOnClickListener {
            if (editMode) { setEditMode(false); return@setOnClickListener }
            Apps.launch(this, app, iv)
        }
        frame.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            appMenu(it, app)
            true
        }
        return frame
    }

    private fun appIcon(app: AppInfo, px: Int) =
        Looks.icon(app.key, app.icon, px, prefs.effectiveShape, prefs.iconStyle, prefs.tint, prefs.monoIcons, app.packIcon)

    private fun accentColor(): Int = if (Build.VERSION.SDK_INT >= 31)
        try { getColor(android.R.color.system_accent1_500) } catch (_: Exception) { 0xFF3D7BF7.toInt() } else 0xFF3D7BF7.toInt()

    private fun applyBadge(pkg: String, dot: TextView) {
        val n = if (prefs.badges) BadgeService.counts[pkg] ?: 0 else 0
        dot.visibility = if (n > 0) View.VISIBLE else View.GONE
        dot.text = if (prefs.badgeCount && n > 0) (if (n > 99) "99+" else "$n") else ""
        if (prefs.badgeCount) dot.setPadding(dpi(4f), 0, dpi(4f), 0)
    }

    private fun updateBadges() {
        if (isFinishing) return
        badgeViews.entries.toList().forEach { (v, pkg) -> applyBadge(pkg, v) }
    }

    // ------------------------------------------------------------------
    // Ordner
    // ------------------------------------------------------------------

    private fun folderView(id: String, sizeDp: Int): FrameLayout {
        val frame = FrameLayout(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpi(6f), 0, dpi(6f))
        }
        frame.addView(col, FrameLayout.LayoutParams(-1, -2))
        val px = dpi(sizeDp.toFloat())
        val apps = prefs.folderApps(id).mapNotNull { Apps.byKey(it) }
        val box = object : View(this) {
            private val pp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            override fun onDraw(c: Canvas) {
                val sz = width.toFloat()
                val path = Looks.maskPath(prefs.effectiveShape.let { if (it == Shape.SYSTEM) Shape.ROUNDED else it }, sz)
                pp.color = if (look == Look.ANDROID) 0xE6E8E2F0.toInt() else 0x59FFFFFF
                c.drawPath(path, pp)
                val n = if (apps.size > 4) 3 else 2
                val gap = sz * (if (n == 3) 0.07f else 0.1f)
                val cell = (sz - gap * (n + 1)) / n
                apps.take(n * n).forEachIndexed { i, a ->
                    val b = appIcon(a, cell.toInt().coerceAtLeast(8))
                    val x = gap + (i % n) * (cell + gap)
                    val y = gap + (i / n) * (cell + gap)
                    c.drawBitmap(b, x, y, pp)
                }
            }
        }
        col.addView(box, LinearLayout.LayoutParams(px, px))
        if (prefs.labels) col.addView(TextView(this).apply {
            text = prefs.folderName(id)
            setTextColor(textColor)
            textSize = prefs.labelSize.toFloat()
            typeface = if (prefs.labelFont >= 0) Looks.namedFont(this@HomeActivity, Looks.labelFonts[prefs.labelFont.coerceIn(0, Looks.labelFonts.size - 1)], 500)
                else Looks.font(this@HomeActivity, look, 500)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(dp(3f), 0f, dp(1f), shadowColor)
            setPadding(dpi(2f), dpi(5f), dpi(2f), 0)
        }, LinearLayout.LayoutParams(-1, -2))
        col.setOnClickListener { if (editMode) setEditMode(false) else openFolder(id, box) }
        col.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            val pm = PopupMenu(this, it)
            pm.menu.add(0, 1, 0, "Umbenennen")
            pm.menu.add(0, 2, 1, "Ordner auflösen")
            pm.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> textDialog("Ordner umbenennen", prefs.folderName(id)) { n -> prefs.setFolderName(id, n); rebuildHome() }
                    2 -> {
                        val fav = prefs.favorites.toMutableList()
                        val at = fav.indexOf("folder:$id").coerceAtLeast(0)
                        fav.remove("folder:$id")
                        fav.addAll(at.coerceAtMost(fav.size), prefs.folderApps(id).filter { it !in fav })
                        prefs.favorites = fav
                        prefs.deleteFolder(id)
                        rebuildHome()
                    }
                }
                true
            }
            pm.show()
            true
        }
        return frame
    }

    private fun rebuildHome() {
        fillHome()
        refreshDrawer()
        builtFor = signature()
    }

    private fun textDialog(title: String, value: String, onOk: (String) -> Unit) {
        val et = EditText(this).apply { setText(value); setSelectAllOnFocus(true); setSingleLine() }
        android.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setView(et.also { it.setPadding(dpi(20f), dpi(12f), dpi(20f), dpi(12f)) })
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("OK") { _, _ -> et.text.toString().trim().takeIf { it.isNotEmpty() }?.let(onOk) }
            .show()
    }

    /** App in einen (neuen) Ordner legen */
    private fun addToFolder(app: AppInfo) {
        val ids = prefs.folderIds
        val names = ids.map { prefs.folderName(it) } + "＋ Neuer Ordner"
        android.app.AlertDialog.Builder(this)
            .setTitle("In Ordner legen")
            .setItems(names.toTypedArray()) { _, which ->
                if (which < ids.size) {
                    val id = ids[which]
                    prefs.setFolderApps(id, (prefs.folderApps(id) + app.key).distinct())
                    prefs.favorites = prefs.favorites - app.key
                    rebuildHome()
                } else textDialog("Neuer Ordner", "Ordner") { n ->
                    val id = System.currentTimeMillis().toString(36)
                    prefs.setFolderName(id, n)
                    prefs.setFolderApps(id, listOf(app.key))
                    val fav = prefs.favorites.toMutableList()
                    val at = fav.indexOf(app.key)
                    if (at >= 0) fav[at] = "folder:$id" else fav.add("folder:$id")
                    prefs.favorites = fav
                    rebuildHome()
                }
            }
            .show()
    }

    private fun openFolder(id: String, from: View) {
        closeFolder()
        val apps = prefs.folderApps(id).mapNotNull { Apps.byKey(it) }
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(0x99000000.toInt())
            isClickable = true
            setOnClickListener { closeFolder() }
            alpha = 0f
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(if (look == Look.APPLE) 38f else 30f)
                setColor(when (look) { Look.ANDROID -> 0xF2E9E4F0.toInt(); Look.NOTHING -> 0xF2141414.toInt(); else -> 0x4DFFFFFF })
                if (look != Look.ANDROID) setStroke(dpi(1f), 0x33FFFFFF)
            }
            setPadding(dpi(14f), dpi(18f), dpi(14f), dpi(18f))
        }
        val title = TextView(this).apply {
            text = prefs.folderName(id)
            textSize = 22f
            typeface = Looks.font(this@HomeActivity, look, 700, display = true)
            setTextColor(if (look == Look.ANDROID) 0xFF1B1B1F.toInt() else Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpi(12f))
            setOnClickListener { textDialog("Ordner umbenennen", prefs.folderName(id)) { n -> prefs.setFolderName(id, n); text = n; rebuildHome() } }
        }
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        outer.addView(title)
        val cols = if (apps.size <= 4) 2 else if (apps.size <= 9) 3 else 4
        apps.chunked(cols).forEach { r ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            r.forEach { a ->
                val v = iconView(a, prefs.iconDp, prefs.labels, false, false)
                (v.getChildAt(0) as? LinearLayout)?.let { cv ->
                    // Text im Ordner immer lesbar
                    for (k in 0 until cv.childCount) (cv.getChildAt(k) as? TextView)?.setTextColor(if (look == Look.ANDROID) 0xFF1B1B1F.toInt() else Color.WHITE)
                    cv.setOnClickListener { Apps.launch(this, a, cv); main.postDelayed({ closeFolder() }, 250) }
                    cv.setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        val pm = PopupMenu(this, it)
                        pm.menu.add(0, 1, 0, "Aus Ordner nehmen")
                        Apps.shortcuts(this, a).forEachIndexed { i, sc -> pm.menu.add(1, 100 + i, i, sc.shortLabel ?: "") }
                        val scs = Apps.shortcuts(this, a)
                        pm.setOnMenuItemClickListener { item ->
                            if (item.itemId == 1) {
                                val rest = prefs.folderApps(id) - a.key
                                prefs.setFolderApps(id, rest)
                                prefs.favorites = prefs.favorites + a.key
                                if (rest.isEmpty()) prefs.deleteFolder(id)
                                closeFolder(); rebuildHome()
                            } else scs.getOrNull(item.itemId - 100)?.let { sc -> Apps.startShortcut(this, sc); closeFolder() }
                            true
                        }
                        pm.show()
                        true
                    }
                }
                row.addView(v, LinearLayout.LayoutParams(dpi(prefs.iconDp + 30f), -2))
            }
            outer.addView(row)
        }
        panel.addView(outer)
        scrim.addView(panel, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        folderOverlay = scrim
        // Öffnen aus dem Ordner-Icon heraus
        val loc = IntArray(2); from.getLocationOnScreen(loc)
        panel.scaleX = 0.3f; panel.scaleY = 0.3f
        panel.post {
            val pl = IntArray(2); panel.getLocationOnScreen(pl)
            panel.translationX = (loc[0] + from.width / 2f) - (pl[0] + panel.width / 2f)
            panel.translationY = (loc[1] + from.height / 2f) - (pl[1] + panel.height / 2f)
            panel.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f).setDuration(360).setInterpolator(ease).start()
        }
        scrim.animate().alpha(1f).setDuration(220).start()
    }

    private fun closeFolder() {
        val v = folderOverlay ?: return
        folderOverlay = null
        v.animate().alpha(0f).setDuration(180).withEndAction { root.removeView(v) }.start()
    }

    // ------------------------------------------------------------------
    // Widgets (Heute-Seite)
    // ------------------------------------------------------------------

    private var pendingWidget = -1

    private fun addWidget() {
        val id = try { widgetHost.allocateAppWidgetId() } catch (_: Exception) { return }
        pendingWidget = id
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_PICK)
                .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, id), REQ_PICK)
        } catch (_: Exception) {
            widgetHost.deleteAppWidgetId(id)
            android.widget.Toast.makeText(this, "Widget-Auswahl wird auf diesem Gerät nicht unterstützt", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val id = data?.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidget) ?: pendingWidget
        if (id < 0) return
        if (resultCode != RESULT_OK) {
            try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
            return
        }
        val awm = android.appwidget.AppWidgetManager.getInstance(this)
        val info = awm.getAppWidgetInfo(id)
        if (requestCode == REQ_PICK && info?.configure != null) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
                return
            } catch (_: Exception) {}
        }
        prefs.widgets = prefs.widgets + id
        rebuildHome()
    }

    private fun widgetViews(container: LinearLayout) {
        val awm = android.appwidget.AppWidgetManager.getInstance(this)
        for (id in prefs.widgets) {
            val info = awm.getAppWidgetInfo(id) ?: continue
            val hv = try { widgetHost.createView(applicationContext, id, info) } catch (_: Exception) { null } ?: continue
            val h = maxOf(info.minHeight, dpi(90f))
            container.addView(hv, LinearLayout.LayoutParams(-1, h).apply { bottomMargin = dpi(12f) })
        }
    }

    private fun manageWidgets() {
        val awm = android.appwidget.AppWidgetManager.getInstance(this)
        val ids = prefs.widgets
        val names = ids.map { awm.getAppWidgetInfo(it)?.loadLabel(packageManager) ?: "Widget" }
        if (ids.isEmpty()) { addWidget(); return }
        android.app.AlertDialog.Builder(this)
            .setTitle("Widgets – zum Entfernen antippen")
            .setItems(names.toTypedArray()) { _, w ->
                try { widgetHost.deleteAppWidgetId(ids[w]) } catch (_: Exception) {}
                prefs.widgets = ids - ids[w]
                rebuildHome()
            }
            .setPositiveButton("＋ Widget hinzufügen") { _, _ -> addWidget() }
            .setNegativeButton("Fertig", null)
            .show()
    }

    private fun appMenu(anchor: View, app: AppInfo) {
        val pm = PopupMenu(this, anchor)
        val fav = prefs.favorites
        val dock = prefs.dockApps
        // App-Shortcuts (wie bei Pixel/Nova): ganz oben
        val scs = Apps.shortcuts(this, app)
        scs.forEachIndexed { i, sc -> pm.menu.add(2, 100 + i, i, "↗  " + (sc.shortLabel ?: "")) }
        pm.menu.add(0, 10, 30, "In Ordner legen")
        if (app.key in fav) pm.menu.add(0, 1, 20, "Von der ersten Seite entfernen") else pm.menu.add(0, 2, 20, "Auf die erste Seite")
        if (app.key in dock) pm.menu.add(0, 3, 21, "Aus dem Dock entfernen") else pm.menu.add(0, 4, 21, "Ins Dock")
        pm.menu.add(0, 7, 22, "Startbildschirm bearbeiten")
        pm.menu.add(0, 8, 23, "App ausblenden")
        pm.menu.add(0, 9, 24, "Umbenennen")
        pm.menu.add(0, 5, 24, "App-Info")
        pm.menu.add(0, 6, 25, "Deinstallieren")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> prefs.favorites = fav - app.key
                2 -> prefs.favorites = fav + app.key
                3 -> prefs.dockApps = dock - app.key
                4 -> prefs.dockApps = (dock + app.key).takeLast(prefs.dockCount)
                9 -> renameDialog(app)
                5 -> Apps.appInfo(this, app)
                6 -> Apps.uninstall(this, app)
                7 -> setEditMode(true)
                8 -> prefs.hidden = prefs.hidden + app.key
                10 -> addToFolder(app)
                in 100..110 -> scs.getOrNull(item.itemId - 100)?.let { Apps.startShortcut(this, it) }
            }
            if (item.itemId in listOf(1, 2, 3, 4, 8)) {
                fillHome()
                refreshDrawer()
                builtFor = signature()
            }
            true
        }
        pm.show()
    }

    private fun renameDialog(app: AppInfo) {
        val et = EditText(this).apply {
            setText(prefs.label(app.key, app.label))
            setSelectAllOnFocus(true)
            setSingleLine()
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("App umbenennen")
            .setView(et.also { it.setPadding(dpi(20f), dpi(12f), dpi(20f), dpi(12f)) })
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Original") { _, _ -> prefs.setLabel(app.key, null); fillHome(); refreshDrawer(); builtFor = signature() }
            .setPositiveButton("Speichern") { _, _ ->
                prefs.setLabel(app.key, et.text.toString().trim())
                fillHome(); refreshDrawer(); builtFor = signature()
            }
            .show()
    }

    /** Wie viele Icon-Reihen passen auf eine Seite? */
    private fun rowsPerPage(): Int {
        val hDp = resources.displayMetrics.heightPixels / resources.displayMetrics.density
        val cell = prefs.iconDp + (if (prefs.labels) 30 else 16)
        val reserved = 70 + (if (prefs.dock) prefs.iconDp + 40 else 0) + (if (prefs.search) 60 else 24)
        return ((hDp - reserved) / cell).toInt().coerceIn(3, 7)
    }

    /** Heute-Ansicht: Datum, Akku, meistgenutzte Apps, Suche */
    private fun todayPage(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(18f), dpi(8f), dpi(18f), dpi(16f))
        }
        val now = java.util.Calendar.getInstance()
        val h = now.get(java.util.Calendar.HOUR_OF_DAY)
        val greet = when (h) { in 5..10 -> "Guten Morgen"; in 11..17 -> "Hallo"; in 18..22 -> "Guten Abend"; else -> "Gute Nacht" }
        col.addView(TextView(this).apply {
            text = java.text.SimpleDateFormat("EEEE, d. MMMM", java.util.Locale.GERMAN).format(now.time).uppercase()
            textSize = 13f
            letterSpacing = 0.06f
            setTextColor((textColor and 0x00FFFFFF) or 0xB3000000.toInt())
            typeface = Looks.font(this@HomeActivity, look, 600)
        })
        col.addView(TextView(this).apply {
            text = greet
            textSize = 34f
            setTextColor(textColor)
            typeface = Looks.font(this@HomeActivity, look, 800, display = true)
            setPadding(0, 0, 0, dpi(14f))
        })
        fun glass(): GradientDrawable = GradientDrawable().apply {
            cornerRadius = dp(24f)
            setColor(if (prefs.darkText) 0x59FFFFFF else 0x33FFFFFF)
            setStroke(maxOf(1, dpi(0.8f)), 0x33FFFFFF)
        }
        // Suche
        col.addView(TextView(this).apply {
            text = "⌕   Suchen"
            textSize = 16f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor((textColor and 0x00FFFFFF) or 0xCC000000.toInt())
            background = glass().apply { cornerRadius = dp(16f) }
            setPadding(dpi(12f), 0, dpi(12f), 0)
            setOnClickListener { openDrawer(focusSearch = true) }
        }, LinearLayout.LayoutParams(-1, dpi(46f)).apply { bottomMargin = dpi(12f) })
        // Akku + Uhr nebeneinander
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val wLook = Look.values().getOrNull(prefs.widgetStyle) ?: look
        val tc = ClockView(this, wLook, prefs.darkText)
        todayClock = tc
        row.addView(tc, LinearLayout.LayoutParams(-1, dpi(ClockView.heightDp(wLook))))
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(12f) })
        // Eigene Widgets (echte Android-Widgets)
        widgetViews(col)
        col.addView(TextView(this).apply {
            text = if (prefs.widgets.isEmpty()) "＋  Widget hinzufügen" else "Widgets bearbeiten"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            typeface = Looks.font(this@HomeActivity, look, 600)
            background = glass().apply { cornerRadius = dp(20f) }
            setPadding(0, dpi(10f), 0, dpi(10f))
            setOnClickListener { if (prefs.widgets.isEmpty()) addWidget() else manageWidgets() }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(12f) })
        // Meistgenutzt
        val top = visibleApps().sortedByDescending { prefs.launches(it.key) }.take(8)
        if (top.isNotEmpty()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = glass()
                setPadding(dpi(6f), dpi(12f), dpi(6f), dpi(10f))
            }
            card.addView(TextView(this).apply {
                text = "Meistgenutzt"
                textSize = 14f
                setTextColor(textColor)
                typeface = Looks.font(this@HomeActivity, look, 700)
                setPadding(dpi(12f), 0, 0, dpi(8f))
            })
            top.chunked(4).forEach { r ->
                val rr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                r.forEach { a -> rr.addView(iconView(a, (prefs.iconDp * 0.85f).toInt(), prefs.labels, false, false), LinearLayout.LayoutParams(0, -2, 1f)) }
                repeat(4 - r.size) { rr.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
                card.addView(rr)
            }
            col.addView(card)
        }
        return ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(col)
        }
    }

    /** Raster aus Apps (AppInfo) und Ordnern (String-ID) */
    private fun gridOf(apps: List<Any>, rows: Int): LinearLayout {
        val cols = prefs.columns
        val g = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(12f), 0, dpi(12f), 0)
        }
        apps.chunked(cols).take(rows).forEach { rowApps ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowApps.forEach { a ->
                val v = if (a is AppInfo) iconView(a, prefs.iconDp, prefs.labels, false, true) else folderView(a as String, prefs.iconDp)
                row.addView(v, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(cols - rowApps.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            g.addView(row)
        }
        return g
    }

    private fun fillMinimalHome(favorites: List<Any>) {
        homeIcons.clear()
        clockView = null
        todayClock = null
        pager.home = 0
        val apps: List<Any> = (favorites.ifEmpty { visibleApps().take(12) }).take(12)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(28f), dpi(22f), dpi(28f), dpi(18f))
        }

        minimalClock = TextView(this).apply {
            textSize = 64f
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setTextColor(textColor)
            includeFontPadding = false
            gravity = Gravity.START
        }
        page.addView(minimalClock, LinearLayout.LayoutParams(-1, -2))
        minimalDate = TextView(this).apply {
            textSize = 15f
            typeface = Looks.font(this@HomeActivity, look, 500)
            setTextColor(if (prefs.darkText) 0xAA111114.toInt() else 0xCCFFFFFF.toInt())
            setPadding(dpi(2f), dpi(2f), 0, dpi(24f))
        }
        page.addView(minimalDate, LinearLayout.LayoutParams(-1, -2))
        val separator = if (prefs.darkText) 0x22111114 else 0x33FFFFFF
        apps.forEachIndexed { index, entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                val selectable = android.util.TypedValue()
                theme.resolveAttribute(android.R.attr.selectableItemBackground, selectable, true)
                background = if (selectable.resourceId != 0) getDrawable(selectable.resourceId) else null
                minimumHeight = dpi(54f)
                contentDescription = when (entry) {
                    is AppInfo -> prefs.label(entry.key, entry.label)
                    else -> prefs.folderName(entry as String)
                }
            }
            val title = TextView(this).apply {
                text = when (entry) {
                    is AppInfo -> prefs.label(entry.key, entry.label)
                    else -> prefs.folderName(entry as String)
                }
                textSize = 19f
                typeface = Looks.font(this@HomeActivity, look, 500)
                setTextColor(textColor)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(title, LinearLayout.LayoutParams(0, -1, 1f))
            row.setOnClickListener {
                when (entry) {
                    is AppInfo -> Apps.launch(this, entry, row)
                    else -> openFolder(entry as String, row)
                }
            }
            row.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                if (entry is AppInfo) appMenu(it, entry) else homeMenu(it)
                true
            }
            page.addView(row, LinearLayout.LayoutParams(-1, dpi(54f)))
            if (index < apps.lastIndex) page.addView(View(this).apply { setBackgroundColor(separator) },
                LinearLayout.LayoutParams(-1, dpi(1f)))
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(page, ViewGroup.LayoutParams(-1, -2))
        }
        scroll.setOnLongClickListener {
            homeMenu(it)
            true
        }
        scroll.setOnClickListener { if (editMode) setEditMode(false) }
        scroll.setOnTouchListener { _, e -> doubleTapDetector.onTouchEvent(e); false }
        pager.setPages(listOf(scroll))
        updateDots(0)
        dockRow.removeAllViews()
        updateMinimalClock()
    }

    private fun updateMinimalClock() {
        val now = Date()
        val time = SimpleDateFormat("HH:mm", Locale.GERMANY).format(now)
        val date = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(now)
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.GERMANY) else it.toString() }
        if (minimalClock?.text != time) minimalClock?.text = time
        if (minimalDate?.text != date) minimalDate?.text = date
    }

    private fun fillHome() {
        if (!::pager.isInitialized) return
        homeIcons.clear()
        val pages = ArrayList<View>()
        val rows = rowsPerPage()
        val cols = prefs.columns
        val hidden = prefs.hidden
        val favApps: List<Any> = prefs.favorites.mapNotNull { k ->
            if (k.startsWith("folder:")) k.removePrefix("folder:").takeIf { prefs.folderApps(it).isNotEmpty() }
            else Apps.byKey(k)?.takeIf { it.key !in hidden }
        }
        if (prefs.minimalMode) {
            fillMinimalHome(favApps)
            return
        }
        minimalClock = null
        minimalDate = null

        // Seite 1: Widget + Favoriten
        val first = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val wLook = Look.values().getOrNull(prefs.widgetStyle) ?: look
        clockView = if (prefs.clock) ClockView(this, wLook, prefs.darkText).also {
            first.addView(it, LinearLayout.LayoutParams(-1, dpi(ClockView.heightDp(wLook))).apply {
                leftMargin = dpi(14f); rightMargin = dpi(14f)
            })
        } else null
        val widgetRows = if (prefs.clock) ((ClockView.heightDp(wLook) + 10) / (prefs.iconDp + if (prefs.labels) 30 else 16)).roundToInt() else 0
        val firstRows = (rows - widgetRows).coerceAtLeast(1)
        first.addView(gridOf(favApps, firstRows), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(10f) })
        pages.add(first)

        // weitere Seiten: übrige Favoriten + (optional) alle anderen Apps
        val rest = ArrayList<Any>()
        rest.addAll(favApps.drop(firstRows * cols))
        if (prefs.allOnHome) {
            val shown = prefs.favorites.toSet() + prefs.dockApps.toSet() + prefs.folderIds.flatMap { prefs.folderApps(it) }
            rest.addAll(visibleApps().filter { it.key !in shown })
        }
        rest.chunked(rows * cols).forEach { chunk -> pages.add(gridOf(chunk, rows)) }

        // „Heute“-Seite links (wie iOS): nach rechts wischen auf der ersten Seite
        val hadToday = pager.home == 1
        if (prefs.todayPage) {
            pages.add(0, todayPage())
            pager.home = 1
        } else pager.home = 0
        if (hadToday != (pager.home == 1) || pager.pageCount == 0) pager.toPageSilently(pager.home)

        // Long-Press auf freie Fläche → Menü
        pages.forEach { p ->
            p.setOnLongClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                homeMenu(it)
                true
            }
            p.setOnClickListener { if (editMode) setEditMode(false) }
            p.setOnTouchListener { _, e -> doubleTapDetector.onTouchEvent(e); false }
        }
        pager.setPages(pages)
        updateDots(pager.current)

        dockRow.removeAllViews()
        prefs.dockApps.mapNotNull { Apps.byKey(it) }.filter { it.key !in hidden }.take(prefs.dockCount).forEach { a ->
            dockRow.addView(iconView(a, prefs.dockIconDp, prefs.dockLabels, true, false), LinearLayout.LayoutParams(0, -2, 1f))
        }
        if (editMode) setEditMode(true)
    }

    private fun homeMenu(anchor: View) {
        val pm = PopupMenu(this, anchor, Gravity.CENTER)
        pm.menu.add(0, 1, 0, "Startbildschirm bearbeiten")
        pm.menu.add(0, 2, 1, "Hintergrundbild")
        pm.menu.add(0, 3, 2, "Lumora Home Einstellungen")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> setEditMode(true)
                2 -> try { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild")) } catch (_: Exception) {}
                3 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        pm.show()
    }

    /** Wackel-Modus wie beim iPhone: Icons wackeln, „−“ entfernt sie. */
    private fun setEditMode(on: Boolean) {
        editMode = on
        wiggles.forEach { it.cancel() }
        wiggles.clear()
        doneButton.visibility = if (on) View.VISIBLE else View.GONE
        homeIcons.forEachIndexed { i, f ->
            f.findViewWithTag<View>("badge")?.visibility = if (on) View.VISIBLE else View.GONE
            if (on) {
                val a = ObjectAnimator.ofFloat(f, View.ROTATION, -1.8f, 1.8f).apply {
                    duration = 130L + (i % 3) * 12L
                    repeatCount = ValueAnimator.INFINITE
                    repeatMode = ValueAnimator.REVERSE
                    startDelay = (i % 5) * 20L
                    start()
                }
                wiggles.add(a)
            } else {
                f.rotation = 0f
            }
        }
    }

    // ------------------------------------------------------------------
    // App-Übersicht
    // ------------------------------------------------------------------

    private fun drawerColor(): Int {
        val base = when (look) {
            Look.APPLE -> 0xFF101014.toInt()
            Look.ANDROID -> if (Build.VERSION.SDK_INT >= 31) try {
                getColor(if (systemDark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50)
            } catch (_: Exception) { if (systemDark) 0xFF1B1B1F.toInt() else 0xFFF7F7FA.toInt() }
            else if (systemDark) 0xFF1B1B1F.toInt() else 0xFFF7F7FA.toInt()
            Look.ONEUI, Look.GALAXY, Look.NOTHING -> 0xFF000000.toInt()
            Look.COLOROS -> 0xFF1A1A1E.toInt()
            Look.HYPEROS -> 0xFF121214.toInt()
            Look.MAGICOS -> 0xFF15131C.toInt()
            Look.ORIGINOS -> 0xFF101820.toInt()
            Look.LIQUID_GLASS -> 0xFF172034.toInt()
        }
        val opacity = prefs.drawerOpacity.coerceIn(0, 100) / 100f
        val glassAlpha = if (look == Look.LIQUID_GLASS) 96 + prefs.glassStrength * 1.15f else 255f
        val a = (glassAlpha * opacity).roundToInt().coerceIn(0, 255)
        return (base and 0x00FFFFFF) or (a shl 24)
    }

    private fun buildDrawer() {
        drawer = FrameLayout(this).apply {
            setBackgroundColor(drawerColor())
            visibility = View.GONE
            isClickable = true
            setPadding(0, insetTop, 0, insetBottom)
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        drawer.addView(col, FrameLayout.LayoutParams(-1, -1))
        col.addView(TextView(this).apply {
            text = when (look) { Look.APPLE -> "App-Mediathek"; Look.NOTHING -> "APPS"; else -> "Apps" }
            textSize = if (look == Look.NOTHING) 30f else 28f
            typeface = Looks.font(this@HomeActivity, look, 700, display = true)
            setTextColor(drawerTextColor)
            setPadding(dpi(22f), dpi(18f), dpi(22f), dpi(8f))
        })
        drawerSearch = EditText(this).apply {
            hint = if (look == Look.APPLE) "Suchen" else "Apps & Web durchsuchen"
            setSingleLine()
            textSize = 16f
            setTextColor(drawerTextColor)
            setHintTextColor(if (androidLightDrawer) 0x991B1B1F.toInt() else 0x99FFFFFF.toInt())
            typeface = Looks.font(this@HomeActivity, look, 400)
            setPadding(dpi(18f), dpi(10f), dpi(18f), dpi(10f))
            background = if (look == Look.LIQUID_GLASS) glassSurface(24f, prefs.glassStrength) else GradientDrawable().apply { cornerRadius = dp(if (look == Look.APPLE) 12f else 26f); setColor(if (androidLightDrawer) 0xFFE7E6EB.toInt() else 0xFF2C2C30.toInt()) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = refreshDrawer()
            })
            setOnEditorActionListener { _, _, _ ->
                val first = (drawerGrid.adapter as? DrawerAdapter)?.first()
                if (first != null) Apps.launch(this@HomeActivity, first, null) else webSearch(text.toString())
                true
            }
        }
        col.addView(drawerSearch, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dpi(18f); rightMargin = dpi(18f); bottomMargin = dpi(8f)
        })
        // Websuche
        webRow = TextView(this).apply {
            textSize = 15f
            setTextColor(drawerTextColor)
            typeface = Looks.font(this@HomeActivity, look, 500)
            setPadding(dpi(18f), dpi(12f), dpi(18f), dpi(12f))
            background = GradientDrawable().apply { cornerRadius = dp(14f); setColor(if (androidLightDrawer) 0xFFE7E6EB.toInt() else 0xFF2C2C30.toInt()) }
            visibility = View.GONE
            setOnClickListener { webSearch(drawerSearch.text.toString()) }
        }
        col.addView(webRow, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dpi(18f); rightMargin = dpi(18f); bottomMargin = dpi(8f)
        })
        // Vorschläge (meistgenutzt)
        col.addView(TextView(this).apply {
            text = if (look == Look.NOTHING) "VORSCHLÄGE" else "Vorschläge"
            textSize = 13f
            setTextColor(0x99FFFFFF.toInt())
            typeface = Looks.font(this@HomeActivity, look, 600)
            setPadding(dpi(22f), dpi(4f), 0, 0)
            tag = "suggestTitle"
            visibility = View.GONE
        })
        suggestRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dpi(8f), 0, dpi(8f), dpi(6f)) }
        col.addView(suggestRow)
        drawerGrid = GridView(this).apply {
            numColumns = if (prefs.drawerList) 1 else prefs.drawerColumns
            verticalSpacing = dpi(4f)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(dpi(8f), 0, dpi(8f), dpi(20f))
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            isFastScrollEnabled = true
            isFastScrollAlwaysVisible = prefs.drawerSort == 0 && !prefs.drawerList
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            adapter = DrawerAdapter()
        }
        col.addView(drawerGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
    }

    private fun refreshDrawer() {
        if (!::drawerGrid.isInitialized) return
        val q = drawerSearch.text?.toString().orEmpty()
        (drawerGrid.adapter as? DrawerAdapter)?.filter(q)
        webRow.visibility = if (q.isBlank()) View.GONE else View.VISIBLE
        webRow.text = "⌕   „$q“ im Web suchen"
        // Vorschläge nur ohne Suchbegriff
        suggestRow.removeAllViews()
        val title = (suggestRow.parent as ViewGroup).findViewWithTag<View>("suggestTitle")
        val top = visibleApps().filter { prefs.launches(it.key) > 0 }.sortedByDescending { prefs.launches(it.key) }.take(prefs.drawerColumns)
        val show = q.isBlank() && top.isNotEmpty()
        title?.visibility = if (show) View.VISIBLE else View.GONE
        suggestRow.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            top.forEach { a ->
                suggestRow.addView(iconView(a, (prefs.drawerIconDp * 0.9f).toInt(), true, false, false).apply {
                    setOnClickListener { Apps.launch(this@HomeActivity, a, this); main.postDelayed({ closeDrawer(false) }, 300) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            repeat(prefs.drawerColumns - top.size) { suggestRow.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
        }
    }

    private fun webSearch(q: String) {
        if (q.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, q))
        } catch (_: Exception) {
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://duckduckgo.com/?q=" + Uri.encode(q)))) } catch (_: Exception) {}
        }
        main.postDelayed({ closeDrawer(false) }, 300)
    }

    private inner class DrawerAdapter : BaseAdapter(), android.widget.SectionIndexer {
        private var items: List<AppInfo> = sorted(visibleApps())
        private var sections: Array<String> = emptyArray()
        private var sectionPos: IntArray = IntArray(0)

        private fun sorted(all: List<AppInfo>): List<AppInfo> = when (prefs.drawerSort) {
            1 -> all.sortedWith(compareByDescending<AppInfo> { prefs.launches(it.key) }.thenBy { prefs.label(it.key, it.label).lowercase() })
            2 -> all.sortedByDescending { it.installed }
            else -> all.sortedBy { prefs.label(it.key, it.label).lowercase() }
        }

        /** Tolerante Suche: Anfang, enthält, oder Anfangsbuchstaben („wa“ → WhatsApp, „gm“ → Google Maps) */
        private fun score(name: String, q: String): Int {
            val n = name.lowercase()
            if (n.startsWith(q)) return 0
            if (n.split(' ', '-', '.').any { it.startsWith(q) }) return 1
            if (n.contains(q)) return 2
            val initials = n.split(' ', '-', '.').filter { it.isNotEmpty() }.joinToString("") { it.take(1) }
            if (initials.startsWith(q)) return 3
            return -1
        }

        fun filter(q: String) {
            val qq = q.trim().lowercase()
            val all = visibleApps()
            items = if (qq.isEmpty()) sorted(all) else all.map { it to score(prefs.label(it.key, it.label), qq) }
                .filter { it.second >= 0 }.sortedBy { it.second }.map { it.first }
            // Buchstaben-Abschnitte für den Schnell-Scroller (nur A–Z)
            if (qq.isEmpty() && prefs.drawerSort == 0) {
                val sec = ArrayList<String>(); val pos = ArrayList<Int>()
                items.forEachIndexed { i, a ->
                    val l = prefs.label(a.key, a.label).firstOrNull()?.uppercaseChar()?.let { if (it.isLetter()) it.toString() else "#" } ?: "#"
                    if (sec.lastOrNull() != l) { sec.add(l); pos.add(i) }
                }
                sections = sec.toTypedArray(); sectionPos = pos.toIntArray()
            } else { sections = emptyArray(); sectionPos = IntArray(0) }
            notifyDataSetChanged()
        }

        override fun getSections(): Array<Any> = sections.map { it as Any }.toTypedArray()
        override fun getPositionForSection(sectionIndex: Int): Int =
            sectionPos.getOrElse(sectionIndex.coerceIn(0, maxOf(0, sectionPos.size - 1))) { 0 }
        override fun getSectionForPosition(position: Int): Int {
            var r = 0
            for (i in sectionPos.indices) if (sectionPos[i] <= position) r = i
            return r
        }

        fun first() = items.firstOrNull()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val a = items[position]
            if (prefs.drawerList) {
                // Listen-Ansicht: Icon + Name nebeneinander
                return LinearLayout(this@HomeActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dpi(18f), dpi(8f), dpi(18f), dpi(8f))
                    val px = dpi(prefs.drawerIconDp.coerceIn(36, 64).toFloat())
                    addView(ImageView(this@HomeActivity).apply {
                        setImageBitmap(appIcon(a, px))
                    }, LinearLayout.LayoutParams(px, px))
                    addView(TextView(this@HomeActivity).apply {
                        text = prefs.label(a.key, a.label)
                        textSize = 16f
                        setTextColor(drawerTextColor)
                        typeface = Looks.font(this@HomeActivity, look, 500)
                        setPadding(dpi(16f), 0, 0, 0)
                    })
                    setOnClickListener {
                        Apps.launch(this@HomeActivity, a, this)
                        main.postDelayed({ closeDrawer(animate = false) }, 300)
                    }
                    setOnLongClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        appMenu(it, a)
                        true
                    }
                }
            }
            return iconView(a, (prefs.drawerIconDp * 0.92f).toInt(), true, false, false).apply {
                setOnClickListener {
                    Apps.launch(this@HomeActivity, a, this)
                    main.postDelayed({ closeDrawer(animate = false) }, 300)
                }
            }
        }
    }

    private fun openDrawer(focusSearch: Boolean = false) {
        if (drawerOpen) return
        if (editMode) setEditMode(false)
        drawerOpen = true
        drawer.visibility = View.VISIBLE
        drawerSearch.setText("")
        refreshDrawer()
        when (look) {
            Look.APPLE, Look.LIQUID_GLASS -> { drawer.alpha = 0f; drawer.scaleX = 0.94f; drawer.scaleY = 0.94f; drawer.translationY = 0f }
            Look.NOTHING -> { drawer.alpha = 0f; drawer.translationY = 0f }
            else -> { drawer.alpha = 0f; drawer.translationY = dp(80f) }
        }
        val opening = drawer.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
            .setDuration(animationDuration(if (look == Look.LIQUID_GLASS) 420L else 320L))
        opening.setInterpolator(if (look == Look.LIQUID_GLASS) android.view.animation.OvershootInterpolator(0.65f) else ease).start()
        home.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(animationDuration(260L)).start()
        if (focusSearch) {
            drawerSearch.requestFocus()
            main.postDelayed({
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(drawerSearch, 0)
            }, 200)
        }
    }

    private fun closeDrawer(animate: Boolean = true) {
        if (!drawerOpen) return
        drawerOpen = false
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(drawerSearch.windowToken, 0)
        drawerSearch.clearFocus()
        home.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(if (animate) animationDuration(300L) else 0L).start()
        if (!animate) {
            drawer.visibility = View.GONE
            return
        }
        drawer.animate().alpha(0f).translationY(if (look == Look.APPLE || look == Look.NOTHING || look == Look.LIQUID_GLASS) 0f else dp(60f))
            .scaleX(if (look == Look.APPLE || look == Look.LIQUID_GLASS) 0.95f else 1f)
            .scaleY(if (look == Look.APPLE || look == Look.LIQUID_GLASS) 0.95f else 1f)
            .setDuration(animationDuration(240L)).withEndAction { drawer.visibility = View.GONE }.start()
    }

    // ------------------------------------------------------------------
    // Gesten: hoch = Apps, runter = Suche oder Benachrichtigungen
    // ------------------------------------------------------------------

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val dy = e2.y - (e1?.y ?: e2.y)
            if (abs(vy) < abs(vx) * 1.2f || abs(dy) < dp(60f) || editMode) return false
            if (!drawerOpen && vy < 0) {
                when (prefs.swipeUpAction) {
                    1 -> openDrawer(focusSearch = true)
                    2 -> expandNotifications()
                    3 -> startActivity(Intent(this@HomeActivity, SettingsActivity::class.java))
                    4 -> openDrawer()
                    else -> return false
                }
                return true
            }
            if (!drawerOpen && vy > 0) {
                if (prefs.swipeDownSearch) openDrawer(focusSearch = true) else expandNotifications()
                return true
            }
            if (drawerOpen && vy > 0 && !drawerGrid.canScrollVertically(-1)) { closeDrawer(); return true }
            return false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (::gestures.isInitialized) gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        private const val REQ_PICK = 41
        private const val REQ_CONFIG = 42
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandNotifications() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Exception) {}
    }
}

/** Seitenweise blättern (mit Einrasten). */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class Pager(ctx: Context, private val effect: Int, private val motionScale: Int, private val onPage: (Int) -> Unit) : HorizontalScrollView(ctx) {
    private val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    private var pageAnimator: ValueAnimator? = null
    private var touchVelocity: VelocityTracker? = null
    var current = 0
        private set
    /** Index der eigentlichen Startseite (1, wenn links die „Heute“-Seite liegt) */
    var home = 0
    val pageCount get() = row.childCount

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
        addView(row, LayoutParams(-2, -1))
    }

    private fun pageWidth() = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels

    fun setPages(pages: List<View>) {
        row.removeAllViews()
        val w = pageWidth()
        pages.forEach { row.addView(it, LinearLayout.LayoutParams(w, -1)) }
        current = current.coerceIn(0, max(0, pages.size - 1))
        post { scrollTo(current * pageWidth(), 0); onPage(current) }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            c.layoutParams = (c.layoutParams as LinearLayout.LayoutParams).apply { width = w }
        }
        post { scrollTo(current * w, 0) }
    }

    fun toPageSilently(i: Int) {
        current = i.coerceAtLeast(0)
    }

    fun toPage(i: Int) {
        current = i.coerceIn(0, max(0, pageCount - 1))
        animatePageScrollTo(current * pageWidth(), 0)
        onPage(current)
    }

    private fun animatePageScrollTo(x: Int, y: Int) {
        val target = x.coerceIn(0, max(0, (pageCount - 1) * pageWidth()))
        pageAnimator?.cancel()
        if (motionScale == 0 || target == scrollX) {
            scrollTo(target, 0)
            return
        }
        pageAnimator = ValueAnimator.ofInt(scrollX, target).apply {
            val distance = kotlin.math.abs(target - scrollX).toFloat() / pageWidth().coerceAtLeast(1)
            duration = (220L + 110L * distance.coerceIn(0f, 1f)).times(motionScale.coerceIn(50, 150) / 100f).toLong().coerceAtLeast(120L)
            interpolator = PathInterpolator(0.22f, 0.61f, 0.36f, 1f)
            addUpdateListener { scrollTo(it.animatedValue as Int, 0) }
            start()
        }
    }

    /** Eigenes Einrasten statt freiem Schwung */
    override fun fling(velocityX: Int) {}

    /** Blätter-Effekte: 0 Schieben · 1 Zoom · 2 Würfel · 3 Stapel */
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (effect == 0) return
        val w = pageWidth().toFloat()
        for (i in 0 until row.childCount) {
            val c = row.getChildAt(i)
            val f = ((c.left - l) / w).coerceIn(-1f, 1f) // -1 = links weg, 0 = sichtbar, 1 = rechts
            val af = kotlin.math.abs(f)
            when (effect) {
                1 -> {
                    val sc = 1f - 0.18f * af
                    c.scaleX = sc; c.scaleY = sc
                    c.alpha = 1f - 0.7f * af
                }
                2 -> {
                    c.cameraDistance = w * 8
                    c.pivotX = if (f < 0) w else 0f
                    c.pivotY = c.height / 2f
                    c.rotationY = 70f * f
                }
                3 -> {
                    if (f < 0) {
                        c.translationX = -f * w * 0.85f
                        val sc = 1f - 0.12f * af
                        c.scaleX = sc; c.scaleY = sc
                        c.alpha = 1f - 0.6f * af
                    } else {
                        c.translationX = 0f; c.scaleX = 1f; c.scaleY = 1f; c.alpha = 1f
                    }
                }
            }
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pageAnimator?.cancel()
                touchVelocity?.recycle()
                touchVelocity = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                touchVelocity?.addMovement(ev)
        }

        val result = super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val w = pageWidth()
                val tracker = touchVelocity
                tracker?.computeCurrentVelocity(1000)
                val velocity = tracker?.xVelocity ?: 0f
                tracker?.recycle()
                touchVelocity = null

                val moved = scrollX - current * w
                val minFling = 700f * resources.displayMetrics.density
                val target = when {
                    velocity < -minFling -> current + 1
                    velocity > minFling -> current - 1
                    moved > w * 0.18f -> current + 1
                    moved < -w * 0.18f -> current - 1
                    else -> current
                }
                if (target != current) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                toPage(target)
            }
            MotionEvent.ACTION_CANCEL -> {
                touchVelocity?.recycle()
                touchVelocity = null
                toPage(current)
            }
        }
        return result
    }
}

/** Uhr-Widgets im Stil des jeweiligen Looks. */
@SuppressLint("ViewConstructor")
class ClockView(ctx: Context, private val look: Look, private val darkText: Boolean) : View(ctx) {
    companion object {
        var battery = -1
        var charging = false
        fun heightDp(look: Look) = when (look) {
            Look.APPLE -> 170f
            Look.ANDROID -> 96f
            Look.ONEUI -> 160f
            Look.GALAXY -> 200f
            Look.NOTHING -> 156f
            Look.COLOROS -> 146f
            Look.HYPEROS -> 140f
            Look.MAGICOS -> 152f
            Look.ORIGINOS -> 150f
            Look.LIQUID_GLASS -> 148f
        }
    }

    private val d = resources.displayMetrics.density
    private val glassStrength = HomePrefs(ctx).glassStrength
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var cv: Canvas? = null
    private val fg get() = if (darkText) 0xFF111114.toInt() else Color.WHITE

    private fun text(t: String, x: Float, y: Float, size: Float, weight: Int, color: Int = fg,
                     align: Paint.Align = Paint.Align.LEFT, display: Boolean = false, shadow: Boolean = true) {
        p.textSize = size * d
        p.typeface = Looks.font(context, look, weight, display)
        p.color = color
        p.textAlign = align
        if (shadow && !darkText) p.setShadowLayer(4 * d, 0f, d, 0x66000000) else p.clearShadowLayer()
        cv?.drawText(t, x, y, p)
    }

    /**
     * Akku-Symbol im Stil des jeweiligen Systems. x = linke Kante, cy = Mitte, h = Höhe.
     * Gibt die Breite zurück (inkl. Prozentzahl).
     */
    private fun batteryGlyph(c: Canvas, x: Float, cy: Float, h: Float, withText: Boolean = true): Float {
        if (battery < 0) return 0f
        val lv = battery.coerceIn(0, 100) / 100f
        val low = battery <= 20 && !charging
        val fgc = fg
        val dim = (fg and 0x00FFFFFF) or 0x55000000
        val accent = when {
            low -> 0xFFFF453A.toInt()
            charging && look != Look.NOTHING -> 0xFF30D158.toInt()
            else -> fgc
        }
        p.clearShadowLayer()
        p.style = Paint.Style.FILL
        val w: Float
        when (look) {
            Look.ANDROID -> {
                // Pixel: gefüllte Kapsel
                w = h * 2.0f
                p.color = dim; r.set(x, cy - h / 2, x + w, cy + h / 2); c.drawRoundRect(r, h * 0.45f, h * 0.45f, p)
                c.save(); val pt = android.graphics.Path(); pt.addRoundRect(r, h * 0.45f, h * 0.45f, android.graphics.Path.Direction.CW); c.clipPath(pt)
                p.color = accent; c.drawRect(x, cy - h / 2, x + w * lv.coerceAtLeast(0.08f), cy + h / 2, p); c.restore()
            }
            Look.HYPEROS -> {
                // Xiaomi: gefüllte Pille mit Zahl darin
                w = h * 2.4f
                p.color = dim; r.set(x, cy - h / 2, x + w, cy + h / 2); c.drawRoundRect(r, h * 0.3f, h * 0.3f, p)
                p.color = accent; r.set(x, cy - h / 2, x + w * lv.coerceAtLeast(0.1f), cy + h / 2); c.drawRoundRect(r, h * 0.3f, h * 0.3f, p)
                p.textSize = h * 0.72f; p.textAlign = Paint.Align.CENTER
                p.typeface = Looks.font(context, look, 700)
                p.color = if (darkText) Color.WHITE else 0xFF111114.toInt()
                c.drawText("$battery", x + w / 2, cy + h * 0.26f, p)
                p.textAlign = Paint.Align.LEFT
                return w
            }
            Look.NOTHING -> {
                // Nothing: Punktraster
                val cols = 8; val cell = h / 2.4f; val rad = cell * 0.36f
                w = cols * cell
                val lit = (lv * cols + 0.5f).toInt().coerceIn(1, cols)
                for (i in 0 until cols) for (j in 0 until 2) {
                    p.color = if (i < lit) (if (low) accent else fgc) else dim
                    c.drawCircle(x + (i + 0.5f) * cell, cy + (j - 0.5f) * cell, rad, p)
                }
            }
            Look.APPLE -> {
                w = h * 2.1f
                p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.1f; p.color = dim
                r.set(x, cy - h / 2, x + w * 0.9f, cy + h / 2); c.drawRoundRect(r, h * 0.32f, h * 0.32f, p)
                p.style = Paint.Style.FILL
                r.set(x + w * 0.92f, cy - h * 0.18f, x + w, cy + h * 0.18f); c.drawRoundRect(r, h * 0.1f, h * 0.1f, p)
                val ins = h * 0.17f
                p.color = accent; r.set(x + ins, cy - h / 2 + ins, x + ins + (w * 0.9f - 2 * ins) * lv.coerceAtLeast(0.06f), cy + h / 2 - ins)
                c.drawRoundRect(r, h * 0.16f, h * 0.16f, p)
            }
            else -> {
                // One UI / ColorOS / MagicOS / OriginOS: dünne Kontur, kantiger
                w = h * 2.0f
                p.style = Paint.Style.STROKE; p.strokeWidth = h * 0.08f; p.color = fgc
                r.set(x, cy - h / 2, x + w * 0.92f, cy + h / 2); c.drawRoundRect(r, h * 0.18f, h * 0.18f, p)
                p.style = Paint.Style.FILL
                c.drawRect(x + w * 0.94f, cy - h * 0.16f, x + w, cy + h * 0.16f, p)
                val ins = h * 0.2f
                p.color = accent; c.drawRect(x + ins, cy - h / 2 + ins, x + ins + (w * 0.92f - 2 * ins) * lv.coerceAtLeast(0.06f), cy + h / 2 - ins, p)
            }
        }
        if (!withText) return w
        p.textSize = h * 1.15f
        p.typeface = Looks.font(context, look, 600)
        p.color = fgc
        p.textAlign = Paint.Align.LEFT
        val t = (if (charging) "⚡" else "") + "$battery %"
        c.drawText(t, x + w + h * 0.4f, cy + h * 0.4f, p)
        return w + h * 0.4f + p.measureText(t)
    }

    override fun onDraw(c: Canvas) {
        cv = c
        if (battery < 0) {
            val i = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (i != null) {
                battery = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            }
        }
        val now = Date()
        val de = Locale.GERMANY
        val w = width.toFloat()
        val time = SimpleDateFormat("HH:mm", de).format(now)
        when (look) {
            Look.APPLE -> {
                // Zwei kleine iOS-Widgets: Kalender + Batterien
                val gap = 14 * d
                val s = minOf((w - gap) / 2, height - 12 * d)
                val left = (w - (2 * s + gap)) / 2
                r.set(left, 6 * d, left + s, 6 * d + s)
                p.clearShadowLayer()
                p.color = 0xF5FFFFFF.toInt()
                c.drawRoundRect(r, 22 * d, 22 * d, p)
                text(SimpleDateFormat("EEEE", de).format(now).uppercase(), r.left + 16 * d, r.top + 28 * d, 12f, 700, 0xFFFF3B30.toInt(), shadow = false)
                text(SimpleDateFormat("d", de).format(now), r.left + 14 * d, r.top + 82 * d, 50f, 400, 0xFF111114.toInt(), shadow = false)
                text("Keine Termine", r.left + 16 * d, r.bottom - 18 * d, 12f, 500, 0xFF8E8E93.toInt(), shadow = false)
                r.set(left + s + gap, 6 * d, left + 2 * s + gap, 6 * d + s)
                p.color = 0x40FFFFFF
                c.drawRoundRect(r, 22 * d, 22 * d, p)
                val cx = r.centerX()
                val cy = r.centerY() - 8 * d
                val rr = s * 0.28f
                p.style = Paint.Style.STROKE
                p.strokeWidth = 6 * d
                p.strokeCap = Paint.Cap.ROUND
                p.color = 0x33FFFFFF
                c.drawCircle(cx, cy, rr, p)
                p.color = if (battery in 0..20) 0xFFFF453A.toInt() else 0xFF30D158.toInt()
                val lvl = battery.coerceIn(0, 100)
                c.drawArc(cx - rr, cy - rr, cx + rr, cy + rr, -90f, 360f * lvl / 100f, false, p)
                p.style = Paint.Style.FILL
                text(if (charging) "⚡" else "▯", cx, cy + 7 * d, 18f, 600, Color.WHITE, align = Paint.Align.CENTER)
                text("$lvl %", cx, r.bottom - 16 * d, 15f, 600, Color.WHITE, align = Paint.Align.CENTER)
            }
            Look.ANDROID -> {
                text(SimpleDateFormat("EEE., d. MMM.", de).format(now), 10 * d, 44 * d, 24f, 400)
                text(time, 10 * d, 78 * d, 16f, 500)
                p.textSize = 16 * d
                batteryGlyph(c, 10 * d + p.measureText(time) + 14 * d, 72 * d, 11 * d)
            }
            Look.ONEUI -> {
                text(time, w / 2, 96 * d, 76f, 250, align = Paint.Align.CENTER)
                text(SimpleDateFormat("EEE., d. MMMM", de).format(now), w / 2, 128 * d, 16f, 500, align = Paint.Align.CENTER)
                batteryGlyph(c, w / 2 - 38 * d, 146 * d, 10 * d)
            }
            Look.GALAXY -> {
                text(SimpleDateFormat("HH", de).format(now), 12 * d, 86 * d, 84f, 800)
                text(SimpleDateFormat("mm", de).format(now), 12 * d, 166 * d, 84f, 300)
                text(SimpleDateFormat("EEEE d. MMM", de).format(now), w - 14 * d, 160 * d, 15f, 500, align = Paint.Align.RIGHT)
            }
            Look.LIQUID_GLASS -> {
                val strength = glassStrength
                val alpha = (36 + strength * 0.42f).roundToInt().coerceIn(0, 100)
                r.set(12 * d, 8 * d, w - 12 * d, 142 * d)
                p.clearShadowLayer()
                p.color = Color.argb(alpha, 255, 255, 255)
                c.drawRoundRect(r, 28 * d, 28 * d, p)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.2f * d
                p.color = Color.argb((80 + strength * 1.2f).roundToInt().coerceIn(0, 220), 255, 255, 255)
                c.drawRoundRect(r, 28 * d, 28 * d, p)
                p.style = Paint.Style.FILL
                text(time, w / 2, 82 * d, 60f, 500, align = Paint.Align.CENTER)
                text(SimpleDateFormat("EEEE, d. MMMM", de).format(now), w / 2, 108 * d, 15f, 500, align = Paint.Align.CENTER)
                batteryGlyph(c, w / 2 - 34 * d, 126 * d, 8 * d)
            }
            Look.NOTHING -> {
                text(time, w / 2, 96 * d, 84f, 700, align = Paint.Align.CENTER, display = true)
                p.clearShadowLayer()
                p.color = 0xFFD71921.toInt()
                c.drawCircle(w / 2 + 128 * d, 34 * d, 5 * d, p)
                text(SimpleDateFormat("EEE d MMM", de).format(now).uppercase(), w / 2, 130 * d, 14f, 500, align = Paint.Align.CENTER)
                batteryGlyph(c, w / 2 - 44 * d, 144 * d, 9 * d)
            }
            Look.COLOROS -> {
                text(time, 10 * d, 84 * d, 72f, 200)
                text(SimpleDateFormat("EEEE, d. MMMM", de).format(now), 12 * d, 116 * d, 16f, 400)
                batteryGlyph(c, 12 * d, 132 * d, 10 * d)
            }
            Look.HYPEROS -> {
                // Xiaomi: kräftige Uhr links, Datum + Akku klein darunter
                text(time, 8 * d, 86 * d, 78f, 800)
                val ds = SimpleDateFormat("EEEE, d. MMMM", de).format(now)
                text(ds, 12 * d, 120 * d, 15f, 500)
                p.textSize = 15 * d
                batteryGlyph(c, 12 * d + p.measureText(ds) + 12 * d, 115 * d, 13 * d)
            }
            Look.MAGICOS -> {
                // Honor: zentrierte, leichte Uhr mit Datum in Pille
                text(time, w / 2, 86 * d, 70f, 300, align = Paint.Align.CENTER)
                val dt = SimpleDateFormat("EEE, d. MMM", de).format(now)
                p.textSize = 14 * d
                val tw = p.measureText(dt) + 28 * d
                r.set(w / 2 - tw / 2, 102 * d, w / 2 + tw / 2, 128 * d)
                p.clearShadowLayer()
                p.color = 0x33FFFFFF
                c.drawRoundRect(r, 13 * d, 13 * d, p)
                text(dt, w / 2, 120 * d, 14f, 500, align = Paint.Align.CENTER, shadow = false)
                batteryGlyph(c, w / 2 - 36 * d, 140 * d, 9 * d)
            }
            Look.ORIGINOS -> {
                // vivo: große, dünne Uhr links, Datum fett rechts daneben
                text(SimpleDateFormat("HH", de).format(now), 10 * d, 92 * d, 92f, 200)
                text(SimpleDateFormat("mm", de).format(now), w * 0.42f, 92 * d, 92f, 200)
                text(SimpleDateFormat("EEEE", de).format(now), 12 * d, 128 * d, 16f, 700)
                text(SimpleDateFormat("d. MMMM", de).format(now), 12 * d, 148 * d, 14f, 400)
                p.textSize = 14 * d
                batteryGlyph(c, 12 * d + p.measureText(SimpleDateFormat("d. MMMM", de).format(now)) + 14 * d, 143 * d, 10 * d)
            }
        }
        cv = null
    }
}
