from PIL import Image, ImageDraw, ImageFont

W, H = 1024, 500
FONT_DIR = "C:/Windows/Fonts/"

def font(name, size):
    return ImageFont.truetype(FONT_DIR + name, size)

title_font = font("segoeuib.ttf", 68)
tagline_font = font("segoeui.ttf", 27)
pill_font = font("segoeuib.ttf", 18)

# --- gradient background: stamp red -> plum -> indigo, matching the app's own accent/gradient ---
c1 = (193, 68, 45)     # 0xC1442D — Merge/accent red
c2 = (150, 60, 100)    # midpoint plum
c3 = (76, 95, 213)     # 0x4C5FD5 — GradientButton's indigo

img = Image.new("RGB", (W, H))
px = img.load()
for x in range(W):
    t = x / (W - 1)
    if t < 0.5:
        tt = t / 0.5
        r = int(c1[0] + (c2[0] - c1[0]) * tt)
        g = int(c1[1] + (c2[1] - c1[1]) * tt)
        b = int(c1[2] + (c2[2] - c1[2]) * tt)
    else:
        tt = (t - 0.5) / 0.5
        r = int(c2[0] + (c3[0] - c2[0]) * tt)
        g = int(c2[1] + (c3[1] - c2[1]) * tt)
        b = int(c2[2] + (c3[2] - c2[2]) * tt)
    for y in range(H):
        px[x, y] = (r, g, b)

draw = ImageDraw.Draw(img, "RGBA")

# subtle diagonal sheen
for i in range(0, W + H, 6):
    draw.line([(i, 0), (0, i)], fill=(255, 255, 255, 10), width=2)

# --- rounded white icon tile with a simple "page with lines" glyph, matching FileIconAvatar ---
icon_size = 176
icon_x, icon_y = 72, (H - icon_size) // 2
draw.rounded_rectangle(
    [icon_x, icon_y, icon_x + icon_size, icon_y + icon_size],
    radius=40, fill=(255, 255, 255, 255)
)
gx, gy, gw, gh = icon_x + 58, icon_y + 38, 60, 100
line_color = (193, 68, 45)
draw.rounded_rectangle([gx, gy, gx + gw, gy + gh], radius=8, outline=line_color, width=5)
ly = gy + 24
for w_frac in (0.62, 0.62, 0.4):
    lw = int(gw * w_frac) - 16
    draw.rounded_rectangle([gx + 12, ly, gx + 12 + lw, ly + 8], radius=4, fill=line_color)
    ly += 22

# --- title + tagline ---
text_x = icon_x + icon_size + 44
draw.text((text_x, 148), "TrenDoc", font=title_font, fill=(255, 255, 255, 255))
draw.text((text_x, 232), "Merge, split & compress PDFs —", font=tagline_font, fill=(255, 255, 255, 235))
draw.text((text_x, 270), "no pop-ups, no subscription, no account.", font=tagline_font, fill=(255, 255, 255, 235))

# --- pill badges ---
def pill(x, y, label):
    padding_x, padding_y = 20, 11
    bbox = draw.textbbox((0, 0), label, font=pill_font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x0, y0 = x, y
    x1, y1 = x + w + padding_x * 2, y + h + padding_y * 2
    draw.rounded_rectangle([x0, y0, x1, y1], radius=(y1 - y0) // 2,
                            fill=(255, 255, 255, 40), outline=(255, 255, 255, 90), width=1)
    draw.text((x0 + padding_x, y0 + padding_y - bbox[1]), label, font=pill_font, fill=(255, 255, 255, 255))
    return x1

px_cursor = text_x
py = 372
for label in ("No pop-ups", "No subscription", "Nothing shared"):
    px_cursor = pill(px_cursor, py, label) + 14

import os
out_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "feature-graphic.png")
img.save(out_path, "PNG")
print("saved", out_path, img.size)
