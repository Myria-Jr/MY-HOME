package app.alltag

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.YearMonth

enum class Tab(val label: String, val icon: ImageVector) {
    HEUTE("Heute", Ic.today), AUFGABEN("Aufgaben", Ic.tasks), NOTIZEN("Notizen", Ic.notes), GELD("Geld", Ic.money), FOKUS("Fokus", Ic.focus)
}

sealed interface Sheet {
    data class TaskEdit(val id: String?) : Sheet
    data class NoteEdit(val id: String?) : Sheet
    data class MoneyEdit(val id: String?) : Sheet
    data object Settings : Sheet
}

class ToastData(val text: String, val undo: (() -> Unit)?)

object UiState {
    var sheet by mutableStateOf<Sheet?>(null)
    var toastData by mutableStateOf<ToastData?>(null)
    var taskFilter by mutableStateOf("offen")
    var noteQuery by mutableStateOf("")
    var month by mutableStateOf(YearMonth.now())
    var askNotifications: () -> Unit = {}
    fun toast(text: String, undo: (() -> Unit)? = null) { toastData = ToastData(text, undo) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(applicationContext)
        FocusTimer.restore(applicationContext)
        setContent { AlltagApp() }
    }

    override fun onStart() { super.onStart(); AppFlags.foreground = true }
    override fun onStop() { super.onStop(); AppFlags.foreground = false }
}

@Composable
fun AlltagApp(initialTab: Tab = Tab.HEUTE, dark: Boolean = isSystemInDarkTheme()) {
    val ctx = LocalContext.current
    val target = pal(dark, Store.space == "arbeit")
    val accent by animateColorAsState(target.accent, tween(600), label = "acc")
    val b1 by animateColorAsState(target.b1, tween(1200), label = "b1")
    val b2 by animateColorAsState(target.b2, tween(1200), label = "b2")
    val b3 by animateColorAsState(target.b3, tween(1200), label = "b3")
    val p = target.copy(accent = accent, b1 = b1, b2 = b2, b3 = b3)

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    SideEffect {
        UiState.askNotifications = {
            if (Build.VERSION.SDK_INT >= 33 && !asked &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) { asked = true; launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
        }
    }

    // Focus timer heartbeat
    LaunchedEffect(FocusTimer.running) {
        while (FocusTimer.running) {
            FocusTimer.now = System.currentTimeMillis()
            if (FocusTimer.remaining() <= 0) {
                val wasFocus = FocusTimer.finish(ctx)
                Feedback.done(ctx)
                UiState.toast(if (wasFocus) "Session geschafft. Kurz durchatmen." else "Pause vorbei. Weiter geht’s.")
                break
            }
            delay(250)
        }
    }

    BackHandler(enabled = tab != Tab.HEUTE && UiState.sheet == null) { tab = Tab.HEUTE }

    val scheme = if (dark) darkColorScheme(primary = p.accent, onPrimary = p.onAcc, surface = p.glass2, onSurface = p.fg, background = p.bg, surfaceContainerHigh = p.glass2)
    else lightColorScheme(primary = p.accent, onPrimary = p.onAcc, surface = p.glass2, onSurface = p.fg, background = p.bg, surfaceContainerHigh = p.glass2)

    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPal provides p, LocalContentColor provides p.fg, LocalTextStyle provides Body) {
            Box(Modifier.fillMaxSize()) {
                Aurora()
                AnimatedContent(
                    tab, label = "tabs",
                    transitionSpec = { (fadeIn(tween(240)) + slideInVertically(tween(300)) { it / 40 }) togetherWith fadeOut(tween(120)) },
                ) { t ->
                    when (t) {
                        Tab.HEUTE -> TodayScreen { tab = it }
                        Tab.AUFGABEN -> TasksScreen()
                        Tab.NOTIZEN -> NotesScreen()
                        Tab.GELD -> MoneyScreen()
                        Tab.FOKUS -> FocusScreen()
                    }
                }

                AnimatedVisibility(
                    visible = tab == Tab.AUFGABEN || tab == Tab.NOTIZEN || tab == Tab.GELD,
                    enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 92.dp),
                ) {
                    RoundBtn(Ic.plus, "Neu", size = 58.dp) {
                        UiState.sheet = when (tab) {
                            Tab.NOTIZEN -> Sheet.NoteEdit(null)
                            Tab.GELD -> Sheet.MoneyEdit(null)
                            else -> Sheet.TaskEdit(null)
                        }
                    }
                }

                TabBar(tab, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 14.dp)) { tab = it }
                ToastHost(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 92.dp, start = 16.dp, end = 16.dp))
            }
            SheetHost()
        }
    }
}

@Composable
private fun TabBar(current: Tab, modifier: Modifier, onSelect: (Tab) -> Unit) {
    val p = LocalPal.current
    val w = 66.dp
    val x by animateDpAsState(w * current.ordinal, spring(dampingRatio = 0.66f, stiffness = 420f), label = "lens")
    Box(modifier.shadow(18.dp, CircleShape, ambientColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f), spotColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.25f))
        .glass(p.copy(glass = p.glass2.copy(alpha = 0.94f)), CircleShape).padding(6.dp)) {
        Box(
            Modifier.offset(x = x).width(w).height(54.dp).clip(CircleShape).background(p.accentSoft)
                .border(1.dp, p.accent.copy(alpha = 0.18f), CircleShape),
        )
        Row {
            Tab.entries.forEach { t ->
                val c by animateColorAsState(if (t == current) p.accent else p.muted, label = "tabc")
                Column(
                    Modifier.width(w).height(54.dp).clip(CircleShape).clickable(role = Role.Tab) { onSelect(t) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    Icon(t.icon, null, tint = c, modifier = Modifier.size(23.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, color = c, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ToastHost(modifier: Modifier) {
    val p = LocalPal.current
    val t = UiState.toastData
    val last = remember { arrayOfNulls<ToastData>(1) }
    if (t != null) last[0] = t
    LaunchedEffect(t) {
        if (t != null) { delay(4200); if (UiState.toastData === t) UiState.toastData = null }
    }
    AnimatedVisibility(
        visible = t != null, modifier = modifier,
        enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        val d = last[0] ?: return@AnimatedVisibility
        Row(
            Modifier.widthIn(max = 480.dp).shadow(14.dp, CircleShape).clip(CircleShape).background(p.glass2)
                .border(1.dp, p.edge, CircleShape).padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(d.text, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
            if (d.undo != null) {
                Spacer(Modifier.width(10.dp))
                Text(
                    "Rückgängig", color = p.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    modifier = Modifier.clip(CircleShape).clickable { d.undo.invoke(); UiState.toastData = null }.padding(horizontal = 10.dp, vertical = 8.dp),
                )
            } else Spacer(Modifier.width(10.dp))
        }
    }
}
