package de.myria.island

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.camera2.CameraManager
import android.location.GnssStatus
import android.location.LocationManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
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

    private var landscape = false
    private var screenOff = false
    private var locked = false

    private var privacy = Privacy()
    private val camerasInUse = HashSet<String>()
    private var gnssRegistered = false

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
        registerPrivacyWatchers()
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        updateSuppressed()
        c.setMusic(IslandBus.music)
        c.setLive(IslandBus.live)
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

    private var tornDown = false

    private fun teardown() {
        if (tornDown) return
        tornDown = true
        IslandBus.serviceRunning = false
        IslandBus.remove(this)
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(this)
        try { unregisterReceiver(systemReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(btReceiver) } catch (_: Exception) {}
        unregisterPrivacyWatchers()
        controller?.release()
        controller = null
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPosition()
        updateWindow()
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateSuppressed()
        controller?.refresh()
    }

    private fun updateSuppressed() {
        controller?.suppressed = (cfg.hideLandscape && landscape) || screenOff ||
            (locked && !cfg.showOnLockscreen)
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
        val sw = screenWidth()
        if (cfg.autoCamera) {
            val r = cutoutRect()
            if (r != null && r.height() > 0) {
                x = r.centerX() - sw / 2f
                y = max(0f, r.centerY() - cfg.heightDp * d / 2f)
            }
        }
        lp.x = (x + cfg.offsetXDp * d).toInt()
        lp.y = (y + cfg.offsetYDp * d).toInt()
        // Fix: Bei seitlicher Kamera darf die große Island nicht aus dem Bildschirm ragen
        view?.hostWidthPx = (sw - 2 * abs(lp.x)).coerceAtLeast((sw * 0.5f).toInt())
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
        val type = when (cfg.hapticStrength) {
            0 -> if (strong) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK
            2 -> if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.VIRTUAL_KEY
            else -> if (strong) HapticFeedbackConstants.LONG_PRESS else HapticFeedbackConstants.CONTEXT_CLICK
        }
        @Suppress("DEPRECATION")
        v.performHapticFeedback(type, HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
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
    // Events vom Bus
    // ------------------------------------------------------------------

    override fun onMusic(m: MusicInfo?) {
        if (testRevert == null) controller?.setMusic(m)
    }

    private var testLive: List<Content> = emptyList()
    private val clearTestLive = Runnable {
        testLive = emptyList()
        controller?.setLive(IslandBus.live)
    }

    override fun onLive(list: List<Content>) {
        controller?.setLive(list + testLive)
    }

    override fun onTestLive(list: List<Content>) {
        testLive = list
        controller?.setLive(IslandBus.live + list)
        handler.removeCallbacks(clearTestLive)
        if (list.isNotEmpty()) handler.postDelayed(clearTestLive, 20_000)
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
        updateSuppressed()
        // Berechtigungen evtl. neu erteilt → Watcher nachziehen
        registerGnss()
        registerBluetooth()
    }

    // ------------------------------------------------------------------
    // Kamera / Mikrofon / Standort
    // ------------------------------------------------------------------

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(cameraId: String) {
            camerasInUse.add(cameraId)
            pushPrivacy()
        }
        override fun onCameraAvailable(cameraId: String) {
            camerasInUse.remove(cameraId)
            pushPrivacy()
        }
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            privacy = privacy.copy(mic = !configs.isNullOrEmpty())
            controller?.privacy = privacy
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onStarted() {
            privacy = privacy.copy(location = true)
            controller?.privacy = privacy
        }
        override fun onStopped() {
            privacy = privacy.copy(location = false)
            controller?.privacy = privacy
        }
    }

    private fun pushPrivacy() {
        privacy = privacy.copy(camera = camerasInUse.isNotEmpty())
        controller?.privacy = privacy
    }

    private fun registerPrivacyWatchers() {
        try {
            (getSystemService(CAMERA_SERVICE) as CameraManager).registerAvailabilityCallback(cameraCallback, handler)
        } catch (_: Exception) {}
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.registerAudioRecordingCallback(recordingCallback, handler)
            privacy = privacy.copy(mic = am.activeRecordingConfigurations.isNotEmpty())
        } catch (_: Exception) {}
        registerGnss()
        registerBluetooth()
    }

    @Suppress("DEPRECATION", "MissingPermission")
    private fun registerGnss() {
        if (gnssRegistered) return
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        try {
            val lm = getSystemService(LOCATION_SERVICE) as LocationManager
            gnssRegistered = lm.registerGnssStatusCallback(gnssCallback, handler)
        } catch (_: Exception) {}
    }

    private fun unregisterPrivacyWatchers() {
        try { (getSystemService(CAMERA_SERVICE) as CameraManager).unregisterAvailabilityCallback(cameraCallback) } catch (_: Exception) {}
        try { (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioRecordingCallback(recordingCallback) } catch (_: Exception) {}
        if (gnssRegistered) {
            try { (getSystemService(LOCATION_SERVICE) as LocationManager).unregisterGnssStatusCallback(gnssCallback) } catch (_: Exception) {}
            gnssRegistered = false
        }
    }

    // ------------------------------------------------------------------
    // Bluetooth-Kopfhörer
    // ------------------------------------------------------------------

    private var btRegistered = false
    private var lastDeviceName = ""
    private var lastDeviceAt = 0L

    private val btReceiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(ctx: Context, intent: Intent) {
            val dev: BluetoothDevice = (if (Build.VERSION.SDK_INT >= 33)
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            else intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)) ?: return
            val name = try { dev.name ?: "Kopfhörer" } catch (_: SecurityException) { "Kopfhörer" }
            val isAudio = try {
                val cls = dev.bluetoothClass
                cls == null || cls.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO
            } catch (_: SecurityException) { true }
            if (!isAudio) return
            when (intent.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    lastDeviceName = name
                    lastDeviceAt = System.currentTimeMillis()
                    controller?.showTransient(Content.Device(name, true, batteryOf(dev)))
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED ->
                    controller?.showTransient(Content.Device(name, false, -1))
                ACTION_BATTERY_LEVEL -> {
                    val lvl = intent.getIntExtra(EXTRA_BATTERY_LEVEL, -1)
                    // Akkustand kommt oft kurz nach dem Verbinden → Anzeige aktualisieren
                    if (lvl in 0..100 && name == lastDeviceName && System.currentTimeMillis() - lastDeviceAt < 6000) {
                        controller?.showTransient(Content.Device(name, true, lvl))
                    }
                }
            }
        }
    }

    private fun batteryOf(dev: BluetoothDevice): Int = try {
        (dev.javaClass.getMethod("getBatteryLevel").invoke(dev) as? Int)?.takeIf { it in 0..100 } ?: -1
    } catch (_: Exception) { -1 }

    private fun registerBluetooth() {
        if (btRegistered) return
        if (Build.VERSION.SDK_INT >= 31 &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) return
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BATTERY_LEVEL)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(btReceiver, f, Context.RECEIVER_EXPORTED)
            else registerReceiver(btReceiver, f)
            btRegistered = true
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------
    // Akku, Lautlos, Bildschirm
    // ------------------------------------------------------------------

    private var lastPlugged: Boolean? = null
    private var lastLevel = -1
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
                    val prevLevel = lastLevel
                    lastPlugged = plugged
                    lastLevel = pct
                    if (was != null && !was && plugged) controller?.showTransient(state)
                    if (!plugged && prevLevel >= 0 &&
                        ((prevLevel > 20 && pct <= 20) || (prevLevel > 10 && pct <= 10))
                    ) {
                        controller?.showTransient(Content.Charging(pct, false))
                    }
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
                Intent.ACTION_SCREEN_OFF -> {
                    screenOff = true
                    locked = true
                    updateSuppressed()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOff = false
                    locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
                    updateSuppressed()
                }
                Intent.ACTION_USER_PRESENT -> {
                    val wasLocked = locked
                    locked = false
                    screenOff = false
                    updateSuppressed()
                    if (wasLocked) controller?.showTransient(UnlockContent())
                }
            }
        }
    }

    private fun registerSystemReceivers() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(systemReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemReceiver, f)
        }
    }

    companion object {
        private const val ACTION_BATTERY_LEVEL = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
