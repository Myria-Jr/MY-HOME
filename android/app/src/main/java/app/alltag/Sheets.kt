@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package app.alltag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun SheetHost() {
    val s = UiState.sheet ?: return
    key(s) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val close: () -> Unit = {
            scope.launch { state.hide() }.invokeOnCompletion { if (UiState.sheet == s) UiState.sheet = null }
        }
        ModalBottomSheet(
            onDismissRequest = { if (UiState.sheet == s) UiState.sheet = null },
            sheetState = state,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                when (s) {
                    is Sheet.TaskEdit -> TaskSheet(s.id, close)
                    is Sheet.NoteEdit -> NoteSheet(s.id, close)
                    is Sheet.MoneyEdit -> MoneySheet(s.id, close)
                    Sheet.Settings -> SettingsSheet(close)
                }
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmallEmphasized)
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun Actions(onDelete: (() -> Unit)?, primary: String, enabled: Boolean = true, onPrimary: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) FilledTonalIconButton(
            onClick = onDelete, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
        ) { Icon(Ic.trash, "Löschen") }
        Button(onClick = onPrimary, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f).height(56.dp)) {
            Text(primary, style = MaterialTheme.typography.titleMediumEmphasized)
        }
    }
}

@Composable
private fun autoFocus(enabled: Boolean): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (enabled) { delay(350); runCatching { fr.requestFocus() } } }
    return fr
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, shape = CircleShape,
        leadingIcon = if (selected) ({ Icon(Ic.check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
    )
}

/* ---------- date picking ---------- */
@Composable
private fun DateChips(value: String, allowNone: Boolean, presets: List<Pair<String, LocalDate>>, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val presetKeys = presets.map { it.second.toString() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { (label, d) -> Choice(label, value == d.toString()) { onChange(d.toString()) } }
        if (allowNone) Choice("Ohne Datum", value.isEmpty()) { onChange("") }
        val custom = value.isNotEmpty() && value !in presetKeys
        FilterChip(
            selected = custom, onClick = { picking = true }, shape = CircleShape,
            label = { Text(if (custom) relDay(value) else "Datum wählen") },
            leadingIcon = { Icon(Ic.calendar, null, Modifier.size(FilterChipDefaults.IconSize)) },
        )
    }
    if (picking) {
        val initial = runCatching { LocalDate.parse(value) }.getOrDefault(today())
        val st = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton({
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()) }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ picking = false }) { Text("Abbrechen") } },
        ) { DatePicker(st) }
    }
}

/* ---------- task ---------- */
@Composable
private fun TaskSheet(id: String?, close: () -> Unit) {
    val existing = remember(id) { id?.let { i -> Store.tasks.firstOrNull { it.id == i } } }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var due by remember { mutableStateOf(existing?.due ?: today().toString()) }
    var prio by remember { mutableIntStateOf(existing?.prio ?: 0) }
    val fr = autoFocus(existing == null)

    SheetTitle(if (existing != null) "Aufgabe" else "Neue Aufgabe")
    OutlinedTextField(
        title, { title = it }, Modifier.fillMaxWidth().focusRequester(fr),
        label = { Text("Was ist zu tun?") }, shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Column {
        Label("Fällig")
        DateChips(due, allowNone = true, presets = listOf("Heute" to today(), "Morgen" to today().plusDays(1), "In 1 Woche" to today().plusDays(7))) { due = it }
    }
    Column {
        Label("Wichtigkeit")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            listOf(0 to "Normal", 1 to "Wichtig", 2 to "Dringend").forEachIndexed { i, (v, l) ->
                ToggleButton(
                    checked = prio == v, onCheckedChange = { prio = v },
                    shapes = when (i) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        2 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    modifier = Modifier.weight(1f),
                ) { Text(l) }
            }
        }
    }
    Actions(
        onDelete = existing?.let { t ->
            {
                val idx = Store.removeTask(t); close()
                UiState.toast("Aufgabe gelöscht") { Store.restoreTask(idx, t) }
            }
        },
        primary = if (existing != null) "Sichern" else "Hinzufügen",
        enabled = title.isNotBlank(),
    ) {
        if (existing != null) Store.putTask(existing.copy(title = title.trim(), due = due, prio = prio, ex = false))
        else Store.putTask(Task(space = Store.space, title = title.trim(), due = due, prio = prio))
        close()
    }
}

/* ---------- note ---------- */
@Composable
private fun NoteSheet(id: String?, close: () -> Unit) {
    val start = remember(id) { id?.let { i -> Store.notes.firstOrNull { it.id == i } } }
    var noteId by remember { mutableStateOf(start?.id) }
    var title by remember { mutableStateOf(start?.title ?: "") }
    var body by remember { mutableStateOf(start?.body ?: "") }
    var deleted by remember { mutableStateOf(false) }
    val fr = autoFocus(start == null)

    fun sync() {
        if (noteId == null && title.isBlank() && body.isBlank()) return
        val cur = noteId?.let { i -> Store.notes.firstOrNull { it.id == i } } ?: Note(space = Store.space, title = "", body = "")
        noteId = cur.id
        Store.putNote(cur.copy(title = title, body = body, updated = System.currentTimeMillis(), ex = false))
    }
    DisposableEffect(Unit) {
        onDispose {
            if (!deleted) {
                val cur = noteId?.let { i -> Store.notes.firstOrNull { it.id == i } }
                if (cur != null && cur.title.isBlank() && cur.body.isBlank()) Store.removeNote(cur)
            }
        }
    }
    val plain = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
    )

    TextField(
        title, { title = it; sync() }, Modifier.fillMaxWidth().focusRequester(fr),
        placeholder = { Text("Titel", style = MaterialTheme.typography.headlineSmallEmphasized) },
        textStyle = MaterialTheme.typography.headlineSmallEmphasized, colors = plain,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    TextField(
        body, { body = it; sync() }, Modifier.fillMaxWidth(),
        placeholder = { Text("Schreib einfach los …") }, minLines = 8, colors = plain,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Text("Wird automatisch gespeichert.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp))
    Actions(
        onDelete = if (noteId != null) ({
            val cur = Store.notes.firstOrNull { it.id == noteId }
            if (cur != null) {
                deleted = true
                val idx = Store.removeNote(cur); close()
                UiState.toast("Notiz gelöscht") { Store.restoreNote(idx, cur) }
            } else close()
        }) else null,
        primary = "Fertig",
        onPrimary = close,
    )
}

/* ---------- money ---------- */
@Composable
private fun MoneySheet(id: String?, close: () -> Unit) {
    val existing = remember(id) { id?.let { i -> Store.money.firstOrNull { it.id == i } } }
    val sp = Store.space
    var type by remember { mutableStateOf(existing?.type ?: "out") }
    var amount by remember { mutableStateOf(existing?.amount?.let { "%.2f".format(java.util.Locale.GERMANY, it) } ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var cat by remember { mutableStateOf(existing?.cat ?: CATS.getValue(sp).getValue("out").first()) }
    var date by remember { mutableStateOf(existing?.date ?: today().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val fr = autoFocus(existing == null)

    SheetTitle(if (existing != null) "Buchung" else "Neue Buchung")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        listOf(Triple("out", "Ausgabe", Ic.outArrow), Triple("in", "Einnahme", Ic.inArrow)).forEachIndexed { i, (k, l, ic) ->
            ToggleButton(
                checked = type == k,
                onCheckedChange = { if (type != k) { type = k; cat = CATS.getValue(sp).getValue(k).first() } },
                shapes = if (i == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { Icon(ic, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(l) }
        }
    }
    OutlinedTextField(
        amount, { amount = it; error = null }, Modifier.fillMaxWidth().focusRequester(fr),
        label = { Text("Betrag in Euro" + if (sp == "arbeit") " (brutto)" else "") },
        textStyle = MaterialTheme.typography.headlineMediumEmphasized, singleLine = true, shape = RoundedCornerShape(20.dp),
        suffix = { Text("€", style = MaterialTheme.typography.headlineSmall) },
        isError = error != null, supportingText = error?.let { e -> { Text(e) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    OutlinedTextField(
        label, { label = it }, Modifier.fillMaxWidth(),
        label = { Text("Wofür?") }, placeholder = { Text(if (type == "in") "z. B. Rechnung 2026-115" else "z. B. Wocheneinkauf") },
        singleLine = true, shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Column {
        Label("Kategorie")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CATS.getValue(sp).getValue(type).forEach { c -> Choice(c, cat == c) { cat = c } }
        }
    }
    Column {
        Label("Datum")
        DateChips(date, allowNone = false, presets = listOf("Heute" to today(), "Gestern" to today().minusDays(1))) { date = it }
    }
    Actions(
        onDelete = existing?.let { e ->
            {
                val idx = Store.removeEntry(e); close()
                UiState.toast("Buchung gelöscht") { Store.restoreEntry(idx, e) }
            }
        },
        primary = if (existing != null) "Sichern" else "Buchen",
    ) {
        val a = parseAmount(amount)
        if (a == null || a <= 0) { error = "Bitte einen Betrag größer als 0 eingeben, z. B. 12,50."; return@Actions }
        Store.putEntry(Entry(id = existing?.id ?: uid(), space = sp, type = type, amount = a, cat = cat, label = label.trim(), date = date))
        UiState.month = java.time.YearMonth.from(LocalDate.parse(date))
        close()
    }
}

/* ---------- settings ---------- */
@Composable
private fun SettingsSheet(close: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    @Suppress("DEPRECATION") val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf(Store.name) }
    var armed by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        ShapeBadge(MaterialShapes.Cookie9Sided, cs.primaryContainer, 52.dp) {
            Text(name.trim().take(1).uppercase().ifEmpty { "A" }, style = MaterialTheme.typography.titleLargeEmphasized, color = cs.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        SheetTitle("Einstellungen")
    }
    OutlinedTextField(
        name, { name = it; Store.rename(it.trim()) }, Modifier.fillMaxWidth(),
        label = { Text("Dein Name") }, placeholder = { Text("Für die Begrüßung") }, singleLine = true, shape = RoundedCornerShape(20.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
    )
    Column {
        Label("Daten")
        Text("Alles bleibt auf diesem Handy. Kein Konto, keine Cloud.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SegItem(Pos.First, color = cs.surfaceContainerHigh, onClick = {
                val sb = StringBuilder("Alltag – ${today()}\n")
                listOf("privat" to "Privat", "arbeit" to "Arbeit").forEach { (k, l) ->
                    sb.append("\n== $l ==\nOffene Aufgaben:\n")
                    Store.tasks.filter { it.space == k && !it.done }.forEach { sb.append("• ${it.title}${if (it.due.isNotEmpty()) " (${relDay(it.due)})" else ""}\n") }
                    sb.append("Notizen:\n")
                    Store.notes.filter { it.space == k }.forEach { sb.append("• ${it.title}\n") }
                }
                clipboard.setText(AnnotatedString(sb.toString()))
                msg = "In die Zwischenablage kopiert."
            }) { Text("Zusammenfassung kopieren", style = MaterialTheme.typography.bodyLarge) }
            if (Store.hasExamples()) SegItem(Pos.Middle, color = cs.surfaceContainerHigh, onClick = { Store.clearExamples(); msg = "Beispiele entfernt." }) {
                Text("Beispiele entfernen", style = MaterialTheme.typography.bodyLarge)
            }
            SegItem(Pos.Last, color = if (armed) cs.errorContainer else cs.surfaceContainerHigh, onClick = {
                if (armed) { Store.wipe(); close(); UiState.toast("Alles gelöscht. Frischer Start.") } else armed = true
            }) {
                Icon(Ic.trash, null, tint = cs.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(if (armed) "Nochmal tippen: wirklich alles löschen" else "Alles löschen", style = MaterialTheme.typography.bodyLarge, color = cs.error)
            }
        }
        if (msg != null) Text(msg!!, style = MaterialTheme.typography.labelMedium, color = cs.primary, modifier = Modifier.padding(top = 10.dp, start = 8.dp))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Alltag 2.0", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
        FilledTonalButton(onClick = close, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 24.dp)) { Text("Fertig") }
    }
}
