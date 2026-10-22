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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
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
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

@Composable
fun OpenStreetMapTrackerView(
    currentPoint: LocationPoint?,
    routePoints: List<LocationPoint>,
    geofences: List<GeofenceZone>,
    modifier: Modifier = Modifier,
    userLabel: String = "User",
    isLiveTracking: Boolean = true,
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
            controller.setZoom(16.5)
            val initialLat = currentPoint?.latitude ?: -6.2088
            val initialLng = currentPoint?.longitude ?: 106.8456
            controller.setCenter(GeoPoint(initialLat, initialLng))
        }
    }

    // Auto-center camera when user coordinates change
    LaunchedEffect(currentPoint?.latitude, currentPoint?.longitude) {
        if (currentPoint != null) {
            mapView.controller.animateTo(GeoPoint(currentPoint.latitude, currentPoint.longitude))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            update = { map ->
                map.overlays.clear()

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

                // 2. Render Route Polyline on OpenStreetMap
                if (routePoints.size > 1) {
                    val geoPoints = routePoints.map { GeoPoint(it.latitude, it.longitude) }
                    val polyline = Polyline(map).apply {
                        setPoints(geoPoints)
                        outlinePaint.color = android.graphics.Color.parseColor("#0F52BA")
                        outlinePaint.strokeWidth = 10f
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    }
                    map.overlays.add(polyline)

                    // Start marker
                    val startPt = routePoints.first()
                    val startMarker = Marker(map).apply {
                        position = GeoPoint(startPt.latitude, startPt.longitude)
                        title = "Titik Awal Keberangkatan"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                    map.overlays.add(startMarker)
                }

                // 3. Render Live User GPS Position Marker
                if (currentPoint != null) {
                    val userMarker = Marker(map).apply {
                        position = GeoPoint(currentPoint.latitude, currentPoint.longitude)
                        title = if (currentPoint.isMock) "⚠️ $userLabel [FAKE GPS!]" else "● $userLabel"
                        snippet = String.format(
                            "%.0f km/h | Akurasi: ±%.1fm | %s",
                            currentPoint.speedKmh,
                            currentPoint.accuracyMeters,
                            currentPoint.geofenceStatus
                        )
                        rotation = currentPoint.bearing
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                    map.overlays.add(userMarker)
                }

                map.invalidate()
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("osm_map_view")
        )

        // Floating HUD for OSM Map
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top Left Status Pill
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                        Text(
                            text = if (currentPoint?.isMock == true) "⚠️ Terdeteksi Fake GPS"
                            else "OpenStreetMap: Live Tracking",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (currentPoint?.isMock == true) StatusRed else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (currentPoint != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("Lat: %.5f, Lng: %.5f", currentPoint.latitude, currentPoint.longitude),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Akurasi: ±${currentPoint.accuracyMeters.toInt()}m • ${currentPoint.geofenceStatus}",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = GeoBluePrimary
                        )
                    }
                }
            }

            // Top Right Recenter Button
            FloatingActionButton(
                onClick = {
                    currentPoint?.let {
                        mapView.controller.animateTo(GeoPoint(it.latitude, it.longitude))
                    }
                    onMapCenterRequested()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
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
