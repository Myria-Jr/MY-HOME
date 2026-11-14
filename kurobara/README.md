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
