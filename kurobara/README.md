# KUROBARA 🥀

*five days. one heartbeat.*

Ein Stealth-Schul-Spiel für Roblox. Du spielst **Nyx Amane**, eine feste Spielfigur (nicht dein eigener Avatar). Nyx hat ihr Leben lang ihren eigenen Herzschlag nicht gehört, bis **Haru Seto** ihr eine Tür aufgehalten hat. Jetzt will **Mirei Hanazono** ihm am Freitag bei Sonnenuntergang auf dem Dach ihre Liebe gestehen. Du hast fünf Tage.

Alle Figuren, die Schule, die Story und die Texte sind eigene Erfindungen. Das Spiel selbst ist auf Englisch.

---

## Starten

**Option A: einfach öffnen**
1. `Kurobara.rbxl` in **Roblox Studio** öffnen.
2. Auf **Play** drücken. Die Map (Schule, Hof, Turnhalle, Garten, Straße, Nyx' Haus) wird beim Start automatisch gebaut.

**Option B: mit Rojo (Live-Sync beim Coden)**
```bash
cd kurobara
rojo serve          # im Studio-Plugin "Connect" klicken
rojo build -o Kurobara.rbxl   # neue .rbxl bauen
```

> 💡 Map selbst bearbeiten: Play → `Workspace.Map` kopieren → Stop → einfügen.
> Danach in `src/server/Builder.luau` `Builder.UseExistingMap = true` setzen.

## Steuerung

| Taste | Aktion |
|---|---|
| WASD | laufen |
| Shift (halten) | rennen |
| C | schleichen |
| E / F | interagieren (G = loslassen / ablegen) |
| Klick | mit Waffe angreifen |
| Tab | Handy (Notizen: Mireis Tagesablauf, Regeln) |
| 1–9 | Hotbar |

## Neu in v4

- **Kamera:** Taste **V** (oder Handy → Kamera) wechselt zwischen **Ego** und **3rd Person**. In Ego siehst du Nyx' Körper und Arme, wenn du nach unten schaust.
- **iPad/Handy:** eigene Glass-Buttons. Rechts ein Bogen aus Angriff, Rennen und Schleichen, oben links Kamera und Handy. Darüber erscheinen Kontext-Buttons für das, was du gerade tun kannst, zum Beispiel „Drag · Aoi“ oder „Shower“.
- **Neues Handy:** Homescreen mit Uhr und Apps: Nachrichten (Mama, Klassen-Chat, eine unbekannte Nummer, später Haru), Notizen, eine **Live-Karte**, Profil und Einstellungen. Schließen geht mit ✕, Tippen daneben, Tab oder Esc.
- **Gespräche mit Antworten:** Smalltalk, Komplimente, nach Haru oder Mirei fragen (die NPCs verraten, wo sie gerade sind), nach Vermissten fragen und **„Komm mal mit“**. Dann folgt dir die Person, bis du „Warte hier“ sagst.
- **Mehr Leben:** 24 zusätzliche Mitschüler, 3 neue Lehrer. Alle machen kleine Spaziergänge über den Campus, und der Hall-Monitor patrouilliert durch alle Stockwerke.
- **Erwischt = alles neu:** Nach dem Game-Over-Screen startet das Spiel automatisch neu, und Blut, Leichen, Nachrichten und Verdacht sind weg.
- **Neue Map in echtem Maßstab** (1 Stud ≈ 30 cm, Nyx 1,60 m, Autos 4,5 m):
  - **Schule:** 3 Stockwerke mit Treppenhäusern und Dach, 6 Klassen, Bibliothek, Musik, Kunst, Labor, Club-Räume, Atrium
  - **Campus:** Mensa-Gebäude, Sporthalle mit Umkleiden und Duschen, Freibad, Sportplatz, Garten mit Gewächshaus, Verbrennungsofen, großer Innenhof mit Brunnen
  - **Stadt:** Hauptstraße mit Strommasten und Ampeln, Konbini, Café, Ramen-Laden, Buchladen, Häuser, Wohnblock, Park mit Teich und Schrein, Bahnhof mit Zug, Nyx' Haus

## So läuft ein Tag

07:00 Zuhause → Schule → Unterricht (am eigenen Platz in 2-A **Attend class** = Zeit überspringen) → Mittag → Unterricht → Clubs → 18:00 nach Hause.

- **Waffen:** Box Cutter (Kunstraum), Kitchen Knife (Cafeteria + Zuhause), Baseball Bat (Turnhalle), Fire Axe (Glaskasten im Flur), Garden Shears (Gewächshaus). Jede Waffe hat ihre eigene Kill-Animation.
- **Leichen:** ziehen (E), mit der Axt zerlegen (F), in Müllsäcke packen, im Verbrennungsofen verbrennen, im Garten vergraben (Schaufel) oder als Sack in den Container werfen.
- **Spuren:** Blutlachen aufwischen (Mop), blutige Uniform duschen/verbrennen/wechseln, Waffen am Waschbecken waschen.
- **Ryo Amamiya 🔍 (Gegenspieler):** Sobald jemand verschwindet, folgt er dir zwischen den Stunden und schreibt alles auf. Bei 100 % geht er zu den Lehrern. Er sieht Angriffe von vorne kommen, also nur von hinten.
- **Mitschüler mit Persönlichkeit:** Gossips erzählen es sofort allen, Delinquents (weißes Hemd) sind schnell und haben keine Angst, Leute im Occult Club sind still. Alle haben Idle-Animationen und schauen dich an, wenn du nah kommst.
- **Blut:** organische Lachen, die braun trocknen, blutige Fußabdrücke, Schleifspuren, Spritzer mit Zacken, Wand- und Deckentropfen, Blutklumpen (Extreme), Killcam mit Letterbox und Hitstop.
- **Herzschlag (BPM) = Wahnsinn:** Kills treiben ihn hoch. Ab 140 zuckt Nyx, ab 175 lacht sie unkontrolliert. Nähe zu Haru und das Tagebuch zuhause beruhigen.
- **NPCs:** *Cowards* rennen zum Tor, *Heroes* jagen dich, *Phone*-Leute filmen dich, **Lehrer kann man nicht besiegen**. Wenn Haru dich sieht, ist alles vorbei.
- **Polizei:** kommt ca. 2,5 Minuten nach einem Anruf. Lebende Zeugen, Videos, Blut an dir oder eine blutige Waffe bedeuten Verhaftung.

**3 Enden:** HEARTBEAT ♥ · STILL BEATING ◆ · SILENCE ✕

## Sounds & Stimmen 🔊

Die Standard-Sounds von Roblox (Schritte, Sprung, „oof“) sind raus. Alles ist jetzt selbst gemacht.

**Stimmen funktionieren sofort.** Jede Sprechblase wird über Roblox Text-to-Speech laut gesprochen. Jede Figur hat ihre eigene Stimme, Tonhöhe und Geschwindigkeit, und Schreie laufen über einen Verzerrer. Dazu gibt es Durchsagen zur Schulglocke und Nyx' geflüsterte Gedanken. Die Stimmen kannst du in `Config.Voice` ändern.

**Effekte & Musik: einmal 4 Dateien hochladen**

Die Dateien liegen in `audio/` und sind komplett selbst synthetisiert:

| Datei | Inhalt |
|---|---|
| `kurobara_sfx_sheet.ogg` | alle 25 Effekte in einer Datei: Stechen, Schneiden, Schläge, Splatter, Herzschlag, Schulgong, Sirene, Schritte, Horror-Sting … |
| `music_calm.ogg` | Spieluhr-Theme |
| `music_tension.ogg` | Drone mit Herzschlag (hoher BPM / Polizei) |
| `music_chase.ogg` | Verfolgungsjagd |

1. In Studio **Asset Manager → Bulk Import** öffnen und die 4 Dateien auswählen. Alternativ geht es über create.roblox.com → Creations → Audio.
2. Jede Datei rechtsklicken und **Copy Asset ID** wählen.
3. In Studio **ReplicatedStorage → Shared → AudioIds** öffnen und die IDs als `"rbxassetid://123…"` einfügen.

Roblox begrenzt die Zahl der Audio-Uploads pro Monat. Deshalb stecken alle Effekte in einer einzigen Datei. Solange keine IDs eingetragen sind, bleiben die Effekte stumm. Die Stimmen laufen trotzdem.

**Echte Schreie (optional):** In `AudioIds` gibt es die Listen `ScreamF`, `ScreamM`, `Gasp` und `NyxLaugh`. Dort kannst du eigene Aufnahmen eintragen, zum Beispiel selbst aufgenommen 🎤, oder Sounds aus dem Creator Store (Toolbox → Audio → „scream“). Solange die Listen leer sind, schreit die Text-to-Speech-Stimme.

Neu generieren: `python3 tools/make_audio.py` (braucht numpy und ffmpeg).

## Gore-Einstellung ⚠️

In `src/shared/Config.luau`:

```lua
Config.GorePreset = "Extreme"   -- "Extreme" | "Restricted17" | "Mild"
```

- **Extreme**: Zerstückelung, Arterien-Spray, Blut auf dem Bildschirm. **Nur zum Testen in Studio.**
- **Restricted17**: viel realistisches Blut, keine Zerstückelung. Für Veröffentlichung mit 17+ Freigabe.
- **Mild**: stilisiert und wenig Blut. Für alle Altersstufen.

> Roblox moderiert Gore sehr streng. Wenn du das Spiel mit "Extreme" veröffentlichst, kann es gelöscht und dein Account verwarnt werden. **Vor dem Veröffentlichen umstellen!** Außerdem in den Game Settings **Max Players = 1** einstellen, denn das Spiel ist für eine Person gebaut.

## Anpassen

| Was | Wo |
|---|---|
| Zahlen (Tempo, BPM, Polizei-Timer, Sicht der NPCs) | `src/shared/Config.luau` |
| Figuren, Dialoge, Story, Enden | `src/shared/Story.luau` |
| Waffen & Treffer-Timing | `src/shared/Items.luau` |
| Animationen (Keyframes, ohne Uploads) | `src/shared/Anim.luau` |
| Map | `src/server/Builder.luau` |
| Sounds (eigene IDs eintragen) | `Config.Sounds` |

Die Animationen sind **prozedural**: Es sind Keyframes im Code, du musst also nichts hochladen. Du kannst auch eigene Animationen im Studio-Animation-Editor bauen und einbinden. Die Sounds nutzen Roblox-Standardsounds. Für echte Schreie oder Splatter-Sounds trägst du in `Config.Sounds` eigene Sound-IDs ein.

## Ehrlicher Status

Der Code ist syntaktisch geprüft, und der Map-Builder wurde gegen die echte Roblox-API-Datenbank getestet. **In Roblox Studio selbst wurde das Spiel noch nicht gespielt.** Rechne beim ersten Play mit Kleinigkeiten wie Waffen-Griff-Winkel, NPC-Wegen oder Sitzhöhen. Fehler stehen im Studio-Output (`[Kurobara] …`).
