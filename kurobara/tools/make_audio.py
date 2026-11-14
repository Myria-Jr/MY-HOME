#!/usr/bin/env python3
"""KUROBARA · original sound generator.

Synthesizes every sound effect from scratch (no samples) and writes:
  audio/kurobara_sfx_sheet.ogg   all SFX in one file (upload 1x to Roblox)
  audio/music_calm.ogg           music-box theme (loop)
  audio/music_tension.ogg        dread drone (loop)
  audio/music_chase.ogg          chase loop
  ../src/shared/AudioSheet.luau  where each sound sits inside the sheet

Run:  python3 tools/make_audio.py      (needs numpy + ffmpeg)
"""
import json
import math
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
OUT = os.path.join(ROOT, "audio")
rng = np.random.default_rng(7)


# ── building blocks ─────────────────────────────────────────────────────────
def t_axis(dur):
    return np.arange(int(SR * dur)) / SR


def env(n, attack, decay):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    return a * np.exp(-np.maximum(t - attack, 0) / max(decay, 1e-4))


def noise(dur):
    return rng.uniform(-1, 1, int(SR * dur))


def fft_filter(x, lo=None, hi=None):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    m = np.ones_like(f)
    if lo:
        m *= 1 / (1 + (lo / np.maximum(f, 1)) ** 4)
    if hi:
        m *= 1 / (1 + (f / hi) ** 4)
    return np.fft.irfft(X * m, len(x))


def sweep_bandpass(x, f0, f1, q=4.0):
    """state-variable bandpass whose centre moves from f0 to f1 (log)."""
    n = len(x)
    out = np.zeros(n)
    low = band = 0.0
    for i in range(n):
        fc = f0 * (f1 / f0) ** (i / n)
        f = 2 * math.sin(math.pi * min(fc, SR / 6) / SR)
        high = x[i] - low - band / q
        band += f * high
        low += f * band
        out[i] = band
    return out


def chirp(dur, f0, f1, decay, shape="exp"):
    t = t_axis(dur)
    if shape == "exp":
        freq = f0 * (f1 / f0) ** (t / dur)
    else:
        freq = f0 + (f1 - f0) * t / dur
    phase = 2 * np.pi * np.cumsum(freq) / SR
    return np.sin(phase) * np.exp(-t / decay)


def place(buf, snd, at):
    i = int(at * SR)
    j = min(len(buf), i + len(snd))
    buf[i:j] += snd[: j - i]
    return buf


def norm(x, peak=0.9):
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def reverb(x, mix=0.25, decay=0.45):
    out = np.copy(x)
    for d, g in ((0.029, 0.6), (0.037, 0.55), (0.041, 0.5), (0.053, 0.45), (0.067, 0.4), (0.089, 0.35)):
        k = int(d * SR)
        y = np.zeros(len(x) + k * 8)
        y[: len(x)] = x
        for rep in range(1, 8):
            y[k * rep : k * rep + len(x)] += x * (g * decay) ** rep
        out = out + mix * y[: len(x)] / 6
    return out


def pad(x, after=0.0):
    return np.concatenate([x, np.zeros(int(SR * after))])


# ── sound effects ───────────────────────────────────────────────────────────
def stab(v):
    d = 0.55
    thump = chirp(d, 120 + v * 15, 55, 0.07) * 0.9
    wet = fft_filter(noise(d), hi=1800 + v * 500) * env(int(SR * d), 0.002, 0.11)
    squelch = sweep_bandpass(noise(d) * env(int(SR * d), 0.01, 0.15), 1400 - v * 200, 260, q=6) * 3
    am = 0.6 + 0.4 * np.sin(2 * np.pi * (28 + v * 6) * t_axis(d))
    tear = fft_filter(noise(d), lo=4000) * env(int(SR * d), 0.0005, 0.012) * 0.5
    return norm(thump + wet * 0.8 + squelch * am + tear)


def slash(v):
    d = 0.7
    n = noise(d) * np.exp(-((t_axis(d) - 0.18) ** 2) / (2 * 0.07 ** 2))
    whoosh = sweep_bandpass(n, 500 + v * 200, 3200, q=2.5) * 2.5
    wet = fft_filter(noise(d), hi=1500) * env(int(SR * d), 0.25, 0.09) * (t_axis(d) > 0.24)
    return norm(whoosh + wet * 0.6)


def blunt(v):
    d = 0.7
    thud = chirp(d, 75 - v * 8, 38, 0.16)
    body = fft_filter(noise(d), hi=420) * env(int(SR * d), 0.003, 0.14) * 1.2
    crack = fft_filter(noise(d), lo=2500) * env(int(SR * d), 0.0003, 0.008)
    ping = np.sin(2 * np.pi * 2300 * t_axis(d)) * env(int(SR * d), 0.0002, 0.02) * 0.4
    return norm(thud + body + crack * 0.9 + ping)


def splat(v):
    d = 0.6
    buf = fft_filter(noise(d), hi=2200) * env(int(SR * d), 0.002, 0.06) * 0.8
    for _ in range(5 + v * 2):
        blip = chirp(0.08, rng.uniform(700, 1300), rng.uniform(220, 380), 0.025) * rng.uniform(0.3, 0.7)
        place(buf, blip, rng.uniform(0.0, 0.28))
    return norm(reverb(buf, 0.15))


def drip():
    d = 0.6
    p = chirp(0.06, 900, 1900, 0.03)
    buf = place(np.zeros(int(SR * d)), p, 0.0)
    return norm(reverb(buf, 0.6, 0.6), 0.7)


def heartbeat():
    d = 0.8
    lub = (np.sin(2 * np.pi * 52 * t_axis(d)) + 0.4 * np.sin(2 * np.pi * 104 * t_axis(d))) * env(int(SR * d), 0.008, 0.07)
    dub = (np.sin(2 * np.pi * 64 * t_axis(d)) + 0.3 * np.sin(2 * np.pi * 128 * t_axis(d))) * env(int(SR * d), 0.006, 0.055)
    buf = place(np.zeros(int(SR * d)), lub, 0.0)
    place(buf, dub * 0.75, 0.27)
    return norm(fft_filter(buf, hi=220), 0.95)


def bell_tone(freq, dur):
    t = t_axis(dur)
    partials = [(0.5, 0.35, 2.4), (1.0, 1.0, 1.8), (1.19, 0.4, 1.1), (1.5, 0.3, 0.9), (2.0, 0.45, 0.8), (2.74, 0.2, 0.5), (3.76, 0.12, 0.3)]
    out = np.zeros_like(t)
    for ratio, amp, dec in partials:
        out += amp * np.sin(2 * np.pi * freq * ratio * t + rng.uniform(0, 6)) * np.exp(-t / dec)
    return out * env(len(t), 0.003, 50)


def chime():
    # an original 8-note school chime (not any existing melody)
    notes = [523.25, 659.25, 440.0, 392.0, 440.0, 523.25, 587.33, 523.25]
    buf = np.zeros(int(SR * 8.2))
    for i, f in enumerate(notes):
        place(buf, bell_tone(f, 3.0) * (0.9 if i % 4 == 0 else 0.75), i * 0.62)
    return norm(reverb(buf, 0.35, 0.7), 0.8)


def shutter():
    d = 0.3
    c = fft_filter(noise(d), lo=1500) * env(int(SR * d), 0.0003, 0.004)
    buf = np.zeros(int(SR * d))
    place(buf, c, 0.0)
    place(buf, c * 0.8, 0.07)
    return norm(buf, 0.7)


def siren():
    d = 4.0
    t = t_axis(d)
    freq = 980 + 330 * np.sin(2 * np.pi * t / 2.0)
    ph = 2 * np.pi * np.cumsum(freq) / SR
    tone = np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.18 * np.sin(3 * ph)
    trem = 0.85 + 0.15 * np.sin(2 * np.pi * 7 * t)
    fade = np.minimum(1, np.minimum(t / 0.05, (d - t) / 0.05))
    return norm(fft_filter(tone * trem * fade, hi=4000), 0.6)


def body_drop():
    d = 0.9
    thud = chirp(d, 60, 32, 0.2)
    rustle = fft_filter(noise(d), lo=200, hi=1600) * env(int(SR * d), 0.01, 0.25) * 0.5
    return norm(thud + rustle)


def burn():
    d = 3.0
    t = t_axis(d)
    roar = fft_filter(noise(d), hi=700) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * t))
    buf = roar * np.minimum(1, np.minimum(t / 0.4, (d - t) / 0.6))
    for _ in range(40):
        crack = fft_filter(noise(0.02), lo=2000) * env(int(SR * 0.02), 0.0002, 0.003)
        place(buf, crack * rng.uniform(0.2, 0.6), rng.uniform(0.1, d - 0.1))
    return norm(buf, 0.75)


def water():
    d = 3.0
    t = t_axis(d)
    hiss = fft_filter(noise(d), lo=1800, hi=9000) * (0.8 + 0.2 * np.sin(2 * np.pi * 3.3 * t))
    return norm(hiss * np.minimum(1, np.minimum(t / 0.2, (d - t) / 0.4)), 0.55)


def glass():
    d = 1.2
    buf = fft_filter(noise(d), lo=3000) * env(int(SR * d), 0.0005, 0.05)
    for _ in range(26):
        f = rng.uniform(2200, 7000)
        ping = np.sin(2 * np.pi * f * t_axis(0.4)) * np.exp(-t_axis(0.4) / rng.uniform(0.03, 0.12))
        place(buf, ping * rng.uniform(0.15, 0.4), rng.uniform(0.0, 0.5))
    return norm(reverb(buf, 0.3))


def unsheath():
    d = 0.8
    scrape = sweep_bandpass(noise(d) * env(int(SR * d), 0.05, 0.2), 2500, 6500, q=8) * 3
    ring = np.sin(2 * np.pi * 2850 * t_axis(d)) * env(int(SR * d), 0.18, 0.35) * 0.35
    return norm(scrape + ring, 0.7)


def footstep(v):
    d = 0.25
    tap = fft_filter(noise(d), lo=300, hi=2500 + v * 400) * env(int(SR * d), 0.001, 0.018)
    low = np.sin(2 * np.pi * (85 + v * 7) * t_axis(d)) * env(int(SR * d), 0.002, 0.03) * 0.6
    return norm(tap + low, 0.55)


def sting():
    d = 3.6
    t = t_axis(d)
    cluster = np.zeros_like(t)
    for f in (110.0, 116.54, 164.81, 174.61, 246.94):
        saw = 2 * ((f * t) % 1) - 1
        cluster += saw
    cluster = fft_filter(cluster, hi=1600)
    swell = np.clip(t / 0.5, 0, 1) ** 2 * np.exp(-np.maximum(t - 0.5, 0) / 1.1)
    hit = fft_filter(noise(d), hi=3000) * env(len(t), 0.48, 0.15) * (t > 0.48)
    vib = 1760 * (1 + 0.012 * np.sin(2 * np.pi * 6 * t))
    screech = fft_filter(2 * ((np.cumsum(vib) / SR) % 1) - 1, lo=900, hi=5000) * swell * 0.35
    return norm(reverb(cluster * swell * 0.5 + hit * 0.8 + screech, 0.4, 0.7), 0.85)


# ── music (original compositions) ───────────────────────────────────────────
def note_hz(n):
    names = {"C": -9, "D": -7, "E": -5, "F": -4, "G": -2, "A": 0, "B": 2}
    acc = 1 if "#" in n else (-1 if "b" in n[1:] else 0)
    octave = int(n[-1])
    semis = names[n[0]] + acc + (octave - 4) * 12
    return 440.0 * 2 ** (semis / 12)


def music_box(freq, dur):
    t = t_axis(dur)
    tone = np.sin(2 * np.pi * freq * t) + 0.25 * np.sin(2 * np.pi * freq * 3 * t) + 0.08 * np.sin(2 * np.pi * freq * 5.2 * t)
    return tone * env(len(t), 0.002, 0.9)


def soft_pad(freqs, dur):
    t = t_axis(dur)
    out = np.zeros_like(t)
    for f in freqs:
        for det in (-0.004, 0.004):
            out += np.sin(2 * np.pi * f * (1 + det) * t)
    shape = np.minimum(1, np.minimum(t / 0.6, (dur - t) / 0.8))
    return out * shape / (2 * len(freqs))


def make_loop(render, length):
    """render(buf_seconds) -> audio longer than length; fold the tail back to the start."""
    full = render(length + 4)
    n = int(SR * length)
    loop = full[:n].copy()
    tail = full[n:]
    loop[: len(tail)] += tail[: min(len(tail), n)]
    return loop


def music_calm():
    bpm = 88
    beat = 60 / bpm
    bars = 8
    length = bars * 4 * beat
    chords = [["A3", "C4", "E4"], ["F3", "A3", "C4"], ["C4", "E4", "G4"], ["E3", "G#3", "B3"]] * 2
    melody = [
        ("E5", 0, 1), ("A5", 1, 1), ("G5", 2, 0.5), ("E5", 2.5, 0.5), ("C5", 3, 1),
        ("F5", 4, 1.5), ("E5", 5.5, 0.5), ("C5", 6, 1), ("A4", 7, 1),
        ("G4", 8, 1), ("C5", 9, 1), ("E5", 10, 1), ("G5", 11, 1),
        ("G#5", 12, 1.5), ("B5", 13.5, 0.5), ("E5", 14, 2),
        ("A5", 16, 1), ("C6", 17, 0.5), ("B5", 17.5, 0.5), ("A5", 18, 1), ("E5", 19, 1),
        ("F5", 20, 1), ("A5", 21, 1), ("G5", 22, 0.5), ("F5", 22.5, 0.5), ("E5", 23, 1),
        ("D5", 24, 1), ("E5", 25, 0.5), ("G5", 25.5, 0.5), ("C5", 26, 2),
        ("B4", 28, 1), ("D5", 29, 1), ("G#4", 30, 1), ("E4", 31, 1),
    ]

    def render(sec):
        buf = np.zeros(int(SR * sec))
        for i, ch in enumerate(chords):
            place(buf, soft_pad([note_hz(n) for n in ch], 4 * beat + 0.8) * 0.35, i * 4 * beat)
        for n, b, _d in melody:
            place(buf, music_box(note_hz(n), 2.5) * 0.5, b * beat)
        return reverb(buf, 0.35, 0.7)

    return norm(make_loop(render, length), 0.7)


def music_tension():
    length = 24.0

    def render(sec):
        t = t_axis(sec)
        drone = np.zeros_like(t)
        for f in (55.0, 55.4, 82.4, 87.3):
            drone += 2 * ((f * t) % 1) - 1
        lfo = 0.5 + 0.5 * np.sin(2 * np.pi * t / 8)
        drone = fft_filter(drone, hi=240) * (0.5 + 0.3 * lfo)
        beat = 60 / 66
        buf = drone * 0.4
        k = 0
        while k * beat < sec:
            hb = heartbeat() * 0.5
            place(buf, hb, k * beat)
            k += 1
        high = np.zeros_like(t)
        for f in (1046.5, 1108.7):
            high += np.sin(2 * np.pi * f * t) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * t))
        buf += high * 0.03 * lfo
        return buf

    return norm(make_loop(render, length), 0.7)


def music_chase():
    bpm = 150
    beat = 60 / bpm
    length = 16 * beat * 2

    def render(sec):
        buf = np.zeros(int(SR * sec))
        kick = chirp(0.3, 130, 45, 0.09)
        tom = chirp(0.3, 210, 120, 0.12) * 0.6
        bass_notes = ["A1", "A1", "C2", "A1", "D2", "A1", "E2", "D#2"] * 4
        b = 0
        while b * beat < sec - 1:
            place(buf, kick, b * beat)
            if b % 4 == 3:
                place(buf, tom, (b + 0.5) * beat)
                place(buf, tom * 0.8, (b + 0.75) * beat)
            b += 1
        for i, n in enumerate(bass_notes * 2):
            at = i * 0.5 * beat
            if at > sec - 1:
                break
            f = note_hz(n)
            tt = t_axis(0.5 * beat)
            saw = (2 * ((f * tt) % 1) - 1) * env(len(tt), 0.003, 0.09)
            place(buf, fft_filter(saw, hi=900) * 0.7, at)
        hat = fft_filter(noise(0.05), lo=6000) * env(int(SR * 0.05), 0.0005, 0.012) * 0.25
        h = 0
        while h * 0.5 * beat < sec - 1:
            place(buf, hat, h * 0.5 * beat + 0.25 * beat)
            h += 1
        return buf

    return norm(make_loop(render, length), 0.75)


# ── output ──────────────────────────────────────────────────────────────────
def write_ogg(path, x):
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype(np.int16)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = tmp.name
    with wave.open(wav_path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "6", path], check=True)
    os.unlink(wav_path)


def main():
    os.makedirs(OUT, exist_ok=True)
    sfx = [
        ("Stab", stab(0)), ("Stab", stab(1)), ("Stab", stab(2)),
        ("Slash", slash(0)), ("Slash", slash(1)),
        ("Blunt", blunt(0)), ("Blunt", blunt(1)),
        ("Splat", splat(0)), ("Splat", splat(1)), ("Splat", splat(2)),
        ("Drip", drip()),
        ("Heartbeat", heartbeat()),
        ("Bell", chime()),
        ("Shutter", shutter()),
        ("Siren", siren()),
        ("BodyDrop", body_drop()),
        ("Burn", burn()),
        ("Water", water()),
        ("Glass", glass()),
        ("Unsheath", unsheath()),
        ("Footstep", footstep(0)), ("Footstep", footstep(1)), ("Footstep", footstep(2)), ("Footstep", footstep(3)),
        ("Sting", sting()),
    ]
    gap = 0.3
    sheet = []
    regions = {}
    cursor = 0.2
    sheet.append(np.zeros(int(SR * cursor)))
    for name, snd in sfx:
        dur = len(snd) / SR
        regions.setdefault(name, []).append([round(cursor, 4), round(dur, 4)])
        sheet.append(snd)
        sheet.append(np.zeros(int(SR * gap)))
        cursor += dur + gap
    write_ogg(os.path.join(OUT, "kurobara_sfx_sheet.ogg"), np.concatenate(sheet))
    write_ogg(os.path.join(OUT, "music_calm.ogg"), music_calm())
    write_ogg(os.path.join(OUT, "music_tension.ogg"), music_tension())
    write_ogg(os.path.join(OUT, "music_chase.ogg"), music_chase())

    lines = [
        "-- KUROBARA · generated by tools/make_audio.py — where each sound lives inside kurobara_sfx_sheet.ogg",
        "-- { start seconds, duration seconds } · do not edit by hand, re-run the script instead",
        "return {",
    ]
    for name, regs in regions.items():
        inner = ", ".join("{ %s, %s }" % (s, d) for s, d in regs)
        lines.append("\t%s = { %s }," % (name, inner))
    lines.append("}")
    with open(os.path.join(ROOT, "src", "shared", "AudioSheet.luau"), "w") as f:
        f.write("\n".join(lines) + "\n")
    print(json.dumps({k: len(v) for k, v in regions.items()}))
    print("sheet length: %.1fs" % cursor)


if __name__ == "__main__":
    main()
