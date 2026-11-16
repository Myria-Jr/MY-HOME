# Alltag

Die eine App für jeden Tag – privat und für die Arbeit.

- **Heute** – Begrüßung, fällige Aufgaben, Monatssaldo, Fokus-Timer, letzte Notiz
- **Aufgaben** – mit Fälligkeit und Wichtigkeit, gruppiert nach Überfällig / Heute / Morgen / Demnächst
- **Notizen** – mit Suche, speichern automatisch
- **Geld** – Einnahmen & Ausgaben pro Monat, Kategorien; im Bereich *Arbeit* mit geschätzter USt-Zahllast (19 %)
- **Fokus** – 25/5/15-Minuten-Timer mit Wochenübersicht

Oben umschalten zwischen **Privat** und **Arbeit** – jeder Bereich hat eigene Daten.
Alles bleibt lokal auf dem Gerät. Kein Konto, keine Cloud.

| Heute | Heute (Arbeit, dunkel) | Aufgaben | Notizen | Geld | Fokus |
|---|---|---|---|---|---|
| ![](android/screenshots/heute.png) | ![](android/screenshots/heute-dark-arbeit.png) | ![](android/screenshots/aufgaben-arbeit.png) | ![](android/screenshots/notizen.png) | ![](android/screenshots/geld-arbeit.png) | ![](android/screenshots/fokus.png) |

## Android (nativ)

Kotlin + Jetpack Compose mit **Material 3 Expressive**, Ordner [`android/`](android). Android 8.0 oder neuer.

Material You (Farben vom Hintergrundbild ab Android 12; *Arbeit* nutzt die Tertiärfarbe), einklappende große Top-Bar, verbundene Toggle-Buttons, FAB-Menü, Shape-Morphing beim Abhaken, welliger Fokus-Ring, Loading-Indicator, gruppierte Listen, Wischen zum Löschen mit Rückgängig, Feder-Animationen.

- **Installieren:** [`Alltag.apk`](Alltag.apk) aufs Handy laden, öffnen, „Aus dieser Quelle installieren“ erlauben.
- **Selbst bauen:** `cd android && ./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- Bei jedem Push baut GitHub Actions die APK automatisch (Tab *Actions* → *Alltag-apk*).

Extras auf Android: Benachrichtigung + Vibration, wenn der Fokus-Timer abläuft (auch wenn die App zu ist), Haptik beim Abhaken, Datumsauswahl, Android-Zurück-Geste, Themed Icon.

> Die APK ist mit einem Debug-Schlüssel signiert. Für den Play Store braucht es einen eigenen Signier-Schlüssel.

## Web

`index.html` im Browser öffnen. Auf dem iPhone in Safari → Teilen → *Zum Home-Bildschirm*.
