package com.example.util

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

object FirebaseInitializer {
    private const val TAG = "FirebaseInitializer"

    fun init(context: Context): FirebaseApp {
        val app = try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                FirebaseApp.getInstance()
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }

        if (app != null) return app

        val appContext = context.applicationContext ?: context
        val fromResource = try {
            FirebaseOptions.fromResource(appContext)
        } catch (e: Exception) {
            Log.w(TAG, "FirebaseOptions.fromResource failed, using explicit fallback options", e)
            null
        }

        val options = fromResource ?: FirebaseOptions.Builder()
            .setApplicationId("1:645794320680:android:1ae7d1ee405491242a001d")
            .setApiKey("AIzaSyCUqZv1gFAuiQMQU60qnUQ0_31aNgIbdcU")
            .setProjectId("ardent-cycling-465314-u3")
            .setGcmSenderId("645794320680")
            .setStorageBucket("ardent-cycling-465314-u3.firebasestorage.app")
            .build()

        return try {
            FirebaseApp.initializeApp(appContext, options)
        } catch (e: Exception) {
            Log.e(TAG, "FirebaseApp.initializeApp failed with options", e)
            FirebaseApp.getInstance()
        }
    }
}
