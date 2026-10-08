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


# ---------------------------------------------------------------- shaping
# The sound palette is deliberately dark and heavy: every impact is layered on a sub-bass
# "thump" that drops in pitch, saturated for punch, low-passed to remove harsh highs, and
# given a short room reverb so it feels like it lands somewhere.

def saturate(x, drive=2.5):
    return np.tanh(x * drive) / np.tanh(drive)


def reverb(x, length=0.7, mix=0.25, damp=2500):
    """Cheap convolution reverb: an exponentially decaying, low-passed noise tail."""
    n = int(RATE * length)
    tail = lowpass(rng.uniform(-1, 1, n), damp) * np.exp(-np.arange(n) / RATE * 6.0 / length)
    tail /= np.sqrt(np.sum(tail ** 2)) + 1e-9
    size = len(x) + n
    wet = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(tail, size), size)
    dry = np.concatenate([x, np.zeros(n)])
    return dry * (1 - mix) + wet / (np.max(np.abs(wet)) + 1e-9) * np.max(np.abs(x)) * mix


def thump(f0, f1, dur, curve=2.5):
    """Sub-bass kick: a sine dropping from f0 to f1 Hz."""
    return sweep(f0, f1, dur) * env(int(RATE * dur), 0.002, curve=curve)


def dirt(dur, cutoff, curve=4):
    """Low-passed noise burst: dust, crunch, debris."""
    return lowpass(noise(dur), cutoff) * env(int(RATE * dur), 0.001, curve=curve)


def heavy(x, drive=2.2, room=0.5, wet=0.22):
    return reverb(saturate(lowpass(x, 5000), drive), room, wet)


# ---------------------------------------------------------------- effects

def deploy():
    # A dark "summon": a low swell that ends in a soft boom.
    n = noise(0.45)
    swell = lowpass(n, np.linspace(200, 900, len(n))) * np.linspace(0, 1, len(n)) ** 2 * 0.6
    boom = pad(thump(90, 35, 0.35), 0.3)
    return heavy(mix(swell, boom), 1.8, 0.6, 0.25)


def land(heavy_land=False):
    if heavy_land:
        return heavy(mix(thump(70, 22, 0.6, 2), dirt(0.5, 500, 3) * 0.8), 2.8, 0.8, 0.3)
    return heavy(mix(thump(95, 35, 0.3), dirt(0.25, 700) * 0.6), 2.2, 0.4, 0.18)


def sword():
    # Dark steel: low inharmonic ring, a band-limited slash, and body impact.
    t = t_axis(0.4)
    ring = sum(np.sin(2 * np.pi * f * t) * a for f, a in [(310, 1), (467, 0.7), (692, 0.45), (1010, 0.25)])
    ring = ring * env(len(t), 0.001, curve=7) * 0.45
    slash = lowpass(highpass(noise(0.08), 600), 2600) * env(int(RATE * 0.08), 0.004, curve=2) * 0.8
    return heavy(mix(ring, slash, thump(120, 45, 0.18) * 0.9), 2.4, 0.4, 0.2)


def punch():
    return heavy(mix(thump(100, 32, 0.28, 2), dirt(0.1, 1200, 3) * 0.7), 3.0, 0.35, 0.15)


def bow():
    # Low string thrum and a dull whoosh.
    thrum = sweep(150, 105, 0.22, "tri") * env(int(RATE * 0.22), 0.001, curve=4)
    whoosh = lowpass(highpass(noise(0.25), 300), 1400) * env(int(RATE * 0.25), 0.04, curve=2) * 0.5
    return heavy(mix(thrum, whoosh), 1.6, 0.3, 0.12)


def hit():
    return heavy(mix(thump(140, 55, 0.12), dirt(0.05, 1500, 3) * 0.6), 2.5, 0.25, 0.12)


def gun():
    crack = lowpass(noise(0.3), np.linspace(3500, 250, int(RATE * 0.3))) * env(int(RATE * 0.3), 0.001, curve=5)
    return heavy(mix(crack, thump(110, 35, 0.3) * 0.9), 3.0, 0.7, 0.3)


def cannon():
    boom = thump(65, 20, 0.9, 1.8)
    blast = lowpass(noise(0.7), np.linspace(2200, 120, int(RATE * 0.7))) * env(int(RATE * 0.7), 0.001, curve=3) * 0.8
    return heavy(mix(boom, blast), 3.2, 1.0, 0.32)


def fire():
    n = noise(0.5)
    roar = lowpass(n, np.linspace(250, 1100, len(n))) * env(len(n), 0.08, curve=1.5)
    rumble = thump(60, 40, 0.5, 1.2) * 0.5
    return heavy(mix(roar, rumble), 2.0, 0.5, 0.2)


def explosion(big=False):
    dur = 1.4 if big else 0.75
    n = noise(dur)
    blast = lowpass(n, np.linspace(2600 if big else 2000, 80, len(n))) * env(len(n), 0.002, curve=2.5)
    boom = thump(75 if big else 95, 18, dur, 1.6) * (1.2 if big else 0.9)
    debris = np.zeros(int(RATE * dur))
    for _ in range(10 if big else 5):
        d = dirt(0.06, 900, 3) * rng.uniform(0.1, 0.3)
        s = int(RATE * rng.uniform(0.1, dur * 0.7))
        debris[s:s + len(d)] += d[: len(debris) - s]
    return heavy(mix(blast, boom, debris), 3.4 if big else 2.8, 1.4 if big else 0.9, 0.35)


def throw():
    n = noise(0.3)
    return heavy(lowpass(n, np.linspace(250, 1000, len(n))) * env(len(n), 0.06, curve=2), 1.4, 0.3, 0.12)


def blip():
    # Minion shot: a dark magic pulse.
    pulse = sweep(220, 330, 0.14, "tri") * env(int(RATE * 0.14), 0.002, curve=2)
    return heavy(mix(pulse * 0.6, thump(120, 60, 0.12) * 0.6), 2.0, 0.3, 0.2)


def zap():
    dur = 0.45
    t = t_axis(dur)
    f = 55 + 30 * (rng.uniform(0, 1, len(t)) > 0.96).cumsum() % 4
    buzz = lowpass(np.sign(np.sin(2 * np.pi * np.cumsum(f) / RATE)), 1800)
    crackle = lowpass(highpass(noise(dur), 1200), 4500) * (rng.uniform(0, 1, len(t)) > 0.75)
    body = mix(buzz * 0.6, crackle * 0.5, thump(110, 40, 0.2) * 0.8) * env(len(t), 0.001, curve=1.8)
    return heavy(body, 2.4, 0.5, 0.22)


def spin():
    n = noise(0.45)
    cut = 250 + 900 * np.abs(np.sin(np.linspace(0, 2 * np.pi, len(n))))
    return heavy(mix(lowpass(n, cut) * env(len(n), 0.03, curve=1.5), thump(90, 50, 0.3) * 0.4), 1.8, 0.4, 0.18)


def volley():
    out = np.zeros(int(RATE * 0.8))
    for _ in range(8):
        b = bow() * 0.45
        start = int(RATE * rng.uniform(0, 0.18))
        out[start:start + len(b)] += b[: len(out) - start]
    return out


def death():
    # A low, muffled collapse.
    groan = sweep(130, 55, 0.35, "saw") * env(int(RATE * 0.35), 0.01, curve=2)
    return heavy(mix(lowpass(groan, 600) * 0.6, dirt(0.3, 800, 3) * 0.6, thump(80, 30, 0.3) * 0.7), 2.2, 0.6, 0.25)


def tower_down():
    rumble = lowpass(noise(2.2), 220) * env(int(RATE * 2.2), 0.05, curve=1.6)
    rocks = np.zeros(int(RATE * 2.2))
    for _ in range(16):
        r = hit() * rng.uniform(0.15, 0.4)
        s = int(RATE * rng.uniform(0.25, 1.6))
        rocks[s:s + len(r)] += r[: len(rocks) - s]
    return mix(explosion(big=True), rumble * 0.9, rocks)


def click():
    return heavy(mix(sweep(520, 380, 0.05, "tri") * env(int(RATE * 0.05), 0.001, curve=2), thump(140, 80, 0.06) * 0.5), 1.5, 0.15, 0.1)


def deny():
    a = lowpass(sweep(95, 85, 0.16, "square"), 900) * env(int(RATE * 0.16), 0.002, curve=1)
    b = pad(lowpass(sweep(80, 70, 0.2, "square"), 900) * env(int(RATE * 0.2), 0.002, curve=1.5), 0.17)
    return heavy(mix(a, b) * 0.7, 1.6, 0.2, 0.1)

# ---------------------------------------------------------------- music & jingles

NOTE = {n: 440 * 2 ** ((i - 9) / 12) for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}


def freq(name):
    """'A4' -> Hz."""
    return NOTE[name[:-1]] * 2 ** (int(name[-1]) - 4)


def voice(f, dur, kind="saw", cutoff=1200, curve=1.2, vol=1.0):
    """A filtered oscillator note: dark saw brass, square bass, or a soft sine pad."""
    n = int(RATE * dur)
    t = np.arange(n) / RATE
    if kind == "saw":
        w = sum((2 * ((f * k * t) % 1) - 1) / k for k in (1, 1.004))  # two slightly detuned saws
    elif kind == "square":
        w = np.sign(np.sin(2 * np.pi * f * t))
    else:
        w = np.sin(2 * np.pi * f * t) + 0.3 * np.sin(4 * np.pi * f * t)
    return lowpass(w, cutoff) * env(n, 0.01, curve=curve) * vol


def seq(notes, step, **kw):
    out = []
    for n in notes:
        if n == "-":
            out.append(np.zeros(int(RATE * step)))
        elif n == "~":  # hold the previous note: extend silence-free by repeating decay tail
            out.append(np.zeros(int(RATE * step)))
        else:
            chord = n.split("+")
            out.append(sum(voice(freq(c), step, **kw) for c in chord) / len(chord))
    return np.concatenate(out)


def timpani(dur=0.6, f=55):
    return mix(thump(f * 1.6, f, dur, 2), dirt(0.05, 400, 3) * 0.3)


def victory():
    # Heavy brass stabs rising to a held major chord over timpani.
    brass = seq(["C3+G3+C4", "-", "D#3+A#3+D#4", "-", "F3+C4+F4", "G3+D4+G4", "C3+G3+C4+E4", "~", "~", "~"], 0.16, cutoff=1500, curve=0.9, vol=0.9)
    drums = mix(timpani(), pad(timpani(), 0.32), pad(timpani(0.9, 45), 0.96))
    return heavy(mix(brass, drums), 1.8, 1.2, 0.3)


def defeat():
    # A slow, descending minor line in low brass with a final deep hit.
    brass = seq(["G2+D3", "~", "F#2+C#3", "~", "F2+C3", "~", "D2+A2+D3", "~", "~", "~"], 0.22, cutoff=900, curve=0.8, vol=0.9)
    hit_ = pad(thump(60, 22, 1.2, 1.5), 1.32)
    return heavy(mix(brass, hit_), 2.0, 1.4, 0.35)


def battle_music():
    """Dark 8-bar loop in D minor, 104 bpm: war drums, a pulsing sub bass, low brass and a drone."""
    bpm = 104
    eighth = 60 / bpm / 2
    bars = 8
    total = int(RATE * eighth * 8 * bars)
    roots = ["D2", "D2", "A#1", "C2", "D2", "D2", "A#1", "A1"]
    bass = []
    for r in roots:
        bass += [r, r, "-", r, r, "-", r, r]
    bass_track = seq(bass, eighth, kind="square", cutoff=260, curve=1.5, vol=0.9)
    chords = ["D3+F3+A3", "D3+F3+A3", "A#2+D3+F3", "C3+E3+G3", "D3+F3+A3", "D3+G3+A#3", "A#2+D3+F3", "A2+C#3+E3"]
    brass_track = seq([c for c in chords for _ in range(2)], eighth * 4, kind="saw", cutoff=700, curve=0.6, vol=0.45)
    drone = voice(freq("D2"), total / RATE, kind="sine", cutoff=300, curve=0.01, vol=0.25)
    drums = np.zeros(total)
    beat = int(RATE * eighth * 2)
    kick = thump(70, 30, 0.35, 2.2) * 1.1
    tom = thump(110, 70, 0.25, 2) * 0.6
    snare = lowpass(highpass(noise(0.25), 300), 2500) * env(int(RATE * 0.25), 0.001, curve=3) * 0.45
    for i in range(total // beat):
        s = i * beat
        hits = [kick] if i % 4 in (0, 2) else [snare]
        if i % 8 == 7:
            hits += [pad(tom, eighth), pad(tom * 0.8, eighth * 1.5)]
        for h in hits:
            drums[s:s + len(h)] += h[: max(0, total - s)]
    music = mix(bass_track, brass_track, drone, drums)[:total]
    # Wrap the reverb tail around so the loop point is seamless.
    wet = reverb(saturate(music, 1.4), 1.2, 0.25)
    out = wet[:total].copy()
    out[: len(wet) - total] += wet[total:]
    return lowpass(out, 6000)


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
