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

    @Test
    fun testRouteSegmenterSplitBySessionId() {
        // Point 1 and 2 at location A in Session 1
        val pt1 = LocationPoint(
            id = 1,
            userId = "USR-01",
            companyId = "CORP",
            sessionId = "SESSION-A",
            latitude = -6.2000,
            longitude = 106.8000,
            timestamp = 1000L
        )
        val pt2 = LocationPoint(
            id = 2,
            userId = "USR-01",
            companyId = "CORP",
            sessionId = "SESSION-A",
            latitude = -6.2005,
            longitude = 106.8005,
            timestamp = 2000L
        )
        // User turns off sharelok, travels to C (5 km away), and turns sharelok on with Session 2
        val pt3 = LocationPoint(
            id = 3,
            userId = "USR-01",
            companyId = "CORP",
            sessionId = "SESSION-C",
            latitude = -6.2500,
            longitude = 106.8500,
            timestamp = 5000L
        )
        val pt4 = LocationPoint(
            id = 4,
            userId = "USR-01",
            companyId = "CORP",
            sessionId = "SESSION-C",
            latitude = -6.2505,
            longitude = 106.8505,
            timestamp = 6000L
        )

        val segments = com.example.util.RouteSegmenter.segmentRoute(listOf(pt1, pt2, pt3, pt4))
        assertEquals("Should be split into 2 distinct segments", 2, segments.size)
        assertEquals(2, segments[0].size)
        assertEquals(2, segments[1].size)

        // Segmented distance should ONLY sum within Segment 1 and within Segment 2
        val segmentedDistance = com.example.util.RouteSegmenter.calculateSegmentedDistanceKm(listOf(pt1, pt2, pt3, pt4))
        // Distance pt1->pt2 is ~78 meters, pt3->pt4 is ~78 meters. Total < 0.2 km.
        // If unsegmented, it would include the 7.5 km leap between pt2 and pt3!
        assertTrue("Segmented distance should be under 0.3 km (was $segmentedDistance km)", segmentedDistance < 0.3)
    }

    @Test
    fun testRouteSegmenterSplitByTimeGap() {
        val pt1 = LocationPoint(
            id = 1,
            userId = "USR-01",
            companyId = "CORP",
            latitude = -6.2000,
            longitude = 106.8000,
            timestamp = 1000000L
        )
        // Next point 30 minutes later (> 15 min gap threshold)
        val pt2 = LocationPoint(
            id = 2,
            userId = "USR-01",
            companyId = "CORP",
            latitude = -6.2020,
            longitude = 106.8020,
            timestamp = 1000000L + (30 * 60 * 1000L)
        )

        val segments = com.example.util.RouteSegmenter.segmentRoute(listOf(pt1, pt2))
        assertEquals("30-minute inactivity gap must split into 2 segments", 2, segments.size)
    }

    @Test
    fun testRouteSegmenterSplitByExtremeJump() {
        val pt1 = LocationPoint(
            id = 1,
            userId = "USR-01",
            companyId = "CORP",
            latitude = -6.2000,
            longitude = 106.8000,
            timestamp = 1000L
        )
        // Jump > 5 km away
        val pt2 = LocationPoint(
            id = 2,
            userId = "USR-01",
            companyId = "CORP",
            latitude = -6.2700,
            longitude = 106.8000,
            timestamp = 3000L
        )

        val segments = com.example.util.RouteSegmenter.segmentRoute(listOf(pt1, pt2))
        assertEquals("Extreme distance jump must split into 2 segments", 2, segments.size)
    }

    @Test
    fun testRouteSegmenterKeepSameSegmentWhenStoppingAtSameLocation() {
        // User stops at cafe / parking for 45 minutes without moving location (< 20 meters)
        val pt1 = LocationPoint(
            id = 1,
            userId = "USR-01",
            companyId = "CORP",
            latitude = -6.200000,
            longitude = 106.800000,
            timestamp = 1000000L
        )
        val pt2 = LocationPoint(
            id = 2,
            userId = "USR-01",
            companyId = "CORP",
            latitude = -6.200050,
            longitude = 106.800050, // ~8 meters away
            timestamp = 1000000L + (45 * 60 * 1000L) // 45 minutes later
        )

        val segments = com.example.util.RouteSegmenter.segmentRoute(listOf(pt1, pt2))
        assertEquals("Stationary stop at same place must stay in 1 continuous trip", 1, segments.size)
        assertEquals(2, segments[0].size)
    }
}
