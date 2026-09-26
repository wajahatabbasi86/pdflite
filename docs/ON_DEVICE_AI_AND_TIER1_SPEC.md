# TrenDoc — On-Device AI & Tier-1 Strategic Features Specification

**Document Version:** 1.0  
**Target Application:** TrenDoc (`com.trendoc.pdflite`)  
**Core Guarantee:** 100% On-Device Processing • Zero Third-Party Cloud APIs • Zero Data Egress • No Forced Subscriptions • No Watermarks

---

## Table of Contents
1. [Architectural Principles & Privacy Moat](#1-architectural-principles--privacy-moat)
2. [Feature 1: True Black-Out PDF Redaction & Smart PII Auto-Detection](#2-feature-1-true-black-out-pdf-redaction--smart-pii-auto-detection)
3. [Feature 2: Quick Sign & Local Date Stamp](#3-feature-2-quick-sign--local-date-stamp)
4. [Feature 3: 100% Offline Neural OCR & Searchable PDF (Requirements §9 Implementation)](#4-feature-3-100-offline-neural-ocr--searchable-pdf-requirements-9-implementation)
5. [Feature 4: Target File Size PDF Compression (USCIS / IRS / Court Presets)](#5-feature-4-target-file-size-pdf-compression-uscis--irs--court-presets)
6. [Feature 5: AES-256 PDF Encryption & Permission Locking](#6-feature-5-aes-256-pdf-encryption--permission-locking)
7. [Feature 6: Computer Vision Document Deskew, Corner Detection & B&W Filter](#7-feature-6-computer-vision-document-deskew-corner-detection--bw-filter)
8. [Feature 7: AI Form Auto-Fill via Local Encrypted User Profile](#8-feature-7-ai-form-auto-fill-via-local-encrypted-user-profile)
9. [Feature 8: Local On-Device LLM & Summarization ("Chat with PDF" via MediaPipe GenAI)](#9-feature-8-local-on-device-llm--summarization-chat-with-pdf-via-mediapipe-genai)
10. [Feature 9: AI PDF Generation & Branding Pipeline (Cover Pages, Seals & Letterheads)](#10-feature-9-ai-pdf-generation--branding-pipeline-cover-pages-seals--letterheads)
11. [Gradle Dependency Catalog Additions (`libs.versions.toml`)](#11-gradle-dependency-catalog-additions-libsversionstoml)
12. [Tier-1 Market Monetization & Packaging Strategy](#12-tier-1-market-monetization--packaging-strategy)

---

## 1. Architectural Principles & Privacy Moat

TrenDoc’s primary commercial advantage in Tier-1 countries (USA, UK, Canada, Germany, Australia, EU) is **cryptographic and technical privacy**:
- **Zero Network Traffic for Document Data**: No file, page, image, text snippet, or metadata ever leaves the device.
- **$0.00 Ongoing API/Token Costs**: Zero dependency on OpenAI, Anthropic, AWS, or custom cloud backends.
- **Enterprise & Regulatory Alignment**: Works seamlessly under **HIPAA** (healthcare), **GDPR** (EU privacy), and attorney-client / CPA confidentiality standards.
- **Airplane-Mode Operability**: Every feature functions without an active internet connection.

---

## 2. Feature 1: True Black-Out PDF Redaction & Smart PII Auto-Detection

### 2.1 The Problem
Amateur PDF apps draw black rectangles as image overlays on top of text. Anyone opening the file can still drag to select, copy, or search the sensitive characters beneath. In legal, tax, and medical environments, this constitutes a serious data breach.

### 2.2 Functional Specification
1. **Interactive UI**:
   - The user selects a document and views pages via `PdfRenderer`.
   - A redaction brush allows the user to drag bounding boxes over sensitive regions.
2. **AI Smart PII Detection (One-Tap)**:
   - Evaluates page text on-device using regex patterns and Google ML Kit Entity Extraction.
   - Automatically flags:
     - US Social Security Numbers (`\b\d{3}-\d{2}-\d{4}\b`)
     - Employer Identification Numbers (`\b\d{2}-\d{7}\b`)
     - Credit card numbers (Luhn-validated 13–16 digits)
     - Bank routing/account numbers
     - Email addresses and phone numbers
   - Renders red highlight boxes with a "Redact All" or individual "Approve" button.
3. **Cryptographic Sanitization (PdfBox-Android)**:
   - For each target bounding box on a page:
     - Inspects the underlying content stream (`PDPage.getContentStreams()`).
     - Parses tokens: locates `BT` (Begin Text) to `ET` (End Text) blocks.
     - Strips out `Tj`, `TJ`, and glyph positioning operators whose coordinate bounds intersect the redaction rectangle.
     - Draws a solid black rectangle (`0 0 0 rg`, `re`, `f`) permanently into the stream.
   - Clears document metadata dictionaries (Author, Title, Keywords, XMP packet) that might leak redacted text.

### 2.3 Key Classes to Add
- `ui/redact/RedactScreen.kt` (Compose touch overlay)
- `ui/redact/RedactViewModel.kt` (StateFlow, page bitmap rendering)
- `util/PdfSanitizer.kt` (COS stream token parser & glyph stripper)
- `util/PiiDetector.kt` (Regex + ML Kit Entity Extraction)

---

## 3. Feature 2: Quick Sign & Local Date Stamp

### 3.1 The Problem
DocuSign and Adobe Sign force users into recurring accounts ($15/mo) and multi-step verification flows just to place a simple signature on a permission slip, rental agreement, or invoice.

### 3.2 Functional Specification
1. **Signature Creation & Local Vault**:
   - Finger/stylus drawing canvas in Jetpack Compose (`drawPath` with smoothed Bézier curves).
   - Stored locally as a transparent PNG in private app storage (`context.filesDir/signatures/sig_default.png`).
   - Supports saving 1 primary signature and 1 initials stamp.
2. **Document Placement Flow**:
   - Tap "Place Signature" or "Date Stamp".
   - Signature appears as a movable, resizable, rotatable overlay on the current page.
   - "Date Stamp" automatically formats the current localized date (e.g. `Sep 26, 2026` or `26/09/2026`).
3. **PDF Flattening**:
   - Uses `PDPageContentStream(doc, page, AppendMode.APPEND, true, true)`.
   - Converts the signature bitmap into a `PDImageXObject` and stamps it at the normalized PDF coordinate `(x, y, width, height)`.
   - Flattens the content so the signature cannot be detached or extracted as an independent object.

### 3.3 Key Classes to Add
- `ui/sign/SignaturePadDialog.kt`
- `ui/sign/SignPdfScreen.kt`
- `ui/sign/SignPdfViewModel.kt`

---

## 4. Feature 3: 100% Offline Neural OCR & Searchable PDF (Requirements §9 Implementation)

### 4.1 The Problem
Millions of mobile documents are photos of physical paper (receipts, utility bills, letters). They are inert image bitmaps: text cannot be highlighted, copied, or indexed by OS-level search (Spotlight, Windows Search, Android search).

### 4.2 Functional Specification
1. **Model Selection**:
   - Google ML Kit Text Recognition v2 **bundled model** (`com.google.mlkit:text-recognition`).
   - Bundled directly inside APK assets (~18MB uncompressed, ~8MB compressed).
   - Operates on Latin alphabet (English, Spanish, French, German, Italian, Portuguese).
   - Zero network permissions requested or utilized.
2. **Processing Pipeline**:
   - Read PDF page bitmap via `PdfRenderer` at 300 DPI (`RENDER_MODE_FOR_PRINT`).
   - Pass `InputImage.fromBitmap(bitmap, 0)` into `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)`.
   - Extract recognized `TextBlock` -> `Line` -> `Element` with exact pixel bounding boxes.
3. **Invisible Text Layer Injection (PdfBox-Android)**:
   - Open target document with `PDDocument`.
   - For each recognized text element:
     - Map view coordinates `(left, top, right, bottom)` to PDF page points (`72 DPI` coordinate space, Y-inverted).
     - Begin text object: `PDPageContentStream.beginText()`.
     - Set text rendering mode to **Invisible / Invisible Mode 3** (`3 Tr`).
     - Set font (Standard 14 `PDType1Font.HELVETICA`).
     - Position text using `newLineAtOffset(x, y)` and write glyphs using `showText(element.text)`.
     - End text object: `PDPageContentStream.endText()`.
4. **Output**:
   - Visually identical to the original scanned image.
   - Text is fully selectable, copyable, and searchable via `Ctrl+F` in any PDF reader.

### 4.3 Key Classes to Add
- `ui/ocr/OcrScreen.kt`
- `ui/ocr/OcrViewModel.kt`
- `ocr/PdfOcrEngine.kt` (Coordinates transformer and invisible layer injector)

---

## 5. Feature 4: Target File Size PDF Compression (USCIS / IRS / Court Presets)

### 5.1 The Problem
Government and corporate portals enforce strict file size upload ceilings:
- **USCIS (US Citizenship & Immigration)**: Max 6MB or 12MB.
- **IRS e-File / State Tax Portals**: Max 5MB.
- **Courts (PACER / State E-Filing)**: Max 2MB to 10MB.
- **Email Providers**: Max 25MB attachment limit.
Users currently have to guess with vague "Low/Medium/High" quality sliders.

### 5.2 Functional Specification
1. **Target Selector UI**:
   - Quick preset chips:
     - `Email (Under 2 MB)`
     - `Gov & Tax Portals (Under 5 MB)`
     - `Court E-Filing (Under 10 MB)`
     - `Custom Target (Enter MB/KB)`
2. **Adaptive Binary-Search Compression Algorithm**:
   - Analyze original PDF stream: compute total byte size of embedded `COSName.XOBJECT` images vs. text/vector objects.
   - If image payload is the dominant size:
     - Run a bounded binary search (up to 4 passes in memory) adjusting JPEG quality (between 25% and 85%) and downscale resolution factor (between 0.5x and 1.0x).
     - Re-encode images using `JPEGFactory.createFromImage`.
   - Guarantees the resulting file fits within `targetBytes ± 3%` without over-compressing.
3. **Honest Feedback**:
   - If the file cannot reach the target (e.g. pure vector content with millions of paths), clearly display: *"Document text and paths already optimal. Minimum achievable size: X.X MB"*.

### 5.3 Key Classes to Add
- Enhance `ui/compress/CompressScreen.kt` with Preset Target Chips.
- Enhance `ui/compress/CompressViewModel.kt` with binary search optimizer `compressToTargetSize(targetBytes: Long)`.

---

## 6. Feature 5: AES-256 PDF Encryption & Permission Locking

### 6.1 The Problem
CPAs, realtors, HR managers, and attorneys routinely email documents containing banking details, social security numbers, and salary information. Sending unencrypted PDFs violates baseline fiduciary duty and GDPR/HIPAA standards.

### 6.2 Functional Specification
1. **User Interface**:
   - Input fields:
     - **Open Password** (required to view the PDF).
     - **Owner / Permissions Password** (required to change permissions or remove security).
   - Permission Toggles:
     - Allow Printing (High Res / Low Res / Disallow)
     - Allow Copying Text & Graphics (Yes / No)
     - Allow Form Filling (Yes / No)
     - Allow Annotations (Yes / No)
2. **Cryptographic Engine (PdfBox-Android + Bouncy Castle)**:
   - Configure `StandardProtectionPolicy`:
     ```kotlin
     val policy = StandardProtectionPolicy(ownerPassword, userPassword, accessPermissions).apply {
         encryptionKeyLength = 256 // AES-256 (PDF 2.0 / Acrobat X standard)
         preferAES = true
     }
     document.protect(policy)
     ```
   - Persist to SAF with `.pdf` extension.

### 6.3 Key Classes to Add
- `ui/encrypt/EncryptPdfScreen.kt`
- `ui/encrypt/EncryptPdfViewModel.kt`

---

## 7. Feature 6: Computer Vision Document Deskew, Corner Detection & B&W Filter

### 7.1 The Problem
When users photograph paperwork on a desk, the result usually has tilted perspective, uneven room shadows, and yellow incandescent lighting, which looks unprofessional when submitted for official purposes.

### 7.2 Functional Specification
1. **Implementation Route A (Recommended — Native Zero-Setup)**:
   - Utilize Android’s **Play Services Document Scanner API** (`com.google.android.gms:play-services-mlkit-document-scanner`).
   - The OS renders Google’s official system document camera UI.
   - Automatically handles:
     - Real-time page boundary detection.
     - Automatic quad warping / perspective deskewing.
     - Shadow removal and clean monochromatic black-and-white thresholding.
   - Returns clean JPEG URIs or a compiled PDF directly back to TrenDoc via an `ActivityResultContract`.
2. **Implementation Route B (Pure Standalone In-App)**:
   - Capture photo with existing `CameraCaptureUtils.kt`.
   - Apply on-device OpenCV / RenderScript thresholding:
     - Grayscale conversion -> Gaussian blur -> Adaptive Otsu thresholding -> High-contrast monochrome document scan.

---

## 8. Feature 7: AI Form Auto-Fill via Local Encrypted User Profile

### 8.1 The Problem
Filling out IRS forms, state DMV documents, or rental applications on mobile requires repeatedly typing full legal name, date of birth, SSN, street address, and phone number across dozens of individual form fields.

### 8.2 Functional Specification
1. **Encrypted Local Profile Storage**:
   - Store user profile data in Android Jetpack Security (`EncryptedSharedPreferences` backed by Android KeyStore hardware master key).
   - Fields: First Name, Last Name, Street Address, City, State, ZIP, Phone, Email, SSN/Tax ID.
2. **Semantic Field Matching Engine**:
   - When an AcroForm document is loaded (`FillFormsViewModel.kt`), inspect each `PDField`:
     - Inspect field name (`field.fullyQualifiedName`) and alternative tooltip text (`field.alternateFieldName`).
     - Run semantic token normalization (lowercasing, underscore removal, abbreviation expansion).
     - Match against profile dictionary:
       - `fname`, `first_name`, `given_name`, `applicant_first` -> Profile First Name
       - `lname`, `last_name`, `surname`, `family_name` -> Profile Last Name
       - `ssn`, `social_security`, `tax_id`, `ein` -> Profile Tax ID
       - `addr`, `street_address`, `residence_line1` -> Profile Address
3. **One-Tap Execution**:
   - Surface banner: *"Detected 14 autofillable fields. [Auto-Fill Form]"*.
   - User reviews pre-filled values in native Compose UI before exporting.

---

## 9. Feature 8: Local On-Device LLM & Summarization ("Chat with PDF" via MediaPipe GenAI)

### 9.1 The Problem
Users want to ask questions like *"What is the notice period in Section 12?"* or *"Summarize the payment obligations in 3 bullets"*. Standard apps send documents to OpenAI servers, risking confidentiality.

### 9.2 Functional Specification
1. **Runtime Framework**:
   - **Google MediaPipe GenAI Task** (`com.google.mediapipe:tasks-genai`).
   - Direct on-device inference using the phone's GPU/NPU.
2. **Model Selection**:
   - **Gemma-2-2B-IT** or **Qwen2.5-1.5B-Instruct** (4-bit quantized `.bin` / `.task` format).
   - Model size: ~1.2 GB to 1.5 GB.
   - Stored in app internal storage upon one-time user opt-in download.
3. **Document Context Injection**:
   - Extract plain text from PDF using PdfBox `PDFTextStripper`.
   - Truncate / chunk to fit the model's 2,048-token context window.
   - Run prompt template:
     ```
     You are a private document assistant. Answer the user question based strictly on the text provided below.
     --- DOCUMENT ---
     {extracted_text}
     --- QUESTION ---
     {user_question}
     ```
4. **Performance & Requirements**:
   - Target devices: Android 10+ (API 29+), minimum 6GB RAM.
   - Tokens per second: 12–25 tok/sec on modern Snapdragon / Tensor / Dimensity SoCs.

---

## 10. Feature 9: AI PDF Generation & Branding Pipeline (Cover Pages, Seals & Letterheads)

### 10.1 The Problem & Product Alignment
Small businesses, independent consultants, and freelancers frequently need to convert photos, estimates, and receipts into formal, branded client deliverables. However, compiled camera scans look raw and lack executive polish:
- No formal cover page or presentation title sheet.
- No corporate letterhead branding or watermark.
- Missing authentic approval badges, stamps, or official seals.

Rather than cluttering the app with general-purpose text-to-image tools (e.g. generating unrelated fantasy art), this feature scopes generative image capabilities **strictly to PDF creation and brand elevation workflows**.

### 10.2 Functional Specification
1. **Integration into Step 5 (`Image(s) → PDF`) and Document Creation**:
   - Inside `ImageToPdfScreen.kt`, surface an optional action toolbar:
     - **"Generate Branded Cover Page"**
     - **"Create Custom Document Stamp / Seal"**
     - **"Generate Custom Letterhead Header"**
2. **AI Generation Workflows (Opt-In Cloud Module)**:
   - **Cover Page Generator**:
     - Input: Document Title, Subtitle/Company, Color Theme / Style prompt (e.g., *"Executive navy blue geometric borders with gold accent"*).
     - Output: High-resolution vertical page (`3:4` or `A4` aspect ratio).
     - Placed automatically as **Page 1** of the compiled PDF.
   - **Transparent Corporate Seal / Stamp**:
     - Prompt: *"Circular crimson corporate approval seal with text 'VERIFIED & APPROVED' and date field"*.
     - Generates clean circular graphic, auto-removes white background to create transparent PNG, and caches it in local signatures/stamps vault.
   - **Generative Scan Cleanup & Degraded Image Restoration**:
     - Text-guided image inpainting: remove coffee cup rings, ink smudges, or camera finger shadows from an invoice image prior to compilation into PDF.
3. **Privacy & Architecture Boundary**:
   - Because image generation requires foundation models (e.g. Gemini image generation / Imagen), this feature is marked as an **Opt-In Cloud Asset Studio**:
     - **Clear User Disclosure**: *"Generating branded covers or stamps uses Google AI services. Your local PDF documents and private data are NEVER uploaded."*
     - The output asset is downloaded locally and embedded into the PDF entirely on-device using PdfBox-Android (`PDImageXObject` + `PDPageContentStream`).

### 10.3 Key Classes to Add
- `ui/branding/AiAssetStudioDialog.kt` (Prompt input, aspect ratio picker, preview)
- `ui/branding/AiAssetStudioViewModel.kt` (StateFlow, API client with proxy/key injection)
- `branding/PdfWatermarkEngine.kt` (PdfBox overlay stamping for headers and watermarks)

---

## 11. Gradle Dependency Catalog Additions (`gradle/libs.versions.toml`)

To add these capabilities to TrenDoc without third-party cloud APIs, declare these official, on-device libraries:

```toml
[versions]
mlkit-text-recognition = "16.0.1"
mlkit-entity-extraction = "16.0.0-beta5"
play-services-scanner = "16.0.0-alpha03"
mediapipe-genai = "0.10.14"
security-crypto = "1.1.0-alpha06"

[libraries]
# 100% Bundled Offline OCR (No internet needed)
mlkit-text-recognition-bundled = { module = "com.google.mlkit:text-recognition", version.ref = "mlkit-text-recognition" }

# On-Device Named Entity Extraction (SSN, emails, phone numbers, addresses for smart redact)
mlkit-entity-extraction = { module = "com.google.mlkit:entity-extraction", version.ref = "mlkit-entity-extraction" }

# Google Play Services On-Device Document Camera Scanner (Perspective warp, deskew, B&W filter)
play-services-document-scanner = { module = "com.google.android.gms:play-services-mlkit-document-scanner", version.ref = "play-services-scanner" }

# Local On-Device LLM ("Chat with PDF" / Summarization without cloud servers)
mediapipe-tasks-genai = { module = "com.google.mediapipe:tasks-genai", version.ref = "mediapipe-genai" }

# Hardware-backed encrypted preferences for Auto-Fill Vault
androidx-security-crypto = { module = "androidx.security:security-crypto", version.ref = "security-crypto" }
