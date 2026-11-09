package de.myria.ai

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client für NVIDIA NIM (build.nvidia.com) – OpenAI-kompatible Chat-API.
 * Den kostenlosen API-Schlüssel („nvapi-…“) gibt's auf build.nvidia.com.
 */
object Nim {
    const val BASE = "https://integrate.api.nvidia.com/v1"

    /** Vorschläge, falls die Modell-Liste (noch) nicht geladen ist. */
    val presets = listOf(
        "meta/llama-3.3-70b-instruct" to "Llama 3.3 70B – stark & schnell",
        "nvidia/llama-3.3-nemotron-super-49b-v1" to "Nemotron Super – NVIDIA",
        "nvidia/llama-3.1-nemotron-70b-instruct" to "Nemotron 70B – NVIDIA",
        "deepseek-ai/deepseek-r1" to "DeepSeek R1 – denkt nach",
        "qwen/qwen2.5-coder-32b-instruct" to "Qwen Coder – Programmieren",
        "mistralai/mixtral-8x22b-instruct-v0.1" to "Mixtral 8×22B",
        "google/gemma-2-27b-it" to "Gemma 2 27B",
        "microsoft/phi-3.5-mini-instruct" to "Phi 3.5 Mini – sehr schnell",
    )

    class Msg(val role: String, var content: String)

    class ApiError(msg: String) : Exception(msg)

    /** Lädt alle verfügbaren Modelle (für die Auswahl). */
    fun models(key: String): List<String> {
        val c = URL("$BASE/models").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 20000
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Accept", "application/json")
        try {
            if (c.responseCode !in 200..299) throw ApiError(errorText(c))
            val body = c.inputStream.bufferedReader().readText()
            val arr = JSONObject(body).optJSONArray("data") ?: JSONArray()
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("id") }
                .filter { it.isNotBlank() }.sorted()
        } finally { c.disconnect() }
    }

    /**
     * Chat mit Streaming. [onToken] bekommt jedes neue Textstück.
     * [cancelled] wird regelmäßig geprüft (Stopp-Taste).
     */
    fun chat(
        key: String, model: String, messages: List<Msg>,
        temperature: Double, maxTokens: Int, topP: Double,
        onToken: (String) -> Unit, cancelled: () -> Boolean,
    ) {
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject()
            .put("model", model)
            .put("messages", arr)
            .put("temperature", temperature)
            .put("top_p", topP)
            .put("max_tokens", maxTokens)
            .put("stream", true)
        val c = URL("$BASE/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 20000
        c.readTimeout = 120000
        c.setRequestProperty("Authorization", "Bearer $key")
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Accept", "text/event-stream")
        try {
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            if (c.responseCode !in 200..299) throw ApiError(errorText(c))
            BufferedReader(InputStreamReader(c.inputStream, Charsets.UTF_8)).use { r ->
                while (true) {
                    if (cancelled()) break
                    val line = r.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val o = try { JSONObject(data) } catch (_: Exception) { continue }
                    val ch = o.optJSONArray("choices")?.optJSONObject(0) ?: continue
                    val delta = ch.optJSONObject("delta")
                    // manche Modelle liefern das „Nachdenken“ separat
                    val reasoning = delta?.optString("reasoning_content", "").orEmpty()
                    if (reasoning.isNotEmpty() && reasoning != "null") onToken("\u0001$reasoning")
                    val t = delta?.optString("content", "").orEmpty()
                    if (t.isNotEmpty() && t != "null") onToken(t)
                }
            }
        } finally { c.disconnect() }
    }

    private fun errorText(c: HttpURLConnection): String {
        val code = c.responseCode
        val raw = try { c.errorStream?.bufferedReader()?.readText().orEmpty() } catch (_: Exception) { "" }
        val detail = try {
            val o = JSONObject(raw)
            o.optJSONObject("error")?.optString("message") ?: o.optString("detail", o.optString("title", raw))
        } catch (_: Exception) { raw }
        return when (code) {
            401, 403 -> "API-Schlüssel ungültig oder ohne Zugriff ($code)."
            404 -> "Modell nicht gefunden (404). Wähle ein anderes Modell."
            429 -> "Zu viele Anfragen – kurz warten (429)."
            else -> "Fehler $code: ${detail.take(200)}"
        }
    }
}
