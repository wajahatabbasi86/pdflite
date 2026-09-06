# EasyDoc (pdflite) — Requirements

This document defines screen-by-screen flows for v1. It expands on the feature list in the root `README.md`.

---

## 1. Global / Shared Behavior

### 1.1 File Access
- All file selection uses the Storage Access Framework (SAF) — no raw file path access, no broad storage permission requests.
- Input: `ACTION_OPEN_DOCUMENT` (supports multi-select where relevant — Merge, Image→PDF).
- Output: `ACTION_CREATE_DOCUMENT` for saving results, defaulting to a sensible filename (e.g. `merged.pdf`, `compressed_<original>.pdf`).
- No file ever leaves the device. No network calls related to file content.

### 1.2 Processing State
- All PDF operations (merge/split/compress/convert) run on a background thread/coroutine — never block the UI thread.
- Every operation screen shows a progress indicator (indeterminate unless page-count-based progress is trivial to compute, e.g. compress/split).
- On success: show a result screen with output filename, "Open," "Share," and "Done" (return to Home) actions.
- On failure: show a plain-language error (see 1.4) with a "Try Again" action; never a raw stack trace or exception message.

### 1.3 Navigation
- Bottom-level structure is flat: Home → Tool Screen → Result Screen. No deep nesting.
- Back button from any Tool Screen returns to Home without prompting, unless a file is mid-processing (then confirm cancel).

### 1.4 Error Handling (shared error states)
- **Invalid/corrupted PDF**: "This file couldn't be read. It may be corrupted or password-protected."
- **Password-protected PDF**: detect via PdfBox exception type; explicit message: "This PDF is password-protected. Remove the password and try again." (No password-entry flow in v1.)
- **Storage full / write failure**: "Not enough space to save this file."
- **No file selected**: primary action stays disabled until a valid selection is made (prevents empty-state errors entirely).

### 1.5 Monetization Touchpoints
- Single AdMob banner, persistent at the bottom of the Home screen only (not on tool/processing screens, to avoid interrupting a task in progress).
- "Remove Ads" entry point: a small persistent link/button on Home (e.g. top app bar action), not a modal interruption.
- No interstitials anywhere in the app, at any screen transition.

---

## 2. Home Screen

**Purpose:** entry point; lets the user pick a tool.

**Layout:**
- Grid or list of 5 tool cards: Merge, Split, Compress, Image→PDF, PDF→Image. Each card: icon + label + one-line description.
- Top app bar: app name, "Remove Ads" action (hidden/replaced with a subtle "Ad-free" indicator if already purchased).
- AdMob banner pinned to bottom (hidden if ads removed).

**Actions:**
- Tap a tool card → navigate to that tool's screen.
- Tap "Remove Ads" → Billing screen/dialog (see §7).

**Empty/first-run state:** no special empty state needed; all 5 tools always visible.

---

## 3. Merge PDFs

**Flow:**
1. **Select Files screen** — SAF multi-select (`ACTION_OPEN_DOCUMENT` with `EXTRA_ALLOW_MULTIPLE`), filtered to `application/pdf`.
2. Selected files display as a reorderable list (drag handles) showing filename + page count + thumbnail (via `PdfRenderer`).
3. User can remove a file from the list or add more (re-invoke picker).
4. Minimum 2 files required to enable "Merge" action.
5. Tap "Merge" → processing state (§1.2) → PdfBox-Android appends documents in list order → SAF save dialog → Result screen.

**Edge cases:**
- Reordering must update the in-memory list before merge executes.
- If any selected file fails to open (corrupted/password-protected), flag that specific item in the list with an inline error icon and exclude it from merge, rather than failing the whole operation.

---

## 4. Split / Extract Pages

**Flow:**
1. **Select File screen** — SAF single-select, `application/pdf`.
2. **Page Selection screen** — thumbnail grid of all pages (via `PdfRenderer`), each tappable/selectable (checkbox overlay).
3. Two modes, toggled at top of screen:
   - **Extract selected pages** → produces one new PDF containing only checked pages, in original order.
   - **Split into ranges** → text input for page ranges (e.g. `1-3, 5, 7-9`) → produces one PDF per range.
4. Tap "Extract"/"Split" → processing state → PdfBox-Android builds output document(s) → SAF save (single save dialog for extract; a save-location + auto-named files for split-into-multiple).

**Edge cases:**
- Range input validation: reject out-of-bounds or malformed ranges inline before enabling the action button.
- Large page-count documents: render thumbnails lazily (paged/virtualized grid) to avoid memory issues.

---

## 5. Compress PDF

**Flow:**
1. **Select File screen** — SAF single-select, `application/pdf`. Show original file size immediately after selection.
2. **Compression Level screen** — simple choice: Low / Medium / High compression (maps to internal image-downsampling / quality parameters applied via PdfBox-Android when re-encoding embedded images).
3. Tap "Compress" → processing state → SAF save dialog → Result screen shows before/after file size and % reduction.

**Edge cases:**
- If the PDF has no compressible content (e.g. already text-only, no images), show resulting size honestly even if reduction is minimal — never fabricate a reduction percentage.
- Very large files: show a warning if file size exceeds a threshold (e.g. >100MB) that processing may take longer, before starting.

---

## 6. Image(s) ↔ PDF

### 6.1 Image(s) → PDF
1. **Select Images screen** — SAF multi-select, `image/*` (jpg/png).
2. Selected images shown as a reorderable thumbnail list (same drag-to-reorder pattern as Merge).
3. Tap "Create PDF" → processing state → each image placed on its own page (page size fit to image aspect ratio, or a fixed page size — decide default: fit-to-image for v1) → SAF save → Result screen.

### 6.2 PDF → Image(s)
1. **Select File screen** — SAF single-select, `application/pdf`.
2. **Options**: output format (JPG/PNG) and resolution/quality preset (Low/Medium/High), plus optional page range (default: all pages).
3. Tap "Convert" → processing state → renders each selected page via `PdfRenderer` to a bitmap → saves as individual image files to a user-chosen SAF directory → Result screen lists output files with a "Share All" action.

**Edge cases:**
- PDF→Image on multi-page documents needs per-page progress (e.g. "Converting page 3 of 12") since this can take noticeably longer than a single-shot operation.

---

## 7. Monetization (AdMob + Play Billing)

**Ad banner:**
- Loads on Home screen only; standard AdMob banner unit, no interstitial or rewarded ad units in v1.
- If the "Remove Ads" purchase is active, banner view is removed entirely (not just hidden) and ad SDK init for banners is skipped.

**Remove Ads purchase flow:**
1. Tap "Remove Ads" from Home top app bar.
2. Simple screen/dialog: price (fetched live from Play Billing), one-line benefit ("Remove the banner ad — one-time purchase, no subscription"), "Buy" button.
3. On successful purchase: acknowledge purchase per Play Billing library requirements, persist entitlement locally, remove banner immediately, dismiss screen.
4. "Restore Purchases" action available (for reinstall/new device cases) — re-queries Play Billing purchase history.

**Edge cases:**
- Billing unavailable (no Play Store account, offline): show a plain message, don't crash; ads remain shown until purchase can complete.

---

## 8. Out of Scope for v1

- Password removal/addition on PDFs.
- OCR / text recognition.
- Cloud storage integration (Drive, Dropbox, etc.) — SAF covers local + any SAF-exposed provider, but no dedicated cloud UI.
- Annotation, form-filling, or editing existing PDF content.
- Dark mode is not required for v1 but should not be actively broken if the system theme is dark (Compose default theming should handle this gracefully).
