package de.myria.island

import android.os.Handler
import android.os.Looper
import de.myria.island.IslandView.Mode

/**
 * Entscheidet, was die Island zeigt. Wird vom echten Overlay UND von der
 * Vorschau in den Einstellungen benutzt – darum verhält sich die Vorschau 1:1 gleich.
 */
class IslandController(
    private val view: IslandView,
    private val host: Host,
) : IslandView.Listener {

    interface Host {
        fun haptic(strong: Boolean)
        fun onModeChanged(mode: Mode) {}
    }

    var cfg: IslandConfig = IslandConfig()
        set(v) {
            field = v
            view.cfg = v
            refresh()
        }

    /** z.B. Querformat → komplett ausblenden */
    var suppressed = false
        set(v) {
            if (field != v) {
                field = v
                refresh()
            }
        }

    private var music: MusicInfo? = null
    private var charging: Content.Charging? = null
    private var transient: Content? = null
    private var expanded = false

    private val handler = Handler(Looper.getMainLooper())
    private val clearTransient = Runnable {
        transient = null
        refresh()
    }
    private val autoCollapse = Runnable {
        if (expanded) {
            expanded = false
            refresh()
        }
    }

    init {
        view.listener = this
    }

    fun setMusic(m: MusicInfo?) {
        val old = music
        music = m
        if (m != null && cfg.showMusic && cfg.autoExpandTrack && m.playing &&
            (old == null || old.id != m.id) && transient == null && !expanded
        ) {
            expanded = true
            scheduleCollapse(3500)
        }
        refresh()
    }

    fun setCharging(c: Content.Charging) {
        charging = c
        refresh()
    }

    fun showTransient(c: Content) {
        val allowed = when (c) {
            is Content.Notif -> cfg.showNotifications
            is Content.Charging -> cfg.showCharging
            is Content.Ringer -> cfg.showRinger
            is Content.Music -> cfg.showMusic
        }
        if (!allowed || !cfg.enabled || suppressed) return
        val ms = when (c) {
            is Content.Notif -> cfg.notifSeconds * 1000L
            is Content.Ringer -> 2200L
            else -> 3000L
        }
        transient = c
        expanded = false
        handler.removeCallbacks(clearTransient)
        handler.postDelayed(clearTransient, ms)
        if (cfg.haptics) host.haptic(false)
        refresh()
    }

    fun refresh() {
        val c: Content? = if (!cfg.enabled || suppressed) null else transient ?: persistent()
        val mode = when {
            !cfg.enabled || suppressed -> Mode.HIDDEN
            c == null -> if (cfg.alwaysVisible) Mode.IDLE else Mode.HIDDEN
            expanded && c is Content.Music -> Mode.EXPANDED
            else -> Mode.COMPACT
        }
        if (mode != Mode.EXPANDED) expanded = false
        view.show(mode, c)
        host.onModeChanged(mode)
    }

    private fun persistent(): Content? {
        val m = music
        if (cfg.showMusic && m != null && (m.playing || expanded)) return Content.Music(m)
        val ch = charging
        if (cfg.showCharging && cfg.chargingPersistent && ch != null && ch.plugged) return ch
        return null
    }

    private fun scheduleCollapse(ms: Long) {
        handler.removeCallbacks(autoCollapse)
        handler.postDelayed(autoCollapse, ms)
    }

    private fun expand() {
        expanded = true
        scheduleCollapse(8000)
        if (cfg.haptics) host.haptic(true)
        refresh()
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        handler.removeCallbacks(autoCollapse)
        refresh()
    }

    private fun dismissTransient() {
        handler.removeCallbacks(clearTransient)
        transient = null
        refresh()
    }

    // ---- Touch aus der View ----

    override fun onIslandTap() {
        when (val c = view.target) {
            is Content.Music -> {
                if (view.mode == Mode.EXPANDED) {
                    c.info.actions?.open()
                    collapse()
                } else if (cfg.tapExpands) {
                    expand()
                } else {
                    if (cfg.haptics) host.haptic(false)
                    c.info.actions?.open()
                }
            }
            is Content.Notif -> {
                if (cfg.haptics) host.haptic(false)
                c.open?.invoke()
                dismissTransient()
            }
            is Content.Charging, is Content.Ringer -> dismissTransient()
            null -> {}
        }
    }

    override fun onIslandLongPress() {
        when (val c = view.target) {
            is Content.Music -> {
                if (view.mode == Mode.EXPANDED) return
                if (cfg.tapExpands) {
                    if (cfg.haptics) host.haptic(true)
                    c.info.actions?.open()
                } else {
                    expand()
                }
            }
            is Content.Notif -> {
                if (cfg.haptics) host.haptic(true)
                c.open?.invoke()
                dismissTransient()
            }
            else -> {}
        }
    }

    override fun onOutside() {
        collapse()
    }

    override fun onSwipeUp() {
        if (expanded) collapse() else if (transient != null) dismissTransient()
    }

    override fun onButton(button: Int) {
        val m = (view.target as? Content.Music)?.info ?: return
        if (cfg.haptics) host.haptic(false)
        when (button) {
            IslandView.BTN_PREV -> m.actions?.previous()
            IslandView.BTN_PLAY -> m.actions?.playPause()
            IslandView.BTN_NEXT -> m.actions?.next()
        }
        scheduleCollapse(8000)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
    }
}
