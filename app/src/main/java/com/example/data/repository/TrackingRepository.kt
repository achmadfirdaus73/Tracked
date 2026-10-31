package com.example.data.repository

import com.example.data.model.ChatMessage
import com.example.data.model.CompanyProfile
import com.example.data.model.DestinationTask
import com.example.data.model.GeofenceAlert
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.UserProfile
import com.example.data.model.VehicleType
import com.example.data.model.ZoneType
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.google.firebase.FirebaseApp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
                vehicleType = VehicleType.fromCode(doc.getString("vehicleType")),
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
        unitCode: String,
        vehicleType: VehicleType = VehicleType.MOTORCYCLE
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
                "vehicleType" to vehicleType.name,
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
                    vehicleType = vehicleType,
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

    suspend fun updateUserVehicleType(userId: String, vehicleType: VehicleType) {
        try {
            firestore.collection("users").document(userId)
                .update("vehicleType", vehicleType.name)
                .await()
        } catch (_: Exception) { }
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

    // In-memory throttling maps to protect Firebase Free Tier quota (Zero-Cost Telemetry)
    private val lastFirestoreWritePerUser = mutableMapOf<String, Long>()
    private val lastFirestoreLatPerUser = mutableMapOf<String, Double>()
    private val lastFirestoreLngPerUser = mutableMapOf<String, Double>()
    private val lastMockStatePerUser = mutableMapOf<String, Boolean>()

    suspend fun recordLocationPoint(
        point: LocationPoint,
        userName: String,
        forceCloudSync: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val lastTime = lastFirestoreWritePerUser[point.userId] ?: 0L
        val lastLat = lastFirestoreLatPerUser[point.userId]
        val lastLng = lastFirestoreLngPerUser[point.userId]
        val prevMock = lastMockStatePerUser[point.userId]

        val timeDiff = now - lastTime
        val distDiff = if (lastLat != null && lastLng != null) {
            com.example.util.GeofenceEngine.calculateDistanceMeters(lastLat, lastLng, point.latitude, point.longitude)
        } else {
            Double.MAX_VALUE
        }

        // Check if mock status actually changed (e.g. user just turned on/off Fake GPS)
        val isMockStatusTransition = prevMock != null && prevMock != point.isMock || (prevMock == null && point.isMock)

        // Ultra-strict Firestore Quota Protection:
        // Live GPS is already 100% streamed in real-time via MQTT with ZERO database writes.
        // Users collection document should ONLY be updated on:
        // 1) Explicit force sync (e.g. shift start, shift stop, arrival)
        // 2) Fake GPS state change (written exactly ONCE, NOT every second!)
        // 3) At most once every 15 minutes of continuous travel
        // 4) Significant distance moved (> 2,500 meters)
        val shouldWriteToFirestore = forceCloudSync ||
                isMockStatusTransition ||
                timeDiff >= 15 * 60 * 1000L ||
                distDiff >= 2500.0

        if (!shouldWriteToFirestore) {
            // Live point is streamed over MQTT without burning Firestore write quota
            return
        }

        lastFirestoreWritePerUser[point.userId] = now
        lastFirestoreLatPerUser[point.userId] = point.latitude
        lastFirestoreLngPerUser[point.userId] = point.longitude
        lastMockStatePerUser[point.userId] = point.isMock

        try {
            val userUpdate = mutableMapOf<String, Any>(
                "lastLatitude" to point.latitude,
                "lastLongitude" to point.longitude,
                "lastSpeedKmh" to point.speedKmh,
                "lastUpdatedAt" to point.timestamp,
                "batteryLevel" to point.batteryLevel,
                "isCharging" to point.isCharging,
                "hasFakeGpsAlert" to point.isMock
            )

            // Single update call on users document only
            firestore.collection("users").document(point.userId).update(userUpdate).await()
        } catch (e: Exception) {
            // Handle error quietly
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
                        vehicleType = VehicleType.fromCode(doc.getString("vehicleType")),
                        isSharingLocation = doc.getBoolean("isSharingLocation") ?: false,
                        lastLatitude = doc.getDouble("lastLatitude") ?: -6.2088,
                        lastLongitude = doc.getDouble("lastLongitude") ?: 106.8456,
                        lastSpeedKmh = doc.getDouble("lastSpeedKmh")?.toFloat() ?: 0f,
                        lastUpdatedAt = doc.getLong("lastUpdatedAt") ?: System.currentTimeMillis(),
                        batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 100,
                        isCharging = doc.getBoolean("isCharging") ?: false,
                        hasFakeGpsAlert = doc.getBoolean("hasFakeGpsAlert") ?: false
                    )
                } ?: emptyList()
                trySend(users)
            }
        awaitClose { listener.remove() }
    }

    fun getTodayDateKey(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * Compact Cloud Route Sync:
     * Batches dozens of recorded coordinates into a single document array update.
     * Consumes strictly 1 single Firestore write per batch, saving 99% of quota!
     */
    suspend fun syncCompactRouteBatch(
        userId: String,
        companyId: String,
        points: List<LocationPoint>
    ) {
        if (points.isEmpty()) return
        try {
            val dateKey = getTodayDateKey()
            val docId = "${userId}_$dateKey"
            val pointMaps = points.map { pt ->
                mapOf(
                    "lat" to pt.latitude,
                    "lng" to pt.longitude,
                    "spd" to pt.speedKmh.toDouble(),
                    "alt" to pt.altitude,
                    "brg" to pt.bearing.toDouble(),
                    "ts" to pt.timestamp,
                    "geo" to pt.geofenceStatus,
                    "bat" to pt.batteryLevel,
                    "mck" to pt.isMock
                )
            }

            val docRef = firestore.collection("daily_routes").document(docId)
            val docData = mapOf(
                "userId" to userId,
                "companyId" to companyId,
                "date" to dateKey,
                "lastUpdatedAt" to System.currentTimeMillis(),
                "points" to FieldValue.arrayUnion(*pointMaps.toTypedArray())
            )
            // Exactly ONE single Firestore write per batch using SetOptions.merge()!
            docRef.set(docData, SetOptions.merge()).await()
        } catch (_: Exception) {
            // Silently handle offline/transient failure
        }
    }

    fun observeUserHistory(userId: String): Flow<List<LocationPoint>> = callbackFlow {
        // Read full daily route documents for this user
        val listener = firestore.collection("daily_routes")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val allPoints = mutableListOf<LocationPoint>()
                snapshot?.documents?.forEach { doc ->
                    val companyId = doc.getString("companyId") ?: ""
                    val rawList = doc.get("points") as? List<*> ?: emptyList<Any>()
                    for (item in rawList) {
                        if (item is Map<*, *>) {
                            val lat = (item["lat"] as? Number)?.toDouble() ?: 0.0
                            val lng = (item["lng"] as? Number)?.toDouble() ?: 0.0
                            val spd = (item["spd"] as? Number)?.toFloat() ?: 0f
                            val alt = (item["alt"] as? Number)?.toDouble() ?: 0.0
                            val brg = (item["brg"] as? Number)?.toFloat() ?: 0f
                            val ts = (item["ts"] as? Number)?.toLong() ?: 0L
                            val geo = item["geo"] as? String ?: "Normal"
                            val bat = (item["bat"] as? Number)?.toInt() ?: 100
                            val mck = item["mck"] as? Boolean ?: false

                            allPoints.add(
                                LocationPoint(
                                    id = ts,
                                    userId = userId,
                                    companyId = companyId,
                                    latitude = lat,
                                    longitude = lng,
                                    speedKmh = spd,
                                    altitude = alt,
                                    bearing = brg,
                                    timestamp = ts,
                                    geofenceStatus = geo,
                                    batteryLevel = bat,
                                    isMock = mck
                                )
                            )
                        }
                    }
                }
                trySend(allPoints.sortedBy { it.timestamp })
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

    /**
     * 1-on-1 Chat between Admin and User.
     * Note: User only chats with their Company Admin; users cannot chat with each other.
     */
    suspend fun sendChatMessage(
        companyId: String,
        userId: String,
        senderId: String,
        senderName: String,
        senderRole: String,
        text: String
    ): Result<Unit> {
        return try {
            val msgId = UUID.randomUUID().toString()
            val msgData = mapOf(
                "messageId" to msgId,
                "companyId" to companyId,
                "userId" to userId,
                "senderId" to senderId,
                "senderName" to senderName,
                "senderRole" to senderRole,
                "messageText" to text.trim(),
                "timestamp" to System.currentTimeMillis()
            )
            firestore.collection("companies")
                .document(companyId)
                .collection("chats")
                .document(userId)
                .collection("messages")
                .document(msgId)
                .set(msgData)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeChatMessages(companyId: String, userId: String): Flow<List<ChatMessage>> = callbackFlow {
        val listener = firestore.collection("companies")
            .document(companyId)
            .collection("chats")
            .document(userId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    ChatMessage(
                        messageId = doc.getString("messageId") ?: doc.id,
                        companyId = doc.getString("companyId") ?: companyId,
                        userId = doc.getString("userId") ?: userId,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "",
                        senderRole = doc.getString("senderRole") ?: "USER",
                        messageText = doc.getString("messageText") ?: "",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                    )
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    fun observeTasksForCompany(companyId: String): Flow<List<DestinationTask>> = callbackFlow {
        val listener = firestore.collection("destination_tasks")
            .whereEqualTo("companyId", companyId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val tasks = snapshot?.documents?.mapNotNull { doc ->
                    DestinationTask(
                        taskId = doc.getString("taskId") ?: doc.id,
                        companyId = doc.getString("companyId") ?: companyId,
                        userId = doc.getString("userId") ?: "",
                        destinationName = doc.getString("destinationName") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        assignedAt = doc.getLong("assignedAt") ?: System.currentTimeMillis(),
                        estimatedMinutes = doc.getLong("estimatedMinutes")?.toInt() ?: 0,
                        distanceMeters = doc.getDouble("distanceMeters")?.toFloat() ?: 0f,
                        status = doc.getString("status") ?: "ASSIGNED",
                        arrivedAt = doc.getLong("arrivedAt"),
                        actualDurationMinutes = doc.getLong("actualDurationMinutes")?.toInt(),
                        notes = doc.getString("notes") ?: ""
                    )
                }?.sortedByDescending { it.assignedAt } ?: emptyList()
                trySend(tasks)
            }
        awaitClose { listener.remove() }
    }

    fun observeActiveTaskForUser(userId: String): Flow<DestinationTask?> = callbackFlow {
        val listener = firestore.collection("destination_tasks")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val activeTask = snapshot?.documents?.mapNotNull { doc ->
                    DestinationTask(
                        taskId = doc.getString("taskId") ?: doc.id,
                        companyId = doc.getString("companyId") ?: "",
                        userId = doc.getString("userId") ?: userId,
                        destinationName = doc.getString("destinationName") ?: "",
                        latitude = doc.getDouble("latitude") ?: 0.0,
                        longitude = doc.getDouble("longitude") ?: 0.0,
                        assignedAt = doc.getLong("assignedAt") ?: System.currentTimeMillis(),
                        estimatedMinutes = doc.getLong("estimatedMinutes")?.toInt() ?: 0,
                        distanceMeters = doc.getDouble("distanceMeters")?.toFloat() ?: 0f,
                        status = doc.getString("status") ?: "ASSIGNED",
                        arrivedAt = doc.getLong("arrivedAt"),
                        actualDurationMinutes = doc.getLong("actualDurationMinutes")?.toInt(),
                        notes = doc.getString("notes") ?: ""
                    )
                }?.filter { it.status == "ASSIGNED" || (it.status == "ARRIVED" && (System.currentTimeMillis() - (it.arrivedAt ?: 0L)) < 30 * 60 * 1000L) }
                ?.maxByOrNull { it.assignedAt }

                trySend(activeTask)
            }
        awaitClose { listener.remove() }
    }

    suspend fun assignDestinationTask(task: DestinationTask): Result<Unit> {
        return try {
            val taskData = mapOf(
                "taskId" to task.taskId,
                "companyId" to task.companyId,
                "userId" to task.userId,
                "destinationName" to task.destinationName,
                "latitude" to task.latitude,
                "longitude" to task.longitude,
                "assignedAt" to task.assignedAt,
                "estimatedMinutes" to task.estimatedMinutes,
                "distanceMeters" to task.distanceMeters,
                "status" to task.status,
                "notes" to task.notes
            )
            firestore.collection("destination_tasks").document(task.taskId).set(taskData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markTaskArrived(taskId: String, arrivedAt: Long, actualDurationMinutes: Int): Result<Unit> {
        return try {
            val updates = mapOf<String, Any>(
                "status" to "ARRIVED",
                "arrivedAt" to arrivedAt,
                "actualDurationMinutes" to actualDurationMinutes
            )
            firestore.collection("destination_tasks").document(taskId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun cancelTask(taskId: String): Result<Unit> {
        return try {
            firestore.collection("destination_tasks").document(taskId).update("status", "CANCELLED").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
