package com.stitchlens.app

import android.app.Application
import com.stitchlens.app.util.DocumentDetector

class StitchLensApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DocumentDetector.init()
    }
}
