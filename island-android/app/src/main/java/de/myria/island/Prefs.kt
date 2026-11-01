package de.myria.island

import android.content.Context
import android.content.SharedPreferences

/** Alle Einstellungen. Standardwerte = Apple-Look. */
data class IslandConfig(
    // Design
    val design: Int = DESIGN_APPLE,
    val uiLook: Int = LOOK_APPLE,

    // Allgemein
    val enabled: Boolean = true,
    val alwaysVisible: Boolean = false,
    val idleTouchThrough: Boolean = true,
    val hideLandscape: Boolean = true,
    val showOnLockscreen: Boolean = true,
    val idleContent: Int = IDLE_NONE,

    // Größe & Position
    val widthDp: Int = 112,
    val heightDp: Int = 34,
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
    val autoCamera: Boolean = true,
    val expandedWidthPct: Int = 100,
    val expandedRadiusDp: Int = 44,
    val compactExtraDp: Int = 0,
    val compactRoundPct: Int = 100,

    // Erscheinungsbild
    val style: Int = STYLE_BLACK,
    val glow: Boolean = true,
    val glowStrength: Int = 60,
    val accentMode: Int = ACCENT_COVER,
    val accentColor: Int = 0xFFFF6AA2.toInt(),
    val fontScale: Int = 1,
    val fontStyle: Int = FONT_SYSTEM,
    val shadow: Boolean = false,
    val waveStyle: Int = WAVE_BARS,
    val progressStyle: Int = PROGRESS_LINE,
    val controlStyle: Int = CONTROL_PLAIN,

    // Liquid Glass
    val glassOpacity: Int = 55,
    val glassShine: Int = 70,
    val glassBorder: Boolean = true,
    val glassBorderWidth: Int = 10,
    val glassRainbow: Boolean = true,
    val glassTint: Int = 20,

    // Animation
    val bounce: Int = 45,
    val speed: Int = 50,
    val squish: Boolean = true,
    val reduceMotion: Boolean = false,

    // Live-Aktivitäten
    val showMusic: Boolean = true,
    val showNotifications: Boolean = true,
    val showCharging: Boolean = true,
    val chargingPersistent: Boolean = false,
    val showLowBattery: Boolean = true,
    val showRinger: Boolean = true,
    val showTimer: Boolean = true,
    val showCalls: Boolean = true,
    val showNav: Boolean = true,
    val showProgress: Boolean = true,
    val showDevices: Boolean = true,
    val privacyCamera: Boolean = true,
    val privacyMic: Boolean = true,
    val privacyLocation: Boolean = true,
    val splitBubble: Boolean = true,
    val showUnlock: Boolean = true,
    val showVolume: Boolean = false,
    val showTorch: Boolean = true,
    val showFocus: Boolean = true,
    val showAirplane: Boolean = true,

    // Musik
    val waveform: Boolean = true,
    val waveformCover: Boolean = true,
    val musicSeek: Boolean = true,
    val musicAppIcon: Boolean = true,
    val pausedLingerSec: Int = 4,
    val autoExpandTrack: Boolean = false,

    // Mitteilungen
    val notifSeconds: Int = 4,
    val notifSilent: Boolean = false,
    val notifDnd: Boolean = false,
    val notifHideContent: Boolean = false,
    val notifActions: Boolean = true,
    val notifAutoExpand: Boolean = false,
    val excludedApps: Set<String> = emptySet(),

    // Neu in Isla 4
    val animStyle: Int = ANIM_APPLE,
    val swipeHorizontal: Int = SWIPE_SWITCH,
    val doubleTapAction: Int = DOUBLE_PLAYPAUSE,
    val idleLongPress: Int = IDLE_LP_TIMER,
    val vinyl: Boolean = false,
    val chargeDetails: Boolean = true,
    val tempWarning: Boolean = true,
    val batteryRing: Boolean = false,
    val showNetwork: Boolean = false,
    val showAlarm: Boolean = true,
    val notifQueue: Boolean = true,
    val notifHistory: Boolean = true,
    val quietHours: Boolean = false,
    val quietStart: Int = 22,
    val quietEnd: Int = 7,
    val hiddenInApps: Set<String> = emptySet(),
    val powerSaveReduce: Boolean = true,
    val appFont: Int = -1,
    val timerMinutes: Int = 5,

    // Verhalten
    val tapExpands: Boolean = false,
    val swipeDownExpands: Boolean = true,
    val autoCollapseSec: Int = 8,
    val haptics: Boolean = true,
    val hapticStrength: Int = 1,
) {
    /** 1.0 = kein Nachschwingen, 0.45 = sehr bouncy */
    val dampingRatio: Float get() = if (reduceMotion) 1f else 1f - bounce / 100f * 0.55f
    val stiffness: Float get() = 160f + speed / 100f * 560f
    val textScale: Float get() = when (fontScale) { 0 -> 0.9f; 2 -> 1.12f; else -> 1f }

    companion object {
        const val DESIGN_APPLE = 0
        const val DESIGN_ANDROID = 1
        const val DESIGN_ONEUI = 2
        const val DESIGN_NOTHING = 3
        const val DESIGN_CUSTOM = 4
        const val DESIGN_GLASS = 5

        const val LOOK_APPLE = 0
        const val LOOK_ANDROID = 1
        const val LOOK_ONEUI = 2
        const val LOOK_NOTHING = 3
        const val LOOK_GLASS = 4

        const val STYLE_BLACK = 0
        const val STYLE_GLASS = 1
        const val STYLE_TINT = 2
        const val STYLE_MATERIAL = 3
        const val STYLE_GRAPHITE = 4
        const val STYLE_OUTLINE = 5

        const val ACCENT_COVER = 0
        const val ACCENT_CUSTOM = 1
        const val ACCENT_WHITE = 2
        const val ACCENT_SYSTEM = 3

        const val WAVE_BARS = 0
        const val WAVE_DOTS = 1
        const val WAVE_LINE = 2

        const val PROGRESS_LINE = 0
        const val PROGRESS_SQUIGGLE = 1
        const val PROGRESS_DOTS = 2

        const val CONTROL_PLAIN = 0
        const val CONTROL_FILLED = 1
        const val CONTROL_CIRCLE = 2
        const val CONTROL_OUTLINE = 3

        const val FONT_SYSTEM = 0
        const val FONT_GOOGLE = 1
        const val FONT_CONDENSED = 2
        const val FONT_MONO = 3
        const val FONT_SERIF = 4

        const val IDLE_NONE = 0
        const val IDLE_TIME = 1
        const val IDLE_BATTERY = 2
        const val IDLE_BOTH = 3
        const val IDLE_DATE = 4
        const val IDLE_ALARM = 5
        const val IDLE_DATE_BATTERY = 6

        const val ANIM_APPLE = 0
        const val ANIM_ANDROID = 1
        const val ANIM_ONEUI = 2
        const val ANIM_NOTHING = 3
        const val ANIM_JELLY = 4

        const val SWIPE_OFF = 0
        const val SWIPE_SWITCH = 1
        const val SWIPE_TRACK = 2

        const val DOUBLE_NONE = 0
        const val DOUBLE_PLAYPAUSE = 1
        const val DOUBLE_OPEN = 2
        const val DOUBLE_EXPAND = 3
        const val DOUBLE_TORCH = 4

        const val IDLE_LP_NONE = 0
        const val IDLE_LP_TIMER = 1
        const val IDLE_LP_STOPWATCH = 2
        const val IDLE_LP_TORCH = 3
        const val IDLE_LP_SETTINGS = 4

        val ACCENT_CHOICES = intArrayOf(
            0xFFFF6AA2.toInt(), 0xFFFF453A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(),
            0xFF30D158.toInt(), 0xFF64D2FF.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(),
        )
    }
}

object Prefs {
    private const val NAME = "island"

    const val DESIGN = "design"
    const val UI_LOOK = "ui_look"

    const val ENABLED = "enabled"
    const val ALWAYS = "always_visible"
    const val IDLE_TOUCH_THROUGH = "idle_touch_through"
    const val HIDE_LANDSCAPE = "hide_landscape"
    const val LOCKSCREEN = "lockscreen"
    const val IDLE_CONTENT = "idle_content"

    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val OFFSET_X = "offset_x"
    const val OFFSET_Y = "offset_y"
    const val AUTO_CAMERA = "auto_camera"
    const val EXPANDED_WIDTH = "expanded_width"
    const val EXPANDED_RADIUS = "expanded_radius"
    const val COMPACT_EXTRA = "compact_extra"
    const val COMPACT_ROUND = "compact_round"

    const val STYLE = "style"
    const val GLOW = "glow"
    const val GLOW_STRENGTH = "glow_strength"
    const val ACCENT_MODE = "accent_mode"
    const val ACCENT_COLOR = "accent_color"
    const val FONT_SCALE = "font_scale"
    const val FONT_STYLE = "font_style"
    const val SHADOW = "shadow"
    const val WAVE_STYLE = "wave_style"
    const val PROGRESS_STYLE = "progress_style"
    const val CONTROL_STYLE = "control_style"

    const val GLASS_OPACITY = "glass_opacity"
    const val GLASS_SHINE = "glass_shine"
    const val GLASS_BORDER = "glass_border"
    const val GLASS_BORDER_WIDTH = "glass_border_width"
    const val GLASS_RAINBOW = "glass_rainbow"
    const val GLASS_TINT = "glass_tint"

    const val BOUNCE = "bounce"
    const val SPEED = "speed"
    const val SQUISH = "squish"
    const val REDUCE_MOTION = "reduce_motion"

    const val MUSIC = "music"
    const val NOTIFS = "notifications"
    const val CHARGING = "charging"
    const val CHARGING_PERSISTENT = "charging_persistent"
    const val LOW_BATTERY = "low_battery"
    const val RINGER = "ringer"
    const val TIMER = "timer"
    const val CALLS = "calls"
    const val NAV = "nav"
    const val PROGRESS = "progress"
    const val DEVICES = "devices"
    const val PRIV_CAMERA = "priv_camera"
    const val PRIV_MIC = "priv_mic"
    const val PRIV_LOCATION = "priv_location"
    const val SPLIT = "split_bubble"
    const val UNLOCK = "unlock"
    const val VOLUME = "volume"
    const val TORCH = "torch"
    const val FOCUS = "focus"
    const val AIRPLANE = "airplane"

    const val WAVEFORM = "waveform"
    const val WAVEFORM_COVER = "waveform_cover"
    const val MUSIC_SEEK = "music_seek"
    const val MUSIC_APP_ICON = "music_app_icon"
    const val PAUSED_LINGER = "paused_linger"
    const val AUTO_EXPAND_TRACK = "auto_expand_track"

    const val NOTIF_SECONDS = "notif_seconds"
    const val NOTIF_SILENT = "notif_silent"
    const val NOTIF_DND = "notif_dnd"
    const val NOTIF_HIDE_CONTENT = "notif_hide_content"
    const val NOTIF_ACTIONS = "notif_actions"
    const val NOTIF_AUTO_EXPAND = "notif_auto_expand"
    const val EXCLUDED = "excluded_apps"

    const val ANIM_STYLE = "anim_style"
    const val SWIPE_H = "swipe_horizontal"
    const val DOUBLE_TAP = "double_tap"
    const val IDLE_LP = "idle_long_press"
    const val VINYL = "vinyl"
    const val CHARGE_DETAILS = "charge_details"
    const val TEMP_WARNING = "temp_warning"
    const val BATTERY_RING = "battery_ring"
    const val NETWORK = "network"
    const val ALARM = "alarm"
    const val NOTIF_QUEUE = "notif_queue"
    const val NOTIF_HISTORY = "notif_history"
    const val QUIET = "quiet_hours"
    const val QUIET_START = "quiet_start"
    const val QUIET_END = "quiet_end"
    const val HIDDEN_IN_APPS = "hidden_in_apps"
    const val POWER_SAVE = "power_save_reduce"
    const val APP_FONT = "app_font"
    const val TIMER_MINUTES = "timer_minutes"
    const val ONBOARDED = "onboarded"

    const val TAP_EXPANDS = "tap_expands_v2"
    const val SWIPE_DOWN = "swipe_down_expands"
    const val AUTO_COLLAPSE = "auto_collapse"
    const val HAPTICS = "haptics"
    const val HAPTIC_STRENGTH = "haptic_strength"

    fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(c: Context): IslandConfig {
        val p = sp(c)
        val d = IslandConfig()
        fun b(k: String, v: Boolean) = p.getBoolean(k, v)
        fun i(k: String, v: Int) = try { p.getInt(k, v) } catch (_: ClassCastException) { v }
        return IslandConfig(
            design = i(DESIGN, d.design),
            uiLook = i(UI_LOOK, d.uiLook),

            enabled = b(ENABLED, d.enabled),
            alwaysVisible = b(ALWAYS, d.alwaysVisible),
            idleTouchThrough = b(IDLE_TOUCH_THROUGH, d.idleTouchThrough),
            hideLandscape = b(HIDE_LANDSCAPE, d.hideLandscape),
            showOnLockscreen = b(LOCKSCREEN, d.showOnLockscreen),
            idleContent = i(IDLE_CONTENT, d.idleContent),

            widthDp = i(WIDTH, d.widthDp),
            heightDp = i(HEIGHT, d.heightDp),
            offsetXDp = i(OFFSET_X, d.offsetXDp),
            offsetYDp = i(OFFSET_Y, d.offsetYDp),
            autoCamera = b(AUTO_CAMERA, d.autoCamera),
            expandedWidthPct = i(EXPANDED_WIDTH, d.expandedWidthPct),
            expandedRadiusDp = i(EXPANDED_RADIUS, d.expandedRadiusDp),
            compactExtraDp = i(COMPACT_EXTRA, d.compactExtraDp),
            compactRoundPct = i(COMPACT_ROUND, d.compactRoundPct),

            style = i(STYLE, d.style),
            glow = b(GLOW, d.glow),
            glowStrength = i(GLOW_STRENGTH, d.glowStrength),
            accentMode = i(ACCENT_MODE, d.accentMode),
            accentColor = i(ACCENT_COLOR, d.accentColor),
            fontScale = i(FONT_SCALE, d.fontScale),
            fontStyle = i(FONT_STYLE, d.fontStyle),
            shadow = b(SHADOW, d.shadow),
            waveStyle = i(WAVE_STYLE, d.waveStyle),
            progressStyle = i(PROGRESS_STYLE, d.progressStyle),
            controlStyle = i(CONTROL_STYLE, d.controlStyle),

            glassOpacity = i(GLASS_OPACITY, d.glassOpacity),
            glassShine = i(GLASS_SHINE, d.glassShine),
            glassBorder = b(GLASS_BORDER, d.glassBorder),
            glassBorderWidth = i(GLASS_BORDER_WIDTH, d.glassBorderWidth),
            glassRainbow = b(GLASS_RAINBOW, d.glassRainbow),
            glassTint = i(GLASS_TINT, d.glassTint),

            bounce = i(BOUNCE, d.bounce),
            speed = i(SPEED, d.speed),
            squish = b(SQUISH, d.squish),
            reduceMotion = b(REDUCE_MOTION, d.reduceMotion),

            showMusic = b(MUSIC, d.showMusic),
            showNotifications = b(NOTIFS, d.showNotifications),
            showCharging = b(CHARGING, d.showCharging),
            chargingPersistent = b(CHARGING_PERSISTENT, d.chargingPersistent),
            showLowBattery = b(LOW_BATTERY, d.showLowBattery),
            showRinger = b(RINGER, d.showRinger),
            showTimer = b(TIMER, d.showTimer),
            showCalls = b(CALLS, d.showCalls),
            showNav = b(NAV, d.showNav),
            showProgress = b(PROGRESS, d.showProgress),
            showDevices = b(DEVICES, d.showDevices),
            privacyCamera = b(PRIV_CAMERA, d.privacyCamera),
            privacyMic = b(PRIV_MIC, d.privacyMic),
            privacyLocation = b(PRIV_LOCATION, d.privacyLocation),
            splitBubble = b(SPLIT, d.splitBubble),
            showUnlock = b(UNLOCK, d.showUnlock),
            showVolume = b(VOLUME, d.showVolume),
            showTorch = b(TORCH, d.showTorch),
            showFocus = b(FOCUS, d.showFocus),
            showAirplane = b(AIRPLANE, d.showAirplane),

            waveform = b(WAVEFORM, d.waveform),
            waveformCover = b(WAVEFORM_COVER, d.waveformCover),
            musicSeek = b(MUSIC_SEEK, d.musicSeek),
            musicAppIcon = b(MUSIC_APP_ICON, d.musicAppIcon),
            pausedLingerSec = i(PAUSED_LINGER, d.pausedLingerSec),
            autoExpandTrack = b(AUTO_EXPAND_TRACK, d.autoExpandTrack),

            notifSeconds = i(NOTIF_SECONDS, d.notifSeconds),
            notifSilent = b(NOTIF_SILENT, d.notifSilent),
            notifDnd = b(NOTIF_DND, d.notifDnd),
            notifHideContent = b(NOTIF_HIDE_CONTENT, d.notifHideContent),
            notifActions = b(NOTIF_ACTIONS, d.notifActions),
            notifAutoExpand = b(NOTIF_AUTO_EXPAND, d.notifAutoExpand),
            excludedApps = try { p.getStringSet(EXCLUDED, null)?.toSet() } catch (_: ClassCastException) { null } ?: emptySet(),

            animStyle = i(ANIM_STYLE, d.animStyle),
            swipeHorizontal = i(SWIPE_H, d.swipeHorizontal),
            doubleTapAction = i(DOUBLE_TAP, d.doubleTapAction),
            idleLongPress = i(IDLE_LP, d.idleLongPress),
            vinyl = b(VINYL, d.vinyl),
            chargeDetails = b(CHARGE_DETAILS, d.chargeDetails),
            tempWarning = b(TEMP_WARNING, d.tempWarning),
            batteryRing = b(BATTERY_RING, d.batteryRing),
            showNetwork = b(NETWORK, d.showNetwork),
            showAlarm = b(ALARM, d.showAlarm),
            notifQueue = b(NOTIF_QUEUE, d.notifQueue),
            notifHistory = b(NOTIF_HISTORY, d.notifHistory),
            quietHours = b(QUIET, d.quietHours),
            quietStart = i(QUIET_START, d.quietStart),
            quietEnd = i(QUIET_END, d.quietEnd),
            hiddenInApps = try { p.getStringSet(HIDDEN_IN_APPS, null)?.toSet() } catch (_: ClassCastException) { null } ?: emptySet(),
            powerSaveReduce = b(POWER_SAVE, d.powerSaveReduce),
            appFont = i(APP_FONT, d.appFont),
            timerMinutes = i(TIMER_MINUTES, d.timerMinutes),

            tapExpands = b(TAP_EXPANDS, d.tapExpands),
            swipeDownExpands = b(SWIPE_DOWN, d.swipeDownExpands),
            autoCollapseSec = i(AUTO_COLLAPSE, d.autoCollapseSec),
            haptics = b(HAPTICS, d.haptics),
            hapticStrength = i(HAPTIC_STRENGTH, d.hapticStrength),
        )
    }

    /**
     * Design-Vorlage anwenden. Schreibt alle Look-Werte – danach kann man
     * trotzdem alles einzeln anpassen. „Custom“ lässt alles, wie es ist.
     */
    fun applyDesign(c: Context, design: Int) {
        val e = sp(c).edit().putInt(DESIGN, design)
        fun look(style: Int, accentMode: Int, accent: Int?, radius: Int, round: Int, bounce: Int, speed: Int,
                 wave: Int, progress: Int, control: Int, font: Int, ui: Int, glow: Boolean, anim: Int) {
            e.putInt(ANIM_STYLE, anim)
            e.putInt(STYLE, style).putInt(ACCENT_MODE, accentMode)
            if (accent != null) e.putInt(ACCENT_COLOR, accent)
            e.putInt(EXPANDED_RADIUS, radius).putInt(COMPACT_ROUND, round)
                .putInt(BOUNCE, bounce).putInt(SPEED, speed)
                .putInt(WAVE_STYLE, wave).putInt(PROGRESS_STYLE, progress)
                .putInt(CONTROL_STYLE, control).putInt(FONT_STYLE, font)
                .putInt(UI_LOOK, ui).putBoolean(GLOW, glow)
        }
        val C = IslandConfig
        when (design) {
            C.DESIGN_APPLE -> look(C.STYLE_BLACK, C.ACCENT_COVER, null, 44, 100, 45, 50,
                C.WAVE_BARS, C.PROGRESS_LINE, C.CONTROL_PLAIN, Fonts.INTER, C.LOOK_APPLE, true, C.ANIM_APPLE)
            C.DESIGN_ANDROID -> look(C.STYLE_MATERIAL, C.ACCENT_SYSTEM, null, 32, 100, 15, 62,
                C.WAVE_LINE, C.PROGRESS_SQUIGGLE, C.CONTROL_FILLED, C.FONT_GOOGLE, C.LOOK_ANDROID, false, C.ANIM_ANDROID)
            C.DESIGN_ONEUI -> look(C.STYLE_GRAPHITE, C.ACCENT_CUSTOM, 0xFF3E91FF.toInt(), 28, 100, 28, 48,
                C.WAVE_BARS, C.PROGRESS_LINE, C.CONTROL_CIRCLE, C.FONT_SYSTEM, C.LOOK_ONEUI, false, C.ANIM_ONEUI)
            C.DESIGN_NOTHING -> look(C.STYLE_OUTLINE, C.ACCENT_CUSTOM, 0xFFD71921.toInt(), 20, 72, 0, 72,
                C.WAVE_DOTS, C.PROGRESS_DOTS, C.CONTROL_OUTLINE, 7, C.LOOK_NOTHING, false, C.ANIM_NOTHING)
            C.DESIGN_GLASS -> {
                look(C.STYLE_GLASS, C.ACCENT_COVER, null, 40, 100, 40, 52,
                    C.WAVE_BARS, C.PROGRESS_LINE, C.CONTROL_PLAIN, Fonts.INTER, C.LOOK_GLASS, true, C.ANIM_APPLE)
                e.putInt(GLASS_OPACITY, 45).putInt(GLASS_SHINE, 80).putBoolean(GLASS_BORDER, true).putBoolean(GLASS_RAINBOW, true)
            }
            else -> {}
        }
        e.apply()
    }

    fun setExcluded(c: Context, pkg: String, excluded: Boolean) = setInSet(c, EXCLUDED, pkg, excluded)

    fun setInSet(c: Context, key: String, pkg: String, inSet: Boolean) {
        val cur = sp(c).getStringSet(key, null)?.toMutableSet() ?: mutableSetOf()
        if (inSet) cur.add(pkg) else cur.remove(pkg)
        sp(c).edit().putStringSet(key, cur).apply()
    }

    /** Alle Einstellungen als JSON (für Export). */
    fun export(c: Context): String {
        val o = org.json.JSONObject()
        for ((k, v) in sp(c).all) {
            when (v) {
                is Set<*> -> o.put(k, org.json.JSONArray(v.map { it.toString() }))
                is Int -> o.put(k, org.json.JSONObject().put("i", v))
                is Long -> o.put(k, org.json.JSONObject().put("l", v))
                is Float -> o.put(k, org.json.JSONObject().put("f", v.toDouble()))
                else -> o.put(k, v)
            }
        }
        return o.put("_atoll", 1).toString()
    }

    /** JSON wieder einlesen. @return true wenn gültig */
    fun import(c: Context, json: String): Boolean {
        return try {
            val o = org.json.JSONObject(json.trim())
            if (!o.has("_atoll")) return false
            val e = sp(c).edit().clear()
            for (k in o.keys()) {
                if (k == "_atoll") continue
                when (val v = o.get(k)) {
                    is org.json.JSONArray -> e.putStringSet(k, (0 until v.length()).map { v.getString(it) }.toSet())
                    is org.json.JSONObject -> when {
                        v.has("i") -> e.putInt(k, v.getInt("i"))
                        v.has("l") -> e.putLong(k, v.getLong("l"))
                        v.has("f") -> e.putFloat(k, v.getDouble("f").toFloat())
                    }
                    is Boolean -> e.putBoolean(k, v)
                    is String -> e.putString(k, v)
                    is Int -> e.putInt(k, v)
                }
            }
            e.apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun resetGeometry(c: Context) {
        sp(c).edit()
            .remove(WIDTH).remove(HEIGHT).remove(OFFSET_X).remove(OFFSET_Y).remove(AUTO_CAMERA)
            .remove(EXPANDED_WIDTH).remove(EXPANDED_RADIUS).remove(COMPACT_EXTRA).remove(COMPACT_ROUND)
            .apply()
    }

    fun resetGlass(c: Context) {
        sp(c).edit().remove(GLASS_OPACITY).remove(GLASS_SHINE).remove(GLASS_BORDER)
            .remove(GLASS_BORDER_WIDTH).remove(GLASS_RAINBOW).remove(GLASS_TINT).apply()
    }

    fun resetAll(c: Context) {
        sp(c).edit().clear().apply()
    }
}
