#!/usr/bin/env python3
"""Баннеры w0y: шапка для GitHub и для телеграм-канала.

Рисуются кодом из того же пиксельного пера, что и иконка приложения
(tools/make_icons.py), поэтому баннер не может разойтись с иконкой: если
символ поправят, обе картинки пересоберутся из одного места.

Фон повторяет фон приложения и сайта — точечная сетка, редкие звёзды по
фиксированному зерну и синее свечение сверху. Карточка с иконкой — та же
офсетная тень без размытия, что у PixelCard во всей экосистеме.

    python3 tools/make_banners.py           # оба баннера в docs/banners
"""
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import make_icons  # noqa: E402

BG = (10, 10, 12, 255)
CARD = (18, 18, 22, 255)
BLUE = (74, 125, 251, 255)
AMBER = (255, 184, 77, 255)
CREAM = (247, 242, 230, 255)
MUTED = (150, 150, 160, 255)
GRID_DOT = (42, 42, 50, 255)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PIXEL_FONT = os.path.join(ROOT, "app", "src", "main", "res", "font", "press_start_2p.ttf")
SANS = "/usr/share/fonts/noto/NotoSans-Regular.ttf"
SANS_BOLD = "/usr/share/fonts/noto/NotoSans-Bold.ttf"

# Звёзды и сетка — те же числа, что в Starfield.kt, только в пикселях
# баннера: шаг сетки крупнее экранного, иначе на 1280px это шум.
GRID_STEP = 34
STAR_SEED = 0x7E5F1
STAR_COUNT = 54

TEXT = {
    "en": {
        "tagline": "YouTube Music client for Android",
        "line": "no ads · no Premium limits · your own account",
        "chips": ["NO ADS", "DOWNLOADS", "120 HZ"],
        "foot": "github.com/mistqkw/texfi-w0y · AGPL-3.0",
    },
    "ru": {
        "tagline": "Клиент YouTube Music для Android",
        "line": "без рекламы · без ограничений · свой аккаунт",
        "chips": ["БЕЗ РЕКЛАМЫ", "ЗАГРУЗКИ", "120 ГЦ"],
        "foot": "github.com/mistqkw/texfi-w0y · открытый код, AGPL-3.0",
    },
}


def font(path, size):
    return ImageFont.truetype(path, size)


def glow(img, cx, cy, radius, color, peak):
    """Свечение сверху — как у героя на сайте. Рисуется кольцами, потому
    что радиального градиента в PIL нет, а размытие большого слоя на этом
    размере заметно дороже и по краю всё равно даёт ступеньку."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    steps = 90
    for i in range(steps, 0, -1):
        r = radius * i / steps
        alpha = int(peak * 255 * (1 - i / steps) ** 2)
        if alpha <= 0:
            continue
        draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=color[:3] + (alpha,))
    img.alpha_composite(layer)


def background(w, h):
    img = Image.new("RGBA", (w, h), BG)
    glow(img, w * 0.5, -h * 0.12, w * 0.85, BLUE, 0.16)

    draw = ImageDraw.Draw(img)
    y = GRID_STEP / 2
    while y < h:
        x = GRID_STEP / 2
        while x < w:
            draw.ellipse([x - 1.5, y - 1.5, x + 1.5, y + 1.5], fill=GRID_DOT)
            x += GRID_STEP
        y += GRID_STEP

    stars = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(stars)
    rnd = random.Random(STAR_SEED)
    for _ in range(STAR_COUNT):
        x, y = rnd.random() * w, rnd.random() * h
        r = 1.0 + rnd.random() * 2.2
        alpha = int((0.18 + rnd.random() * 0.4) * 255)
        color = BLUE if rnd.random() < 0.22 else (255, 255, 255, 255)
        sd.ellipse([x - r, y - r, x + r, y + r], fill=color[:3] + (alpha,))
    img.alpha_composite(stars)
    return img


def icon_card(img, x, y, size):
    """Иконка в карточке TexFi: бордер 4px и сдвинутая тень без размытия.
    Тень именно сдвинутая — Material-elevation с блюром выбивается из
    пиксельной графики сильнее, чем что-либо другое."""
    draw = ImageDraw.Draw(img)
    radius = int(size * 0.17)
    offset = max(6, size // 18)
    draw.rounded_rectangle(
        [x + offset, y + offset, x + size + offset, y + size + offset],
        radius=radius, fill=BLUE[:3] + (110,),
    )
    draw.rounded_rectangle([x, y, x + size, y + size], radius=radius,
                           fill=CARD, outline=BLUE, width=4)
    icon = make_icons.render("quill_wide", size - 16, with_bg=False,
                             symbol_scale=make_icons.LEGACY_SCALE)
    img.alpha_composite(icon, (x + 8, y + 8))


def chips(img, x, y, labels, lang):
    """Лейблы в рамке. У пиксельного Press Start 2P нет кириллицы, поэтому
    в русском варианте лейблы набраны обычным жирным в верхнем регистре —
    это единственное честное решение: битмап-шрифтом там просто нечем
    набрать, а рисовать латиницей поверх русского текста хуже."""
    draw = ImageDraw.Draw(img)
    if lang == "en":
        f = font(PIXEL_FONT, 15)
        pad_x, pad_y = 16, 13
    else:
        f = font(SANS_BOLD, 19)
        pad_x, pad_y = 16, 11
    cx = x
    for label in labels:
        box = draw.textbbox((0, 0), label, font=f)
        w = box[2] - box[0] + pad_x * 2
        h = box[3] - box[1] + pad_y * 2
        draw.rectangle([cx, y, cx + w, y + h], outline=BLUE, width=2)
        draw.text((cx + pad_x - box[0], y + pad_y - box[1]), label,
                  font=f, fill=BLUE)
        cx += w + 14
    return cx


def banner(w, h, lang):
    t = TEXT[lang]
    img = background(w, h)
    draw = ImageDraw.Draw(img)

    title = font(PIXEL_FONT, int(h * 0.082))
    tag = font(SANS_BOLD, int(h * 0.05))
    line = font(SANS, int(h * 0.035))
    foot = font(SANS, int(h * 0.028))

    gap_title = int(h * 0.062)
    gap_tag = int(h * 0.032)
    gap_line = int(h * 0.05)
    title_h = int(h * 0.082)
    chip_h = int(h * 0.062)
    block_h = (title_h + gap_title + int(h * 0.05) + gap_tag
               + int(h * 0.035) + gap_line + chip_h)

    card = int(h * 0.48)
    card_x = int(w * 0.075)
    # Карточка и текстовый блок центрируются как одно целое: по отдельности
    # один из них всегда оказывается выше другого, и баннер перекашивает.
    top = (h - max(card, block_h)) // 2
    icon_card(img, card_x, top + (max(card, block_h) - card) // 2, card)

    x = card_x + card + int(w * 0.055)
    y = top + (max(card, block_h) - block_h) // 2

    draw.text((x, y), "TexFi", font=title, fill=CREAM)
    # Пробел у Press Start 2P шире обычного: имя из двух слов без подгонки
    # разъезжается и читается как две надписи.
    tw = draw.textlength("TexFi", font=title) + title_h * 0.45
    draw.text((x + tw, y), "w0y", font=title, fill=BLUE)

    y += title_h + gap_title
    draw.text((x, y), t["tagline"], font=tag, fill=CREAM)
    y += int(h * 0.05) + gap_tag
    draw.text((x, y), t["line"], font=line, fill=MUTED)
    y += int(h * 0.035) + gap_line
    chips(img, x, y, t["chips"], lang)

    draw.text((x, h - int(h * 0.085)), t["foot"], font=foot, fill=MUTED)

    # Янтарная полоса у нижнего края: вторичный акцент w0y, тот же, что у
    # эквалайзера на сайте. Ровно одна деталь, иначе баннер рассыпается.
    draw.rectangle([0, h - 6, w, h], fill=AMBER)
    return img


def main():
    out = os.path.join(ROOT, "docs", "banners")
    os.makedirs(out, exist_ok=True)
    # 1280×640 — размер, который GitHub просит для social preview.
    banner(1280, 640, "en").convert("RGB").save(
        os.path.join(out, "github.png"), optimize=True)
    # 16:9 — так телеграм показывает картинку в посте, не обрезая.
    banner(1280, 720, "ru").convert("RGB").save(
        os.path.join(out, "telegram.png"), optimize=True)
    print("docs/banners/github.png, docs/banners/telegram.png")


if __name__ == "__main__":
    main()
