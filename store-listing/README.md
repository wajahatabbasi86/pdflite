# Play Console listing assets

Copy-paste-ready text and graphics for the Play Console "Store presence > Main store listing"
page (step 9 of the build order — see the root [README.md](../README.md)).

| File | Play Console field | Limit | Notes |
|---|---|---|---|
| `short-description.txt` | Short description | 80 characters | |
| `full-description.txt` | Full description | 4000 characters | |
| `release-notes.txt` | "What's new" for the first release | 500 characters | |
| `feature-graphic.png` | Feature graphic | exactly 1024×500, JPG/PNG (no alpha) | Generated to spec — see below |
| `screenshots/*.png` | Phone screenshots | 2–8 images, 320–3840px per side, PNG/JPG | Real on-device captures, 1080×2400 (well within the 16:9–9:21 aspect range) |

## Screenshots

Captured on-device from a debug build (Pixel 8 AVD), in the order Play Console displays them:

1. `01-home.png` — Home, all six tools in the List layout (the ad banner strip is cropped out — a
   real production build would show a live ad here, not a "Test Ad" placeholder)
2. `02-merge.png` — Merge PDFs entry screen
3. `03-split.png` — Split / Extract Pages entry screen
4. `04-compress.png` — Compress PDF entry screen
5. `05-appearance.png` — Appearance settings (accent color, card/border/muted tints, background
   style, Home layout, theme) — the app's user-customizable theming, a real differentiator worth
   surfacing in the listing rather than just showing tool screens

Not included: a View PDF screenshot with a rendered page — the on-device sample PDF used for
testing throughout this project renders a blank page, which would look broken in a store
listing. Swap in a real, visually rich PDF and recapture that screen before publishing if a
sixth screenshot is wanted.

## Feature graphic

`feature-graphic.png` (1024×500) was generated programmatically (Pillow/PIL) rather than
hand-designed in an image editor, using the same visual language as the in-app design system:
the Merge-red → GradientButton-indigo gradient, and a glyph in the same "page with lines" style
as `FileIconAvatar`/`ToolGlyph`. `gen_feature_graphic.py` (kept alongside this file, requires
`pip install Pillow`) reproduces it exactly — edit the copy/colors there and re-run
`python gen_feature_graphic.py` from this directory to regenerate.

## Still needed before actually publishing

- A real Play Console developer account and app listing created there (this repo only prepares
  the assets — nothing here uploads them).
- The real AdMob App ID and ad unit ID, and the real Play Billing product ID for "remove_ads",
  swapping out the test IDs called out in `AndroidManifest.xml` / `AdBanner.kt` /
  `BillingRepository.kt`.
- A privacy policy URL (required by Play Console once the app requests any permission or uses
  ads/billing, both of which EasyDoc does).
- Content rating questionnaire and target audience/data-safety form, filled out in Play Console
  directly — no local asset substitutes for these.
