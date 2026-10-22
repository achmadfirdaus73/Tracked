package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationPoint
import com.example.ui.theme.GeoBluePrimary
import com.example.ui.theme.GeoCyanAccent
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.util.GeofenceEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RouteAnalyticsCharts(
    points: List<LocationPoint>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onPointSelected: (Int?) -> Unit = {}
) {
    if (points.isEmpty()) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Belum Ada Riwayat Perjalanan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Data grafik kecepatan dan elevasi akan muncul begitu user mulai membagikan lokasi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    var activeTab by remember { mutableIntStateOf(0) } // 0 = Kecepatan, 1 = Elevasi, 2 = Timeline Perjalanan
    var scrubRatio by remember { mutableFloatStateOf(if (selectedIndex != null) selectedIndex.toFloat() / points.size.coerceAtLeast(1) else 1f) }

    // Summary calculation
    var totalDistanceMeters = 0.0
    for (i in 0 until points.size - 1) {
        totalDistanceMeters += GeofenceEngine.calculateDistanceMeters(
            points[i].latitude, points[i].longitude,
            points[i + 1].latitude, points[i + 1].longitude
        )
    }
    val totalDistanceKm = totalDistanceMeters / 1000.0
    val maxSpeed = points.maxOfOrNull { it.speedKmh } ?: 0f
    val avgSpeed = if (points.isNotEmpty()) points.map { it.speedKmh }.average().toFloat() else 0f
    val durationMinutes = if (points.size > 1) {
        ((points.last().timestamp - points.first().timestamp) / 60000L).coerceAtLeast(1)
    } else 0L

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. High-level Metric Cards Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MetricCard(
                title = "Total Jarak",
                value = String.format(Locale.getDefault(), "%.2f km", totalDistanceKm),
                subtitle = "$durationMinutes menit perjalanan",
                icon = Icons.Default.DirectionsCar,
                tint = GeoBluePrimary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Kecepatan Max",
                value = String.format(Locale.getDefault(), "%.1f km/h", maxSpeed),
                subtitle = String.format(Locale.getDefault(), "Rata-rata: %.1f km/h", avgSpeed),
                icon = Icons.Default.Speed,
                tint = if (maxSpeed > 80f) StatusRed else GeoCyanAccent,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Chart Selector Tabs
        TabRow(
            selectedTabIndex = activeTab,
            modifier = Modifier.padding(horizontal = 16.dp),
            containerColor = Color.Transparent,
            divider = {}
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Grafik Kecepatan", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("Profil Elevasi", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("Timeline Rute", fontWeight = FontWeight.SemiBold) },
                icon = { Icon(Icons.Default.AvTimer, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Chart Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                when (activeTab) {
                    0 -> {
                        Text(
                            text = "Profil Kecepatan Real-Time (km/h vs Waktu)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Garis putus-putus biru menunjukkan kecepatan rata-rata (${String.format(Locale.getDefault(), "%.1f", avgSpeed)} km/h)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        SpeedLineChart(
                            points = points,
                            avgSpeed = avgSpeed,
                            maxSpeed = maxSpeed.coerceAtLeast(40f),
                            selectedIndex = selectedIndex,
                            onPointSelected = onPointSelected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                        )
                    }
                    1 -> {
                        val minAlt = (points.minOfOrNull { it.altitude } ?: 0.0).toFloat()
                        val maxAlt = (points.maxOfOrNull { it.altitude } ?: 50.0).toFloat().coerceAtLeast(minAlt + 20f)

                        Text(
                            text = "Profil Ketinggian / Elevasi (mdpl)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Rentang ketinggian rute: ${minAlt.toInt()} m s/d ${maxAlt.toInt()} m di atas permukaan laut",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        ElevationAreaChart(
                            points = points,
                            minAlt = minAlt,
                            maxAlt = maxAlt,
                            selectedIndex = selectedIndex,
                            onPointSelected = onPointSelected,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(190.dp)
                        )
                    }
                    2 -> {
                        Text(
                            text = "Playback Perjalanan Waktu & Geofencing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Geser slider untuk melihat posisi dan status user pada titik tertentu di peta",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Current inspected point
                        val currentIndex = ((points.size - 1) * scrubRatio).toInt().coerceIn(points.indices)
                        val currPt = points[currentIndex]
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(currPt.timestamp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Titik #${currentIndex + 1} dari ${points.size} • $timeStr",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Kecepatan: ${currPt.speedKmh.toInt()} km/h | Alt: ${currPt.altitude.toInt()}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = currPt.geofenceStatus,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (currPt.geofenceStatus.contains("Terlarang")) StatusRed else GeoBluePrimary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Slider(
                            value = scrubRatio,
                            onValueChange = {
                                scrubRatio = it
                                val idx = ((points.size - 1) * it).toInt().coerceIn(points.indices)
                                onPointSelected(idx)
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = GeoBluePrimary,
                                activeTrackColor = GeoBluePrimary
                            ),
                            modifier = Modifier.testTag("route_playback_slider")
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(tint.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpeedLineChart(
    points: List<LocationPoint>,
    avgSpeed: Float,
    maxSpeed: Float,
    selectedIndex: Int?,
    onPointSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val chartTopSpeed = (maxSpeed * 1.15f).coerceAtLeast(50f)
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Canvas(
        modifier = modifier
            .testTag("speed_line_chart")
            .pointerInput(points) {
                detectTapGestures { offset ->
                    if (points.isNotEmpty()) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val idx = ((points.size - 1) * fraction).toInt()
                        onPointSelected(idx)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val bottomPadding = 30f
        val chartH = h - bottomPadding

        // Draw horizontal grid lines (0%, 25%, 50%, 75%, 100%)
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = chartH - (i.toFloat() / gridLines * chartH)
            val speedLabel = (i.toFloat() / gridLines * chartTopSpeed).toInt()

            drawLine(
                color = Color.LightGray.copy(alpha = 0.35f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )

            // Y-Axis label
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.GRAY
                    textSize = 24f
                    textAlign = android.graphics.Paint.Align.LEFT
                }
                drawText("$speedLabel", 8f, y - 4f, paint)
            }
        }

        // Draw Average Speed Reference Line (Dashed)
        val avgY = chartH - (avgSpeed / chartTopSpeed * chartH)
        drawLine(
            color = GeoBluePrimary.copy(alpha = 0.6f),
            start = Offset(0f, avgY),
            end = Offset(w, avgY),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
        )

        if (points.isEmpty()) return@Canvas

        // Build Speed Path
        val path = Path()
        val fillPath = Path()
        fillPath.moveTo(0f, chartH)

        val stepX = w / (points.size - 1).coerceAtLeast(1)

        points.forEachIndexed { index, pt ->
            val x = index * stepX
            val y = chartH - (pt.speedKmh / chartTopSpeed * chartH).coerceIn(0f, chartH)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((points.size - 1) * stepX, chartH)
        fillPath.close()

        // Draw Speed Gradient Fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    GeoCyanAccent.copy(alpha = 0.35f),
                    GeoBluePrimary.copy(alpha = 0.05f)
                ),
                startY = 0f,
                endY = chartH
            )
        )

        // Draw Speed Polyline
        drawPath(
            path = path,
            color = GeoBluePrimary,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // If a point is selected, draw cursor line and detail bubble
        if (selectedIndex != null && selectedIndex in points.indices) {
            val selPt = points[selectedIndex]
            val selX = selectedIndex * stepX
            val selY = chartH - (selPt.speedKmh / chartTopSpeed * chartH)

            drawLine(
                color = StatusYellow,
                start = Offset(selX, 0f),
                end = Offset(selX, chartH),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(color = StatusYellow, radius = 6.dp.toPx(), center = Offset(selX, selY))
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = Offset(selX, selY))
        }

        // X-Axis time markers at start and end
        drawContext.canvas.nativeCanvas.apply {
            val paint = android.graphics.Paint().apply {
                color = android.graphics.Color.DKGRAY
                textSize = 24f
            }
            val startText = timeFormat.format(Date(points.first().timestamp))
            val endText = timeFormat.format(Date(points.last().timestamp))
            paint.textAlign = android.graphics.Paint.Align.LEFT
            drawText(startText, 4f, h - 4f, paint)
            paint.textAlign = android.graphics.Paint.Align.RIGHT
            drawText(endText, w - 4f, h - 4f, paint)
        }
    }
}

@Composable
private fun ElevationAreaChart(
    points: List<LocationPoint>,
    minAlt: Float,
    maxAlt: Float,
    selectedIndex: Int?,
    onPointSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val altRange = (maxAlt - minAlt).coerceAtLeast(10f)

    Canvas(
        modifier = modifier
            .testTag("elevation_area_chart")
            .pointerInput(points) {
                detectTapGestures { offset ->
                    if (points.isNotEmpty()) {
                        val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                        val idx = ((points.size - 1) * fraction).toInt()
                        onPointSelected(idx)
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val bottomPadding = 26f
        val chartH = h - bottomPadding

        if (points.isEmpty()) return@Canvas

        val stepX = w / (points.size - 1).coerceAtLeast(1)
        val path = Path()
        val fillPath = Path()
        fillPath.moveTo(0f, chartH)

        points.forEachIndexed { index, pt ->
            val x = index * stepX
            val altNorm = ((pt.altitude.toFloat() - minAlt) / altRange).coerceIn(0f, 1f)
            val y = chartH - (altNorm * chartH * 0.85f + 10f)
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        fillPath.lineTo((points.size - 1) * stepX, chartH)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF10B981).copy(alpha = 0.45f),
                    Color(0xFF047857).copy(alpha = 0.08f)
                ),
                startY = 0f,
                endY = chartH
            )
        )

        drawPath(
            path = path,
            color = Color(0xFF10B981),
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
