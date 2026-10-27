package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.util.RoadNetworkEngine
import com.example.util.RouteSegmenter
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.ui.theme.GeoBluePrimary
import com.example.ui.theme.GeoCyanAccent
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InteractiveMapView(
    currentPoint: LocationPoint?,
    routePoints: List<LocationPoint>,
    geofences: List<GeofenceZone>,
    modifier: Modifier = Modifier,
    userLabel: String = "User",
    vehicleType: com.example.data.model.VehicleType = com.example.data.model.VehicleType.MOTORCYCLE,
    isLiveTracking: Boolean = true,
    compassHeading: Float = 0f,
    isUserMode: Boolean = false,
    showTopStatusPill: Boolean = !isUserMode,
    highlightPointIndex: Int? = null,
    onMapCenterRequested: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var zoomLevel by remember { mutableFloatStateOf(if (isUserMode && isLiveTracking) 28f else 16f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    var isDarkMapStyle by remember { mutableStateOf(false) }

    // Auto zoom-in when live tracking is active on the user side
    LaunchedEffect(isLiveTracking, isUserMode) {
        if (isUserMode && isLiveTracking) {
            zoomLevel = 28f
            panOffsetX = 0f
            panOffsetY = 0f
        }
    }

    // Vehicle Marker Drawable for live rendering
    val vehicleMarkerDrawable = remember(vehicleType, isLiveTracking, currentPoint?.isMock) {
        com.example.util.MapMarkerHelper.createVehicleMarkerDrawable(
            context = context,
            vehicleType = vehicleType,
            isLive = isLiveTracking,
            isMock = currentPoint?.isMock == true
        )
    }

    // Detect 5-10 minute Stop Points
    val detectedStops = remember(routePoints) {
        com.example.util.StopPointDetector.detectStopPoints(routePoints, minDurationMinutes = 5)
    }

    val routeSegments = remember(routePoints) {
        RouteSegmenter.segmentRoute(routePoints)
    }
    var matchedSegments by remember { mutableStateOf<List<List<Pair<Double, Double>>>>(emptyList()) }

    // Map-Matching: Asynchronously stitch consecutive coordinates along actual road geometry per segment
    LaunchedEffect(routeSegments) {
        if (routeSegments.isNotEmpty()) {
            matchedSegments = withContext(Dispatchers.IO) {
                routeSegments.map { seg ->
                    if (seg.size > 1) {
                        val coords = seg.map { Pair(it.latitude, it.longitude) }
                        RoadNetworkEngine.matchRouteToRoadNetwork(coords)
                    } else {
                        seg.map { Pair(it.latitude, it.longitude) }
                    }
                }
            }
        } else {
            matchedSegments = emptyList()
        }
    }

    // Reference center anchor coordinate
    val centerLat = currentPoint?.latitude ?: routePoints.lastOrNull()?.latitude ?: -6.2088
    val centerLng = currentPoint?.longitude ?: routePoints.lastOrNull()?.longitude ?: 106.8456

    // Animate radar sweep & GPS pulse
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseFraction by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )

    // Reset pan whenever requested or when recenter is tapped
    fun recenter() {
        panOffsetX = 0f
        panOffsetY = 0f
        onMapCenterRequested()
    }

    Box(modifier = modifier.clipToBounds()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("interactive_canvas_map")
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        panOffsetX += dragAmount.x
                        panOffsetY += dragAmount.y
                    }
                }
        ) {
            val canvasW = size.width
            val canvasH = size.height
            val mapCenterX = canvasW / 2f + panOffsetX
            val mapCenterY = canvasH / 2f + panOffsetY

            // Degrees per meter roughly in equator region
            // 1 degree lat ~= 111,320m, 1 degree lng ~= 111,320m * cos(lat)
            val metersPerPixel = (2000f / zoomLevel) / 100f
            val latDegreePerPixel = metersPerPixel / 111320.0
            val lngDegreePerPixel = metersPerPixel / (111320.0 * cos(Math.toRadians(centerLat)))

            fun geoToPixel(lat: Double, lng: Double): Offset {
                val dLng = lng - centerLng
                val dLat = lat - centerLat
                val x = mapCenterX + (dLng / lngDegreePerPixel).toFloat()
                val y = mapCenterY - (dLat / latDegreePerPixel).toFloat() // inverted Y for screen
                return Offset(x, y)
            }

            fun metersToPixels(meters: Float): Float {
                return meters / metersPerPixel.toFloat()
            }

            // 1. Draw Map Background & Styled Vector City Grid
            drawMapBase(isDarkMapStyle, mapCenterX, mapCenterY, canvasW, canvasH)

            // 2. Draw Geofence Zones (Disks + Radar waves)
            for (fence in geofences) {
                val centerPx = geoToPixel(fence.latitude, fence.longitude)
                val radiusPx = metersToPixels(fence.radiusMeters)

                val zoneColor = try {
                    Color(android.graphics.Color.parseColor(fence.colorHex))
                } catch (e: Exception) {
                    GeoBluePrimary
                }

                // Fill zone
                drawCircle(
                    color = zoneColor.copy(alpha = 0.16f),
                    radius = radiusPx,
                    center = centerPx
                )

                // Dashed boundary
                drawCircle(
                    color = zoneColor.copy(alpha = 0.85f),
                    radius = radiusPx,
                    center = centerPx,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 10f), 0f)
                    )
                )

                // Outer radar pulse on geofence
                drawCircle(
                    color = zoneColor.copy(alpha = (1f - pulseFraction) * 0.35f),
                    radius = radiusPx + (pulseFraction * 20.dp.toPx()),
                    center = centerPx,
                    style = Stroke(width = 2.dp.toPx())
                )

                // Geofence label badge
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 30f
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val bgPaint = android.graphics.Paint().apply {
                        color = zoneColor.copy(alpha = 0.9f).hashCode()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val text = "🛡️ ${fence.name} (${fence.radiusMeters.toInt()}m)"
                    val bounds = android.graphics.Rect()
                    paint.getTextBounds(text, 0, text.length, bounds)
                    val bgRect = android.graphics.RectF(
                        centerPx.x - bounds.width() / 2f - 20f,
                        centerPx.y - radiusPx - 45f,
                        centerPx.x + bounds.width() / 2f + 20f,
                        centerPx.y - radiusPx + 15f
                    )
                    drawRoundRect(bgRect, 14f, 14f, bgPaint)
                    drawText(text, centerPx.x, centerPx.y - radiusPx - 5f, paint)
                }
            }

            // 3. Draw Route Polylines per Segment (Road Matched, no teleport lines across sessions)
            val displaySegments = if (matchedSegments.isNotEmpty()) {
                matchedSegments
            } else {
                routeSegments.map { seg -> seg.map { Pair(it.latitude, it.longitude) } }
            }

            for ((segIdx, segCoords) in displaySegments.withIndex()) {
                if (segCoords.size > 1) {
                    for (i in 0 until segCoords.size - 1) {
                        val p1 = segCoords[i]
                        val p2 = segCoords[i + 1]
                        val pt1Px = geoToPixel(p1.first, p1.second)
                        val pt2Px = geoToPixel(p2.first, p2.second)

                        // Polyline shadow
                        drawLine(
                            color = Color.Black.copy(alpha = 0.25f),
                            start = Offset(pt1Px.x, pt1Px.y + 3f),
                            end = Offset(pt2Px.x, pt2Px.y + 3f),
                            strokeWidth = 9.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // Polyline core (Navigation vibrant line)
                        drawLine(
                            color = GeoCyanAccent,
                            start = pt1Px,
                            end = pt2Px,
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Draw intermediate breadcrumb dots
                    for (i in segCoords.indices step 4) {
                        val ptPx = geoToPixel(segCoords[i].first, segCoords[i].second)
                        drawCircle(
                            color = Color.White,
                            radius = 3.dp.toPx(),
                            center = ptPx
                        )
                    }

                    // Draw Start Point Marker for each segment
                    val startGeo = segCoords.first()
                    val startPx = geoToPixel(startGeo.first, startGeo.second)
                    drawCircle(
                        color = StatusGreen,
                        radius = 12.dp.toPx(),
                        center = startPx
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = startPx
                    )
                    drawCircle(
                        color = StatusGreen,
                        radius = 5.dp.toPx(),
                        center = startPx
                    )

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 26f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val bgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#059669")
                            style = android.graphics.Paint.Style.FILL
                        }
                        val text = if (segIdx == 0) "🏁 START" else "🏁 TRIP ${segIdx + 1}"
                        val bounds = android.graphics.Rect()
                        paint.getTextBounds(text, 0, text.length, bounds)
                        val bgRect = android.graphics.RectF(
                            startPx.x - bounds.width() / 2f - 16f,
                            startPx.y - 20.dp.toPx() - 28f,
                            startPx.x + bounds.width() / 2f + 16f,
                            startPx.y - 20.dp.toPx() + 10f
                        )
                        drawRoundRect(bgRect, 12f, 12f, bgPaint)
                        drawText(text, startPx.x, startPx.y - 20.dp.toPx() - 4f, paint)
                    }
                }
            }

                // Draw Stop Points (5-10+ minutes stops)
                for (stop in detectedStops) {
                    val stopPx = geoToPixel(stop.latitude, stop.longitude)
                    drawCircle(
                        color = StatusRed,
                        radius = 11.dp.toPx(),
                        center = stopPx
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = stopPx
                    )
                    drawCircle(
                        color = StatusRed,
                        radius = 4.dp.toPx(),
                        center = stopPx
                    )

                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = 24f
                            isFakeBoldText = true
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val bgPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#DC2626")
                            style = android.graphics.Paint.Style.FILL
                        }
                        val text = "🛑 ${stop.durationMinutes}m"
                        val bounds = android.graphics.Rect()
                        paint.getTextBounds(text, 0, text.length, bounds)
                        val bgRect = android.graphics.RectF(
                            stopPx.x - bounds.width() / 2f - 14f,
                            stopPx.y - 18.dp.toPx() - 26f,
                            stopPx.x + bounds.width() / 2f + 14f,
                            stopPx.y - 18.dp.toPx() + 8f
                        )
                        drawRoundRect(bgRect, 10f, 10f, bgPaint)
                        drawText(text, stopPx.x, stopPx.y - 18.dp.toPx() - 4f, paint)
                    }
                }

            // 4. Highlighted scrubber point (if scrubbing history timeline)
            if (highlightPointIndex != null && highlightPointIndex in routePoints.indices) {
                val hlPoint = routePoints[highlightPointIndex]
                val hlPx = geoToPixel(hlPoint.latitude, hlPoint.longitude)

                drawCircle(
                    color = StatusYellow.copy(alpha = 0.4f),
                    radius = 24.dp.toPx(),
                    center = hlPx
                )
                drawCircle(
                    color = StatusYellow,
                    radius = 10.dp.toPx(),
                    center = hlPx
                )
                drawCircle(
                    color = Color.White,
                    radius = 5.dp.toPx(),
                    center = hlPx
                )
            }

            // 5. Draw Live User Location & Vehicle Marker
            if (currentPoint != null) {
                val userPx = geoToPixel(currentPoint.latitude, currentPoint.longitude)
                val accuracyPx = metersToPixels(currentPoint.accuracyMeters.coerceAtLeast(3f))

                // GPS Accuracy circle
                drawCircle(
                    color = GeoCyanAccent.copy(alpha = 0.15f),
                    radius = accuracyPx.coerceIn(16.dp.toPx(), 90.dp.toPx()),
                    center = userPx
                )
                drawCircle(
                    color = GeoCyanAccent.copy(alpha = 0.5f),
                    radius = accuracyPx.coerceIn(16.dp.toPx(), 90.dp.toPx()),
                    center = userPx,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Pulsing wave
                if (isLiveTracking) {
                    drawCircle(
                        color = GeoCyanAccent.copy(alpha = (1f - pulseFraction) * 0.7f),
                        radius = (16.dp.toPx() + pulseFraction * 36.dp.toPx()),
                        center = userPx,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }

                // Main Vehicle Marker with dynamic Compass Heading Rotation
                val effectiveHeading = if (compassHeading > 0f) compassHeading else currentPoint.bearing

                drawContext.canvas.nativeCanvas.apply {
                    save()
                    translate(userPx.x, userPx.y)
                    rotate(effectiveHeading)
                    val bmp = (vehicleMarkerDrawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, null)
                    }
                    restore()
                }

                // Live User Name + Speed Badge
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 30f
                        isFakeBoldText = true
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val bgPaint = android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#1E293B")
                        style = android.graphics.Paint.Style.FILL
                    }
                    val speedText = String.format("%.0f km/h", currentPoint.speedKmh)
                    val label = "${vehicleType.iconEmoji} $userLabel | $speedText"
                    val bounds = android.graphics.Rect()
                    paint.getTextBounds(label, 0, label.length, bounds)
                    val bgRect = android.graphics.RectF(
                        userPx.x - bounds.width() / 2f - 20f,
                        userPx.y + 42.dp.toPx() - 4f,
                        userPx.x + bounds.width() / 2f + 20f,
                        userPx.y + 42.dp.toPx() + 38f
                    )
                    drawRoundRect(bgRect, 16f, 16f, bgPaint)
                    drawText(label, userPx.x, userPx.y + 42.dp.toPx() + 25f, paint)
                }
            }
        }

        // Map HUD Controls & Telemetry Overlay
        MapControlsOverlay(
            currentPoint = currentPoint,
            isLiveTracking = isLiveTracking,
            isDarkMapStyle = isDarkMapStyle,
            showTopStatusPill = showTopStatusPill,
            onToggleMapStyle = { isDarkMapStyle = !isDarkMapStyle },
            onZoomIn = { zoomLevel = (zoomLevel * 1.35f).coerceAtMost(48f) },
            onZoomOut = { zoomLevel = (zoomLevel / 1.35f).coerceAtLeast(4f) },
            onRecenter = { recenter() },
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun DrawScope.drawMapBase(
    isDark: Boolean,
    centerX: Float,
    centerY: Float,
    width: Float,
    height: Float
) {
    val bgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val blockColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val roadColor = if (isDark) Color(0xFF334155) else Color(0xFFFFFFFF)
    val highwayColor = if (isDark) Color(0xFF475569) else Color(0xFFFEF3C7)
    val parkColor = if (isDark) Color(0xFF064E3B) else Color(0xFFDCFCE7)

    drawRect(color = bgColor, size = size)

    // Render urban grid blocks
    val gridSize = 140f
    val offsetX = (centerX % gridSize)
    val offsetY = (centerY % gridSize)

    for (x in -gridSize.toInt()..width.toInt() + gridSize.toInt() step gridSize.toInt()) {
        for (y in -gridSize.toInt()..height.toInt() + gridSize.toInt() step gridSize.toInt()) {
            val drawX = x.toFloat() + offsetX - gridSize
            val drawY = y.toFloat() + offsetY - gridSize

            // Alternate some blocks as parks or civic spaces
            val isPark = (x / gridSize.toInt() + y / gridSize.toInt()) % 5 == 0
            val bColor = if (isPark) parkColor else blockColor

            drawRoundRect(
                color = bColor,
                topLeft = Offset(drawX + 14f, drawY + 14f),
                size = androidx.compose.ui.geometry.Size(gridSize - 28f, gridSize - 28f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
            )
        }
    }

    // Arterial roads
    for (x in -gridSize.toInt()..width.toInt() + gridSize.toInt() step (gridSize * 2).toInt()) {
        val rx = x.toFloat() + offsetX - gridSize
        drawLine(
            color = highwayColor,
            start = Offset(rx, 0f),
            end = Offset(rx, height),
            strokeWidth = 26f
        )
    }
    for (y in -gridSize.toInt()..height.toInt() + gridSize.toInt() step (gridSize * 2).toInt()) {
        val ry = y.toFloat() + offsetY - gridSize
        drawLine(
            color = roadColor,
            start = Offset(0f, ry),
            end = Offset(width, ry),
            strokeWidth = 20f
        )
    }
}

@Composable
private fun MapControlsOverlay(
    currentPoint: LocationPoint?,
    isLiveTracking: Boolean,
    isDarkMapStyle: Boolean,
    showTopStatusPill: Boolean = true,
    onToggleMapStyle: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.padding(16.dp)) {
        // Top Left Status Badge & GPS Telemetry Pill (Only if showTopStatusPill is true)
        if (showTopStatusPill) {
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                shadowElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(
                                    color = if (isLiveTracking) StatusGreen else StatusYellow,
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isLiveTracking) "GPS Real-Time Aktif" else "Pelacakan Ditunda",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (currentPoint != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format(
                                "Lat: %.5f, Lng: %.5f",
                                currentPoint.latitude,
                                currentPoint.longitude
                            ),
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
        }

        // Controls (Layer toggle & Recenter)
        Column(
            modifier = Modifier
                .align(if (showTopStatusPill) Alignment.TopEnd else Alignment.BottomEnd)
                .padding(bottom = if (showTopStatusPill) 0.dp else 90.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = onToggleMapStyle,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("map_layer_toggle"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Ganti Tampilan Peta",
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            FloatingActionButton(
                onClick = onRecenter,
                modifier = Modifier
                    .size(46.dp)
                    .testTag("map_recenter_button"),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = "Pusatkan Lokasi Saya",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Bottom Right Zoom In / Out Buttons
        Column(
            modifier = Modifier.align(Alignment.BottomEnd),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Column {
                    IconButton(
                        onClick = onZoomIn,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("zoom_in_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Zoom In"
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(1.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    IconButton(
                        onClick = onZoomOut,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("zoom_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Zoom Out"
                        )
                    }
                }
            }
        }
    }
}
