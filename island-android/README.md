# Island 🏝️ – Dynamic Island für Android

Native Android-App (Kotlin, keine Libraries) mit einer Dynamic Island im Apple-Stil.

## Features
**Live-Aktivitäten (wie iPhone)**
- 🎵 Musik: Album-Cover mit Pop-Animation, Wellenform in Cover-Farbe, Glow, großer Player mit Spulen, ⏮ ⏯ ⏭, App-Icon
- 📞 Anrufe: Annehmen / Ablehnen direkt in der Island, laufendes Gespräch mit Dauer + Auflegen
- ⏱ Timer, Stoppuhr, Sprachaufnahmen (Live-Countdown)
- 🧭 Navigation (Google Maps, Waze, OsmAnd …)
- ⬆️ Uploads & Downloads mit Fortschrittsring + Häkchen wenn fertig
- ⚡ Laden mit Animation (Akku füllt sich, Prozent zählen hoch, grüne Welle), Akku-schwach-Warnung
- 💬 Jede Mitteilung, runterziehen = ganzer Text + Aktions-Buttons
- 🎧 Kopfhörer mit Akkustand · 🔓 Entsperr-Animation · 🔕 Lautlos/Klingeln
- 🟢🟠🔵 Kamera-, Mikrofon- und Standort-Punkte
- Zwei Aktivitäten gleichzeitig → zweite als Bubble (antippen tauscht)

**Einstellungen im iOS-Stil** mit Unterseiten: Erscheinungsbild (Schwarz / Liquid Glass / Farbig, Akzentfarbe, Glow, Schrift),
Größe & Position, Animation (Bounce, Tempo, Bewegung reduzieren), Live-Aktivitäten, Musik, Mitteilungen,
App-Auswahl mit Suche, Gesten & Haptik, Datenschutz-Anzeigen, Berechtigungen, Fehlerbericht.

## Installieren
1. Unter **Releases** (oder Actions → Artifact) `Island.apk` laden und installieren.
2. App öffnen → **Bedienungshilfe** einschalten (zeichnet die Island über der Statusleiste).
   - Android 13+: ausgegraut? → App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“.
3. **Mitteilungen & Musik** einschalten.

## Selbst bauen
```
cd island-android
./gradlew assembleRelease
```
