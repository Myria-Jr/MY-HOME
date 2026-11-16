package app.alltag

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.max

enum class Mode(val label: String, val min: Int) { FOKUS("Fokus", 25), PAUSE("Pause", 5), LANG("Lange Pause", 15) }

object AppFlags {
    @Volatile var foreground = false
}

object FocusTimer {
    var mode by mutableStateOf(Mode.FOKUS)
    var total by mutableIntStateOf(25 * 60)
    var left by mutableIntStateOf(25 * 60)
    var endAt by mutableLongStateOf(0L)
    var running by mutableStateOf(false)
    var what by mutableStateOf("")
    var now by mutableLongStateOf(System.currentTimeMillis())
    private var restored = false

    fun remaining(): Int = if (running) max(0, ((endAt - now + 999) / 1000).toInt()) else left

    fun setMode(ctx: Context, m: Mode) {
        if (running) Alarm.cancel(ctx)
        mode = m; total = m.min * 60; left = total; running = false
        persist(ctx)
    }

    fun toggle(ctx: Context) {
        if (running) {
            now = System.currentTimeMillis(); left = remaining(); running = false; Alarm.cancel(ctx)
        } else {
            if (left <= 0) left = total
            now = System.currentTimeMillis(); endAt = now + left * 1000L; running = true
            Alarm.schedule(ctx, endAt, mode == Mode.FOKUS)
        }
        persist(ctx)
    }

    fun reset(ctx: Context) = setMode(ctx, mode)

    /** Called when the countdown hits zero while the app runs. Returns true if a focus session ended. */
    fun finish(ctx: Context): Boolean {
        val wasFocus = mode == Mode.FOKUS
        if (wasFocus) Store.addFocusSession()
        running = false
        val next = if (!wasFocus) Mode.FOKUS else if ((Store.focus[today().toString()] ?: 0) % 4 == 0) Mode.LANG else Mode.PAUSE
        mode = next; total = next.min * 60; left = total
        persist(ctx)
        return wasFocus
    }

    private fun persist(ctx: Context) {
        ctx.getSharedPreferences("timer", Context.MODE_PRIVATE).edit()
            .putString("mode", mode.name).putInt("left", left).putLong("endAt", endAt)
            .putBoolean("running", running).putString("what", what).apply()
    }

    fun restore(ctx: Context) {
        if (restored) return
        restored = true
        val p = ctx.getSharedPreferences("timer", Context.MODE_PRIVATE)
        val m = runCatching { Mode.valueOf(p.getString("mode", "FOKUS")!!) }.getOrDefault(Mode.FOKUS)
        mode = m; total = m.min * 60
        left = p.getInt("left", total).coerceIn(0, total)
        endAt = p.getLong("endAt", 0L)
        what = p.getString("what", "") ?: ""
        now = System.currentTimeMillis()
        if (p.getBoolean("running", false)) {
            running = true
            if (endAt <= now) finish(ctx)
        }
    }
}

object Alarm {
    private fun pending(ctx: Context, focus: Boolean = true): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, 7, Intent(ctx, TimerReceiver::class.java).putExtra("focus", focus),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun schedule(ctx: Context, at: Long, focus: Boolean) {
        runCatching {
            val am = ctx.getSystemService(AlarmManager::class.java)
            val pi = pending(ctx, focus)
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context) {
        runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx)) }
    }
}

/** Posts "time's up" when the timer ends while the app is in the background. */
class TimerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (AppFlags.foreground) return
        val focus = intent.getBooleanExtra("focus", true)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("timer", "Fokus-Timer", NotificationManager.IMPORTANCE_HIGH))
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(ctx, "timer")
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(if (focus) "Session geschafft" else "Pause vorbei")
            .setContentText(if (focus) "Zeit für eine Pause." else "Weiter geht’s.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(1, n)
    }
}

object Feedback {
    fun done(ctx: Context) {
        runCatching {
            val v: Vibrator = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            else @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 120, 90, 180), -1))
        }
        runCatching {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tg.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 700)
        }
    }
}
