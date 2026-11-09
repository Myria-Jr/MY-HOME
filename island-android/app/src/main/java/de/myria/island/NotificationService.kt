package de.myria.island

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * Bekommt Benachrichtigungen + laufende Musik (MediaSessions) und macht daraus
 * Live-Aktivitäten: Anrufe, Timer, Navigation, Uploads/Downloads …
 */
class NotificationService : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var current: MediaController? = null
    private val recent = HashMap<String, Long>()

    /** key → Live-Aktivität */
    private val live = LinkedHashMap<String, Content>()

    private var artCacheKey: String? = null
    private var artCache: Bitmap? = null
    private var accentCache: Int = DEFAULT_ACCENT
    private val iconCache = HashMap<String, Bitmap?>()

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
        // Bereits laufende Live-Aktivitäten einsammeln
        try {
            activeNotifications?.forEach { sbn -> classifyLive(sbn)?.let { live[sbn.key] = it } }
        } catch (_: Exception) {}
        pushLive()
    }

    override fun onListenerDisconnected() {
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Exception) {}
        current?.unregisterCallback(callback)
        current = null
        live.clear()
        IslandBus.setLive(emptyList())
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
        val excluded = Prefs.load(this).excludedApps
        val usable = list?.filter { it.packageName !in excluded }
        val best = usable?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: usable?.firstOrNull { it.sessionToken == current?.sessionToken }
            ?: usable?.firstOrNull()
        if (best?.sessionToken != current?.sessionToken) {
            current?.unregisterCallback(callback)
            current = best
            best?.registerCallback(callback, main)
        }
        publish()
    }

    private val mediaLargeIcon = HashMap<String, Bitmap>()

    /** Cover über content://-Adresse (z. B. lokale Musik-Apps) */
    private fun loadArtUri(md: MediaMetadata): Bitmap? {
        val uri = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_ART_URI)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI) ?: return null
        if (!uri.startsWith("content://") && !uri.startsWith("android.resource://") && !uri.startsWith("file://")) return null
        return try {
            contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }
                android.graphics.BitmapFactory.decodeStream(it, null, o)
            }
        } catch (_: Exception) { null }
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
        // Fix Cover: vorher wurde das Cover nur beim ERSTEN Mal gelesen – kam es etwas später
        // (wie bei Spotify/YouTube Music üblich), blieb es für das ganze Lied leer.
        if (artCacheKey != id || artCache == null) {
            val raw = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                ?: md.description?.iconBitmap
                ?: loadArtUri(md)
                ?: mediaLargeIcon[c.packageName]
            val art = raw?.let { softCopy(it) }
            if (artCacheKey != id || art != null) {
                artCache = art
                accentCache = art?.let { Colors.accentOf(it) } ?: DEFAULT_ACCENT
            }
            artCacheKey = id
        }

        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING ||
            st?.state == PlaybackState.STATE_BUFFERING
        val controls = c.transportControls
        val canSeek = st != null && (st.actions and PlaybackState.ACTION_SEEK_TO) != 0L
        val actions = object : MediaActions {
            override fun playPause() {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
            }
            override fun next() = controls.skipToNext()
            override fun previous() = controls.skipToPrevious()
            override fun seekTo(ms: Long) = controls.seekTo(ms)
            override fun open() {
                val pi = c.sessionActivity
                if (pi != null) sendPending(this@NotificationService, pi)
                else launchApp(this@NotificationService, c.packageName)
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
                appIcon = appIcon(c.packageName),
                canSeek = canSeek,
            )
        )
    }

    // ------------------------------------------------------------------
    // Benachrichtigungen
    // ------------------------------------------------------------------

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        try { handle(sbn, rankingMap) } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        val old = live.remove(sbn.key) ?: return
        if (old is Content.Progress && old.progress >= 0.95f && !old.done) {
            IslandBus.event(doneOf(old))
        }
        pushLive()
    }

    private fun pushLive() = IslandBus.setLive(live.values.toList())

    private fun handle(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val extras = n.extras
        val cfg = Prefs.load(this)
        if (sbn.packageName in cfg.excludedApps) return

        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
            // Großes Bild der Medien-Benachrichtigung = Cover (Fallback für Apps ohne Cover in den Metadaten)
            try {
                val li = n.getLargeIcon()?.loadDrawable(this)
                val bmp = (li as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: (extras.getParcelable(Notification.EXTRA_LARGE_ICON) as? Bitmap)
                if (bmp != null) mediaLargeIcon[sbn.packageName] = softCopy(bmp)
            } catch (_: Exception) {}
            refreshSessions()
            return
        }

        // 1) Live-Aktivität?
        val liveContent = classifyLive(sbn)
        val old = live[sbn.key]
        if (liveContent != null) {
            live[sbn.key] = liveContent
            pushLive()
            return
        } else if (old != null) {
            live.remove(sbn.key)
            pushLive()
            if (old is Content.Progress && !old.done) {
                // Upload/Download fertig → Häkchen statt normaler Benachrichtigung
                IslandBus.event(doneOf(old))
                return
            }
        }

        // 2) Normale Benachrichtigung
        if (!sbn.isClearable || sbn.isOngoing) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0) return
        when (n.category) {
            Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_SYSTEM, Notification.CATEGORY_STATUS -> return
        }

        if (rankingMap != null) {
            val r = Ranking()
            if (rankingMap.getRanking(sbn.key, r)) {
                val minImportance = if (cfg.notifSilent) NotificationManager.IMPORTANCE_LOW
                else NotificationManager.IMPORTANCE_DEFAULT
                if (r.importance < minImportance) return
                if (!cfg.notifDnd && !r.matchesInterruptionFilter()) return
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
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return

        val app = appLabel(sbn.packageName)
        val icon = loadIcon(n, sbn.packageName)
        val key = sbn.key
        val autoCancel = n.flags and Notification.FLAG_AUTO_CANCEL != 0
        val open = openerFor(n, sbn.packageName, key, autoCancel)

        if (cfg.notifHistory) NotifHistory.add(this, app, title, text)
        IslandBus.event(
            Content.Notif(
                icon = icon,
                app = app,
                title = if (cfg.notifHideContent) app else title,
                text = if (cfg.notifHideContent) "Neue Mitteilung" else text,
                open = open,
                actions = if (cfg.notifActions && !cfg.notifHideContent) actionsOf(n, key, autoCancel) else emptyList(),
                id = key + "@" + sbn.postTime,
                accent = (appIcon(sbn.packageName) ?: icon)?.let { Colors.accentOf(it) },
            )
        )
    }

    /** Erkennt Anrufe, Navigation, Timer/Stoppuhr und Fortschritt. */
    private fun classifyLive(sbn: StatusBarNotification): Content? {
        if (sbn.packageName == packageName) return null
        if (sbn.packageName in Prefs.load(this).excludedApps) return null
        val n = sbn.notification ?: return null
        val ex = n.extras
        if (ex.containsKey(Notification.EXTRA_MEDIA_SESSION)) return null
        val pkg = sbn.packageName
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val app = appLabel(pkg)
        val open = openerFor(n, pkg, sbn.key, false)

        // --- Anruf ---
        // Fix Anrufe: auch Samsung/WhatsApp & Co. erkennen (CallStyle-Extras, Annehmen-Knopf, Vollbild)
        val callStyle = ex.containsKey("android.callType") || ex.containsKey("android.callPerson")
        val hasAnswer = callIntent(n, "android.answerIntent") != null || actionMatching(n, ANSWER) != null
        val isCall = (n.category == Notification.CATEGORY_CALL || callStyle) &&
            (sbn.isOngoing || n.fullScreenIntent != null || hasAnswer || chrono)
        if (isCall) {
            val incoming = !chrono && (n.fullScreenIntent != null || hasAnswer)
            val accept = callIntent(n, "android.answerIntent") ?: actionMatching(n, ANSWER)
            val decline = callIntent(n, "android.declineIntent")
                ?: callIntent(n, "android.hangUpIntent")
                ?: actionMatching(n, DECLINE)
            return Content.Call(
                key = sbn.key,
                name = title.ifBlank { app },
                app = app,
                avatar = loadIcon(n, pkg),
                incoming = incoming,
                since = if (chrono) n.`when` else 0L,
                accept = accept?.let { pi -> { sendPending(this, pi) } },
                decline = decline?.let { pi -> { sendPending(this, pi) } },
                open = open,
                pkg = pkg,
                // Lautsprecher, Stumm … (ohne Annehmen/Ablehnen/Auflegen)
                actions = actionsOf(n, sbn.key, false)
                    .filter { !ANSWER.containsMatchIn(it.title) && !DECLINE.containsMatchIn(it.title) }
                    .take(2),
            )
        }

        // --- Navigation ---
        if (n.category == "navigation" || (sbn.isOngoing && pkg in NAV_APPS)) {
            if (title.isBlank() && text.isBlank()) return null
            val arrow = try { n.getLargeIcon()?.loadDrawable(this)?.let { drawableToBitmap(it, dp(56)) } } catch (_: Exception) { null }
            return Content.Nav(sbn.key, title.ifBlank { text }, if (title.isBlank()) "" else text, arrow, open, pkg)
        }

        // --- Timer / Stoppuhr / Aufnahme ---
        if (chrono && sbn.isOngoing && n.`when` > 0) {
            val countDown = ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)
            return Content.Timer(
                key = sbn.key,
                app = app,
                title = title.ifBlank { if (countDown) "Timer" else app },
                icon = appIcon(pkg),
                base = n.`when`,
                countDown = countDown,
                open = open,
                actions = actionsOf(n, sbn.key, false).take(2),
            )
        }

        // --- Upload / Download / Fortschritt ---
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        if ((max > 0 || indeterminate) && (sbn.isOngoing || !sbn.isClearable)) {
            val p = if (max > 0) ex.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max else -1f
            return Content.Progress(
                key = sbn.key,
                app = app,
                title = title.ifBlank { text.ifBlank { app } },
                icon = appIcon(pkg),
                progress = if (indeterminate) -1f else p.coerceIn(0f, 1f),
                done = false,
                open = open,
            )
        }
        return null
    }

    private fun doneOf(p: Content.Progress) =
        Content.Progress("done-" + p.key, p.app, p.title, p.icon, 1f, true, p.open)

    @Suppress("DEPRECATION")
    private fun callIntent(n: Notification, key: String): PendingIntent? = try {
        n.extras.getParcelable(key) as? PendingIntent
    } catch (_: Exception) { null }

    private fun actionMatching(n: Notification, words: Regex): PendingIntent? =
        n.actions?.firstOrNull { it.title?.toString()?.let { t -> words.containsMatchIn(t) } == true }?.actionIntent

    private fun actionsOf(n: Notification, key: String, autoCancel: Boolean): List<NotifAction> {
        val list = n.actions ?: return emptyList()
        return list.filter { a ->
            a.actionIntent != null && !a.title.isNullOrBlank() &&
                (a.remoteInputs == null || a.remoteInputs.isEmpty())
        }.take(3).map { a ->
            NotifAction(a.title.toString()) {
                sendPending(this, a.actionIntent)
                if (autoCancel) try { cancelNotification(key) } catch (_: Exception) {}
            }
        }
    }

    private fun openerFor(n: Notification, pkg: String, key: String, autoCancel: Boolean): () -> Unit {
        val pi = n.contentIntent
        return {
            if (pi != null) sendPending(this, pi) else launchApp(this, pkg)
            if (autoCancel) try { cancelNotification(key) } catch (_: Exception) {}
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun loadIcon(n: Notification, pkg: String): Bitmap? {
        return try {
            val large = n.getLargeIcon()?.loadDrawable(this)
            if (large != null) drawableToBitmap(large, dp(48)) else appIcon(pkg)
        } catch (_: Exception) {
            appIcon(pkg)
        }
    }

    private fun appIcon(pkg: String): Bitmap? = iconCache.getOrPut(pkg) {
        try { drawableToBitmap(packageManager.getApplicationIcon(pkg), dp(48)) } catch (_: Exception) { null }
    }

    @Suppress("DEPRECATION")
    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg
    }

    companion object {
        val DEFAULT_ACCENT = 0xFFFF6AA2.toInt()

        private val NAV_APPS = setOf(
            "com.google.android.apps.maps", "com.waze", "com.here.app.maps",
            "net.osmand", "net.osmand.plus", "com.sygic.aura", "app.organicmaps",
        )
        private val ANSWER = Regex("(?i)answer|annehmen|accept|abheben|přijmout")
        private val DECLINE = Regex("(?i)decline|ablehnen|auflegen|hang ?up|beenden|reject|odmítnout|zavěsit")

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

        /** Hardware-Bitmaps können wir nicht auslesen → Kopie in Software, max 512 px. */
        fun softCopy(b: Bitmap): Bitmap {
            val hw = Build.VERSION.SDK_INT >= 26 && b.config == Bitmap.Config.HARDWARE
            if (!hw && b.width <= 512 && b.height <= 512) return b
            val s = minOf(1f, 512f / maxOf(b.width, b.height))
            val src = if (hw) b.copy(Bitmap.Config.ARGB_8888, false) else b
            return if (s < 1f) Bitmap.createScaledBitmap(
                src, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true,
            ) else src
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

        fun launchApp(ctx: Context, pkg: String) {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { ctx.startActivity(i) } catch (_: Exception) {}
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
