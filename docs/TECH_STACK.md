# TrenDoc (pdflite) — Tech Stack & Architecture Decisions

This document expands on the summary table in the root `README.md`. It records *why* each
choice was made, not just what was chosen, so future contributors (or future us) don't
re-litigate these decisions per screen.

---

## Core

| Layer | Choice | Rationale |
|---|---|---|
| Language | **Kotlin** | Repo baseline. |
| UI toolkit | **Jetpack Compose (Material 3)** | Repo baseline. M3 gives dark-mode theming largely for free, satisfying the "should not be actively broken" requirement in `REQUIREMENTS.md` §8 without extra work. |
| Architecture | **MVVM** — `ViewModel` + `StateFlow`/`Flow` | Every tool screen (Merge, Split, Compress, Convert) follows the same pick-file → background-process → progress → result lifecycle described in `REQUIREMENTS.md` §1.2. Standardizing on one ViewModel pattern now avoids five slightly-different implementations of the same state machine. |
| Navigation | **Compose Navigation** (`androidx.navigation.compose`), single-activity | `REQUIREMENTS.md` §1.3 specifies a flat Home → Tool → Result structure with no deep nesting — Compose Navigation maps directly onto that with a single `NavHost`. |
| Async | **Kotlin Coroutines** | Required so PDF operations never block the UI thread (§1.2). |
| Build config | **Gradle Kotlin DSL** + version catalog (`libs.versions.toml`) | Centralizes dependency versions as PdfBox-Android, AdMob, Billing, and Navigation are added across the 9-step build order in the README. |

## PDF & file handling

| Layer | Choice | Rationale |
|---|---|---|
| PDF manipulation (merge/split/compress/create) | **PdfBox-Android** (Apache 2.0) | Repo baseline. |
| PDF preview/thumbnails | **Android `PdfRenderer`** (built-in) | Repo baseline; no extra dependency needed. |
| File access | **Storage Access Framework (SAF)**, via a shared `SafFileUtils` wrapper | `REQUIREMENTS.md` §1.1 mandates SAF-only access (no raw paths, no broad storage permission). A single wrapper around `ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` is reused by all five tool screens instead of each screen inlining intent code. |
| Image handling (Image↔PDF) | **Android `BitmapFactory`/`Bitmap`** (built-in) | This is local file I/O, not network image loading — no need for Coil/Glide. |

## Monetization (build step 8)

| Layer | Choice |
|---|---|
| Ads | Google AdMob — single banner unit, Home screen only, no interstitials (`REQUIREMENTS.md` §7) |
| Payments | Google Play Billing Library — one-time IAP for "Remove Ads", with restore-purchases support |

## Testing

| Layer | Choice | Rationale |
|---|---|---|
| Unit tests | **JUnit + Turbine** (for `Flow` testing) | Cheap to wire in at the start of Step 1; painful to retrofit onto ViewModels once five of them exist. |
| UI tests | **Compose UI Testing** (`androidx.compose.ui.test`) | Optional for v1, but the scaffold is UI-test-friendly from the first screen if we choose to add coverage later. |

## Environment

| Item | Choice |
|---|---|
| IDE | Android Studio (built on IntelliJ Platform — same keymap/theme importable from IntelliJ IDEA) |
| Min SDK | API 21 (Android 5.0), per README |
| Target/Compile SDK | Latest stable at time of each dependency bump |
| Package name | `com.trendoc.pdflite` |

---

## Planned, Not Yet Added

| Layer | Choice (when built) | Rationale |
|---|---|---|
| OCR (see `REQUIREMENTS.md` §9) | **ML Kit Text Recognition v2 — bundled model** (`com.google.mlkit:text-recognition`), Latin script only | The bundled variant requires zero network access ever, matching the offline-first claim; the unbundled variant is smaller at install but needs one download before first use. Non-Latin scripts only exist as unbundled models, so they're excluded from this scope rather than compromising the offline guarantee for everyone. Not added to `libs.versions.toml` or any build file — this is a decision record only, until the feature is actually prioritized. |
| Form filling (see `REQUIREMENTS.md` §10) | **PdfBox-Android's existing `PDAcroForm`/`PDField` APIs** — no new dependency | Already in the project (§ PDF manipulation above); no reason to add another library for structured form-field reading/writing. Scoped deliberately to AcroForm fields only, not arbitrary text editing, since no library (PdfBox-Android or otherwise) does reliable content-stream text reflow — that's a much harder, unsolved-in-general problem, not a dependency choice. |

## Notes

- None of the above overrides anything in the root `README.md` or `REQUIREMENTS.md` — this
  document only fills in implementation-level decisions those files leave unspecified
  (e.g. *which* architecture pattern satisfies "background thread/coroutine" in §1.2).
- If a decision here needs to change, update both this file and the summary table in
  `README.md` so they don't drift.
