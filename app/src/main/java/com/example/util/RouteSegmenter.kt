package com.example.util

import com.example.data.model.LocationPoint

/**
 * RouteSegmenter: Intelligent Trip & Session Segmentation Engine.
 *
 * Solves the "teleport line" issue:
 * When a user stops tracking or goes off-duty at location A, travels elsewhere,
 * and resumes location sharing at location C:
 * 1. Points are divided into discrete segments based on:
 *    - Session ID boundaries (new tracking session assigned upon turning sharelok ON)
 *    - Time gap between consecutive points > 15 minutes (900,000 ms)
 *    - Teleport distance gap > 400 meters with time delta > 2 minutes (120,000 ms)
 *    - Extreme jump > 2,000 meters regardless of elapsed time
 * 2. Map rendering engines (OpenStreetMap & InteractiveCanvas) render polylines
 *    strictly within each segment, omitting any connecting line between A and C.
 * 3. Odometer & analytics accurately sum distance and active duration only within
 *    continuous segments, preventing phantom kilometers from corrupting trip logs.
 */
object RouteSegmenter {

    const val MAX_TIME_GAP_MS = 15 * 60 * 1000L // 15 minutes
    const val TELEPORT_DISTANCE_METERS = 400.0   // 400 meters
    const val TELEPORT_TIME_MS = 2 * 60 * 1000L  // 2 minutes
    const val EXTREME_JUMP_METERS = 2000.0       // 2 km

    /**
     * Splits a flat list of location points into segmented continuous journeys.
     */
    fun segmentRoute(
        points: List<LocationPoint>,
        maxTimeGapMs: Long = MAX_TIME_GAP_MS,
        teleportDistanceMeters: Double = TELEPORT_DISTANCE_METERS,
        teleportTimeMs: Long = TELEPORT_TIME_MS,
        extremeDistanceMeters: Double = EXTREME_JUMP_METERS
    ): List<List<LocationPoint>> {
        if (points.isEmpty()) return emptyList()
        val sortedPoints = points.sortedBy { it.timestamp }
        val segments = mutableListOf<MutableList<LocationPoint>>()
        var currentSegment = mutableListOf<LocationPoint>()

        for (pt in sortedPoints) {
            if (currentSegment.isEmpty()) {
                currentSegment.add(pt)
            } else {
                val prev = currentSegment.last()
                val sessionMismatched = pt.sessionId.isNotBlank() &&
                        prev.sessionId.isNotBlank() &&
                        pt.sessionId != prev.sessionId

                val timeGapMs = pt.timestamp - prev.timestamp
                val distanceM = RoadNetworkEngine.calculateDistanceMeters(
                    prev.latitude, prev.longitude,
                    pt.latitude, pt.longitude
                )

                val isTimeGap = timeGapMs > maxTimeGapMs
                val isTeleportGap = (distanceM > teleportDistanceMeters && timeGapMs > teleportTimeMs) ||
                        distanceM > extremeDistanceMeters

                if (sessionMismatched || isTimeGap || isTeleportGap) {
                    segments.add(currentSegment)
                    currentSegment = mutableListOf(pt)
                } else {
                    currentSegment.add(pt)
                }
            }
        }

        if (currentSegment.isNotEmpty()) {
            segments.add(currentSegment)
        }

        return segments
    }

    /**
     * Computes the actual physical odometer distance (in kilometers),
     * strictly within verified continuous segments. Phantom jumps between
     * inactive gaps (such as A to C) are entirely excluded.
     */
    fun calculateSegmentedDistanceKm(points: List<LocationPoint>): Double {
        val segments = segmentRoute(points)
        var totalMeters = 0.0
        for (segment in segments) {
            for (i in 0 until segment.size - 1) {
                totalMeters += RoadNetworkEngine.calculateDistanceMeters(
                    segment[i].latitude, segment[i].longitude,
                    segment[i + 1].latitude, segment[i + 1].longitude
                )
            }
        }
        return totalMeters / 1000.0
    }

    /**
     * Computes total active travel time (in minutes) across segments,
     * ignoring the idle/off-duty duration between trips.
     */
    fun calculateActiveDurationMinutes(points: List<LocationPoint>): Long {
        val segments = segmentRoute(points)
        var totalMs = 0L
        for (segment in segments) {
            if (segment.size >= 2) {
                totalMs += (segment.last().timestamp - segment.first().timestamp)
            }
        }
        return (totalMs / 60000L).coerceAtLeast(0L)
    }
}
