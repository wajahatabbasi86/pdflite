# Play Console listing assets

Copy-paste-ready text and graphics for the Play Console "Store presence > Main store listing"
page (step 9 of the build order — see the root [README.md](../README.md)).

## Positioning

The short/full descriptions and feature graphic here don't lead with a feature list — they lead
with "no pop-ups, no subscription, no account, nothing shared." That copy isn't aspirational: it
was written after (1) auditing this codebase directly to confirm every one of those claims holds
with zero exceptions (no `Dialog` anywhere in the app, entitlement state checked in exactly two
places, both Billing product-type calls are `INAPP` never `SUBS`, zero third-party data sharing),
and (2) reading real, high-vote-count user reviews on the three largest competitors in this
category (Adobe Acrobat 500M+, Xodo 10M+, Foxit 10M+) — all three have their own users' top
reviews complaining about exactly the pattern this copy calls out by name. If TrenDoc's behavior
ever changes (an ad placement added elsewhere, a dialog introduced, a new SDK that shares data),
this copy needs to be revisited before it's a lie rather than a fact.

| File | Play Console field | Limit | Notes |
|---|---|---|---|
| `short-description.txt` | Short description | 80 characters | |
| `full-description.txt` | Full description | 4000 characters | |
| `release-notes.txt` | "What's new" for the first release | 500 characters | |
| `feature-graphic.png` | Feature graphic | exactly 1024×500, JPG/PNG (no alpha) | Generated to spec — see below |
| `app-icon-512.png` | Hi-res icon | exactly 512×512, 32-bit PNG | Generated alongside the real app icon — see below |
| `screenshots/*.png` | Phone screenshots | 2–8 images, 320–3840px per side, PNG/JPG | Real on-device captures, 1080×2400 (well within the 16:9–9:21 aspect range) |
| `privacy-policy.html` | App content > Privacy policy (a public URL) | must be publicly reachable | Publish this file (e.g. as a Claude Artifact, or any static host) and paste its public URL into Play Console — see below |

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

## Feature graphic & app icon

`feature-graphic.png` (1024×500) and the app icon (`app/src/main/res/mipmap-*/`,
`app-icon-512.png` below) are both generated programmatically (Pillow/PIL) rather than
hand-designed, matching the TrenDoc brand sheet: a navy card with a glowing cyan document glyph
(folded corner, a "D"-shaped bracket, a dashed connector path with node dots), and the
"Tren" (navy) + "doc" (cyan) wordmark.

- `../tools/gen_app_icon.py` — regenerates every adaptive/legacy mipmap density under
  `app/src/main/res/mipmap-*/` plus `app-icon-512.png` (the 512×512 hi-res icon Play Console
  asks for separately from the APK). Edit the color constants at the top of that file to adjust
  the brand palette, then re-run `python tools/gen_app_icon.py` from the repo root.
- `gen_feature_graphic.py` imports the same glyph/color code from `gen_app_icon.py`, so the icon
  tile shown on the feature graphic always matches the real app icon. Re-run
  `python gen_feature_graphic.py` from this directory after regenerating the icon.

Both require `pip install Pillow`.

## Privacy policy

`privacy-policy.html` is a self-contained, no-build static page — open it directly in a browser,
or publish it anywhere that serves static HTML (a Claude Artifact, GitHub Pages, any static host).
It documents, accurately, what the app actually does as of this commit: SAF-only file access (no
files ever leave the device), what's stored locally (Appearance prefs, the "ads removed" flag),
and the two third-party SDKs involved (Google AdMob, Google Play Billing). **Before publishing
it for real**, replace the placeholder support address in its "Contact" section (search the file
for `[email protected]`) with a real one you monitor — Play Console requires a reachable
contact method on this page, not just an email address in the Console itself.

If the app's actual data practices change later (a new permission, a new SDK, an analytics
addition), update this file's content and its "Effective" date to match — don't let it drift out
of sync with what the app actually does, since that mismatch is exactly what Play Console's
policy review and a real user complaint would both catch.

## Still needed before actually publishing

- A real Play Console developer account and app listing created there (this repo only prepares
  the assets — nothing here uploads them). Set the account's public developer name to
  **TrenBridge IT** — that's the actual "by ___" byline shown under the app name on the Play
  Store listing page itself; the text assets here (full description, privacy policy masthead)
  already say "by TrenBridge IT" to match.
- The real AdMob App ID and ad unit ID, and the real Play Billing product ID for "remove_ads",
  swapping out the test IDs called out in `AndroidManifest.xml` / `AdBanner.kt` /
  `BillingRepository.kt`.
- The privacy policy above, published somewhere public, with its placeholder contact address
  replaced.
- Content rating questionnaire and target audience/data-safety form, filled out in Play Console
  directly — no local asset substitutes for these (though the privacy policy above is what you'll
  be describing accurately in that data-safety form).
