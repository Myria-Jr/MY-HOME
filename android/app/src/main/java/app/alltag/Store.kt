package app.alltag

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.min
import kotlin.math.roundToInt

fun uid(): String = UUID.randomUUID().toString().replace("-", "").take(12)

data class Task(
    val id: String = uid(),
    val space: String,
    val title: String,
    val due: String = "",
    val prio: Int = 0,
    val done: Boolean = false,
    val created: Long = System.currentTimeMillis(),
    val ex: Boolean = false,
)

data class Note(
    val id: String = uid(),
    val space: String,
    val title: String,
    val body: String,
    val updated: Long = System.currentTimeMillis(),
    val ex: Boolean = false,
)

data class Entry(
    val id: String = uid(),
    val space: String,
    val type: String, // "in" | "out"
    val amount: Double,
    val cat: String,
    val label: String,
    val date: String,
    val ex: Boolean = false,
)

val CATS: Map<String, Map<String, List<String>>> = mapOf(
    "privat" to mapOf(
        "out" to listOf("Wohnen", "Essen", "Mobilität", "Abos", "Freizeit", "Gesundheit", "Sonstiges"),
        "in" to listOf("Gehalt", "Rückzahlung", "Sonstiges"),
    ),
    "arbeit" to mapOf(
        "out" to listOf("Software", "Reise", "Büro", "Marketing", "Sonstiges"),
        "in" to listOf("Umsatz", "Sonstiges"),
    ),
)

object Store {
    private var file: File? = null

    var space by mutableStateOf("privat")
    var name by mutableStateOf("")
    var exFocus by mutableStateOf(false)
    val tasks = mutableStateListOf<Task>()
    val notes = mutableStateListOf<Note>()
    val money = mutableStateListOf<Entry>()
    val focus = mutableStateMapOf<String, Int>()

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "alltag.json")
        file = f
        val ok = f.exists() && runCatching { load(JSONObject(f.readText())) }.isSuccess
        if (!ok) {
            seed(); save()
        }
    }

    fun save() {
        val f = file ?: return
        runCatching {
            val tmp = File(f.parentFile, "alltag.json.tmp")
            tmp.writeText(toJson().toString())
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText()); tmp.delete()
            }
        }
    }

    /* ---- queries ---- */
    fun hasExamples() = exFocus || tasks.any { it.ex } || notes.any { it.ex } || money.any { it.ex }

    /* ---- mutations ---- */
    fun setSpaceTo(s: String) { space = s; save() }
    fun rename(n: String) { name = n.take(40); save() }

    fun putTask(t: Task) { val i = tasks.indexOfFirst { it.id == t.id }; if (i >= 0) tasks[i] = t else tasks.add(t); save() }
    fun removeTask(t: Task): Int { val i = tasks.indexOfFirst { it.id == t.id }; if (i >= 0) tasks.removeAt(i); save(); return i }
    fun restoreTask(i: Int, t: Task) { tasks.add(i.coerceIn(0, tasks.size), t); save() }

    fun putNote(n: Note) { val i = notes.indexOfFirst { it.id == n.id }; if (i >= 0) notes[i] = n else notes.add(n); save() }
    fun removeNote(n: Note): Int { val i = notes.indexOfFirst { it.id == n.id }; if (i >= 0) notes.removeAt(i); save(); return i }
    fun restoreNote(i: Int, n: Note) { notes.add(i.coerceIn(0, notes.size), n); save() }

    fun putEntry(e: Entry) { val i = money.indexOfFirst { it.id == e.id }; if (i >= 0) money[i] = e else money.add(e); save() }
    fun removeEntry(e: Entry): Int { val i = money.indexOfFirst { it.id == e.id }; if (i >= 0) money.removeAt(i); save(); return i }
    fun restoreEntry(i: Int, e: Entry) { money.add(i.coerceIn(0, money.size), e); save() }

    fun addFocusSession() { val k = today().toString(); focus[k] = (focus[k] ?: 0) + 1; save() }

    fun clearExamples() {
        tasks.removeAll { it.ex }; notes.removeAll { it.ex }; money.removeAll { it.ex }
        if (exFocus) { focus.clear(); exFocus = false }
        save()
    }

    fun wipe() {
        tasks.clear(); notes.clear(); money.clear(); focus.clear(); exFocus = false
        save()
    }

    /* ---- json ---- */
    private fun toJson() = JSONObject().apply {
        put("v", 1); put("space", space); put("name", name); put("exFocus", exFocus)
        put("tasks", JSONArray().apply {
            tasks.forEach { t ->
                put(JSONObject().put("id", t.id).put("space", t.space).put("title", t.title).put("due", t.due)
                    .put("prio", t.prio).put("done", t.done).put("created", t.created).put("ex", t.ex))
            }
        })
        put("notes", JSONArray().apply {
            notes.forEach { n ->
                put(JSONObject().put("id", n.id).put("space", n.space).put("title", n.title).put("body", n.body)
                    .put("updated", n.updated).put("ex", n.ex))
            }
        })
        put("money", JSONArray().apply {
            money.forEach { e ->
                put(JSONObject().put("id", e.id).put("space", e.space).put("type", e.type).put("amount", e.amount)
                    .put("cat", e.cat).put("label", e.label).put("date", e.date).put("ex", e.ex))
            }
        })
        put("focus", JSONObject().apply { focus.forEach { (k, v) -> put(k, v) } })
    }

    private fun load(o: JSONObject) {
        val t = mutableListOf<Task>(); val n = mutableListOf<Note>(); val m = mutableListOf<Entry>(); val f = mutableMapOf<String, Int>()
        o.optJSONArray("tasks")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).run {
                t += Task(getString("id"), getString("space"), getString("title"), optString("due"), optInt("prio"),
                    optBoolean("done"), optLong("created"), optBoolean("ex"))
            }
        }
        o.optJSONArray("notes")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).run {
                n += Note(getString("id"), getString("space"), optString("title"), optString("body"), optLong("updated"), optBoolean("ex"))
            }
        }
        o.optJSONArray("money")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).run {
                m += Entry(getString("id"), getString("space"), getString("type"), getDouble("amount"), optString("cat"),
                    optString("label"), getString("date"), optBoolean("ex"))
            }
        }
        o.optJSONObject("focus")?.let { j -> j.keys().forEach { k -> f[k] = j.optInt(k) } }
        space = o.optString("space", "privat"); name = o.optString("name"); exFocus = o.optBoolean("exFocus")
        tasks.clear(); tasks.addAll(t); notes.clear(); notes.addAll(n); money.clear(); money.addAll(m); focus.clear(); focus.putAll(f)
    }

    /* ---- example data ---- */
    private fun seed() {
        val t = today()
        val now = System.currentTimeMillis()
        fun dom(d: Int) = t.withDayOfMonth(min(d, t.dayOfMonth)).toString()
        fun T(s: String, title: String, due: LocalDate, prio: Int = 0, done: Boolean = false) =
            Task(space = s, title = title, due = due.toString(), prio = prio, done = done, created = now, ex = true)
        fun N(s: String, title: String, body: String, minsAgo: Long) =
            Note(space = s, title = title, body = body, updated = now - minsAgo * 60_000, ex = true)
        fun M(s: String, type: String, amt: Double, cat: String, label: String, date: String) =
            Entry(space = s, type = type, amount = amt, cat = cat, label = label, date = date, ex = true)

        tasks.clear(); notes.clear(); money.clear(); focus.clear()
        tasks += listOf(
            T("privat", "Steuererklärung anfangen", t.minusDays(1), 1),
            T("privat", "Zahnarzttermin ausmachen", t),
            T("privat", "Wäsche abholen", t),
            T("privat", "Geburtstagsgeschenk für Lena", t.plusDays(3), 1),
            T("privat", "Pflanzen gießen", t, done = true),
            T("arbeit", "Angebot an Müller GmbH schicken", t, 2),
            T("arbeit", "Team-Meeting vorbereiten", t),
            T("arbeit", "Rechnung 2026-114 stellen", t.plusDays(1), 1),
            T("arbeit", "Reisekosten einreichen", t.plusDays(5)),
            T("arbeit", "Quartalszahlen prüfen", t, done = true),
        )
        notes += listOf(
            N("privat", "Einkaufsliste", "Hafermilch\nTomaten\nKaffee\nKnäckebrot\nBasilikum", 40),
            N("privat", "Ideen fürs Wochenende", "Flohmarkt am Samstag\nNeue Folge animieren – Walk-Cycle üben\nSonntag: Freunde zum Kochen", 60 * 26),
            N("privat", "Gäste-WLAN", "Netz: Zuhause-Gast\nPasswort klebt hinten am Router", 60 * 24 * 6),
            N("arbeit", "Meeting-Notizen", "Launch auf Februar verschoben\nAnna übernimmt das Onboarding\nNächster Termin: Donnerstag 10:00", 90),
            N("arbeit", "Kundendaten", "Müller GmbH – Kd.-Nr. 40211\nZahlungsziel 14 Tage\nAnsprechpartnerin: Frau Weber", 60 * 24 * 3),
        )
        money += listOf(
            M("privat", "in", 2450.0, "Gehalt", "Gehalt", dom(1)),
            M("privat", "out", 820.0, "Wohnen", "Miete", dom(1)),
            M("privat", "out", 10.99, "Abos", "Musik-Abo", dom(3)),
            M("privat", "out", 64.30, "Essen", "Wocheneinkauf", dom(8)),
            M("privat", "out", 58.0, "Mobilität", "Tanken", dom(11)),
            M("privat", "out", 24.0, "Freizeit", "Kino", dom(14)),
            M("privat", "out", 38.75, "Essen", "Wocheneinkauf", dom(15)),
            M("arbeit", "in", 1785.0, "Umsatz", "Rechnung 2026-112", dom(4)),
            M("arbeit", "in", 952.0, "Umsatz", "Rechnung 2026-113", dom(12)),
            M("arbeit", "out", 59.50, "Software", "Software-Lizenz", dom(2)),
            M("arbeit", "out", 89.90, "Reise", "Bahnticket Köln", dom(9)),
            M("arbeit", "out", 23.80, "Büro", "Büromaterial", dom(13)),
        )
        focus[t.minusDays(1).toString()] = 4
        focus[t.minusDays(2).toString()] = 2
        focus[t.minusDays(3).toString()] = 5
        focus[t.minusDays(5).toString()] = 3
        focus[t.toString()] = 1
        exFocus = true
        space = "privat"
    }
}

/* ---------- formatting ---------- */
fun today(): LocalDate = LocalDate.now()

private val EUR: NumberFormat = NumberFormat.getCurrencyInstance(Locale.GERMANY)
fun eur(v: Double): String = EUR.format(v)

val FMT_LONG: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
val FMT_SHORT: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d. MMM", Locale.GERMAN)
val FMT_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)
val FMT_WD: DateTimeFormatter = DateTimeFormatter.ofPattern("EE", Locale.GERMAN)

fun relDay(key: String): String {
    val t = today()
    val d = runCatching { LocalDate.parse(key) }.getOrNull() ?: return key
    return when (d) {
        t -> "Heute"
        t.plusDays(1) -> "Morgen"
        t.minusDays(1) -> "Gestern"
        else -> FMT_SHORT.format(d)
    }
}

fun ago(ts: Long): String {
    val m = ((System.currentTimeMillis() - ts) / 60_000.0).roundToInt()
    if (m < 1) return "gerade eben"
    if (m < 60) return "vor $m Min."
    val h = (m / 60.0).roundToInt()
    if (h < 24) return "vor $h Std."
    return relDay(java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString())
}

fun parseAmount(raw: String): Double? {
    var v = raw.trim().replace("€", "").replace(" ", "")
    if (v.contains(',')) v = v.replace(".", "").replace(',', '.')
    val n = v.toDoubleOrNull() ?: return null
    return (n * 100).roundToInt() / 100.0
}

fun greeting(): String {
    val h = java.time.LocalTime.now().hour
    val g = when {
        h < 5 -> "Gute Nacht"
        h < 11 -> "Guten Morgen"
        h < 17 -> "Hallo"
        h < 22 -> "Guten Abend"
        else -> "Gute Nacht"
    }
    return if (Store.name.isNotBlank()) "$g, ${Store.name}" else g
}
