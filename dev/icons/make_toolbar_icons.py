"""Draws the flat icons of the main toolbar (own work, same license as DocFetcher).

Each icon is drawn on a 16 x 16 unit grid at high resolution and scaled down to
dist/img/tb_<name>.png (16 px) and dist/img/tb_<name>@2x.png (32 px).
Usage: python dev/icons/make_toolbar_icons.py
"""
import os
from PIL import Image, ImageDraw

U = 32  # pixels per grid unit while drawing
SIZE = 16 * U

INK = (72, 72, 72, 255)
GRAY = (110, 110, 110, 255)
PAPER = (255, 255, 255, 255)
BLUE = (0, 103, 192, 255)
AMBER = (234, 172, 52, 255)
AMBER_DARK = (204, 140, 30, 255)
RED = (200, 48, 40, 255)
YELLOW = (255, 190, 20, 255)
BEIGE = (240, 208, 160, 255)


def u(*values):
    return [v * U for v in values]


def new():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def page(d, x0, y0, x1, y1, lines=0):
    d.rectangle(u(x0, y0, x1, y1), fill=PAPER, outline=INK, width=U)
    for i in range(lines):
        y = y0 + 2.5 + i * 2
        d.rectangle(u(x0 + 1.8, y, x1 - 1.8, y + 0.8), fill=BLUE)


def icon_print():
    img, d = new()
    page(d, 4, 1, 12, 6)
    d.rounded_rectangle(u(0.5, 5, 15.5, 12), radius=1.5 * U, fill=GRAY)
    d.rectangle(u(2, 7, 3.5, 8), fill=(120, 200, 120, 255))
    page(d, 3.5, 9, 12.5, 15.5)
    d.rectangle(u(5.3, 11.2, 10.7, 12), fill=BLUE)
    d.rectangle(u(5.3, 13.2, 10.7, 14), fill=BLUE)
    return img


def icon_mail():
    img, d = new()
    d.rounded_rectangle(u(0.5, 3, 15.5, 13), radius=U, fill=BLUE)
    d.line(u(1.2, 3.8, 8, 9.2, 14.8, 3.8), fill=PAPER, width=int(1.4 * U), joint="curve")
    return img


def icon_rename():
    # A pencil drawn horizontally, then turned by 45 degrees
    big = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    cy, h = 8, 2.2
    d.polygon(u(0.3, cy, 3.5, cy - h, 3.5, cy + h), fill=BEIGE)
    d.polygon(u(0.3, cy, 1.4, cy - 0.75, 1.4, cy + 0.75), fill=INK)
    d.rectangle(u(3.5, cy - h, 12.5, cy + h), fill=YELLOW)
    d.rectangle(u(3.5, cy - 0.35, 12.5, cy + 0.35), fill=(230, 160, 0, 255))
    d.rectangle(u(12.5, cy - h, 13.5, cy + h), fill=GRAY)
    d.rounded_rectangle(u(13.3, cy - h, 15.7, cy + h), radius=0.8 * U, fill=RED)
    return big.rotate(45, resample=Image.BICUBIC, center=(SIZE / 2, SIZE / 2))


def folder(d):
    d.rounded_rectangle(u(1, 2.5, 7, 6), radius=0.8 * U, fill=AMBER_DARK)
    d.rounded_rectangle(u(1, 4, 15, 14), radius=U, fill=AMBER)


def icon_move():
    img, d = new()
    folder(d)
    d.rectangle(u(0, 8, 7.5, 10), fill=BLUE)
    d.polygon(u(7, 5.5, 11.5, 9, 7, 12.5), fill=BLUE)
    return img


def plus_badge(d):
    d.ellipse(u(8.8, 8.8, 15.8, 15.8), fill=BLUE)
    d.rectangle(u(11.7, 10.3, 12.9, 14.3), fill=PAPER)
    d.rectangle(u(10.3, 11.7, 14.3, 12.9), fill=PAPER)


def icon_roots():
    # A house: back to the list of indexed root folders
    img, d = new()
    d.polygon(u(8, 0.8, 15.6, 8, 13.6, 8, 8, 2.9, 2.4, 8, 0.4, 8), fill=BLUE)
    d.polygon(u(8, 3.6, 13.2, 8.5, 13.2, 15, 2.8, 15, 2.8, 8.5), fill=AMBER)
    d.rectangle(u(6.6, 10, 9.4, 15), fill=AMBER_DARK)
    return img


def icon_up():
    img, d = new()
    d.polygon(u(8, 1, 14.5, 7.5, 10.2, 7.5, 10.2, 15, 5.8, 15, 5.8, 7.5, 1.5, 7.5), fill=BLUE)
    return img


def icon_refresh():
    img, d = new()
    d.arc(u(1.8, 1.8, 14.2, 14.2), start=-60, end=250, fill=BLUE, width=int(2.2 * U))
    # Arrow head at the open end (top right)
    d.polygon(u(9.2, 0.2, 15.2, 1.6, 11.6, 6.6), fill=BLUE)
    return img


def icon_new_folder():
    img, d = new()
    folder(d)
    plus_badge(d)
    return img


def icon_merge():
    # A stapler, seen from the side: base, arm hinged at the back, staple head in front
    img, d = new()
    d.rounded_rectangle(u(0.8, 11.2, 15.2, 13.6), radius=0.8 * U, fill=INK)
    d.rounded_rectangle(u(10.8, 10.2, 14.6, 11.4), radius=0.3 * U, fill=GRAY)  # Anvil
    d.polygon(u(1.2, 7.4, 13.6, 4.6, 15.2, 5.6, 15.2, 8.2, 2.4, 10.4), fill=BLUE)  # Arm
    d.rectangle(u(12.8, 8.0, 14.4, 10.2), fill=INK)  # Staple head
    d.ellipse(u(0.6, 7.4, 3.8, 11.8), fill=INK)  # Hinge
    d.ellipse(u(1.6, 8.6, 2.8, 10.6), fill=GRAY)
    return img


def icon_sync():
    # Two panes, coupled by a double arrow
    img, d = new()
    d.rounded_rectangle(u(0.5, 2, 6, 14), radius=0.8 * U, outline=INK, width=int(1.1 * U), fill=PAPER)
    d.rounded_rectangle(u(10, 2, 15.5, 14), radius=0.8 * U, outline=INK, width=int(1.1 * U), fill=PAPER)
    for y in (4.5, 7, 9.5):
        d.rectangle(u(1.9, y, 4.6, y + 0.8), fill=GRAY)
        d.rectangle(u(11.4, y, 14.1, y + 0.8), fill=GRAY)
    d.rectangle(u(5, 7.3, 11, 8.7), fill=BLUE)
    d.polygon(u(3.6, 8, 6.6, 5.4, 6.6, 10.6), fill=BLUE)
    d.polygon(u(12.4, 8, 9.4, 5.4, 9.4, 10.6), fill=BLUE)
    return img


def icon_split():
    img, d = new()
    # Blades crossing above the handles
    d.polygon(u(4.8, 9.5, 11.6, 0.6, 12.4, 1.4, 7, 10.5), fill=GRAY)
    d.polygon(u(11.2, 9.5, 4.4, 0.6, 3.6, 1.4, 9, 10.5), fill=INK)
    d.ellipse(u(7.4, 6.6, 8.6, 7.8), fill=PAPER)
    for cx in (4.2, 11.8):
        d.ellipse(u(cx - 3, 9.5, cx + 3, 15.5), fill=BLUE)
        d.ellipse(u(cx - 1.5, 11, cx + 1.5, 14), fill=(0, 0, 0, 0))
    return img


def icon_delete():
    img, d = new()
    d.rounded_rectangle(u(5.5, 0.8, 10.5, 3.5), radius=0.6 * U, outline=RED, width=int(1.2 * U))
    d.rounded_rectangle(u(1.5, 2.5, 14.5, 4.5), radius=0.6 * U, fill=RED)
    d.polygon(u(3, 5.3, 13, 5.3, 12, 15.5, 4, 15.5), fill=RED)
    for x in (5.6, 7.6, 9.6):
        d.rounded_rectangle(u(x, 7, x + 0.9, 13.8), radius=0.4 * U, fill=PAPER)
    return img


ICONS = {
    "print": icon_print,
    "mail": icon_mail,
    "rename": icon_rename,
    "move": icon_move,
    "merge": icon_merge,
    "split": icon_split,
    "delete": icon_delete,
    "roots": icon_roots,
    "up": icon_up,
    "refresh": icon_refresh,
    "new_folder": icon_new_folder,
    "sync": icon_sync,
}


def main():
    out_dir = os.path.join(os.path.dirname(__file__), "..", "..", "dist", "img")
    for name, draw in ICONS.items():
        img = draw()
        img.resize((16, 16), Image.LANCZOS).save(os.path.join(out_dir, "tb_%s.png" % name))
        img.resize((32, 32), Image.LANCZOS).save(os.path.join(out_dir, "tb_%s@2x.png" % name))


if __name__ == "__main__":
    main()
