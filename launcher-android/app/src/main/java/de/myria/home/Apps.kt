package de.myria.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings

class AppInfo(val key: String, val label: String, val component: ComponentName, val icon: Drawable)

/** Lädt alle startbaren Apps über LauncherApps (wie echte Launcher). */
object Apps {
    @Volatile
    var all: List<AppInfo> = emptyList()
        private set

    fun load(ctx: Context): List<AppInfo> {
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val list = try {
            la.getActivityList(null, Process.myUserHandle()).map {
                val cn = it.componentName
                AppInfo(cn.flattenToShortString(), it.label?.toString() ?: cn.packageName, cn,
                    it.getBadgedIcon(0))
            }
        } catch (_: Exception) { emptyList() }
        all = list.sortedBy { it.label.lowercase() }
        return all
    }

    fun byKey(key: String) = all.firstOrNull { it.key == key }

    fun launch(ctx: Context, app: AppInfo, view: android.view.View?) {
        val la = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        try {
            val bounds = view?.let {
                val loc = IntArray(2); it.getLocationOnScreen(loc)
                android.graphics.Rect(loc[0], loc[1], loc[0] + it.width, loc[1] + it.height)
            }
            val opts = view?.let {
                android.app.ActivityOptions.makeScaleUpAnimation(it, 0, 0, it.width, it.height).toBundle()
            }
            la.startMainActivity(app.component, Process.myUserHandle(), bounds, opts)
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {}
        }
    }

    fun appInfo(ctx: Context, app: AppInfo) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.component.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    fun uninstall(ctx: Context, app: AppInfo) {
        try {
            @Suppress("DEPRECATION")
            ctx.startActivity(Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:" + app.component.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    /** Sinnvolle Start-Auswahl: Telefon, Nachrichten, Browser, Kamera fürs Dock, dazu ein paar Favoriten. */
    fun defaults(ctx: Context): Pair<List<String>, List<String>> {
        val pm = ctx.packageManager
        fun pkgFor(i: Intent): String? = try {
            @Suppress("DEPRECATION")
            pm.resolveActivity(i, 0)?.activityInfo?.packageName?.takeIf { it != "android" }
        } catch (_: Exception) { null }
        fun keyFor(pkg: String?): String? = pkg?.let { p -> all.firstOrNull { it.component.packageName == p }?.key }
        val dock = listOfNotNull(
            keyFor(pkgFor(Intent(Intent.ACTION_DIAL))),
            keyFor(pkgFor(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")))),
            keyFor(pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")))),
            keyFor(pkgFor(Intent(MediaStore.ACTION_IMAGE_CAPTURE))),
        ).distinct()
        val fav = ArrayList<String>()
        listOfNotNull(
            keyFor(pkgFor(Intent(Settings.ACTION_SETTINGS))),
            keyFor(pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")))),
            keyFor(pkgFor(Intent(Intent.ACTION_VIEW).setType("image/*"))),
            keyFor(pkgFor(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time")))),
        ).forEach { if (it !in dock && it !in fav) fav.add(it) }
        for (a in all) {
            if (fav.size >= 8) break
            if (a.key !in dock && a.key !in fav && a.component.packageName != ctx.packageName) fav.add(a.key)
        }
        return fav to dock
    }
}
