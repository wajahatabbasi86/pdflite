"""Generates TrenDoc's app icon assets (adaptive foreground/background + legacy
mipmap PNGs): a red card — the color universally associated with PDF — with a
crisp white document glyph (rounded corners, a folded top-right dog-ear, and a
bold red "b" mark: a stem plus a "D"-shaped bracket with a donut node dot),
plus a dashed connector path on the red background behind it.

Run: python tools/gen_app_icon.py
Regenerate whenever the brand artwork in this file's color constants changes.
"""

import math
import os

from PIL import Image, ImageDraw, ImageFilter

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(REPO_ROOT, "app", "src", "main", "res")

# --- brand colors: PDF red, not the earlier navy/cyan treatment ---
BG_DARK = (140, 16, 22)       # #8C1016 — bottom-right of the background gradient (deep red, not brown)
BG_LIGHT = (209, 34, 41)      # #D12229 — top-left of the background gradient (PDF red)
GLOW = (255, 240, 232)        # #FFF0E8 — warm near-white, reused for pill/label text on dark backgrounds
EDGE = (255, 255, 255)        # the page card itself — pure white, max contrast against the red fill
FOLD = (253, 226, 226)        # #FDE2E2 — pale pink for the folded dog-ear corner
DASH = (222, 140, 132)        # muted rose for the dashed connector path, on the red background
ACCENT = (213, 33, 40)        # #D52128 — bold red for the "b" glyph mark and for text on light backgrounds

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
    """Diagonal PDF-red gradient, light at top-left, dark at bottom-right."""
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            t = (x + y) / (2 * (size - 1))
            r = int(BG_LIGHT[0] + (BG_DARK[0] - BG_LIGHT[0]) * t)
            g = int(BG_LIGHT[1] + (BG_DARK[1] - BG_LIGHT[1]) * t)
            b = int(BG_LIGHT[2] + (BG_DARK[2] - BG_LIGHT[2]) * t)
            px[x, y] = (r, g, b)
    return img


def draw_glyph(size, glow=False):
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

    # the page: a plain white card, rounded on all four corners, with a folded
    # (dog-eared) top-right corner — a flat, crisp file glyph, not the glowing
    # wireframe of the earlier draft
    page_l, page_t, page_r, page_b = 280, 210, 700, 790
    fold = 90
    corner_r = 46

    draw.rounded_rectangle([page_l, page_t, page_r, page_b], radius=corner_r, fill=EDGE + (255,))
    # the fold cut replaces that corner's rounding with a diagonal dog-ear
    draw.polygon(
        [(page_r - fold, page_t), (page_r, page_t), (page_r, page_t + fold)],
        fill=FOLD + (255,),
    )
    # small inward curve where the fold's point meets the page, matching a real dog-ear
    notch_r = 22
    draw.pieslice(
        [page_r - fold - notch_r, page_t + fold - notch_r, page_r - fold + notch_r, page_t + fold + notch_r],
        start=0, end=90, fill=EDGE + (255,),
    )

    # bold "b" glyph: a vertical stem + a "D"-shaped bracket, in solid ACCENT red
    stem_x, stem_top, stem_bottom = 430, 350, 690
    stroke_w = 34
    draw.line([(stem_x, stem_top), (stem_x, stem_bottom)], fill=ACCENT + (255,), width=stroke_w)

    cx, cy, r = 460, 540, 130
    draw.arc(
        [cx - r, cy - r, cx + r, cy + r], start=-90, end=90,
        fill=ACCENT + (255,), width=stroke_w,
    )

    # donut node dot where the bracket's tip meets the (implied) connector path
    dot_x, dot_y, dot_r = cx + r, cy, 34
    draw.ellipse([dot_x - dot_r, dot_y - dot_r, dot_x + dot_r, dot_y + dot_r], fill=(163, 18, 24, 255))
    hole_r = 15
    draw.ellipse([dot_x - hole_r, dot_y - hole_r, dot_x + hole_r, dot_y + hole_r], fill=EDGE + (255,))

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
