package com.example.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.provider.Settings

data class FakeGpsDetectionResult(
    val isMockDetected: Boolean,
    val detectedApps: List<String> = emptyList(),
    val reason: String = ""
)

object FakeGpsDetector {

    private val KNOWN_FAKE_GPS_PACKAGES = setOf(
        "com.lexa.fakegps",
        "com.rosteam.gpsemulator",
        "com.incorporateapps.fakegps",
        "com.fakegps.mock",
        "com.hopeway.fakegps",
        "com.mocker.fakegps",
        "com.gsmartstudio.fakegps",
        "com.blogspot.skronak.fakegps",
        "com.theappninjas.fakegpsjoystick",
        "com.fly.gps"
    )

    /**
     * Checks if a Location object originates from a mock/fake provider.
     */
    fun isLocationMock(location: Location?): Boolean {
        if (location == null) return false

        // In API 31+ (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (location.isMock) return true
        }

        // Standard deprecation-safe check for Android 18+
        @Suppress("DEPRECATION")
        if (location.isFromMockProvider) {
            return true
        }

        return false
    }

    /**
     * Scans installed applications to detect known Mock Location or Fake GPS applications.
     */
    fun findInstalledFakeGpsApps(context: Context): List<String> {
        val detected = mutableListOf<String>()
        val pm = context.packageManager

        try {
            val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            for (pkg in packages) {
                val pkgName = pkg.packageName.lowercase()
                // Check against known mock packages
                if (KNOWN_FAKE_GPS_PACKAGES.contains(pkgName)) {
                    val appLabel = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkgName
                    detected.add("$appLabel ($pkgName)")
                    continue
                }

                // Check if non-system app requests ACCESS_MOCK_LOCATION
                val isSystemApp = (pkg.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
                val reqPerms = pkg.requestedPermissions
                if (!isSystemApp && reqPerms != null) {
                    val hasMockPermission = reqPerms.any {
                        it.equals("android.permission.ACCESS_MOCK_LOCATION", ignoreCase = true)
                    }
                    if (hasMockPermission) {
                        val appLabel = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: pkgName
                        detected.add("$appLabel ($pkgName)")
                    }
                }
            }
        } catch (_: Exception) {
            // Safe fallback if package inspection restricted
        }

        return detected
    }

    /**
     * Comprehensive security audit combining location metadata and mock application scan.
     */
    fun audit(context: Context, location: Location?): FakeGpsDetectionResult {
        // 1. Direct location telemetry mock flag
        if (isLocationMock(location)) {
            return FakeGpsDetectionResult(
                isMockDetected = true,
                reason = "Sinyal GPS dilaporkan langsung oleh sistem sebagai Provider Palsu (Mock Location Provider)"
            )
        }

        // 2. Installed Fake GPS application detection
        val fakeApps = findInstalledFakeGpsApps(context)
        if (fakeApps.isNotEmpty()) {
            return FakeGpsDetectionResult(
                isMockDetected = true,
                detectedApps = fakeApps,
                reason = "Terdeteksi ${fakeApps.size} aplikasi Fake GPS terpasang: ${fakeApps.first()}"
            )
        }

        // 3. Settings Allow Mock Location check (older Android versions)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val allowMock = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ALLOW_MOCK_LOCATION
            )
            if (allowMock != null && allowMock != "0") {
                return FakeGpsDetectionResult(
                    isMockDetected = true,
                    reason = "Fitur 'Allow Mock Location' aktif di Opsi Pengembang"
                )
            }
        }

        return FakeGpsDetectionResult(isMockDetected = false)
    }
}
