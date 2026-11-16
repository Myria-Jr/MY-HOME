@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.alltag

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.toShape

/* ---------- shared ---------- */
@Composable
fun listPadding(inner: PaddingValues): PaddingValues {
    val ld = LocalLayoutDirection.current
    return PaddingValues(
        start = inner.calculateStartPadding(ld) + 16.dp, end = inner.calculateEndPadding(ld) + 16.dp,
        top = inner.calculateTopPadding() + 4.dp, bottom = inner.calculateBottomPadding() + 96.dp,
    )
}

@Composable
fun ScreenList(inner: PaddingValues, content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = listPadding(inner),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

fun spaceName() = if (Store.space == "arbeit") "Arbeit" else "Privat"

private fun LazyListScope.gap(h: Int = 12) = item(contentType = "gap") { Spacer(Modifier.height(h.dp)) }

private fun LazyListScope.taskGroup(name: String, list: List<Task>) {
    item(key = "h-$name") { SectionHeader("$name · ${list.size}", Modifier.animateItem()) }
    itemsIndexed(list, key = { _, t -> t.id }) { i, t -> TaskItem(t, posOf(i, list.size), Modifier.animateItem()) }
}

/* ---------- Heute ---------- */
@Composable
fun TodayScreen(inner: PaddingValues, go: (Tab) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val t = today().toString()
    val sp = Store.space
    val open = Store.tasks.filter { it.space == sp && !it.done && it.due.isNotEmpty() && it.due <= t }
        .sortedWith(compareBy<Task> { it.due }.thenByDescending { it.prio })
    val doneToday = Store.tasks.count { it.space == sp && it.done && it.due == t }
    val (inSum, outSum) = monthTotals(sp, t.take(7))
    val note = Store.notes.filter { it.space == sp }.maxByOrNull { it.updated }
    val sessions = Store.focus[t] ?: 0
    var q by remember { mutableStateOf("") }
    val add = {
        if (q.isNotBlank()) {
            Store.putTask(Task(space = sp, title = q.trim(), due = t)); q = ""
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            UiState.toast("Für heute eingeplant")
        }
    }

    ScreenList(inner) {
        if (Store.hasExamples()) item(key = "ex") {
            Surface(Modifier.fillMaxWidth().padding(bottom = 12.dp).animateItem(), shape = RoundedCornerShape(24.dp), color = cs.secondaryContainer) {
                Row(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Das sind Beispieldaten zum Ausprobieren.", style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer, modifier = Modifier.weight(1f))
                    Button(onClick = { Store.clearExamples(); UiState.toast("Beispiele entfernt. Jetzt gehört die App dir.") }, shapes = ButtonDefaults.shapes()) { Text("Entfernen") }
                }
            }
        }
        item(key = "quick") {
            PillField(
                q, { q = it }, if (sp == "arbeit") "Was steht bei der Arbeit an?" else "Was steht heute an?",
                leading = Ic.plus,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailing = {
                    if (q.isNotBlank()) FilledIconButton(onClick = add, shapes = IconButtonDefaults.shapes()) { Icon(Ic.check, "Hinzufügen", Modifier.size(20.dp)) }
                },
            )
        }
        item(key = "h-today") { SectionHeader("Heute fällig · ${open.size}", Modifier.animateItem(), action = "Alle") { go(Tab.AUFGABEN) } }
        if (open.isEmpty()) item(key = "empty") {
            Surface(Modifier.animateItem(), shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer) {
                EmptyState(MaterialShapes.Sunny, Ic.check, "Alles erledigt", if (doneToday > 0) "$doneToday heute geschafft. Gönn dir was." else "Gönn dir was. Oder plan den nächsten Tag.")
            }
        }
        val shown = open.take(6)
        itemsIndexed(shown, key = { _, x -> x.id }) { i, x -> TaskItem(x, posOf(i, shown.size), Modifier.animateItem()) }
        if (open.size > 6) item(key = "more") {
            Text("+ ${open.size - 6} weitere", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        gap(16)
        item(key = "cards") {
            Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Money
                Surface(onClick = { go(Tab.GELD) }, modifier = Modifier.weight(1f).height(176.dp), shape = RoundedCornerShape(32.dp), color = cs.primaryContainer) {
                    Column(Modifier.padding(18.dp)) {
                        Text(FMT_MONTH.format(today()).substringBefore(' '), style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
                        Spacer(Modifier.weight(1f))
                        Text("Saldo", style = MaterialTheme.typography.labelMedium, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                        Text(
                            eur(inSum - outSum), style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onPrimaryContainer,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        val ratio = if (inSum + outSum > 0) (inSum / (inSum + outSum)).toFloat() else 0.5f
                        LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth(), color = cs.primary, trackColor = cs.onPrimaryContainer.copy(alpha = 0.18f))
                    }
                }
                // Focus
                Surface(onClick = { go(Tab.FOKUS) }, modifier = Modifier.weight(1f).height(176.dp), shape = RoundedCornerShape(32.dp), color = cs.tertiaryContainer) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Fokus", style = MaterialTheme.typography.labelLarge, color = cs.onTertiaryContainer, modifier = Modifier.weight(1f))
                            if (FocusTimer.running) LoadingIndicator(Modifier.size(28.dp), color = cs.onTertiaryContainer)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(clock(FocusTimer.remaining()), style = MaterialTheme.typography.headlineMediumEmphasized, color = cs.onTertiaryContainer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$sessions heute", style = MaterialTheme.typography.labelMedium, color = cs.onTertiaryContainer.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                            FilledIconButton(
                                onClick = {
                                    if (!FocusTimer.running) UiState.askNotifications()
                                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                    FocusTimer.toggle(ctx)
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = cs.tertiary, contentColor = cs.onTertiary),
                            ) { Icon(if (FocusTimer.running) Ic.pause else Ic.play, "Timer starten oder pausieren", Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
        gap(10)
        item(key = "note") {
            Surface(
                onClick = { UiState.sheet = Sheet.NoteEdit(note?.id) }, modifier = Modifier.fillMaxWidth().animateItem(),
                shape = RoundedCornerShape(32.dp), color = cs.secondaryContainer,
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Zuletzt notiert", style = MaterialTheme.typography.labelLarge, color = cs.onSecondaryContainer)
                    Spacer(Modifier.height(8.dp))
                    if (note == null) Text("Noch leer. Tipp hier und halte einen Gedanken fest.", color = cs.onSecondaryContainer)
                    else {
                        Text(note.title.ifBlank { "Ohne Titel" }, style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onSecondaryContainer)
                        if (note.body.isNotBlank()) Text(note.body, style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text(ago(note.updated), style = MaterialTheme.typography.labelSmall, color = cs.onSecondaryContainer.copy(alpha = 0.7f), modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }
}

/* ---------- Aufgaben ---------- */
@Composable
fun TasksScreen(inner: PaddingValues) {
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

    ScreenList(inner) {
        item(key = "chips") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("offen" to "Offen · ${open.size}", "heute" to "Heute", "erledigt" to "Erledigt").forEach { (k, l) ->
                    val sel = UiState.taskFilter == k
                    FilterChip(
                        selected = sel, onClick = { UiState.taskFilter = k }, label = { Text(l) },
                        leadingIcon = if (sel) ({ Icon(Ic.check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                        shape = CircleShape,
                    )
                }
            }
        }
        if (groups.isEmpty()) item(key = "empty") {
            if (UiState.taskFilter == "erledigt") EmptyState(MaterialShapes.Clover4Leaf, Ic.tasks, "Noch nichts erledigt", "Das ändert sich gleich.")
            else EmptyState(MaterialShapes.Sunny, Ic.check, "Nichts offen", "Tippe auf + für eine neue Aufgabe.")
        }
        groups.forEach { (n, l) -> taskGroup(n, l) }
        item(key = "hint") {
            Text("Tipp: Nach links wischen zum Löschen.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 14.dp))
        }
    }
}

/* ---------- Notizen ---------- */
@Composable
fun NotesScreen(inner: PaddingValues) {
    val cs = MaterialTheme.colorScheme
    val sp = Store.space
    val all = Store.notes.filter { it.space == sp }
    val q = UiState.noteQuery.trim().lowercase()
    val list = all.filter { q.isEmpty() || (it.title + " " + it.body).lowercase().contains(q) }.sortedByDescending { it.updated }
    val palette = listOf(
        cs.surfaceContainerHigh to cs.onSurface, cs.primaryContainer to cs.onPrimaryContainer,
        cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer,
    )

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(160.dp),
            modifier = Modifier.widthIn(max = 760.dp).fillMaxSize(),
            contentPadding = listPadding(inner),
            verticalItemSpacing = 10.dp,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = StaggeredGridItemSpan.FullLine) {
                PillField(UiState.noteQuery, { UiState.noteQuery = it }, "Notizen durchsuchen", leading = Ic.search, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search))
            }
            if (list.isEmpty()) item(span = StaggeredGridItemSpan.FullLine) {
                if (q.isNotEmpty()) EmptyState(MaterialShapes.Ghostish, Ic.search, "Nichts gefunden", "Versuch ein anderes Wort.")
                else EmptyState(MaterialShapes.Flower, Ic.notes, "Noch keine Notizen", "Tippe auf + und schreib los.")
            }
            items(list, key = { it.id }) { n ->
                val (bg, fg) = palette[Math.floorMod(n.id.hashCode(), palette.size)]
                Surface(onClick = { UiState.sheet = Sheet.NoteEdit(n.id) }, modifier = Modifier.animateItem(), shape = RoundedCornerShape(28.dp), color = bg, contentColor = fg) {
                    Column(Modifier.padding(18.dp)) {
                        Text(n.title.ifBlank { "Ohne Titel" }, style = MaterialTheme.typography.titleMediumEmphasized)
                        if (n.body.isNotBlank()) Text(n.body, style = MaterialTheme.typography.bodyMedium, color = fg.copy(alpha = 0.82f), maxLines = 8, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text(ago(n.updated), style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.65f), modifier = Modifier.padding(top = 12.dp))
                    }
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

private fun catIndex(space: String, cat: String): Int {
    val all = CATS.getValue(space).getValue("out") + CATS.getValue(space).getValue("in")
    return all.indexOf(cat).coerceAtLeast(0)
}

@Composable
fun MoneyScreen(inner: PaddingValues) {
    val cs = MaterialTheme.colorScheme
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
    val good = Money.good
    val bad = Money.bad

    ScreenList(inner) {
        item(key = "month") {
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { UiState.month = UiState.month.minusMonths(1) }, shapes = IconButtonDefaults.shapes()) { Icon(Ic.left, "Vorheriger Monat") }
                Text(FMT_MONTH.format(UiState.month.atDay(1)), style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                FilledTonalIconButton(onClick = { UiState.month = UiState.month.plusMonths(1) }, shapes = IconButtonDefaults.shapes()) { Icon(Ic.right, "Nächster Monat") }
            }
        }
        item(key = "hero") {
            Surface(shape = RoundedCornerShape(36.dp), color = cs.primaryContainer, contentColor = cs.onPrimaryContainer) {
                Column(Modifier.fillMaxWidth().padding(22.dp)) {
                    Text("Saldo", style = MaterialTheme.typography.labelLarge)
                    Text(eur(bal), style = MaterialTheme.typography.displaySmallEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MiniStat("Rein", inSum, Ic.inArrow, good, Modifier.weight(1f))
                        MiniStat("Raus", outSum, Ic.outArrow, bad, Modifier.weight(1f))
                    }
                }
            }
        }
        if (sp == "arbeit") item(key = "vat") {
            Surface(Modifier.padding(top = 10.dp), shape = RoundedCornerShape(28.dp), color = cs.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Umsatzsteuer · geschätzt", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        VatCol("USt.", ust, Modifier.weight(1f)); VatCol("Vorsteuer", vst, Modifier.weight(1f)); VatCol("Zahllast", ust - vst, Modifier.weight(1f), strong = true)
                    }
                    Text("19 % aus Bruttobeträgen", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item(key = "h-cat") { SectionHeader("Wofür") }
        if (cats.isEmpty()) item(key = "nocat") {
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer) {
                EmptyState(MaterialShapes.Cookie12Sided, Ic.money, "Keine Ausgaben", "In diesem Monat ist noch nichts rausgegangen.")
            }
        }
        itemsIndexed(cats, key = { _, c -> "c-" + c.first }) { i, (c, v) ->
            val w by animateFloatAsState((v / max).toFloat(), tween(700), label = "bar")
            SegItem(posOf(i, cats.size), Modifier.animateItem()) {
                ShapeBadge(CategoryShapes[catIndex(sp, c) % CategoryShapes.size], cs.secondaryContainer, 40.dp) {
                    Text(c.take(1), style = MaterialTheme.typography.titleMediumEmphasized, color = cs.onSecondaryContainer)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(c, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Text(eur(v), style = MaterialTheme.typography.labelLargeEmphasized)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(progress = { w }, modifier = Modifier.fillMaxWidth(), color = cs.primary, trackColor = cs.surfaceContainerHighest)
                }
            }
        }
        item(key = "h-list") { SectionHeader("Buchungen · ${list.size}") }
        if (list.isEmpty()) item(key = "nolist") {
            Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer) {
                EmptyState(MaterialShapes.Pill, Ic.plus, "Leerer Monat", "Tippe auf + für die erste Buchung.")
            }
        }
        byDate.forEach { (d, l) ->
            item(key = "d-$d") { Text(relDay(d), style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 4.dp).animateItem()) }
            itemsIndexed(l, key = { _, e -> e.id }) { i, e ->
                SegItem(posOf(i, l.size), Modifier.animateItem(), onClick = { UiState.sheet = Sheet.MoneyEdit(e.id) }) {
                    val isIn = e.type == "in"
                    ShapeBadge(CategoryShapes[catIndex(sp, e.cat) % CategoryShapes.size], if (isIn) good.copy(alpha = 0.18f) else cs.primaryContainer, 40.dp) {
                        Icon(if (isIn) Ic.inArrow else Ic.outArrow, null, tint = if (isIn) good else cs.onPrimaryContainer, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.label.ifBlank { e.cat }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(e.cat, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                    Text((if (isIn) "+" else "−") + eur(e.amount), style = MaterialTheme.typography.titleSmallEmphasized, color = if (isIn) good else cs.onSurface)
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, v: Double, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLowest.copy(alpha = 0.55f)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                Text(eur(v), style = MaterialTheme.typography.labelLargeEmphasized, color = cs.onSurface, maxLines = 1)
            }
        }
    }
}

@Composable
private fun VatCol(label: String, v: Double, modifier: Modifier, strong: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(eur(v), style = if (strong) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium, color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
    }
}

/* ---------- Fokus ---------- */
fun clock(s: Int) = "%02d:%02d".format(s / 60, s % 60)

@Composable
fun FocusScreen(inner: PaddingValues) {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val t = today()
    val days = (6 downTo 0).map { t.minusDays(it.toLong()) }
    val counts = days.map { Store.focus[it.toString()] ?: 0 }
    val maxC = maxOf(4, counts.maxOrNull() ?: 0)
    val sessions = Store.focus[t.toString()] ?: 0
    val left = FocusTimer.remaining()
    val prog = left.toFloat() / FocusTimer.total.coerceAtLeast(1)
    val stroke = remember(density) { with(density) { Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round) } }

    ScreenList(inner) {
        item(key = "modes") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                Mode.entries.forEachIndexed { i, m ->
                    ToggleButton(
                        checked = FocusTimer.mode == m,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            if (!FocusTimer.running || m != FocusTimer.mode) FocusTimer.setMode(ctx, m)
                        },
                        shapes = when (i) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            Mode.entries.size - 1 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        modifier = Modifier.weight(if (m == Mode.LANG) 1.35f else 1f).height(48.dp),
                    ) { Text("${m.label} ${m.min}", maxLines = 1) }
                }
            }
        }
        item(key = "ring") {
            Box(Modifier.fillMaxWidth().padding(vertical = 22.dp), contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { prog },
                    modifier = Modifier.size(288.dp),
                    color = cs.primary,
                    trackColor = cs.secondaryContainer,
                    stroke = stroke,
                    trackStroke = stroke,
                    amplitude = { if (FocusTimer.running) 1f else 0f },
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(clock(left), style = MaterialTheme.typography.displayLargeEmphasized)
                    Text(
                        FocusTimer.what.ifBlank { FocusTimer.mode.label }, style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp),
                    )
                }
            }
        }
        item(key = "what") {
            OutlinedTextField(
                FocusTimer.what, { FocusTimer.what = it.take(60) }, Modifier.fillMaxWidth(),
                label = { Text("Woran arbeitest du?") }, singleLine = true, shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            )
        }
        item(key = "btns") {
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (!FocusTimer.running) UiState.askNotifications()
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        FocusTimer.toggle(ctx)
                    },
                    shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1.4f).height(64.dp),
                ) {
                    Icon(if (FocusTimer.running) Ic.pause else Ic.play, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(if (FocusTimer.running) "Pause" else "Start", style = MaterialTheme.typography.titleMediumEmphasized)
                }
                FilledTonalButton(onClick = { FocusTimer.reset(ctx) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(64.dp)) {
                    Icon(Ic.refresh, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Neu")
                }
            }
        }
        item(key = "h-week") { SectionHeader("Diese Woche · ${counts.sum()} × 25 Min.", Modifier.padding(top = 10.dp)) }
        item(key = "week") {
            Surface(shape = RoundedCornerShape(32.dp), color = cs.surfaceContainer) {
                Column(Modifier.padding(20.dp)) {
                    Row(Modifier.fillMaxWidth().height(150.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        days.forEachIndexed { i, d ->
                            val isToday = d == t
                            val h by animateFloatAsState((counts[i].toFloat() / maxC).coerceAtLeast(0.06f), tween(700), label = "wk")
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (counts[i] > 0) "${counts[i]}" else "", style = MaterialTheme.typography.labelMedium, color = if (isToday) cs.primary else cs.onSurfaceVariant)
                                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.widthIn(max = 28.dp).fillMaxWidth().fillMaxHeight(h).clip(CircleShape).background(if (isToday) cs.primary else cs.primaryContainer))
                                }
                                Text(
                                    FMT_WD.format(d).removeSuffix("."),
                                    style = if (isToday) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelMedium,
                                    color = if (isToday) cs.primary else cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Heute · $sessions", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (sessions == 0) Text("Noch keine Session. Die erste ist die schwerste.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(sessions) { i ->
                            Box(Modifier.size(22.dp).clip(CategoryShapes[i % CategoryShapes.size].toShape()).background(cs.tertiary))
                        }
                    }
                }
            }
        }
    }
}
