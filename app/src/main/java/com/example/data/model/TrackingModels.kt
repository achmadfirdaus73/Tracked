package com.example.data.model

data class LocationPoint(
    val id: Long = 0,
    val userId: String,
    val companyId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val accuracyMeters: Float = 3f,
    val bearing: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val geofenceStatus: String = "Normal",
    val batteryLevel: Int = 85,
    val isMock: Boolean = false
)

data class GeofenceZone(
    val id: String,
    val companyId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val zoneType: ZoneType = ZoneType.OFFICE,
    val colorHex: String = "#0F52BA"
)

enum class ZoneType(val label: String, val defaultColor: String) {
    OFFICE("Kantor / Pusat", "#0F52BA"),
    WAREHOUSE("Gudang Distribusi", "#00A896"),
    CLIENT_SITE("Lokasi Klien", "#8B5CF6"),
    CHECKPOINT("Titik Singgah / Pos", "#F59E0B")
}

data class GeofenceAlert(
    val id: Long = 0,
    val companyId: String,
    val userId: String,
    val userName: String,
    val geofenceName: String,
    val eventType: String, // "MASUK", "KELUAR", "FAKE_GPS_TERDETEKSI"
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double,
    val longitude: Double,
    val description: String = "",
    val isSecurityWarning: Boolean = false
)

data class CompanyProfile(
    val companyId: String,
    val companyName: String,
    val adminEmail: String,
    val sector: String,
    val registeredAt: Long = System.currentTimeMillis()
)

data class UserProfile(
    val userId: String,
    val companyId: String,
    val email: String,
    val fullName: String,
    val unitCode: String,
    val isSharingLocation: Boolean = false,
    val lastLatitude: Double = -6.2088,
    val lastLongitude: Double = 106.8456,
    val lastSpeedKmh: Float = 0f,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val hasFakeGpsAlert: Boolean = false
)

sealed interface AuthSession {
    data object None : AuthSession
    data class UserSession(val user: UserProfile) : AuthSession
    data class AdminSession(val company: CompanyProfile) : AuthSession
}
