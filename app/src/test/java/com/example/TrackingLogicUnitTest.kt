package com.example

import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.ZoneType
import com.example.util.CryptoUtil
import com.example.util.ExportUtil
import com.example.util.GeofenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingLogicUnitTest {

    @Test
    fun testCryptoUtilSaltAndVerification() {
        val salt = CryptoUtil.generateSalt()
        assertTrue(salt.length >= 16)

        val password = "SecretEnterprisePassword123"
        val hash = CryptoUtil.hashPassword(password, salt)

        assertTrue(CryptoUtil.verifyPassword(password, salt, hash))
        assertFalse(CryptoUtil.verifyPassword("WrongPassword", salt, hash))
    }

    @Test
    fun testGeofenceEngineDistanceAndInsideDetection() {
        // Jakarta Monas (-6.1754, 106.8272)
        val latCenter = -6.1754
        val lngCenter = 106.8272

        // A point 100 meters away
        val latNear = -6.1760
        val lngNear = 106.8272
        val distance = GeofenceEngine.calculateDistanceMeters(latCenter, lngCenter, latNear, lngNear)
        assertTrue("Distance should be approximately 66m: $distance", distance in 50.0..100.0)

        val fence = GeofenceZone(
            id = "f1",
            companyId = "CORP-TEST",
            name = "Zona Monas",
            latitude = latCenter,
            longitude = lngCenter,
            radiusMeters = 300f,
            zoneType = ZoneType.OFFICE
        )

        val (matchingInside, statusInside) = GeofenceEngine.evaluateGeofences(latNear, lngNear, listOf(fence))
        assertEquals(1, matchingInside.size)
        assertTrue(statusInside.contains("Zona Monas"))

        // A point 5 km away
        val (matchingFar, statusFar) = GeofenceEngine.evaluateGeofences(-6.2500, 106.8272, listOf(fence))
        assertEquals(0, matchingFar.size)
        assertTrue(statusFar.contains("Di luar zona"))
    }

    @Test
    fun testExportCsvAndGeoJson() {
        val points = listOf(
            LocationPoint(
                id = 1,
                userId = "USR-01",
                companyId = "CORP-TEST",
                latitude = -6.2000,
                longitude = 106.8000,
                altitude = 30.0,
                speedKmh = 45f,
                accuracyMeters = 3f,
                bearing = 90f,
                timestamp = 1700000000000L,
                geofenceStatus = "Normal",
                batteryLevel = 90
            ),
            LocationPoint(
                id = 2,
                userId = "USR-01",
                companyId = "CORP-TEST",
                latitude = -6.2050,
                longitude = 106.8050,
                altitude = 32.0,
                speedKmh = 50f,
                accuracyMeters = 2.5f,
                bearing = 95f,
                timestamp = 1700000010000L,
                geofenceStatus = "Di dalam: Kantor",
                batteryLevel = 89
            )
        )

        val csv = ExportUtil.generateCsv(points)
        assertTrue(csv.contains("Latitude,Longitude,AltitudeMeters,SpeedKmH"))
        assertTrue(csv.contains("-6.2000000,106.8000000"))

        val geoJson = ExportUtil.generateGeoJson(points)
        assertTrue(geoJson.contains("\"type\": \"FeatureCollection\""))
        assertTrue(geoJson.contains("\"type\": \"LineString\""))
    }

    @Test
    fun testFakeGpsDetectorNullSafety() {
        val isMock = com.example.util.FakeGpsDetector.isLocationMock(null)
        assertFalse(isMock)
    }
}
