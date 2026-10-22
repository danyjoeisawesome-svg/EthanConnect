"""Replaces the real LAN address in two Play screenshots with a generic example (192.168.1.20)."""
from PIL import Image, ImageDraw, ImageFont
import os

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), "play", "screenshots")
NEW = "192.168.1.20:8443"


def font_for_width(target_w):
    for name in ("segoeui.ttf", "arial.ttf"):
        try:
            for size in range(60, 20, -1):
                f = ImageFont.truetype(name, size)
                if f.getlength(NEW) <= target_w:
                    return f
        except OSError:
            continue
    return ImageFont.load_default()


def redact(fname, box, text_xy, color=(255, 255, 255)):
    path = os.path.join(D, fname)
    im = Image.open(path).convert("RGB")
    d = ImageDraw.Draw(im)
    x0, y0, x1, y1 = box
    bg = im.getpixel((x0 - 4, y0 + 2))
    d.rectangle(box, fill=bg)
    f = font_for_width(x1 - x0 - 6)
    d.text(text_xy, NEW, font=f, fill=color)
    im.save(path)


# settings: address line under "Paired PC"
redact("tv-3-settings-1920x1080.png", (44, 205, 380, 258), (49, 207))
# pairing dialog: address field (underline at y~562 is left untouched)
redact("tv-2-pairing-1920x1080.png", (512, 506, 880, 556), (519, 509))
print("done")
