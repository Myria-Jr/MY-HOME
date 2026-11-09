package de.myria.island

import android.content.Context
import android.graphics.Typeface
import android.os.Build

/**
 * Alle Schriften: System-Schriften + mitgelieferte Open-Source-Schriften
 * (Google Fonts, SIL Open Font License – siehe assets/fonts/OFL.txt).
 * Die Reihenfolge = gespeicherter Index, darum nur hinten anhängen!
 */
object Fonts {
    class F(val name: String, val file: String? = null, val family: String? = null, val note: String? = null)

    val ALL = listOf(
        F("System", family = "sans-serif"),                         // 0
        F("Google Sans", family = "google-sans", note = "Auf Pixel-Handys"), // 1
        F("Schmal", family = "sans-serif-condensed"),               // 2
        F("Mono", family = "monospace"),                            // 3
        F("Serif", family = "serif"),                               // 4
        F("Inter", file = "inter.ttf", note = "Fast wie Apples SF Pro"),  // 5
        F("Outfit", file = "outfit.ttf", note = "Rund & modern"),   // 6
        F("Space Grotesk", file = "spacegrotesk.ttf", note = "Technisch"), // 7
        F("Nunito", file = "nunito.ttf", note = "Weich & rund"),    // 8
        F("Manrope", file = "manrope.ttf", note = "Klar & elegant"), // 9
        F("Sora", file = "sora.ttf", note = "Geometrisch"),         // 10
        F("Doto", file = "doto.ttf", note = "Punkt-Matrix wie Nothing"), // 11
        F("Playfair", file = "playfair.ttf", note = "Edel, Serifen"), // 12
        F("JetBrains Mono", file = "jetbrainsmono.ttf", note = "Code-Look"), // 13
        F("Pacifico", file = "pacifico.ttf", note = "Verspielt, Handschrift"), // 14
        F("Comic", family = "casual"),                              // 15
        F("Schreibschrift", family = "cursive"),                    // 16
        F("Dünn", family = "sans-serif-light"),                     // 17
    )

    const val INTER = 5
    const val DOTO = 11

    private val cache = HashMap<String, Typeface>()

    fun get(ctx: Context, index: Int, weight: Int): Typeface {
        val i = index.coerceIn(0, ALL.size - 1)
        val key = "$i/$weight"
        cache[key]?.let { return it }
        val f = ALL[i]
        val tf = try {
            if (f.file != null) {
                Typeface.Builder(ctx.applicationContext.assets, "fonts/" + f.file)
                    .setFontVariationSettings("'wght' $weight")
                    .setWeight(weight.coerceIn(1, 1000))
                    .build()
            } else {
                val base = Typeface.create(f.family ?: "sans-serif", Typeface.NORMAL)
                if (Build.VERSION.SDK_INT >= 28) Typeface.create(base, weight.coerceIn(1, 1000), false)
                else Typeface.create(base, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
            }
        } catch (_: Exception) {
            null
        } ?: Typeface.create("sans-serif", if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        cache[key] = tf
        return tf
    }
}
