package com.example.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.model.AuthSession
import com.example.data.model.CompanyProfile
import com.example.data.model.UserProfile
import com.example.data.model.VehicleType

/**
 * Persistent Application-scoped Session holder using Android SharedPreferences.
 * Prevents user logout when switching apps (e.g. WhatsApp, Instagram, background kills)
 * or when exiting and reopening the application.
 */
object AppSessionManager {
    private const val TAG = "AppSessionManager"
    private const val PREFS_NAME = "geotrack_session_prefs"

    private const val KEY_SESSION_TYPE = "session_type"
    private const val VAL_TYPE_NONE = "NONE"
    private const val VAL_TYPE_USER = "USER"
    private const val VAL_TYPE_ADMIN = "ADMIN"

    // User Session Fields
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_COMPANY_ID = "user_company_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_UNIT = "user_unit"
    private const val KEY_USER_VEHICLE = "user_vehicle"
    private const val KEY_USER_SHARING = "user_sharing"
    private const val KEY_USER_LAT = "user_lat"
    private const val KEY_USER_LNG = "user_lng"
    private const val KEY_USER_SPEED = "user_speed"
    private const val KEY_USER_BATTERY = "user_battery"
    private const val KEY_USER_CHARGING = "user_charging"

    // Admin Session Fields
    private const val KEY_ADMIN_COMPANY_ID = "admin_company_id"
    private const val KEY_ADMIN_COMPANY_NAME = "admin_company_name"
    private const val KEY_ADMIN_EMAIL = "admin_email"
    private const val KEY_ADMIN_SECTOR = "admin_sector"
    private const val KEY_ADMIN_REG_AT = "admin_registered_at"

    var currentSession: AuthSession = AuthSession.None
        private set

    private fun getPrefs(context: Context): SharedPreferences {
        return context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes session state from persistent storage on app startup.
     */
    fun init(context: Context): AuthSession {
        val prefs = getPrefs(context)
        val type = prefs.getString(KEY_SESSION_TYPE, VAL_TYPE_NONE) ?: VAL_TYPE_NONE

        currentSession = when (type) {
            VAL_TYPE_USER -> {
                val userId = prefs.getString(KEY_USER_ID, null)
                val companyId = prefs.getString(KEY_USER_COMPANY_ID, null)
                val email = prefs.getString(KEY_USER_EMAIL, "") ?: ""
                val name = prefs.getString(KEY_USER_NAME, "") ?: ""
                val unit = prefs.getString(KEY_USER_UNIT, "") ?: ""
                val vehicleStr = prefs.getString(KEY_USER_VEHICLE, VehicleType.MOTORCYCLE.name)
                val isSharing = prefs.getBoolean(KEY_USER_SHARING, true)
                val lat = prefs.getFloat(KEY_USER_LAT, -6.2088f).toDouble()
                val lng = prefs.getFloat(KEY_USER_LNG, 106.8456f).toDouble()
                val speed = prefs.getFloat(KEY_USER_SPEED, 0f)
                val battery = prefs.getInt(KEY_USER_BATTERY, 100)
                val charging = prefs.getBoolean(KEY_USER_CHARGING, false)

                if (!userId.isNullOrBlank() && !companyId.isNullOrBlank()) {
                    val user = UserProfile(
                        userId = userId,
                        companyId = companyId,
                        email = email,
                        fullName = name,
                        unitCode = unit,
                        vehicleType = VehicleType.fromCode(vehicleStr),
                        isSharingLocation = isSharing,
                        lastLatitude = lat,
                        lastLongitude = lng,
                        lastSpeedKmh = speed,
                        batteryLevel = battery,
                        isCharging = charging
                    )
                    Log.d(TAG, "Restored persistent user session for ${user.fullName} (${user.userId})")
                    AuthSession.UserSession(user)
                } else {
                    AuthSession.None
                }
            }
            VAL_TYPE_ADMIN -> {
                val companyId = prefs.getString(KEY_ADMIN_COMPANY_ID, null)
                val companyName = prefs.getString(KEY_ADMIN_COMPANY_NAME, "") ?: ""
                val email = prefs.getString(KEY_ADMIN_EMAIL, "") ?: ""
                val sector = prefs.getString(KEY_ADMIN_SECTOR, "Logistik & Transportasi") ?: "Logistik"
                val regAt = prefs.getLong(KEY_ADMIN_REG_AT, System.currentTimeMillis())

                if (!companyId.isNullOrBlank()) {
                    val company = CompanyProfile(
                        companyId = companyId,
                        companyName = companyName,
                        adminEmail = email,
                        sector = sector,
                        registeredAt = regAt
                    )
                    Log.d(TAG, "Restored persistent admin session for ${company.companyName}")
                    AuthSession.AdminSession(company)
                } else {
                    AuthSession.None
                }
            }
            else -> AuthSession.None
        }
        return currentSession
    }

    /**
     * Persists the current session to disk and updates in-memory cache.
     */
    fun saveSession(context: Context, session: AuthSession) {
        currentSession = session
        val prefs = getPrefs(context)
        val editor = prefs.edit()

        when (session) {
            is AuthSession.UserSession -> {
                val u = session.user
                editor.putString(KEY_SESSION_TYPE, VAL_TYPE_USER)
                editor.putString(KEY_USER_ID, u.userId)
                editor.putString(KEY_USER_COMPANY_ID, u.companyId)
                editor.putString(KEY_USER_EMAIL, u.email)
                editor.putString(KEY_USER_NAME, u.fullName)
                editor.putString(KEY_USER_UNIT, u.unitCode)
                editor.putString(KEY_USER_VEHICLE, u.vehicleType.name)
                editor.putBoolean(KEY_USER_SHARING, u.isSharingLocation)
                editor.putFloat(KEY_USER_LAT, u.lastLatitude.toFloat())
                editor.putFloat(KEY_USER_LNG, u.lastLongitude.toFloat())
                editor.putFloat(KEY_USER_SPEED, u.lastSpeedKmh)
                editor.putInt(KEY_USER_BATTERY, u.batteryLevel)
                editor.putBoolean(KEY_USER_CHARGING, u.isCharging)
            }
            is AuthSession.AdminSession -> {
                val c = session.company
                editor.putString(KEY_SESSION_TYPE, VAL_TYPE_ADMIN)
                editor.putString(KEY_ADMIN_COMPANY_ID, c.companyId)
                editor.putString(KEY_ADMIN_COMPANY_NAME, c.companyName)
                editor.putString(KEY_ADMIN_EMAIL, c.adminEmail)
                editor.putString(KEY_ADMIN_SECTOR, c.sector)
                editor.putLong(KEY_ADMIN_REG_AT, c.registeredAt)
            }
            is AuthSession.None -> {
                editor.clear()
            }
        }
        editor.apply()
    }

    /**
     * Clears persistent session storage upon explicit user logout.
     */
    fun clearSession(context: Context) {
        currentSession = AuthSession.None
        getPrefs(context).edit().clear().apply()
        Log.d(TAG, "Cleared user session from storage")
    }

    fun setSession(session: AuthSession) {
        currentSession = session
    }

    fun clear() {
        currentSession = AuthSession.None
    }
}
