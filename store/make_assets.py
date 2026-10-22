"""Generates EthanConnect's icon + banner PNGs (Android resources and Play Console store assets).
Run:  python make_assets.py   (needs Pillow)"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
ANDROID_RES = os.path.join(HERE, "..", "android", "app", "src", "main", "res")
BLUE, DARK, WHITE = (59, 130, 246), (17, 24, 39), (255, 255, 255)


def glyph(size, fg=WHITE, accent=BLUE, bg=None):
    """A monitor with a link arrow: the 'second screen' mark."""
    im = Image.new("RGBA", (size, size), bg or (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    s = size / 100
    d.rounded_rectangle((14 * s, 24 * s, 86 * s, 66 * s), 6 * s, fill=accent, outline=fg, width=max(1, round(3 * s)))
    d.rectangle((44 * s, 66 * s, 56 * s, 76 * s), fill=fg)
    d.rounded_rectangle((32 * s, 76 * s, 68 * s, 82 * s), 3 * s, fill=fg)
    # arrow pointing right-up inside the screen
    d.polygon([(34 * s, 52 * s), (56 * s, 52 * s), (56 * s, 60 * s), (70 * s, 45 * s), (56 * s, 30 * s),
               (56 * s, 38 * s), (34 * s, 38 * s)], fill=fg)
    return im


def font(size, bold=True):
    for name in ("segoeuib.ttf" if bold else "segoeui.ttf", "arialbd.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def icon(size):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((0, 0, size - 1, size - 1), size * 0.22, fill=DARK)
    g = glyph(int(size * 0.8))
    im.alpha_composite(g, (int(size * 0.1), int(size * 0.1)))
    return im


def banner(w, h):
    im = Image.new("RGB", (w, h), DARK)
    d = ImageDraw.Draw(im)
    g = glyph(int(h * 0.5))
    title, sub = font(int(h * 0.13)), font(int(h * 0.06), False)
    tw = max(d.textlength("EthanConnect", font=title), d.textlength("Your PC as a second screen", font=sub))
    gap = h * 0.06
    x = (w - (g.width + gap + tw)) / 2
    im.paste(g, (int(x), (h - g.height) // 2), g)
    d.text((x + g.width + gap, h * 0.36), "EthanConnect", font=title, fill=WHITE)
    d.text((x + g.width + gap, h * 0.36 + h * 0.17), "Your PC as a second screen", font=sub, fill=(156, 163, 175))
    return im


def main():
    for folder, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        out = os.path.join(ANDROID_RES, f"mipmap-{folder}")
        os.makedirs(out, exist_ok=True)
        icon(size).save(os.path.join(out, "ic_launcher.png"))
    os.makedirs(os.path.join(ANDROID_RES, "drawable-xhdpi"), exist_ok=True)
    banner(320, 180).save(os.path.join(ANDROID_RES, "drawable-xhdpi", "banner.png"))
    os.makedirs(os.path.join(HERE, "play"), exist_ok=True)
    icon(512).save(os.path.join(HERE, "play", "icon-512.png"))
    banner(1280, 720).save(os.path.join(HERE, "play", "tv-banner-1280x720.png"))
    # 1024x500 feature graphic
    fg = banner(1024, 500)
    fg.save(os.path.join(HERE, "play", "feature-graphic-1024x500.png"))
    # PC app icon (.ico) for the installer / exe
    ico = icon(256)
    ico.save(os.path.join(HERE, "..", "pc", "ethanconnect.ico"), sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    print("assets written")


if __name__ == "__main__":
    main()
