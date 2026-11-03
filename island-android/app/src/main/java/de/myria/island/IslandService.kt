package de.myria.island

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.graphics.Color
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
import android.view.MotionEvent
import android.view.View
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
    /** Unsichtbares Touch-Fenster: nur es ändert seine Größe → kein Ruckeln beim Zeichnen. */
    private val tlp = WindowManager.LayoutParams()
    private var touchView: View? = null
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
        c.cfg = effective()

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

        // Zeichen-Fenster: immer bildschirmbreit und durchlässig – seine Breite ändert sich nie,
        // daher kann Android beim Verkleinern kein abgeschnittenes altes Bild zeigen (Ruck nach links).
        tlp.copyFrom(lp)
        tlp.title = "IslandTouch"
        tlp.flags = baseFlags()
        lp.width = screenWidth()
        lp.flags = drawFlags()
        val tv = object : View(this) {
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(e: MotionEvent): Boolean {
                val iv = view ?: return false
                val ev = MotionEvent.obtain(e)
                ev.offsetLocation((iv.width - width) / 2f, 0f)
                val r = iv.dispatchTouchEvent(ev)
                ev.recycle()
                return r
            }
        }
        touchView = tv

        try {
            wm.addView(v, lp)
            wm.addView(tv, tlp)
        } catch (e: Exception) {
            try { wm.removeView(v) } catch (_: Exception) {}
            view = null
            touchView = null
            return
        }

        Prefs.sp(this).registerOnSharedPreferenceChangeListener(this)
        IslandBus.add(this)
        IslandBus.serviceRunning = true

        registerSystemReceivers()
        registerStatusWatchers()
        registerPrivacyWatchers()
        registerNetwork()
        OwnTimers.onChange = { pushLive() }
        OwnTimers.onTimerDone = { timerDone() }
        updateNextAlarm(false)
        landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        updateSuppressed()
        c.setMusic(IslandBus.music)
        pushLive()
    }

    // ------------------------------------------------------------------
    // Vordergrund-App → in bestimmten Apps ausblenden
    // ------------------------------------------------------------------

    private var foregroundHidden = false
    private val ignoredPkgs by lazy {
        val set = hashSetOf(packageName, "com.android.systemui", "android")
        try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.enabledInputMethodList.forEach { set.add(it.packageName) }
        } catch (_: Exception) {}
        set
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in ignoredPkgs) return
        controller?.foregroundPkg = pkg
        val hide = pkg in cfg.hiddenInApps
        if (hide != foregroundHidden) {
            foregroundHidden = hide
            updateSuppressed()
        }
    }

    /** Einstellungen + Energiesparmodus (spart Akku: keine Wellenform, kein Glow, ruhige Animationen) */
    private fun effective(): IslandConfig {
        val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
        return if (cfg.powerSaveReduce && pm.isPowerSaveMode)
            cfg.copy(waveform = false, glow = false, reduceMotion = true, vinyl = false)
        else cfg
    }
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
        try { unregisterReceiver(screenReceiver) } catch (_: Exception) {}
        unregisterStatusWatchers()
        try { unregisterReceiver(btReceiver) } catch (_: Exception) {}
        unregisterPrivacyWatchers()
        unregisterNetwork()
        OwnTimers.onChange = null
        OwnTimers.onTimerDone = null
        controller?.release()
        controller = null
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
        touchView?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        touchView = null
        handler.removeCallbacksAndMessages(null)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val night = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (lastNight != null && lastNight != night) status("darkmode", if (night) "Dunkelmodus" else "Heller Modus",
            if (night) SGlyph.MOON else SGlyph.SUN, if (night) 0xFF7D7AFF.toInt() else 0xFFFFD60A.toInt(), 160f)
        lastNight = night
        applyPosition()
        updateWindow()
        landscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        updateSuppressed()
        controller?.refresh()
    }

    private var lastMode = IslandView.Mode.HIDDEN

    private fun updateSuppressed() {
        onModeChanged(lastMode)
        controller?.suppressed = (cfg.hideLandscape && landscape) || screenOff || foregroundHidden ||
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

    private fun drawFlags(): Int =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED

    private var pendingShrink: Runnable? = null

    /**
     * Fix Zittern:
     *  1. Breite hat immer dieselbe Gerade/Ungerade-Parität wie der Bildschirm → das Fenster
     *     sitzt pixelgenau mittig, die Island springt nicht mehr um einen halben Pixel.
     *  2. Beim Animieren nur EINMAL großzügig wachsen (Platz für den ganzen Bounce),
     *     nicht bei jedem Nachfedern erneut.
     *  3. Verkleinern erst, wenn die Island 250 ms ruhig ist – kein Hin-und-Her.
     */
    private fun resizeWindow(w: Int, h: Int, settled: Boolean) {
        val cw = tlp.width
        val ch = tlp.height
        val sw = screenWidth()
        fun parity(x: Int): Int {
            var v = x.coerceAtMost(sw)
            if ((sw - v) % 2 != 0) v += 1
            return v.coerceAtMost(sw)
        }
        if (!settled) {
            pendingShrink?.let { handler.removeCallbacks(it) }
            pendingShrink = null
            val grow = w > cw || h > ch
            if (!grow) return
            val nw = if (w > cw) parity(w + (w * 0.22f + 24 * density).toInt()) else cw
            val nh = if (h > ch) h + (h * 0.22f + 24 * density).toInt() else ch
            apply(nw, nh)
            return
        }
        val nw = parity(w)
        val nh = h
        if (nw > cw || nh > ch) {
            apply(maxOf(nw, cw), maxOf(nh, ch))
            return
        }
        if (cw - nw <= 2 && ch - nh <= 2) return
        if (pendingShrink != null) return
        val r = Runnable {
            pendingShrink = null
            apply(nw, nh)
        }
        pendingShrink = r
        handler.postDelayed(r, 250)
    }

    private fun apply(nw: Int, nh: Int) {
        if (nw == tlp.width && nh == tlp.height) return
        tlp.width = nw
        tlp.height = nh
        // Höhe des Zeichen-Fensters folgt nur beim Wachsen sofort; Verkleinern schneidet unten ab,
        // die Island hängt oben → kein sichtbarer Sprung.
        lp.height = nh
        updateWindow()
    }

    private fun updateWindow() {
        val v = view ?: return
        lp.width = screenWidth()
        tlp.x = lp.x
        tlp.y = lp.y
        try { wm.updateViewLayout(v, lp) } catch (_: Exception) {}
        touchView?.let { try { wm.updateViewLayout(it, tlp) } catch (_: Exception) {} }
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
        lastMode = mode
        // Fix Entsperren: auf dem Sperrbildschirm fängt die Island keine Berührungen ab
        val t = !(locked || mode == IslandView.Mode.HIDDEN ||
            (mode == IslandView.Mode.IDLE && cfg.idleTouchThrough))
        if (t != touchable) {
            touchable = t
            tlp.flags = baseFlags()
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
    /** Live-Aktivitäten, die der Service selbst erzeugt (Taschenlampe) */
    private var systemLive: List<Content> = emptyList()
    private val clearTestLive = Runnable {
        testLive = emptyList()
        pushLive()
    }

    private fun pushLive() {
        controller?.setLive(IslandBus.live + systemLive + OwnTimers.items() + testLive)
    }

    // ---- Host-Aktionen (leere Pille gedrückt halten / Doppeltipp) ----

    override fun toggleTorch() {
        val cm = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            if (torchesOn.isNotEmpty()) {
                for (id in torchesOn.toList()) cm.setTorchMode(id, false)
            } else {
                val id = cm.cameraIdList.firstOrNull { id ->
                    cm.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                } ?: return
                cm.setTorchMode(id, true)
            }
        } catch (_: Exception) {}
    }

    override fun startTimer() = OwnTimers.startTimer(cfg.timerMinutes.coerceIn(1, 120))

    // ---- Kontrollzentrum ----

    private val audio get() = getSystemService(AUDIO_SERVICE) as AudioManager

    override fun volume(): Float {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override fun setVolume(v: Float) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max + 0.5f).toInt().coerceIn(0, max), 0) } catch (_: Exception) {}
    }

    override fun canBrightness(): Boolean = android.provider.Settings.System.canWrite(this)

    override fun brightness(): Float = try {
        android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Exception) { 0.5f }

    private var askedWriteSettings = false

    override fun setBrightness(v: Float) {
        if (!canBrightness()) {
            if (!askedWriteSettings) {
                askedWriteSettings = true
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        android.net.Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {}
            }
            return
        }
        try {
            val cr = contentResolver
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            android.provider.Settings.System.putInt(cr, android.provider.Settings.System.SCREEN_BRIGHTNESS, (v * 255).toInt().coerceIn(1, 255))
        } catch (_: Exception) {}
    }

    override fun openCamera() {
        try {
            startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    /** 4 = WLAN, 5 = Bluetooth, 6 = Rechner, 7 = Einstellungen */
    override fun openShortcut(index: Int) {
        val i = when (index) {
            4 -> Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            5 -> Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            6 -> Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
            else -> Intent(android.provider.Settings.ACTION_SETTINGS)
        }
        try { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {
            try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
        }
    }

    override fun onCalibrate(on: Boolean) {
        controller?.calibrating = on
    }

    override fun startStopwatch() {
        if (OwnTimers.stopwatchRunning) OwnTimers.stopStopwatch() else OwnTimers.startStopwatch()
    }

    override fun openSettings() {
        try {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    private fun timerDone() {
        try {
            @Suppress("DEPRECATION") val v = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            v.vibrate(android.os.VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1))
        } catch (_: Exception) {}
        controller?.showTransient(Content.Notif(null, "Lumora", "Timer abgelaufen ⏰", "Dein Timer ist fertig.", null))
    }

    override fun onLive(list: List<Content>) = pushLive()

    override fun onTestLive(list: List<Content>) {
        testLive = list
        pushLive()
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
        controller?.cfg = effective()
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

    private val torchesOn = HashSet<String>()

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (enabled) torchesOn.add(cameraId) else torchesOn.remove(cameraId)
            pushTorch()
            pushPrivacy()
            pushLive()
        }
    }

    /** Max. Helligkeitsstufe der Lampe (Android 13+), 1 = nicht regelbar */
    private fun torchMax(id: String): Int = if (Build.VERSION.SDK_INT >= 33) try {
        (getSystemService(CAMERA_SERVICE) as CameraManager).getCameraCharacteristics(id)
            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    } catch (_: Exception) { 1 } else 1

    private fun torchLevel(id: String): Float {
        val max = torchMax(id)
        if (max <= 1 || Build.VERSION.SDK_INT < 33) return 1f
        return try {
            (getSystemService(CAMERA_SERVICE) as CameraManager).getTorchStrengthLevel(id).toFloat() / max
        } catch (_: Exception) { 1f }
    }

    private fun pushTorch() {
        val id = torchesOn.firstOrNull()
        systemLive = if (id == null) emptyList() else listOf(TorchContent(
            off = {
                val cm = getSystemService(CAMERA_SERVICE) as CameraManager
                for (tid in torchesOn.toList()) try { cm.setTorchMode(tid, false) } catch (_: Exception) {}
            },
            level = torchLevel(id),
            adjustable = torchMax(id) > 1,
        ))
        pushLive()
    }

    override fun setTorchLevel(level: Float) {
        val id = torchesOn.firstOrNull() ?: return
        val max = torchMax(id)
        if (max <= 1 || Build.VERSION.SDK_INT < 33) return
        try {
            (getSystemService(CAMERA_SERVICE) as CameraManager)
                .turnOnTorchWithStrengthLevel(id, (level * max).toInt().coerceIn(1, max))
        } catch (_: Exception) {}
        handler.postDelayed({ pushTorch() }, 120)
    }

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
            applyPrivacy()
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onStarted() {
            privacy = privacy.copy(location = true)
            applyPrivacy()
        }
        override fun onStopped() {
            privacy = privacy.copy(location = false)
            applyPrivacy()
        }
    }

    private fun pushPrivacy() {
        // Taschenlampe belegt auf manchen Geräten die Kamera → kein grüner Punkt dafür
        privacy = privacy.copy(camera = (camerasInUse - torchesOn).isNotEmpty())
        applyPrivacy()
    }

    /** Zeitpunkt des letzten Entsperrens (Gesichtsentsperrung nutzt kurz die Kamera) */
    private var unlockedAt = 0L

    /**
     * Fix Entsperren: Während der Sperrbildschirm aktiv ist und kurz nach dem Entsperren
     * wird die Kamera ignoriert – sonst ploppt bei Gesichtsentsperrung ein grüner Punkt auf.
     */
    private fun applyPrivacy() {
        val faceWindow = locked || System.currentTimeMillis() - unlockedAt < 3000
        val shown = if (faceWindow) privacy.copy(camera = false) else privacy
        controller?.privacy = shown
        if (faceWindow && privacy.camera) {
            handler.removeCallbacks(reapplyPrivacy)
            handler.postDelayed(reapplyPrivacy, 3100)
        }
    }

    private val reapplyPrivacy = Runnable { applyPrivacy() }

    private fun registerPrivacyWatchers() {
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            cm.registerAvailabilityCallback(cameraCallback, handler)
            cm.registerTorchCallback(torchCallback, handler)
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
        try {
            val cm = getSystemService(CAMERA_SERVICE) as CameraManager
            cm.unregisterAvailabilityCallback(cameraCallback)
            cm.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {}
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
    private var lastFocus = false
    private var lastHeatWarn = 0L

    /** Bildschirmereignisse kommen auf manchen Android-Versionen von privilegierten System-Apps. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOff = true
                    locked = true
                    updateSuppressed()
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOff = false
                    locked = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
                    updateSuppressed()
                    applyPrivacy()
                }
                Intent.ACTION_USER_PRESENT -> {
                    onUnlockedChecks()
                    val wasLocked = locked
                    locked = false
                    screenOff = false
                    unlockedAt = System.currentTimeMillis()
                    updateSuppressed()
                    applyPrivacy()
                    if (wasLocked) handler.postDelayed({ controller?.showTransient(UnlockContent()) }, 180)
                }
            }
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                    val pct = if (level >= 0) level * 100 / scale else 0
                    val tempC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                        .let { if (it == Int.MIN_VALUE) Float.NaN else it / 10f }
                    val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
                    val timeLeft = if (plugged && Build.VERSION.SDK_INT >= 28) try { bm.computeChargeTimeRemaining() } catch (_: Exception) { -1L } else -1L
                    val current = try { kotlin.math.abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)) } catch (_: Exception) { 0 }
                    // Manche Geräte melden µA, manche mA
                    val fast = plugged && (current >= 1_800_000 || current in 1800..20000)
                    val state = Content.Charging(pct, plugged, timeLeft, fast, tempC)
                    if (!tempC.isNaN() && tempC >= 42f && System.currentTimeMillis() - lastHeatWarn > 10 * 60_000L) {
                        lastHeatWarn = System.currentTimeMillis()
                        controller?.showTransient(HeatContent(tempC))
                    }
                    controller?.setCharging(state)
                    val was = lastPlugged
                    val prevLevel = lastLevel
                    lastPlugged = plugged
                    lastLevel = pct
                    if (was != null && !was && plugged) controller?.showTransient(state)
                    if (plugged && pct >= 100 && prevLevel in 0..99) status("full", "Voll geladen", SGlyph.BOLT, 0xFF30D158.toInt(), 150f)
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
                ACTION_VOLUME -> {
                    val stream = intent.getIntExtra(EXTRA_VOLUME_STREAM, -1)
                    if (stream != AudioManager.STREAM_MUSIC || screenOff) return
                    val am = getSystemService(AUDIO_SERVICE) as AudioManager
                    val vol = intent.getIntExtra(EXTRA_VOLUME_VALUE, am.getStreamVolume(stream))
                    val max = am.getStreamMaxVolume(stream).coerceAtLeast(1)
                    val prev = intent.getIntExtra(EXTRA_VOLUME_PREV, -1)
                    if (prev == vol) return
                    controller?.showTransient(VolumeContent(vol.toFloat() / max, vol == 0))
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    val on = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
                    if (on != lastFocus) {
                        lastFocus = on
                        controller?.showTransient(FocusContent(on))
                    }
                }
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    controller?.showTransient(AirplaneContent(intent.getBooleanExtra("state", false)))
                }
                android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    controller?.cfg = effective()
                    val on = (getSystemService(POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode
                    status("powersave", if (on) "Energiesparen an" else "Energiesparen aus", SGlyph.LEAF,
                        if (on) 0xFFFFD60A.toInt() else 0xFF8E8E93.toInt(), 190f)
                }
                android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> updateNextAlarm(true)
            }
        }
    }

    // ------------------------------------------------------------------
    // Weitere System-Meldungen (Hotspot, NFC, Bluetooth, Apps, USB, Termine …)
    // ------------------------------------------------------------------

    private var lastNight: Boolean? = null
    private var lastLocation: Boolean? = null
    private var lastRotation: Int? = null
    private var unlockStart = 0L
    private var screenTimeShown = false
    private var lastStorageCheck = 0L
    private val shownEvents = HashSet<Long>()

    private fun status(kind: String, title: String, glyph: Int, color: Int, widthDp: Float = 150f, app: android.graphics.Bitmap? = null) {
        handler.post { controller?.showTransient(StatusContent(kind, title, glyph, color, app, widthDp)) }
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (isInitialStickyBroadcast) return
            when (intent.action) {
                "android.net.wifi.WIFI_AP_STATE_CHANGED" -> when (intent.getIntExtra("wifi_state", -1)) {
                    13 -> status("hotspot", "Hotspot an", SGlyph.HOTSPOT, 0xFF30D158.toInt(), 150f)
                    11 -> status("hotspot", "Hotspot aus", SGlyph.HOTSPOT, 0xFF8E8E93.toInt(), 150f)
                }
                "android.nfc.action.ADAPTER_STATE_CHANGED" -> when (intent.getIntExtra("android.nfc.extra.ADAPTER_STATE", -1)) {
                    3 -> status("nfc", "NFC an", SGlyph.NFC, 0xFF0A84FF.toInt(), 120f)
                    1 -> status("nfc", "NFC aus", SGlyph.NFC, 0xFF8E8E93.toInt(), 120f)
                }
                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> when (intent.getIntExtra(android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1)) {
                    android.bluetooth.BluetoothAdapter.STATE_ON -> status("bluetooth", "Bluetooth an", SGlyph.BLUETOOTH, 0xFF0A84FF.toInt(), 160f)
                    android.bluetooth.BluetoothAdapter.STATE_OFF -> status("bluetooth", "Bluetooth aus", SGlyph.BLUETOOTH, 0xFF8E8E93.toInt(), 160f)
                }
                android.location.LocationManager.MODE_CHANGED_ACTION -> {
                    val on = try { (getSystemService(LOCATION_SERVICE) as android.location.LocationManager).isLocationEnabled } catch (_: Exception) { return }
                    if (on != lastLocation) {
                        lastLocation = on
                        status("location", if (on) "Standort an" else "Standort aus", SGlyph.LOCATION,
                            if (on) 0xFF0A84FF.toInt() else 0xFF8E8E93.toInt(), 150f)
                    }
                }
                "android.hardware.usb.action.USB_STATE" -> {
                    val connected = intent.getBooleanExtra("connected", false)
                    val data = intent.getBooleanExtra("mtp", false) || intent.getBooleanExtra("ptp", false) || intent.getBooleanExtra("adb", false)
                    if (connected && data) status("usb", "USB verbunden", SGlyph.USB, Color.WHITE, 160f)
                }
                AudioManager.ACTION_HEADSET_PLUG -> {
                    if (intent.getIntExtra("state", 0) == 1) status("jack", "Kopfhörer", SGlyph.HEADPHONES, Color.WHITE, 140f)
                }
            }
        }
    }

    private val pkgStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart ?: return
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            val pm = packageManager
            when (intent.action) {
                Intent.ACTION_PACKAGE_ADDED -> {
                    val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: Exception) { return }
                    val icon = try { drawableToBitmap(pm.getApplicationIcon(pkg)) } catch (_: Exception) { null }
                    status("apps", if (replacing) "$name aktualisiert" else "$name installiert", SGlyph.DOWNLOAD, 0xFF0A84FF.toInt(),
                        (110f + name.length * 7.5f).coerceIn(170f, 300f), icon)
                }
                Intent.ACTION_PACKAGE_FULLY_REMOVED -> status("apps", "App entfernt", SGlyph.TRASH, 0xFFFF453A.toInt(), 150f)
            }
        }
    }

    private fun drawableToBitmap(d: android.graphics.drawable.Drawable): android.graphics.Bitmap {
        val sz = (48 * density).toInt()
        val b = android.graphics.Bitmap.createBitmap(sz, sz, android.graphics.Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, sz, sz)
        d.draw(android.graphics.Canvas(b))
        return b
    }

    private val rotationObserver = object : android.database.ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            val v = try { android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION) } catch (_: Exception) { return }
            if (lastRotation != null && lastRotation != v)
                status("rotation", if (v == 1) "Drehen an" else "Drehsperre", SGlyph.ROTATE, if (v == 1) Color.WHITE else 0xFFFF9F0A.toInt(), 150f)
            lastRotation = v
        }
    }

    /** Minütlich: Termine & Bildschirmzeit */
    private val minuteTick = object : Runnable {
        override fun run() {
            checkCalendar()
            checkScreenTime()
            handler.postDelayed(this, 60_000L)
        }
    }

    private fun registerStatusWatchers() {
        lastNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        lastLocation = try { (getSystemService(LOCATION_SERVICE) as android.location.LocationManager).isLocationEnabled } catch (_: Exception) { null }
        lastRotation = try { android.provider.Settings.System.getInt(contentResolver, android.provider.Settings.System.ACCELEROMETER_ROTATION) } catch (_: Exception) { null }
        unlockStart = System.currentTimeMillis()
        val f = IntentFilter().apply {
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
            addAction("android.nfc.action.ADAPTER_STATE_CHANGED")
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(android.location.LocationManager.MODE_CHANGED_ACTION)
            addAction("android.hardware.usb.action.USB_STATE")
            addAction(AudioManager.ACTION_HEADSET_PLUG)
        }
        val pf = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
            addDataScheme("package")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(statusReceiver, f, Context.RECEIVER_EXPORTED)
                registerReceiver(pkgStatusReceiver, pf, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(statusReceiver, f)
                registerReceiver(pkgStatusReceiver, pf)
            }
        } catch (_: Exception) {}
        try {
            contentResolver.registerContentObserver(
                android.provider.Settings.System.getUriFor(android.provider.Settings.System.ACCELEROMETER_ROTATION), false, rotationObserver)
        } catch (_: Exception) {}
        handler.postDelayed(minuteTick, 60_000L)
    }

    private fun unregisterStatusWatchers() {
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(pkgStatusReceiver) } catch (_: Exception) {}
        try { contentResolver.unregisterContentObserver(rotationObserver) } catch (_: Exception) {}
        handler.removeCallbacks(minuteTick)
    }

    private fun onUnlockedChecks() {
        unlockStart = System.currentTimeMillis()
        screenTimeShown = false
        // Speicher höchstens alle 12 h prüfen
        if (System.currentTimeMillis() - lastStorageCheck > 12 * 3600_000L) {
            lastStorageCheck = System.currentTimeMillis()
            try {
                val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
                val free = st.availableBytes.toDouble() / st.totalBytes.coerceAtLeast(1)
                if (free < 0.05) handler.postDelayed({
                    status("storage", "Speicher fast voll", SGlyph.STORAGE, 0xFFFF9F0A.toInt(), 200f)
                }, 2500)
            } catch (_: Exception) {}
        }
    }

    private fun checkScreenTime() {
        if (screenOff || locked || screenTimeShown || "screentime" !in cfg.statusOn) return
        val mins = (System.currentTimeMillis() - unlockStart) / 60_000L
        if (mins >= 60) {
            screenTimeShown = true
            status("screentime", "1 Std. am Handy", SGlyph.CLOCK, 0xFF64D2FF.toInt(), 170f)
        }
    }

    private fun checkCalendar() {
        if ("calendar" !in cfg.statusOn || screenOff) return
        if (checkSelfPermission(android.Manifest.permission.READ_CALENDAR) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        Thread {
            try {
                val now = System.currentTimeMillis()
                val uri = android.provider.CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                    android.content.ContentUris.appendId(it, now + 9 * 60_000L)
                    android.content.ContentUris.appendId(it, now + 11 * 60_000L)
                }.build()
                contentResolver.query(uri, arrayOf(
                    android.provider.CalendarContract.Instances.EVENT_ID,
                    android.provider.CalendarContract.Instances.TITLE,
                    android.provider.CalendarContract.Instances.BEGIN,
                    android.provider.CalendarContract.Instances.ALL_DAY,
                ), null, null, null)?.use { cur ->
                    while (cur.moveToNext()) {
                        val id = cur.getLong(0)
                        val title = cur.getString(1) ?: "Termin"
                        val begin = cur.getLong(2)
                        if (cur.getInt(3) == 1 || begin < now + 8 * 60_000L) continue
                        if (!shownEvents.add(id * 31 + begin)) continue
                        val mins = ((begin - now) / 60_000L).coerceAtLeast(1)
                        val t = "$title · in $mins min"
                        status("calendar", t, SGlyph.CALENDAR, 0xFFFF453A.toInt(), (90f + t.length * 7.2f).coerceIn(180f, 320f))
                        break
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun registerSystemReceivers() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(ACTION_VOLUME)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        lastFocus = try {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).currentInterruptionFilter !=
                NotificationManager.INTERRUPTION_FILTER_ALL
        } catch (_: Exception) { false }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(systemReceiver, f, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(systemReceiver, f)
        }

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(screenReceiver, screenFilter)
        }
    }

    // ------------------------------------------------------------------
    // Wecker
    // ------------------------------------------------------------------

    private var lastAlarmText: String? = null

    private fun updateNextAlarm(announce: Boolean) {
        val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        val next = try { am.nextAlarmClock } catch (_: Exception) { null }
        val text = next?.let {
            android.text.format.DateFormat.getTimeFormat(this).format(java.util.Date(it.triggerTime))
        }
        controller?.nextAlarm = text
        if (announce && text != null && text != lastAlarmText) controller?.showTransient(AlarmContent(text))
        lastAlarmText = text
    }

    // ------------------------------------------------------------------
    // Internet
    // ------------------------------------------------------------------

    private var netCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var netKind = -1

    private fun registerNetwork() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(n: android.net.Network, caps: android.net.NetworkCapabilities) {
                val kind = when {
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> 1
                    caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
                    else -> 1
                }
                handler.post { changeNet(kind) }
            }
            override fun onLost(n: android.net.Network) {
                handler.postDelayed({
                    if (cm.activeNetwork == null) changeNet(0)
                }, 1500)
            }
        }
        try {
            cm.registerDefaultNetworkCallback(cb, handler)
            netCallback = cb
        } catch (_: Exception) {}
    }

    private fun changeNet(kind: Int) {
        val old = netKind
        netKind = kind
        if (old != -1 && old != kind) controller?.showTransient(NetworkContent(kind))
    }

    private fun unregisterNetwork() {
        val cb = netCallback ?: return
        try { (getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(cb) } catch (_: Exception) {}
        netCallback = null
    }

    companion object {
        private const val ACTION_VOLUME = "android.media.VOLUME_CHANGED_ACTION"
        private const val EXTRA_VOLUME_STREAM = "android.media.EXTRA_VOLUME_STREAM_TYPE"
        private const val EXTRA_VOLUME_VALUE = "android.media.EXTRA_VOLUME_STREAM_VALUE"
        private const val EXTRA_VOLUME_PREV = "android.media.EXTRA_PREV_VOLUME_STREAM_VALUE"
        private const val ACTION_BATTERY_LEVEL = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}
