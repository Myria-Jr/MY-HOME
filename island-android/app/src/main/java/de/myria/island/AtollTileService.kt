package de.myria.island

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Kachel in den Schnelleinstellungen: Lumora mit einem Tipp an/aus. */
class AtollTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        val on = Prefs.load(this).enabled
        Prefs.sp(this).edit().putBoolean(Prefs.ENABLED, !on).apply()
        update()
    }

    private fun update() {
        val t = qsTile ?: return
        val on = Prefs.load(this).enabled
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "Lumora"
        if (android.os.Build.VERSION.SDK_INT >= 29) t.subtitle = if (on) "An" else "Aus"
        t.updateTile()
    }
}
