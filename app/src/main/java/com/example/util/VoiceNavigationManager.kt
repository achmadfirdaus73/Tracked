package com.example.util

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TurnType(val iconEmoji: String, val defaultText: String) {
    DEPART("", "Mulai perjalanan"),
    STRAIGHT("", "Lurus terus"),
    SLIGHT_LEFT("", "Sedikit ke kiri"),
    SLIGHT_RIGHT("", "Sedikit ke kanan"),
    TURN_LEFT("", "Belok kiri"),
    TURN_RIGHT("", "Belok kanan"),
    SHARP_LEFT("", "Belok tajam ke kiri"),
    SHARP_RIGHT("", "Belok tajam ke kanan"),
    UTURN("", "Putar balik"),
    ARRIVE("", "Tiba di tujuan")
}

data class NavigationManeuver(
    val stepIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val turnType: TurnType,
    val roadName: String,
    val instruction: String,
    val distanceMetersFromStart: Double = 0.0
)

data class LiveNavigationGuidance(
    val nextManeuver: NavigationManeuver?,
    val distanceToNextManeuverMeters: Double,
    val displayInstruction: String,
    val isArrived: Boolean
)

class VoiceNavigationManager(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    var isMuted by mutableStateOf(false)
    var currentGuidance by mutableStateOf<LiveNavigationGuidance?>(null)

    // Speech anti-spam trackers
    private var lastSpokenKey: String = ""
    private var lastSpokenTimestamp: Long = 0L

    // Active route maneuvers
    private var activeManeuvers: List<NavigationManeuver> = emptyList()

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("VoiceNav", "Failed to init TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val indonesian = Locale("id", "ID")
            val result = tts?.setLanguage(indonesian)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to default system language
                tts?.setLanguage(Locale.getDefault())
            }
            tts?.setSpeechRate(1.05f)
            tts?.setPitch(1.0f)
            isTtsReady = true
        } else {
            isTtsReady = false
        }
    }

    /**
     * Initializes a new route and generates turn-by-turn maneuvers from route coordinates and road names.
     */
    fun setRoutePoints(points: List<Pair<Double, Double>>, destinationName: String) {
        if (points.size < 2) {
            activeManeuvers = emptyList()
            currentGuidance = null
            return
        }

        activeManeuvers = generateManeuvers(points, destinationName)
        lastSpokenKey = ""
        lastSpokenTimestamp = 0L

        // Initial announcement
        speak("Navigasi dimulai menuju $destinationName. Tetap hati-hati di jalan.")
    }

    /**
     * Updates real-time user vehicle position along the route.
     * Evaluates the upcoming maneuver and triggers voice speech when crossing distance thresholds.
     */
    fun updateVehicleLocation(userLat: Double, userLng: Double, destLat: Double, destLng: Double) {
        if (activeManeuvers.isEmpty()) return

        val distToDest = calculateDistanceMeters(userLat, userLng, destLat, destLng)
        if (distToDest <= 30.0) {
            currentGuidance = LiveNavigationGuidance(
                nextManeuver = activeManeuvers.lastOrNull(),
                distanceToNextManeuverMeters = distToDest,
                displayInstruction = "Tiba di tujuan",
                isArrived = true
            )
            triggerSpokenGuidance("arrived", "Anda telah sampai di lokasi tujuan.")
            return
        }

        // Find upcoming maneuver ahead of user
        var nextManeuver: NavigationManeuver? = null
        var minDistanceToManeuver = Double.MAX_VALUE

        for (maneuver in activeManeuvers) {
            val dist = calculateDistanceMeters(userLat, userLng, maneuver.latitude, maneuver.longitude)
            // Consider maneuvers ahead (more than 15 meters away)
            if (dist > 15.0 && dist < minDistanceToManeuver) {
                minDistanceToManeuver = dist
                nextManeuver = maneuver
            }
        }

        if (nextManeuver == null) {
            nextManeuver = activeManeuvers.lastOrNull()
            minDistanceToManeuver = distToDest
        }

        if (nextManeuver != null) {
            val displayDist = if (minDistanceToManeuver >= 1000) {
                String.format(Locale.US, "%.1f km", minDistanceToManeuver / 1000.0)
            } else {
                "${minDistanceToManeuver.toInt()} m"
            }

            val instructionText = "${nextManeuver.instruction} ($displayDist)"

            currentGuidance = LiveNavigationGuidance(
                nextManeuver = nextManeuver,
                distanceToNextManeuverMeters = minDistanceToManeuver,
                displayInstruction = instructionText,
                isArrived = false
            )

            // Voice announcement trigger logic
            val stepIdx = nextManeuver.stepIndex
            val actionPhrase = getSpokenActionPhrase(nextManeuver.turnType)

            when {
                // Phase 1: 150m - 250m announcement
                minDistanceToManeuver in 140.0..220.0 -> {
                    val key = "far_${stepIdx}"
                    val spokenText = "${(minDistanceToManeuver / 50).toInt() * 50} meter lagi, $actionPhrase."
                    triggerSpokenGuidance(key, spokenText)
                }
                // Phase 2: Immediate execution (20m - 50m)
                minDistanceToManeuver in 20.0..55.0 -> {
                    val key = "near_${stepIdx}"
                    val spokenText = "Sekarang, $actionPhrase."
                    triggerSpokenGuidance(key, spokenText)
                }
            }
        }
    }

    private fun getSpokenActionPhrase(turnType: TurnType): String {
        return when (turnType) {
            TurnType.DEPART -> "mulai berkendara"
            TurnType.STRAIGHT -> "lurus terus"
            TurnType.SLIGHT_LEFT -> "ambil jalur agak ke kiri"
            TurnType.SLIGHT_RIGHT -> "ambil jalur agak ke kanan"
            TurnType.TURN_LEFT -> "belok kiri"
            TurnType.TURN_RIGHT -> "belok kanan"
            TurnType.SHARP_LEFT -> "belok tajam ke kiri"
            TurnType.SHARP_RIGHT -> "belok tajam ke kanan"
            TurnType.UTURN -> "putar balik"
            TurnType.ARRIVE -> "tiba di tujuan"
        }
    }

    private fun triggerSpokenGuidance(key: String, text: String) {
        val now = System.currentTimeMillis()
        if (key != lastSpokenKey && (now - lastSpokenTimestamp > 4500L)) {
            lastSpokenKey = key
            lastSpokenTimestamp = now
            speak(text)
        }
    }

    fun speak(text: String) {
        if (isMuted || !isTtsReady || text.isBlank()) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GeoTrack_Nav_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            Log.e("VoiceNav", "TTS speak error: ${e.message}")
        }
    }

    fun toggleMute() {
        isMuted = !isMuted
        if (isMuted) {
            tts?.stop()
        } else {
            speak("Suara panduan navigasi aktif")
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {}
        currentGuidance = null
        activeManeuvers = emptyList()
        lastSpokenKey = ""
    }

    fun destroy() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {}
        tts = null
        isTtsReady = false
    }

    /**
     * Generates real turn maneuvers by analyzing route headings and vector angles.
     */
    private fun generateManeuvers(
        points: List<Pair<Double, Double>>,
        destinationName: String
    ): List<NavigationManeuver> {
        val maneuvers = mutableListOf<NavigationManeuver>()
        if (points.size < 2) return maneuvers

        // Step 0: Departure
        maneuvers.add(
            NavigationManeuver(
                stepIndex = 0,
                latitude = points.first().first,
                longitude = points.first().second,
                turnType = TurnType.DEPART,
                roadName = "Titik Keberangkatan",
                instruction = "Mulai ikuti rute jalan",
                distanceMetersFromStart = 0.0
            )
        )

        var accumulatedDist = 0.0
        var lastHeading = calculateBearing(points[0], points[1])

        // Iterate through segments to detect significant heading shifts (turns)
        val sampleStride = (points.size / 30).coerceIn(1, 4)
        for (i in 1 until points.size - 1 step sampleStride) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val pNext = if (i + sampleStride < points.size) points[i + sampleStride] else points.last()

            val segmentDist = calculateDistanceMeters(pPrev.first, pPrev.second, pCurr.first, pCurr.second)
            accumulatedDist += segmentDist

            val newHeading = calculateBearing(pCurr, pNext)
            var angleDiff = newHeading - lastHeading
            while (angleDiff > 180.0) angleDiff -= 360.0
            while (angleDiff < -180.0) angleDiff += 360.0

            val turnType = when {
                angleDiff in 25.0..60.0 -> TurnType.SLIGHT_RIGHT
                angleDiff in 60.0..120.0 -> TurnType.TURN_RIGHT
                angleDiff in 120.0..160.0 -> TurnType.SHARP_RIGHT
                angleDiff in -60.0..-25.0 -> TurnType.SLIGHT_LEFT
                angleDiff in -120.0..-60.0 -> TurnType.TURN_LEFT
                angleDiff in -160.0..-120.0 -> TurnType.SHARP_LEFT
                angleDiff > 160.0 || angleDiff < -160.0 -> TurnType.UTURN
                else -> null
            }

            if (turnType != null && segmentDist > 15.0) {
                val instructionLabel = when (turnType) {
                    TurnType.TURN_RIGHT -> "Belok kanan"
                    TurnType.TURN_LEFT -> "Belok kiri"
                    TurnType.SLIGHT_RIGHT -> "Ambil jalur agak kanan"
                    TurnType.SLIGHT_LEFT -> "Ambil jalur agak kiri"
                    TurnType.SHARP_RIGHT -> "Belok tajam ke kanan"
                    TurnType.SHARP_LEFT -> "Belok tajam ke kiri"
                    TurnType.UTURN -> "Putar balik"
                    else -> "Lurus terus"
                }

                maneuvers.add(
                    NavigationManeuver(
                        stepIndex = maneuvers.size,
                        latitude = pCurr.first,
                        longitude = pCurr.second,
                        turnType = turnType,
                        roadName = "Jalur Berikutnya",
                        instruction = instructionLabel,
                        distanceMetersFromStart = accumulatedDist
                    )
                )
                lastHeading = newHeading
            }
        }

        // Final Step: Arrival at destination
        val lastPoint = points.last()
        val totalDistance = accumulatedDist + calculateDistanceMeters(
            points[points.size - 2].first, points[points.size - 2].second,
            lastPoint.first, lastPoint.second
        )
        maneuvers.add(
            NavigationManeuver(
                stepIndex = maneuvers.size,
                latitude = lastPoint.first,
                longitude = lastPoint.second,
                turnType = TurnType.ARRIVE,
                roadName = destinationName,
                instruction = "Tiba di tujuan: $destinationName",
                distanceMetersFromStart = totalDistance
            )
        )

        return maneuvers
    }

    private fun calculateBearing(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double {
        val lat1 = Math.toRadians(p1.first)
        val lat2 = Math.toRadians(p2.first)
        val dLng = Math.toRadians(p2.second - p1.second)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val bRad = atan2(y, x)
        return (Math.toDegrees(bRad) + 360.0) % 360.0
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
