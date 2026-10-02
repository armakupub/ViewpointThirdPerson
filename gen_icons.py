from PIL import Image, ImageDraw, ImageFont
import math
import os
import sys

# Slices for the game's Back wheel, in the poster's motif: viewfinder brackets and the gold
# head and shoulders. White strokes on a dark outline read on the wheel's translucent backdrop.
SIZE = 64
S = 512
GOLD = (220, 160, 40, 255)
WHITE = (255, 255, 255, 255)
EDGE = (20, 20, 24, 255)
OUT = 22


def font(px):
    try:
        return ImageFont.truetype("arialbd.ttf", px)
    except Exception:
        return ImageFont.load_default()


def disc(d, p, r, c):
    d.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=c)


def line(d, a, b, w, c):
    n = max(2, int(math.dist(a, b) / (w * 0.15)))
    for i in range(n + 1):
        t = i / n
        disc(d, (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t), w / 2, c)


def outlined(d, draw_fn, w):
    draw_fn(d, w + OUT, EDGE)
    draw_fn(d, w, WHITE)


def brackets(box, arm):
    x0, y0, x1, y1 = box

    def f(d, w, c):
        for cx, cy, dx, dy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
            line(d, (cx, cy), (cx + dx * arm, cy), w, c)
            line(d, (cx, cy), (cx, cy + dy * arm), w, c)
    return f


def figure(d, cx, head_y, s):
    for grow, c in ((OUT / 2, EDGE), (0, GOLD)):
        disc(d, (cx, head_y), 50 * s + grow, c)
        d.rounded_rectangle([cx - 22 * s - grow, head_y + 30 * s, cx + 22 * s + grow, head_y + 80 * s], 10 * s, fill=c)
        d.pieslice([cx - 150 * s - grow, head_y + 62 * s - grow, cx + 150 * s + grow, head_y + 300 * s + grow], 180, 360, fill=c)


def reticle(c, r):
    def f(d, w, col):
        for i in range(0, 360, 2):
            a = math.radians(i)
            disc(d, (c[0] + r * math.cos(a), c[1] + r * math.sin(a)), w / 2, col)
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            line(d, (c[0] + dx * r * 0.45, c[1] + dy * r * 0.45), (c[0] + dx * r * 1.45, c[1] + dy * r * 1.45), w, col)
    return f


def label(d, text, xy):
    f = font(150)
    d.text(xy, text, font=f, fill=WHITE, anchor="mm", stroke_width=14, stroke_fill=EDGE)


def canvas():
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def done(img):
    return img.resize((SIZE, SIZE), Image.LANCZOS)


BOX = (40, 40, 472, 472)


def view_3d():
    # Into Viewpoint's view: the shot from behind the shoulder, framed.
    img, d = canvas()
    figure(d, 256, 250, 1.15)
    outlined(d, brackets(BOX, 120), 40)
    return done(img)


def view_iso():
    # Back to the game's view: a patch of isometric floor.
    img, d = canvas()

    def tiles(dd, w, c):
        cx, cy, hw, hh = 256, 270, 210, 120
        top, right, bottom, left = (cx, cy - hh), (cx + hw, cy), (cx, cy + hh), (cx - hw, cy)
        for a, b in ((top, right), (right, bottom), (bottom, left), (left, top)):
            line(dd, a, b, w, c)
        line(dd, ((top[0] + left[0]) / 2, (top[1] + left[1]) / 2), ((right[0] + bottom[0]) / 2, (right[1] + bottom[1]) / 2), w, c)
        line(dd, ((top[0] + right[0]) / 2, (top[1] + right[1]) / 2), ((left[0] + bottom[0]) / 2, (left[1] + bottom[1]) / 2), w, c)
    outlined(d, tiles, 30)
    return done(img)


def to_third():
    img, d = canvas()
    figure(d, 210, 250, 1.0)
    outlined(d, brackets(BOX, 120), 40)
    label(d, "3P", (360, 150))
    return done(img)


def to_first():
    img, d = canvas()
    outlined(d, reticle((230, 270), 90), 30)
    outlined(d, brackets(BOX, 120), 40)
    label(d, "1P", (360, 150))
    return done(img)


def shoulder():
    img, d = canvas()
    figure(d, 256, 250, 1.05)

    def arrows(dd, w, c):
        y = 110
        line(dd, (90, y), (422, y), w, c)
        for x, dx in ((90, 1), (422, -1)):
            line(dd, (x, y), (x + dx * 70, y - 60), w, c)
            line(dd, (x, y), (x + dx * 70, y + 60), w, c)
    outlined(d, arrows, 36)
    return done(img)


ICONS = {"view_3d": view_3d, "view_iso": view_iso, "to_third": to_third, "to_first": to_first, "shoulder": shoulder}

if __name__ == "__main__":
    here = os.path.dirname(os.path.abspath(__file__))
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "mod_files", "42.21", "media", "ui", "ViewpointThirdPerson")
    os.makedirs(out, exist_ok=True)
    for name, make in ICONS.items():
        make().save(os.path.join(out, name + ".png"))
    print("Saved", ", ".join(ICONS), "to", out)
