package app.alltag

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Rounded line icons, drawn on a 24-unit grid. */
object Ic {
    private fun circ(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"
    private fun stroke(name: String, w: Float, vararg d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.forEach {
            addPath(
                PathParser().parsePathString(it).toNodes(), fill = null, stroke = SolidColor(Color.Black),
                strokeLineWidth = w, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
    private fun filled(name: String, vararg d: String) = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        d.forEach { addPath(PathParser().parsePathString(it).toNodes(), fill = SolidColor(Color.Black)) }
    }.build()

    val today = stroke("today", 2f, circ(12f, 12f, 4.2f), "M12 2.5v2.2M12 19.3v2.2M4.6 4.6l1.6 1.6M17.8 17.8l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.6 19.4l1.6-1.6M17.8 6.2l1.6-1.6")
    val todayFilled = ImageVector.Builder("todayF", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(PathParser().parsePathString(circ(12f, 12f, 5f)).toNodes(), fill = SolidColor(Color.Black))
        addPath(
            PathParser().parsePathString("M12 2.5v1.8M12 19.7v1.8M4.6 4.6l1.3 1.3M18.1 18.1l1.3 1.3M2.5 12h1.8M19.7 12h1.8M4.6 19.4l1.3-1.3M18.1 5.9l1.3-1.3").toNodes(),
            stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f, strokeLineCap = StrokeCap.Round,
        )
    }.build()
    val tasks = stroke("tasks", 2f, circ(12f, 12f, 9f), "M8 12.3l2.8 2.8L16.2 9.5")
    val tasksFilled = filled("tasksF", "M12 2a10 10 0 1 0 0 20a10 10 0 1 0 0-20zm4.9 7.2l-6.1 6.4a1 1 0 0 1-1.4 0L6.9 13a1 1 0 1 1 1.4-1.4l1.8 1.8 5.4-5.6a1 1 0 0 1 1.4 1.4z")
    val notes = stroke("notes", 2f, "M6.5 3.5h8.5l4 4V19a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19V5A1.5 1.5 0 0 1 6.5 3.5z", "M14.5 3.5V8H19M8.5 12.5h7M8.5 16h5")
    val notesFilled = filled("notesF", "M6.5 2.5h8.3a1 1 0 0 1 .7.3l4.2 4.2a1 1 0 0 1 .3.7V19a2.5 2.5 0 0 1-2.5 2.5h-11A2.5 2.5 0 0 1 4 19V5a2.5 2.5 0 0 1 2.5-2.5zM8.5 11.5a1 1 0 0 0 0 2h7a1 1 0 0 0 0-2zm0 3.5a1 1 0 0 0 0 2h5a1 1 0 0 0 0-2z")
    val money = stroke("money", 2f, "M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z", "M3 10h18M16 14.5h2", "M6 6l9.5-2.6a1.5 1.5 0 0 1 1.8 1L18 6")
    val moneyFilled = filled("moneyF", "M6 5h12a4 4 0 0 1 4 4v1H2V9a4 4 0 0 1 4-4zM2 12h20v4a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4zm14 2a1 1 0 0 0 0 2h2a1 1 0 0 0 0-2z")
    val focus = stroke("focus", 2f, circ(12f, 13f, 8f), "M12 9v4l2.5 2.5M9.5 2.5h5")
    val focusFilled = filled("focusF", "M9.5 1.5h5a1 1 0 0 1 0 2h-5a1 1 0 0 1 0-2zM12 4a9 9 0 1 1 0 18a9 9 0 0 1 0-18zm0 4a1 1 0 0 0-1 1v4a1 1 0 0 0 .3.7l2.5 2.5a1 1 0 0 0 1.4-1.4L13 12.6V9a1 1 0 0 0-1-1z")
    val plus = stroke("plus", 2.4f, "M12 5v14M5 12h14")
    val check = stroke("check", 3f, "M5 12.5l4.5 4.5L19 7.5")
    val left = stroke("left", 2.2f, "M15 5l-7 7 7 7")
    val right = stroke("right", 2.2f, "M9 5l7 7-7 7")
    val close = stroke("close", 2.2f, "M6 6l12 12M18 6L6 18")
    val search = stroke("search", 2.2f, circ(11f, 11f, 7f), "M20 20l-3.6-3.6")
    val home = stroke("home", 2f, "M4 10.5L12 4l8 6.5V19a1.5 1.5 0 0 1-1.5 1.5H15V15a1 1 0 0 0-1-1h-4a1 1 0 0 0-1 1v5.5H5.5A1.5 1.5 0 0 1 4 19z")
    val work = stroke("work", 2f, "M5 7.5h14A1.5 1.5 0 0 1 20.5 9v9a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 18V9A1.5 1.5 0 0 1 5 7.5z", "M9 7.5V6a1.5 1.5 0 0 1 1.5-1.5h3A1.5 1.5 0 0 1 15 6v1.5M3.5 12.5h17")
    val trash = stroke("trash", 2f, "M4.5 7h15M10 7V5h4v2M6.5 7l.8 12a1.5 1.5 0 0 0 1.5 1.4h6.4a1.5 1.5 0 0 0 1.5-1.4l.8-12M10 11v6M14 11v6")
    val calendar = stroke("calendar", 2f, "M6 5h12a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M4 10h16M8.5 3v4M15.5 3v4")
    val refresh = stroke("refresh", 2f, "M19.5 12a7.5 7.5 0 1 1-2.2-5.3M19.5 4.5v4h-4")
    val inArrow = stroke("in", 2.2f, "M12 4v14M6 12l6 6 6-6")
    val outArrow = stroke("out", 2.2f, "M12 20V6M6 12l6-6 6 6")
    val gear = stroke(
        "gear", 2f, circ(12f, 12f, 3f),
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val play = filled("play", "M8 5.5v13a1 1 0 0 0 1.5.9l10.4-6.5a1 1 0 0 0 0-1.8L9.5 4.6A1 1 0 0 0 8 5.5z")
    val pause = filled(
        "pause",
        "M7.7 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2H7.7a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 7.7 5z",
        "M14.7 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2h-1.6a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 14.7 5z",
    )
}
