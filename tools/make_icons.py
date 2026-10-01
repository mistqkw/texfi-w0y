#!/usr/bin/env python3
"""Иконка w0y: плоский пиксель-арт из кода.

Сетка 24×24 делит все плотности Android нацело (48/72/96/144/192 →
2/3/4/6/8 px на ячейку), поэтому символ остаётся пиксель в пиксель чётким
на любом экране.

Стиль жёстко ограничен: два тона акцента плюс янтарная деталь, плоская
заливка, твёрдая теневая грань справа-снизу. Никаких градиентов, свечений
и псевдо-объёма — на 48px они превращаются в кашу.

    python3 tools/make_icons.py preview          # лист в docs/icon-concepts
    python3 tools/make_icons.py apply <concept>  # все форматы в res/
"""
import os
import sys

from PIL import Image, ImageDraw

GRID = 24

# Палитра семьи TexFi, снятая с иконок f0kus/m0ney/files: синий — контур,
# кремовый — заливка, фон почти чёрный. Ровно два цвета, без теней
# и градиентов: у всей линейки так, и на 48px это единственное, что живёт.
BG = (13, 13, 15, 255)
BLUE = (74, 125, 251, 255)
CREAM = (247, 242, 230, 255)
WHITE = (255, 255, 255, 255)

# Символ занимает столько же холста, сколько в f0kus и m0ney (замерено по
# их ассетам: 0.56×0.66 у обычной иконки, 0.34×0.40 у адаптивного слоя).
# В ряду на домашнем экране разный «вес» иконок ломает ощущение семьи
# сильнее, чем разная форма.
LEGACY_SCALE = 0.78
ADAPTIVE_SCALE = 0.48


class Art:
    """Силуэт по сетке. Контур и заливка расставляются автоматически:
    ячейка на краю формы — синяя, внутренняя — кремовая. Так рисунок
    невозможно случайно «размазать» тенями, а толщина контура везде одна.
    """

    def __init__(self, n=GRID):
        self.n = n
        self.mask = [[False] * n for _ in range(n)]
        self.overrides = {}

    def span(self, y, x0, x1):
        for x in range(x0, x1 + 1):
            if 0 <= x < self.n and 0 <= y < self.n:
                self.mask[y][x] = True

    def block(self, x0, y0, x1, y1):
        for y in range(y0, y1 + 1):
            self.span(y, x0, x1)

    def erase(self, x0, y0, x1, y1):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if 0 <= x < self.n and 0 <= y < self.n:
                    self.mask[y][x] = False

    def paint(self, x0, y0, x1, y1, color):
        """Ячейки поверх автоматической раскраски: катушки внутри окна,
        блик на корпусе. Без этого деталь внутри выреза нарисовать нечем."""
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                if 0 <= x < self.n and 0 <= y < self.n:
                    self.overrides[(x, y)] = color

    def disc(self, cx, cy, r, color):
        for y in range(self.n):
            for x in range(self.n):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    self.overrides[(x, y)] = color

    def polygon(self, points):
        """Заливка по вершинам: у молнии важен точный излом, и подгонять
        его построчно — это и есть та кривизна, из-за которой форма
        превращается в варежку."""
        n = len(points)
        for y in range(self.n):
            for x in range(self.n):
                px, py = x + 0.5, y + 0.5
                inside = False
                j = n - 1
                for i in range(n):
                    xi, yi = points[i]
                    xj, yj = points[j]
                    if (yi > py) != (yj > py):
                        xint = xi + (py - yi) * (xj - xi) / (yj - yi)
                        if px < xint:
                            inside = not inside
                    j = i
                if inside:
                    self.mask[y][x] = True

    def filled(self, x, y):
        return 0 <= x < self.n and 0 <= y < self.n and self.mask[y][x]

    @property
    def cells(self):
        out = {}
        for y in range(self.n):
            for x in range(self.n):
                if not self.mask[y][x]:
                    continue
                edge = any(
                    not self.filled(x + dx, y + dy)
                    for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                )
                out[(x, y)] = BLUE if edge else CREAM
        out.update(self.overrides)
        return out


def _rows(rows) -> Art:
    a = Art()
    for y, x0, x1 in rows:
        a.span(y, x0, x1)
    return a


def quill_slim() -> Art:
    """Узкая молния-перо. Координатная схема (сетка 24×24):

      верхний луч строки 2-10,  шаг: одна колонка влево на две строки
      излом       строки 11-12, вылет вправо на четыре колонки
      нижний клин строки 13-21, сходится в остриё слева внизу

    Шаг ступеней одинаковый по всей длине — именно рваный шаг делал
    прошлые версии похожими на набросок.
    """
    return _rows([
        (2, 14, 15), (3, 14, 16), (4, 13, 16), (5, 13, 16), (6, 12, 15),
        (7, 12, 15), (8, 11, 14), (9, 11, 14), (10, 10, 14),
        (11, 10, 18), (12, 9, 17),
        (13, 9, 16), (14, 8, 15), (15, 8, 14), (16, 7, 13), (17, 7, 12),
        (18, 6, 11), (19, 6, 10), (20, 5, 8), (21, 5, 6),
    ])


def quill_wide() -> Art:
    """То же перо, штрих на ячейку толще: увереннее на 48px,
    в ряду иконок читается тяжелее."""
    return _rows([
        (2, 13, 15), (3, 13, 16), (4, 12, 16), (5, 12, 16), (6, 11, 15),
        (7, 11, 15), (8, 10, 15), (9, 10, 14), (10, 9, 14),
        (11, 9, 19), (12, 8, 18),
        (13, 8, 17), (14, 7, 16), (15, 7, 15), (16, 6, 14), (17, 6, 13),
        (18, 5, 12), (19, 5, 10), (20, 4, 9), (21, 4, 6),
    ])


def quill_steep() -> Art:
    """Перо круче и стремительнее: длиннее верхний луч, резче излом,
    сильнее завал по диагонали."""
    return _rows([
        (1, 15, 16), (2, 15, 17), (3, 14, 17), (4, 14, 17), (5, 13, 16),
        (6, 13, 16), (7, 12, 16), (8, 12, 15), (9, 11, 15), (10, 11, 15),
        (11, 10, 19), (12, 10, 18),
        (13, 9, 17), (14, 9, 16), (15, 8, 15), (16, 8, 14), (17, 7, 13),
        (18, 7, 12), (19, 6, 10), (20, 6, 9), (21, 5, 7), (22, 5, 6),
    ])


CONCEPTS = {"quill_slim": quill_slim, "quill_wide": quill_wide, "quill_steep": quill_steep}


def render(concept, px, with_bg=True, mono=False, symbol_scale=1.0, rounded=False):
    art = CONCEPTS[concept]()
    img = Image.new("RGBA", (px, px), BG if with_bg else (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    if with_bg and rounded:
        img = Image.new("RGBA", (px, px), (0, 0, 0, 0))
        draw = ImageDraw.Draw(img)
        draw.rounded_rectangle([0, 0, px - 1, px - 1], radius=int(px * 0.22), fill=BG)
    cell = px * symbol_scale / GRID
    origin = (px - cell * GRID) / 2
    for (x, y), color in art.cells.items():
        x0 = origin + x * cell
        y0 = origin + y * cell
        # Нахлёст 0.5px: без него между ячейками видны щели.
        draw.rectangle([x0, y0, x0 + cell + 0.5, y0 + cell + 0.5],
                       fill=WHITE if mono else color)
    return img


def _shaped(concept, px, shape):
    """Формы, в которых Android показывает иконку: квадрат со скруглением,
    круг (round) и сквиркл адаптивной маски."""
    icon = render(concept, px, symbol_scale=LEGACY_SCALE)
    mask = Image.new("L", (px, px), 0)
    d = ImageDraw.Draw(mask)
    if shape == "square":
        d.rounded_rectangle([0, 0, px - 1, px - 1], radius=int(px * 0.16), fill=255)
    elif shape == "round":
        d.ellipse([0, 0, px - 1, px - 1], fill=255)
    else:
        d.rounded_rectangle([0, 0, px - 1, px - 1], radius=int(px * 0.30), fill=255)
    icon.putalpha(mask)
    return icon


def preview(out_dir):
    os.makedirs(out_dir, exist_ok=True)
    names = list(CONCEPTS)
    shapes = ["square", "round", "squircle"]
    sizes = [48, 96, 192]
    pad, colw, rowh = 24, 230, 216
    sheet = Image.new(
        "RGBA",
        (pad + len(shapes) * len(sizes) * 72 + len(shapes) * colw,
         pad + len(names) * rowh),
        (24, 24, 28, 255),
    )
    for i, concept in enumerate(names):
        x = pad
        for shape in shapes:
            for size in sizes:
                icon = _shaped(concept, size, shape)
                sheet.paste(icon, (x, pad + i * rowh + (192 - size) // 2), icon)
                x += size + 16
            x += 24
        render(concept, 512, rounded=True).save(
            os.path.join(out_dir, f"{concept}-512.png"))
    sheet.save(os.path.join(out_dir, "concepts.png"))
    print("готово:", out_dir)


DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
ADAPTIVE = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}


def apply(concept, res_dir):
    for density, size in DENSITIES.items():
        d = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(d, exist_ok=True)
        render(concept, size, rounded=True, symbol_scale=LEGACY_SCALE).save(
            os.path.join(d, "ic_launcher.png"))
        icon = render(concept, size, symbol_scale=LEGACY_SCALE)
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
        icon.putalpha(mask)
        icon.save(os.path.join(d, "ic_launcher_round.png"))
    for density, size in ADAPTIVE.items():
        d = os.path.join(res_dir, f"mipmap-{density}")
        os.makedirs(d, exist_ok=True)
        # 66% полотна: остальное — safe zone под адаптивные маски.
        render(concept, size, with_bg=False, symbol_scale=ADAPTIVE_SCALE).save(
            os.path.join(d, "ic_launcher_foreground.png"))
        render(concept, size, with_bg=False, mono=True, symbol_scale=ADAPTIVE_SCALE).save(
            os.path.join(d, "ic_launcher_monochrome.png"))
    print(f"ассеты иконки «{concept}» записаны в {res_dir}")


def desktop(concept, out_dir):
    """Иконка для Linux-версии: те же пиксели, что на телефоне. Размеры кратны
    сетке 24, поэтому ячейка целое число пикселей и края остаются чёткими."""
    os.makedirs(out_dir, exist_ok=True)
    for size in (48, 96, 192, 384):
        render(concept, size, rounded=True, symbol_scale=LEGACY_SCALE).save(
            os.path.join(out_dir, f"w0y-{size}.png"))
    print(f"иконки «{concept}» для десктопа записаны в {out_dir}")


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    cmd = sys.argv[1] if len(sys.argv) > 1 else "preview"
    if cmd == "preview":
        preview(os.path.join(root, "docs", "icon-concepts"))
    elif cmd == "desktop":
        desktop(sys.argv[2], os.path.join(root, "desktop", "src", "main", "resources", "icons"))
    elif cmd == "apply":
        apply(sys.argv[2], os.path.join(root, "app", "src", "main", "res"))
    else:
        raise SystemExit(__doc__)
