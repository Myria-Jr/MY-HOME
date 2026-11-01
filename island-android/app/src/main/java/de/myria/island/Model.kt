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

    class Charging(val level: Int, val plugged: Boolean) : Content() {
        override val key get() = "charging"
        override val priority get() = 10
        override val expandable get() = false
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
