package com.example.data.repository

import com.example.data.model.CompanyProfile
import com.example.data.model.GeofenceAlert
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.UserProfile
import com.example.data.model.ZoneType
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.FirebaseApp
import java.util.UUID

class TrackingRepository {
    private val firestore by lazy {
        val dbId = "ai-studio-android-tracepat-7bbf96bc-8584-4c94-8542-eece2235998d"
        val app = FirebaseApp.getInstance()
        try {
            FirebaseFirestore.getInstance(app, dbId)
        } catch (e: Exception) {
            FirebaseFirestore.getInstance(app)
        }
    }

    suspend fun registerCompany(
        companyName: String,
        adminEmail: String,
        plainPassword: String,
        customCompanyId: String?,
        sector: String
    ): Result<CompanyProfile> {
        return try {
            val cleanEmail = adminEmail.trim().lowercase()
            val companyId = if (!customCompanyId.isNullOrBlank()) {
                customCompanyId.trim().uppercase()
            } else {
                "CORP-" + (1000 + (Math.random() * 9000).toInt())
            }

            val existingDoc = firestore.collection("companies").document(companyId).get().await()
            if (existingDoc.exists()) {
                return Result.failure(Exception("ID Perusahaan '$companyId' sudah dipakai. Gunakan ID lain."))
            }

            val companyData = mapOf(
                "companyId" to companyId,
                "companyName" to companyName.trim(),
                "adminEmail" to cleanEmail,
                "password" to plainPassword,
                "sector" to sector.trim(),
                "createdAt" to System.currentTimeMillis()
            )
            firestore.collection("companies").document(companyId).set(companyData).await()

            val defaultGeofences = listOf(
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "companyId" to companyId,
                    "name" to "Kantor Pusat",
                    "latitude" to -6.2088,
                    "longitude" to 106.8456,
                    "radiusMeters" to 350.0,
                    "zoneType" to ZoneType.OFFICE.name,
                    "colorHex" to ZoneType.OFFICE.defaultColor
                ),
                mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "companyId" to companyId,
                    "name" to "Depot & Hub Distribusi",
                    "latitude" to -6.2160,
                    "longitude" to 106.8520,
                    "radiusMeters" to 500.0,
                    "zoneType" to ZoneType.WAREHOUSE.name,
                    "colorHex" to ZoneType.WAREHOUSE.defaultColor
                )
            )
            for (fence in defaultGeofences) {
                firestore.collection("geofences").document(fence["id"] as String).set(fence).await()
            }

            Result.success(
                CompanyProfile(
                    companyId = companyId,
                    companyName = companyName.trim(),
                    adminEmail = cleanEmail,
                    sector = sector.trim()
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Gagal mendaftarkan perusahaan ke Firebase"))
        }
    }

    suspend fun loginCompany(
        identifier: String,
        plainPassword: String
    ): Result<CompanyProfile> {
        return try {
            val trimmed = identifier.trim()
            var adminEmail = trimmed
            var companyProfile: CompanyProfile? = null
            var expectedPassword: String? = null

            if (!trimmed.contains("@")) {
                val doc = firestore.collection("companies").document(trimmed.uppercase()).get().await()
                if (!doc.exists()) {
                    return Result.failure(Exception("Perusahaan dengan ID '$trimmed' tidak ditemukan."))
                }
                adminEmail = doc.getString("adminEmail") ?: ""
                expectedPassword = doc.getString("password")
                companyProfile = CompanyProfile(
                    companyId = doc.getString("companyId") ?: trimmed,
                    companyName = doc.getString("companyName") ?: "",
                    adminEmail = adminEmail,
                    sector = doc.getString("sector") ?: ""
                )
            } else {
                val query = firestore.collection("companies").whereEqualTo("adminEmail", trimmed.lowercase()).get().await()
                if (query.isEmpty) {
                    return Result.failure(Exception("Perusahaan dengan email tersebut tidak ditemukan."))
                }
                val doc = query.documents.first()
                expectedPassword = doc.getString("password")
                companyProfile = CompanyProfile(
                    companyId = doc.getString("companyId") ?: "",
                    companyName = doc.getString("companyName") ?: "",
                    adminEmail = doc.getString("adminEmail") ?: "",
                    sector = doc.getString("sector") ?: ""
                )
            }

            if (expectedPassword != null && expectedPassword != plainPassword) {
                return Result.failure(Exception("Password yang Anda masukkan salah."))
            }

            Result.success(companyProfile!!)
        } catch (e: Exception) {
            Result.failure(Exception("Login gagal: ${e.message ?: "Periksa kembali email dan password."}"))
        }
    }

    suspend fun loginUser(
        companyId: String,
        email: String
    ): Result<UserProfile> {
        return try {
            val normCompanyId = companyId.trim().uppercase()
            val normEmail = email.trim().lowercase()

            val companyDoc = firestore.collection("companies").document(normCompanyId).get().await()
            if (!companyDoc.exists()) {
                return Result.failure(Exception("ID Perusahaan '$normCompanyId' tidak ditemukan."))
            }

            val query = firestore.collection("users")
                .whereEqualTo("companyId", normCompanyId)
                .whereEqualTo("email", normEmail)
                .get()
                .await()

            if (query.isEmpty) {
                return Result.failure(Exception("Email '$normEmail' belum didaftarkan oleh admin perusahaan."))
            }

            val doc = query.documents.first()
            val user = UserProfile(
                userId = doc.getString("userId") ?: "",
                companyId = doc.getString("companyId") ?: normCompanyId,
                email = doc.getString("email") ?: normEmail,
                fullName = doc.getString("fullName") ?: "",
                unitCode = doc.getString("unitCode") ?: "Unit-01",
                isSharingLocation = doc.getBoolean("isSharingLocation") ?: true,
                lastLatitude = doc.getDouble("lastLatitude") ?: -6.2088,
                lastLongitude = doc.getDouble("lastLongitude") ?: 106.8456,
                lastSpeedKmh = doc.getDouble("lastSpeedKmh")?.toFloat() ?: 0f,
                lastUpdatedAt = doc.getLong("lastUpdatedAt") ?: System.currentTimeMillis(),
                hasFakeGpsAlert = doc.getBoolean("hasFakeGpsAlert") ?: false
            )
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Login karyawan gagal"))
        }
    }

    suspend fun addEmployee(
        companyId: String,
        email: String,
        fullName: String,
        unitCode: String
    ): Result<UserProfile> {
        return try {
            val normCompanyId = companyId.trim().uppercase()
            val normEmail = email.trim().lowercase()

            val newUserId = "USR-" + UUID.randomUUID().toString().take(8).uppercase()
            val userData = mapOf(
                "userId" to newUserId,
                "companyId" to normCompanyId,
                "email" to normEmail,
                "fullName" to fullName.trim(),
                "unitCode" to unitCode.trim().ifBlank { "Unit-01" },
                "isSharingLocation" to false,
                "lastLatitude" to -6.2088,
                "lastLongitude" to 106.8456,
                "lastSpeedKmh" to 0.0,
                "lastUpdatedAt" to System.currentTimeMillis(),
                "hasFakeGpsAlert" to false
            )

            firestore.collection("users").document(newUserId).set(userData).await()

            Result.success(
                UserProfile(
                    userId = newUserId,
                    companyId = normCompanyId,
                    email = normEmail,
                    fullName = fullName.trim(),
                    unitCode = unitCode.trim().ifBlank { "Unit-01" },
                    isSharingLocation = false,
                    lastLatitude = -6.2088,
                    lastLongitude = 106.8456,
                    lastSpeedKmh = 0f,
                    lastUpdatedAt = System.currentTimeMillis(),
                    hasFakeGpsAlert = false
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Gagal mendaftarkan karyawan ke Firestore"))
        }
    }

    suspend fun setLocationSharing(userId: String, isSharing: Boolean) {
        try {
            firestore.collection("users").document(userId)
                .update("isSharingLocation", isSharing)
                .await()
        } catch (e: Exception) {
            // handle error
        }
    }

    suspend fun recordLocationPoint(
        point: LocationPoint,
        userName: String
    ) {
        try {
            val pointId = UUID.randomUUID().toString()
            val pointData = mapOf(
                "id" to pointId,
                "userId" to point.userId,
                "companyId" to point.companyId,
                "latitude" to point.latitude,
                "longitude" to point.longitude,
                "altitude" to point.altitude,
                "speedKmh" to point.speedKmh,
                "accuracyMeters" to point.accuracyMeters,
                "bearing" to point.bearing,
                "timestamp" to point.timestamp,
                "geofenceStatus" to point.geofenceStatus,
                "batteryLevel" to point.batteryLevel,
                "isMock" to point.isMock
            )
            firestore.collection("location_points").document(pointId).set(pointData).await()

            val userUpdate = mutableMapOf<String, Any>(
                "lastLatitude" to point.latitude,
                "lastLongitude" to point.longitude,
                "lastSpeedKmh" to point.speedKmh,
                "lastUpdatedAt" to point.timestamp
            )
            if (point.isMock) {
                userUpdate["hasFakeGpsAlert"] = true
            }

            firestore.collection("users").document(point.userId).update(userUpdate).await()
        } catch (e: Exception) {
            // handle error
        }
    }

    fun observeUsersForCompany(companyId: String): Flow<List<UserProfile>> = callbackFlow {
        val listener = firestore.collection("users")
            .whereEqualTo("companyId", companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val users = snapshot?.documents?.mapNotNull { doc ->
                    UserProfile(
                        userId = doc.getString("userId") ?: "",
                        companyId = doc.getString("companyId") ?: companyId,
                        email = doc.getString("email") ?: "",
                        fullName = doc.getString("fullName") ?: "",
                        unitCode = doc.getString("unitCode") ?: "",
                        isSharingLocation = doc.getBoolean("isSharingLocation") ?: false,
                        lastLatitude = doc.getDouble("lastLatitude") ?: -6.2088,
                        lastLongitude = doc.getDouble("lastLongitude") ?: 106.8456,
                        lastSpeedKmh = doc.getDouble("lastSpeedKmh")?.toFloat() ?: 0f,
                        lastUpdatedAt = doc.getLong("lastUpdatedAt") ?: System.currentTimeMillis(),
                        hasFakeGpsAlert = doc.getBoolean("hasFakeGpsAlert") ?: false
                    )
                } ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    fun observeUserHistory(userId: String): Flow<List<LocationPoint>> = callbackFlow {
        val listener = firestore.collection("location_points")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val points = snapshot?.documents?.mapNotNull { doc ->
                    LocationPoint(
                        id = doc.getLong("idNum") ?: doc.id.hashCode().toLong(),
                        userId = doc.getString("userId") ?: userId,
                        companyId = doc.getString("companyId") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        altitude = doc.getDouble("altitude") ?: 0.0,
                        speedKmh = doc.getDouble("speedKmh")?.toFloat() ?: 0f,
                        accuracyMeters = doc.getDouble("accuracyMeters")?.toFloat() ?: 0f,
                        bearing = doc.getDouble("bearing")?.toFloat() ?: 0f,
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        geofenceStatus = doc.getString("geofenceStatus") ?: "Normal",
                        batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 100,
                        isMock = doc.getBoolean("isMock") ?: false
                    )
                }?.sortedBy { it.timestamp } ?: emptyList()
                trySend(points)
            }
        awaitClose { listener.remove() }
    }

    fun observeGeofences(companyId: String): Flow<List<GeofenceZone>> = callbackFlow {
        val listener = firestore.collection("geofences")
            .whereEqualTo("companyId", companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val zones = snapshot?.documents?.mapNotNull { doc ->
                    val typeStr = doc.getString("zoneType") ?: "OFFICE"
                    val zType = try { ZoneType.valueOf(typeStr) } catch (e: Exception) { ZoneType.OFFICE }
                    GeofenceZone(
                        id = doc.getString("id") ?: doc.id,
                        companyId = doc.getString("companyId") ?: companyId,
                        name = doc.getString("name") ?: "",
                        latitude = doc.getDouble("latitude") ?: -6.2088,
                        longitude = doc.getDouble("longitude") ?: 106.8456,
                        radiusMeters = doc.getDouble("radiusMeters")?.toFloat() ?: 300f,
                        zoneType = zType,
                        colorHex = doc.getString("colorHex") ?: zType.defaultColor
                    )
                } ?: emptyList()
                trySend(zones)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getGeofences(companyId: String): List<GeofenceZone> {
        return try {
            val query = firestore.collection("geofences")
                .whereEqualTo("companyId", companyId)
                .get()
                .await()
            query.documents.mapNotNull { doc ->
                val typeStr = doc.getString("zoneType") ?: "OFFICE"
                val zType = try { ZoneType.valueOf(typeStr) } catch (e: Exception) { ZoneType.OFFICE }
                GeofenceZone(
                    id = doc.getString("id") ?: doc.id,
                    companyId = doc.getString("companyId") ?: companyId,
                    name = doc.getString("name") ?: "",
                    latitude = doc.getDouble("latitude") ?: -6.2088,
                    longitude = doc.getDouble("longitude") ?: 106.8456,
                    radiusMeters = doc.getDouble("radiusMeters")?.toFloat() ?: 300f,
                    zoneType = zType,
                    colorHex = doc.getString("colorHex") ?: zType.defaultColor
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addGeofence(geofence: GeofenceZone) {
        try {
            val fenceData = mapOf(
                "id" to geofence.id,
                "companyId" to geofence.companyId,
                "name" to geofence.name,
                "latitude" to geofence.latitude,
                "longitude" to geofence.longitude,
                "radiusMeters" to geofence.radiusMeters,
                "zoneType" to geofence.zoneType.name,
                "colorHex" to geofence.colorHex
            )
            firestore.collection("geofences").document(geofence.id).set(fenceData).await()
        } catch (e: Exception) {
            // handle error
        }
    }

    suspend fun deleteGeofence(geofenceId: String) {
        try {
            firestore.collection("geofences").document(geofenceId).delete().await()
        } catch (e: Exception) {
            // handle error
        }
    }

    fun observeAlerts(companyId: String): Flow<List<GeofenceAlert>> = callbackFlow {
        val listener = firestore.collection("geofence_alerts")
            .whereEqualTo("companyId", companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val alerts = snapshot?.documents?.mapNotNull { doc ->
                    GeofenceAlert(
                        id = doc.getLong("idNum") ?: doc.id.hashCode().toLong(),
                        companyId = doc.getString("companyId") ?: companyId,
                        userId = doc.getString("userId") ?: "",
                        userName = doc.getString("userName") ?: "",
                        geofenceName = doc.getString("geofenceName") ?: "",
                        eventType = doc.getString("eventType") ?: "MASUK",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        description = doc.getString("description") ?: "",
                        isSecurityWarning = doc.getBoolean("isSecurityWarning") ?: false
                    )
                }?.sortedByDescending { it.timestamp } ?: emptyList()
                trySend(alerts)
            }
        awaitClose { listener.remove() }
    }

    suspend fun recordGeofenceAlert(alert: GeofenceAlert) {
        try {
            val alertId = UUID.randomUUID().toString()
            val alertData = mapOf(
                "id" to alertId,
                "companyId" to alert.companyId,
                "userId" to alert.userId,
                "userName" to alert.userName,
                "geofenceName" to alert.geofenceName,
                "eventType" to alert.eventType,
                "timestamp" to alert.timestamp,
                "latitude" to alert.latitude,
                "longitude" to alert.longitude,
                "description" to alert.description,
                "isSecurityWarning" to alert.isSecurityWarning
            )
            firestore.collection("geofence_alerts").document(alertId).set(alertData).await()
        } catch (e: Exception) {
            // handle error
        }
    }

    suspend fun clearHistory(userId: String) {
        try {
            val query = firestore.collection("location_points")
                .whereEqualTo("userId", userId)
                .get()
                .await()
            for (doc in query.documents) {
                doc.reference.delete().await()
            }
        } catch (e: Exception) {
            // handle error
        }
    }
}
