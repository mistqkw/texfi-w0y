#!/usr/bin/env python3
"""Ролик w0y для TikTok: 1080×1920, 60 fps, 15 с, со своим звуком.

Всё рисуется кодом из тех же чисел, что иконка и баннер (make_icons.py,
make_banners.py): пиксельное перо, синий #4a7dfb, кремовый, янтарь,
Press Start 2P. Монтаж идёт по долям 120 BPM (доля = 0.5 с), звук
синтезируется здесь же и совпадает с ударами картинки.

    python3 tools/make_promo.py preview     # контактный лист кадров
    python3 tools/make_promo.py render      # docs/promo/w0y-tiktok.mp4
"""
import math
import os
import struct
import subprocess
import sys
import wave

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
import make_icons  # noqa: E402

W, H, FPS = 1080, 1920, 60
DUR = 16.0
BEAT = 0.5

BG = (10, 10, 12)
CARD = (18, 18, 22)
BLUE = (74, 125, 251)
AMBER = (255, 184, 77)
CREAM = (247, 242, 230)
MUTED = (150, 150, 160)
DIM = (60, 60, 72)
GRID_DOT = (42, 42, 50)
GREEN = (78, 217, 138)

PIXEL_FONT = os.path.join(ROOT, "app", "src", "main", "res", "font", "press_start_2p.ttf")
SANS_BOLD = "/usr/share/fonts/noto/NotoSans-Bold.ttf"

_fonts = {}


def pf(size):
    key = ("p", size)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(PIXEL_FONT, size)
    return _fonts[key]


def sf(size):
    key = ("s", size)
    if key not in _fonts:
        _fonts[key] = ImageFont.truetype(SANS_BOLD, size)
    return _fonts[key]


# ---------------------------------------------------------------- easing

def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))


def prog(t, t0, d):
    return clamp((t - t0) / d)


def out_cubic(p):
    return 1 - (1 - p) ** 3


def out_back(p, s=1.70158):
    p -= 1
    return 1 + (s + 1) * p ** 3 + s * p * p


def steps(p, n):
    return 1.0 if p >= 1 else math.floor(p * n) / n


def lerp(a, b, p):
    return a + (b - a) * p


def mix(c1, c2, p):
    return tuple(int(round(lerp(a, b, p))) for a, b in zip(c1, c2))


def h01(a, b=0, c=0):
    n = (a * 374761393 + b * 668265263 + c * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65536.0


# ---------------------------------------------------------------- canvas

class Canvas:
    def __init__(self, img, ox=0, oy=0):
        self.im = img
        self.d = ImageDraw.Draw(img)
        self.ox = ox
        self.oy = oy

    def rect(self, x0, y0, x1, y1, fill):
        a, b = round(x0 + self.ox), round(y0 + self.oy)
        c, d = round(x1 + self.ox) - 1, round(y1 + self.oy) - 1
        if c >= a and d >= b:
            self.d.rectangle([a, b, c, d], fill=fill)

    def frame(self, x0, y0, x1, y1, color, w):
        self.rect(x0, y0, x1, y0 + w, color)
        self.rect(x0, y1 - w, x1, y1, color)
        self.rect(x0, y0, x0 + w, y1, color)
        self.rect(x1 - w, y0, x1, y1, color)

    def card(self, x0, y0, x1, y1, shadow=12, border=4, fill=CARD, bc=BLUE):
        self.rect(x0 + shadow, y0 + shadow, x1 + shadow, y1 + shadow, bc)
        self.rect(x0, y0, x1, y1, fill)
        self.frame(x0, y0, x1, y1, bc, border)

    def rrect(self, x0, y0, x1, y1, r, fill=None, outline=None, width=0):
        if round(x1) - round(x0) < 3 or round(y1) - round(y0) < 3:
            return
        self.d.rounded_rectangle(
            [round(x0 + self.ox), round(y0 + self.oy), round(x1 + self.ox) - 1, round(y1 + self.oy) - 1],
            radius=r, fill=fill, outline=outline, width=width)

    def text(self, s, x, y, font, fill, anchor="lm"):
        self.d.text((round(x + self.ox), round(y + self.oy)), s, font=font, fill=fill, anchor=anchor)

    def stext(self, s, x, y, font, fill, shadow=BLUE, off=6, anchor="lm"):
        self.text(s, x + off, y + off, font, shadow, anchor)
        self.text(s, x, y, font, fill, anchor)


# ------------------------------------------------------------ background

STARS = [(h01(i, 1), h01(i, 2), 2 + int(h01(i, 3) * 3), h01(i, 4) * 6.28) for i in range(54)]


def backdrop(c, t):
    ph = t % BEAT
    pulse = math.exp(-ph * 10) if t >= 1.5 else 0.0
    dot = mix(GRID_DOT, (56, 88, 170), pulse * 0.9)
    step = 54
    scroll = int(t * 8) % step
    for gx in range(0, W + step, step):
        for gy in range(-step, H + step, step):
            y = gy + scroll
            c.rect(gx, y, gx + 4, y + 4, dot)
    for sx, sy, sz, sp in STARS:
        tw = 0.5 + 0.5 * math.sin(t * 1.6 + sp)
        col = mix(BG, CREAM, 0.12 + 0.3 * tw)
        c.rect(sx * W, sy * H, sx * W + sz, sy * H + sz, col)


# ------------------------------------------------------------------ logo

CELLS = make_icons.quill_wide().cells


def draw_logo(c, cx, cy, size, t, t0, k=1.0):
    """Перо собирается из ячеек, потом вокруг вырастает плитка с жёсткой тенью."""
    cell = max(1, round(size * 0.78 / 24))
    sym = cell * 24
    ox0, oy0 = cx - sym / 2, cy - sym / 2
    scale = 0.35 + 0.65 * size / 680
    tile_t = t0 + 1.3 * k
    p = prog(t, tile_t, 0.3 * k)
    if p > 0:
        e = out_back(steps(p, 8))
        half = size / 2 * e
        sh = size * 0.06 * clamp(e)
        c.rrect(cx - half + sh, cy - half + sh, cx + half + sh, cy + half + sh, int(size * 0.22 * e), fill=BLUE)
        c.rrect(cx - half, cy - half, cx + half, cy + half, int(size * 0.22 * e), fill=CARD,
                outline=BLUE, width=max(2, int(size * 0.014)))
    for (x, y), color in CELLS.items():
        hx, hy = ox0 + x * cell, oy0 + y * cell
        delay = (h01(x, y) * 0.5 if color == BLUE else 0.3 + h01(x, y) * 0.5) * k
        q = prog(t, t0 + delay, 0.42 * k)
        if q <= 0:
            continue
        if q >= 1:
            c.rect(hx, hy, hx + cell + 1, hy + cell + 1, color)
            continue
        e = out_cubic(steps(q, 10))
        ang = h01(x, y, 1) * 6.283
        dist = (500 + h01(x, y, 2) * 700) * scale
        px = lerp(hx + math.cos(ang) * dist, hx, e)
        py = lerp(hy + math.sin(ang) * dist, hy, e)
        side = cell * (0.35 + 0.65 * e)
        off = (cell - side) / 2
        c.rect(px + off, py + off, px + off + side, py + off + side, color)


# ---------------------------------------------------------- helpers

INK = BG
LIGHT_DOT = (224, 218, 203)


def flat_bg(c, color):
    c.rect(-40, -40, W + 40, H + 40, color)
    dot = mix(color, (0, 0, 0), 0.12) if color != CREAM else LIGHT_DOT
    scroll = int(0 * 8)
    for gx in range(0, W + 54, 54):
        for gy in range(-54, H + 54, 54):
            c.rect(gx, gy + scroll, gx + 4, gy + scroll + 4, dot)


def typed(txt, t, start, per=0.06):
    n = int(max(0, t - start) / per) + (1 if t >= start else 0)
    return txt[:n]


def big(c, txt, cx, cy, size, fill, shadow=None, off=8):
    if shadow is not None:
        c.text(txt, cx + off, cy + off, pf(size), shadow, "mm")
    c.text(txt, cx, cy, pf(size), fill, "mm")


def slam(s, st, dur=0.22, dist=260):
    p = prog(s, st, dur)
    if p <= 0:
        return dist if st <= 0 else None
    return (1 - out_back(steps(p, 6))) * dist


MAG = ["..XXXX..", ".X....X.", "X......X", "X......X", ".X....X.", "..XXXXX.", "......XX", ".......XX"]
NOTE = ["..XXXXX", "..XXXXX", "..X...X", "..X...X", "..X...X", "XXX.XXX", "XXX.XXX"]


def glyph(c, rows, x, y, cell, color):
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "X":
                c.rect(x + i * cell, y + j * cell, x + (i + 1) * cell + 1, y + (j + 1) * cell + 1, color)


# ---------------------------------------------------------------- scenes

def scene_intro(c, t):
    draw_logo(c, 540, 720, 680, t, 0.2, 1.0)
    title_row(c, t, 2.0, 2.5, 1230)
    p = prog(t, 2.6, 0.2)
    if p > 0:
        e = steps(p, 5)
        c.text("YouTube Music client for Android", 540, 1340 + (1 - e) * 30, sf(46), mix(BG, CREAM, e), "mm")


def title_row(c, t, t_tex, t_w0y, y, size=96):
    x = 540 - 9 * size / 2
    c.stext(typed("TexFi", t, t_tex), x, y, pf(size), CREAM, shadow=BLUE, off=6)
    if t >= t_w0y:
        gx = x + 6 * size
        word = typed("w0y", t, t_w0y)
        dt = t - t_w0y
        amp = 14 * max(0.0, 1 - dt / 0.3) * (1 if int(t * 30) % 2 == 0 else -1)
        if amp:
            c.text(word, gx - amp, y, pf(size), CREAM)
            c.text(word, gx + amp, y, pf(size), AMBER)
        c.text(word, gx, y, pf(size), BLUE)


def split_text(c, txt, cx, cy, size, pitch, fill, shadow=None, s=0.0, stagger=0.06, dist=140):
    x0 = cx - (len(txt) * pitch) / 2 + pitch / 2
    for i, ch in enumerate(txt):
        if ch == " ":
            continue
        d = slam(s, stagger * i, 0.16, dist)
        if d is None:
            continue
        if shadow is not None:
            c.text(ch, x0 + i * pitch + 8, cy + d + 8, pf(size), shadow, "mm")
        c.text(ch, x0 + i * pitch, cy + d, pf(size), fill, "mm")


def rgb_split(c, txt, cx, cy, size, s, base):
    f = int(s * 30)
    amp = 10 * (1 if f % 2 == 0 else -1) * (0.4 + 0.6 * abs(math.sin(s * 40)))
    c.text(txt, cx - amp, cy, pf(size), BLUE, "mm")
    c.text(txt, cx + amp, cy, pf(size), AMBER, "mm")
    c.text(txt, cx, cy, pf(size), base, "mm")


def scene_new(c, s, img):
    # 0-0.75 NEW / 0.75-1.5 DESIGN / 1.5-2.5 stacked
    if s < 0.5:
        flat_bg(c, BLUE)
        big(c, "NEW", 540, 900 + (slam(s, 0.0, 0.2, 500) or 0), 296, CREAM, INK, 12)
    elif s < 1.0:
        flat_bg(c, CREAM)
        u = s - 0.5
        big(c, "DESIGN", 540, 900 + (slam(u, 0.0, 0.2, 500) or 0), 152, INK, BLUE, 10)
    else:
        backdrop(c, s)
        u = s - 1.0
        n = slam(u, 0.0, 0.18, 400) or 0
        rgb_split(c, "NEW", 540, 740 + n, 296, u, CREAM)
        d = slam(u, 0.15, 0.18, 400)
        if d is not None:
            rgb_split(c, "DESIGN", 540, 1010 + d, 152, u, CREAM)
        if u > 0.5:
            c.text(typed("nothing like it.", u, 0.5, 0.04), 540, 1200, sf(52), MUTED, "mm")


NEVER = [("NEVER", 176, AMBER, INK, CREAM), ("SEEN", 240, CREAM, INK, BLUE), ("BEFORE.", 136, BLUE, CREAM, INK)]


def scene_never(c, s):
    i = min(2, int(s / 0.5))
    if i < 2 or s < 1.5:
        word, size, bg, fg, sh = NEVER[min(i, 2)]
        flat_bg(c, bg)
        local = s - 0.5 * i
        sz = size + 20 if local < 0.05 else size
        big(c, word, 540, 900, sz, fg, sh, 10)
        for k in range(3):
            c.rect(440 + k * 80, 1120, 440 + k * 80 + 48, 1136, fg if k == i else mix(bg, fg, 0.3))
    else:
        backdrop(c, s)
        u = s - 1.5
        for j, (word, size, bg, fg, sh) in enumerate(NEVER):
            y = 650 + j * 260
            col = [AMBER, CREAM, BLUE][j]
            split_text(c, word, 540, y, [176, 216, 136][j], [176, 216, 136][j], col, None, u, 0.0, 200)


def scene_ads(c, s, img):
    c.text("YOUR MUSIC", 540, 600, pf(56), CREAM, "mm")
    c.text("INTERRUPTED?", 540, 680, pf(56), CREAM, "mm")
    cy = 950
    if s < 0.5:
        d = slam(s, 0.0, 0.18, 400)
        amp = (12 if int(s * 30) % 2 == 0 else -12) * (0.4 + 0.6 * abs(math.sin(s * 70)))
        y = cy + d
        big(c, "ADS", 540 - amp, y, 296, BLUE)
        big(c, "ADS", 540 + amp, y, 296, CREAM)
        big(c, "ADS", 540, y, 296, AMBER)
    else:
        q = prog(s, 0.5, 0.4)
        bx0, by0, bw, bh = 20, 780, 1040, 330
        layer = Image.new("RGBA", (bw, bh), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        ld.text((520, 170), "ADS", font=pf(296), fill=AMBER, anchor="mm")
        blk = 26
        for gx in range(0, bw, blk):
            for gy in range(0, bh, blk):
                if h01(gx // blk, gy // blk, 3) < q:
                    ld.rectangle([gx, gy, gx + blk - 1, gy + blk - 1], fill=(0, 0, 0, 0))
        img.paste(layer, (bx0 + c.ox, by0 + c.oy), layer)
    d = slam(s, 0.55, 0.2, 300)
    if d is not None:
        big(c, "NONE.", 540, 1250 + d, 144, CREAM, BLUE)


def scene_tap(c, s):
    d = slam(s, 0.0, 0.2, 400)
    big(c, "TAP.", 540, 560 + d, 200, CREAM, INK, 10)
    t0 = 0.35
    if s >= t0:
        for i in range(5):
            st = t0 + i * 0.06
            if s < st:
                continue
            side = 120 + steps(prog(s, st, 0.5), 10) * 1900
            if side < 1800:
                c.frame(540 - side / 2, 1000 - side / 2, 540 + side / 2, 1000 + side / 2, CREAM if i % 2 == 0 else INK, 14)
        for i in range(15):
            amp = math.exp(-(s - t0) * 2.4) * (0.35 + 0.65 * abs(math.sin(i * 1.7 + s * 34)))
            h = max(12, int(340 * amp) // 12 * 12)
            x = 60 + i * 64
            c.rect(x, 1000 - h / 2, x + 40, 1000 + h / 2, INK)
            c.rect(x, 1000 - h / 2, x + 40, 1000 - h / 2 + 12, CREAM)
        d2 = slam(s, t0, 0.2, 300)
        big(c, "PLAYING.", 540, 1430 + d2, 120, INK, CREAM, 8)


def scene_speed(c, s):
    flip = 0.75
    c.text("PER TRACK", 540, 1400, pf(48), MUTED, "mm")
    if s < flip:
        word, size, pitch = "SLOWED", 128, 158
        x0 = 540 - (len(word) * pitch) / 2 + pitch / 2
        for i, ch in enumerate(word):
            st = 0.02 + 0.1 * i
            d = slam(s, st, 0.16, 120)
            if d is not None:
                c.text(ch, x0 + i * pitch, 800 + d, pf(size), BLUE, "mm")
        for i in range(12):
            h = 40 + int(60 * (0.5 + 0.5 * math.sin(s * 5 + i * 0.6))) // 6 * 6
            c.rect(150 + i * 66, 1080 - h / 2, 150 + i * 66 + 40, 1080 + h / 2, DIM)
        c.text("x0.8", 540, 1250, pf(64), BLUE, "mm")
    else:
        u = s - flip
        word, size, pitch = "SPED UP", 120, 128
        x0 = 540 - (len(word) * pitch) / 2 + pitch / 2
        f = int(u * 60)
        jx = round((h01(f, 1) - 0.5) * 14)
        for i, ch in enumerate(word):
            if u >= 0.025 * i:
                c.text(ch, x0 + i * pitch + jx, 800, pf(size), AMBER, "mm")
        for i in range(16):
            y = 640 + int(h01(i, 5) * 640) // 8 * 8
            length = 160 + int(h01(i, 6) * 360)
            x = (W + 200) - ((u * (5200 + h01(i, 7) * 3000) + h01(i, 8) * 1400) % (W + 700))
            c.rect(x, y, x + length, y + 8, mix(BG, AMBER, 0.55))
        c.text("x1.25", 540, 1250, pf(64), AMBER, "mm")


def scene_dl(c, s, img):
    big(c, "DOWNLOADS", 540, 560, 88, INK)
    p = steps(prog(s, 0.1, 1.0), 50)
    n = int(p * 100 + 0.001)
    label = "%d%%" % n
    cy = 950
    base = (208, 201, 184)
    c.text(label, 540, cy, pf(240), base, "mm")
    if p > 0:
        rw, rh = 1000, 300
        mask = Image.new("L", (rw, rh), 0)
        ImageDraw.Draw(mask).text((500, 150), label, font=pf(240), fill=255, anchor="mm")
        top = int(300 - (150 + 120)) if False else 0
        fill_y = 270 - int(p * 240)
        ImageDraw.Draw(mask).rectangle([0, 0, rw, fill_y - 1], fill=0)
        img.paste(Image.new("RGB", (rw, rh), BLUE), (40 + c.ox, cy - 150 + c.oy), mask)
    if s >= 1.1:
        d = slam(s, 1.1, 0.2, 260)
        big(c, "DONE.", 540, 1250 + d, 128, INK, BLUE)
        glyph(c, NOTE, 540 - 31.5, 1400, 9, BLUE)


def scene_sections(c, s):
    d = slam(s, 0.0, 0.2, 400)
    big(c, "11", 540, 560 + d, 296, AMBER, BLUE, 10)
    c.text(typed("SECTIONS", s, 0.1, 0.05), 540, 770, pf(72), CREAM, "mm")
    x0, y0, tile, gap = 104, 880, 200, 24
    for i in range(12):
        st = 0.3 + 0.07 * i
        if s < st:
            continue
        x = x0 + (i % 4) * (tile + gap)
        y = y0 + (i // 4) * (tile + gap)
        e = steps(prog(s, st, 0.1), 3)
        hot = s - st < 0.04
        if i == 11:
            c.rect(x, y, x + tile, y + tile, AMBER)
            glyph(c, MAG, x + 44, y + 26, 14, INK)
            c.text("SEARCH", x + tile / 2, y + 168, pf(24), INK, "mm")
            continue
        c.rect(x, y, x + tile, y + tile, CARD)
        c.frame(x, y, x + tile, y + tile, CREAM if hot else (BLUE if e >= 0.5 else DIM), 4)
        sx, sy = x + 40, y + 70
        c.rect(sx, sy, sx + 120, sy + 60, BLUE if e >= 0.5 else CARD)
        c.frame(sx, sy, sx + 120, sy + 60, BLUE if e >= 0.5 else DIM, 4)
        kx = lerp(sx + 8, sx + 120 - 8 - 36, e)
        c.rect(kx, sy + 12, kx + 36, sy + 48, CREAM if e >= 0.5 else MUTED)


def scene_hz(c, s):
    n = int(out_cubic(prog(s, 0.05, 0.45)) * 120)
    d = slam(s, 0.0, 0.15, 300)
    big(c, "%d" % n, 540, 800 + d, 296, INK, CREAM, 10)
    if s >= 0.45:
        big(c, "HZ", 540, 1060, 144, INK, CREAM, 8)
    f = int(s * 60)
    for i in range(24):
        lit = (f - i) % 24
        col = INK if lit == 0 else (mix(AMBER, INK, 0.35) if lit < 4 else mix(AMBER, INK, 0.1))
        c.rect(60 + i * 40, 1300, 60 + i * 40 + 32, 1332, col)


def scene_yours(c, s):
    cols, rows, cw, ch = 6, 8, 150, 120
    x0, y0 = 540 - cols * cw / 2, 520
    for r in range(rows):
        for k in range(cols):
            phase = (k + r * 0.7) * 0.07
            on = int((s - phase) * 9) % 2 == 1 if s > phase else False
            x, y = x0 + k * cw + 20, y0 + r * ch + 25
            c.rect(x, y, x + 110, y + 70, INK if on else mix(INK, CREAM, 0.12))
            kx = x + (58 if on else 6)
            c.rect(kx, y + 6, kx + 46, y + 64, CREAM if on else MUTED)
    d = slam(s, 0.0, 0.18, 400)
    big(c, "YOURS.", 540, 330 + d, 176, INK, CREAM, 10)
    if s > 0.3:
        c.text(typed("every setting. your call.", s, 0.3, 0.02), 540, 1530, sf(48), INK, "mm")


CLAIMS = [("FAST.", "tracks start instantly", AMBER, INK, CREAM),
          ("SMOOTH.", "an interface that flows", CREAM, INK, BLUE),
          ("YOURS.", "settings for everything", BLUE, CREAM, INK)]


def scene_claims(c, s):
    i = min(2, int(s / 0.85))
    word, cap, bg, fg, sh = CLAIMS[i]
    flat_bg(c, bg)
    local = s - 0.85 * i
    size = {0: 176, 1: 136, 2: 160}[i]
    split_text(c, word, 540, 860, size, size, fg, sh, local, 0.05, 160)
    if local > 0.3:
        c.text(typed(cap, local, 0.3, 0.025), 540, 1080, sf(52), fg, "mm")
    for k in range(3):
        c.rect(440 + k * 80, 1300, 440 + k * 80 + 48, 1316, fg if k == i else mix(bg, fg, 0.3))


O = 11.7


def scene_outro(c, t):
    draw_logo(c, 540, 640, 620, t, O, 0.55)
    title_row(c, t, O + 0.75, O + 1.05, 1080)
    p = prog(t, O + 1.25, 0.25)
    if p > 0:
        e = steps(p, 5)
        c.text("YouTube Music client for Android", 540, 1180 + (1 - e) * 30, sf(44), mix(BG, CREAM, e), "mm")
    if t >= O + 0.9:
        p = steps(prog(t, O + 0.9, 0.15), 4)
        w = 260 * p
        c.rect(540 - w / 2, 150, 540 + w / 2, 240, AMBER)
        if p >= 1:
            c.text("BETA", 540, 195, pf(56), INK, "mm")
    if t >= O + 1.2:
        c.text(typed("STABLE VERSION: COMING SOON", t, O + 1.2, 0.03), 540, 285, pf(32), CREAM, "mm")
    if t >= O + 1.6:
        lines = ["NEW DESIGN.", "NEVER SEEN BEFORE."]
        for i, ln in enumerate(lines):
            c.text(typed(ln, t, O + 1.6 + 0.5 * i, 0.04), 540, 1270 + i * 62, pf(32), AMBER if i == 0 else CREAM, "mm")
    if t >= O + 2.0:
        url = "texfi-hub.vercel.app/en"
        w = 700
        p = steps(prog(t, O + 2.0, 0.2), 5)
        c.rect(540 - w / 2 * p, 1395, 540 + w / 2 * p, 1465, BLUE)
        if p >= 1:
            c.text(typed(url, t, O + 2.2, 0.02), 540, 1430, sf(44), CREAM, "mm")
    c.text(typed("animation create by ai", t, O + 2.2, 0.03), 540, 1530, pf(32), MUTED, "mm")


SCENES = [(0.0, 1.2, "ads", BG), (1.2, 2.7, "new", None), (2.7, 4.2, "never", None), (4.2, 5.2, "tap", BLUE),
          (5.2, 6.7, "speed", BG), (6.7, 8.2, "dl", CREAM), (8.2, 9.7, "sections", BG), (9.7, 10.7, "hz", AMBER),
          (10.7, 11.7, "yours", CREAM)]
SHAKES = [(0.0, 18), (0.6, 22), (1.2, 20), (1.7, 12), (2.2, 16), (2.7, 12), (3.2, 9), (3.7, 9), (4.2, 20),
          (5.2, 9), (5.95, 9), (6.7, 9), (8.2, 9), (9.7, 9), (10.7, 9), (11.7, 16), (12.4, 10), (12.75, 8)]
FLASHES = [(0.6, 0.3), (1.2, 0.3), (11.7, 0.25), (12.4, 0.3)]


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
    name = None
    for a, b, nm, colr in SCENES:
        if a <= t < b:
            name, s, colr_ = nm, t - a, colr
    if name is None:
        backdrop(c, t)
        scene_outro(c, t)
    else:
        if colr_ == BG:
            backdrop(c, t)
        elif colr_ is not None:
            flat_bg(c, colr_)
        if name == "new":
            scene_new(c, s, img)
        elif name == "never":
            scene_never(c, s)
        elif name == "ads":
            scene_ads(c, s + 0.12, img)
        elif name == "tap":
            scene_tap(c, s)
        elif name == "speed":
            scene_speed(c, s)
        elif name == "dl":
            scene_dl(c, s, img)
        elif name == "sections":
            scene_sections(c, s)
        elif name == "hz":
            scene_hz(c, s)
        else:
            scene_yours(c, s)
    for tf, amp in FLASHES:
        dt = t - tf
        if 0 <= dt < 0.25:
            img = Image.blend(img, Image.new("RGB", (W, H), CREAM), amp * math.exp(-dt * 18))
    return img


TRANS = [1.2, 4.2, 6.7, 9.7, 11.7]
BIG_HITS = [te for te, amp in SHAKES if amp >= 16]


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
    for th in BIG_HITS:
        dt = t - th
        if 0 <= dt < 0.12:
            f = int(dt * 60)
            for k in range(5):
                y = int(h01(f, k, 4) * (H - 160))
                hh = 40 + int(h01(f, k, 5) * 120)
                dx = round((h01(f, k, 6) - 0.5) * 120)
                band = img.crop((0, y, W, y + hh))
                img.paste(band, (dx, y))
    z = 0.035 * math.exp(-(t % BEAT) * 16) if 0.0 <= t < 13.7 else 0.0
    if z > 0.004:
        cw, ch = W / (1 + z), H / (1 + z)
        l, tp = (W - cw) / 2, (H - ch) / 2
        img = img.resize((W, H), Image.BILINEAR, box=(l, tp, l + cw, tp + ch))
    if t > DUR - 0.5:
        img = Image.blend(img, Image.new("RGB", (W, H), BG), clamp((t - (DUR - 0.5)) / 0.5))
    return img


# ----------------------------------------------------------------- audio

SR = 44100


class Track:
    def __init__(self, seconds):
        self.buf = [0.0] * int(seconds * SR)

    def put(self, t0, samples, gain=1.0):
        i = int(t0 * SR)
        n = min(len(samples), len(self.buf) - i)
        buf = self.buf
        for k in range(n):
            buf[i + k] += samples[k] * gain


def _noise_seed():
    state = [123456789]

    def rnd():
        state[0] = (state[0] * 1103515245 + 12345) & 0x7FFFFFFF
        return state[0] / 0x3FFFFFFF - 1.0
    return rnd


RND = _noise_seed()


def kick(dur=0.32):
    out, ph = [], 0.0
    for k in range(int(dur * SR)):
        t = k / SR
        ph += 2 * math.pi * (45 + 100 * math.exp(-t * 30)) / SR
        out.append(math.sin(ph) * math.exp(-t * 9))
    return out


def hat(dur=0.05):
    out, prev = [], 0.0
    for k in range(int(dur * SR)):
        n = RND()
        out.append((n - prev) * math.exp(-k / SR * 90))
        prev = n
    return out


def clap(dur=0.2):
    out, prev = [], 0.0
    for k in range(int(dur * SR)):
        t = k / SR
        n = RND()
        env = math.exp(-t * 24) + 0.6 * math.exp(-((t - 0.012) * 300) ** 2) + 0.5 * math.exp(-((t - 0.026) * 300) ** 2)
        out.append((n - 0.6 * prev) * env * 0.7)
        prev = n
    return out


def crash(dur=0.9, decay=4.5):
    out, prev = [], 0.0
    for k in range(int(dur * SR)):
        n = RND()
        out.append((n - prev) * math.exp(-k / SR * decay))
        prev = n
    return out


def riser(dur):
    out, ph = [], 0.0
    n_all = int(dur * SR)
    for k in range(n_all):
        p = k / n_all
        ph += 2 * math.pi * (180 + 1400 * p * p) / SR
        out.append((math.sin(ph) * 0.5 + RND() * 0.35) * p * p)
    return out


def sq(freq, dur, duty=0.5, decay=8.0):
    out = []
    for k in range(int(dur * SR)):
        t = k / SR
        x = 1.0 if (freq * t) % 1.0 < duty else -1.0
        env = min(1.0, t / 0.004) * math.exp(-t * decay)
        out.append(x * env)
    return out


def midi(n):
    return 440.0 * 2 ** ((n - 69) / 12)


def build_audio(path):
    tr = Track(DUR + 0.8)
    beat = BEAT
    total_beats = int(13.7 / beat)
    chords = [[57, 60, 64, 69], [53, 57, 60, 65], [60, 64, 67, 72], [55, 59, 62, 67]]
    bass = [45, 41, 48, 43]
    k_wave, h_wave, c_wave = kick(), hat(), clap()

    tr.put(0.0, crash(0.6, 6.0), 0.5)
    tr.put(0.6, crash(0.35, 9.0), 0.45)
    tr.put(1.2, crash(1.0, 3.5), 0.5)
    tr.put(1.2, kick(0.5), 1.0)
    tr.put(11.2, riser(0.5), 0.35)
    tr.put(11.7, crash(1.0, 3.5), 0.5)
    tr.put(11.7, kick(0.5), 1.0)

    for b in range(total_beats):
        t = b * beat
        tr.put(t, k_wave, 0.9)
        if True:
            tr.put(t + beat / 2, h_wave, 0.28)
        if t >= 1.2 and b % 2 == 1:
            tr.put(t, c_wave, 0.5)
        if t >= 1.2:
            chord_i = int((t - 1.2) / 2.0) % 4
            root = bass[chord_i]
            tr.put(t, sq(midi(root), beat * 0.9, 0.5, 5.0), 0.16)
            tr.put(t + beat / 2, sq(midi(root), beat * 0.4, 0.5, 9.0), 0.12)
            notes = chords[chord_i]
            for s16 in range(4):
                ts = t + s16 * beat / 4
                nn = notes[[0, 1, 2, 3, 2, 1][(b * 4 + s16) % 6]]
                tr.put(ts, sq(midi(nn), 0.16, 0.25, 14.0), 0.10)
                if t >= 4.2:
                    tr.put(ts + 0.03, sq(midi(nn + 12), 0.12, 0.125, 18.0), 0.045)

    for te, _ in SHAKES:
        tr.put(te, sq(880, 0.12, 0.5, 26.0), 0.16)
    tr.put(12.75, sq(midi(81), 0.5, 0.25, 5.0), 0.12)
    tr.put(12.4, sq(midi(69), 1.6, 0.25, 2.0), 0.12)
    tr.put(12.4, sq(midi(72), 1.6, 0.25, 2.0), 0.10)
    tr.put(12.4, sq(midi(76), 1.6, 0.25, 2.0), 0.10)

    out = bytearray()
    n = len(tr.buf)
    for i, x in enumerate(tr.buf):
        t = i / SR
        fade = clamp((DUR + 0.5 - t) / 0.9)
        y = math.tanh(x * 0.9) * 0.9 * fade
        v = int(y * 32767)
        out += struct.pack("<hh", v, v)
    with wave.open(path, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(bytes(out))


# ------------------------------------------------------------------ main

def preview():
    times = [0.5, 1.0, 1.45, 1.7, 2.6, 3.0, 3.9, 4.2, 4.6, 5.0, 5.9, 6.5,
             7.6, 8.2, 9.8, 10.2, 12.4, 11.7, 12.5, 12.9, 13.3, 13.9, 14.6, 14.95]
    tw, th = 270, 480
    cols = 8
    rows = math.ceil(len(times) / cols)
    sheet = Image.new("RGB", (tw * cols, th * rows), (0, 0, 0))
    for i, t in enumerate(times):
        sheet.paste(render_frame(t).resize((tw, th), Image.LANCZOS), ((i % cols) * tw, (i // cols) * th))
    out = os.environ.get("PROMO_SHEET", os.path.join(ROOT, "docs", "promo", "sheet.png"))
    os.makedirs(os.path.dirname(out), exist_ok=True)
    sheet.save(out)
    print(out)


def render():
    out_dir = os.path.join(ROOT, "docs", "promo")
    os.makedirs(out_dir, exist_ok=True)
    wav = os.path.join(out_dir, "w0y-tiktok.wav")
    silent = os.path.join(out_dir, "w0y-silent.mp4")
    final = os.path.join(out_dir, "w0y-tiktok.mp4")
    print("audio...", flush=True)
    build_audio(wav)
    print("video...", flush=True)
    p = subprocess.Popen(
        ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
         "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "medium", "-crf", "15",
         "-pix_fmt", "yuv420p", "-movflags", "+faststart", silent],
        stdin=subprocess.PIPE)
    frames = int(DUR * FPS)
    for i in range(frames):
        p.stdin.write(render_frame(i / FPS).tobytes())
        if i % 60 == 0:
            print(i, "/", frames, flush=True)
    p.stdin.close()
    p.wait()
    music = os.environ.get("PROMO_MUSIC")
    if music:
        fade = f"afade=t=out:st={DUR - 1.2}:d=1.2"
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", silent, "-i", music, "-t", str(DUR),
                        "-filter_complex", f"[1:a]{fade},volume=0.9[a]", "-map", "0:v", "-map", "[a]",
                        "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", final], check=True)
    else:
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", silent, "-i", wav, "-c:v", "copy",
                        "-c:a", "aac", "-b:a", "192k", "-shortest", final], check=True)
    os.remove(silent)
    os.remove(wav)
    print(final)


if __name__ == "__main__":
    {"preview": preview, "render": render}[sys.argv[1] if len(sys.argv) > 1 else "preview"]()
