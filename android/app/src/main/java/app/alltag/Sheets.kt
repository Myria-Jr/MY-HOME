package app.alltag

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost() {
    val s = UiState.sheet ?: return
    val p = LocalPal.current
    key(s) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val scope = rememberCoroutineScope()
        val close: () -> Unit = {
            scope.launch { state.hide() }.invokeOnCompletion { if (UiState.sheet == s) UiState.sheet = null }
        }
        ModalBottomSheet(
            onDismissRequest = { if (UiState.sheet == s) UiState.sheet = null },
            sheetState = state,
            containerColor = p.glass2,
            contentColor = p.fg,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(38.dp, 5.dp).clip(CircleShape).background(p.hair)) },
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
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
private fun SheetHead(title: String, close: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = H2.copy(fontSize = 20.sp), modifier = Modifier.weight(1f))
        CircleBtn(Ic.close, "Schließen", size = 38.dp, onClick = close)
    }
}

@Composable
private fun Actions(onDelete: (() -> Unit)?, primary: String, enabled: Boolean = true, onPrimary: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onDelete != null) Btn("Löschen", danger = true, onClick = onDelete)
        Spacer(Modifier.weight(1f))
        Btn(primary, primary = true, enabled = enabled, onClick = onPrimary)
    }
}

@Composable
private fun autoFocus(enabled: Boolean): FocusRequester {
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (enabled) { delay(300); runCatching { fr.requestFocus() } } }
    return fr
}

/* ---------- date picking ---------- */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DateChips(value: String, allowNone: Boolean, presets: List<Pair<String, LocalDate>>, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val presetKeys = presets.map { it.second.toString() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { (label, d) -> Chip(label, value == d.toString()) { onChange(d.toString()) } }
        if (allowNone) Chip("Ohne Datum", value.isEmpty()) { onChange("") }
        val custom = value.isNotEmpty() && value !in presetKeys
        Chip(if (custom) relDay(value) else "Datum wählen …", custom) { picking = true }
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskSheet(id: String?, close: () -> Unit) {
    val existing = remember(id) { id?.let { i -> Store.tasks.firstOrNull { it.id == i } } }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var due by remember { mutableStateOf(existing?.due ?: today().toString()) }
    var prio by remember { mutableIntStateOf(existing?.prio ?: 0) }
    val fr = autoFocus(existing == null)
    val save = {
        if (title.isNotBlank()) {
            if (existing != null) Store.putTask(existing.copy(title = title.trim(), due = due, prio = prio, ex = false))
            else Store.putTask(Task(space = Store.space, title = title.trim(), due = due, prio = prio))
            close()
        }
    }

    SheetHead(if (existing != null) "Aufgabe" else "Neue Aufgabe", close)
    Column {
        FieldLabel("Was?")
        Field(
            title, { title = it }, "z. B. Rechnung bezahlen", Modifier.focusRequester(fr),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
    Column {
        FieldLabel("Fällig")
        DateChips(due, allowNone = true, presets = listOf("Heute" to today(), "Morgen" to today().plusDays(1), "In einer Woche" to today().plusDays(7))) { due = it }
    }
    Column {
        FieldLabel("Wichtigkeit")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "Normal", 1 to "Wichtig", 2 to "Dringend").forEach { (v, l) -> Chip(l, prio == v) { prio = v } }
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
        onPrimary = save,
    )
}

/* ---------- note ---------- */
@Composable
private fun NoteSheet(id: String?, close: () -> Unit) {
    val p = LocalPal.current
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

    SheetHead(if (start != null) "Notiz" else "Neue Notiz", close)
    Field(
        title, { title = it; sync() }, "Titel", Modifier.focusRequester(fr),
        textStyle = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Field(
        body, { body = it; sync() }, "Schreib einfach los …", singleLine = false, minLines = 9,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
    Text("Wird automatisch gespeichert.", style = Small, color = p.muted)
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoneySheet(id: String?, close: () -> Unit) {
    val p = LocalPal.current
    val existing = remember(id) { id?.let { i -> Store.money.firstOrNull { it.id == i } } }
    val sp = Store.space
    var type by remember { mutableStateOf(existing?.type ?: "out") }
    var amount by remember { mutableStateOf(existing?.amount?.let { "%.2f".format(java.util.Locale.GERMANY, it) } ?: "") }
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var cat by remember { mutableStateOf(existing?.cat ?: CATS.getValue(sp).getValue("out").first()) }
    var date by remember { mutableStateOf(existing?.date ?: today().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    val fr = autoFocus(existing == null)
    val cats = CATS.getValue(sp).getValue(type)

    SheetHead(if (existing != null) "Buchung" else "Neue Buchung", close)
    Segmented(listOf("out" to "Ausgabe", "in" to "Einnahme"), type, {
        if (it != type) { type = it; cat = CATS.getValue(sp).getValue(it).first() }
    }, itemWidth = 104.dp)
    Column {
        FieldLabel("Betrag in Euro" + if (sp == "arbeit") " (brutto)" else "")
        Field(
            amount, { amount = it; error = null }, "0,00", Modifier.focusRequester(fr),
            textStyle = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        if (error != null) Text(error!!, color = p.bad, style = Small, modifier = Modifier.padding(top = 6.dp))
    }
    Column {
        FieldLabel("Wofür?")
        Field(label, { label = it }, if (type == "in") "z. B. Rechnung 2026-115" else "z. B. Wocheneinkauf",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
    }
    Column {
        FieldLabel("Kategorie")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEach { c -> Chip(c, cat == c) { cat = c } }
        }
    }
    Column {
        FieldLabel("Datum")
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
        val data = Entry(id = existing?.id ?: uid(), space = sp, type = type, amount = a, cat = cat, label = label.trim(), date = date)
        Store.putEntry(data)
        UiState.month = java.time.YearMonth.from(LocalDate.parse(date))
        close()
    }
}

/* ---------- settings ---------- */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSheet(close: () -> Unit) {
    val p = LocalPal.current
    val clipboard = LocalClipboardManager.current
    var name by remember { mutableStateOf(Store.name) }
    var armed by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    SheetHead("Einstellungen", close)
    Column {
        FieldLabel("Dein Name")
        Field(name, { name = it; Store.rename(it.trim()) }, "Für die Begrüßung",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    }
    Column {
        FieldLabel("Daten")
        Text("Alles bleibt auf diesem Handy. Kein Konto, keine Cloud.", style = Small, color = p.muted)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("Zusammenfassung kopieren") {
                val sb = StringBuilder("Alltag – ${today()}\n")
                listOf("privat" to "Privat", "arbeit" to "Arbeit").forEach { (k, l) ->
                    sb.append("\n== $l ==\nOffene Aufgaben:\n")
                    Store.tasks.filter { it.space == k && !it.done }.forEach { sb.append("• ${it.title}${if (it.due.isNotEmpty()) " (${relDay(it.due)})" else ""}\n") }
                    sb.append("Notizen:\n")
                    Store.notes.filter { it.space == k }.forEach { sb.append("• ${it.title}\n") }
                }
                clipboard.setText(AnnotatedString(sb.toString()))
                msg = "In die Zwischenablage kopiert."
            }
            if (Store.hasExamples()) Btn("Beispiele entfernen") { Store.clearExamples(); msg = "Beispiele entfernt." }
            Btn(if (armed) "Wirklich alles löschen?" else "Alles löschen", danger = true) {
                if (armed) { Store.wipe(); close(); UiState.toast("Alles gelöscht. Frischer Start.") } else armed = true
            }
        }
        if (msg != null) Text(msg!!, style = Small, color = p.muted, modifier = Modifier.padding(top = 8.dp))
    }
    Text("Alltag 1.0", style = Small, color = p.faint, modifier = Modifier.padding(top = 4.dp))
    Row { Spacer(Modifier.weight(1f)); Btn("Fertig", primary = true, onClick = close) }
    Spacer(Modifier.width(0.dp))
}
