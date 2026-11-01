package de.myria.island

import android.graphics.Bitmap
import android.os.SystemClock
import java.util.concurrent.CopyOnWriteArrayList

interface MediaActions {
    fun playPause()
    fun next()
    fun previous()
    fun open()
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

/** Was die Island gerade anzeigt. */
sealed class Content {
    class Music(val info: MusicInfo) : Content()
    class Charging(val level: Int, val plugged: Boolean) : Content()
    class Notif(
        val icon: Bitmap?,
        val app: String,
        val title: String,
        val text: String,
        val open: (() -> Unit)?,
    ) : Content()
    class Ringer(val silent: Boolean, val vibrate: Boolean) : Content()
}

/** Kleiner In-Process-Bus: NotificationService / MainActivity → IslandService. */
object IslandBus {
    interface Listener {
        fun onMusic(m: MusicInfo?) {}
        fun onEvent(c: Content) {}
        fun onTestMusic(m: MusicInfo) {}
    }

    private val listeners = CopyOnWriteArrayList<Listener>()

    var music: MusicInfo? = null
        private set

    @Volatile
    var serviceRunning = false

    fun add(l: Listener) { listeners.addIfAbsent(l) }
    fun remove(l: Listener) { listeners.remove(l) }

    fun setMusic(m: MusicInfo?) {
        music = m
        listeners.forEach { it.onMusic(m) }
    }

    fun event(c: Content) = listeners.forEach { it.onEvent(c) }
    fun testMusic(m: MusicInfo) = listeners.forEach { it.onTestMusic(m) }
}
