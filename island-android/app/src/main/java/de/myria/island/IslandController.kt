package de.myria.island

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import de.myria.island.IslandView.Mode

/**
 * Entscheidet, was die Island zeigt. Wird vom echten Overlay UND von der
 * Vorschau in den Einstellungen benutzt – darum verhält sich die Vorschau 1:1 gleich.
 *
 * Wie beim iPhone:
 *  - wichtigste Aktivität = Haupt-Island, zweitwichtigste = kleine Bubble rechts
 *  - Tippen öffnet die App, gedrückt halten (oder runterziehen) erweitert
 *  - nach oben wischen = einklappen bzw. Aktivität kurz wegwischen
 */
class IslandController(
    private val view: IslandView,
    private val host: Host,
) : IslandView.Listener {

    interface Host {
        fun haptic(strong: Boolean)
        fun onModeChanged(mode: Mode) {}
        fun toggleTorch() {}
        fun startTimer() {}
        fun startStopwatch() {}
        fun openSettings() {}
        fun setTorchLevel(level: Float) {}
    }

    var cfg: IslandConfig = IslandConfig()
        set(v) {
            field = v
            view.cfg = v
            // Fix: Akku-Rand sofort an/aus, nicht erst beim nächsten Akku-Signal
            val ch = charging
            view.ringLevel = if (v.batteryRing && ch != null && ch.plugged) ch.level / 100f else -1f
            refresh()
        }

    /** z.B. Querformat, Bildschirm aus, Sperrbildschirm → komplett ausblenden */
    var suppressed = false
        set(v) {
            if (field != v) {
                field = v
                refresh()
            }
        }

    var privacy = Privacy()
        set(v) {
            if (field != v) {
                field = v
                view.privacy = v
                refresh()
            }
        }

    private var music: MusicInfo? = null
    private var pausedSince = 0L
    private var live: List<Content> = emptyList()
    private var charging: Content.Charging? = null
    private var transient: Content? = null
    private var expandedKey: String? = null
    private var pinnedKey: String? = null
    private var snoozedKey: String? = null
    private var knownLive = HashSet<String>()
    private val queue = ArrayList<Content.Notif>()

    /** Nächster Wecker (für die leere Pille) */
    var nextAlarm: String? = null
        set(v) {
            field = v
            view.nextAlarm = v
        }

    private val handler = Handler(Looper.getMainLooper())
    private val clearTransient = Runnable {
        transient = null
        // Warteschlange: nächste Mitteilung zeigen
        if (queue.isNotEmpty()) {
            val next = queue.removeAt(0)
            view.queueCount = queue.size
            showTransient(next)
            return@Runnable
        }
        view.queueCount = 0
        refresh()
    }
    private val autoCollapse = Runnable {
        if (expandedKey != null) {
            expandedKey = null
            refresh()
        }
    }
    private val lingerEnd = Runnable { refresh() }

    init {
        view.listener = this
    }

    // ------------------------------------------------------------------
    // Eingänge
    // ------------------------------------------------------------------

    fun setMusic(m: MusicInfo?) {
        val old = music
        music = m
        if (m != null && !m.playing && (old?.playing == true)) {
            pausedSince = SystemClock.elapsedRealtime()
            handler.removeCallbacks(lingerEnd)
            handler.postDelayed(lingerEnd, cfg.pausedLingerSec * 1000L + 50)
        }
        if (m?.playing == true) pausedSince = 0L
        // Neuer Song oder wieder Play → Wegwischen aufheben
        if (snoozedKey == "music" && m != null && (old == null || old.id != m.id || (m.playing && !old.playing))) {
            snoozedKey = null
        }
        if (m != null && cfg.showMusic && cfg.autoExpandTrack && m.playing &&
            (old == null || old.id != m.id) && transient == null && expandedKey == null
        ) {
            expandedKey = "music"
            scheduleCollapse(3500)
        }
        refresh()
    }

    fun setLive(list: List<Content>) {
        live = list
        val keys = list.map { it.key }.toHashSet()
        for (c in list) {
            if (c.key !in knownLive && c is Content.Call && c.incoming && cfg.showCalls && cfg.haptics) {
                host.haptic(true)
            }
            // Taschenlampe an → kurz groß aufklappen mit Lichtkegel (wie iOS)
            if (c.key !in knownLive && c is TorchContent && cfg.showTorch && transient == null) {
                expandedKey = c.key
                scheduleCollapse(2800)
                if (cfg.haptics) host.haptic(true)
            }
        }
        knownLive = keys
        if (snoozedKey != null && snoozedKey != "music" && snoozedKey !in keys) snoozedKey = null
        if (pinnedKey != null && pinnedKey !in keys && pinnedKey != "music") pinnedKey = null
        refresh()
    }

    fun setCharging(c: Content.Charging) {
        charging = c
        view.idleBattery = c.level
        view.ringLevel = if (cfg.batteryRing && c.plugged) c.level / 100f else -1f
        refresh()
    }

    fun allowed(c: Content): Boolean = when (c) {
        is Content.Notif -> cfg.showNotifications
        is Content.Charging -> if (c.plugged) cfg.showCharging else cfg.showLowBattery
        is Content.Ringer -> cfg.showRinger
        is Content.Music -> cfg.showMusic
        is Content.Timer -> cfg.showTimer
        is Content.Call -> cfg.showCalls
        is Content.Nav -> cfg.showNav
        is Content.Progress -> cfg.showProgress
        is Content.Device -> cfg.showDevices
        is UnlockContent -> cfg.showUnlock
        is VolumeContent -> cfg.showVolume
        is TorchContent -> cfg.showTorch
        is FocusContent -> cfg.showFocus
        is AirplaneContent -> cfg.showAirplane
        is NetworkContent -> cfg.showNetwork
        is HeatContent -> cfg.tempWarning
        is AlarmContent -> cfg.showAlarm
    }

    private fun inQuietHours(): Boolean {
        if (!cfg.quietHours) return false
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val s = cfg.quietStart
        val e = cfg.quietEnd
        return if (s == e) false else if (s < e) h in s until e else h >= s || h < e
    }

    fun showTransient(c: Content) {
        if (!allowed(c) || !cfg.enabled || suppressed) return
        if (c is Content.Notif && inQuietHours()) return
        // Warteschlange statt überschreiben
        val cur = transient
        if (c is Content.Notif && cur is Content.Notif && cfg.notifQueue && cur.key != c.key) {
            if (queue.size < 9) queue.add(c)
            view.queueCount = queue.size
            return
        }
        val quiet = c is VolumeContent && transient is VolumeContent
        val ms = when (c) {
            is VolumeContent -> 1600L
            is FocusContent, is AirplaneContent -> 2200L
            is Content.Notif -> cfg.notifSeconds * 1000L + if (cfg.notifAutoExpand) 3000L else 0L
            is Content.Ringer -> 2200L
            is UnlockContent -> 1500L
            is Content.Progress -> 2600L
            is Content.Device -> 3500L
            is Content.Charging -> 3400L
            else -> 3000L
        }
        transient = c
        expandedKey = if (c is Content.Notif && cfg.notifAutoExpand && c.expandable) c.key else null
        handler.removeCallbacks(clearTransient)
        handler.postDelayed(clearTransient, ms)
        if (cfg.haptics && c !is UnlockContent && c !is VolumeContent && !quiet) host.haptic(false)
        refresh()
    }

    // ------------------------------------------------------------------
    // Entscheidung
    // ------------------------------------------------------------------

    private fun persistentList(): List<Content> {
        val out = ArrayList<Content>()
        for (c in live) if (allowed(c)) out.add(c)
        val m = music
        if (cfg.showMusic && m != null) {
            val lingering = pausedSince > 0 &&
                SystemClock.elapsedRealtime() - pausedSince < cfg.pausedLingerSec * 1000L
            if (m.playing || lingering || expandedKey == "music") out.add(Content.Music(m))
        }
        val ch = charging
        if (cfg.showCharging && cfg.chargingPersistent && ch != null && ch.plugged) out.add(ch)
        return out
            .filter { it.key != snoozedKey }
            .sortedWith(compareByDescending<Content> { if (it.key == pinnedKey) 1000 else 0 }.thenByDescending { it.priority })
    }

    fun refresh() {
        if (!cfg.enabled || suppressed) {
            view.show(Mode.HIDDEN, null, null)
            host.onModeChanged(Mode.HIDDEN)
            return
        }
        val pers = persistentList()
        val primary = transient ?: pers.firstOrNull()
        val second = if (cfg.splitBubble) pers.firstOrNull { it.key != primary?.key } else null
        val privacyOn = (privacy.camera && cfg.privacyCamera) || (privacy.mic && cfg.privacyMic) ||
            (privacy.location && cfg.privacyLocation)
        val mode = when {
            primary == null -> if (cfg.alwaysVisible || privacyOn) Mode.IDLE else Mode.HIDDEN
            expandedKey == primary.key && primary.expandable -> Mode.EXPANDED
            else -> Mode.COMPACT
        }
        if (mode != Mode.EXPANDED && expandedKey != null && expandedKey != primary?.key) expandedKey = null
        view.show(mode, primary, second)
        host.onModeChanged(mode)
    }

    private fun scheduleCollapse(ms: Long = cfg.autoCollapseSec * 1000L) {
        handler.removeCallbacks(autoCollapse)
        handler.postDelayed(autoCollapse, ms)
    }

    private fun expand(c: Content) {
        if (!c.expandable) return
        expandedKey = c.key
        if (c === transient) handler.removeCallbacks(clearTransient)
        scheduleCollapse(if (c is Content.Notif) cfg.autoCollapseSec * 1000L + 4000 else cfg.autoCollapseSec * 1000L)
        if (cfg.haptics) host.haptic(true)
        refresh()
    }

    private fun collapse() {
        if (expandedKey == null) return
        expandedKey = null
        handler.removeCallbacks(autoCollapse)
        if (transient != null) {
            handler.removeCallbacks(clearTransient)
            handler.postDelayed(clearTransient, 1800)
        }
        refresh()
    }

    private fun dismissTransient() {
        handler.removeCallbacks(clearTransient)
        expandedKey = null
        // Fix: wartende Mitteilungen kommen danach dran (statt hängenzubleiben)
        clearTransient.run()
    }

    private fun open(c: Content?) {
        when (c) {
            is Content.Music -> c.info.actions?.open()
            is Content.Notif -> c.open?.invoke()
            is Content.Timer -> c.open?.invoke()
            is Content.Call -> c.open?.invoke()
            is Content.Nav -> c.open?.invoke()
            is Content.Progress -> c.open?.invoke()
            else -> {}
        }
    }

    // ------------------------------------------------------------------
    // Touch aus der View
    // ------------------------------------------------------------------

    override fun onIslandTap() {
        val c = view.target ?: return
        if (c is TorchContent) {
            if (cfg.haptics) host.haptic(true)
            collapse()
            c.off?.invoke()
            return
        }
        if (view.mode == Mode.EXPANDED) {
            if (cfg.haptics) host.haptic(false)
            open(c)
            if (c === transient) dismissTransient() else collapse()
            return
        }
        when (c) {
            is Content.Notif -> {
                if (cfg.haptics) host.haptic(false)
                open(c)
                dismissTransient()
            }
            is Content.Call -> if (c.incoming) open(c) else tapPersistent(c)
            is TorchContent -> {
                if (cfg.haptics) host.haptic(true)
                c.off?.invoke()
            }
            is Content.Charging -> if (c.plugged && cfg.chargeDetails) expand(c) else dismissTransient()
            is Content.Ringer, is Content.Device, is UnlockContent, is VolumeContent, is FocusContent,
            is AirplaneContent, is NetworkContent, is HeatContent, is AlarmContent -> dismissTransient()
            else -> tapPersistent(c)
        }
    }

    private fun tapPersistent(c: Content) {
        if (cfg.tapExpands && c.expandable) {
            expand(c)
        } else {
            if (cfg.haptics) host.haptic(false)
            open(c)
        }
    }

    override fun onIslandLongPress() {
        val c = view.target
        if (c == null) {
            // Leere Pille gedrückt halten
            if (view.mode != Mode.IDLE) return
            when (cfg.idleLongPress) {
                IslandConfig.IDLE_LP_TIMER -> host.startTimer()
                IslandConfig.IDLE_LP_STOPWATCH -> host.startStopwatch()
                IslandConfig.IDLE_LP_TORCH -> host.toggleTorch()
                IslandConfig.IDLE_LP_SETTINGS -> host.openSettings()
                else -> return
            }
            if (cfg.haptics) host.haptic(true)
            return
        }
        if (view.mode == Mode.EXPANDED) return
        if (cfg.tapExpands) {
            if (cfg.haptics) host.haptic(true)
            open(c)
            if (c === transient) dismissTransient()
        } else {
            expand(c)
        }
    }

    override fun onOutside() {
        collapse()
    }

    override fun onSwipeUp() {
        val c = view.target
        when {
            expandedKey != null -> collapse()
            transient != null -> dismissTransient()
            c != null -> {
                // Aktivität kurz „wegwischen“ – kommt bei Änderung zurück
                snoozedKey = c.key
                if (cfg.haptics) host.haptic(false)
                refresh()
            }
        }
    }

    override fun onSwipeDown() {
        val c = view.target ?: return
        if (cfg.swipeDownExpands) expand(c)
    }

    override fun onDoubleTap() {
        val c = view.target
        if (cfg.haptics) host.haptic(true)
        when (cfg.doubleTapAction) {
            IslandConfig.DOUBLE_PLAYPAUSE -> (music ?: (c as? Content.Music)?.info)?.actions?.playPause()
            IslandConfig.DOUBLE_OPEN -> open(c)
            IslandConfig.DOUBLE_EXPAND -> if (c != null) expand(c)
            IslandConfig.DOUBLE_TORCH -> host.toggleTorch()
        }
    }

    override fun onSwipeHorizontal(dir: Int) {
        val c = view.target ?: return
        if (cfg.swipeHorizontal == IslandConfig.SWIPE_OFF) return
        if (cfg.swipeHorizontal == IslandConfig.SWIPE_TRACK && c is Content.Music) {
            if (dir > 0) c.info.actions?.previous() else c.info.actions?.next()
            if (cfg.haptics) host.haptic(false)
            return
        }
        // Zwischen den laufenden Aktivitäten blättern
        val list = persistentList()
        if (list.size < 2 || transient != null) return
        val idx = list.indexOfFirst { it.key == c.key }.coerceAtLeast(0)
        val next = list[(idx + (if (dir > 0) -1 else 1) + list.size) % list.size]
        pinnedKey = next.key
        if (cfg.haptics) host.haptic(false)
        refresh()
    }

    override fun onTorchLevel(level: Float) {
        host.setTorchLevel(level)
        if (cfg.haptics) host.haptic(false)
        scheduleCollapse(3500)
    }

    override fun onBubbleTap() {
        val s = view.secondary ?: return
        pinnedKey = s.key
        if (cfg.haptics) host.haptic(false)
        refresh()
    }

    override fun onHit(run: () -> Unit) {
        if (cfg.haptics) host.haptic(false)
        run()
        if (expandedKey != null) scheduleCollapse()
    }

    override fun onSeek(ms: Long) {
        val m = music ?: return
        m.actions?.seekTo(ms)
        if (cfg.haptics) host.haptic(false)
        music = MusicInfo(
            m.id, m.title, m.artist, m.art, m.accent, m.playing, m.durationMs, ms,
            SystemClock.elapsedRealtime(), m.speed, m.actions, m.appIcon, m.canSeek,
        )
        scheduleCollapse()
        refresh()
    }

    fun release() {
        queue.clear()
        handler.removeCallbacksAndMessages(null)
    }
}
