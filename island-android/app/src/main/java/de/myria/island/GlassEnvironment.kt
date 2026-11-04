package de.myria.island

import android.annotation.TargetApi
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.os.Handler

internal data class GlassEnvironment(val primary: Int, val secondary: Int) {
    val primaryLuminance = Color.luminance(primary)
    val secondaryLuminance = Color.luminance(secondary)
    val luminance = primaryLuminance * 0.7f + secondaryLuminance * 0.3f

    fun lightInkOnSurface(material: Int): Boolean = when (material) {
        IslandConfig.GLASS_LIGHT -> false
        IslandConfig.GLASS_AUTO -> luminance < 0.36f
        IslandConfig.GLASS_CLEAR -> luminance < 0.20f
        else -> true
    }

    companion object {
        val DARK = GlassEnvironment(0xFF191E2B.toInt(), 0xFF2D3445.toInt())
        val LIGHT = GlassEnvironment(0xFFE7E7ED.toInt(), 0xFFCDD4E1.toInt())
    }
}

@TargetApi(27)
internal class GlassWallpaperLight(
    context: Context,
    private val main: Handler,
    private val update: (GlassEnvironment?) -> Unit,
) : AutoCloseable {
    private val manager = WallpaperManager.getInstance(context)
    private var closed = false
    private var receivedUpdate = false
    private val listener = WallpaperManager.OnColorsChangedListener { colors, flags ->
        if (!closed && flags and WallpaperManager.FLAG_SYSTEM != 0) {
            receivedUpdate = true
            update(colors?.toEnvironment())
        }
    }

    init {
        manager.addOnColorsChangedListener(listener, main)
        Thread({
            val colors = runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
            main.post {
                if (!closed && !receivedUpdate) update(colors?.toEnvironment())
            }
        }, "Lumora wallpaper colors").start()
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { manager.removeOnColorsChangedListener(listener) }
    }

    private fun WallpaperColors.toEnvironment(): GlassEnvironment {
        val primary = primaryColor.toArgb()
        return GlassEnvironment(primary, secondaryColor?.toArgb() ?: tertiaryColor?.toArgb() ?: primary)
    }
}
