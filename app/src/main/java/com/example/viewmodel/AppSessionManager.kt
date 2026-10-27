package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.data.model.AuthSession
import com.example.data.model.UserProfile
import com.example.data.model.CompanyProfile

/**
 * Singleton / Persistent Application-scoped Session holder to prevent
 * user logout when switching away from the app (e.g. WA, Instagram, background).
 */
object AppSessionManager {
    var currentSession: AuthSession = AuthSession.None
        private set

    fun setSession(session: AuthSession) {
        currentSession = session
    }

    fun clear() {
        currentSession = AuthSession.None
    }
}
