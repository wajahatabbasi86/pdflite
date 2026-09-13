# EasyDoc (repo: pdflite)

**Offline Android PDF toolkit — merge, split, compress, convert. No watermarks, no hidden paywalls, no subscriptions.**

## Why this exists

Most free PDF apps on the Play Store bury basic features behind ads, fake trials, and paywalled exports. Research into competitor reviews turned up the same complaints again and again: forced interstitial ads before opening a single document, watermarks on "free" exports, and subscription flows that demand payment info upfront.

EasyDoc is a small, honest alternative: five core PDF tools, fully on-device, one fair price to remove a single non-intrusive ad — nothing else.

## Features (v1)

- 📎 Merge PDFs
- ✂️ Split / extract pages
- 🗜️ Compress PDF
- 🖼️ Image(s) → PDF
- 📄 PDF → Image(s)

## Positioning

- **Offline-first** — all processing happens on-device, no files ever leave the phone
- **No watermark** on any output, free or paid
- **No account/sign-up** required
- **One banner ad, no interstitials** — a single optional one-time "Remove Ads" purchase, no subscriptions
- **No hidden trials** — pricing shown upfront

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose (Material 3) |
| Architecture | MVVM — `ViewModel` + `StateFlow`/`Flow` |
| Navigation | Compose Navigation (`androidx.navigation.compose`), single-activity |
| Async | Kotlin Coroutines |
| Build config | Gradle Kotlin DSL + version catalog (`libs.versions.toml`) |
| PDF rendering (previews/thumbnails) | Android `PdfRenderer` (built-in) |
| PDF manipulation (merge/split/compress/create) | [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0) |
| File access | Storage Access Framework (SAF), via a shared `SafFileUtils` wrapper |
| Image handling | Android `BitmapFactory`/`Bitmap` (built-in) |
| Ads | Google AdMob (single banner placement) |
| Payments | Google Play Billing (one-time IAP) |
| Testing | JUnit + Turbine (unit), Compose UI Testing (UI, optional for v1) |
| Min SDK | API 21 (Android 5.0) |
| IDE | Android Studio (Quail 4 / 2026.1.4 or later stable) |

Full architecture rationale is in [`docs/TECH_STACK.md`](docs/TECH_STACK.md).

## Project Status

🚧 In development — **4 of 9 build-order steps complete** (see below). See the
[`easydoc` GitHub Project board](../../projects) for granular task tracking.

Full requirements and screen-by-screen flows are documented in [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md).

### Done
- ✅ Step 1 — Project setup, SAF file picker, `PdfRenderer` thumbnail viewer
- ✅ Step 2 — Home screen + navigation
- ✅ Step 3 — Merge PDFs (multi-select, reorder, thumbnails, corrupted/password-protected
  file detection, save via SAF)
- ✅ Step 4 — Split / Extract pages (page selection grid, extract-selected and
  split-by-ranges modes, inline range validation)
- ✅ Supporting infrastructure: shared `ResultScreen` (Open/Share/Done), `EasyDocApplication`
  (PdfBox-Android resource loader init), launcher icon, `gradle.properties` JVM heap config

### Remaining
- ⬜ Step 5 — Image(s) → PDF
- ⬜ Step 6 — PDF → Image(s)
- ⬜ Step 7 — Compress PDF
- ⬜ Step 8 — Monetization (AdMob banner + Play Billing IAP)
- ⬜ Step 9 — Polish, error handling, Play Console listing

### Documented, not yet built
Two additional features have full requirements written in `docs/REQUIREMENTS.md` but are
explicitly deferred — not part of the build order above unless prioritized in:
- §9 — OCR / Searchable PDF (Latin script only, ML Kit's bundled/offline model)
- §10 — Fill Existing PDF Forms (AcroForm fields via PdfBox-Android)

## Build Order

1. ✅ Project setup, SAF file picker, PdfRenderer thumbnail viewer
2. ✅ Home screen + navigation
3. ✅ Merge PDFs
4. ✅ Split / Extract pages
5. ⬜ Image(s) → PDF
6. ⬜ PDF → Image(s)
7. ⬜ Compress PDF
8. ⬜ Monetization (AdMob banner + Play Billing IAP)
9. ⬜ Polish, error handling, Play Console listing

## License

Apache License 2.0 — see [LICENSE](LICENSE)
