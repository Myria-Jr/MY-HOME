package de.myria.island

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.max

/**
 * Bedienungshilfe-Dienst: Nur er darf (ohne Root) über der Statusleiste
 * und der Kamera-Aussparung zeichnen. Liest KEINE Bildschirminhalte.
 */
class IslandService : AccessibilityService(), IslandBus.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener, IslandController.Host {

    private lateinit var wm: WindowManager
    private var view: IslandView? = null
    private var controller: IslandController? = null
    private val lp = WindowManager.LayoutParams()
    private var cfg = IslandConfig()
    private var touchable = true
    private val handler = Handler(Looper.getMainLooper())
    private var testRevert: Runnable? = null

    private val density get() = resources.displayMetrics.density

    // ------------------------------------------------------------------
    // Lebenszyklus
    // ------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        cfg = Prefs.load(this)

        val v = IslandView(this)
        view = v
        v.hostWidthPx = screenWidth()
        val c = IslandController(v, this)
        controller = c
        c.cfg = cfg

        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        lp.format = PixelFormat.TRANSLUCENT
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        lp.width = (cfg.widthDp * density * 1.4f).toInt()
        lp.height = (cfg.heightDp * density * 1.4f).toInt()
        lp.flags = baseFlags()
        if (Build.VERSION.SDK_INT >= 30) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        } else if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        lp.title = "Island"
        applyPosition()

        v.onWindowNeed = { w, h, settled -> resizeWindow(w, h, settled) }

        try {
            wm.addView(v, lp)
        } catch (e: Exception) {
            view = null
            return
        }

        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        IslandBus.add(this)
        IslandBus.serviceRunning = true

        registerSystemReceivers()
        updateLandscape(resources.configuration)
        c.setMusic(IslandBus.music)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    private fun teardown() {
        IslandBus.serviceRunning = false
        IslandBus.remove(this)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        try { unregisterReceiver(systemReceiver) } catch (_: Exception) {}
        controller?.release()
        controller = null
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        view?.hostWidthPx = screenWidth()
        applyPosition()
        updateWindow()
        updateLandscape(newConfig)
        controller?.refresh()
    }

    private fun updateLandscape(c: Configuration) {
        controller?.suppressed = cfg.hideLandscape && c.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    // ------------------------------------------------------------------
    // Fenster
    // ------------------------------------------------------------------

    private fun baseFlags(): Int {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return f
    }

    private fun resizeWindow(w: Int, h: Int, settled: Boolean) {
        val cw = lp.width
        val ch = lp.height
        val nw: Int
        val nh: Int
        if (settled) {
            nw = w; nh = h
        } else {
            // Beim Animieren nur wachsen (mit etwas Luft für den Bounce) → kein Flackern
            val slackW = (w * 0.08f + 12 * density).toInt()
            val slackH = (h * 0.08f + 12 * density).toInt()
            nw = if (w > cw) w + slackW else cw
            nh = if (h > ch) h + slackH else ch
        }
        if (abs(nw - cw) > 1 || abs(nh - ch) > 1) {
            lp.width = nw
            lp.height = nh
            updateWindow()
        }
    }

    private fun updateWindow() {
        val v = view ?: return
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
    }

    private fun applyPosition() {
        val d = density
        var x = 0f
        var y = 10 * d
        if (cfg.autoCamera) {
            val r = cutoutRect()
            if (r != null && r.height() > 0) {
                x = r.centerX() - screenWidth() / 2f
                y = max(0f, r.centerY() - cfg.heightDp * d / 2f)
            }
        }
        lp.x = (x + cfg.offsetXDp * d).toInt()
        lp.y = (y + cfg.offsetYDp * d).toInt()
    }

    @Suppress("DEPRECATION")
    private fun cutoutRect(): Rect? {
        return try {
            when {
                Build.VERSION.SDK_INT >= 30 ->
                    wm.currentWindowMetrics.windowInsets.displayCutout?.boundingRectTop
                Build.VERSION.SDK_INT >= 29 ->
                    wm.defaultDisplay.cutout?.boundingRectTop
                else -> null
            }?.takeIf { !it.isEmpty }
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun screenWidth(): Int {
        if (Build.VERSION.SDK_INT >= 30) return wm.currentWindowMetrics.bounds.width()
        val m = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(m)
        return m.widthPixels
    }

    // ------------------------------------------------------------------
    // Host-Callbacks
    // ------------------------------------------------------------------

    override fun haptic(strong: Boolean) {
        val v = view ?: return
        @Suppress("DEPRECATION")
        v.performHapticFeedback(
            if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK,
            HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING,
        )
    }

    override fun onModeChanged(mode: IslandView.Mode) {
        val t = !(mode == IslandView.Mode.HIDDEN ||
            (mode == IslandView.Mode.IDLE && cfg.idleTouchThrough))
        if (t != touchable) {
            touchable = t
            lp.flags = baseFlags()
            updateWindow()
        }
    }

    // ------------------------------------------------------------------
    // Events
    // ------------------------------------------------------------------

    override fun onMusic(m: MusicInfo?) {
        if (testRevert == null) controller?.setMusic(m)
    }

    override fun onEvent(c: Content) {
        controller?.showTransient(c)
    }

    override fun onTestMusic(m: MusicInfo) {
        testRevert?.let { handler.removeCallbacks(it) }
        controller?.setMusic(m)
        val r = Runnable {
            testRevert = null
            controller?.setMusic(IslandBus.music)
        }
        testRevert = r
        handler.postDelayed(r, 10_000)
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        cfg = Prefs.load(this)
        controller?.cfg = cfg
        applyPosition()
        updateWindow()
        updateLandscape(resources.configuration)
    }

    private var lastPlugged: Boolean? = null
    private var lastRinger: Int? = null

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val pct = if (level >= 0) level * 100 / scale else 0
                    val state = Content.Charging(pct, plugged)
                    controller?.setCharging(state)
                    val was = lastPlugged
                    lastPlugged = plugged
                    if (was != null && !was && plugged) controller?.showTransient(state)
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val mode = intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE, AudioManager.RINGER_MODE_NORMAL)
                    val was = lastRinger
                    lastRinger = mode
                    if (was != null && was != mode && !isInitialStickyBroadcast) {
                        controller?.showTransient(
                            Content.Ringer(
                                silent = mode == AudioManager.RINGER_MODE_SILENT,
                                vibrate = mode == AudioManager.RINGER_MODE_VIBRATE,
                            )
                        )
                    }
                }
            }
        }
    }

    private fun registerSystemReceivers() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(systemReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemReceiver, f)
        }
    }
}
