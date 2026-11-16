package app.alltag

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------- Palette ---------- */
data class Pal(
    val dark: Boolean,
    val bg: Color, val fg: Color, val muted: Color, val faint: Color,
    val glass: Color, val glass2: Color, val thumb: Color, val edge: Color, val hi: Color,
    val hair: Color, val fill: Color,
    val accent: Color, val onAcc: Color,
    val b1: Color, val b2: Color, val b3: Color,
    val good: Color, val bad: Color, val warn: Color,
) {
    val accentSoft get() = accent.copy(alpha = if (dark) 0.20f else 0.14f)
}

fun pal(dark: Boolean, work: Boolean): Pal = if (!dark) Pal(
    dark = false,
    bg = Color(0xFFECEFF5), fg = Color(0xFF14161C), muted = Color(0xFF5F6779), faint = Color(0xFF9299AA),
    glass = Color.White.copy(alpha = 0.62f), glass2 = Color(0xFFF5F7FB), thumb = Color.White,
    edge = Color.White.copy(alpha = 0.80f), hi = Color.White,
    hair = Color(0x17181E34), fill = Color(0x0D181E34),
    accent = if (work) Color(0xFF0B8A7D) else Color(0xFF0A74F0), onAcc = Color.White,
    b1 = if (work) Color(0xFF86DCCF) else Color(0xFFFFB6A3),
    b2 = if (work) Color(0xFFB3BFFF) else Color(0xFF8FC6FF),
    b3 = if (work) Color(0xFFFFDB94) else Color(0xFFC9F1DA),
    good = Color(0xFF16834A), bad = Color(0xFFD9333A), warn = Color(0xFFB86200),
) else Pal(
    dark = true,
    bg = Color(0xFF07090D), fg = Color(0xFFF1F3F8), muted = Color(0xFFA3AABA), faint = Color(0xFF6D7486),
    glass = Color(0xFF1E222E).copy(alpha = 0.62f), glass2 = Color(0xFF1A1E28), thumb = Color(0xFF343A49),
    edge = Color.White.copy(alpha = 0.08f), hi = Color.White.copy(alpha = 0.18f),
    hair = Color.White.copy(alpha = 0.08f), fill = Color.White.copy(alpha = 0.06f),
    accent = if (work) Color(0xFF34D1BD) else Color(0xFF4AA3FF), onAcc = Color(0xFF051019),
    b1 = if (work) Color(0xFF0C4F4A) else Color(0xFF6E2A38),
    b2 = if (work) Color(0xFF28307A) else Color(0xFF173F7A),
    b3 = if (work) Color(0xFF5C3F0E) else Color(0xFF174D3C),
    good = Color(0xFF45D08A), bad = Color(0xFFFF6B70), warn = Color(0xFFFFB547),
)

val LocalPal = staticCompositionLocalOf { pal(dark = false, work = false) }

/* ---------- Type ---------- */
val Display = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 41.sp, letterSpacing = (-1.2).sp)
val H2 = TextStyle(fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2).sp)
val Body = TextStyle(fontSize = 15.sp, lineHeight = 21.sp)
val Small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
val Label = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp)
val Num = TextStyle(fontFeatureSettings = "tnum")

/* ---------- Icons (same drawings as the web version) ---------- */
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

    val today = stroke("today", 1.9f, circ(12f, 12f, 4.2f), "M12 2.5v2.2M12 19.3v2.2M4.6 4.6l1.6 1.6M17.8 17.8l1.6 1.6M2.5 12h2.2M19.3 12h2.2M4.6 19.4l1.6-1.6M17.8 6.2l1.6-1.6")
    val tasks = stroke("tasks", 1.9f, circ(12f, 12f, 9f), "M8 12.3l2.8 2.8L16.2 9.5")
    val notes = stroke("notes", 1.9f, "M6.5 3.5h8.5l4 4V19a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 19V5A1.5 1.5 0 0 1 6.5 3.5z", "M14.5 3.5V8H19M8.5 12.5h7M8.5 16h5")
    val money = stroke("money", 1.9f, "M6 6h12a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3z", "M3 10h18M16 14.5h2", "M6 6l9.5-2.6a1.5 1.5 0 0 1 1.8 1L18 6")
    val focus = stroke("focus", 1.9f, circ(12f, 13f, 8f), "M12 9v4l2.5 2.5M9.5 2.5h5")
    val plus = stroke("plus", 2.4f, "M12 5v14M5 12h14")
    val check = stroke("check", 3.2f, "M5 12.5l4.5 4.5L19 7.5")
    val left = stroke("left", 2.2f, "M15 5l-7 7 7 7")
    val right = stroke("right", 2.2f, "M9 5l7 7-7 7")
    val close = stroke("close", 2.2f, "M6 6l12 12M18 6L6 18")
    val search = stroke("search", 2f, circ(11f, 11f, 7f), "M20 20l-3.6-3.6")
    val gear = stroke(
        "gear", 1.9f, circ(12f, 12f, 3f),
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )
    val play = filled("play", "M8 5.5v13a1 1 0 0 0 1.5.9l10.4-6.5a1 1 0 0 0 0-1.8L9.5 4.6A1 1 0 0 0 8 5.5z")
    val pause = filled(
        "pause",
        "M7.7 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2H7.7a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 7.7 5z",
        "M14.7 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2h-1.6a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 14.7 5z",
    )
}

/* ---------- Surfaces ---------- */
fun Modifier.glass(p: Pal, shape: Shape = RoundedCornerShape(26.dp)): Modifier =
    this.clip(shape).background(p.glass).border(1.dp, Brush.verticalGradient(listOf(p.hi, p.edge)), shape)

fun Modifier.tap(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(), indication = null, onClick = onClick,
)

@Composable
fun Aurora(modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val t = rememberInfiniteTransition(label = "aurora")
    val k by t.animateFloat(0f, 1f, infiniteRepeatable(tween(26_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "k")
    val j by t.animateFloat(0f, 1f, infiniteRepeatable(tween(33_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "j")
    Canvas(modifier.fillMaxSize().background(p.bg)) {
        val m = maxOf(size.width, size.height)
        fun blob(c: Color, x: Float, y: Float, r: Float) {
            drawCircle(
                Brush.radialGradient(0f to c, 0.45f to c.copy(alpha = c.alpha * 0.55f), 1f to c.copy(alpha = 0f), center = Offset(x, y), radius = r),
                radius = r, center = Offset(x, y),
            )
        }
        val a = if (p.dark) 0.85f else 0.95f
        blob(p.b1.copy(alpha = a), size.width * (0.0f + 0.12f * k), size.height * (0.02f + 0.06f * j), m * 0.62f)
        blob(p.b2.copy(alpha = a), size.width * (1.02f - 0.12f * j), size.height * (0.16f + 0.06f * k), m * 0.55f)
        blob(p.b3.copy(alpha = a), size.width * (0.30f + 0.15f * k), size.height * (1.02f - 0.08f * j), m * 0.6f)
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(18.dp), content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(modifier.fillMaxWidth().glass(p).padding(padding), content = content)
}

@Composable
fun CardHead(title: String, sub: String? = null, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = H2)
        if (sub != null) Text("  ·  $sub", style = Body.merge(Num), color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.weight(1f))
        if (action != null) Text(
            action, color = p.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 4.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun Header(eyebrow: String, title: String) {
    val p = LocalPal.current
    Column(Modifier.padding(start = 4.dp, top = 18.dp, bottom = 4.dp)) {
        Text(eyebrow, color = p.muted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Spacer(Modifier.height(2.dp))
        Text(title, style = Display)
    }
}

@Composable
fun TopBar() {
    val p = LocalPal.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(30.dp).clip(RoundedCornerShape(9.dp))
                .background(Brush.linearGradient(listOf(lerp(p.accent, Color.White, 0.3f), p.accent))),
            contentAlignment = Alignment.Center,
        ) { Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(9.dp))
        Text("Alltag", style = H2)
        Spacer(Modifier.weight(1f))
        Segmented(listOf("privat" to "Privat", "arbeit" to "Arbeit"), Store.space, { Store.setSpaceTo(it) }, itemWidth = 74.dp)
        Spacer(Modifier.width(8.dp))
        CircleBtn(Ic.gear, "Einstellungen") { UiState.sheet = Sheet.Settings }
    }
}

@Composable
fun CircleBtn(icon: ImageVector, desc: String, size: Dp = 42.dp, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.size(size).glass(p, CircleShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = p.fg, modifier = Modifier.size(20.dp)) }
}

@Composable
fun Segmented(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, itemWidth: Dp = 84.dp) {
    val p = LocalPal.current
    val idx = options.indexOfFirst { it.first == selected }.coerceAtLeast(0)
    val x by animateDpAsState(itemWidth * idx, spring(dampingRatio = 0.68f, stiffness = 520f), label = "seg")
    Box(Modifier.glass(p, CircleShape).padding(4.dp)) {
        Box(
            Modifier.offset(x = x).width(itemWidth).height(34.dp)
                .shadow(5.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                .clip(CircleShape).background(p.thumb),
        )
        Row {
            options.forEach { (key, label) ->
                val c by animateColorAsState(if (key == selected) p.fg else p.muted, label = "segc")
                Box(
                    Modifier.width(itemWidth).height(34.dp).clip(CircleShape).tap { onSelect(key) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, color = c, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
            }
        }
    }
}

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    textStyle: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    filled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
) {
    val p = LocalPal.current
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value = value, onValueChange = onValueChange, modifier = modifier,
        textStyle = textStyle.merge(TextStyle(color = p.fg)),
        singleLine = singleLine, minLines = minLines,
        cursorBrush = SolidColor(p.accent),
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Box(
                (if (filled) Modifier.clip(shape).background(p.fill).border(1.dp, p.hair, shape) else Modifier)
                    .fillMaxWidth().padding(contentPadding),
            ) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = p.faint, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                inner()
            }
        },
    )
}

/** filter = dark/light inverted pill (list filters); otherwise an accent-tinted option pill. */
@Composable
fun Chip(label: String, selected: Boolean, filter: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val bg by animateColorAsState(
        when {
            selected && filter -> p.fg
            selected -> p.accentSoft
            filter -> p.glass
            else -> p.fill
        }, label = "chipbg",
    )
    val fg = when {
        selected && filter -> p.bg
        selected -> p.accent
        else -> p.muted
    }
    Box(
        Modifier.clip(CircleShape).background(bg)
            .border(1.dp, if (filter && !selected) p.edge else if (!selected) p.hair else Color.Transparent, CircleShape)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1) }
}

@Composable
fun Btn(text: String, modifier: Modifier = Modifier, primary: Boolean = false, danger: Boolean = false, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPal.current
    val bg = when {
        danger -> p.bad.copy(alpha = 0.12f)
        primary -> p.accent
        else -> p.fill
    }
    val fg = when {
        danger -> p.bad
        primary -> p.onAcc
        else -> p.fg
    }
    Row(
        modifier.clip(CircleShape).background(if (enabled) bg else bg.copy(alpha = bg.alpha * 0.4f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun RoundBtn(icon: ImageVector, desc: String, size: Dp = 42.dp, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier.size(size).shadow(8.dp, CircleShape, ambientColor = p.accent, spotColor = p.accent)
            .clip(CircleShape).background(p.accent).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = p.onAcc, modifier = Modifier.size(size * 0.45f)) }
}

@Composable
fun Hair() {
    val p = LocalPal.current
    Box(Modifier.fillMaxWidth().height(1.dp).background(p.hair))
}

@Composable
fun GroupTitle(text: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Text(text, color = p.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp, style = Num, modifier = modifier.padding(start = 2.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
fun FieldLabel(text: String) {
    val p = LocalPal.current
    Text(text.uppercase(), style = Label, color = p.muted, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
fun Empty(title: String, text: String) {
    val p = LocalPal.current
    Column(Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Spacer(Modifier.height(2.dp))
        Text(text, color = p.muted, style = Body)
    }
}

/* ---------- Task row ---------- */
@Composable
fun TaskRow(t: Task) {
    val p = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var shownDone by remember(t.id, t.done) { mutableStateOf(t.done) }
    val fill by animateColorAsState(if (shownDone) p.accent else Color.Transparent, label = "chk")
    val ring by animateColorAsState(if (shownDone) p.accent else p.faint, label = "ring")
    val pop by animateFloatAsState(if (shownDone) 1f else 0.999f, spring(dampingRatio = 0.35f, stiffness = 600f), label = "pop")
    val late = t.due.isNotEmpty() && t.due < today().toString() && !t.done

    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.padding(top = 1.dp).size(26.dp).scale(if (shownDone) pop else 1f).clip(CircleShape).background(fill)
                .border(2.dp, ring, CircleShape)
                .clickable(role = Role.Checkbox) {
                    shownDone = !shownDone
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch { delay(380); Store.putTask(t.copy(done = !t.done, ex = t.ex)) }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (shownDone) Icon(Ic.check, if (t.done) "Als offen markieren" else "Als erledigt markieren", tint = p.onAcc, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).tap { UiState.sheet = Sheet.TaskEdit(t.id) }) {
            Text(
                t.title, fontWeight = FontWeight.Medium,
                color = if (shownDone) p.faint else p.fg,
                textDecoration = if (shownDone) TextDecoration.LineThrough else null,
            )
            if (t.due.isNotEmpty() || t.prio > 0) Row(Modifier.padding(top = 2.dp)) {
                if (t.due.isNotEmpty()) Text(
                    (if (late) "Überfällig · " else "") + relDay(t.due), style = Small,
                    color = if (late) p.bad else p.muted, fontWeight = if (late) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (t.due.isNotEmpty() && t.prio > 0) Spacer(Modifier.width(10.dp))
                if (t.prio > 0) Text(
                    if (t.prio == 2) "!! Dringend" else "! Wichtig", style = Small, fontWeight = FontWeight.Bold,
                    color = if (t.prio == 2) p.bad else p.warn,
                )
            }
        }
    }
}
