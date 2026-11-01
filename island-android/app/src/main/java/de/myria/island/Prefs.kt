package de.myria.island

import android.content.Context
import android.content.SharedPreferences

/** Alle Einstellungen der Island. Standardwerte = Apple-Look. */
data class IslandConfig(
    val enabled: Boolean = true,
    val alwaysVisible: Boolean = false,
    val widthDp: Int = 112,
    val heightDp: Int = 34,
    val offsetXDp: Int = 0,
    val offsetYDp: Int = 0,
    val autoCamera: Boolean = true,
    val style: Int = STYLE_BLACK,
    val glow: Boolean = true,
    val bounce: Int = 45,
    val speed: Int = 50,
    val showMusic: Boolean = true,
    val showNotifications: Boolean = true,
    val showCharging: Boolean = true,
    val showRinger: Boolean = true,
    val chargingPersistent: Boolean = false,
    val notifSeconds: Int = 4,
    val haptics: Boolean = true,
    val hideLandscape: Boolean = true,
    val idleTouchThrough: Boolean = true,
    val tapExpands: Boolean = true,
    val autoExpandTrack: Boolean = false,
    val waveform: Boolean = true,
) {
    /** 1.0 = kein Nachschwingen, 0.45 = sehr bouncy */
    val dampingRatio: Float get() = 1f - bounce / 100f * 0.55f
    val stiffness: Float get() = 160f + speed / 100f * 560f

    companion object {
        const val STYLE_BLACK = 0
        const val STYLE_GLASS = 1
    }
}

object Prefs {
    private const val NAME = "island"

    const val ENABLED = "enabled"
    const val ALWAYS = "always_visible"
    const val WIDTH = "width"
    const val HEIGHT = "height"
    const val OFFSET_X = "offset_x"
    const val OFFSET_Y = "offset_y"
    const val AUTO_CAMERA = "auto_camera"
    const val STYLE = "style"
    const val GLOW = "glow"
    const val BOUNCE = "bounce"
    const val SPEED = "speed"
    const val MUSIC = "music"
    const val NOTIFS = "notifications"
    const val CHARGING = "charging"
    const val RINGER = "ringer"
    const val CHARGING_PERSISTENT = "charging_persistent"
    const val NOTIF_SECONDS = "notif_seconds"
    const val HAPTICS = "haptics"
    const val HIDE_LANDSCAPE = "hide_landscape"
    const val IDLE_TOUCH_THROUGH = "idle_touch_through"
    const val TAP_EXPANDS = "tap_expands"
    const val AUTO_EXPAND_TRACK = "auto_expand_track"
    const val WAVEFORM = "waveform"

    fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun load(c: Context): IslandConfig {
        val p = sp(c)
        val d = IslandConfig()
        return IslandConfig(
            enabled = p.getBoolean(ENABLED, d.enabled),
            alwaysVisible = p.getBoolean(ALWAYS, d.alwaysVisible),
            widthDp = p.getInt(WIDTH, d.widthDp),
            heightDp = p.getInt(HEIGHT, d.heightDp),
            offsetXDp = p.getInt(OFFSET_X, d.offsetXDp),
            offsetYDp = p.getInt(OFFSET_Y, d.offsetYDp),
            autoCamera = p.getBoolean(AUTO_CAMERA, d.autoCamera),
            style = p.getInt(STYLE, d.style),
            glow = p.getBoolean(GLOW, d.glow),
            bounce = p.getInt(BOUNCE, d.bounce),
            speed = p.getInt(SPEED, d.speed),
            showMusic = p.getBoolean(MUSIC, d.showMusic),
            showNotifications = p.getBoolean(NOTIFS, d.showNotifications),
            showCharging = p.getBoolean(CHARGING, d.showCharging),
            showRinger = p.getBoolean(RINGER, d.showRinger),
            chargingPersistent = p.getBoolean(CHARGING_PERSISTENT, d.chargingPersistent),
            notifSeconds = p.getInt(NOTIF_SECONDS, d.notifSeconds),
            haptics = p.getBoolean(HAPTICS, d.haptics),
            hideLandscape = p.getBoolean(HIDE_LANDSCAPE, d.hideLandscape),
            idleTouchThrough = p.getBoolean(IDLE_TOUCH_THROUGH, d.idleTouchThrough),
            tapExpands = p.getBoolean(TAP_EXPANDS, d.tapExpands),
            autoExpandTrack = p.getBoolean(AUTO_EXPAND_TRACK, d.autoExpandTrack),
            waveform = p.getBoolean(WAVEFORM, d.waveform),
        )
    }

    fun resetGeometry(c: Context) {
        sp(c).edit()
            .remove(WIDTH).remove(HEIGHT).remove(OFFSET_X).remove(OFFSET_Y).remove(AUTO_CAMERA)
            .apply()
    }
}
