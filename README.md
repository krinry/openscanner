# 📄 OpenScanner

> **OpenScanner** is a 100% free, open-source, offline, and privacy-respecting document scanner app for Android — built as a clean-room alternative to **OKEN Scanner** and **CamScanner** without ads, tracking, watermarks, or cloud subscriptions.

---

## 🌟 Key Features (Matched 1:1 with OKEN Scanner)

1. **High-Speed Camera Viewfinder (CameraX):**
   - Real-time 60fps document boundary detection with green tracking overlay.
   - Flash controls (Off, On, Auto), touch-to-focus, and grid guides.
   - Multiple Capture Modes:
     - **Single Mode:** Instant auto-capture on document stabilization.
     - **Batch Mode:** Rapid continuous shutter burst for scanning entire books/notebooks.
     - **ID Card Mode:** Dual-sided card capture aligned onto a single A4 page.
     - **QR & Barcode Mode:** Live camera code scanning and action trigger.
     - **Book Mode:** Dual-page split with curve flattening.

2. **Interactive 8-Point Crop Editor:**
   - 4 corner handles + 4 midpoint edge handles for millimeter-precision cropping.
   - **Magnifying Loupe (Floating Loupe):** High-zoom crosshair window hovering above finger touch for exact edge positioning.
   - Auto-crop calculation and "Select All" full-frame fallback.

3. **High-Performance Document Enhancement Pipeline (C++ NDK + Kotlin):**
   - **Magic Color (Vivid):** Local adaptive contrast enhancement (CLAHE) + background whitening + saturation boost.
   - **B&W / B&W 2:** Bradley-Roth & Sauvola adaptive binarization for ultra-crisp document contrast.
   - **Grayscale:** Monochromatic contrast-stretched 8-bit output.
   - **Lighten:** Background shadow removal.
   - **Manual Sliders:** Fine-grained Brightness and Contrast adjustments (-100 to +100).

4. **Multi-Page Document & File Management:**
   - Document library with search, tags, and modification timestamps.
   - Multi-page grid view with drag-and-drop page reordering and deletion.
   - High-resolution fullscreen page viewer with pinch-to-zoom (1x - 5x).

5. **Professional PDF Engine:**
   - Export to PDF with custom paper dimensions: **A4**, **US Letter**, **US Legal**, or **Fit to Image**.
   - Adjustable PDF compression quality (High, Medium, Small file size).
   - Custom security watermarks (e.g. "CONFIDENTIAL", date, or custom stamps).
   - AES 128-bit PDF password encryption.
   - Native Android Share Sheet integration.

6. **On-Device Offline OCR (Google ML Kit):**
   - Zero internet connection required.
   - Extracts printed text from scanned documents in real time.
   - One-tap copy to clipboard.

---

## 🏗️ Technical Architecture

```
OpenScanner
├── app/
│   ├── src/main/
│   │   ├── cpp/                     # High-Speed Native C++ Image Processing (libopenscanner.so)
│   │   │   ├── CMakeLists.txt
│   │   │   ├── native_scanner.h
│   │   │   ├── native_scanner.cpp   # JNI Bridge
│   │   │   ├── edge_detector.cpp    # Sobel gradient contour detection
│   │   │   ├── perspective_warp.cpp # 3x3 Homography Bilinear Warp
│   │   │   └── magic_color.cpp      # CLAHE & Bradley-Roth adaptive filters
│   │   ├── java/org/openscanner/
│   │   │   ├── camera/              # CameraX ImageAnalysis & DocumentAnalyzer
│   │   │   ├── cv/                  # Quadrilateral math & NativeScannerEngine fallback
│   │   │   ├── data/                # Room DB (documents & pages) & ScannerRepository
│   │   │   ├── ocr/                 # Google ML Kit Text Recognition
│   │   │   ├── pdf/                 # PDFBox Android generator with AES protection
│   │   │   └── ui/                  # 100% Jetpack Compose Material 3 UI:
│   │   │       ├── home/            # Document list, search bar, rename, delete
│   │   │       ├── camera/          # Live viewfinder with green laser polygon
│   │   │       ├── crop/            # 8-point handles & magnifying loupe
│   │   │       ├── filter/          # Live filter previews & sliders
│   │   │       ├── detail/          # Page thumbnails grid & PDF export dialog
│   │   │       └── viewer/          # Zoom/pan viewer & OCR bottom sheet
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
│   └── libs.versions.toml           # Modern Gradle version catalog
├── settings.gradle.kts
└── build.gradle.kts
```

---

## 🛠️ How to Build & Run

### Prerequisites
- **Android Studio Iguana / Jellyfish** (or newer).
- **Android SDK:** API 34 (compileSdk: 34, minSdk: 26).
- **NDK:** 25.1+ (configured automatically via CMake).
- **JDK:** Java 17 or Java 21.

### Steps
1. Open the project in Android Studio:
   ```bash
   cd /path/to/openscanner
   ```
2. Let Gradle sync dependencies.
3. Connect an Android device (or launch an Emulator) with USB Debugging enabled.
4. Click **Run** (`Shift + F10`) or assemble the APK via terminal:
   ```bash
   ./gradlew assembleDebug
   ```
5. The debug APK will be generated at:
   `app/build/outputs/apk/debug/app-debug.apk`

---

## 🔒 Privacy & Clean-Room Guarantee
- **No Analytics / No Ad SDKs / No Tracking:** OpenScanner contains zero tracking code (unlike commercial scanner apps which bundle Facebook Ads, AppsFlyer, etc.).
- **100% Local Processing:** Every document is stored in private internal app storage (`context.filesDir/scans/`) and processed strictly on-device.
