package com.example.data.model

import java.util.UUID

data class LocationPoint(
    val id: Long = 0,
    val userId: String,
    val companyId: String,
    val sessionId: String = "",
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val accuracyMeters: Float = 3f,
    val bearing: Float = 0f,
    val timestamp: Long = System.currentTimeMillis(),
    val geofenceStatus: String = "Normal",
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
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

enum class VehicleType(
    val label: String,
    val iconEmoji: String,
    val code: String
) {
    MOTORCYCLE("Motor", "🏍️", "motorcycle"),
    CAR("Mobil", "🚗", "car"),
    BICYCLE("Sepeda", "🚲", "bicycle"),
    TRUCK("Truk", "🚚", "truck"),
    WALK("Pejalan Kaki", "🚶", "walk");

    companion object {
        fun fromCode(code: String?): VehicleType {
            return entries.find { it.name.equals(code, ignoreCase = true) || it.code.equals(code, ignoreCase = true) } ?: MOTORCYCLE
        }
    }
}

data class StopPoint(
    val id: String = UUID.randomUUID().toString(),
    val userId: String = "",
    val latitude: Double,
    val longitude: Double,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val durationMinutes: Int,
    val locationName: String = "Titik Berhenti"
)

data class DestinationTask(
    val taskId: String = UUID.randomUUID().toString(),
    val companyId: String = "",
    val userId: String = "",
    val destinationName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val assignedAt: Long = System.currentTimeMillis(),
    val estimatedMinutes: Int = 0,
    val distanceMeters: Float = 0f,
    val status: String = "ASSIGNED", // "ASSIGNED", "ARRIVED", "CANCELLED"
    val arrivedAt: Long? = null,
    val actualDurationMinutes: Int? = null,
    val notes: String = "",
    val selectedRouteIndex: Int = 0,
    val selectedRouteName: String = "Rute 1 (Tercepat)"
)

data class UserProfile(
    val userId: String,
    val companyId: String,
    val email: String,
    val fullName: String,
    val unitCode: String,
    val vehicleType: VehicleType = VehicleType.MOTORCYCLE,
    val isSharingLocation: Boolean = false,
    val lastLatitude: Double = -6.2088,
    val lastLongitude: Double = 106.8456,
    val lastSpeedKmh: Float = 0f,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val hasFakeGpsAlert: Boolean = false
)

data class ChatMessage(
    val messageId: String = UUID.randomUUID().toString(),
    val companyId: String = "",
    val userId: String = "", // 1-on-1 thread per employee (User only chats with Admin)
    val senderId: String = "",
    val senderName: String = "",
    val senderRole: String = "USER", // "ADMIN" or "USER"
    val messageText: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

sealed interface AuthSession {
    data object None : AuthSession
    data class UserSession(val user: UserProfile) : AuthSession
    data class AdminSession(val company: CompanyProfile) : AuthSession
}
