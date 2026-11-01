# Lumora 🏝️ – Dynamic Island für Android

Native Android-App (Kotlin, ohne Libraries). Eine Dynamic Island in **jedem Design**:
Apple, Android (Material You), One UI, Nothing – oder komplett Custom.

## Designs
| Design | Island | Einstellungs-App |
|---|---|---|
| **Apple** | Schwarz, Cover-Glow, federnde Animation | iOS-Einstellungen |
| **Android** | Material-You-Fläche, Wellen-Fortschritt, gefüllter Play-Button | Material 3 |
| **One UI** | Graphit mit Rand, blauer Akzent, runder Play-Button | One UI |
| **Nothing** | Umriss, Punkt-Wellenform, Mono-Schrift, rot | Nothing OS |
| **Custom** | alles frei | frei wählbar |

**Liquid Glass** mit Reglern für Milchigkeit, Glanz, Tönung, Kanten-Stärke und Farbsaum.

## Neu in Lumora 4
Liquid-Glass-Look für die ganze App (nach stormaref/LiquidGlassSkill) · 10 Schriftarten für Island & App ·
5 Animationsstile (Apple, Android, One UI, Nothing, Jelly) · eigener Timer & Stoppuhr · Mitteilungs-Verlauf,
Warteschlange & Ruhezeiten · in bestimmten Apps ausblenden · Doppeltipp- & Wisch-Aktionen · Vinyl-Cover ·
Lade-Details, Akku-Rand, Hitze-Warnung · WLAN & Wecker · Energiesparmodus · Schnelleinstellungs-Kachel ·
Export/Import · Begrüßung · 13 App-Symbole (u.a. Trans Pride 🏳️‍⚧️)

## Live-Aktivitäten
🎵 Musik (Cover-Pop, Spulen, 3 Wellenform-Stile) · 📞 Anrufe annehmen/ablehnen · ⏱ Timer & Stoppuhr ·
🧭 Navigation · ⬆️ Uploads/Downloads · ⚡ Laden mit Animation · 🔦 Taschenlampe (antippen = aus) ·
🔊 Lautstärke · 🌙 Fokus · ✈️ Flugmodus · 🎧 Kopfhörer + Akku · 🔓 Entsperren · 🔕 Lautlos ·
💬 jede Mitteilung mit Aktionen · 🟢🟠🔵 Kamera/Mikro/Standort-Punkte · Uhrzeit/Akku in der leeren Pille ·
zwei Aktivitäten gleichzeitig als Bubble.

## Installieren
1. Unter **Releases** `Lumora.apk` laden und installieren (über ältere „Atoll“/„Island“-Versionen drüber).
2. App öffnen → **Bedienungshilfe** einschalten.
   - Android 13+: ausgegraut? → App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.
3. **Mitteilungen & Musik** einschalten.

## Selbst bauen
```
cd island-android
./gradlew assembleRelease
```
