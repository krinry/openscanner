package org.openscanner

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import org.openscanner.data.local.AppDatabase

class OpenScannerApp : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getDatabase(this)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Initialize PDFBox resource loader
        PDFBoxResourceLoader.init(applicationContext)
    }

    companion object {
        lateinit var instance: OpenScannerApp
            private set
    }
}
