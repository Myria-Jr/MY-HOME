package app.alltag

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/* ---------- shared layout ---------- */
@Composable
fun screenPadding(): PaddingValues {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(start = 16.dp, end = 16.dp, top = top + 10.dp, bottom = bottom + 130.dp)
}

@Composable
fun ScreenColumn(content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = screenPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

private fun spaceName() = if (Store.space == "arbeit") "Arbeit" else "Privat"

/* ---------- Heute ---------- */
@Composable
fun TodayScreen(go: (Tab) -> Unit) {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val t = today().toString()
    val sp = Store.space
    val open = Store.tasks.filter { it.space == sp && !it.done && it.due.isNotEmpty() && it.due <= t }
        .sortedWith(compareBy<Task> { it.due }.thenByDescending { it.prio })
    val doneToday = Store.tasks.count { it.space == sp && it.done && it.due == t }
    val (inSum, outSum) = monthTotals(sp, t.take(7))
    val note = Store.notes.filter { it.space == sp }.maxByOrNull { it.updated }
    val sessions = Store.focus[t] ?: 0

    ScreenColumn {
        item { TopBar() }
        item { Header(FMT_LONG.format(today()), greeting()) }
        if (Store.hasExamples()) item {
            Row(Modifier.fillMaxWidth().glass(p, RoundedCornerShape(18.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Du siehst Beispieldaten zum Ausprobieren.", style = Small, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Btn("Entfernen") { Store.clearExamples(); UiState.toast("Beispiele entfernt. Jetzt gehört die App dir.") }
            }
        }
        item { QuickAdd() }
        item {
            GlassCard {
                CardHead("Heute fällig", "${open.size} offen", "Alle") { go(Tab.AUFGABEN) }
                if (open.isEmpty()) Empty("Alles erledigt.", if (doneToday > 0) "$doneToday heute geschafft. Gönn dir was." else "Gönn dir was. Oder plan den nächsten Tag.")
                open.take(6).forEachIndexed { i, task -> if (i > 0) Hair(); TaskRow(task) }
                if (open.size > 6) Text("+ ${open.size - 6} weitere", color = p.muted, style = Small, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            GlassCard {
                CardHead(today().month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.GERMAN), action = "Details") { go(Tab.GELD) }
                Text("SALDO", style = Label, color = p.muted)
                Text(
                    eur(inSum - outSum), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp, style = Num,
                    color = if (inSum - outSum >= 0) p.good else p.bad,
                )
                val ratio = if (inSum + outSum > 0) (inSum / (inSum + outSum)).toFloat() else 0.5f
                Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(p.bad.copy(alpha = 0.28f))) {
                    Box(Modifier.fillMaxWidth(ratio).fillMaxHeight().clip(CircleShape).background(p.good))
                }
                Row(Modifier.padding(top = 10.dp)) {
                    Text("Rein ", style = Small, color = p.muted); Text(eur(inSum), style = Small.merge(Num), fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(16.dp))
                    Text("Raus ", style = Small, color = p.muted); Text(eur(outSum), style = Small.merge(Num), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            GlassCard {
                CardHead("Fokus", action = "Öffnen") { go(Tab.FOKUS) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(clock(FocusTimer.remaining()), fontSize = 42.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, style = Num)
                        Text("$sessions ${if (sessions == 1) "Session" else "Sessions"} heute", color = p.muted, style = Small)
                    }
                    RoundBtn(if (FocusTimer.running) Ic.pause else Ic.play, "Timer starten oder pausieren", size = 54.dp) {
                        if (!FocusTimer.running) UiState.askNotifications()
                        FocusTimer.toggle(ctx)
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.tap { note?.let { UiState.sheet = Sheet.NoteEdit(it.id) } }) {
                CardHead("Zuletzt notiert", action = "Notizen") { go(Tab.NOTIZEN) }
                if (note == null) Empty("Noch leer.", "Halte Gedanken fest, bevor sie weg sind.")
                else {
                    Text(note.title.ifBlank { "Ohne Titel" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (note.body.isNotBlank()) Text(note.body, color = p.muted, maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Text(ago(note.updated), color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun QuickAdd() {
    val p = LocalPal.current
    var q by remember { mutableStateOf("") }
    val add = {
        if (q.isNotBlank()) {
            Store.putTask(Task(space = Store.space, title = q.trim(), due = today().toString()))
            q = ""
            UiState.toast("Für heute eingeplant")
        }
    }
    Row(
        Modifier.fillMaxWidth().glass(p, CircleShape).padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Field(
            q, { q = it }, if (Store.space == "arbeit") "Was steht bei der Arbeit an?" else "Was steht heute an?",
            Modifier.weight(1f), filled = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )
        RoundBtn(Ic.plus, "Hinzufügen") { add() }
    }
}

/* ---------- Aufgaben ---------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksScreen() {
    val t = today().toString()
    val tm = today().plusDays(1).toString()
    val sp = Store.space
    val mine = Store.tasks.filter { it.space == sp }
    val open = mine.filter { !it.done }
    val order = compareBy<Task> { it.due.ifEmpty { "9999" } }.thenByDescending { it.prio }
    val groups: List<Pair<String, List<Task>>> = when (UiState.taskFilter) {
        "erledigt" -> listOf("Erledigt" to mine.filter { it.done }.sortedByDescending { it.created })
        "heute" -> listOf("Überfällig" to open.filter { it.due.isNotEmpty() && it.due < t }, "Heute" to open.filter { it.due == t })
        else -> listOf(
            "Überfällig" to open.filter { it.due.isNotEmpty() && it.due < t },
            "Heute" to open.filter { it.due == t },
            "Morgen" to open.filter { it.due == tm },
            "Demnächst" to open.filter { it.due.isNotEmpty() && it.due > tm },
            "Irgendwann" to open.filter { it.due.isEmpty() },
        )
    }.map { (n, l) -> n to (if (n == "Erledigt") l else l.sortedWith(order)) }.filter { it.second.isNotEmpty() }

    ScreenColumn {
        item { TopBar() }
        item { Header("${spaceName()} · ${open.size} offen", "Aufgaben") }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("offen" to "Offen", "heute" to "Heute", "erledigt" to "Erledigt").forEach { (k, l) ->
                    Chip(l, UiState.taskFilter == k, filter = true) { UiState.taskFilter = k }
                }
            }
        }
        item {
            GlassCard(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                if (groups.isEmpty()) {
                    if (UiState.taskFilter == "erledigt") Empty("Noch nichts erledigt.", "Das ändert sich gleich.")
                    else Empty("Nichts offen.", "Tippe auf + für eine neue Aufgabe.")
                }
                groups.forEach { (name, list) ->
                    GroupTitle("$name  ${list.size}")
                    list.forEachIndexed { i, task -> if (i > 0) Hair(); TaskRow(task) }
                }
            }
        }
    }
}

/* ---------- Notizen ---------- */
@Composable
fun NotesScreen() {
    val p = LocalPal.current
    val sp = Store.space
    val all = Store.notes.filter { it.space == sp }
    val q = UiState.noteQuery.trim().lowercase()
    val list = all.filter { q.isEmpty() || (it.title + " " + it.body).lowercase().contains(q) }.sortedByDescending { it.updated }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(160.dp),
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
            contentPadding = screenPadding(),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) { TopBar() }
            item(span = StaggeredGridItemSpan.FullLine) { Header("${spaceName()} · ${all.size} Notizen", "Notizen") }
            item(span = StaggeredGridItemSpan.FullLine) {
                Row(Modifier.fillMaxWidth().glass(p, CircleShape).padding(start = 16.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Ic.search, null, tint = p.faint, modifier = Modifier.size(18.dp))
                    Field(
                        UiState.noteQuery, { UiState.noteQuery = it }, "Notizen durchsuchen", Modifier.weight(1f), filled = false,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 14.dp),
                    )
                }
            }
            if (list.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                GlassCard {
                    if (q.isNotEmpty()) Empty("Nichts gefunden.", "Versuch ein anderes Wort.")
                    else Empty("Noch keine Notizen.", "Tippe auf + und schreib los.")
                }
            }
            items(list, key = { it.id }) { n ->
                Column(Modifier.fillMaxWidth().glass(p, RoundedCornerShape(20.dp)).tap { UiState.sheet = Sheet.NoteEdit(n.id) }.padding(16.dp)) {
                    Text(n.title.ifBlank { "Ohne Titel" }, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (n.body.isNotBlank()) Text(n.body, color = p.muted, maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text(ago(n.updated), color = p.faint, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

/* ---------- Geld ---------- */
fun monthTotals(space: String, ym: String): Pair<Double, Double> {
    val l = Store.money.filter { it.space == space && it.date.startsWith(ym) }
    return l.filter { it.type == "in" }.sumOf { it.amount } to l.filter { it.type == "out" }.sumOf { it.amount }
}

@Composable
fun MoneyScreen() {
    val p = LocalPal.current
    val sp = Store.space
    val ym = UiState.month.toString()
    val list = Store.money.filter { it.space == sp && it.date.startsWith(ym) }
    val inSum = list.filter { it.type == "in" }.sumOf { it.amount }
    val outSum = list.filter { it.type == "out" }.sumOf { it.amount }
    val bal = inSum - outSum
    val cats = list.filter { it.type == "out" }.groupBy { it.cat }.mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
    val max = cats.firstOrNull()?.second ?: 1.0
    val vat = { a: Double -> a * 19 / 119 }
    val ust = list.filter { it.type == "in" }.sumOf { vat(it.amount) }
    val vst = list.filter { it.type == "out" }.sumOf { vat(it.amount) }
    val byDate = list.sortedByDescending { it.date }.groupBy { it.date }

    ScreenColumn {
        item { TopBar() }
        item { Header(spaceName(), if (sp == "arbeit") "Einnahmen & Ausgaben" else "Geld") }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleBtn(Ic.left, "Vorheriger Monat") { UiState.month = UiState.month.minusMonths(1) }
                Text(
                    FMT_MONTH.format(UiState.month.atDay(1)), style = H2, textAlign = TextAlign.Center,
                    modifier = Modifier.width(170.dp),
                )
                CircleBtn(Ic.right, "Nächster Monat") { UiState.month = UiState.month.plusMonths(1) }
            }
        }
        item {
            GlassCard {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Rein", inSum, p.good, Modifier.weight(1f))
                    Stat("Raus", outSum, p.bad, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Stat("Saldo", bal, if (bal >= 0) p.good else p.bad, Modifier.fillMaxWidth())
                if (sp == "arbeit") {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Umsatzsteuer ${eur(ust)}  ·  Vorsteuer ${eur(vst)}\nZahllast ${eur(ust - vst)}  (geschätzt, 19 % aus Brutto)",
                        style = Small.merge(Num), color = p.muted,
                    )
                }
            }
        }
        item {
            GlassCard {
                CardHead("Wofür", "Ausgaben")
                if (cats.isEmpty()) Empty("Keine Ausgaben.", "In diesem Monat ist noch nichts rausgegangen.")
                cats.forEach { (c, v) ->
                    val w by animateFloatAsState((v / max).toFloat().coerceAtLeast(0.03f), tween(600), label = "bar")
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(c, style = Small, modifier = Modifier.width(88.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f).height(10.dp).clip(CircleShape).background(p.fill)) {
                            Box(Modifier.fillMaxWidth(w).fillMaxHeight().clip(CircleShape).background(p.accent))
                        }
                        Text(eur(v), style = Small.merge(Num), modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
        item {
            GlassCard {
                CardHead("Buchungen", "${list.size}")
                if (list.isEmpty()) Empty("Leerer Monat.", "Tippe auf + für die erste Buchung.")
                byDate.forEach { (d, l) ->
                    GroupTitle(relDay(d))
                    l.forEachIndexed { i, e ->
                        if (i > 0) Hair()
                        Row(Modifier.fillMaxWidth().tap { UiState.sheet = Sheet.MoneyEdit(e.id) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            val tint = if (e.type == "in") p.good else p.accent
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                Text(e.cat.take(2), color = tint, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.label.ifBlank { e.cat }, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(e.cat, style = Small, color = p.muted)
                            }
                            Text(
                                (if (e.type == "in") "+" else "−") + eur(e.amount), fontWeight = FontWeight.SemiBold, style = Num,
                                color = if (e.type == "in") p.good else p.fg,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: Double, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val p = LocalPal.current
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(p.fill).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label.uppercase(), style = Label, color = p.muted)
        Spacer(Modifier.height(4.dp))
        Text(eur(value), fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, color = color, style = Num, maxLines = 1, softWrap = false)
    }
}

/* ---------- Fokus ---------- */
fun clock(s: Int) = "%02d:%02d".format(s / 60, s % 60)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScreen() {
    val p = LocalPal.current
    val ctx = LocalContext.current
    val t = today()
    val days = (6 downTo 0).map { t.minusDays(it.toLong()) }
    val counts = days.map { Store.focus[it.toString()] ?: 0 }
    val maxC = maxOf(4, counts.maxOrNull() ?: 0)
    val sessions = Store.focus[t.toString()] ?: 0
    val left = FocusTimer.remaining()
    val prog by animateFloatAsState(left.toFloat() / FocusTimer.total.coerceAtLeast(1), tween(300, easing = LinearEasing), label = "ring")

    ScreenColumn {
        item { TopBar() }
        item { Header("Eine Sache. Volle Aufmerksamkeit.", "Fokus") }
        item {
            GlassCard(padding = PaddingValues(horizontal = 18.dp, vertical = 24.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Mode.entries.forEach { m ->
                            Chip("${m.label} · ${m.min}", FocusTimer.mode == m) { if (!FocusTimer.running || m != FocusTimer.mode) FocusTimer.setMode(ctx, m) }
                        }
                    }
                    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val sw = 12.dp.toPx()
                            val tl = Offset(sw / 2, sw / 2)
                            val sz = Size(size.width - sw, size.height - sw)
                            drawArc(p.fill, 0f, 360f, false, tl, sz, style = Stroke(sw))
                            drawArc(p.accent, -90f, 360f * prog, false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(clock(left), fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = (-2.5).sp, style = Num)
                            Text(FocusTimer.what.ifBlank { FocusTimer.mode.label }, color = p.muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                        }
                    }
                    Field(
                        FocusTimer.what, { FocusTimer.what = it.take(60) }, "Woran arbeitest du?",
                        Modifier.widthIn(max = 340.dp),
                        textStyle = TextStyle(fontSize = 16.sp, textAlign = TextAlign.Center),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Btn(if (FocusTimer.running) "Pause" else "Start", Modifier.width(140.dp), primary = true, icon = if (FocusTimer.running) Ic.pause else Ic.play) {
                            if (!FocusTimer.running) UiState.askNotifications()
                            FocusTimer.toggle(ctx)
                        }
                        Btn("Zurücksetzen") { FocusTimer.reset(ctx) }
                    }
                }
            }
        }
        item {
            GlassCard {
                CardHead("Diese Woche", "${counts.sum()} × 25 Min.")
                Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    days.forEachIndexed { i, d ->
                        val isToday = d == t
                        val h by animateFloatAsState((counts[i].toFloat() / maxC).coerceAtLeast(0.03f), tween(600), label = "wk")
                        Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (counts[i] > 0) "${counts[i]}" else "", style = Small.merge(Num), color = if (isToday) p.fg else p.muted)
                            Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.BottomCenter) {
                                Box(
                                    Modifier.widthIn(max = 30.dp).fillMaxWidth().fillMaxHeight(h)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                                        .background(if (isToday) p.accent else p.accentSoft),
                                )
                            }
                            Text(FMT_WD.format(d).removeSuffix("."), style = Small, color = if (isToday) p.fg else p.muted, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
                GroupTitle("Heute · $sessions")
                if (sessions == 0) Text("Noch keine Session. Die erste ist die schwerste.", style = Small, color = p.muted)
                else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(sessions) { Box(Modifier.size(12.dp).clip(CircleShape).background(p.accent)) }
                }
            }
        }
    }
}
