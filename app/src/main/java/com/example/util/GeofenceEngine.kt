package com.example.util

import com.example.data.model.GeofenceZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeofenceEngine {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * Calculates distance between two GPS coordinates in meters using the Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * Checks which geofences contain the given coordinate.
     * Returns a pair of (Active geofences, Geofence status summary string)
     */
    fun evaluateGeofences(
        latitude: Double,
        longitude: Double,
        geofences: List<GeofenceZone>
    ): Pair<List<GeofenceZone>, String> {
        val matching = mutableListOf<GeofenceZone>()
        for (fence in geofences) {
            val dist = calculateDistanceMeters(latitude, longitude, fence.latitude, fence.longitude)
            if (dist <= fence.radiusMeters) {
                matching.add(fence)
            }
        }

        val status = if (matching.isEmpty()) {
            "Di luar zona geofencing"
        } else {
            "Di dalam: " + matching.joinToString(", ") { it.name }
        }
        return Pair(matching, status)
    }

    /**
     * Calculates initial bearing from (lat1, lon1) to (lat2, lon2) in degrees [0, 360).
     */
    fun calculateBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        val theta = atan2(y, x)
        val bearing = (Math.toDegrees(theta) + 360.0) % 360.0
        return bearing.toFloat()
    }
}
