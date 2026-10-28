package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationPoint
import com.example.data.model.UserProfile
import com.example.ui.components.AdminUserChatSheet
import com.example.ui.components.AppMapContainer
import com.example.ui.theme.GeoBluePrimary
import com.example.ui.theme.GeoCyanAccent
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.util.BatteryHelper
import com.example.util.RoadNetworkEngine
import com.example.util.RouteSegmenter
import com.example.util.StopPointDetector
import com.example.util.rememberCompassHeading
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserTrackerScreen(
    user: UserProfile,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    onEnterPip: () -> Unit = {}
) {
    val isSharing by viewModel.isSharingLocation.collectAsState()
    val currentPt by viewModel.currentPoint.collectAsState()
    val historyPoints by viewModel.targetUserHistory.collectAsState()
    val geofences by viewModel.companyGeofences.collectAsState()
    val alertBanner by viewModel.activeAlertBanner.collectAsState()
    val activeTask by viewModel.activeUserTask.collectAsState()

    val context = LocalContext.current
    var realBattery by remember { mutableStateOf(BatteryHelper.getBatteryStatus(context)) }
    var showChatSheet by remember { mutableStateOf(false) }
    var showTaskDialog by remember { mutableStateOf(false) }

    // Read real-time phone compass orientation (azimuth 0-360°)
    val compassHeading by rememberCompassHeading(enabled = true)

    // Continuously read the user's actual phone battery level and charging status
    LaunchedEffect(Unit) {
        while (true) {
            realBattery = BatteryHelper.getBatteryStatus(context)
            delay(3500L)
        }
    }

    val isFakeGpsDetected = currentPt?.isMock == true

    // Compute live telemetry metrics (strictly segmented, excluding off-duty gap jumps)
    val totalDistanceKm = remember(historyPoints) {
        RouteSegmenter.calculateSegmentedDistanceKm(historyPoints)
    }

    val detectedStops = remember(historyPoints) {
        StopPointDetector.detectStopPoints(historyPoints, minDurationMinutes = 5)
    }

    val tripMinutes = remember(historyPoints) {
        RouteSegmenter.calculateActiveDurationMinutes(historyPoints)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = GeoBluePrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(user.vehicleType.iconEmoji, fontSize = 20.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = user.fullName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isSharing) StatusGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = if (isSharing) "ONLINE" else "STANDBY",
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (isSharing) StatusGreen else Color.Gray
                                    )
                                }
                            }
                            Text(
                                text = "${user.companyId} • Unit ${user.unitCode} (${user.vehicleType.label})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Picture-in-Picture (PiP) Floating Window Button
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier.testTag("btn_topbar_enter_pip")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GeoBluePrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PictureInPictureAlt,
                                    contentDescription = "Jendela Pop-up Mengambang (PiP)",
                                    tint = GeoBluePrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                    // Destination Task Notification Icon Button
                    if (activeTask != null) {
                        IconButton(
                            onClick = { showTaskDialog = true },
                            modifier = Modifier.testTag("btn_topbar_task_notif")
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (activeTask?.status == "ARRIVED") StatusGreen.copy(alpha = 0.18f) else Color(0xFF7C3AED).copy(alpha = 0.18f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.PinDrop,
                                        contentDescription = "Notifikasi Tugas Tujuan",
                                        tint = if (activeTask?.status == "ARRIVED") StatusGreen else Color(0xFF7C3AED),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 2.dp, end = 2.dp)
                                            .size(8.dp)
                                            .background(if (activeTask?.status == "ARRIVED") StatusGreen else StatusRed, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                    // Chat Admin Direct Button
                    IconButton(
                        onClick = {
                            viewModel.openChatWithUser(user.userId)
                            showChatSheet = true
                        },
                        modifier = Modifier.testTag("btn_topbar_chat_admin")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GeoBluePrimary.copy(alpha = 0.12f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Chat,
                                    contentDescription = "Chat Admin",
                                    tint = GeoBluePrimary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("btn_user_logout")
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Keluar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. Critical Fake GPS Warning Banner
            AnimatedVisibility(
                visible = isFakeGpsDetected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .testTag("fake_gps_warning_banner"),
                    shape = RoundedCornerShape(12.dp),
                    color = StatusRed,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ PERINGATAN: MOCK LOCATION / FAKE GPS AKTIF",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 12.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Matikan aplikasi Fake GPS di Opsi Pengembang untuk merekam rute sah.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }
                    }
                }
            }

            // 2. Geofence Activity Banner (if any)
            AnimatedVisibility(
                visible = alertBanner != null && !isFakeGpsDetected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                alertBanner?.let { alert ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("geofence_alert_banner"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (alert.eventType == "MASUK") StatusGreen else StatusYellow,
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notifikasi Area: ${alert.eventType}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = alert.description,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissAlertBanner() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }



            // 3. Map Container with Interactive HUD
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.35f)
            ) {
                AppMapContainer(
                    currentPoint = currentPt,
                    routePoints = historyPoints,
                    geofences = geofences,
                    userLabel = user.fullName,
                    isLiveTracking = isSharing,
                    vehicleType = user.vehicleType,
                    compassHeading = compassHeading,
                    destinationTask = activeTask,
                    isUserMode = true,
                    showTopStatusPill = false,
                    modifier = Modifier.fillMaxSize()
                )

                // Top Compact Floating Telematics HUD Pill (Sleek, minimal width, won't cover map or side controls)
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    shadowElevation = 5.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. Status Indicator Chip (Live vs Standby)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSharing) StatusGreen.copy(alpha = 0.15f) else StatusYellow.copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(
                                            color = if (isSharing) StatusGreen else StatusYellow,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isSharing) "LIVE" else "STANDBY",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 10.sp,
                                    color = if (isSharing) StatusGreen else Color(0xFFB45309)
                                )
                            }
                        }

                        // 2. Real-time Compass Azimuth
                        Text(
                            text = "🧭 ${compassHeading.toInt()}°",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Box(modifier = Modifier.size(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))

                        // 3. Current Speed
                        Text(
                            text = "⚡ ${(currentPt?.speedKmh ?: 0f).toInt()} km/h",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GeoBluePrimary
                        )

                        Box(modifier = Modifier.size(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))

                        // 4. True Battery Percentage
                        val batColor = when {
                            realBattery.isCharging -> StatusGreen
                            realBattery.percentage <= 20 -> StatusRed
                            realBattery.percentage <= 50 -> StatusYellow
                            else -> StatusGreen
                        }
                        Text(
                            text = "${if (realBattery.isCharging) "⚡" else "🔋"}${realBattery.percentage}%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = batColor
                        )

                        // 5. Stops (if any detected)
                        if (detectedStops.isNotEmpty()) {
                            Box(modifier = Modifier.size(3.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                            Text(
                                text = "🛑 ${detectedStops.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = StatusRed
                            )
                        }
                    }
                }
            }

            // 4. Modern Executive Telematics Control Panel
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f),
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Location Sharing Transmission Master Bar
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isFakeGpsDetected) StatusRed.copy(alpha = 0.08f)
                        else if (isSharing) GeoBluePrimary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSharing) GeoBluePrimary.copy(alpha = 0.3f) else Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            color = if (isFakeGpsDetected) StatusRed
                                            else if (isSharing) StatusGreen
                                            else Color.Gray,
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = if (isFakeGpsDetected) "Lokasi Ditolak (Mock Provider)"
                                        else if (isSharing) "Transmisi Rute GPS Aktif"
                                        else "Transmisi Lokasi Standby",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFakeGpsDetected) StatusRed else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isFakeGpsDetected) "Matikan Fake GPS di HP Anda"
                                        else if (isSharing) "Posisi Anda dipantau real-time oleh dispatcher"
                                        else "Aktifkan untuk mulai perjalanan & tugas rute",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Switch(
                                checked = isSharing,
                                onCheckedChange = { viewModel.toggleLocationSharing(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = if (isFakeGpsDetected) StatusRed else StatusGreen
                                ),
                                modifier = Modifier.testTag("switch_sharelok")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Quick Telematics Summary Pills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickMetricCard(
                            label = "Waktu Jalan",
                            value = "${tripMinutes}m",
                            icon = Icons.Default.Timer,
                            tint = GeoBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        QuickMetricCard(
                            label = "Akurasi GPS",
                            value = "±${(currentPt?.accuracyMeters ?: 4f).toInt()}m",
                            icon = Icons.Default.GpsFixed,
                            tint = GeoCyanAccent,
                            modifier = Modifier.weight(1f)
                        )
                        QuickMetricCard(
                            label = "Status Jalur",
                            value = if (isSharing) "Terhubung" else "Standby",
                            icon = Icons.Default.Route,
                            tint = if (isSharing) StatusGreen else Color.Gray,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Chat with Admin Button (1-on-1 Driver to Admin)
                    Button(
                        onClick = {
                            viewModel.openChatWithUser(user.userId)
                            showChatSheet = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_open_chat_dispatcher"),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoBluePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Hubungi Admin / Dispatcher",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Floating Mini-Window (PiP) Mode Button (Google Maps-like floating window)
                    OutlinedButton(
                        onClick = onEnterPip,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("btn_enter_pip_mode"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = GeoBluePrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.2.dp, GeoBluePrimary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, modifier = Modifier.size(19.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Buka Pop-up Jendela (PiP)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Export History & Clear Trip Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportHistory(historyPoints, "CSV") },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_export_csv"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Ekspor CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.exportHistory(historyPoints, "GeoJSON") },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("btn_export_geojson"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("GeoJSON", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier
                                .height(44.dp)
                                .testTag("btn_clear_history"),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Reset Rute")
                        }
                    }
                }
            }
        }
    }

    // 1-on-1 Chat Sheet with Admin (User only communicates with Company Admin)
    if (showChatSheet) {
        val chatMessages by viewModel.activeChatMessages.collectAsState()
        AdminUserChatSheet(
            title = "Chat Admin Perusahaan",
            subtitle = "Admin / Dispatcher • ${user.companyId}",
            messages = chatMessages,
            currentSenderId = user.userId,
            currentSenderRole = "USER",
            onSendMessage = { text ->
                viewModel.sendChatMessage(text)
            },
            onDismiss = {
                showChatSheet = false
                viewModel.closeChat()
            }
        )
    }
    // Task Notification Dialog (Shows details when notification icon is tapped)
    if (showTaskDialog && activeTask != null) {
        val task = activeTask!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (task.status == "ARRIVED") "✅" else "🎯", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (task.status == "ARRIVED") "Telah Tiba di Tujuan" else "Tugas Rute Tujuan",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "Lokasi Tujuan: ${task.destinationName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (task.status == "ARRIVED") {
                        Text(
                            text = "🎉 Anda telah sampai di titik lokasi tujuan!\nWaktu tempuh: ${task.actualDurationMinutes ?: 0} menit",
                            fontSize = 12.sp,
                            color = StatusGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        val distKm = String.format(java.util.Locale.US, "%.1f km", task.distanceMeters / 1000.0)
                        Text(
                            text = "Estimasi Waktu: ~${task.estimatedMinutes} menit\nJarak Tempuh: $distKm",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (task.notes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Catatan Admin: ${task.notes}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showTaskDialog = false }) {
                    Text("Tutup", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun QuickMetricCard(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
