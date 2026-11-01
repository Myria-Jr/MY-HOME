package de.myria.island

import android.app.Application
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Speichert Abstürze, damit man sie in den Einstellungen sehen + teilen kann. */
class IslandApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val ver = try {
                    packageManager.getPackageInfo(packageName, 0).versionName
                } catch (_: Exception) { "?" }
                val text = buildString {
                    append("Zeit: ").append(SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY).format(Date())).append('\n')
                    append("Atoll: ").append(ver).append('\n')
                    append("Gerät: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                        .append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("Thread: ").append(t.name).append("\n\n")
                    append(sw.toString())
                }
                crashFile(this).writeText(text)
            } catch (_: Throwable) {}
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        fun crashFile(app: android.content.Context) = File(app.filesDir, "crash.txt")
    }
}
