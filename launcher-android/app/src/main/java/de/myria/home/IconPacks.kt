package de.myria.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Icon-Packs aus dem Play Store (Nova/ADW/Lawnchair-Format, appfilter.xml).
 * Funktioniert mit praktisch allen Icon-Packs.
 */
object IconPacks {
    class Pack(val pkg: String, val label: String)

    private val THEME_ACTIONS = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.fede.launcher.THEME_ICONPACK",
        "com.anddoes.launcher.THEME",
        "com.teslacoilsw.launcher.THEME",
    )

    @Volatile private var loadedPkg: String? = null
    @Volatile private var map: Map<String, String> = emptyMap()
    @Volatile private var res: Resources? = null

    /** Installierte Icon-Packs */
    fun installed(ctx: Context): List<Pack> {
        val pm = ctx.packageManager
        val seen = LinkedHashMap<String, Pack>()
        for (a in THEME_ACTIONS) {
            val list = try {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(Intent(a), PackageManager.GET_META_DATA)
            } catch (_: Exception) { emptyList() }
            for (ri in list) {
                val p = ri.activityInfo?.packageName ?: continue
                if (p !in seen) seen[p] = Pack(p, ri.loadLabel(pm).toString())
            }
        }
        return seen.values.sortedBy { it.label.lowercase() }
    }

    /** Lädt die Zuordnung des Packs (einmal pro Pack). Läuft im Hintergrund-Thread. */
    fun prepare(ctx: Context, pkg: String?) {
        if (pkg.isNullOrEmpty()) { loadedPkg = null; map = emptyMap(); res = null; return }
        if (pkg == loadedPkg) return
        val r = try { ctx.packageManager.getResourcesForApplication(pkg) } catch (_: Exception) { null }
        if (r == null) { loadedPkg = null; map = emptyMap(); res = null; return }
        val m = HashMap<String, String>()
        try {
            val parser: XmlPullParser? = run {
                @Suppress("DiscouragedApi")
                val id = r.getIdentifier("appfilter", "xml", pkg)
                if (id != 0) r.getXml(id) else try {
                    val f = XmlPullParserFactory.newInstance().newPullParser()
                    f.setInput(r.assets.open("appfilter.xml"), "UTF-8")
                    f
                } catch (_: Exception) { null }
            }
            if (parser != null) {
                var ev = parser.eventType
                while (ev != XmlPullParser.END_DOCUMENT) {
                    if (ev == XmlPullParser.START_TAG && parser.name == "item") {
                        val comp = parser.getAttributeValue(null, "component")
                        val dr = parser.getAttributeValue(null, "drawable")
                        if (comp != null && dr != null && comp.startsWith("ComponentInfo{")) {
                            val cn = ComponentName.unflattenFromString(comp.removePrefix("ComponentInfo{").removeSuffix("}"))
                            if (cn != null) m[cn.flattenToShortString()] = dr
                            // auch nur über das Paket finden (falls die Activity anders heißt)
                            if (cn != null && "pkg:" + cn.packageName !in m) m["pkg:" + cn.packageName] = dr
                        }
                    }
                    ev = parser.next()
                }
            }
        } catch (_: Exception) {}
        res = r
        map = m
        loadedPkg = pkg
    }

    /** Icon aus dem Pack für diese App – oder null */
    fun iconFor(cn: ComponentName, density: Int): Drawable? {
        val r = res ?: return null
        val pkg = loadedPkg ?: return null
        val name = map[cn.flattenToShortString()] ?: map["pkg:" + cn.packageName] ?: return null
        return try {
            @Suppress("DiscouragedApi")
            val id = r.getIdentifier(name, "drawable", pkg)
            if (id == 0) null else r.getDrawableForDensity(id, density, null)
        } catch (_: Exception) { null }
    }

    val active: Boolean get() = loadedPkg != null && map.isNotEmpty()
}
