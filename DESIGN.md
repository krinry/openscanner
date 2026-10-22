# OpenScanner — Design System & UI/UX Guidelines (DESIGN.md)

## 1. Visual Philosophy & Identity

OpenScanner's visual identity balances **modern minimalism, high-speed utility, and extreme readability**. 

* **Clean-Room Vector Visuals:** 100% of custom graphics (such as Manila folder vectors and crop loupes) are drawn programmatically using Jetpack Compose `Canvas` and standard Google Material 3 vector assets. Zero copyrighted icons or commercial graphic assets are used.
* **Dual-Theme First:** First-class support for **High-Contrast AMOLED Dark Mode** (`#000000`) for OLED power savings, alongside an ultra-clean **Paper Light Mode** (`#F8F9FA`) engineered for daylight document readability.
* **Instant Tactile Feedback:** High-contrast emerald interactive indicators, smooth gesture drags, and live visual confirmations.

---

## 2. Color System & Semantic Tokens

### 2.1 Core Brand Colors
| Token | Hex Value | Semantic Role |
| :--- | :--- | :--- |
| `ScannerGreen` | `#00BA66` | Primary brand emerald green, shutter buttons, active toggles |
| `ScannerGreenLight` | `#00D675` | Highlight green, active chips, laser viewfinder borders |
| `ScannerGreenDark` | `#008C4C` | Pressed green, active container text |
| `ScannerFolderYellow`| `#F0AF3A` | Warm Manila folder body & tab color |
| `ScannerBlueAccent` | `#0D7DFC` | PDF tools, import accents |
| `ScannerRed` | `#EE3F3F` | Destructive actions, delete confirmations |

### 2.2 AMOLED Dark Theme Tokens
* **Background (`#000000`):** Pure OLED black for maximum contrast and battery efficiency.
* **Surface / Card (`#141414`):** Elevated dark graphite card surface.
* **Surface Secondary (`#1E1E1E`):** Secondary chips, thumbnail placeholder boxes, and subtle pill containers.
* **Card Border (`#242424`):** Crisp 0.5dp card outline for structural clarity.
* **Text Primary (`#FFFFFF`):** High-contrast white for headings and document titles.
* **Text Secondary (`#8E8E93`):** Neutral gray for timestamps, page counters, and subtitles.
* **Text Muted (`#636366`):** Low-contrast gray for search placeholders and disabled states.
* **Primary Container (`#1E2822`):** Subtle emerald-tinted dark container.

### 2.3 Paper Light Theme Tokens
* **Background (`#F8F9FA`):** Soft, non-glare off-white background mimicking high-grade paper.
* **Surface / Card (`#FFFFFF`):** Pure white document cards with subtle elevation.
* **Surface Secondary (`#F1F3F5`):** Soft gray container for chips and inactive buttons.
* **Card Border (`#E9ECEF`):** Subtle light border for card delineation.
* **Text Primary (`#17202B`):** Deep slate black for crisp typographic readability.
* **Text Secondary (`#6C757D`):** Balanced neutral slate for secondary metadata.
* **Text Muted (`#ADB5BD`):** Light slate for placeholder inputs.
* **Primary Container (`#DCF5E8`):** Mint green container tint for selected items.

---

## 3. Typographic Hierarchy

| Level | Size | Weight | Line Height | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Headline Large** | 21sp | ExtraBold | 28sp | Top App Header ("OpenScanner") |
| **Headline Medium**| 19sp | Bold | 26sp | Folder title headers ("All docs (12) ▾") |
| **Title Medium** | 16-17sp | Bold | 22sp | Section headers, dialog titles, top bar titles |
| **Body Large** | 15sp | SemiBold | 20sp | Document title cards, tool cards |
| **Body Medium** | 13-14sp | Medium / Normal | 18sp | Folder item names, input text fields |
| **Caption** | 11-12sp | Normal / Bold | 16sp | Timestamps, page counters ("01", "02"), bottom navigation labels |

---

## 4. Key Reusable Components & Interaction Patterns

### 4.1 Manila Folder Vector (`FolderGraphic.kt`)
Drawn programmatically using Compose `Path`:
* Base tab and backing sheet with a 6dp corner radius.
* Internal white sheet peeking out from the folder tab (`#F8F9FA`).
* Distinct front flap with a gentle bezier curve.

### 4.2 Signature Floating Emerald Shutter FAB (`HomeBottomBar.kt`)
* Size: 60x60dp circle centered above the bottom navigation bar.
* Elevation: 10dp drop shadow with an offset of `-14dp` on the Y-axis.
* Color: `ScannerGreen` (`#00BA66`) with a crisp white camera glyph.

### 4.3 Interactive 8-Point Crop Overlay & Magnifier Loupe (`DraggablePolygonOverlay.kt`, `MagnifierLoupe.kt`)
* **Corner Handles (4):** 16dp radius circular touch targets with a white center and emerald 3.5dp stroke.
* **Edge Midpoint Handles (4):** 9dp radius pill handles for single-axis edge dragging.
* **Magnifier Loupe:** 110dp diameter floating circular loupe positioned above the user's active touch finger to prevent occlusion, rendering a 2x magnified bitmap preview with centered emerald crosshairs.

### 4.4 Verified Scanned Badge
* Displayed on the bottom-right corner of document preview cards.
* 15-18dp emerald circle featuring a crisp white checkmark (`Icons.Default.Check`) indicating the document has been successfully processed and stored locally.

### 4.5 Two-Digit Page Badges ("01", "02") (`PageGridCard.kt`)
* Positioned on a translucent black bottom strip (`Color.Black.copy(alpha = 0.60f)`) spanning the thumbnail card.
* Displays high-contrast formatted page indices (`01`, `02`, `03`...) matching professional document archive layouts.

### 4.6 Theme Switcher Control (☀️ / 🌙)
* Quick-toggle icon button in `HomeTopBar` providing instantaneous transition between AMOLED Dark and Paper Light modes.
* Full-width theme control card in `HomeToolsTab` with live status indicator and switch control.
