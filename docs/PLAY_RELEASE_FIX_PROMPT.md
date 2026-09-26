# TrenDoc — Play Store Readiness Work Order

> **How to use this file.** This is a self-contained brief. Paste it (or point an agent at it)
> to start work without any prior conversation context. Each task states the problem, the exact
> files, the fix, and how to verify. Work the phases in order — Phase 1 is what blocks the
> upload, Phase 2 is what would tank your Android Vitals after launch.
>
> **Repo:** `pdflite` · **Package:** `com.trendoc.pdflite` · **App name shown to users:** TrenDoc
> **Audit date:** 2026-09-26 · **Audited against:** Google Play policy, Android Vitals, Material 3 a11y

---

## Ground rules for whoever picks this up

- Do **not** commit `keystore.properties`, `trendoc-release.jks`, or `local.properties`. They are
  correctly gitignored today — keep it that way.
- Do **not** put real AdMob unit IDs, API keys, or signing passwords in tracked source. Use
  `BuildConfig` fields sourced from gitignored properties files.
- The app's privacy architecture (SAF-only file access, no broad storage permission, no network
  path for document data, scoped FileProvider) is **correct and worth preserving**. Nothing in
  this document should weaken it.
- After each phase, run `./gradlew assembleDebug` and `./gradlew lint` before moving on.
- Prefer small, reviewable commits — one per numbered task where practical.

---

## Phase 1 — Release blockers (Play will reject or suspend)

### 1.1 Raise `targetSdk` to 36 and upgrade the build toolchain

**Problem.** `app/build.gradle.kts:25` sets `targetSdk = 35`. Play's annual rule requires new apps
and updates to target the API level released within the last year — as of 31 Aug 2026 that is
**API 36 (Android 16)**. The Console will reject the upload.

This is a toolchain upgrade, not a one-line edit:
- `gradle/libs.versions.toml` pins **AGP 8.6.1**, which cannot compile against SDK 36 (needs 8.9+).
- `gradle/wrapper/gradle-wrapper.properties` is already on **Gradle 9.7.1**, which AGP 8.6.1 does
  not officially support. That pairing is overdue regardless.

**Do:**
- [ ] Confirm the current target-API deadline in your own Play Console before choosing the number.
- [ ] Bump AGP to the latest stable 8.9+ (or current stable) in `libs.versions.toml`.
- [ ] Bump Kotlin + the Compose compiler plugin to a version matching that AGP.
- [ ] Set `compileSdk = 36` and `targetSdk = 36` in `app/build.gradle.kts`.
- [ ] Update `buildToolsVersion` to match.
- [ ] Resolve any new deprecation/behaviour-change warnings surfaced by the bump.

**Verify:** `./gradlew clean assembleRelease` succeeds; installed app reports `targetSdk 36`.

---

### 1.2 Make the store listing describe the app that actually exists

**Problem.** `store-listing/full-description.txt` makes four claims the code contradicts. This is
the highest-risk item in this document: misrepresented in-app purchases fall under Play's
**Deceptive Behavior** policy, which gets apps *suspended*, not merely rejected.

| Listing claims | Code actually does |
|---|---|
| "a single **one-time payment**" to remove ads | `PAID_REMOVAL_DURATION_MILLIS = 24h`, a **consumable** that expires — `billing/BillingRepository.kt:175` |
| "One small banner ad **on the home screen only**" | Banner is a sibling of the NavHost → shows on **every screen** — `nav/TrenDocNavHost.kt:184` |
| "**no video ads**" | `billing/RewardedAdRepository.kt` shows a full-screen rewarded video |
| "no 'welcome' screens" | `onboarding/OnboardingScreen.kt` runs on first launch |

The **in-app** copy is already honest (`"Buy — 1 day ad-free"` in `ui/billing/BillingScreen.kt`).
So the default fix is to correct the listing, not the code.

**Do — decide the product question first:**
- [ ] **Decision required:** should "Remove Ads" be a genuine permanent non-consumable unlock, or
      stay a 24-hour consumable? Paying to "Remove Ads" and having them return next day will draw
      refund requests and 1-star reviews even if Play allows it.
  - **If permanent:** change the product to non-consumable, replace `consumeAsync` with
    `acknowledgePurchase`, add a `queryPurchasesAsync` restore on startup, and store a boolean
    entitlement instead of an expiry timestamp.
  - **If staying time-limited (recommended default, lower effort):** rewrite the listing to say so
    plainly — e.g. *"a one-time payment buys a day without the banner; no subscription, no
    auto-renewal."* Never use the bare phrase "one-time payment to remove ads."
- [ ] Rewrite the banner-placement claim to "one small banner, shown on every screen" — or move the
      banner to Home only, so the original claim becomes true.
- [ ] Remove the "no video ads" claim, or remove the rewarded-video feature. Keep one.
- [ ] Remove the "no welcome screens" claim, or remove the onboarding flow. Keep one.
- [ ] Re-read the whole of `full-description.txt` and `short-description.txt` line by line against
      the shipped build. Fix anything else that drifted.

**Verify:** every factual claim in the listing maps to something observable in a release build.

---

### 1.3 Fix the privacy policy and complete Data Safety correctly

**Problem A — the policy states something false.** `store-listing/privacy-policy.html` §3 says
*"TrenDoc requests only two Android permissions"* and *"does not request … camera."* The manifest
declares `CAMERA` at `app/src/main/AndroidManifest.xml:18`. A privacy policy contradicting the
manifest is a direct Data Safety rejection.

**Problem B — the "no identifiers" claim.** Both the policy and the listing claim no device
identifiers are shared. AdMob collects the **Advertising ID**. Your Data Safety form must declare
*"Device or other IDs — collected and shared, for Advertising."* A form/policy mismatch is one of
the most common rejection causes on Play.

**Problem C — the URL may not be live.** `ui/settings/AppearanceScreen.kt:194` points at
`https://trenbridgeit.com/trendoc/privacy-policy.html`, with a code comment saying *"Set once …
is hosted."* Play requires a live, publicly reachable URL.

**Do:**
- [ ] Add a CAMERA row to the policy's §3 permissions table: requested only on "Take Photo" in
      Image(s) → PDF; the photo goes to app-private cache and is never transmitted.
- [ ] Correct §5 to say the banner appears on every screen (or fix the code per 1.2) **and**
      disclose the rewarded video ad.
- [ ] Correct §4: the stored value is an ad-free **expiry timestamp**, not a "has purchased" boolean.
- [ ] Remove or qualify the "no device identifiers shared" claim in both the policy and the listing.
- [ ] Host the policy at the exact URL in `AppearanceScreen.kt:194` and confirm it loads publicly.
- [ ] Fill the Play Data Safety form to match: Device/other IDs → collected + shared → Advertising;
      Purchase history → via Play Billing. Declare that files/documents are **not** collected.

**Verify:** manifest permissions ⊆ policy permission table; Data Safety answers ⊆ policy text.

---

### 1.4 Replace Google's test AdMob IDs with real ones

**Problem.** Three of Google's public test IDs are hardcoded and would ship to production:

| Location | Value |
|---|---|
| `app/src/main/AndroidManifest.xml:37` | App ID `ca-app-pub-3940256099942544~3347511713` |
| `ui/common/AdBanner.kt:15` | Banner unit `…/6300978111` |
| `billing/RewardedAdRepository.kt:17` | Rewarded unit `…/5224354917` |

Shipping these means **zero ad revenue**, and AdMob flags apps serving test IDs in production.

**Do:**
- [ ] Create the real app + ad units in the AdMob console.
- [ ] Add `admob.properties` (gitignored) plus a committed `admob.properties.example`, mirroring the
      existing `keystore.properties` / `keystore.properties.example` pattern.
- [ ] Expose the IDs as `buildConfigField` entries so **debug keeps the test IDs** and **release
      uses the real ones** automatically. Enable `buildFeatures { buildConfig = true }`.
- [ ] Inject the manifest app ID via `manifestPlaceholders` rather than hardcoding it.
- [ ] Delete the `TEST_*` constants from Kotlin source.

**Verify:** a release build contains no `ca-app-pub-3940256099942544` string; a debug build still does.

---

### 1.5 Add a consent flow (UMP / CMP) for EEA + UK users

**Problem.** `TrenDocApplication.kt:22` calls `MobileAds.initialize()` unconditionally, and ads load
with no consent gathered anywhere in the codebase. Since January 2024, Google's EU user consent
policy **requires** a Google-certified CMP before serving personalized ads to EEA/UK users. Serving
without one is an AdMob policy violation.

**Do:**
- [ ] Add `com.google.android.ump:user-messaging-platform` to the version catalog and app deps.
- [ ] Configure a consent form in the AdMob console (AdMob's own CMP is the simplest option).
- [ ] On first launch, request consent info → load and show the form if required → only then call
      `MobileAds.initialize()` and request the first ad.
- [ ] Do not initialize the ads SDK at all while an ad-free window is active.
- [ ] Expose a "Privacy options" / "Manage consent" entry in the Appearance/Settings screen where
      the UMP SDK reports one is required.
- [ ] Test with the UMP debug geography set to EEA.

**Verify:** on a device forced to EEA geography, the consent form appears before any ad request.

---

### 1.6 Verify 16 KB page-size compliance — ✅ DONE (2026-09-26)

**Correction to the original finding.** This task was written on the assumption that
`play-services-ads:23.6.0` ships native libraries and so risked 16 KB non-compliance. That was
wrong: both 23.6.0 and 25.5.0 contain **zero `.so` files**. The app's only native libraries come
from AndroidX (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`), and all four
64-bit variants were **already 16 KB aligned** before any change. There was no compliance gap.

**What was done anyway, and why it still mattered.** The dependency bumps stand on their own
merits — Play enforces an annual minimum Play Billing Library version and 7.1.1 was below it, and
23.6.0 was two major versions behind on security fixes. The bump also forced a round of overdue
toolchain modernization:

| Change | Forced by |
|---|---|
| `play-services-ads` 23.6.0 → 25.5.0 | the task itself |
| `minSdk` 21 → 24 | ads 25.4.0+ hard-requires it (24.0.0–25.3.0 require 23) |
| `kotlin` 2.0.21 → 2.3.21 | ads 25.5.0 ships Kotlin 2.3.0 metadata a 2.0.x compiler cannot read |
| `android { kotlinOptions { } }` → `kotlin { compilerOptions { } }` | Kotlin 2.3 made the old DSL a hard error |
| `billing-ktx` 7.1.1 → 9.1.0 | Play's annual Billing Library floor |
| `queryProductDetailsAsync` call sites in `BillingRepository` + `DonationRepository` | Billing 8 changed the callback's 2nd arg from `List<ProductDetails>` to `QueryProductDetailsResult` |

**minSdk decision.** 24 (Android 7.0) was chosen over 23 so the ads SDK can stay on its current
release rather than being pinned at 25.3.0. Costs roughly the bottom ~1% of the global Android
base. README and TECH_STACK updated to match.

**Verified:** `assembleDebug` and `assembleRelease` both succeed; all 4 arm64/x86_64 `.so` files
report `p_align = 16384`.

**Still to confirm:** no 16 KB warning in the Play Console pre-launch report once uploaded.

---

## Phase 2 — Crash and stability (Android Vitals)

### 2.1 Fix the guaranteed OOM in View PDF

**Problem.** `ui/view/ViewPdfViewModel.kt:109-120` renders **every page** of the document into
`ARGB_8888` bitmaps and holds them all simultaneously in UI state. A US-Letter page renders at
612×792×4 ≈ **1.9 MB**. A 100-page document ≈ **190 MB**; a 300-page manual ≈ 570 MB. That exceeds
the heap on most devices.

Two things make it worse:
- The `catch (e: Exception)` at line 123 **does not catch `OutOfMemoryError`** — it's an `Error`,
  not an `Exception`. So this isn't a handled failure; it's a hard crash.
- Bitmaps are never `recycle()`d when the UI state is replaced.

**Do:**
- [ ] Stop rendering all pages up front. Render lazily, driven by what the `LazyColumn` actually
      has on screen.
- [ ] Hold decoded pages in a bounded `LruCache` sized from `Runtime.getRuntime().maxMemory()`
      (a common baseline is 1/8 of max heap), and `recycle()` on eviction.
- [ ] Keep only a page count + placeholder list in `ViewPdfUiState` — not a `List<Bitmap>`.
- [ ] Add an explicit `catch (e: OutOfMemoryError)` around every render path as a backstop, and
      surface a plain-language message via `util/PdfErrorMessages.kt`.
- [ ] Preserve the existing zoom re-render behaviour in `renderPageAtResolution` — it is already
      correctly capped and short-lived.

**Verify:** open a 500-page PDF on a low-RAM device or emulator (≤2 GB) without crashing; heap
stays flat while scrolling.

---

### 2.2 Apply the same fix to the Split page grid

**Problem.** `ui/split/SplitViewModel.kt:120-133` has the identical shape at 300×400 (~480 KB/page).
Survivable longer, but a 500-page file still reaches ~240 MB.

**Do:**
- [ ] Render thumbnails lazily as grid cells come into view.
- [ ] Use `Bitmap.Config.RGB_565` for thumbnails — halves memory, and PDF thumbnails have no alpha.
- [ ] Recycle on eviction.

---

### 2.3 Guard the unprotected `startActivity`

**Problem.** `ui/settings/AppearanceScreen.kt:183` launches the privacy policy URL with no
`try/catch`. A device with no browser throws `ActivityNotFoundException` → crash.

**Do:**
- [ ] Wrap in `try/catch (ActivityNotFoundException)` and show a snackbar fallback.
- [ ] Audit for any other unguarded `startActivity` / share-intent calls and apply the same guard.

---

### 2.4 Fix the coroutine scope leak and the stuck-ad state

- [ ] `billing/BillingRepository.kt:56` creates a `CoroutineScope` that `close()` (line 169) never
      cancels. Call `scope.cancel()` in `close()`.
- [ ] `billing/RewardedAdRepository.show()` doesn't implement
      `onAdFailedToShowFullScreenContent`. A failed show leaves `rewardedAd` non-null and
      `_isReady` false forever. Implement the callback: null the ad, reload, surface the failure.

---

## Phase 3 — Security hardening

### 3.1 Restrict backup — currently leaks document URIs to Google Drive

**Problem.** `AndroidManifest.xml:24` sets `allowBackup="true"` with **no** `dataExtractionRules`
(required for targetSdk 31+) and no `fullBackupContent`. Result: the ad-free entitlement **and the
Recents list — which contains content URIs of the user's documents** — get backed up off-device.
This directly contradicts the privacy policy's *"None of it is synced to a server."*

**Do:**
- [ ] Add `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml`.
- [ ] Exclude the `entitlement_preferences`, `recents_preferences`, and `files_browser_preferences`
      DataStore files from both cloud backup and device-to-device transfer.
- [ ] Appearance and onboarding preferences are fine to back up — they carry no document references.
- [ ] Wire both via `android:dataExtractionRules` and `android:fullBackupContent`.

**Verify:** `adb shell bmgr backupnow com.trendoc.pdflite`, then confirm the excluded files are absent.

---

### 3.2 Clean up camera captures

**Problem.** `util/CameraCaptureUtils.kt:22` writes photos to `cacheDir/camera_captures/` and
nothing ever deletes them. These are photos of the user's documents accumulating indefinitely.

**Do:**
- [ ] Delete each capture once it has been consumed into the PDF pipeline.
- [ ] Sweep the directory of anything older than ~24h on app start.

---

### 3.3 Tighten the PDF `VIEW` intent filter

**Problem.** `AndroidManifest.xml:57` includes `android.intent.category.BROWSABLE` on a
mimeType-only intent filter with no `<data android:scheme>`. `BROWSABLE` serves no purpose here and
needlessly widens who can hand the app a URI.

**Do:**
- [ ] Remove the `BROWSABLE` category.
- [ ] In `MainActivity.capturePdfViewIntent`, validate the incoming URI's scheme is `content://`
      (or `file://` if you intend to keep supporting it) before storing it in `PendingPdfIntent`.

---

### 3.4 Note: the entitlement trusts the device clock

`billing/EntitlementRepository.kt` uses `System.currentTimeMillis()`. Setting the clock forward,
watching a rewarded video, then setting it back yields a long ad-free window. **Low stakes** — it
only affects ad display — so this is informational, not a required fix. If 1.2 moves Remove Ads to
a permanent non-consumable, the timestamp goes away for the paid path anyway.

---

## Phase 4 — Accessibility

This is the weakest area of the codebase. A grep for `semantics`, `Role.`, `stateDescription`, and
`heading()` across all 9.3k lines returns **zero matches**.

- [ ] **Content descriptions.** 29 of 54 icons pass `contentDescription = null`. Add real labels to
      everything actionable — starting with all four bottom-nav tabs
      (`nav/TrenDocNavHost.kt:221-239`). Genuinely decorative icons may keep `null`; icons that are
      the *only* label for a control may not.
- [ ] **PDF pages are unlabelled.** `ui/view/ViewPdfScreen.kt:315` and `:653` render the document
      with `contentDescription = null`. TalkBack announces nothing for the app's main reading
      surface. At minimum: `"Page 3 of 12"`.
- [ ] **Add `Role.Button` to bare clickables.** 14 sites use `Modifier.clickable` with no role, so
      TalkBack doesn't announce them as actionable. Affected files: `ui/common/GradientButton.kt`,
      `ui/common/ToolUiParts.kt`, `ui/compress/CompressScreen.kt`, `ui/fillforms/FillFormsScreen.kt`,
      `ui/home/HomeScreen.kt`, `ui/settings/AppearanceScreen.kt`, `ui/split/SplitScreen.kt`,
      `ui/view/ViewPdfScreen.kt`.
- [ ] **Touch targets below 48dp.** `ColorDot` is 36dp (`ui/settings/AppearanceScreen.kt:234`). The
      form overlays in `ui/fillforms/FillFormsScreen.kt:299` size themselves from PDF widget
      geometry and are frequently far smaller. Use `Modifier.minimumInteractiveComponentSize()` or
      pad the touch area without changing the visual size. Play's pre-launch report flags both.
- [ ] **Unlabelled text field.** `ui/fillforms/FillFormsScreen.kt:285` uses `BasicTextField` with no
      label or content description — a form field a screen reader cannot identify. Label it from the
      PDF form field's own name.
- [ ] **Selection state isn't exposed.** `ColorDot`, `BackgroundTile`, `LayoutTile`, and the split
      page grid convey selection purely through a border. Add
      `Modifier.semantics { selected = isSelected }`.
- [ ] **Add `heading()`** to section headers so TalkBack users can navigate by heading.

**Verify:** run Accessibility Scanner on every screen; run the Play pre-launch report and confirm
the accessibility section is clean.

---

## Phase 5 — Code quality and polish

### 5.1 Extract all user-facing strings

**Problem.** There are **zero** `stringResource` calls in the codebase. Every string is hardcoded
in Kotlin; `res/values/strings.xml` contains only `app_name`. This blocks localization entirely and
prevents Play's translation tooling from working.

- [ ] Move every user-facing string into `strings.xml` and reference via `stringResource(...)`.
- [ ] Use plurals (`<plurals>`) for page/file counts rather than string concatenation.
- [ ] Typography already uses `sp` via `MaterialTheme`, so font scaling works — leave that alone.

### 5.2 Fix the branding mismatch

`res/values/strings.xml` sets `app_name` to **"PDF Lite Viewer"**, but the package, store listing,
privacy policy, and README all say **TrenDoc**. The launcher label and the Play listing title must
match, or the listing looks like a different app.

- [ ] Decide the real name. Update `app_name`, the README, and the store listing to agree.

### 5.3 Re-enable release lint

`app/build.gradle.kts:72` sets `checkReleaseBuilds = false` because `lintVitalRelease` crashed with
a UAST class-loading error. Understandable, but it disables the gate that would have caught several
items in this document.

- [ ] After the AGP bump (1.1), remove `checkReleaseBuilds = false` and confirm lint runs clean.
- [ ] Fix or explicitly baseline whatever it reports.

### 5.4 Clean up the build file

- [ ] `app/build.gradle.kts:20` — `35.also { compileSdk = it }` is a no-op idiom. Write
      `compileSdk = 36`.
- [ ] `app/build.gradle.kts:96` — `var compileSdkMinor = 0` is dead code. Delete it.

### 5.5 Add tests

**Problem.** JUnit, Turbine, and `kotlinx-coroutines-test` are all declared in
`app/build.gradle.kts`, but there is **no** `src/test` or `src/androidTest` directory. The README's
tech-stack table lists "JUnit + Turbine (unit), Compose UI Testing" — that claim is currently unbacked.

- [ ] Start with the pure logic that's easy to cover and easy to break:
  - `ui/split/SplitViewModel` range parsing (`"1-3, 7, 9-12"`, overlaps, out-of-range, malformed)
  - `recents/RecentsRepository` serialize/parse round-trip, dedupe-on-retouch, 20-entry cap
  - `billing/EntitlementRepository` window stacking (`grantAdFreeFor` from both paths)
  - `util/PdfErrorMessages.forOpenFailure` exception mapping
- [ ] Add Compose UI tests for the main navigation flow once semantics exist (Phase 4 makes this
      much easier — test tags and content descriptions are what UI tests select on).
- [ ] Either add the tests or correct the README. Don't leave the claim standing unbacked.

### 5.6 Introduce dependency injection

Repositories are hand-constructed inside ViewModels (`RecentsRepository(application)`) and in
`remember {}` blocks inside composables. This makes them impossible to substitute in tests. Adopt
Hilt, or at minimum a manual service-locator + ViewModel factory, so Phase 5.5 is actually feasible.

### 5.7 Fix the stale doc comment

`billing/DonationRepository.kt:49` describes Remove Ads as *"single **non-consumable**"*, while
`BillingRepository` correctly treats it as a consumable. Contradictory comments mislead the next
reader. Fix whichever ends up wrong after the 1.2 decision.

### 5.8 Theme and icon polish

- [ ] `res/values/themes.xml` parents `android:Theme.Material.Light.NoActionBar` — not a DayNight
      theme. Dark-mode launches flash white. Use a `DayNight` parent, or add `values-night/`.
- [ ] `res/mipmap-anydpi-v26/ic_launcher.xml` has no `<monochrome>` layer, so Android 13+ themed
      icons fall back. Add one.

### 5.9 Version for release

`app/build.gradle.kts:26-27` is `versionCode = 1`, `versionName = "0.1.0"`. Fine for a first
upload — just make it a deliberate choice rather than a leftover. Consider `1.0.0` for a public
launch, and set up a versioning scheme before the second release.

---

## Completion checklist

**Cannot upload without:**
- [ ] 1.1 targetSdk 36 + toolchain
- [ ] 1.2 store listing matches the app
- [ ] 1.3 privacy policy accurate, hosted, and matching Data Safety
- [ ] 1.4 real AdMob IDs
- [ ] 1.5 UMP consent flow
- [ ] 1.6 16 KB compliance verified

**Should not launch without:**
- [ ] 2.1 / 2.2 OOM fixes
- [ ] 2.3 / 2.4 crash and leak fixes
- [ ] 3.1 backup rules
- [ ] 3.2 camera cache cleanup

**Strongly recommended before launch:**
- [ ] Phase 4 accessibility
- [ ] 5.1 string extraction, 5.2 branding, 5.3 lint

**Post-launch backlog:**
- [ ] 5.5 tests, 5.6 DI, 5.8 polish, 3.3 intent filter, 3.4 clock hardening

---

## Final pre-submission gate

- [ ] `./gradlew clean bundleRelease` succeeds with signing configured.
- [ ] Play Console **pre-launch report** is clean — crashes, accessibility, and performance sections.
- [ ] Data Safety form answers are consistent with both the manifest and the hosted privacy policy.
- [ ] Every claim in the store listing is observable in the release build.
- [ ] Tested on a low-RAM device (≤2 GB) with a large PDF (300+ pages).
- [ ] Tested with TalkBack enabled end to end.
- [ ] Tested with the UMP debug geography forced to EEA.
