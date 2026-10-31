package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.example.data.local.AppDatabase
import com.example.data.local.LocationPointEntity
import com.example.data.model.GeofenceAlert
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.UserProfile
import com.example.data.repository.TrackingRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

class LocationEngine(
    private val context: Context,
    private val repository: TrackingRepository,
    private val scope: CoroutineScope
) {
    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var realGpsCallback: LocationCallback? = null
    private var currentSessionId: String = ""
    private var activeUser: UserProfile? = null
    private val pendingRouteBatch = mutableListOf<LocationPoint>()

    // Track previous inside/outside state of geofences to detect enter/exit events
    private val previousGeofenceState = mutableMapOf<String, Boolean>()
    private var hasReportedMockLocation = false

    @SuppressLint("MissingPermission")
    fun startTracking(
        user: UserProfile,
        sessionId: String = "",
        onPointRecorded: (LocationPoint) -> Unit = {},
        onAlertTriggered: (GeofenceAlert) -> Unit = {}
    ) {
        stopTracking()
        activeUser = user
        hasReportedMockLocation = false
        currentSessionId = if (sessionId.isNotBlank()) sessionId else java.util.UUID.randomUUID().toString()
        startRealGpsTracking(user, onPointRecorded, onAlertTriggered)
    }

    fun stopTracking() {
        realGpsCallback?.let { callback ->
            fusedClient.removeLocationUpdates(callback)
        }
        realGpsCallback = null

        // Flush any remaining recorded points in buffer to Firestore (1 single final write)
        val user = activeUser
        val batchToFlush = synchronized(pendingRouteBatch) {
            val copy = pendingRouteBatch.toList()
            pendingRouteBatch.clear()
            copy
        }
        if (user != null && batchToFlush.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                repository.syncCompactRouteBatch(user.userId, user.companyId, batchToFlush)
            }
        }

        currentSessionId = ""
    }

    @SuppressLint("MissingPermission")
    private fun startRealGpsTracking(
        user: UserProfile,
        onPointRecorded: (LocationPoint) -> Unit,
        onAlertTriggered: (GeofenceAlert) -> Unit
    ) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val auditResult = FakeGpsDetector.audit(context, loc)
                    val isMock = auditResult.isMockDetected

                    processNewCoordinate(
                        user = user,
                        lat = loc.latitude,
                        lng = loc.longitude,
                        alt = loc.altitude,
                        speedKmh = loc.speed * 3.6f, // m/s to km/h
                        accuracyM = loc.accuracy,
                        bearingDeg = loc.bearing,
                        isMock = isMock,
                        mockReason = auditResult.reason,
                        onPointRecorded = onPointRecorded,
                        onAlertTriggered = onAlertTriggered
                    )
                }
            }
        }
        realGpsCallback = callback

        try {
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            // Handle security exception if permissions denied
        }
    }

    private var lastRecordedLat: Double? = null
    private var lastRecordedLng: Double? = null

    private fun processNewCoordinate(
        user: UserProfile,
        lat: Double,
        lng: Double,
        alt: Double,
        speedKmh: Float,
        accuracyM: Float,
        bearingDeg: Float,
        isMock: Boolean,
        mockReason: String,
        onPointRecorded: (LocationPoint) -> Unit,
        onAlertTriggered: (GeofenceAlert) -> Unit
    ) {
        scope.launch(Dispatchers.IO) {
            // High-performance local GPS smoothing: filter out minor hardware jitter (< 20m accuracy)
            val prevLat = lastRecordedLat
            val prevLng = lastRecordedLng
            val effectiveLat = if (prevLat != null && accuracyM > 20f) {
                0.75 * lat + 0.25 * prevLat
            } else {
                lat
            }
            val effectiveLng = if (prevLng != null && accuracyM > 20f) {
                0.75 * lng + 0.25 * prevLng
            } else {
                lng
            }

            lastRecordedLat = effectiveLat
            lastRecordedLng = effectiveLng

            val geofences = repository.getGeofences(user.companyId)
            val (activeFences, rawStatusDesc) = GeofenceEngine.evaluateGeofences(effectiveLat, effectiveLng, geofences)

            val statusDesc = if (isMock) {
                "⚠️ FAKE GPS TERDETEKSI: $rawStatusDesc"
            } else {
                rawStatusDesc
            }

            // 1. Trigger security alert if Fake GPS detected
            if (isMock && !hasReportedMockLocation) {
                hasReportedMockLocation = true
                val mockAlert = GeofenceAlert(
                    companyId = user.companyId,
                    userId = user.userId,
                    userName = user.fullName,
                    geofenceName = "Sistem Keamanan Anti-Fake GPS",
                    eventType = "FAKE_GPS_TERDETEKSI",
                    timestamp = System.currentTimeMillis(),
                    latitude = lat,
                    longitude = lng,
                    description = "⚠️ Peringatan: User ${user.fullName} terdeteksi menggunakan aplikasi lokasi palsu/Fake GPS ($mockReason)!",
                    isSecurityWarning = true
                )
                repository.recordGeofenceAlert(mockAlert)
                onAlertTriggered(mockAlert)
            } else if (!isMock) {
                hasReportedMockLocation = false
            }

            // 2. Check for standard geofence enter/exit triggers
            for (fence in geofences) {
                val isInsideNow = activeFences.any { it.id == fence.id }
                val wasInsideBefore = previousGeofenceState[fence.id] ?: false

                if (isInsideNow && !wasInsideBefore) {
                    val alert = GeofenceAlert(
                        companyId = user.companyId,
                        userId = user.userId,
                        userName = user.fullName,
                        geofenceName = fence.name,
                        eventType = "MASUK",
                        timestamp = System.currentTimeMillis(),
                        latitude = lat,
                        longitude = lng,
                        description = "User ${user.fullName} memasuki area ${fence.name} (${fence.zoneType.label})",
                        isSecurityWarning = false
                    )
                    repository.recordGeofenceAlert(alert)
                    onAlertTriggered(alert)
                } else if (!isInsideNow && wasInsideBefore) {
                    val alert = GeofenceAlert(
                        companyId = user.companyId,
                        userId = user.userId,
                        userName = user.fullName,
                        geofenceName = fence.name,
                        eventType = "KELUAR",
                        timestamp = System.currentTimeMillis(),
                        latitude = effectiveLat,
                        longitude = effectiveLng,
                        description = "User ${user.fullName} keluar dari area ${fence.name}",
                        isSecurityWarning = false
                    )
                    repository.recordGeofenceAlert(alert)
                    onAlertTriggered(alert)
                }
                previousGeofenceState[fence.id] = isInsideNow
            }

            val batteryStatus = BatteryHelper.getBatteryStatus(context)
            val pt = LocationPoint(
                userId = user.userId,
                companyId = user.companyId,
                sessionId = currentSessionId,
                latitude = effectiveLat,
                longitude = effectiveLng,
                altitude = alt,
                speedKmh = speedKmh,
                accuracyMeters = accuracyM,
                bearing = bearingDeg,
                timestamp = System.currentTimeMillis(),
                geofenceStatus = statusDesc,
                batteryLevel = batteryStatus.percentage,
                isCharging = batteryStatus.isCharging,
                isMock = isMock
            )

            // 1. Zero-Cost Live Streaming via MQTT (Instant 20-50ms latency, 0 database writes!)
            MqttManager.publishLocation(
                companyId = user.companyId,
                userId = user.userId,
                fullName = user.fullName,
                unitCode = user.unitCode,
                latitude = effectiveLat,
                longitude = effectiveLng,
                speedKmh = speedKmh,
                bearing = bearingDeg,
                batteryLevel = batteryStatus.percentage,
                isCharging = batteryStatus.isCharging,
                geofenceStatus = statusDesc,
                isMock = isMock
            )

            // 2. Persist full route breadcrumb into local Room Database
            try {
                val db = AppDatabase.getInstance(context)
                val entity = LocationPointEntity(
                    userId = user.userId,
                    companyId = user.companyId,
                    sessionId = currentSessionId,
                    latitude = effectiveLat,
                    longitude = effectiveLng,
                    altitude = alt,
                    speedKmh = speedKmh,
                    accuracyMeters = accuracyM,
                    bearing = bearingDeg,
                    timestamp = pt.timestamp,
                    geofenceStatus = statusDesc,
                    batteryLevel = batteryStatus.percentage,
                    isMock = isMock
                )
                db.locationPointDao().insertPoint(entity)
                db.userDao().updateLocation(
                    userId = user.userId,
                    lat = effectiveLat,
                    lng = effectiveLng,
                    speed = speedKmh,
                    timestamp = pt.timestamp,
                    hasFakeGps = isMock
                )
            } catch (e: Exception) {
                // Ignore local DB failure
            }

            // 3. Compact Route Cloud Sync:
            // Buffer points and push 60 points in 1 single Firestore write (every ~2-3 minutes)
            val batchToSync = synchronized(pendingRouteBatch) {
                pendingRouteBatch.add(pt)
                if (pendingRouteBatch.size >= 60) {
                    val batch = pendingRouteBatch.toList()
                    pendingRouteBatch.clear()
                    batch
                } else null
            }
            if (batchToSync != null) {
                repository.syncCompactRouteBatch(user.userId, user.companyId, batchToSync)
            }

            // 4. Milestone sync to Firestore (throttled to 0-cost checkpoints)
            repository.recordLocationPoint(pt, user.fullName)
            onPointRecorded(pt)
        }
    }
}
