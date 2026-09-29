#!/usr/bin/env python3
"""Ролик всей экосистемы TexFi (f0kus, m0ney, files, w0y): 1920x1080, 60 fps, 30 с, свой звук.

Использует кисти, шрифты, звук и переходы из make_promo.py (тот же язык: синий
#4a7dfb, кремовый, янтарь, Press Start 2P, жёсткие тени, монтаж по долям 120 BPM).

    python3 tools/make_promo_texfi.py preview   # контактный лист
    python3 tools/make_promo_texfi.py render    # docs/promo/texfi-desktop.mp4
"""
import math
import os
import struct
import subprocess
import sys
import wave

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import make_promo as mp  # noqa: E402

W, H = 1920, 1080
mp.W, mp.H = W, H  # backdrop/flat_bg берут размер из модуля

from make_promo import (AMBER, BG, BLUE, CARD, CREAM, DIM, GREEN, MUTED, Canvas, FPS, BEAT, big, clamp,  # noqa: E402
                        flat_bg, backdrop, glyph, h01, lerp, out_back, out_cubic, pf, prog, sf, slam,
                        split_text, steps, typed, INK)

DUR = 30.0
ROOT = mp.ROOT
CX, CY = W // 2, H // 2

ICONS = {
    "f0kus": ["XXXXXXXX", ".XXXXXX.", "..XXXX..", "...XX...", "...XX...", "..X..X..", ".X.XX.X.", "XXXXXXXX"],
    "m0ney": ["..XXXX..", ".XXXXXX.", "XX.XX.XX", "XXXXXXXX", "XXXXXXXX", "XX.XX.XX", ".XXXXXX.", "..XXXX.."],
    "files": ["XXXXX...", "XXXXXX..", "XXXXXXX.", "XXXXXXXX", "XX...XXX", "XX...XXX", "XX...XXX", "XXXXXXXX"],
    "w0y": ["....XXXX", "....XXXX", "....X..X", "....X..X", "....X..X", "XXXXX.XX", "XXXXX.XX", "XXX...XX"],
}
APPS = [("f0kus", "FOCUS.", "stay on the task", AMBER), ("m0ney", "MONEY.", "know where it goes", GREEN),
        ("files", "FILES.", "text and files, any size", CREAM), ("w0y", "MUSIC.", "YouTube Music, your way", BLUE)]
COLOR = dict((a[0], a[3]) for a in APPS)


def tile(c, cx, cy, size, name, e=1.0, hot=False):
    half = size / 2 * e
    if half < 6:
        return
    sh = size * 0.06
    c.rect(cx - half + sh, cy - half + sh, cx + half + sh, cy + half + sh, BLUE)
    c.rect(cx - half, cy - half, cx + half, cy + half, CARD)
    c.frame(cx - half, cy - half, cx + half, cy + half, CREAM if hot else BLUE, max(3, int(size * 0.03)))
    cell = max(2, int(size * 0.075 * e))
    glyph(c, ICONS[name], cx - 4 * cell, cy - 4 * cell, cell, COLOR[name])


def scene_hello(c, s):
    d = slam(s, 0.0, 0.2, 500)
    big(c, "4 APPS.", CX, 380 + d, 176, CREAM, BLUE, 10)
    for i, (name, *_r) in enumerate(APPS):
        st = 0.5 + 0.15 * i
        p = steps(prog(s, st, 0.2), 5)
        if p > 0:
            tile(c, 465 + i * 330, 720, 250, name, out_back(p), s - st < 0.05)
    if s > 1.2:
        c.text(typed("one family.", s, 1.2, 0.05), CX, 940, sf(52), MUTED, "mm")


def scene_style(c, s):
    d = slam(s, 0.0, 0.2, 500)
    big(c, "ONE LOOK.", CX, 400 + d, 190, INK, BLUE, 12)
    for i in range(6):
        st = 0.5 + 0.1 * i
        if s < st:
            continue
        x = 510 + i * 150
        c.rect(x + 8, 640 + 8, x + 120 + 8, 640 + 120 + 8, BLUE)
        c.rect(x, 640, x + 120, 760, [CREAM, BLUE, AMBER, INK, GREEN, DIM][i])
        c.frame(x, 640, x + 120, 760, INK, 4)
    if s > 1.2:
        c.text(typed("#4A7DFB  pixels. hard shadows. no blur.", s, 1.2, 0.04), CX, 900, sf(44), INK, "mm")


LX, RX = 520, 1400


def app_head(c, s, i, fg):
    name, word, cap, col = APPS[i]
    c.text(name, LX, 300, pf(48), fg, "mm")
    split_text(c, word, LX, 450, 128, 128, fg, None, s, 0.05, 160)
    if s > 0.5:
        c.text(typed(cap, s, 0.5, 0.03), LX, 600, sf(46), fg, "mm")
    tile(c, LX, 800, 160, name, steps(prog(s, 0.6, 0.2), 5))


def scene_f0kus(c, s):
    app_head(c, s, 0, CREAM)
    p = prog(s, 0.3, 3.6) * 0.75
    left = int(25 * 60 * (1 - p))
    c.text("%02d:%02d" % (left // 60, left % 60), RX, 460, pf(140), CREAM, "mm")
    c.rect(1000, 640, 1800, 700, CARD)
    c.rect(1000, 640, 1000 + 800 * p / 0.75, 700, AMBER)
    c.frame(1000, 640, 1800, 700, CREAM, 6)
    for k in range(8):
        c.rect(1000 + k * 100, 780, 1000 + k * 100 + 80, 810, AMBER if (k / 8) < p / 0.75 else DIM)


def scene_m0ney(c, s):
    app_head(c, s, 1, INK)
    n = int(out_cubic(prog(s, 0.3, 2.0)) * 1284)
    c.text("+%d.50" % n, RX, 400, pf(100), INK, "mm")
    for i in range(8):
        st = 0.5 + 0.2 * i
        if s < st:
            continue
        h = 60 + int(h01(i, 3) * 300)
        e = steps(prog(s, st, 0.25), 4)
        x = 1000 + i * 100
        c.rect(x, 900 - h * e, x + 70, 900, CARD if i % 2 else INK)
        c.rect(x, 900 - h * e, x + 70, 900 - h * e + 10, CREAM)


def scene_files(c, s):
    app_head(c, s, 2, INK)
    for x in (960, 1640):
        c.rect(x + 10, 400 + 10, x + 200 + 10, 700 + 10, BLUE)
        c.rect(x, 400, x + 200, 700, CARD)
        c.frame(x, 400, x + 200, 700, BLUE, 6)
    for i in range(6):
        p = ((s * 1.3 - i * 0.17) % 1.0)
        x = lerp(1200, 1580, steps(p, 12))
        c.rect(x, 520, x + 60, 580, INK if i % 2 == 0 else BLUE)
    if s > 1.5:
        c.text(typed("no size limit. really.", s, 1.5, 0.04), 1400, 850, sf(44), INK, "mm")


def scene_w0y(c, s):
    app_head(c, s, 3, CREAM)
    for i in range(16):
        amp = 0.35 + 0.65 * abs(math.sin(i * 1.3 + s * 7))
        h = max(24, int(480 * amp) // 12 * 12)
        x = 1000 + i * 50
        c.rect(x, 860 - h, x + 34, 860, CREAM if i % 3 else AMBER)


def scene_grid(c, s):
    d = slam(s, 0.0, 0.2, 500)
    big(c, "TEXFI.", CX, 240 + d, 150, CREAM, BLUE, 10)
    for i, (name, *_r) in enumerate(APPS):
        st = 0.5 + 0.2 * i
        if s < st:
            continue
        e = out_back(steps(prog(s, st, 0.2), 5))
        tile(c, 390 + i * 380, 640, 300, name, e, s - st < 0.05)
    if s > 1.8:
        c.text(typed("f0kus   m0ney   files   w0y", s, 1.8, 0.04), CX, 900, pf(36), AMBER, "mm")


SCENES = [(0.0, 2.0, "hello", BG), (2.0, 4.0, "style", CREAM), (4.0, 8.5, "f0kus", BLUE), (8.5, 13.0, "m0ney", GREEN),
          (13.0, 17.5, "files", CREAM), (17.5, 22.0, "w0y", BG), (22.0, 25.0, "grid", BG)]
FN = {"hello": scene_hello, "style": scene_style, "f0kus": scene_f0kus, "m0ney": scene_m0ney,
      "files": scene_files, "w0y": scene_w0y, "grid": scene_grid}
OUT_T = 25.0
SHAKES = [(s[0], 18) for s in SCENES] + [(OUT_T, 16), (OUT_T + 0.8, 10)]
FLASHES = [(2.0, 0.3), (22.0, 0.3), (OUT_T, 0.25)]
TRANS = [s[0] for s in SCENES[1:]] + [OUT_T]


def scene_outro(c, t):
    u = t - OUT_T
    for i, (name, *_r) in enumerate(APPS):
        p = steps(prog(u, 0.05 + 0.1 * i, 0.2), 5)
        if p > 0:
            tile(c, 660 + i * 200, 250, 170, name, out_back(p))
    c.stext(typed("TexFi", t, OUT_T + 0.6), CX - 2.5 * 130, 520, pf(130), CREAM, shadow=BLUE, off=8)
    if u > 1.4:
        c.text(typed("f0kus  m0ney  files  w0y", t, OUT_T + 1.4, 0.03), CX, 680, pf(38), AMBER, "mm")
    if u > 2.2:
        w = 800
        p = steps(prog(u, 2.2, 0.2), 5)
        c.rect(CX - w / 2 * p, 780, CX + w / 2 * p, 860, BLUE)
        if p >= 1:
            c.text(typed("texfi-hub.vercel.app/en", t, OUT_T + 2.4, 0.02), CX, 820, sf(50), CREAM, "mm")
    c.text(typed("animation create by ai", t, OUT_T + 2.8, 0.03), CX, 960, pf(28), MUTED, "mm")


def render_core(t):
    ox = oy = 0
    for te, amp in SHAKES:
        dt = t - te
        if 0 <= dt < 0.4:
            a = amp * math.exp(-dt * 12)
            ox += round(a * math.cos(dt * 70))
            oy += round(a * math.sin(dt * 83))
    img = Image.new("RGB", (W, H), BG)
    c = Canvas(img, ox, oy)
    for a, b, nm, colr in SCENES:
        if a <= t < b:
            if colr == BG:
                backdrop(c, t)
            else:
                flat_bg(c, colr)
            FN[nm](c, t - a)
            break
    else:
        backdrop(c, t)
        scene_outro(c, t)
    for tf, amp in FLASHES:
        dt = t - tf
        if 0 <= dt < 0.25:
            img = Image.blend(img, Image.new("RGB", (W, H), CREAM), amp * math.exp(-dt * 18))
    return img


def render_frame(t):
    img = render_core(t)
    for tc in TRANS:
        dt = t - tc
        if 0 <= dt < 0.16:
            prev = render_core(tc - 0.02)
            q = dt / 0.16
            blk = 90
            mask = Image.new("L", (W, H), 0)
            md = ImageDraw.Draw(mask)
            for gx in range(0, W, blk):
                for gy in range(0, H, blk):
                    if h01(gx // blk, gy // blk, 9) > q:
                        md.rectangle([gx, gy, gx + blk - 1, gy + blk - 1], fill=255)
            img = Image.composite(prev, img, mask)
    z = 0.03 * math.exp(-(t % BEAT) * 16) if t < DUR - 2.5 else 0.0
    if z > 0.004:
        cw, ch = W / (1 + z), H / (1 + z)
        l, tp = (W - cw) / 2, (H - ch) / 2
        img = img.resize((W, H), Image.BILINEAR, box=(l, tp, l + cw, tp + ch))
    if t > DUR - 0.5:
        img = Image.blend(img, Image.new("RGB", (W, H), BG), clamp((t - (DUR - 0.5)) / 0.5))
    return img


def build_audio(path):
    tr = mp.Track(DUR + 0.8)
    k_wave, h_wave, c_wave = mp.kick(), mp.hat(), mp.clap()
    chords = [[57, 60, 64, 69], [53, 57, 60, 65], [60, 64, 67, 72], [55, 59, 62, 67]]
    bass = [45, 41, 48, 43]
    for te, _ in SHAKES:
        tr.put(te, mp.crash(0.8, 4.0), 0.4)
        tr.put(te, mp.kick(0.5), 1.0)
        tr.put(te, mp.sq(880, 0.12, 0.5, 26.0), 0.16)
    tr.put(OUT_T - 0.5, mp.riser(0.5), 0.35)
    for b in range(int((DUR - 2.0) / BEAT)):
        t = b * BEAT
        tr.put(t, k_wave, 0.9)
        tr.put(t + BEAT / 2, h_wave, 0.28)
        if t >= 2.0 and b % 2 == 1:
            tr.put(t, c_wave, 0.5)
        if t >= 2.0:
            ci = int((t - 2.0) / 2.0) % 4
            tr.put(t, mp.sq(mp.midi(bass[ci]), BEAT * 0.9, 0.5, 5.0), 0.16)
            for s16 in range(4):
                ts = t + s16 * BEAT / 4
                nn = chords[ci][[0, 1, 2, 3, 2, 1][(b * 4 + s16) % 6]]
                tr.put(ts, mp.sq(mp.midi(nn), 0.16, 0.25, 14.0), 0.10)
                if t >= 8.5:
                    tr.put(ts + 0.03, mp.sq(mp.midi(nn + 12), 0.12, 0.125, 18.0), 0.045)
    for n in (69, 72, 76):
        tr.put(OUT_T + 0.8, mp.sq(mp.midi(n), 2.5, 0.25, 1.5), 0.10)
    out = bytearray()
    for i, x in enumerate(tr.buf):
        fade = clamp((DUR + 0.5 - i / mp.SR) / 0.9)
        v = int(math.tanh(x * 0.9) * 0.9 * fade * 32767)
        out += struct.pack("<hh", v, v)
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(mp.SR)
        w.writeframes(bytes(out))


def preview():
    times = [0.5, 1.5, 2.6, 3.5, 5.0, 7.0, 9.5, 11.5, 14.0, 16.0, 18.5, 20.5, 22.8, 24.5, 26.0, 27.5, 28.5, 29.5]
    tw, th, cols = 480, 270, 6
    rows = math.ceil(len(times) / cols)
    sheet = Image.new("RGB", (tw * cols, th * rows))
    for i, t in enumerate(times):
        sheet.paste(render_frame(t).resize((tw, th), Image.LANCZOS), ((i % cols) * tw, (i // cols) * th))
    out = os.environ.get("PROMO_SHEET", os.path.join(ROOT, "docs", "promo", "sheet-texfi-desktop.png"))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print(out)


def render():
    out_dir = os.path.join(ROOT, "docs", "promo")
    os.makedirs(out_dir, exist_ok=True)
    wav = os.path.join(out_dir, "texfi.wav")
    silent = os.path.join(out_dir, "texfi-silent.mp4")
    final = os.path.join(out_dir, "texfi-desktop.mp4")
    build_audio(wav)
    p = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
         "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "medium", "-crf", "16",
         "-pix_fmt", "yuv420p", "-movflags", "+faststart", silent], stdin=subprocess.PIPE)
    for i in range(int(DUR * FPS)):
        p.stdin.write(render_frame(i / FPS).tobytes())
        if i % 60 == 0:
            print(i, flush=True)
    p.stdin.close()
    p.wait()
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", silent, "-i", wav, "-c:v", "copy", "-c:a", "aac",
                    "-b:a", "192k", "-shortest", final], check=True)
    os.remove(silent)
    os.remove(wav)
    print(final)


if __name__ == "__main__":
    {"preview": preview, "render": render}[sys.argv[1] if len(sys.argv) > 1 else "preview"]()
