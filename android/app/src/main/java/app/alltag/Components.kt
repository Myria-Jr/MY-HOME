@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package app.alltag

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/* ---------- shape morphing ---------- */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = morph.toPath(progress, android.graphics.Path())
        val m = android.graphics.Matrix()
        m.setScale(size.width, size.height)
        if (rotation != 0f) m.postRotate(rotation, size.width / 2, size.height / 2)
        p.transform(m)
        return Outline.Generic(p.asComposePath())
    }
}

/* ---------- segmented (grouped) lists, Android 16 style ---------- */
enum class Pos { Single, First, Middle, Last }

fun posOf(i: Int, n: Int) = when {
    n == 1 -> Pos.Single
    i == 0 -> Pos.First
    i == n - 1 -> Pos.Last
    else -> Pos.Middle
}

fun segShape(pos: Pos): Shape = when (pos) {
    Pos.Single -> RoundedCornerShape(24.dp)
    Pos.First -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    Pos.Middle -> RoundedCornerShape(6.dp)
    Pos.Last -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
}

@Composable
fun SegItem(
    pos: Pos,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val inner: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
    if (onClick != null) Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = segShape(pos), color = color, content = inner)
    else Surface(modifier = modifier.fillMaxWidth(), shape = segShape(pos), color = color, content = inner)
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

/* ---------- shapes as icons ---------- */
val CategoryShapes: List<RoundedPolygon> by lazy {
    listOf(
        MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.SoftBurst,
        MaterialShapes.Gem, MaterialShapes.Flower, MaterialShapes.Puffy, MaterialShapes.Cookie6Sided,
    )
}

@Composable
fun ShapeBadge(shape: RoundedPolygon, color: Color, size: Dp = 40.dp, content: @Composable () -> Unit = {}) {
    Box(Modifier.size(size).clip(shape.toShape()).background(color), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun EmptyState(shape: RoundedPolygon, icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    val spin by rememberInfiniteTransition(label = "spin").animateFloat(
        0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "deg",
    )
    Column(modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }.clip(shape.toShape()).background(MaterialTheme.colorScheme.primaryContainer))
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized)
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/* ---------- Privat | Arbeit ---------- */
@Composable
fun SpaceToggle() {
    val haptic = LocalHapticFeedback.current
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        listOf(Triple("privat", "Privat", Ic.home), Triple("arbeit", "Arbeit", Ic.work)).forEachIndexed { i, (k, l, ic) ->
            ToggleButton(
                checked = Store.space == k,
                onCheckedChange = {
                    if (Store.space != k) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); Store.setSpaceTo(k) }
                },
                shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Icon(ic, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(l, maxLines = 1)
            }
        }
    }
}

/* ---------- morphing checkbox: circle → cookie ---------- */
@Composable
fun MorphCheck(checked: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val prog by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.45f, stiffness = 380f), label = "m")
    val shape = MorphShape(morph, prog.coerceIn(0f, 1f), rotation = prog * 40f)
    Box(
        Modifier.size(32.dp).clip(shape)
            .background(if (checked) cs.primary else Color.Transparent)
            .border(2.dp, if (checked) cs.primary else cs.outline, shape)
            .clickable(role = Role.Checkbox, onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        if (prog > 0.35f) Icon(Ic.check, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp).graphicsLayer { scaleX = prog; scaleY = prog })
    }
}

/* ---------- task item with swipe-to-delete ---------- */
@Composable
fun TaskItem(t: Task, pos: Pos, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var shown by remember(t.id, t.done) { mutableStateOf(t.done) }
    val late = t.due.isNotEmpty() && t.due < today().toString() && !t.done
    val swipe = rememberSwipeToDismissBoxState()

    SwipeToDismissBox(
        state = swipe,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        onDismiss = { v ->
            if (v == SwipeToDismissBoxValue.EndToStart) {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                val idx = Store.removeTask(t)
                UiState.toast("Aufgabe gelöscht") { Store.restoreTask(idx, t) }
            }
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(segShape(pos)).background(cs.errorContainer).padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Ic.trash, "Löschen", tint = cs.onErrorContainer) }
        },
    ) {
        SegItem(pos, onClick = { UiState.sheet = Sheet.TaskEdit(t.id) }) {
            MorphCheck(shown) {
                shown = !shown
                haptic.performHapticFeedback(if (shown) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                scope.launch { delay(420); Store.putTask(t.copy(done = !t.done)) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, style = MaterialTheme.typography.bodyLarge,
                    color = if (shown) cs.onSurfaceVariant else cs.onSurface,
                    textDecoration = if (shown) TextDecoration.LineThrough else null,
                )
                if (t.due.isNotEmpty() || t.prio > 0) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (t.due.isNotEmpty()) Text(
                        (if (late) "Überfällig · " else "") + relDay(t.due),
                        style = if (late) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
                        color = if (late) cs.error else cs.onSurfaceVariant,
                    )
                    if (t.prio > 0) Text(
                        if (t.prio == 2) "Dringend" else "Wichtig", style = MaterialTheme.typography.labelMediumEmphasized,
                        color = if (t.prio == 2) cs.error else cs.tertiary,
                    )
                }
            }
            if (t.prio > 0) ShapeBadge(if (t.prio == 2) MaterialShapes.SoftBurst else MaterialShapes.Cookie4Sided, if (t.prio == 2) cs.errorContainer else cs.tertiaryContainer, 12.dp)
        }
    }
}

/* ---------- pill input (search bar / quick add) ---------- */
@Composable
fun PillField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    trailing: @Composable (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth().height(56.dp), shape = CircleShape, color = cs.surfaceContainerHigh) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) { Icon(leading, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(12.dp)) }
            BasicTextField(
                value, onValueChange, Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = cs.onSurface)),
                singleLine = true, cursorBrush = SolidColor(cs.primary),
                keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        inner()
                    }
                },
            )
            trailing?.invoke()
        }
    }
}
