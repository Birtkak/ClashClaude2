#!/usr/bin/env python3
"""Synthesizes the game's sound effects and battle music from scratch.

Everything is generated with simple oscillators, noise and envelopes, so there are
no third-party assets or licenses involved. Run from the repo root:

    python3 tools/make_sounds.py

Writes Ogg Vorbis files to app/src/main/res/raw/ (needs numpy and ffmpeg).
"""
import os
import subprocess
import tempfile
import wave

import numpy as np

RATE = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(7)


def t_axis(dur):
    return np.arange(int(RATE * dur)) / RATE


def env(n, attack=0.005, decay=None, curve=4.0):
    """Fast attack, exponential-ish decay over the whole length (or `decay` seconds)."""
    t = np.arange(n) / RATE
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    d_len = decay if decay else n / RATE
    d = np.clip(1 - t / d_len, 0, 1) ** curve
    return a * d


def noise(dur):
    return rng.uniform(-1, 1, int(RATE * dur))


def lowpass(x, cutoff):
    """One-pole low-pass; cutoff may be a number or an array (a sweep)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    alpha = 1 - np.exp(-2 * np.pi * cutoff / RATE)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += alpha[i] * (x[i] - acc)
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def sweep(f0, f1, dur, shape="sine"):
    """Oscillator gliding exponentially from f0 to f1 Hz."""
    t = t_axis(dur)
    f = f0 * (f1 / f0) ** (t / dur)
    phase = 2 * np.pi * np.cumsum(f) / RATE
    if shape == "square":
        return np.sign(np.sin(phase))
    if shape == "saw":
        return 2 * ((phase / (2 * np.pi)) % 1) - 1
    if shape == "tri":
        return 2 * np.abs(2 * ((phase / (2 * np.pi)) % 1) - 1) - 1
    return np.sin(phase)


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def pad(x, before=0.0):
    return np.concatenate([np.zeros(int(RATE * before)), x])


def normalize(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def write(name, x, peak=0.9):
    x = normalize(x, peak)
    # Tiny fade at both ends to avoid clicks.
    fade = min(len(x) // 4, int(RATE * 0.004))
    if fade > 0:
        x[:fade] *= np.linspace(0, 1, fade)
        x[-fade:] *= np.linspace(1, 0, fade)
    os.makedirs(OUT, exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        path = tmp.name
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    dest = os.path.join(OUT, name + ".ogg")
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", path, "-c:a", "libvorbis", "-q:a", "3", dest],
        check=True,
    )
    os.unlink(path)
    print(f"{dest}  {len(x) / RATE:.2f}s")


# ---------------------------------------------------------------- effects

def deploy():
    # Soft magical "whoomp" when a card is placed.
    n = noise(0.3)
    w = lowpass(n, np.linspace(2500, 300, len(n))) * env(len(n), 0.02, curve=2)
    tone = sweep(500, 900, 0.25) * env(int(RATE * 0.25), 0.01, curve=3) * 0.3
    return mix(w, tone)


def land(heavy=False):
    dur = 0.45 if heavy else 0.25
    thump = sweep(140 if heavy else 220, 45 if heavy else 80, dur) * env(int(RATE * dur), 0.002, curve=3)
    dust = lowpass(noise(dur), 900 if heavy else 1500) * env(int(RATE * dur), 0.002, curve=5) * 0.6
    return mix(thump, dust)


def sword():
    # Metallic clang: a few inharmonic partials plus a click.
    t = t_axis(0.35)
    partials = sum(np.sin(2 * np.pi * f * t) * a for f, a in [(1250, 1), (1870, 0.6), (2690, 0.4), (3510, 0.25)])
    ring = partials * env(len(t), 0.001, curve=6)
    click = highpass(noise(0.03), 2000) * env(int(RATE * 0.03), 0.001, curve=2)
    return mix(ring * 0.6, click)


def punch():
    body = sweep(180, 60, 0.18) * env(int(RATE * 0.18), 0.001, curve=3)
    slap = lowpass(noise(0.08), 2500) * env(int(RATE * 0.08), 0.001, curve=3) * 0.7
    return mix(body, slap)


def bow():
    # String twang plus the arrow whooshing off.
    twang = sweep(420, 300, 0.18, "tri") * env(int(RATE * 0.18), 0.001, curve=4)
    whoosh = highpass(noise(0.2), 1500) * env(int(RATE * 0.2), 0.03, curve=2) * 0.35
    return mix(twang, whoosh)


def hit():
    thunk = sweep(300, 120, 0.09) * env(int(RATE * 0.09), 0.001, curve=3)
    tick = highpass(noise(0.02), 3000) * env(int(RATE * 0.02), 0.001, curve=2) * 0.5
    return mix(thunk, tick)


def gun():
    crack = lowpass(noise(0.25), np.linspace(6000, 600, int(RATE * 0.25))) * env(int(RATE * 0.25), 0.001, curve=6)
    body = sweep(200, 70, 0.15) * env(int(RATE * 0.15), 0.001, curve=3) * 0.6
    return mix(crack, body)


def cannon():
    boom = sweep(110, 35, 0.6) * env(int(RATE * 0.6), 0.002, curve=3)
    blast = lowpass(noise(0.5), np.linspace(3000, 200, int(RATE * 0.5))) * env(int(RATE * 0.5), 0.001, curve=4) * 0.8
    return mix(boom, blast)


def fire():
    # Whooshing fireball launch.
    n = noise(0.4)
    w = lowpass(n, np.linspace(400, 2500, len(n))) * env(len(n), 0.08, curve=1.5)
    crackle = (rng.uniform(0, 1, len(n)) > 0.985) * rng.uniform(-1, 1, len(n)) * env(len(n), 0.05, curve=2) * 0.5
    return mix(w, crackle)


def explosion(big=False):
    dur = 0.9 if big else 0.5
    n = noise(dur)
    blast = lowpass(n, np.linspace(5000 if big else 3500, 150, len(n))) * env(len(n), 0.002, curve=3)
    boom = sweep(90 if big else 130, 30, dur) * env(int(RATE * dur), 0.002, curve=2) * (1.0 if big else 0.7)
    return mix(blast, boom)


def throw():
    n = noise(0.25)
    return lowpass(n, np.linspace(600, 2200, len(n))) * env(len(n), 0.05, curve=2)


def blip():
    return sweep(700, 1400, 0.1, "tri") * env(int(RATE * 0.1), 0.002, curve=2)


def zap():
    # Electric buzz: square wave with random frequency jumps, plus crackle.
    dur = 0.35
    t = t_axis(dur)
    f = 90 + 400 * (rng.uniform(0, 1, len(t)) > 0.97).cumsum() % 3
    buzz = np.sign(np.sin(2 * np.pi * np.cumsum(f) / RATE))
    crack = highpass(noise(dur), 2500) * (rng.uniform(0, 1, len(t)) > 0.7)
    return mix(buzz * 0.5, crack * 0.6) * env(len(t), 0.001, curve=2)


def spin():
    n = noise(0.35)
    sweep_cut = 800 + 1600 * np.abs(np.sin(np.linspace(0, 2 * np.pi, len(n))))
    return lowpass(n, sweep_cut) * env(len(n), 0.03, curve=1.5)


def volley():
    # Many arrows released at once.
    out = np.zeros(int(RATE * 0.6))
    for i in range(7):
        b = bow() * 0.5
        start = int(RATE * rng.uniform(0, 0.15))
        out[start:start + len(b)] += b[: len(out) - start]
    return out


def death():
    # Cartoon "poof".
    n = noise(0.3)
    puff = lowpass(n, np.linspace(3000, 400, len(n))) * env(len(n), 0.005, curve=3)
    pop = sweep(600, 200, 0.12) * env(int(RATE * 0.12), 0.001, curve=3) * 0.5
    return mix(puff, pop)


def tower_down():
    rumble = lowpass(noise(1.4), 400) * env(int(RATE * 1.4), 0.02, curve=2)
    boom = explosion(big=True)
    rocks = np.zeros(int(RATE * 1.4))
    for _ in range(12):
        r = hit() * rng.uniform(0.2, 0.5)
        s = int(RATE * rng.uniform(0.2, 1.1))
        rocks[s:s + len(r)] += r[: len(rocks) - s]
    return mix(boom, rumble * 0.8, rocks)


def click():
    return sweep(1800, 1200, 0.04, "tri") * env(int(RATE * 0.04), 0.001, curve=2)


def deny():
    a = sweep(220, 200, 0.12, "square") * env(int(RATE * 0.12), 0.002, curve=1)
    b = pad(sweep(180, 160, 0.16, "square") * env(int(RATE * 0.16), 0.002, curve=1.5), 0.13)
    return mix(a, b) * 0.6


NOTE = {n: 440 * 2 ** ((i - 9) / 12) for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}


def freq(name):
    """'A4' -> Hz."""
    return NOTE[name[:-1]] * 2 ** (int(name[-1]) - 4)


def tone(f, dur, shape="square", decay_curve=1.5, vol=1.0):
    n = int(RATE * dur)
    t = np.arange(n) / RATE
    phase = 2 * np.pi * f * t
    if shape == "square":
        w = np.sign(np.sin(phase)) * 0.5 + np.sign(np.sin(phase * 2)) * 0.1
    elif shape == "tri":
        w = 2 * np.abs(2 * ((f * t) % 1) - 1) - 1
    else:
        w = np.sin(phase)
    return w * env(n, 0.005, curve=decay_curve) * vol


def melody(notes, step, shape="square", curve=1.5, vol=1.0):
    out = []
    for n in notes:
        if n == "-":
            out.append(np.zeros(int(RATE * step)))
        else:
            out.append(tone(freq(n), step, shape, curve, vol))
    return np.concatenate(out)


def victory():
    return mix(
        melody(["C5", "E5", "G5", "C6", "-", "G5", "C6", "C6"], 0.12, curve=1.2),
        melody(["C4", "C4", "E4", "E4", "G4", "G4", "C5", "C5"], 0.12, "tri", vol=0.6),
    )


def defeat():
    return mix(
        melody(["G4", "-", "F#4", "-", "F4", "-", "E4", "E4", "E4", "E4"], 0.16, curve=1.0),
        melody(["C3", "C3", "B2", "B2", "A#2", "A#2", "A2", "A2", "A2", "A2"], 0.16, "tri", vol=0.6),
    )


# ---------------------------------------------------------------- music

def battle_music():
    """Eight-bar upbeat loop in A minor, 132 bpm, with lead, bass and drums."""
    bpm = 132
    eighth = 60 / bpm / 2
    lead = [
        "A4", "-", "C5", "E5", "D5", "C5", "B4", "C5",
        "A4", "-", "E4", "A4", "B4", "C5", "B4", "G4",
        "F4", "-", "A4", "C5", "B4", "A4", "G4", "A4",
        "E4", "-", "G#4", "B4", "E5", "-", "D5", "B4",
        "A4", "-", "C5", "E5", "A5", "G5", "E5", "C5",
        "D5", "-", "F5", "D5", "C5", "B4", "A4", "B4",
        "C5", "B4", "A4", "G4", "F4", "G4", "A4", "B4",
        "A4", "-", "E4", "-", "A4", "-", "-", "-",
    ]
    bass_roots = ["A2", "A2", "F2", "E2", "A2", "D3", "F2", "E2"]
    bass = []
    for root in bass_roots:
        fifth = {"A2": "E3", "F2": "C3", "E2": "B2", "D3": "A3"}[root]
        bass += [root, root, fifth, root, root, fifth, root, fifth]
    lead_track = melody(lead, eighth, "square", curve=1.2, vol=0.35)
    bass_track = melody(bass, eighth, "tri", curve=0.8, vol=0.5)
    # Drums: kick on beats, snare on 2 and 4, hats on eighths.
    total = len(lead_track)
    drums = np.zeros(total)
    beat = int(RATE * eighth * 2)
    kick = sweep(150, 45, 0.15) * env(int(RATE * 0.15), 0.001, curve=3) * 0.9
    snare = highpass(noise(0.15), 1200) * env(int(RATE * 0.15), 0.001, curve=4) * 0.35
    hat = highpass(noise(0.04), 6000) * env(int(RATE * 0.04), 0.001, curve=3) * 0.12
    for i in range(total // beat):
        s = i * beat
        drums[s:s + len(kick)] += kick[: total - s]
        if i % 2 == 1:
            drums[s:s + len(snare)] += snare[: total - s]
        for h in (s, s + beat // 2):
            drums[h:h + len(hat)] += hat[: max(0, total - h)]
    music = mix(lead_track, bass_track, drums)
    return lowpass(music, 7000)


SOUNDS = {
    "sfx_deploy": deploy,
    "sfx_land": lambda: land(False),
    "sfx_land_heavy": lambda: land(True),
    "sfx_sword": sword,
    "sfx_punch": punch,
    "sfx_bow": bow,
    "sfx_hit": hit,
    "sfx_gun": gun,
    "sfx_cannon": cannon,
    "sfx_fire": fire,
    "sfx_explosion": lambda: explosion(False),
    "sfx_big_explosion": lambda: explosion(True),
    "sfx_throw": throw,
    "sfx_blip": blip,
    "sfx_zap": zap,
    "sfx_spin": spin,
    "sfx_volley": volley,
    "sfx_death": death,
    "sfx_tower_down": tower_down,
    "sfx_click": click,
    "sfx_deny": deny,
    "sfx_victory": victory,
    "sfx_defeat": defeat,
}

if __name__ == "__main__":
    for name, make in SOUNDS.items():
        write(name, make())
    write("music_battle", battle_music(), peak=0.6)
