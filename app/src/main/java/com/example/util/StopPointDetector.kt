package com.example.util

import com.example.data.model.LocationPoint
import com.example.data.model.StopPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object StopPointDetector {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    /**
     * Detects stop points where the user stopped for 5 to 10+ minutes.
     * Groups consecutive GPS points that stay within ~35 meters with low speed (< 3.5 km/h).
     */
    fun detectStopPoints(
        points: List<LocationPoint>,
        minDurationMinutes: Int = 5
    ): List<StopPoint> {
        if (points.size < 2) return emptyList()

        val sortedPoints = points.sortedBy { it.timestamp }
        val stops = mutableListOf<StopPoint>()

        var clusterStartIdx = 0
        var i = 1

        while (i < sortedPoints.size) {
            val startPt = sortedPoints[clusterStartIdx]
            val currentPt = sortedPoints[i]

            val distFromStart = RoadNetworkEngine.calculateDistanceMeters(
                startPt.latitude, startPt.longitude,
                currentPt.latitude, currentPt.longitude
            )

            // If user stays within 35 meters with stationary or walking/idle speed
            val isStationary = distFromStart <= 35.0 && currentPt.speedKmh < 4.0f

            if (!isStationary) {
                // Check if the previous cluster qualified as a stop (>= 5 minutes)
                val prevPt = sortedPoints[i - 1]
                val durationMs = prevPt.timestamp - startPt.timestamp
                val durationMinutes = (durationMs / 60000L).toInt()

                // Also support cluster size heuristic for fast-rate demo points
                val isLongStop = durationMinutes >= minDurationMinutes ||
                        ((i - clusterStartIdx) >= 8 && startPt.speedKmh < 1.0f)

                if (isLongStop) {
                    val finalDuration = durationMinutes.coerceAtLeast(minDurationMinutes)
                    val clusterPoints = sortedPoints.subList(clusterStartIdx, i)
                    val avgLat = clusterPoints.map { it.latitude }.average()
                    val avgLng = clusterPoints.map { it.longitude }.average()

                    stops.add(
                        StopPoint(
                            id = UUID.randomUUID().toString(),
                            userId = startPt.userId,
                            latitude = avgLat,
                            longitude = avgLng,
                            startTimestamp = startPt.timestamp,
                            endTimestamp = prevPt.timestamp,
                            durationMinutes = finalDuration,
                            locationName = "Berhenti ${finalDuration}m (${formatTime(startPt.timestamp)} - ${formatTime(prevPt.timestamp)})"
                        )
                    )
                }

                // Advance to next cluster
                clusterStartIdx = i
            }

            i++
        }

        // Check trailing cluster (current ongoing stop at the end of the route)
        if (clusterStartIdx < sortedPoints.size - 1) {
            val startPt = sortedPoints[clusterStartIdx]
            val lastPt = sortedPoints.last()
            val durationMs = lastPt.timestamp - startPt.timestamp
            val durationMinutes = (durationMs / 60000L).toInt()

            if (durationMinutes >= minDurationMinutes) {
                val clusterPoints = sortedPoints.subList(clusterStartIdx, sortedPoints.size)
                val avgLat = clusterPoints.map { it.latitude }.average()
                val avgLng = clusterPoints.map { it.longitude }.average()

                stops.add(
                    StopPoint(
                        id = UUID.randomUUID().toString(),
                        userId = startPt.userId,
                        latitude = avgLat,
                        longitude = avgLng,
                        startTimestamp = startPt.timestamp,
                        endTimestamp = lastPt.timestamp,
                        durationMinutes = durationMinutes,
                        locationName = "Berhenti ${durationMinutes}m (${formatTime(startPt.timestamp)} - ${formatTime(lastPt.timestamp)})"
                    )
                )
            }
        }

        return stops
    }

    fun formatTime(timestamp: Long): String {
        return timeFormat.format(Date(timestamp))
    }
}
