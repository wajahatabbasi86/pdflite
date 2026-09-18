import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools"))
from gen_app_icon import NAVY_DARK, NAVY_LIGHT, CYAN, radial_navy_background, draw_glyph  # noqa: E402

W, H = 1024, 500
FONT_DIR = "C:/Windows/Fonts/"


def font(name, size):
    return ImageFont.truetype(FONT_DIR + name, size)


title_font = font("segoeuib.ttf", 68)
tagline_font = font("segoeui.ttf", 27)
pill_font = font("segoeuib.ttf", 18)
byline_font = font("segoeuib.ttf", 20)

# --- white background, matching the TrenDoc brand sheet ---
img = Image.new("RGB", (W, H), (255, 255, 255))
draw = ImageDraw.Draw(img, "RGBA")

# --- rounded navy icon tile with the brand glyph, matching the app icon ---
icon_size = 176
icon_x, icon_y = 72, (H - icon_size) // 2
tile_hi = icon_size * 4
tile = radial_navy_background(tile_hi)
mask = Image.new("L", (tile_hi, tile_hi), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, tile_hi - 1, tile_hi - 1], radius=int(tile_hi * 0.22), fill=255)
tile.putalpha(mask)
glyph = draw_glyph(int(tile_hi * 0.66))
tile.paste(glyph, ((tile_hi - glyph.width) // 2, (tile_hi - glyph.height) // 2), glyph)
tile = tile.resize((icon_size, icon_size), Image.LANCZOS)
img.paste(tile, (icon_x, icon_y), tile)

# --- wordmark: "Tren" (dark navy) + "doc" (cyan) ---
text_x = icon_x + icon_size + 44
tren_w = draw.textbbox((0, 0), "Tren", font=title_font)[2]
draw.text((text_x, 118), "Tren", font=title_font, fill=NAVY_DARK)
draw.text((text_x + tren_w, 118), "doc", font=title_font, fill=CYAN)

draw.text((text_x, 202), "Merge, split & compress PDFs —", font=tagline_font, fill=(60, 60, 68, 255))
draw.text((text_x, 240), "no pop-ups, no subscription, no account.", font=tagline_font, fill=(60, 60, 68, 255))


# --- pill badges ---
def pill(x, y, label):
    padding_x, padding_y = 20, 11
    bbox = draw.textbbox((0, 0), label, font=pill_font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x0, y0 = x, y
    x1, y1 = x + w + padding_x * 2, y + h + padding_y * 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=(y1 - y0) // 2,
                            fill=NAVY_DARK + (255,))
    draw.text((x0 + padding_x, y0 + padding_y - bbox[1]), label, font=pill_font, fill=CYAN + (255,))
    return x1


px_cursor = text_x
py = 320
for label in ("No pop-ups", "No subscription", "Nothing shared"):
    px_cursor = pill(px_cursor, py, label) + 14

# --- "by TrenBridge IT" byline, bottom-right ---
byline = "by TrenBridge IT"
bbox = draw.textbbox((0, 0), byline, font=byline_font)
bw = bbox[2] - bbox[0]
draw.text((W - bw - 40, H - 50), byline, font=byline_font, fill=(120, 128, 140, 255))

out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "feature-graphic.png")
img.save(out_path, "PNG")
print("saved", out_path, img.size)
