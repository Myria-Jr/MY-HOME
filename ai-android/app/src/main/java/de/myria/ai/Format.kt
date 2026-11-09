package de.myria.ai

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/** Kleines Markdown → Spannable (fett, kursiv, Code, Codeblöcke, Überschriften, Listen, Zitate). */
object Format {
    /** Denk-Teil getrennt: \u0001-Tokens (reasoning_content) oder <think>…</think> */
    fun split(raw: String): Pair<String, String> {
        val thinkTokens = StringBuilder()
        val answer = StringBuilder()
        // \u0001-markierte Stücke gehören zum Nachdenken
        var i = 0
        val parts = raw.split('\u0001')
        parts.forEachIndexed { idx, p -> if (idx == 0) answer.append(p) else thinkTokens.append(p) }
        var a = answer.toString()
        val open = a.indexOf("<think>")
        if (open >= 0) {
            val close = a.indexOf("</think>", open)
            if (close >= 0) {
                thinkTokens.append(a.substring(open + 7, close))
                a = a.removeRange(open, close + 8)
            } else {
                thinkTokens.append(a.substring(open + 7))
                a = a.substring(0, open)
            }
        }
        i++
        return thinkTokens.toString().trim() to a.trimStart()
    }

    fun stripThinking(raw: String) = split(raw).second

    fun render(md: String, accent: Int, codeBg: Int): CharSequence {
        val out = SpannableStringBuilder()
        val lines = md.replace("\r", "").split('\n')
        var inCode = false
        var codeStart = 0
        for ((li, line) in lines.withIndex()) {
            if (line.trimStart().startsWith("```")) {
                if (!inCode) { inCode = true; codeStart = out.length }
                else {
                    inCode = false
                    if (out.length > codeStart) {
                        out.setSpan(TypefaceSpan("monospace"), codeStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.setSpan(BackgroundColorSpan(codeBg), codeStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.setSpan(RelativeSizeSpan(0.9f), codeStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                continue
            }
            if (inCode) { out.append(line).append('\n'); continue }
            val start = out.length
            var l = line
            var heading = 0
            while (l.startsWith("#")) { heading++; l = l.substring(1) }
            if (heading > 0) l = l.trimStart()
            val bullet = Regex("^\\s*[-*•]\\s+").find(l)
            val numbered = Regex("^\\s*(\\d+)[.)]\\s+").find(l)
            val quote = l.startsWith("> ")
            if (bullet != null) l = l.substring(bullet.range.last + 1)
            if (quote) l = l.substring(2)
            inline(out, l, accent, codeBg)
            val end = out.length
            if (heading > 0 && end > start) {
                out.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(RelativeSizeSpan(when (heading) { 1 -> 1.35f; 2 -> 1.2f; else -> 1.08f }), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (bullet != null && end >= start) out.setSpan(BulletSpan(18, accent), start, maxOf(end, start), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (numbered != null && end > start) out.setSpan(LeadingMarginSpan.Standard(6, 30), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (quote && end > start) {
                out.setSpan(ForegroundColorSpan(0xB3FFFFFF.toInt()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                out.setSpan(LeadingMarginSpan.Standard(24), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (li < lines.size - 1) out.append('\n')
        }
        if (inCode && out.length > codeStart) {
            out.setSpan(TypefaceSpan("monospace"), codeStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(BackgroundColorSpan(codeBg), codeStart, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** **fett**, *kursiv*, `code` */
    private fun inline(out: SpannableStringBuilder, text: String, accent: Int, codeBg: Int) {
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val e = text.indexOf("**", i + 2)
                    if (e > i + 2) {
                        val s = out.length
                        out.append(text, i + 2, e)
                        out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = e + 2; continue
                    }
                }
                text[i] == '`' -> {
                    val e = text.indexOf('`', i + 1)
                    if (e > i + 1) {
                        val s = out.length
                        out.append(text, i + 1, e)
                        out.setSpan(TypefaceSpan("monospace"), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.setSpan(BackgroundColorSpan(codeBg), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        out.setSpan(ForegroundColorSpan(accent), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = e + 1; continue
                    }
                }
                text[i] == '*' && i + 1 < text.length && text[i + 1] != ' ' -> {
                    val e = text.indexOf('*', i + 1)
                    if (e > i + 1) {
                        val s = out.length
                        out.append(text, i + 1, e)
                        out.setSpan(StyleSpan(Typeface.ITALIC), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = e + 1; continue
                    }
                }
            }
            out.append(text[i])
            i++
        }
    }
}
