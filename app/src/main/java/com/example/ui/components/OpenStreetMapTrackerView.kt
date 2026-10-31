package com.example.ui.components

import android.content.Context
import android.graphics.Paint
import android.preference.PreferenceManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.util.RoadNetworkEngine
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.VehicleType
import com.example.util.MapMarkerHelper
import com.example.util.RouteSegmenter
import com.example.util.StopPointDetector
import com.example.ui.theme.GeoBluePrimary
import com.example.ui.theme.GeoCyanAccent
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.example.data.model.DestinationTask
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Composable
fun OpenStreetMapTrackerView(
    currentPoint: LocationPoint?,
    routePoints: List<LocationPoint>,
    geofences: List<GeofenceZone>,
    modifier: Modifier = Modifier,
    destinationTask: DestinationTask? = null,
    userLabel: String = "User",
    vehicleType: VehicleType = VehicleType.MOTORCYCLE,
    isLiveTracking: Boolean = true,
    compassHeading: Float = 0f,
    isUserMode: Boolean = false,
    showTopStatusPill: Boolean = !isUserMode,
    focusedGeoPoint: GeoPoint? = null,
    onMapTapped: ((Double, Double) -> Unit)? = null,
    onMapCenterRequested: () -> Unit = {}
) {
    val context = LocalContext.current

    // Initialize osmdroid configuration
    DisposableEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK) // Free OpenStreetMap tiles
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            setOnTouchListener { v, _ ->
                v.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
            val initialZoom = if (isUserMode && isLiveTracking) 18.0 else 16.5
            controller.setZoom(initialZoom)
            val initialLat = currentPoint?.latitude ?: routePoints.lastOrNull()?.latitude ?: -6.2088
            val initialLng = currentPoint?.longitude ?: routePoints.lastOrNull()?.longitude ?: 106.8456
            controller.setCenter(GeoPoint(initialLat, initialLng))
        }
    }

    val routeSegments = remember(routePoints) {
        RouteSegmenter.segmentRoute(routePoints)
    }

    // Direct, zero-lag conversion of GPS breadcrumbs to map coordinates (follows real roads naturally)
    val displayGeoSegments = remember(routeSegments) {
        routeSegments.map { seg -> seg.map { GeoPoint(it.latitude, it.longitude) } }
    }

    // Detect Stop Points (periods of staying stationary for 5-10+ minutes)
    val detectedStops = remember(routePoints) {
        StopPointDetector.detectStopPoints(routePoints, minDurationMinutes = 5)
    }

    // Cache vehicle marker drawable so it doesn't re-allocate Bitmaps every frame
    val cachedVehicleDrawable = remember(vehicleType, isLiveTracking, currentPoint?.isMock) {
        MapMarkerHelper.createVehicleMarkerDrawable(
            context = context,
            vehicleType = vehicleType,
            isLive = isLiveTracking,
            isMock = currentPoint?.isMock == true
        )
    }

    val cachedStartDrawable = remember { MapMarkerHelper.createStartMarkerDrawable(context) }

    // Auto zoom-in when live tracking is active on the user side
    LaunchedEffect(isLiveTracking, isUserMode) {
        if (isUserMode && isLiveTracking && currentPoint != null) {
            mapView.controller.setZoom(18.0)
            mapView.controller.animateTo(GeoPoint(currentPoint.latitude, currentPoint.longitude))
        }
    }

    // Focus on specific location if requested (e.g. tapping a stop point or recenter)
    LaunchedEffect(focusedGeoPoint) {
        if (focusedGeoPoint != null) {
            mapView.controller.animateTo(focusedGeoPoint)
        }
    }

    var destinationRoadGeoPoints by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var alternativeRoutesGeoPoints by remember { mutableStateOf<List<List<GeoPoint>>>(emptyList()) }
    var chosenRouteIndex by remember { mutableStateOf(0) }
    var lastRoutedStartLat by remember { mutableStateOf<Double?>(null) }
    var lastRoutedStartLng by remember { mutableStateOf<Double?>(null) }

    // Fetch up to 3 alternative road routes for destination task when destination or vehicle position changes
    LaunchedEffect(destinationTask?.taskId, destinationTask?.latitude, destinationTask?.longitude, destinationTask?.selectedRouteIndex) {
        if (destinationTask != null && currentPoint != null) {
            val dist = if (lastRoutedStartLat != null && lastRoutedStartLng != null) {
                com.example.util.GeofenceEngine.calculateDistanceMeters(
                    lastRoutedStartLat!!, lastRoutedStartLng!!,
                    currentPoint.latitude, currentPoint.longitude
                )
            } else Double.MAX_VALUE

            if (dist > 150.0 || destinationRoadGeoPoints.isEmpty()) {
                val routes = withContext(Dispatchers.IO) {
                    RoadNetworkEngine.fetchAlternativeRoutes(
                        startLat = currentPoint.latitude,
                        startLng = currentPoint.longitude,
                        destLat = destinationTask.latitude,
                        destLng = destinationTask.longitude,
                        vehicleType = vehicleType
                    )
                }
                alternativeRoutesGeoPoints = routes.map { r -> r.points.map { GeoPoint(it.first, it.second) } }
                chosenRouteIndex = destinationTask.selectedRouteIndex.coerceIn(0, (routes.size - 1).coerceAtLeast(0))
                val roadPairs = routes.getOrNull(chosenRouteIndex)?.points ?: routes.firstOrNull()?.points ?: emptyList()
                destinationRoadGeoPoints = roadPairs.map { GeoPoint(it.first, it.second) }
                lastRoutedStartLat = currentPoint.latitude
                lastRoutedStartLng = currentPoint.longitude
            }
        } else {
            destinationRoadGeoPoints = emptyList()
            alternativeRoutesGeoPoints = emptyList()
            lastRoutedStartLat = null
            lastRoutedStartLng = null
        }
    }

    // Camera centering behavior:
    // In User Mode (driver navigation): Keep camera centered on vehicle position.
    // In Admin Mode: The camera NEVER auto-centers or moves on its own while inspecting/panning!
    // Centering in Admin mode is strictly user-driven (via focusedGeoPoint, chip selection, or recenter button).
    LaunchedEffect(currentPoint?.latitude, currentPoint?.longitude) {
        if (currentPoint != null && isUserMode && isLiveTracking) {
            mapView.controller.animateTo(GeoPoint(currentPoint.latitude, currentPoint.longitude))
        }
    }

    // Rotate marker smoothly when compass heading changes
    LaunchedEffect(compassHeading) {
        if (compassHeading > 0f) {
            mapView.invalidate()
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize().clipToBounds().testTag("osm_map_view"),
            update = { map ->
                map.overlays.clear()

                // Tap event receiver for setting destination pin directly on the map
                if (onMapTapped != null) {
                    val receiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                            onMapTapped.invoke(p.latitude, p.longitude)
                            return true
                        }
                        override fun longPressHelper(p: GeoPoint): Boolean {
                            onMapTapped.invoke(p.latitude, p.longitude)
                            return true
                        }
                    }
                    map.overlays.add(MapEventsOverlay(receiver))
                }

                // 1. Render Geofence circles on OpenStreetMap
                for (fence in geofences) {
                    val circleCenter = GeoPoint(fence.latitude, fence.longitude)
                    val circlePoints = Polygon.pointsAsCircle(circleCenter, fence.radiusMeters.toDouble())
                    val polygon = Polygon(map).apply {
                        points = circlePoints
                        val colorInt = try {
                            android.graphics.Color.parseColor(fence.colorHex)
                        } catch (_: Exception) {
                            android.graphics.Color.parseColor("#0F52BA")
                        }
                        fillPaint.color = android.graphics.Color.argb(45, android.graphics.Color.red(colorInt), android.graphics.Color.green(colorInt), android.graphics.Color.blue(colorInt))
                        outlinePaint.color = colorInt
                        outlinePaint.strokeWidth = 3f
                        title = "${fence.name} (${fence.radiusMeters.toInt()}m)"
                    }
                    map.overlays.add(polygon)
                }

                // 2. Render Road-Following Route Polylines on OpenStreetMap (Segmented without teleport lines)
                for ((segIdx, segPoints) in displayGeoSegments.withIndex()) {
                    if (segPoints.size > 1) {
                        // Outer road casing outline
                        val casingPolyline = Polyline(map).apply {
                            setPoints(segPoints)
                            outlinePaint.color = android.graphics.Color.parseColor("#0C2340") // Deep Navy casing
                            outlinePaint.strokeWidth = 13f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        map.overlays.add(casingPolyline)

                        // Core road-following navigation line (Enterprise Cyan-Blue)
                        val polyline = Polyline(map).apply {
                            setPoints(segPoints)
                            outlinePaint.color = android.graphics.Color.parseColor("#0284C7") // Sky/Cyan Blue
                            outlinePaint.strokeWidth = 9f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        map.overlays.add(polyline)

                        // Start marker for this segment (Emerald Green START Pin)
                        val startGeo = segPoints.first()
                        val startPt = routeSegments.getOrNull(segIdx)?.firstOrNull()
                        val startMarker = Marker(map).apply {
                            position = startGeo
                            icon = cachedStartDrawable
                            title = if (segIdx == 0) "🏁 Titik Awal Keberangkatan" else "🏁 Mulai Trip #${segIdx + 1}"
                            snippet = if (startPt != null) "Mulai: ${StopPointDetector.formatTime(startPt.timestamp)}" else null
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        map.overlays.add(startMarker)
                    }
                }

                // 2b. Stop Point markers (Stops >= 5/10 minutes)
                for (stop in detectedStops) {
                    val stopMarker = Marker(map).apply {
                        position = GeoPoint(stop.latitude, stop.longitude)
                        icon = MapMarkerHelper.createStopMarkerDrawable(context, stop.durationMinutes)
                        title = "🛑 Titik Berhenti: ${stop.durationMinutes} Menit"
                        snippet = "Waktu: ${StopPointDetector.formatTime(stop.startTimestamp)} - ${StopPointDetector.formatTime(stop.endTimestamp)}"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(stopMarker)
                }

                // 3. Render Live User GPS Position Marker (Admin-Selected Vehicle Pin: Motor/Mobil/Sepeda/Truk)
                if (currentPoint != null) {
                    val effectiveBearing = if (compassHeading > 0f) compassHeading else currentPoint.bearing
                    val userMarker = Marker(map).apply {
                        position = GeoPoint(currentPoint.latitude, currentPoint.longitude)
                        icon = cachedVehicleDrawable
                        title = if (currentPoint.isMock) "⚠️ $userLabel [FAKE GPS!]" else "${vehicleType.iconEmoji} $userLabel (${vehicleType.label})"
                        snippet = String.format(
                            "%.0f km/h | %s | Akurasi: ±%.1fm",
                            currentPoint.speedKmh,
                            currentPoint.geofenceStatus,
                            currentPoint.accuracyMeters
                        )
                        rotation = effectiveBearing
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(userMarker)
                }

                // 4. Render Destination Task Target Pin & Guidance Line
                if (destinationTask != null) {
                    val destGeo = GeoPoint(destinationTask.latitude, destinationTask.longitude)
                    val isArrived = destinationTask.status == "ARRIVED"

                    // Road-following Navigation Line from current vehicle to destination
                    if (currentPoint != null) {
                        // 4a. Draw secondary alternative routes (if 2 or 3 alternatives exist and not arrived)
                        if (alternativeRoutesGeoPoints.size > 1 && !isArrived) {
                            alternativeRoutesGeoPoints.forEachIndexed { rIdx, altPts ->
                                if (rIdx != chosenRouteIndex && altPts.isNotEmpty()) {
                                    val altPolyline = Polyline(map).apply {
                                        setPoints(altPts)
                                        outlinePaint.color = android.graphics.Color.parseColor("#94A3B8")
                                        outlinePaint.strokeWidth = 6f
                                        outlinePaint.strokeCap = Paint.Cap.ROUND
                                        outlinePaint.strokeJoin = Paint.Join.ROUND
                                    }
                                    map.overlays.add(altPolyline)
                                }
                            }
                        }

                        val pointsToUse = if (destinationRoadGeoPoints.isNotEmpty()) {
                            destinationRoadGeoPoints
                        } else {
                            listOf(GeoPoint(currentPoint.latitude, currentPoint.longitude), destGeo)
                        }

                        // Outer road casing outline for chosen route
                        val guideCasing = Polyline(map).apply {
                            setPoints(pointsToUse)
                            outlinePaint.color = if (isArrived) android.graphics.Color.parseColor("#064E3B") else android.graphics.Color.parseColor("#4C1D95")
                            outlinePaint.strokeWidth = 11f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        map.overlays.add(guideCasing)

                        // Core destination road line
                        val guideLine = Polyline(map).apply {
                            setPoints(pointsToUse)
                            outlinePaint.color = if (isArrived) android.graphics.Color.parseColor("#10B981") else android.graphics.Color.parseColor("#8B5CF6")
                            outlinePaint.strokeWidth = 7f
                            outlinePaint.strokeCap = Paint.Cap.ROUND
                            outlinePaint.strokeJoin = Paint.Join.ROUND
                        }
                        map.overlays.add(guideLine)
                    }

                    // Destination Target Marker
                    val destMarker = Marker(map).apply {
                        position = destGeo
                        icon = MapMarkerHelper.createDestinationMarkerDrawable(context, destinationTask.destinationName, isArrived)
                        title = "🎯 Tujuan: ${destinationTask.destinationName}"
                        snippet = if (isArrived) {
                            "✅ Telah Tiba dalam ${destinationTask.actualDurationMinutes ?: 0} menit"
                        } else {
                            "Estimasi: ~${destinationTask.estimatedMinutes} menit • ${destinationTask.notes.ifBlank { "Tugas Rute" }}"
                        }
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(destMarker)
                }

                map.invalidate()
            }
        )

        // Floating Modern Telematics HUD for OSM Map (Only in Admin overview, hidden in User mode to avoid overlap)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Top Left Compact Status Bar (shown in Admin overview)
            if (showTopStatusPill) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (currentPoint?.isMock == true) StatusRed
                                    else if (isLiveTracking) StatusGreen
                                    else StatusYellow,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (currentPoint?.isMock == true) "⚠️ Fake GPS"
                                    else if (isLiveTracking) "Live Tracking" else "Standby",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (currentPoint?.isMock == true) StatusRed else MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ${vehicleType.iconEmoji} ${vehicleType.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = GeoBluePrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (currentPoint != null) {
                                Text(
                                    text = "${currentPoint.speedKmh.toInt()} km/h • ±${currentPoint.accuracyMeters.toInt()}m" +
                                            if (detectedStops.isNotEmpty()) " • 🛑 ${detectedStops.size} Stop" else "",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Recenter Button
            FloatingActionButton(
                onClick = {
                    currentPoint?.let {
                        mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                    onMapCenterRequested()
                },
                modifier = Modifier
                    .align(if (showTopStatusPill) Alignment.TopEnd else Alignment.BottomEnd)
                    .padding(bottom = if (showTopStatusPill) 0.dp else 90.dp)
                    .size(46.dp)
                    .testTag("btn_osm_recenter"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Pusatkan Lokasi",
                    modifier = Modifier.size(22.dp)
                )
            }

            // Bottom Right Zoom In / Out
            Surface(
                modifier = Modifier.align(Alignment.BottomEnd),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Column {
                    IconButton(
                        onClick = { mapView.controller.zoomIn() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Zoom In")
                    }
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(1.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    IconButton(
                        onClick = { mapView.controller.zoomOut() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Zoom Out")
                    }
                }
            }
        }
    }
}
