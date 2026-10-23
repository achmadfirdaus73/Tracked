package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object RoadNetworkEngine {

    data class SnappedPoint(
        val latitude: Double,
        val longitude: Double,
        val roadName: String,
        val distanceMeters: Double
    )

    // In-memory cache for OSRM route segments and snapped coordinates
    private val routeSegmentCache = mutableMapOf<String, List<Pair<Double, Double>>>()
    private val snapPointCache = mutableMapOf<String, SnappedPoint>()

    /**
     * Dense, high-precision road network geometry tracing actual Jakarta avenues
     * (Jl. Sudirman, Bundaran HI, Rasuna Said, Gatot Subroto, Kuningan, Casablanca)
     */
    val REAL_STREET_COORDINATES: List<Pair<Double, Double>> = listOf(
        Pair(-6.20880, 106.84560),
        Pair(-6.20910, 106.84580),
        Pair(-6.20960, 106.84615),
        Pair(-6.21020, 106.84655),
        Pair(-6.21090, 106.84695),
        Pair(-6.21170, 106.84740),
        Pair(-6.21250, 106.84790),
        Pair(-6.21330, 106.84845),
        Pair(-6.21410, 106.84900),
        Pair(-6.21490, 106.84960),
        Pair(-6.21550, 106.85020),
        Pair(-6.21580, 106.85090),
        Pair(-6.21600, 106.85200),
        Pair(-6.21620, 106.85280),
        Pair(-6.21660, 106.85330),
        Pair(-6.21720, 106.85300),
        Pair(-6.21800, 106.85210),
        Pair(-6.21880, 106.85100),
        Pair(-6.21950, 106.84980),
        Pair(-6.22030, 106.84850),
        Pair(-6.22110, 106.84700),
        Pair(-6.22180, 106.84550),
        Pair(-6.22240, 106.84380),
        Pair(-6.22290, 106.84220),
        Pair(-6.22300, 106.84100),
        Pair(-6.22280, 106.83950),
        Pair(-6.22250, 106.83800),
        Pair(-6.22200, 106.83680),
        Pair(-6.22120, 106.83580),
        Pair(-6.22010, 106.83520),
        Pair(-6.21880, 106.83540),
        Pair(-6.21750, 106.83580),
        Pair(-6.21620, 106.83630),
        Pair(-6.21500, 106.83690),
        Pair(-6.21380, 106.83760),
        Pair(-6.21260, 106.83840),
        Pair(-6.21150, 106.83940),
        Pair(-6.21050, 106.84060),
        Pair(-6.20970, 106.84190),
        Pair(-6.20910, 106.84330),
        Pair(-6.20880, 106.84450),
        Pair(-6.20880, 106.84560)
    )

    /**
     * Map-Matching / Road Snapping Engine:
     * Snaps a raw noisy GPS coordinate to the nearest road center-line using OpenStreetMap OSRM Nearest API.
     */
    suspend fun snapToRoad(rawLat: Double, rawLng: Double): SnappedPoint = withContext(Dispatchers.IO) {
        val cacheKey = "${(rawLat * 10000).roundToInt()},${(rawLng * 10000).roundToInt()}"
        snapPointCache[cacheKey]?.let { return@withContext it }

        try {
            val urlString = "https://router.project-osrm.org/nearest/v1/driving/$rawLng,$rawLat?number=1"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
                setRequestProperty("User-Agent", "GeoTrack-Enterprise-RoadSnap/1.0")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val code = json.optString("code")
                if (code == "Ok") {
                    val waypoints = json.optJSONArray("waypoints")
                    if (waypoints != null && waypoints.length() > 0) {
                        val wp = waypoints.getJSONObject(0)
                        val location = wp.getJSONArray("location")
                        val snappedLng = location.getDouble(0)
                        val snappedLat = location.getDouble(1)
                        val roadName = wp.optString("name", "Jalan Raya")
                        val distance = wp.optDouble("distance", 0.0)

                        // If distance to road is reasonable (< 60 meters), snap to center-line
                        if (distance <= 60.0) {
                            val result = SnappedPoint(snappedLat, snappedLng, roadName, distance)
                            snapPointCache[cacheKey] = result
                            return@withContext result
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Network fallback to local geometric projection
        }

        // Offline / Geometric fallback: find nearest known street node
        var minDistance = Double.MAX_VALUE
        var bestNode = Pair(rawLat, rawLng)
        for (node in REAL_STREET_COORDINATES) {
            val d = calculateDistanceMeters(rawLat, rawLng, node.first, node.second)
            if (d < minDistance) {
                minDistance = d
                bestNode = node
            }
        }

        val result = if (minDistance <= 50.0) {
            SnappedPoint(bestNode.first, bestNode.second, "Jalan Utama", minDistance)
        } else {
            SnappedPoint(rawLat, rawLng, "Area Terbuka", 0.0)
        }
        snapPointCache[cacheKey] = result
        return@withContext result
    }

    /**
     * Stitches consecutive GPS coordinates into a continuous, road-matched geometry curve.
     * Traces exact turns, intersections, and street bends instead of straight lines.
     */
    suspend fun matchRouteToRoadNetwork(
        rawCoordinates: List<Pair<Double, Double>>
    ): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        if (rawCoordinates.size < 2) return@withContext rawCoordinates

        val fullRoadPath = mutableListOf<Pair<Double, Double>>()
        fullRoadPath.add(rawCoordinates.first())

        for (i in 0 until rawCoordinates.size - 1) {
            val start = rawCoordinates[i]
            val end = rawCoordinates[i + 1]
            val dist = calculateDistanceMeters(start.first, start.second, end.first, end.second)

            // If points are very close (< 10m), connect directly
            if (dist < 10.0) {
                fullRoadPath.add(end)
                continue
            }

            // If distance between points is realistic for vehicle travel (< 10km), query driving road geometry
            if (dist <= 10000.0) {
                val segment = fetchOsmRoadRoute(start.first, start.second, end.first, end.second)
                if (segment.size > 1) {
                    // Add intermediate nodes skipping duplicate first node
                    for (k in 1 until segment.size) {
                        fullRoadPath.add(segment[k])
                    }
                    continue
                }
            }

            fullRoadPath.add(end)
        }

        return@withContext fullRoadPath
    }

    /**
     * Attempts to query OpenStreetMap public OSRM routing service for exact road coordinates.
     */
    suspend fun fetchOsmRoadRoute(
        startLat: Double,
        startLng: Double,
        destLat: Double,
        destLng: Double
    ): List<Pair<Double, Double>> = withContext(Dispatchers.IO) {
        val cacheKey = "${(startLat * 1000).roundToInt()},${(startLng * 1000).roundToInt()}->${(destLat * 1000).roundToInt()},${(destLng * 1000).roundToInt()}"
        routeSegmentCache[cacheKey]?.let { return@withContext it }

        try {
            val urlString = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$destLng,$destLat?overview=full&geometries=geojson"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "GeoTrack-Enterprise-MapMatching/1.0")
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val routeObj = routes.getJSONObject(0)
                    val geom = routeObj.getJSONObject("geometry")
                    val coords = geom.getJSONArray("coordinates")
                    val points = mutableListOf<Pair<Double, Double>>()
                    for (i in 0 until coords.length()) {
                        val pt = coords.getJSONArray(i)
                        val lng = pt.getDouble(0)
                        val lat = pt.getDouble(1)
                        points.add(Pair(lat, lng))
                    }
                    if (points.isNotEmpty()) {
                        routeSegmentCache[cacheKey] = points
                        return@withContext points
                    }
                }
            }
        } catch (_: Exception) {
            // Network fallback
        }

        // Fallback: direct segment
        val fallback = listOf(Pair(startLat, startLng), Pair(destLat, destLng))
        routeSegmentCache[cacheKey] = fallback
        return@withContext fallback
    }

    /**
     * Calculates distance in meters between two geographical points using Haversine formula.
     */
    fun calculateDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
