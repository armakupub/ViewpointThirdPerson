from PIL import Image, ImageChops, ImageDraw, ImageFont
import math
import os
import sys

SIZE = 512
SS = 4  # supersample factor; drawing happens at SIZE*SS, LANCZOS down
BG = (30, 30, 35)
GOLD = (220, 160, 40)
WHITE = (255, 255, 255)
BANNER = "for Project Viewpoint"

# Motif borrows the camera viewfinder: four corner brackets framing an
# over-the-shoulder shot, the player's head and shoulders seen from behind,
# cut by the frame. The reticle nods to the crosshair in Viewpoint's logo
# without copying it. Icon is its own composition: brackets and figure only.


def font(px, bold=False):
    try:
        return ImageFont.truetype("arialbd.ttf" if bold else "arial.ttf", px)
    except Exception:
        return ImageFont.load_default()


def disc(draw, p, r, color):
    draw.ellipse([p[0] - r, p[1] - r, p[0] + r, p[1] + r], fill=color)


def stamp_line(draw, a, b, w, color):
    # Disc-stamped stroke: round caps, no PIL joint artifacts.
    n = max(2, int(math.dist(a, b) / (w * 0.15)))
    for i in range(n + 1):
        t = i / n
        disc(draw, (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t), w / 2, color)


def brackets(draw, box, arm, w, color):
    x0, y0, x1, y1 = box
    for cx, cy, dx, dy in ((x0, y0, 1, 1), (x1, y0, -1, 1), (x0, y1, 1, -1), (x1, y1, -1, -1)):
        stamp_line(draw, (cx, cy), (cx + dx * arm, cy), w, color)
        stamp_line(draw, (cx, cy), (cx, cy + dy * arm), w, color)


def figure(img, box, cx, head_y, s, color):
    # Head and shoulders from behind, on its own mask clipped to the frame.
    mask = Image.new("L", img.size, 0)
    d = ImageDraw.Draw(mask)
    disc(d, (cx, head_y), 50 * s, 255)
    d.rounded_rectangle([cx - 22 * s, head_y + 30 * s, cx + 22 * s, head_y + 80 * s], 10 * s, fill=255)
    d.ellipse([cx - 150 * s, head_y + 62 * s, cx + 150 * s, head_y + 300 * s], fill=255)
    clip = Image.new("L", img.size, 0)
    ImageDraw.Draw(clip).rectangle(box, fill=255)
    img.paste(color, (0, 0, img.size[0], img.size[1]), ImageChops.multiply(mask, clip))


def reticle(draw, c, r, w, color):
    for i in range(360):
        a = math.radians(i)
        disc(draw, (c[0] + r * math.cos(a), c[1] + r * math.sin(a)), w / 2, color)
    tick = r * 0.75
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        stamp_line(draw, (c[0] + dx * (r - tick), c[1] + dy * (r - tick)),
                   (c[0] + dx * (r + tick * 0.55), c[1] + dy * (r + tick * 0.55)), w, color)
    disc(draw, c, w * 0.6, color)


def banner(draw, s, height=46, text_px=28):
    draw.rectangle([0, 0, SIZE * s, height * s], fill=WHITE)
    draw.text((SIZE * s / 2, height * s / 2), BANNER, fill=BG, font=font(text_px * s, bold=True), anchor="mm")


def title(draw, s, lines=("Third Person", "Camera"), y=410):
    f = font(44 * s)
    b = [draw.textbbox((0, 0), t, font=f) for t in lines]
    th = b[0][3] - b[0][1]
    for i, (t, bb) in enumerate(zip(lines, b)):
        draw.text(((SIZE * s - (bb[2] - bb[0])) / 2, (y + i * (th / s + 12)) * s), t, fill=WHITE, font=f)


def poster():
    s = SS
    img = Image.new("RGB", (SIZE * s, SIZE * s), BG)
    d = ImageDraw.Draw(img)
    box = (62 * s, 72 * s, 450 * s, 384 * s)
    figure(img, box, 178 * s, 288 * s, s, GOLD)
    reticle(d, (338 * s, 206 * s), 30 * s, 8 * s, WHITE)
    brackets(d, box, 56 * s, 12 * s, WHITE)
    banner(d, s)
    title(d, s)
    return img.resize((SIZE, SIZE), Image.LANCZOS)


def icon():
    # Rendered at 512, LANCZOS down to 32; thick strokes so the corners survive.
    img = Image.new("RGB", (512, 512), BG)
    d = ImageDraw.Draw(img)
    box = (40, 40, 472, 472)
    figure(img, box, 256, 236, 1.9, GOLD)
    brackets(d, box, 120, 46, WHITE)
    return img.resize((32, 32), Image.LANCZOS)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.abspath(__file__))
    poster().save(os.path.join(out, "poster.png"))
    icon().save(os.path.join(out, "icon.png"))
    print("Saved poster.png and icon.png to", out)
