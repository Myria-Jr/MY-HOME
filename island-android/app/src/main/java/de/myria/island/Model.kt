package de.myria.island

import android.graphics.Bitmap
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList

interface MediaActions {
    fun playPause()
    fun next()
    fun previous()
    fun open()
    fun seekTo(ms: Long) {}
}

class MusicInfo(
    val id: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val accent: Int,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() zum Zeitpunkt von positionMs */
    val positionTime: Long,
    val speed: Float,
    val actions: MediaActions?,
    val appIcon: Bitmap? = null,
    val canSeek: Boolean = true,
) {
    fun positionNow(): Long {
        if (positionMs < 0) return 0
        var p = positionMs
        if (playing && positionTime > 0) {
            p += ((SystemClock.elapsedRealtime() - positionTime) * speed).toLong()
        }
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

/** Eine Aktion aus einer Benachrichtigung („Pause“, „Als gelesen markieren“ …) */
class NotifAction(val title: String, val run: () -> Unit)

/** Was die Island gerade anzeigt. */
sealed class Content {
    /** Gleicher key → wird „in place“ aktualisiert, anderer key → Überblendung */
    abstract val key: String

    /** Höher = wichtiger (für Haupt-Island vs. kleine Bubble) */
    open val priority: Int get() = 0

    /** Kann man die Island für diesen Inhalt groß aufziehen? */
    open val expandable: Boolean get() = true

    class Music(val info: MusicInfo) : Content() {
        override val key get() = "music"
        override val priority get() = 60
    }

    class Charging(
        val level: Int,
        val plugged: Boolean,
        /** Restzeit bis voll in ms, -1 = unbekannt */
        val timeLeftMs: Long = -1,
        val fast: Boolean = false,
        /** Akkutemperatur in °C, NaN = unbekannt */
        val tempC: Float = Float.NaN,
    ) : Content() {
        override val key get() = "charging"
        override val priority get() = 10
        override val expandable get() = plugged
    }

    class Notif(
        val icon: Bitmap?,
        val app: String,
        val title: String,
        val text: String,
        val open: (() -> Unit)?,
        val actions: List<NotifAction> = emptyList(),
        val id: String = "n" + SystemClock.elapsedRealtimeNanos(),
    ) : Content() {
        override val key get() = id
        override val priority get() = 200
    }

    class Ringer(val silent: Boolean, val vibrate: Boolean) : Content() {
        override val key get() = "ringer"
        override val expandable get() = false
    }

    /** Timer, Stoppuhr, Sprachaufnahme … alles mit laufender Uhr */
    class Timer(
        override val key: String,
        val app: String,
        val title: String,
        val icon: Bitmap?,
        /** System.currentTimeMillis()-Basis (Notification.when) */
        val base: Long,
        val countDown: Boolean,
        val open: (() -> Unit)?,
        val actions: List<NotifAction>,
    ) : Content() {
        override val priority get() = 70
        fun display(): String {
            val now = System.currentTimeMillis()
            val ms = if (countDown) base - now else now - base
            return formatClock(ms.coerceAtLeast(0))
        }
    }

    class Call(
        override val key: String,
        val name: String,
        val app: String,
        val avatar: Bitmap?,
        val incoming: Boolean,
        /** Gesprächsbeginn (System.currentTimeMillis), 0 = unbekannt */
        val since: Long,
        val accept: (() -> Unit)?,
        val decline: (() -> Unit)?,
        val open: (() -> Unit)?,
    ) : Content() {
        override val priority get() = if (incoming) 100 else 90
    }

    class Nav(
        override val key: String,
        val primary: String,
        val secondary: String,
        val arrow: Bitmap?,
        val open: (() -> Unit)?,
    ) : Content() {
        override val priority get() = 80
    }

    /** Upload / Download / Export … */
    class Progress(
        override val key: String,
        val app: String,
        val title: String,
        val icon: Bitmap?,
        /** 0..1, < 0 = unbestimmt */
        val progress: Float,
        val done: Boolean,
        val open: (() -> Unit)?,
    ) : Content() {
        override val priority get() = if (done) 150 else 50
    }

    /** Kopfhörer / Bluetooth-Gerät */
    class Device(val name: String, val connected: Boolean, val battery: Int) : Content() {
        override val key get() = "device"
        override val expandable get() = false
    }
}

/** Internet: 0 = offline, 1 = WLAN, 2 = Mobilfunk */
class NetworkContent(val kind: Int) : Content() {
    override val key get() = "network"
    override val expandable get() = false
}

/** Akku zu heiß */
class HeatContent(val tempC: Float) : Content() {
    override val key get() = "heat"
    override val priority get() = 120
    override val expandable get() = false
}

/** Wecker gestellt */
class AlarmContent(val text: String) : Content() {
    override val key get() = "alarm"
    override val expandable get() = false
}

/** Lautstärke geändert (0..1) */
class VolumeContent(val level: Float, val muted: Boolean) : Content() {
    override val key get() = "volume"
    override val expandable get() = false
}

/** Taschenlampe an – Antippen schaltet sie aus, Wischen regelt die Helligkeit (wie iOS) */
class TorchContent(
    val off: (() -> Unit)?,
    /** Helligkeit 0..1 */
    val level: Float = 1f,
    /** Kann das Handy die Helligkeit regeln? (Android 13+) */
    val adjustable: Boolean = false,
) : Content() {
    override val key get() = "torch"
    override val priority get() = 40
}

/** Nicht stören / Fokus */
class FocusContent(val on: Boolean) : Content() {
    override val key get() = "focus"
    override val expandable get() = false
}

/** Flugmodus */
class AirplaneContent(val on: Boolean) : Content() {
    override val key get() = "airplane"
    override val expandable get() = false
}

/** Schloss-Animation beim Entsperren (wie Face ID) */
class UnlockContent : Content() {
    override val key get() = "unlock"
    override val expandable get() = false
}

/** Kamera / Mikrofon / Standort gerade in Benutzung (wie die iOS-Punkte) */
data class Privacy(val camera: Boolean = false, val mic: Boolean = false, val location: Boolean = false) {
    val any get() = camera || mic || location
}

fun formatClock(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Kleiner In-Process-Bus: NotificationService / MainActivity → IslandService. */
object IslandBus {
    interface Listener {
        fun onMusic(m: MusicInfo?) {}
        fun onEvent(c: Content) {}
        fun onLive(list: List<Content>) {}
        fun onTestMusic(m: MusicInfo) {}
        fun onTestLive(list: List<Content>) {}
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    var music: MusicInfo? = null
        private set

    var live: List<Content> = emptyList()
        private set

    @Volatile
    var serviceRunning = false

    fun add(l: Listener) { listeners.addIfAbsent(l) }
    fun remove(l: Listener) { listeners.remove(l) }

    fun setMusic(m: MusicInfo?) {
        music = m
        listeners.forEach { it.onMusic(m) }
    }

    fun setLive(list: List<Content>) {
        live = list
        listeners.forEach { it.onLive(list) }
    }

    fun event(c: Content) = listeners.forEach { it.onEvent(c) }
    fun testMusic(m: MusicInfo) = listeners.forEach { it.onTestMusic(m) }
    fun testLive(list: List<Content>) = listeners.forEach { it.onTestLive(list) }
}


/** Eigener Timer + Stoppuhr (laufen im App-Prozess, erscheinen als Live-Aktivität). */
object OwnTimers {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var timerEnd = 0L
    private var stopwatchStart = 0L
    var onChange: (() -> Unit)? = null
    var onTimerDone: (() -> Unit)? = null

    val timerRunning get() = timerEnd > System.currentTimeMillis()
    val stopwatchRunning get() = stopwatchStart > 0

    private val done = Runnable {
        timerEnd = 0
        onChange?.invoke()
        onTimerDone?.invoke()
    }

    fun startTimer(minutes: Int) {
        timerEnd = System.currentTimeMillis() + minutes * 60_000L
        main.removeCallbacks(done)
        main.postDelayed(done, minutes * 60_000L)
        onChange?.invoke()
    }

    fun addMinute() {
        if (!timerRunning) return
        timerEnd += 60_000L
        main.removeCallbacks(done)
        main.postDelayed(done, timerEnd - System.currentTimeMillis())
        onChange?.invoke()
    }

    fun stopTimer() {
        timerEnd = 0
        main.removeCallbacks(done)
        onChange?.invoke()
    }

    fun startStopwatch() {
        stopwatchStart = System.currentTimeMillis()
        onChange?.invoke()
    }

    fun stopStopwatch() {
        stopwatchStart = 0
        onChange?.invoke()
    }

    fun items(): List<Content> {
        val out = ArrayList<Content>()
        if (timerRunning) out.add(Content.Timer(
            "own-timer", "Isla", "Timer", null, timerEnd, true, null,
            listOf(NotifAction("+1 Min") { addMinute() }, NotifAction("Stopp") { stopTimer() }),
        ))
        if (stopwatchRunning) out.add(Content.Timer(
            "own-stopwatch", "Isla", "Stoppuhr", null, stopwatchStart, false, null,
            listOf(NotifAction("Stopp") { stopStopwatch() }),
        ))
        return out
    }
}

/** Die letzten Mitteilungen, die in der Island waren (nur lokal auf dem Handy). */
object NotifHistory {
    class Entry(val time: Long, val app: String, val title: String, val text: String)

    private const val MAX = 60
    private val list = ArrayList<Entry>()
    private var loaded = false

    private fun file(c: android.content.Context) = java.io.File(c.filesDir, "notif_history.json")

    @Synchronized
    fun all(c: android.content.Context): List<Entry> {
        load(c)
        return list.toList()
    }

    @Synchronized
    fun add(c: android.content.Context, app: String, title: String, text: String) {
        load(c)
        list.add(0, Entry(System.currentTimeMillis(), app, title, text))
        while (list.size > MAX) list.removeAt(list.size - 1)
        save(c)
    }

    @Synchronized
    fun clear(c: android.content.Context) {
        list.clear()
        loaded = true
        try { file(c).delete() } catch (_: Exception) {}
    }

    private fun load(c: android.content.Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c)
            if (!f.exists()) return
            val a = org.json.JSONArray(f.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                list.add(Entry(o.getLong("t"), o.optString("a"), o.optString("ti"), o.optString("tx")))
            }
        } catch (_: Exception) {}
    }

    private fun save(c: android.content.Context) {
        try {
            val a = org.json.JSONArray()
            for (e in list) a.put(org.json.JSONObject().put("t", e.time).put("a", e.app).put("ti", e.title).put("tx", e.text))
            file(c).writeText(a.toString())
        } catch (_: Exception) {}
    }
}
