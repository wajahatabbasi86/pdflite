"""Generates TrenDoc's app icon assets (adaptive foreground/background + legacy
mipmap PNGs), matching the brand artwork: a dark navy card with a glowing cyan
document glyph (folded corner, a "D"-shaped bracket accenting three lines, and a
dashed connector path with two node dots).

Run: python tools/gen_app_icon.py
Regenerate whenever the brand artwork in this file's color constants changes.
"""

import math
import os

from PIL import Image, ImageDraw, ImageFilter

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res")

# --- brand colors, sampled from the TrenDoc icon artwork ---
NAVY_DARK = (10, 20, 38)      # #0A1426 — bottom-right of the background gradient
NAVY_LIGHT = (16, 33, 61)     # #10213D — top-left of the background gradient
CYAN = (56, 217, 255)         # #38D9FF — glyph glow color
BLUE = (43, 118, 240)         # #2B76F0 — glyph edge/outline color
DASH = (58, 92, 130)          # muted steel-blue for the dashed connector path

DENSITIES = {
    "mdpi": 1.0,
    "hdpi": 1.5,
    "xhdpi": 2.0,
    "xxhdpi": 3.0,
    "xxxhdpi": 4.0,
}
LEGACY_BASE_DP = 48       # ic_launcher.png / ic_launcher_round.png base size
FOREGROUND_BASE_DP = 108  # adaptive icon foreground layer base size
SUPERSAMPLE = 8           # render this many times larger, then downscale for AA


def radial_navy_background(size):
    """Diagonal navy gradient, light at top-left, dark at bottom-right."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            r = int(NAVY_LIGHT[0] + (NAVY_DARK[0] - NAVY_LIGHT[0]) * t)
            g = int(NAVY_LIGHT[1] + (NAVY_DARK[1] - NAVY_LIGHT[1]) * t)
            b = int(NAVY_LIGHT[2] + (NAVY_DARK[2] - NAVY_LIGHT[2]) * t)
            px[x, y] = (r, g, b)
    return img


def draw_glyph(size, glow=True):
    """The document + dashed-path glyph, on a transparent canvas of `size`.

    Drawn at 1000x1000 internal coordinates then resized, so proportions stay
    consistent regardless of the requested output size.
    """
    S = 1000
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer, "RGBA")

    # dashed rounded connector path (top-right corner), drawn first so the
    # document sits on top of it, matching the reference artwork
    def dashed_rounded_path():
        cx, cy, r = 700, 300, 220
        start_deg, end_deg = -180, 90
        steps = 40
        pts = []
        for i in range(steps + 1):
            a = math.radians(start_deg + (end_deg - start_deg) * i / steps)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
        # straight lead-ins so the path visually connects to the document edges
        pts = [(470, 300)] + pts + [(700, 520)]
        dash_len, gap_len = 22, 16
        for i in range(len(pts) - 1):
            x0, y0 = pts[i]
            x1, y1 = pts[i + 1]
            seg_len = math.hypot(x1 - x0, y1 - y0)
            if seg_len == 0:
                continue
            n = max(1, int(seg_len / (dash_len + gap_len)))
            for j in range(n):
                t0 = j / n
                t1 = t0 + (dash_len / seg_len)
                t1 = min(t1, 1.0)
                draw.line(
                    [
                        (x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0),
                        (x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1),
                    ],
                    fill=DASH + (255,), width=10,
                )

    dashed_rounded_path()

    # the page: rounded rect with a folded top-right corner
    page_l, page_t, page_r, page_b = 280, 210, 700, 790
    fold = 90
    outline_w = 16

    page = [
        (page_l + 40, page_t),
        (page_r - fold, page_t),
        (page_r, page_t + fold),
        (page_r, page_b - 40),
        (page_r - 40, page_b),
        (page_l + 40, page_b),
        (page_l, page_b - 40),
        (page_l, page_t + 40),
    ]
    draw.polygon(page, fill=NAVY_DARK + (255,))
    draw.line(page + [page[0]], fill=BLUE + (255,), width=outline_w, joint="curve")
    # folded-corner triangle accent
    draw.polygon(
        [(page_r - fold, page_t), (page_r, page_t + fold), (page_r - fold, page_t + fold)],
        fill=BLUE + (255,),
    )

    # three accent lines + the "D" bracket to their right
    line_x0, line_x1 = 410, 560
    for i, ly in enumerate((470, 540, 610)):
        w = line_x1 - (i * 40) - line_x0
        draw.line([(line_x0, ly), (line_x0 + max(w, 60), ly)], fill=CYAN + (255,), width=12)

    cx, cy, r = 585, 540, 110
    draw.arc(
        [cx - r, cy - r, cx + r, cy + r], start=-95, end=95,
        fill=CYAN + (255,), width=22,
    )

    # node dots
    draw.ellipse([cx + r - 24, cy - 24, cx + r + 24, cy + 24], fill=CYAN + (255,))
    draw.ellipse([page_l - 18, page_b - 18, page_l + 18, page_b + 18], fill=BLUE + (255,))
    draw.ellipse([page_r - 18, page_b - 18, page_r + 18, page_b + 18], fill=CYAN + (255,))

    if glow:
        glow_layer = layer.filter(ImageFilter.GaussianBlur(14))
        combined = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        combined.alpha_composite(glow_layer)
        combined.alpha_composite(layer)
        layer = combined

    return layer.resize((size, size), Image.LANCZOS)


def rounded_mask(size, radius):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return mask


def circle_mask(size):
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, size - 1, size - 1], fill=255)
    return mask


def legacy_icon(size, round_shape):
    hi = size * SUPERSAMPLE
    bg = radial_navy_background(hi)
    glyph = draw_glyph(int(hi * 0.66))
    bg.paste(glyph, ((hi - glyph.width) // 2, (hi - glyph.height) // 2), glyph)
    mask = circle_mask(hi) if round_shape else rounded_mask(hi, int(hi * 0.18))
    out = Image.new("RGBA", (hi, hi), (0, 0, 0, 0))
    out.paste(bg, (0, 0))
    out.putalpha(mask)
    return out.resize((size, size), Image.LANCZOS)


def foreground_layer(size):
    hi = size * SUPERSAMPLE
    # glyph occupies the ~66% "safe zone" Android reserves inside the adaptive
    # icon's 108dp foreground canvas
    glyph = draw_glyph(int(hi * 0.66 * 0.72))
    canvas = Image.new("RGBA", (hi, hi), (0, 0, 0, 0))
    canvas.paste(glyph, ((hi - glyph.width) // 2, (hi - glyph.height) // 2), glyph)
    return canvas.resize((size, size), Image.LANCZOS)


def main():
    for density, scale in DENSITIES.items():
        d = os.path.join(RES_DIR, f"mipmap-{density}")
        os.makedirs(d, exist_ok=True)

        legacy_size = round(LEGACY_BASE_DP * scale)
        legacy_icon(legacy_size, round_shape=False).save(os.path.join(d, "ic_launcher.png"))
        legacy_icon(legacy_size, round_shape=True).save(os.path.join(d, "ic_launcher_round.png"))

        fg_size = round(FOREGROUND_BASE_DP * scale)
        foreground_layer(fg_size).save(os.path.join(d, "ic_launcher_foreground.png"))

        print(f"{density}: legacy {legacy_size}px, foreground {fg_size}px")

    # Play Console hi-res icon (512x512, uploaded separately from the APK)
    store_dir = os.path.join(REPO_ROOT, "store-listing")
    os.makedirs(store_dir, exist_ok=True)
    legacy_icon(512, round_shape=False).convert("RGB").save(
        os.path.join(store_dir, "app-icon-512.png")
    )
    print("store-listing/app-icon-512.png: 512px")


if __name__ == "__main__":
    main()
