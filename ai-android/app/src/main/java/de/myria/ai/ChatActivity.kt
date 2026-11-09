package de.myria.ai

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import kotlin.math.sin

/** Lumora AI – Chat mit NVIDIA-NIM-Modellen. */
class ChatActivity : Activity() {

    private lateinit var prefs: AiPrefs
    private val main = Handler(Looper.getMainLooper())
    private val ease = PathInterpolator(0.2f, 0.9f, 0.1f, 1f)

    private lateinit var root: FrameLayout
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var sendBtn: TextView
    private lateinit var titleView: TextView
    private lateinit var subView: TextView
    private var overlay: View? = null

    private var chat = newChat()
    private var busy = false
    @Volatile private var cancel = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val accent get() = prefs.accent
    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun dpi(v: Float) = dp(v).toInt()
    private val inter: Typeface by lazy { try { Typeface.createFromAsset(assets, "fonts/inter.ttf") } catch (_: Exception) { Typeface.DEFAULT } }
    private fun font(w: Int): Typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(inter, w, false) else inter

    private fun newChat() = Chat(System.currentTimeMillis().toString(36), "Neuer Chat",
        if (::prefs.isInitialized) prefs.model else Nim.presets[0].first,
        if (::prefs.isInitialized) prefs.persona else 0)

    // ------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = AiPrefs(this)
        chat = newChat()
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        build()
        // Text aus anderen Apps
        handleIntent(intent)
        if (prefs.key.isBlank()) main.post { showOnboarding() }
        tts = TextToSpeech(this) { ttsReady = it == TextToSpeech.SUCCESS; if (ttsReady) tts?.language = Locale.GERMAN }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(i: Intent) {
        val t = when (i.action) {
            Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
        if (!t.isNullOrBlank()) { input.setText(t); input.setSelection(input.text.length) }
    }

    override fun onPause() {
        super.onPause()
        Store.save(this, chat)
    }

    override fun onDestroy() {
        cancel = true
        tts?.shutdown()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (overlay != null) closeOverlay() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ------------------------------------------------------------------
    // Aufbau
    // ------------------------------------------------------------------

    private fun build() {
        root = FrameLayout(this)
        root.addView(Backdrop(), FrameLayout.LayoutParams(-1, -1))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        // Kopfzeile
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
        }
        top.addView(roundButton("☰") { showChats() })
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setOnClickListener { showModels() }
        }
        titleView = TextView(this).apply {
            textSize = 17f; typeface = font(700); setTextColor(Color.WHITE); gravity = Gravity.CENTER
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        subView = TextView(this).apply {
            textSize = 12f; typeface = font(500); setTextColor(0x99FFFFFF.toInt()); gravity = Gravity.CENTER
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
        }
        titles.addView(titleView); titles.addView(subView)
        top.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(roundButton("✎") { startNew() })
        top.addView(View(this), LinearLayout.LayoutParams(dpi(8f), 1))
        top.addView(roundButton("⚙") { showSettings() })
        col.addView(top)

        // Nachrichten
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(14f), dpi(8f), dpi(14f), dpi(16f))
        }
        scroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(list)
        }
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        // Eingabe
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            setPadding(dpi(10f), dpi(6f), dpi(10f), dpi(10f))
        }
        val pill = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(26f, 0x26FFFFFF)
            setPadding(dpi(6f), dpi(4f), dpi(4f), dpi(4f))
        }
        pill.addView(TextView(this).apply {
            text = "🎙"; textSize = 18f; gravity = Gravity.CENTER
            setOnClickListener { voiceInput() }
        }, LinearLayout.LayoutParams(dpi(40f), dpi(40f)))
        input = EditText(this).apply {
            hint = "Frag Lumora AI …"
            setHintTextColor(0x80FFFFFF.toInt())
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = font(400)
            background = null
            maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dpi(4f), dpi(10f), dpi(4f), dpi(10f))
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = updateSend()
            })
            setOnEditorActionListener { _, id, ev ->
                if (prefs.enterSends && (id == EditorInfo.IME_ACTION_SEND || ev?.keyCode == KeyEvent.KEYCODE_ENTER)) { send(); true } else false
            }
        }
        pill.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        sendBtn = TextView(this).apply {
            textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.BLACK); typeface = font(800)
            setOnClickListener { if (busy) stop() else send() }
        }
        pill.addView(sendBtn, LinearLayout.LayoutParams(dpi(40f), dpi(40f)))
        bar.addView(pill, LinearLayout.LayoutParams(-1, -2))
        col.addView(bar)

        root.setOnApplyWindowInsetsListener { _, ins ->
            @Suppress("DEPRECATION")
            col.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }
        setContentView(root)
        updateSend()
        renderAll()
    }

    private fun glass(r: Float, fill: Int) = GradientDrawable().apply {
        cornerRadius = dp(r)
        setColor(fill)
        setStroke(maxOf(1, dpi(0.8f)), 0x33FFFFFF)
    }

    private fun roundButton(t: String, onClick: () -> Unit) = TextView(this).apply {
        text = t
        textSize = 18f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        background = glass(21f, 0x1FFFFFFF)
        setOnClickListener { haptic(this); onClick() }
        layoutParams = LinearLayout.LayoutParams(dpi(42f), dpi(42f))
    }

    private fun haptic(v: View) { if (prefs.haptics) v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }

    private fun updateSend() {
        if (!::sendBtn.isInitialized) return
        val has = input.text.isNotBlank()
        sendBtn.text = if (busy) "■" else "↑"
        sendBtn.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (busy || has) accent else 0x33FFFFFF)
        }
        sendBtn.setTextColor(if (busy || has) Color.BLACK else 0x80FFFFFF.toInt())
    }

    private fun updateTitle() {
        val p = Personas.all.getOrNull(chat.persona) ?: Personas.all[0]
        titleView.text = if (chat.messages.any { it.role == "user" }) chat.title else "Lumora AI"
        subView.text = "${p.emoji} ${p.name} · ${shortModel(chat.model)} ▾"
    }

    private fun shortModel(m: String) = m.substringAfter('/').replace("-instruct", "").replace("-it", "")

    // ------------------------------------------------------------------
    // Nachrichten
    // ------------------------------------------------------------------

    private fun renderAll() {
        list.removeAllViews()
        val msgs = chat.messages.filter { it.role != "system" }
        if (msgs.isEmpty()) list.addView(emptyState(), LinearLayout.LayoutParams(-1, -1))
        else msgs.forEach { list.addView(bubble(it)) }
        updateTitle()
        scrollDown(false)
    }

    private fun emptyState(): View {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dpi(8f), dpi(40f), dpi(8f), dpi(20f))
        }
        col.addView(TextView(this).apply {
            text = "✦"
            textSize = 54f
            setTextColor(accent)
            gravity = Gravity.CENTER
        })
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        col.addView(TextView(this).apply {
            text = when (h) { in 5..10 -> "Guten Morgen ☀️"; in 11..17 -> "Hey, was geht?"; in 18..22 -> "Guten Abend 🌙"; else -> "Noch wach? 🌌" }
            textSize = 28f
            typeface = font(800)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, dpi(8f), 0, dpi(4f))
        })
        col.addView(TextView(this).apply {
            text = "Powered by NVIDIA NIM · ${shortModel(chat.model)}"
            textSize = 13f
            setTextColor(0x99FFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dpi(26f))
        })
        val ideas = listOf(
            "🇨🇿" to "Bring mir 5 tschechische Sätze für den Alltag bei",
            "🎵" to "Schreib einen Refrain über Neuanfänge",
            "🎬" to "Gib mir eine 10-Minuten-Animationsübung",
            "💡" to "Erklär mir Quantencomputer wie einer 12-Jährigen",
            "✉️" to "Formuliere eine höfliche Absage-Mail",
            "⌘" to "Schreib eine Kotlin-Funktion, die Primzahlen findet",
        )
        ideas.forEach { (e, t) ->
            col.addView(TextView(this).apply {
                text = "$e   $t"
                textSize = 15f
                typeface = font(500)
                setTextColor(Color.WHITE)
                background = glass(18f, 0x1AFFFFFF)
                setPadding(dpi(16f), dpi(13f), dpi(16f), dpi(13f))
                setOnClickListener { haptic(this); input.setText(t); send() }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(8f) })
        }
        return col
    }

    /** Eine Nachricht. Gibt das Text-View zurück (im Tag), damit Streaming es aktualisieren kann. */
    private fun bubble(m: Nim.Msg): View {
        val user = m.role == "user"
        val wrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (user) Gravity.END else Gravity.START
            setPadding(0, dpi(6f), 0, dpi(6f))
        }
        val think = TextView(this).apply {
            textSize = 13f
            setTextColor(0x8CFFFFFF.toInt())
            typeface = font(400)
            background = glass(14f, 0x14FFFFFF)
            setPadding(dpi(12f), dpi(8f), dpi(12f), dpi(8f))
            visibility = View.GONE
            tag = "think"
            setOnClickListener {
                val full = it.getTag(R.id.tag_full) as? String ?: return@setOnClickListener
                val open = (it.getTag(R.id.tag_open) as? Boolean) != true
                it.setTag(R.id.tag_open, open)
                text = if (open) "💭 Gedanken ▴\n$full" else "💭 Nachgedacht · antippen ▾"
            }
        }
        if (!user) wrap.addView(think, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dpi(6f) })
        val tv = TextView(this).apply {
            textSize = prefs.textSize.toFloat()
            typeface = font(400)
            setTextColor(Color.WHITE)
            setLineSpacing(0f, 1.25f)
            setTextIsSelectable(false)
            if (user) {
                background = GradientDrawable().apply {
                    cornerRadii = floatArrayOf(dp(20f), dp(20f), dp(20f), dp(20f), dp(6f), dp(6f), dp(20f), dp(20f))
                    setColor((accent and 0x00FFFFFF) or 0x59000000)
                    setStroke(maxOf(1, dpi(0.8f)), (accent and 0x00FFFFFF) or 0x80000000.toInt())
                }
                setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
            } else setPadding(dpi(2f), dpi(2f), dpi(2f), dpi(2f))
            tag = "text"
        }
        wrap.addView(tv, LinearLayout.LayoutParams(-2, -2).apply { if (user) leftMargin = dpi(48f) })
        setContent(wrap, m.content)
        val longPress = View.OnLongClickListener { v -> haptic(v); messageMenu(v, m); true }
        tv.setOnLongClickListener(longPress)
        wrap.setOnLongClickListener(longPress)
        wrap.tag = m
        return wrap
    }

    private fun setContent(wrap: View, raw: String) {
        val tv = wrap.findViewWithTag<TextView>("text") ?: return
        val think = wrap.findViewWithTag<TextView>("think")
        val (t, answer) = Format.split(raw)
        tv.text = if (answer.isEmpty() && t.isEmpty()) "…" else Format.render(answer.ifEmpty { if (t.isNotEmpty()) "" else "…" }, accent, 0x33FFFFFF)
        if (tv.text.isEmpty()) tv.text = "…"
        if (think != null) {
            if (t.isNotEmpty()) {
                think.visibility = View.VISIBLE
                think.setTag(R.id.tag_full, t)
                val open = prefs.showThinking || (think.getTag(R.id.tag_open) as? Boolean) == true
                think.text = if (open) "💭 Gedanken ▴\n$t" else if (answer.isEmpty()) "💭 Denkt nach …" else "💭 Nachgedacht · antippen ▾"
            } else think.visibility = View.GONE
        }
    }

    private fun messageMenu(anchor: View, m: Nim.Msg) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "Kopieren")
        pm.menu.add(0, 2, 1, "Teilen")
        pm.menu.add(0, 3, 2, "Vorlesen")
        if (m.role == "user") pm.menu.add(0, 4, 3, "Bearbeiten & neu senden")
        else pm.menu.add(0, 5, 3, "Neu generieren")
        pm.menu.add(0, 6, 4, "Ins Tschechische übersetzen")
        pm.menu.add(0, 7, 5, "Löschen")
        pm.setOnMenuItemClickListener { item ->
            val clean = Format.stripThinking(m.content).trim()
            when (item.itemId) {
                1 -> copy(clean)
                2 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, clean), "Teilen"))
                3 -> speak(clean)
                4 -> {
                    val i = chat.messages.indexOf(m)
                    if (i >= 0 && !busy) {
                        while (chat.messages.size > i) chat.messages.removeAt(chat.messages.size - 1)
                        renderAll(); input.setText(m.content); input.setSelection(input.text.length)
                    }
                }
                5 -> regenerate(m)
                6 -> { input.setText("Übersetze ins Tschechische (mit Aussprache-Hilfe):\n\n$clean"); send() }
                7 -> { chat.messages.remove(m); renderAll(); Store.save(this, chat) }
            }
            true
        }
        pm.show()
    }

    private fun copy(t: String) {
        (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Lumora AI", t))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
    }

    private fun speak(t: String) {
        if (!ttsReady) { Toast.makeText(this, "Sprachausgabe nicht verfügbar", Toast.LENGTH_SHORT).show(); return }
        val clean = t.replace(Regex("[*`#>]"), "")
        // Sprache grob erkennen
        tts?.language = if (Regex("[ěščřžýůťďň]").containsMatchIn(clean)) Locale("cs", "CZ") else Locale.GERMAN
        tts?.speak(clean.take(3900), TextToSpeech.QUEUE_FLUSH, null, "lumora")
    }

    private fun scrollDown(smooth: Boolean = true) {
        scroll.post { if (smooth) scroll.smoothScrollTo(0, list.height) else scroll.scrollTo(0, list.height) }
    }

    // ------------------------------------------------------------------
    // Senden / Streaming
    // ------------------------------------------------------------------

    private fun send() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || busy) return
        if (prefs.key.isBlank()) { showOnboarding(); return }
        input.setText("")
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        if (chat.messages.none { it.role == "user" }) {
            list.removeAllViews()
            chat.title = text.lineSequence().first().take(42)
        }
        val m = Nim.Msg("user", text)
        chat.messages.add(m)
        list.addView(bubble(m))
        ask()
    }

    private fun regenerate(m: Nim.Msg) {
        if (busy) return
        val i = chat.messages.indexOf(m)
        if (i < 0) return
        while (chat.messages.size > i) chat.messages.removeAt(chat.messages.size - 1)
        renderAll()
        ask()
    }

    private fun ask() {
        val reply = Nim.Msg("assistant", "")
        val history = chat.messages.filter { it.role != "system" }.takeLast(prefs.history.coerceIn(2, 100))
            .map { Nim.Msg(it.role, Format.stripThinking(it.content)) }
        val msgs = listOf(Nim.Msg("system", prefs.systemPrompt(chat.persona))) + history
        chat.messages.add(reply)
        val view = bubble(reply)
        list.addView(view)
        updateTitle()
        scrollDown()
        busy = true; cancel = false; updateSend()
        val key = prefs.key; val model = chat.model
        val temp = prefs.temperature.toDouble(); val max = prefs.maxTokens; val topP = prefs.topP.toDouble()
        val sb = StringBuilder()
        var lastUi = 0L
        Thread {
            var err: String? = null
            try {
                Nim.chat(key, model, msgs, temp, max, topP, { tok ->
                    synchronized(sb) { sb.append(tok) }
                    val now = System.currentTimeMillis()
                    if (now - lastUi > 45) {
                        lastUi = now
                        val snap = synchronized(sb) { sb.toString() }
                        main.post { reply.content = snap; setContent(view, snap); if (!scroll.canScrollVertically(1) || true) scrollDown(false) }
                    }
                }, { cancel })
            } catch (e: Nim.ApiError) { err = e.message
            } catch (e: java.net.UnknownHostException) { err = "Keine Internetverbindung."
            } catch (e: java.net.SocketTimeoutException) { err = "Zeitüberschreitung – das Modell braucht zu lange. Versuch ein schnelleres."
            } catch (e: Exception) { err = "Fehler: ${e.message ?: e.javaClass.simpleName}" }
            main.post {
                if (isFinishing) return@post
                val final = synchronized(sb) { sb.toString() }
                reply.content = when {
                    err != null && final.isBlank() -> "⚠️ $err"
                    err != null -> "$final\n\n⚠️ $err"
                    final.isBlank() -> if (cancel) "⏹ Gestoppt." else "(leere Antwort)"
                    else -> final
                }
                setContent(view, reply.content)
                busy = false; updateSend()
                Store.save(this, chat)
                if (prefs.haptics) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                if (prefs.autoSpeak && err == null) speak(Format.stripThinking(reply.content))
                scrollDown()
            }
        }.start()
    }

    private fun stop() { cancel = true; haptic(sendBtn) }

    private fun startNew() {
        if (busy) stop()
        Store.save(this, chat)
        chat = newChat()
        renderAll()
        input.requestFocus()
    }

    // ------------------------------------------------------------------
    // Sprache
    // ------------------------------------------------------------------

    private fun voiceInput() {
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE")
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Sprich mit Lumora AI"), REQ_VOICE)
        } catch (_: Exception) {
            Toast.makeText(this, "Spracheingabe nicht verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE && resultCode == RESULT_OK) {
            val t = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
            input.setText((input.text.toString() + " " + t).trim())
            input.setSelection(input.text.length)
        }
    }

    // ------------------------------------------------------------------
    // Overlays: Chats, Modelle, Einstellungen, Onboarding
    // ------------------------------------------------------------------

    private fun sheet(title: String, fromLeft: Boolean = false, build: (LinearLayout) -> Unit) {
        closeOverlay(animate = false)
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(0xB3000000.toInt())
            isClickable = true
            setOnClickListener { closeOverlay() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = dp(28f)
                setColor(0xF2121214.toInt())
                setStroke(maxOf(1, dpi(0.8f)), 0x26FFFFFF)
            }
            setPadding(dpi(18f), dpi(18f), dpi(18f), dpi(18f))
        }
        panel.addView(TextView(this).apply {
            text = title
            textSize = 24f
            typeface = font(800)
            setTextColor(Color.WHITE)
            setPadding(dpi(4f), 0, 0, dpi(12f))
        })
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        build(inner)
        panel.addView(ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(inner) }, LinearLayout.LayoutParams(-1, -2))
        val lp = if (fromLeft) FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.86f).toInt(), -1, Gravity.START)
            .apply { topMargin = dpi(36f); bottomMargin = dpi(20f); leftMargin = dpi(8f) }
        else FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { topMargin = dpi(60f); leftMargin = dpi(8f); rightMargin = dpi(8f); bottomMargin = dpi(12f) }
        scrim.addView(panel, lp)
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        overlay = scrim
        scrim.alpha = 0f
        scrim.animate().alpha(1f).setDuration(200).start()
        if (fromLeft) panel.translationX = -dp(300f) else panel.translationY = dp(400f)
        panel.animate().translationX(0f).translationY(0f).setDuration(380).setInterpolator(ease).start()
    }

    private fun closeOverlay(animate: Boolean = true) {
        val v = overlay ?: return
        overlay = null
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken, 0)
        if (!animate) { root.removeView(v); return }
        v.animate().alpha(0f).setDuration(180).withEndAction { root.removeView(v) }.start()
    }

    private fun row(t: String, sub: String? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(16f, 0x14FFFFFF)
            setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
            addView(TextView(this@ChatActivity).apply {
                text = t; textSize = 15.5f; typeface = font(600); setTextColor(Color.WHITE)
                maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            })
            if (sub != null) addView(TextView(this@ChatActivity).apply {
                text = sub; textSize = 12.5f; setTextColor(0x8CFFFFFF.toInt()); maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
            setOnClickListener { haptic(this); onClick() }
            if (onLong != null) setOnLongClickListener { haptic(this); onLong(); true }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(8f) }
        }

    private fun showChats() {
        Store.save(this, chat)
        sheet("Chats", fromLeft = true) { c ->
            val search = EditText(this).apply {
                hint = "Chats durchsuchen"
                setHintTextColor(0x80FFFFFF.toInt()); setTextColor(Color.WHITE); textSize = 15f
                background = glass(14f, 0x1FFFFFFF)
                setPadding(dpi(14f), dpi(10f), dpi(14f), dpi(10f))
                setSingleLine()
            }
            c.addView(search, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(10f) })
            c.addView(row("＋  Neuer Chat") { closeOverlay(); startNew() })
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(box)
            fun fill(q: String) {
                box.removeAllViews()
                val all = Store.list(this).filter { q.isBlank() || it.title.contains(q, true) ||
                    it.messages.any { m -> m.content.contains(q, true) } }
                if (all.isEmpty()) box.addView(TextView(this).apply {
                    text = if (q.isBlank()) "Noch keine Chats." else "Nichts gefunden."
                    setTextColor(0x8CFFFFFF.toInt()); setPadding(dpi(6f), dpi(10f), 0, 0)
                })
                val fmt = java.text.SimpleDateFormat("d. MMM, HH:mm", Locale.GERMANY)
                all.forEach { ch ->
                    val p = Personas.all.getOrNull(ch.persona) ?: Personas.all[0]
                    box.addView(row((if (ch.pinned) "📌 " else "") + ch.title,
                        "${p.emoji} ${fmt.format(java.util.Date(ch.updated))} · ${shortModel(ch.model)}",
                        onLong = { chatMenu(ch) { fill(search.text.toString()) } }) {
                        chat = Store.get(this, ch.id) ?: ch
                        closeOverlay(); renderAll()
                    })
                }
            }
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) = fill(s.toString())
            })
            fill("")
        }
    }

    private fun chatMenu(ch: Chat, refresh: () -> Unit) {
        AlertDialog.Builder(this).setTitle(ch.title)
            .setItems(arrayOf(if (ch.pinned) "Lösen" else "Anpinnen", "Umbenennen", "Als Text teilen", "Löschen")) { _, w ->
                val c = Store.get(this, ch.id) ?: return@setItems
                when (w) {
                    0 -> { c.pinned = !c.pinned; Store.save(this, c); refresh() }
                    1 -> textDialog("Umbenennen", c.title) { n -> c.title = n; Store.save(this, c); if (chat.id == c.id) chat.title = n; refresh(); updateTitle() }
                    2 -> startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Store.asText(c)), "Chat teilen"))
                    3 -> { Store.delete(this, c.id); if (chat.id == c.id) { chat = newChat(); renderAll() }; refresh() }
                }
            }.show()
    }

    private fun textDialog(title: String, value: String, multi: Boolean = false, onOk: (String) -> Unit) {
        val et = EditText(this).apply {
            setText(value); setSelectAllOnFocus(!multi)
            if (!multi) setSingleLine() else { minLines = 4; gravity = Gravity.TOP }
        }
        AlertDialog.Builder(this).setTitle(title)
            .setView(et.also { it.setPadding(dpi(20f), dpi(12f), dpi(20f), dpi(12f)) })
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("OK") { _, _ -> onOk(et.text.toString().trim()) }
            .show()
    }

    private fun showModels() {
        sheet("Modell & Persona") { c ->
            c.addView(label("Persona"))
            val hs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            Personas.all.forEachIndexed { i, p ->
                hs.addView(chip("${p.emoji} ${p.name}", i == chat.persona) {
                    if (p.prompt.isEmpty() && prefs.customPrompt.isBlank()) {
                        textDialog("Eigene Persona – System-Anweisung", "", multi = true) { t -> prefs.customPrompt = t }
                    }
                    chat.persona = i; prefs.persona = i; updateTitle(); closeOverlay()
                })
            }
            c.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(hs) },
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(14f) })

            c.addView(label("Modelle"))
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            c.addView(box)
            fun fill() {
                box.removeAllViews()
                val fav = prefs.favModels
                val loaded = prefs.cachedModels
                val presetIds = Nim.presets.map { it.first }
                val entries = (Nim.presets.map { it.first to it.second } +
                    loaded.filter { it !in presetIds }.map { it to "" })
                    .sortedByDescending { it.first in fav }
                entries.forEach { (id, desc) ->
                    val on = id == chat.model
                    box.addView(row((if (on) "● " else "") + (if (id in fav) "★ " else "") + shortModel(id),
                        if (desc.isNotEmpty()) "$desc · $id" else id,
                        onLong = {
                            prefs.favModels = if (id in fav) fav - id else fav + id
                            fill()
                        }) {
                        chat.model = id; prefs.model = id; updateTitle(); closeOverlay()
                    })
                }
            }
            fill()
            c.addView(row("↻  Alle Modelle von NVIDIA laden", "Lädt die aktuelle Liste (über 100 Modelle)") {
                val key = prefs.key
                if (key.isBlank()) { showOnboarding(); return@row }
                Toast.makeText(this, "Lade Modelle …", Toast.LENGTH_SHORT).show()
                Thread {
                    try {
                        val m = Nim.models(key)
                        main.post { prefs.cachedModels = m; fill(); Toast.makeText(this, "${m.size} Modelle geladen", Toast.LENGTH_SHORT).show() }
                    } catch (e: Exception) {
                        main.post { Toast.makeText(this, e.message ?: "Fehler", Toast.LENGTH_LONG).show() }
                    }
                }.start()
            })
            c.addView(row("✎  Modell-ID eingeben", "z. B. nvidia/…") {
                textDialog("Modell-ID", chat.model) { id -> if (id.isNotBlank()) { chat.model = id; prefs.model = id; updateTitle(); closeOverlay() } }
            })
            c.addView(hint("Tipp: Modell gedrückt halten → Favorit ★"))
        }
    }

    private fun label(t: String) = TextView(this).apply {
        text = t.uppercase(); textSize = 12f; letterSpacing = 0.08f; typeface = font(700)
        setTextColor(0x8CFFFFFF.toInt()); setPadding(dpi(4f), dpi(6f), 0, dpi(8f))
    }

    private fun hint(t: String) = TextView(this).apply {
        text = t; textSize = 12.5f; setTextColor(0x80FFFFFF.toInt()); setLineSpacing(0f, 1.2f)
        setPadding(dpi(4f), dpi(4f), dpi(4f), dpi(10f))
    }

    private fun chip(t: String, on: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = t; textSize = 14f; typeface = font(600)
        setTextColor(if (on) Color.BLACK else Color.WHITE)
        background = GradientDrawable().apply { cornerRadius = dp(18f); setColor(if (on) accent else 0x1FFFFFFF) }
        setPadding(dpi(14f), dpi(9f), dpi(14f), dpi(9f))
        setOnClickListener { haptic(this); onClick() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dpi(8f) }
    }

    private fun slider(t: String, min: Int, max: Int, value: Int, fmt: (Int) -> String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dpi(4f), dpi(6f), dpi(4f), dpi(6f)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        top.addView(TextView(this).apply { text = t; textSize = 15f; setTextColor(Color.WHITE) }, LinearLayout.LayoutParams(0, -2, 1f))
        val v = TextView(this).apply { text = fmt(value); textSize = 15f; setTextColor(accent); typeface = font(600) }
        top.addView(v)
        box.addView(top)
        box.addView(SeekBar(this).apply {
            this.max = max - min
            progress = (value - min).coerceIn(0, max - min)
            progressTintList = android.content.res.ColorStateList.valueOf(accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { v.text = fmt(p + min); if (u) onChange(p + min) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        })
        return box
    }

    private fun toggle(t: String, sub: String?, value: Boolean, onChange: (Boolean) -> Unit): View {
        var on = value
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dpi(4f), dpi(10f), dpi(4f), dpi(10f))
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply { text = t; textSize = 15f; setTextColor(Color.WHITE) })
        if (sub != null) texts.addView(TextView(this).apply { text = sub; textSize = 12.5f; setTextColor(0x80FFFFFF.toInt()) })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val sw = TextView(this)
        fun paint() {
            sw.text = if (on) "AN" else "AUS"
            sw.textSize = 12f; sw.typeface = font(800); sw.gravity = Gravity.CENTER
            sw.setTextColor(if (on) Color.BLACK else Color.WHITE)
            sw.background = GradientDrawable().apply { cornerRadius = dp(14f); setColor(if (on) accent else 0x33FFFFFF) }
        }
        paint()
        row.addView(sw, LinearLayout.LayoutParams(dpi(56f), dpi(28f)))
        row.setOnClickListener { on = !on; paint(); haptic(row); onChange(on) }
        return row
    }

    private fun showSettings() {
        sheet("Einstellungen") { c ->
            c.addView(label("NVIDIA API"))
            c.addView(row(if (prefs.key.isBlank()) "API-Schlüssel eingeben" else "API-Schlüssel ändern",
                if (prefs.key.isBlank()) "Kostenlos auf build.nvidia.com" else "nvapi-…" + prefs.key.takeLast(4)) {
                textDialog("NVIDIA API-Schlüssel", prefs.key) { k -> prefs.key = k; closeOverlay() }
            })
            c.addView(row("Schlüssel holen ↗", "build.nvidia.com öffnen") {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://build.nvidia.com/"))) } catch (_: Exception) {}
            })

            c.addView(label("Antworten"))
            c.addView(slider("Kreativität", 0, 100, (prefs.temperature * 100).toInt(), { "%.2f".format(it / 100f) }) { prefs.temperature = it / 100f })
            c.addView(slider("Top-P", 10, 100, (prefs.topP * 100).toInt(), { "%.2f".format(it / 100f) }) { prefs.topP = it / 100f })
            c.addView(slider("Max. Länge", 256, 8192, prefs.maxTokens, { "$it Token" }) { prefs.maxTokens = it })
            c.addView(slider("Gedächtnis", 2, 60, prefs.history, { "$it Nachrichten" }) { prefs.history = it })
            c.addView(toggle("Gedanken immer zeigen", "Bei Denk-Modellen wie DeepSeek R1", prefs.showThinking) { prefs.showThinking = it })
            c.addView(row("Eigene Persona bearbeiten", prefs.customPrompt.ifBlank { "Noch leer" }) {
                textDialog("Eigene Persona – System-Anweisung", prefs.customPrompt, multi = true) { t -> prefs.customPrompt = t }
            })

            c.addView(label("Bedienung"))
            c.addView(slider("Textgröße", 12, 22, prefs.textSize, { "$it" }) { prefs.textSize = it; renderAll() })
            c.addView(toggle("Antworten vorlesen", "Automatisch nach jeder Antwort", prefs.autoSpeak) { prefs.autoSpeak = it })
            c.addView(toggle("Enter sendet", null, prefs.enterSends) { prefs.enterSends = it })
            c.addView(toggle("Haptik", null, prefs.haptics) { prefs.haptics = it })

            c.addView(label("Farbe"))
            val colors = intArrayOf(0xFF76B900.toInt(), 0xFF64D2FF.toInt(), 0xFFBF5AF2.toInt(), 0xFFFF6AA2.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFFFFF.toInt())
            val cr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dpi(4f), 0, 0, dpi(10f)) }
            colors.forEach { col ->
                cr.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL; setColor(col)
                        if (col == prefs.accent) setStroke(dpi(3f), Color.WHITE)
                    }
                    setOnClickListener { prefs.accent = col; closeOverlay(); build() }
                }, LinearLayout.LayoutParams(dpi(34f), dpi(34f)).apply { rightMargin = dpi(10f) })
            }
            c.addView(cr)

            c.addView(label("Daten"))
            c.addView(row("Diesen Chat teilen") {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, Store.asText(chat)), "Chat teilen"))
            })
            c.addView(row("Alle Chats löschen", "Kann nicht rückgängig gemacht werden") {
                AlertDialog.Builder(this).setTitle("Alle Chats löschen?")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Löschen") { _, _ -> Store.deleteAll(this); chat = newChat(); closeOverlay(); renderAll() }
                    .show()
            })
            c.addView(hint("Lumora AI schickt deine Nachrichten direkt an NVIDIA (integrate.api.nvidia.com). Chats bleiben nur auf deinem Handy. KI kann sich irren – wichtige Infos prüfen."))
        }
    }

    private fun showOnboarding() {
        sheet("Willkommen ✦") { c ->
            c.addView(TextView(this).apply {
                text = "Lumora AI nutzt die KI-Modelle von NVIDIA (Llama, Nemotron, DeepSeek, Qwen …).\n\n" +
                    "1. Öffne build.nvidia.com und melde dich kostenlos an\n" +
                    "2. Wähle ein Modell → „Get API Key“\n" +
                    "3. Kopiere den Schlüssel (beginnt mit nvapi-) und füge ihn unten ein"
                textSize = 15f; setTextColor(0xDDFFFFFF.toInt()); setLineSpacing(0f, 1.3f)
                setPadding(dpi(4f), 0, dpi(4f), dpi(14f))
            })
            c.addView(row("build.nvidia.com öffnen ↗") {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://build.nvidia.com/"))) } catch (_: Exception) {}
            })
            val et = EditText(this).apply {
                hint = "nvapi-…"
                setHintTextColor(0x66FFFFFF); setTextColor(Color.WHITE); textSize = 15f
                setSingleLine()
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                background = glass(14f, 0x1FFFFFFF)
                setPadding(dpi(14f), dpi(12f), dpi(14f), dpi(12f))
                setText(prefs.key)
            }
            c.addView(et, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dpi(10f) })
            c.addView(TextView(this).apply {
                text = "Los geht's"
                textSize = 16f; typeface = font(800); gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                background = GradientDrawable().apply { cornerRadius = dp(18f); setColor(accent) }
                setPadding(0, dpi(14f), 0, dpi(14f))
                setOnClickListener {
                    val k = et.text.toString().trim()
                    if (!k.startsWith("nvapi-")) {
                        Toast.makeText(this@ChatActivity, "Der Schlüssel beginnt normalerweise mit „nvapi-“", Toast.LENGTH_LONG).show()
                    }
                    if (k.isNotEmpty()) { prefs.key = k; closeOverlay() }
                }
            })
        }
    }

    /** Animierter Verlaufs-Hintergrund in NVIDIA-Grün */
    private inner class Backdrop : View(this@ChatActivity) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val start = System.nanoTime()
        override fun onDraw(c: Canvas) {
            c.drawColor(0xFF060807.toInt())
            val t = (System.nanoTime() - start) / 1e9f
            val w = width.toFloat(); val h = height.toFloat()
            fun blob(x: Float, y: Float, r: Float, col: Int) {
                p.shader = RadialGradient(x, y, r, col, 0, Shader.TileMode.CLAMP)
                c.drawCircle(x, y, r, p)
            }
            val a = accent and 0x00FFFFFF
            blob(w * (0.2f + 0.08f * sin(t * 0.2f)), h * 0.12f, w * 0.9f, a or 0x38000000)
            blob(w * (0.9f + 0.06f * sin(t * 0.17f + 1)), h * 0.55f, w * 0.8f, 0x262C6BFF)
            blob(w * 0.3f, h * (0.95f + 0.03f * sin(t * 0.23f)), w * 0.9f, a or 0x22000000)
            if (!busy) postInvalidateDelayed(80) else postInvalidateOnAnimation()
        }
    }

    companion object {
        private const val REQ_VOICE = 51
    }
}
