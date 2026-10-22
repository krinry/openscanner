# OpenScanner — Memory Safety & Hardware Optimization (MEMORY.md)

## 1. Challenge & Risk Profile

Modern mobile sensors capture high-resolution images ranging from 12 megapixels (4000x3000) to 50+ megapixels (8192x6144). In uncompressed `ARGB_8888` format:
* A single 12MP bitmap consumes **~48 MB** of RAM.
* A single 48MP bitmap consumes **~192 MB** of RAM.
* Creating intermediate copies during perspective warping, filtering, and PDF generation can rapidly exhaust the typical Android app heap limit (192 MB - 512 MB), triggering fatal `OutOfMemoryError` (OOM) crashes.

OpenScanner is engineered with a **zero-leak, bounded-memory architecture** that guarantees high-speed operation on devices ranging from entry-level Android handsets to flagship hardware.

---

## 2. Core Memory Optimization Strategies

```
┌──────────────────────────────────────────────────────────┐
│                   Camera Frame Capture                   │
│         CameraX ImageProxy (NV21 / YUV_420_888)          │
└────────────────────────────┬─────────────────────────────┘
                             │ Subsampled 720p Gray Plane
┌────────────────────────────▼─────────────────────────────┐
│             Real-Time Edge Detection (C++)               │
│  Zero-Allocation Reusable Buffer • Fixed Sobel Memory   │
└────────────────────────────┬─────────────────────────────┘
                             │ Document Quadrilateral Points
┌────────────────────────────▼─────────────────────────────┐
│                 Full-Res Shutter Capture                 │
│         Streams Directly to Disk (cacheDir/temp/)        │
└────────────────────────────┬─────────────────────────────┘
                             │ Decode on Demand with inSampleSize
┌────────────────────────────▼─────────────────────────────┐
│              Native JNI Transform & Filter               │
│   Direct Buffer Access (jnigraphics) • In-Place Mutation │
└────────────────────────────┬─────────────────────────────┘
                             │ Immediate recycle() of Inputs
┌────────────────────────────▼─────────────────────────────┐
│             Disk Persistence & Coil Thumbnail            │
│  Compact JPEG (Quality 88) • Cache-Controlled Thumbnails │
└──────────────────────────────────────────────────────────┘
```

---

### Strategy 1: In-Place Native Pixel Mutation
Rather than creating duplicate bitmap allocations in memory for each enhancement stage:
1. Native algorithms in `libopenscanner.so` access pixel arrays directly using Android's NDK `AndroidBitmap_lockPixels()` API.
2. The CLAHE, background illumination estimation, and Bradley-Roth binarization algorithms mutate the pixel buffer in-place or use small, pre-allocated lookup tables (LUTs) and downsampled illumination grids (1/8th or 1/16th resolution).
3. `AndroidBitmap_unlockPixels()` unlocks the buffer immediately upon computation completion.

### Strategy 2: Proactive Bitmap Downsampling (`inSampleSize`)
When displaying high-resolution scans on preview screens (such as `CropScreen` or `PageViewerScreen`):
* `BitmapFactory.Options.inJustDecodeBounds = true` reads the raw image dimensions without allocating memory.
* `inSampleSize` is calculated dynamically to downsample the image to match the device's physical viewport resolution.
* This limits preview memory consumption to **< 8 MB per page** regardless of the source camera sensor size.

### Strategy 3: Dedicated Thumbnail Caching Pipeline
* Full-resolution scans are saved into internal app storage (`context.filesDir/scans/`).
* A compact, pre-downsampled thumbnail (maximum 600px dimension, ~40 KB file size) is generated once and written to `context.filesDir/thumbs/`.
* `HomeScreen` grids and lists query **only** the lightweight thumbnail files via Coil's disk-cached async image loader. Full-resolution 48MB bitmaps are never loaded into list views.

### Strategy 4: Streamed Multi-Page PDF Generation
When compiling documents containing 20, 50, or 100+ pages:
* `PdfGenerator` **never** loads all page bitmaps into memory simultaneously.
* Pages are processed sequentially in a streamed pipeline:
  1. Load page $N$ bitmap from disk.
  2. Compress and draw into the PDF page canvas.
  3. Proactively call `bitmap.recycle()` on page $N$.
  4. Proceed to page $N+1$.
* Peak memory usage during PDF generation remains constant regardless of the total page count.

### Strategy 5: Coroutine Dispatcher Isolation
* **`Dispatchers.Main`:** Strictly reserved for 60fps UI recomposition, touch gestures, and animations.
* **`Dispatchers.Default` (CPU Pool):** Dedicated to quadrilateral geometry math, ML Kit OCR parsing, and collage layouts.
* **`Dispatchers.IO` (I/O Pool):** Dedicated to file streaming, JPEG decoding/encoding, and Room database transactions.

---

## 3. Native Buffer Safety Rules (C++ NDK)

1. **Bounds Validation:** Every native C++ function (`warpPerspectiveNative`, `applyMagicColorNative`, `applyNoShadowNative`) validates that `pixels != nullptr`, `width > 0`, and `height > 0` before memory indexing.
2. **Zero Global Pointers:** No static or global bitmap pointers are retained in native memory across JNI calls. All buffers are scoped to their respective function execution.
3. **RAII Containers:** Intermediate algorithm vectors (such as gray planes and integral images) use `std::vector<uint8_t>` or `std::vector<int>` which automatically deallocate memory upon exiting scope.
