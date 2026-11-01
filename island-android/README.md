# Island 🏝️ – Dynamic Island für Android

Native Android-App (Kotlin, keine Libraries) mit einer Dynamic Island im Apple-Stil.

## Features
- **Musik**: Cover, animierte Wellenform in Cover-Farbe, Glow, erweitert mit Fortschritt + ⏮ ⏯ ⏭
- **Benachrichtigungen**: App-Icon, Titel, Text – Tippen öffnet sie
- **Laden** ⚡ und **Lautlos/Klingeln** 🔕
- **Modus**: „Nur bei Bedarf“ (wie iPhone) oder „Immer sichtbar“
- Federphysik-Animationen (Bounce + Tempo einstellbar)
- Stil: Schwarz oder Liquid Glass
- Größe, Position, automatische Ausrichtung an der Kamera
- Live-Vorschau in den Einstellungen

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
