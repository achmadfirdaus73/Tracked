package com.example

import android.app.Application
import com.example.util.FirebaseInitializer

class GeoTrackApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseInitializer.init(this)
        com.example.viewmodel.AppSessionManager.init(this)
    }
}
