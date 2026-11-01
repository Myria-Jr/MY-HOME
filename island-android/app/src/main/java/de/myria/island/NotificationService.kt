package de.myria.island

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Bekommt Benachrichtigungen + Zugriff auf laufende Musik (MediaSessions).
 */
class NotificationService : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var current: MediaController? = null
    private val recent = HashMap<String, Long>()

    private var artCacheKey: String? = null
    private var artCache: Bitmap? = null
    private var accentCache: Int = DEFAULT_ACCENT

    private val component get() = ComponentName(this, NotificationService::class.java)

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list)
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state?.state != PlaybackState.STATE_PLAYING) refreshSessions() else publish()
        }
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = refreshSessions()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val m = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        msm = m
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, component, main)
        } catch (_: SecurityException) {}
        refreshSessions()
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        current?.unregisterCallback(callback)
        current = null
        IslandBus.setMusic(null)
        super.onListenerDisconnected()
    }

    // ------------------------------------------------------------------
    // Musik
    // ------------------------------------------------------------------

    private fun refreshSessions() {
        val list = try { msm?.getActiveSessions(component) } catch (_: SecurityException) { null }
        pick(list)
    }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull { it.sessionToken == current?.sessionToken }
            ?: list?.firstOrNull()
        if (best?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = best
            best?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = current
        val md = c?.metadata
        if (c == null || md == null) {
            IslandBus.setMusic(null)
            return
        }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md.description?.title?.toString()
        if (title.isNullOrBlank()) {
            IslandBus.setMusic(null)
            return
        }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.description?.subtitle?.toString()
            ?: appLabel(c.packageName)

        val id = c.packageName + "|" + title + "|" + artist
        if (artCacheKey != id) {
            val raw = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            val art = raw?.let { softCopy(it) }
            artCache = art
            accentCache = art?.let { Colors.accentOf(it) } ?: DEFAULT_ACCENT
            artCacheKey = id
        }

        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING ||
            st?.state == PlaybackState.STATE_BUFFERING
        val controls = c.transportControls
        val actions = object : MediaActions {
            override fun playPause() {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
            }
            override fun next() = controls.skipToNext()
            override fun previous() = controls.skipToPrevious()
            override fun open() {
                val pi = c.sessionActivity
                if (pi != null) sendPending(this@NotificationService, pi)
                else packageManager.getLaunchIntentForPackage(c.packageName)?.let {
                    it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { startActivity(it) } catch (_: Exception) {}
                }
            }
        }

        IslandBus.setMusic(
            MusicInfo(
                id = id,
                title = title,
                artist = artist,
                art = artCache,
                accent = accentCache,
                playing = playing,
                durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
                positionMs = st?.position ?: 0L,
                positionTime = st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
                speed = st?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
                actions = actions,
            )
        )
    }

    // ------------------------------------------------------------------
    // Benachrichtigungen
    // ------------------------------------------------------------------

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        handle(sbn, rankingMap)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn, currentRankingSafe())
    }

    private fun currentRankingSafe(): RankingMap? = try { currentRanking } catch (_: Exception) { null }

    private fun handle(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val extras = n.extras

        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            refreshSessions()
            return
        }
        if (!sbn.isClearable || sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        when (n.category) {
            Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS, Notification.CATEGORY_SYSTEM,
            Notification.CATEGORY_STATUS -> return
        }

        if (rankingMap != null) {
            val r = Ranking()
            if (rankingMap.getRanking(sbn.key, r)) {
                if (r.importance < NotificationManager.IMPORTANCE_DEFAULT) return
                if (!r.matchesInterruptionFilter()) return
            }
        }

        val now = SystemClock.elapsedRealtime()
        val last = recent[sbn.key]
        recent[sbn.key] = now
        if (last != null) {
            if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return
            if (now - last < 1500) return
        }
        if (recent.size > 200) recent.entries.removeIf { now - it.value > 60_000 }

        val title = (extras.getCharSequence(Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE))?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val app = appLabel(sbn.packageName)
        val icon = loadIcon(n, sbn.packageName)
        val pi = n.contentIntent
        val key = sbn.key
        val autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0
        val open: (() -> Unit) = {
            if (pi != null) sendPending(this, pi)
            if (autoCancel) try { cancelNotification(key) } catch (_: Exception) {}
        }

        IslandBus.event(Content.Notif(icon, app, title, text, open))
    }

    private fun loadIcon(n: Notification, pkg: String): Bitmap? {
        val size = (48 * resources.displayMetrics.density).toInt()
        return try {
            val large = n.getLargeIcon()?.loadDrawable(this)
            val dr = large ?: packageManager.getApplicationIcon(pkg)
            drawableToBitmap(dr, size)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    companion object {
        val DEFAULT_ACCENT = 0xFFFF6AA2.toInt()

        fun drawableToBitmap(dr: Drawable, size: Int): Bitmap {
            if (dr is BitmapDrawable && dr.bitmap != null) return softCopy(dr.bitmap)
            val w = if (dr.intrinsicWidth > 0) dr.intrinsicWidth.coerceAtMost(size * 2) else size
            val h = if (dr.intrinsicHeight > 0) dr.intrinsicHeight.coerceAtMost(size * 2) else size
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            dr.setBounds(0, 0, w, h)
            dr.draw(c)
            return b
        }

        /** Hardware-Bitmaps können wir nicht auslesen → Kopie in Software. */
        fun softCopy(b: Bitmap): Bitmap {
            val hw = Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE
            if (!hw && b.width <= 512 && b.height <= 512) return b
            val max = 512f
            val s = minOf(1f, max / maxOf(b.width, b.height))
            val src = if (hw) b.copy(Bitmap.Config.ARGB_8888, false) else b
            return if (s < 1f) Bitmap.createScaledBitmap(src, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true) else src
        }

        fun sendPending(ctx: Context, pi: PendingIntent) {
            try {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(ctx, 0, null, null, null, null, opts.toBundle())
                } else {
                    pi.send()
                }
            } catch (_: Exception) {}
        }
    }
}

object Colors {
    /** Kräftigste, helle Farbe aus dem Cover – für Wellenform + Leuchten. */
    fun accentOf(b: Bitmap): Int {
        return try {
            val s = Bitmap.createScaledBitmap(b, 16, 16, true)
            val hsv = FloatArray(3)
            var best = NotificationService.DEFAULT_ACCENT
            var bestScore = -1f
            for (y in 0 until 16) for (x in 0 until 16) {
                val px = s.getPixel(x, y)
                Color.colorToHSV(px, hsv)
                if (hsv[2] < 0.25f) continue
                val score = hsv[1] * 0.75f + hsv[2] * 0.25f
                if (score > bestScore) {
                    bestScore = score
                    best = px
                }
            }
            Color.colorToHSV(best, hsv)
            if (hsv[1] < 0.15f) return 0xFFEBEBF5.toInt()
            hsv[1] = hsv[1].coerceIn(0.45f, 0.9f)
            hsv[2] = hsv[2].coerceAtLeast(0.88f)
            Color.HSVToColor(hsv)
        } catch (_: Exception) {
            NotificationService.DEFAULT_ACCENT
        }
    }
}
