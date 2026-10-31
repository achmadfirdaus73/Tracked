package com.example.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LocationSearchResult(
    val id: String,
    val name: String,
    val fullAddress: String,
    val latitude: Double,
    val longitude: Double,
    val type: String = "location"
)

object LocationSearchHelper {
    private const val TAG = "LocationSearchHelper"
    private val searchCache = mutableMapOf<String, List<LocationSearchResult>>()

    /**
     * Searches places, streets, buildings, and landmarks using OpenStreetMap Nominatim.
     * 100% Free, zero API key required, supports full Indonesian addresses and landmarks.
     */
    suspend fun searchPlaces(
        query: String,
        anchorLat: Double? = null,
        anchorLng: Double? = null
    ): List<LocationSearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.length < 2) return@withContext emptyList()

        val cacheKey = cleanQuery.lowercase()
        searchCache[cacheKey]?.let { return@withContext it }

        try {
            val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
            var urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=6&countrycodes=id"
            if (anchorLat != null && anchorLng != null) {
                // Prioritize results within ~50km of current map/user center
                val minLng = anchorLng - 0.5
                val minLat = anchorLat - 0.5
                val maxLng = anchorLng + 0.5
                val maxLat = anchorLat + 0.5
                urlString += "&viewbox=$minLng,$maxLat,$maxLng,$minLat"
            }

            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3500
                readTimeout = 3500
                setRequestProperty("User-Agent", "TracePath-FleetTracker/3.0 (Android Enterprise)")
                setRequestProperty("Accept-Language", "id,en")
            }

            if (connection.responseCode == 200) {
                val jsonText = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(jsonText)
                val results = mutableListOf<LocationSearchResult>()

                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)
                    val displayName = obj.optString("display_name", "")
                    val name = obj.optString("name").ifBlank {
                        displayName.split(",").firstOrNull()?.trim() ?: "Lokasi Tujuan"
                    }
                    val type = obj.optString("type", "lokasi")

                    if (lat != 0.0 && lon != 0.0) {
                        results.add(
                            LocationSearchResult(
                                id = "osm_${obj.optString("osm_id", "$i")}",
                                name = name,
                                fullAddress = displayName,
                                latitude = lat,
                                longitude = lon,
                                type = type
                            )
                        )
                    }
                }

                if (results.isNotEmpty()) {
                    searchCache[cacheKey] = results
                }
                return@withContext results
            }
        } catch (_: Exception) {
            // Silently fallback on network error
        }

        return@withContext emptyList()
    }
}
