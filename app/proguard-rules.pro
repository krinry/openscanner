# Keep native JNI methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# PDFBox
-keep class com.tom_roush.pdfbox.** { *; }

# ML Kit Text Recognition
-keep class com.google.mlkit.vision.text.** { *; }
