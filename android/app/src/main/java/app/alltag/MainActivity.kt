@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.time.YearMonth

enum class Tab(val label: String, val icon: ImageVector, val iconOn: ImageVector) {
    HEUTE("Heute", Ic.today, Ic.todayFilled),
    AUFGABEN("Aufgaben", Ic.tasks, Ic.tasksFilled),
    NOTIZEN("Notizen", Ic.notes, Ic.notesFilled),
    GELD("Geld", Ic.money, Ic.moneyFilled),
    FOKUS("Fokus", Ic.focus, Ic.focusFilled),
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
    AlltagTheme(dark = dark, work = Store.space == "arbeit") { AppScaffold(initialTab) }
}

@Composable
private fun AppScaffold(initialTab: Tab) {
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var fabOpen by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
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

    // Toasts → Material snackbars with undo
    val toast = UiState.toastData
    LaunchedEffect(toast) {
        if (toast == null) return@LaunchedEffect
        snackbar.currentSnackbarData?.dismiss()
        val r = snackbar.showSnackbar(toast.text, actionLabel = if (toast.undo != null) "Rückgängig" else null, duration = SnackbarDuration.Short)
        if (r == SnackbarResult.ActionPerformed) toast.undo?.invoke()
        if (UiState.toastData === toast) UiState.toastData = null
    }

    BackHandler(enabled = fabOpen) { fabOpen = false }
    BackHandler(enabled = !fabOpen && tab != Tab.HEUTE && UiState.sheet == null) { tab = Tab.HEUTE }

    val barState = rememberTopAppBarState()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(barState)
    LaunchedEffect(tab) { barState.heightOffset = 0f; barState.contentOffset = 0f }

    val sp = Store.space
    val (title, subtitle) = when (tab) {
        Tab.HEUTE -> greeting() to FMT_LONG.format(today())
        Tab.AUFGABEN -> "Aufgaben" to "${spaceName()} · ${Store.tasks.count { it.space == sp && !it.done }} offen"
        Tab.NOTIZEN -> "Notizen" to "${spaceName()} · ${Store.notes.count { it.space == sp }} Notizen"
        Tab.GELD -> (if (sp == "arbeit") "Finanzen" else "Geld") to "${spaceName()} · Einnahmen & Ausgaben"
        Tab.FOKUS -> "Fokus" to "Eine Sache. Volle Aufmerksamkeit."
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    SpaceToggle()
                    IconButton(onClick = { UiState.sheet = Sheet.Settings }) { Icon(Ic.gear, "Einstellungen") }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { t ->
                    val sel = t == tab
                    ShortNavigationBarItem(
                        selected = sel,
                        onClick = {
                            if (!sel) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            tab = t; fabOpen = false
                        },
                        icon = { Icon(if (sel) t.iconOn else t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            AnimatedVisibility(tab != Tab.FOKUS, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                FloatingActionButtonMenu(
                    expanded = fabOpen,
                    button = {
                        ToggleFloatingActionButton(
                            checked = fabOpen,
                            onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.ContextClick); fabOpen = it },
                        ) {
                            Icon(Ic.plus, if (fabOpen) "Menü schließen" else "Neu", Modifier.size(24.dp()).graphicsLayer { rotationZ = checkedProgress * 135f })
                        }
                    },
                ) {
                    val items = listOf(
                        Triple("Buchung", Ic.money) { UiState.sheet = Sheet.MoneyEdit(null) },
                        Triple("Notiz", Ic.notes) { UiState.sheet = Sheet.NoteEdit(null) },
                        Triple("Aufgabe", Ic.tasks) { UiState.sheet = Sheet.TaskEdit(null) },
                    )
                    items.forEach { (label, icon, action) ->
                        FloatingActionButtonMenuItem(
                            onClick = { fabOpen = false; action() },
                            icon = { Icon(icon, null) },
                            text = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        AnimatedContent(
            tab, label = "tabs",
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(380)) { it / 6 * dir } + fadeIn(tween(260))) togetherWith
                    (slideOutHorizontally(tween(300)) { -it / 8 * dir } + fadeOut(tween(160)))
            },
        ) { t ->
            when (t) {
                Tab.HEUTE -> TodayScreen(inner) { tab = it }
                Tab.AUFGABEN -> TasksScreen(inner)
                Tab.NOTIZEN -> NotesScreen(inner)
                Tab.GELD -> MoneyScreen(inner)
                Tab.FOKUS -> FocusScreen(inner)
            }
        }
    }
    SheetHost()
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
