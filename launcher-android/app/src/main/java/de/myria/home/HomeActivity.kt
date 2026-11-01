package de.myria.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
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
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.animation.PathInterpolator
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class HomeActivity : Activity() {

    private lateinit var prefs: HomePrefs
    private lateinit var root: FrameLayout
    private lateinit var home: LinearLayout
    private lateinit var drawer: FrameLayout
    private lateinit var drawerSearch: EditText
    private lateinit var drawerGrid: GridView
    private var clockView: ClockView? = null
    private val main = Handler(Looper.getMainLooper())
    private val ease = PathInterpolator(0.2f, 0.9f, 0.1f, 1f)
    private var drawerOpen = false
    private var insetTop = 0
    private var insetBottom = 0
    private var builtFor = ""

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dpi(v: Float) = dp(v).toInt()
    private val look get() = prefs.look

    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = HomePrefs(this)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        root = FrameLayout(this)
        setContentView(root)
        root.setOnApplyWindowInsetsListener { _, ins ->
            @Suppress("DEPRECATION")
            insetTop = ins.systemWindowInsetTop
            @Suppress("DEPRECATION")
            insetBottom = ins.systemWindowInsetBottom
            home.setPadding(dpi(14f), insetTop + dpi(12f), dpi(14f), insetBottom + dpi(10f))
            drawer.setPadding(0, insetTop, 0, insetBottom)
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
        val t = IntentFilter(Intent.ACTION_TIME_TICK).apply { addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(timeReceiver, t, Context.RECEIVER_EXPORTED) else registerReceiver(timeReceiver, t)
    }

    override fun onResume() {
        super.onResume()
        // Einstellungen könnten sich geändert haben → neu aufbauen, wenn nötig
        if (signature() != builtFor) {
            Looks.clearCache()
            build()
            fillHome()
        }
        clockView?.invalidate()
    }

    override fun onDestroy() {
        try { unregisterReceiver(pkgReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(timeReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer()
        // Ein Launcher wird nie mit „Zurück“ beendet
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home-Taste: App-Übersicht schließen
        if (drawerOpen) closeDrawer()
    }

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = loadApps()
    }
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { clockView?.invalidate() }
    }

    private fun signature() = listOf(prefs.look, prefs.columns, prefs.iconDp, prefs.labels, prefs.dock, prefs.clock,
        prefs.search, prefs.monoIcons, prefs.favorites, prefs.dockApps).joinToString()

    private fun loadApps() {
        Thread {
            Apps.load(this)
            main.post {
                if (isFinishing) return@post
                if (!prefs.initialized) {
                    val (fav, dock) = Apps.defaults(this)
                    prefs.favorites = fav
                    prefs.dockApps = dock
                }
                fillHome()
                (drawerGrid.adapter as? DrawerAdapter)?.filter(drawerSearch.text?.toString().orEmpty())
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // Aufbau
    // ------------------------------------------------------------------

    private lateinit var grid: LinearLayout
    private lateinit var dockRow: LinearLayout

    private fun build() {
        root.removeAllViews()
        home = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(14f), insetTop + dpi(12f), dpi(14f), insetBottom + dpi(10f))
        }
        root.addView(home, FrameLayout.LayoutParams(-1, -1))

        clockView = if (prefs.clock) ClockView(this, look).also {
            home.addView(it, LinearLayout.LayoutParams(-1, dpi(ClockView.heightDp(look))))
        } else null

        grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        home.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(14f) })
        home.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        if (prefs.search && look == Look.APPLE) home.addView(searchPill(), LinearLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = dpi(14f)
        })
        dockRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = dockBackground()
            val pv = if (background != null) dpi(12f) else dpi(4f)
            setPadding(dpi(8f), pv, dpi(8f), pv)
        }
        if (prefs.dock) home.addView(dockRow, LinearLayout.LayoutParams(-1, -2))
        if (prefs.search && look in listOf(Look.ANDROID, Look.COLOROS)) {
            home.addView(searchPill(), LinearLayout.LayoutParams(-1, dpi(52f)).apply { topMargin = dpi(12f) })
        }

        // Leere Fläche gedrückt halten → Einstellungen
        home.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            startActivity(Intent(this, SettingsActivity::class.java))
            true
        }

        buildDrawer()
        builtFor = signature()
    }

    private fun dockBackground(): android.graphics.drawable.Drawable? = when (look) {
        Look.APPLE -> GradientDrawable().apply { cornerRadius = dp(32f); setColor(0x38FFFFFF); setStroke(dpi(1f), 0x2EFFFFFF) }
        Look.GALAXY -> GradientDrawable().apply { cornerRadius = dp(22f); setColor(0x26FFFFFF) }
        Look.COLOROS -> GradientDrawable().apply { cornerRadius = dp(30f); setColor(0x30FFFFFF) }
        else -> null
    }

    private fun searchPill(): View {
        val tv = TextView(this).apply {
            text = if (look == Look.APPLE) "⌕  Suchen" else "⌕   Apps & Einstellungen suchen"
            typeface = Looks.font(this@HomeActivity, look, 500)
            textSize = if (look == Look.APPLE) 14f else 16f
            gravity = if (look == Look.APPLE) Gravity.CENTER else Gravity.CENTER_VERTICAL or Gravity.START
            val dark = look == Look.ANDROID
            setTextColor(if (dark) 0xFF3C3C43.toInt() else Color.WHITE)
            setPadding(dpi(if (look == Look.APPLE) 16f else 22f), dpi(7f), dpi(16f), dpi(7f))
            background = GradientDrawable().apply {
                cornerRadius = dp(28f)
                setColor(when (look) {
                    Look.ANDROID -> 0xEBF1F0F6.toInt()
                    Look.APPLE -> 0x40FFFFFF
                    else -> 0x33FFFFFF
                })
            }
            setOnClickListener { openDrawer(focusSearch = true) }
        }
        return tv
    }

    // ------------------------------------------------------------------
    // Icons
    // ------------------------------------------------------------------

    private fun iconView(app: AppInfo, sizeDp: Int, showLabel: Boolean, inDock: Boolean): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dpi(6f), 0, dpi(6f))
        }
        val px = dpi(sizeDp.toFloat())
        val iv = ImageView(this).apply {
            setImageBitmap(Looks.icon(app.key, app.icon, px, look, prefs.monoIcons || look == Look.NOTHING))
            elevation = if (inDock || look == Look.APPLE || look == Look.NOTHING) 0f else dp(2f)
        }
        col.addView(iv, LinearLayout.LayoutParams(px, px))
        if (showLabel) col.addView(TextView(this).apply {
            text = app.label
            setTextColor(Color.WHITE)
            textSize = if (look == Look.NOTHING) 11f else 12f
            typeface = Looks.font(this@HomeActivity, look, if (look == Look.ONEUI || look == Look.GALAXY) 500 else 400)
            if (look == Look.NOTHING) letterSpacing = 0.04f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(dp(3f), 0f, dp(1f), 0x88000000.toInt())
            setPadding(dpi(2f), dpi(5f), dpi(2f), 0)
        }, LinearLayout.LayoutParams(-1, -2))

        col.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(110).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f)
                    .setDuration(260).setInterpolator(android.view.animation.OvershootInterpolator(2.5f)).start()
            }
            false
        }
        col.setOnClickListener { Apps.launch(this, app, iv) }
        col.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            appMenu(it, app)
            true
        }
        return col
    }

    private fun appMenu(anchor: View, app: AppInfo) {
        val pm = PopupMenu(this, anchor)
        val fav = prefs.favorites
        val dock = prefs.dockApps
        if (app.key in fav) pm.menu.add(0, 1, 0, "Vom Startbildschirm entfernen") else pm.menu.add(0, 2, 0, "Zum Startbildschirm")
        if (app.key in dock) pm.menu.add(0, 3, 1, "Aus dem Dock entfernen") else pm.menu.add(0, 4, 1, "Ins Dock")
        pm.menu.add(0, 5, 2, "App-Info")
        pm.menu.add(0, 6, 3, "Deinstallieren")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> prefs.favorites = fav - app.key
                2 -> prefs.favorites = fav + app.key
                3 -> prefs.dockApps = dock - app.key
                4 -> prefs.dockApps = (dock + app.key).takeLast(prefs.columns.coerceAtMost(5))
                5 -> Apps.appInfo(this, app)
                6 -> Apps.uninstall(this, app)
            }
            if (item.itemId in 1..4) {
                fillHome()
                builtFor = signature()
            }
            true
        }
        pm.show()
    }

    private fun fillHome() {
        if (!::grid.isInitialized) return
        grid.removeAllViews()
        val cols = prefs.columns
        val apps = prefs.favorites.mapNotNull { Apps.byKey(it) }
        apps.chunked(cols).forEach { rowApps ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowApps.forEach { a -> row.addView(iconView(a, prefs.iconDp, prefs.labels, false), LinearLayout.LayoutParams(0, -2, 1f)) }
            repeat(cols - rowApps.size) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
            grid.addView(row)
        }
        dockRow.removeAllViews()
        prefs.dockApps.mapNotNull { Apps.byKey(it) }.forEach { a ->
            dockRow.addView(iconView(a, prefs.iconDp, false, true), LinearLayout.LayoutParams(0, -2, 1f))
        }
    }

    // ------------------------------------------------------------------
    // App-Übersicht
    // ------------------------------------------------------------------

    private fun drawerColor() = when (look) {
        Look.APPLE -> 0xE6101014.toInt()
        Look.ANDROID -> 0xF21B1B1F.toInt()
        Look.ONEUI -> 0xEB000000.toInt()
        Look.GALAXY -> 0xD9000000.toInt()
        Look.NOTHING -> 0xF5000000.toInt()
        Look.COLOROS -> 0xE61A1A1E.toInt()
    }

    private fun buildDrawer() {
        drawer = FrameLayout(this).apply {
            setBackgroundColor(drawerColor())
            visibility = View.GONE
            isClickable = true
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        drawer.addView(col, FrameLayout.LayoutParams(-1, -1))
        col.addView(TextView(this).apply {
            text = when (look) {
                Look.APPLE -> "App-Mediathek"
                Look.NOTHING -> "APPS"
                else -> "Apps"
            }
            textSize = if (look == Look.NOTHING) 30f else 28f
            typeface = Looks.font(this@HomeActivity, look, 700, display = true)
            setTextColor(Color.WHITE)
            setPadding(dpi(22f), dpi(18f), dpi(22f), dpi(8f))
        })
        drawerSearch = EditText(this).apply {
            hint = "Suchen"
            setSingleLine()
            textSize = 16f
            setTextColor(Color.WHITE)
            setHintTextColor(0x99FFFFFF.toInt())
            typeface = Looks.font(this@HomeActivity, look, 400)
            setPadding(dpi(18f), dpi(10f), dpi(18f), dpi(10f))
            background = GradientDrawable().apply { cornerRadius = dp(if (look == Look.APPLE) 12f else 26f); setColor(0x26FFFFFF) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    (drawerGrid.adapter as? DrawerAdapter)?.filter(s?.toString().orEmpty())
                }
            })
            setOnEditorActionListener { _, _, _ ->
                (drawerGrid.adapter as? DrawerAdapter)?.first()?.let { Apps.launch(this@HomeActivity, it, null) }
                true
            }
        }
        col.addView(drawerSearch, LinearLayout.LayoutParams(-1, -2).apply {
            leftMargin = dpi(18f); rightMargin = dpi(18f); bottomMargin = dpi(8f)
        })
        drawerGrid = GridView(this).apply {
            numColumns = prefs.columns
            verticalSpacing = dpi(4f)
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            setPadding(dpi(8f), 0, dpi(8f), dpi(20f))
            clipToPadding = false
            isVerticalScrollBarEnabled = false
            selector = android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
            adapter = DrawerAdapter()
        }
        col.addView(drawerGrid, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(drawer, FrameLayout.LayoutParams(-1, -1))
        drawer.setPadding(0, insetTop, 0, insetBottom)
    }

    private inner class DrawerAdapter : BaseAdapter() {
        private var items: List<AppInfo> = Apps.all

        fun filter(q: String) {
            val qq = q.trim().lowercase()
            items = if (qq.isEmpty()) Apps.all else Apps.all.filter { it.label.lowercase().contains(qq) }
            notifyDataSetChanged()
        }

        fun first() = items.firstOrNull()

        override fun getCount() = items.size
        override fun getItem(position: Int) = items[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val a = items[position]
            return iconView(a, (prefs.iconDp * 0.92f).toInt(), true, false).apply {
                setOnClickListener {
                    Apps.launch(this@HomeActivity, a, this)
                    main.postDelayed({ closeDrawer(animate = false) }, 300)
                }
            }
        }
    }

    private fun openDrawer(focusSearch: Boolean = false) {
        if (drawerOpen) return
        drawerOpen = true
        drawer.visibility = View.VISIBLE
        drawerSearch.setText("")
        when (look) {
            Look.APPLE -> { drawer.alpha = 0f; drawer.scaleX = 0.94f; drawer.scaleY = 0.94f }
            Look.NOTHING -> { drawer.alpha = 0f }
            else -> { drawer.alpha = 0f; drawer.translationY = dp(80f) }
        }
        drawer.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f).setDuration(320).setInterpolator(ease).start()
        home.animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(260).start()
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
        home.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(if (animate) 300 else 0).start()
        if (!animate) {
            drawer.visibility = View.GONE
            return
        }
        drawer.animate().alpha(0f).translationY(if (look == Look.APPLE || look == Look.NOTHING) 0f else dp(60f))
            .scaleX(if (look == Look.APPLE) 0.95f else 1f).scaleY(if (look == Look.APPLE) 0.95f else 1f)
            .setDuration(240).withEndAction { drawer.visibility = View.GONE }.start()
    }

    // ------------------------------------------------------------------
    // Gesten: hoch = Apps, runter = Benachrichtigungen
    // ------------------------------------------------------------------

    private val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            val dy = e2.y - (e1?.y ?: e2.y)
            if (abs(vy) < abs(vx) || abs(dy) < dp(60f)) return false
            if (!drawerOpen && vy < 0) { openDrawer(); return true }
            if (!drawerOpen && vy > 0) { expandNotifications(); return true }
            if (drawerOpen && vy > 0 && !drawerGrid.canScrollVertically(-1)) { closeDrawer(); return true }
            return false
        }
    }).apply { setIsLongpressEnabled(false) }

    @SuppressLint("ClickableViewAccessibility")
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        gestures.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun expandNotifications() {
        try {
            val sb = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
        } catch (_: Exception) {}
    }
}

/** Uhr-Widget im Stil des jeweiligen Looks. */
@SuppressLint("ViewConstructor")
class ClockView(ctx: Context, private val look: Look) : View(ctx) {
    companion object {
        fun heightDp(look: Look) = when (look) {
            Look.APPLE -> 156f
            Look.ANDROID -> 96f
            Look.ONEUI -> 150f
            Look.GALAXY -> 200f
            Look.NOTHING -> 150f
            Look.COLOROS -> 140f
        }
    }

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    private fun text(t: String, x: Float, y: Float, size: Float, weight: Int, color: Int = Color.WHITE,
                     align: Paint.Align = Paint.Align.LEFT, display: Boolean = false) {
        p.textSize = size * d
        p.typeface = Looks.font(context, look, weight, display)
        p.color = color
        p.textAlign = align
        p.setShadowLayer(4 * d, 0f, d, 0x66000000)
        canvasRef?.drawText(t, x, y, p)
    }

    private var canvasRef: Canvas? = null

    override fun onDraw(c: Canvas) {
        canvasRef = c
        val now = Date()
        val de = Locale.GERMANY
        val w = width.toFloat()
        val h = height.toFloat()
        val time = SimpleDateFormat("HH:mm", de).format(now)
        when (look) {
            Look.APPLE -> {
                // iOS-Widget: Glas-Karte, Wochentag rot, großer Tag, Uhrzeit
                r.set(4 * d, 8 * d, w - 4 * d, h - 8 * d)
                p.clearShadowLayer()
                p.color = 0x38FFFFFF
                c.drawRoundRect(r, 24 * d, 24 * d, p)
                text(SimpleDateFormat("EEEE", de).format(now).uppercase(), r.left + 20 * d, r.top + 34 * d, 13f, 700, 0xFFFF453A.toInt())
                text(SimpleDateFormat("d", de).format(now), r.left + 18 * d, r.top + 92 * d, 54f, 300)
                text(time, r.right - 20 * d, r.top + 92 * d, 48f, 600, align = Paint.Align.RIGHT)
                text(SimpleDateFormat("MMMM", de).format(now), r.left + 20 * d, r.bottom - 16 * d, 14f, 500, 0xCCFFFFFF.toInt())
            }
            Look.ANDROID -> {
                text(SimpleDateFormat("EEE., d. MMM.", de).format(now), 10 * d, 44 * d, 24f, 400)
                text(time, 10 * d, 78 * d, 16f, 500, 0xDDFFFFFF.toInt())
            }
            Look.ONEUI -> {
                text(time, w / 2, 96 * d, 76f, 250, align = Paint.Align.CENTER)
                text(SimpleDateFormat("EEE., d. MMMM", de).format(now), w / 2, 128 * d, 16f, 500, align = Paint.Align.CENTER)
            }
            Look.GALAXY -> {
                val hh = SimpleDateFormat("HH", de).format(now)
                val mm = SimpleDateFormat("mm", de).format(now)
                text(hh, 12 * d, 86 * d, 84f, 800)
                text(mm, 12 * d, 166 * d, 84f, 300)
                text(SimpleDateFormat("EEEE d. MMM", de).format(now), w - 14 * d, 160 * d, 15f, 500, align = Paint.Align.RIGHT)
            }
            Look.NOTHING -> {
                text(time, w / 2, 96 * d, 84f, 700, align = Paint.Align.CENTER, display = true)
                p.clearShadowLayer()
                p.color = 0xFFD71921.toInt()
                c.drawCircle(w / 2 + 128 * d, 34 * d, 5 * d, p)
                text(SimpleDateFormat("EEE d MMM", de).format(now).uppercase(), w / 2, 130 * d, 14f, 500,
                    align = Paint.Align.CENTER)
            }
            Look.COLOROS -> {
                text(time, 10 * d, 84 * d, 72f, 200)
                text(SimpleDateFormat("EEEE, d. MMMM", de).format(now), 12 * d, 116 * d, 16f, 400, 0xE6FFFFFF.toInt())
            }
        }
        canvasRef = null
    }
}
