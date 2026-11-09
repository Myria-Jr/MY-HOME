package de.myria.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Ein Chat mit Verlauf */
class Chat(
    val id: String,
    var title: String,
    var model: String,
    var persona: Int,
    val messages: MutableList<Nim.Msg> = mutableListOf(),
    var updated: Long = System.currentTimeMillis(),
    var pinned: Boolean = false,
)

/** Personas: Name, Symbol, System-Anweisung */
object Personas {
    class P(val name: String, val emoji: String, val prompt: String)
    val all = listOf(
        P("Standard", "✦", "Du bist Lumora AI, eine hilfsbereite, kluge Assistentin. Antworte auf Deutsch, klar und gut strukturiert, außer der Nutzer schreibt in einer anderen Sprache."),
        P("Tschechisch-Lehrerin", "🇨🇿", "Du bist eine geduldige Tschechisch-Lehrerin für deutschsprachige Lernende. Erkläre auf Deutsch, gib tschechische Beispiele mit Aussprache-Hilfe in Klammern und eine kurze Übung am Ende. Korrigiere Fehler freundlich."),
        P("Songwriterin", "🎵", "Du bist eine kreative Songwriterin. Hilf beim Schreiben von Songtexten: Reime, Refrains, Strophen, Stimmung, Akkord-Ideen. Antworte auf Deutsch, außer der Song soll in einer anderen Sprache sein."),
        P("Animations-Coach", "🎬", "Du bist ein erfahrener 2D/3D-Animations-Coach. Erkläre die 12 Animationsprinzipien, Timing, Spacing, Storyboards und Tools praxisnah und mit konkreten Übungen. Antworte auf Deutsch."),
        P("Programmierer", "⌘", "Du bist ein erfahrener Software-Entwickler. Antworte präzise, mit sauberen Code-Beispielen in Markdown-Codeblöcken und kurzen Erklärungen auf Deutsch."),
        P("Kurz & frech", "⚡", "Du antwortest extrem kurz, witzig und ein bisschen sarkastisch – aber trotzdem hilfreich. Deutsch, maximal 3 Sätze, außer es braucht wirklich mehr."),
        P("Übersetzer", "⇄", "Du bist ein Übersetzer. Übersetze den Text des Nutzers. Ist er Deutsch, übersetze ins Englische; sonst ins Deutsche – außer der Nutzer nennt eine Zielsprache. Gib nur die Übersetzung aus, bei Bedarf eine kurze Anmerkung."),
        P("Eigene", "✎", ""),
    )
}

object Store {
    private fun dir(ctx: Context) = File(ctx.filesDir, "chats").apply { mkdirs() }

    fun list(ctx: Context): List<Chat> =
        (dir(ctx).listFiles() ?: emptyArray()).mapNotNull { load(it) }
            .sortedWith(compareByDescending<Chat> { it.pinned }.thenByDescending { it.updated })

    private fun load(f: File): Chat? = try {
        val o = JSONObject(f.readText())
        val arr = o.optJSONArray("messages") ?: JSONArray()
        Chat(
            o.getString("id"), o.optString("title", "Chat"), o.optString("model", ""), o.optInt("persona", 0),
            (0 until arr.length()).map { arr.getJSONObject(it).let { m -> Nim.Msg(m.getString("role"), m.getString("content")) } }.toMutableList(),
            o.optLong("updated", f.lastModified()), o.optBoolean("pinned", false),
        )
    } catch (_: Exception) { null }

    fun get(ctx: Context, id: String): Chat? = load(File(dir(ctx), "$id.json"))

    fun save(ctx: Context, c: Chat) {
        if (c.messages.none { it.role == "user" }) return
        c.updated = System.currentTimeMillis()
        val arr = JSONArray()
        c.messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val o = JSONObject().put("id", c.id).put("title", c.title).put("model", c.model).put("persona", c.persona)
            .put("updated", c.updated).put("pinned", c.pinned).put("messages", arr)
        try { File(dir(ctx), "${c.id}.json").writeText(o.toString()) } catch (_: Exception) {}
    }

    fun delete(ctx: Context, id: String) { File(dir(ctx), "$id.json").delete() }
    fun deleteAll(ctx: Context) { dir(ctx).listFiles()?.forEach { it.delete() } }

    /** Ganzen Chat als Text (zum Teilen/Exportieren) */
    fun asText(c: Chat): String = buildString {
        append("# ").append(c.title).append("\n\n")
        c.messages.filter { it.role != "system" }.forEach {
            append(if (it.role == "user") "**Du:** " else "**Lumora AI:** ")
            append(Format.stripThinking(it.content).trim()).append("\n\n")
        }
    }
}

/** Einstellungen */
class AiPrefs(ctx: Context) {
    val sp = ctx.getSharedPreferences("ai", Context.MODE_PRIVATE)
    var key: String
        get() = sp.getString("key", "") ?: ""
        set(v) = sp.edit().putString("key", v.trim()).apply()
    var model: String
        get() = sp.getString("model", Nim.presets[0].first) ?: Nim.presets[0].first
        set(v) = sp.edit().putString("model", v).apply()
    var temperature: Float
        get() = sp.getFloat("temp", 0.6f)
        set(v) = sp.edit().putFloat("temp", v).apply()
    var maxTokens: Int
        get() = sp.getInt("max_tokens", 2048)
        set(v) = sp.edit().putInt("max_tokens", v).apply()
    var topP: Float
        get() = sp.getFloat("top_p", 0.9f)
        set(v) = sp.edit().putFloat("top_p", v).apply()
    var persona: Int
        get() = sp.getInt("persona", 0)
        set(v) = sp.edit().putInt("persona", v).apply()
    var customPrompt: String
        get() = sp.getString("custom_prompt", "") ?: ""
        set(v) = sp.edit().putString("custom_prompt", v).apply()
    var showThinking: Boolean
        get() = sp.getBoolean("thinking", false)
        set(v) = sp.edit().putBoolean("thinking", v).apply()
    var haptics: Boolean
        get() = sp.getBoolean("haptics", true)
        set(v) = sp.edit().putBoolean("haptics", v).apply()
    var textSize: Int
        get() = sp.getInt("text_size", 16)
        set(v) = sp.edit().putInt("text_size", v).apply()
    var autoSpeak: Boolean
        get() = sp.getBoolean("auto_speak", false)
        set(v) = sp.edit().putBoolean("auto_speak", v).apply()
    var enterSends: Boolean
        get() = sp.getBoolean("enter_sends", false)
        set(v) = sp.edit().putBoolean("enter_sends", v).apply()
    var history: Int
        get() = sp.getInt("history", 20)
        set(v) = sp.edit().putInt("history", v).apply()
    var accent: Int
        get() = sp.getInt("accent", 0xFF76B900.toInt())
        set(v) = sp.edit().putInt("accent", v).apply()
    var cachedModels: List<String>
        get() = sp.getString("models", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        set(v) = sp.edit().putString("models", v.joinToString("\n")).apply()
    var favModels: Set<String>
        get() = sp.getStringSet("fav_models", null)?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("fav_models", v).apply()

    fun systemPrompt(persona: Int): String {
        val p = Personas.all.getOrNull(persona) ?: Personas.all[0]
        return if (p.prompt.isEmpty()) customPrompt.ifBlank { Personas.all[0].prompt } else p.prompt
    }
}
