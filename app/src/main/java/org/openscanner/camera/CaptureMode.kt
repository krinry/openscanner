package org.openscanner.camera

enum class CaptureMode(val title: String, val description: String) {
    SINGLE("Single", "Single document page with auto-crop"),
    BATCH("Batch", "Continuous scanning for multi-page documents"),
    ID_CARD("ID Card", "Front & Back card sides combined onto one page"),
    QR_CODE("QR Code", "Fast QR and Barcode scanner"),
    BOOK("Book", "Two-page spread with curve correction")
}
