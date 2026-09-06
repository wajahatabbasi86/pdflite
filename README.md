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
| UI | Jetpack Compose |
| PDF rendering (previews/thumbnails) | Android `PdfRenderer` (built-in) |
| PDF manipulation (merge/split/compress/create) | [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0) |
| File access | Storage Access Framework (SAF) |
| Ads | Google AdMob (single banner placement) |
| Payments | Google Play Billing (one-time IAP) |
| Min SDK | API 21 (Android 5.0) |

## Project Status

🚧 In development — see the [`easydoc` GitHub Project board](../../projects) for current build progress.

Full requirements and screen-by-screen flows are documented in [`docs/REQUIREMENTS.md`](docs/REQUIREMENTS.md).

## Build Order

1. Project setup, SAF file picker, PdfRenderer thumbnail viewer
2. Home screen + navigation
3. Merge PDFs
4. Split / Extract pages
5. Image(s) → PDF
6. PDF → Image(s)
7. Compress PDF
8. Monetization (AdMob banner + Play Billing IAP)
9. Polish, error handling, Play Console listing

## License

Apache License 2.0 — see [LICENSE](LICENSE)
