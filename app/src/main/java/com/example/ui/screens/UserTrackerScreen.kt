package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import com.example.data.model.UserProfile
import com.example.util.BatteryHelper
import com.example.ui.components.AdminUserChatSheet
import com.example.ui.components.AppMapContainer
import com.example.ui.theme.GeoBluePrimary
import com.example.ui.theme.GeoCyanAccent
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserTrackerScreen(
    user: UserProfile,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isSharing by viewModel.isSharingLocation.collectAsState()
    val currentPt by viewModel.currentPoint.collectAsState()
    val historyPoints by viewModel.targetUserHistory.collectAsState()
    val geofences by viewModel.companyGeofences.collectAsState()
    val alertBanner by viewModel.activeAlertBanner.collectAsState()

    val context = LocalContext.current
    var realBattery by remember { mutableStateOf(BatteryHelper.getBatteryStatus(context)) }
    var showChatSheet by remember { mutableStateOf(false) }

    // Continuously read the user's actual phone battery level and charging status
    LaunchedEffect(Unit) {
        while (true) {
            realBattery = BatteryHelper.getBatteryStatus(context)
            delay(4000L)
        }
    }

    val isFakeGpsDetected = currentPt?.isMock == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "${user.fullName} ${user.vehicleType.iconEmoji}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${user.companyId} • ${user.unitCode} (${user.vehicleType.label})",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.openChatWithUser(user.userId)
                            showChatSheet = true
                        },
                        modifier = Modifier.testTag("btn_topbar_chat_admin")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Chat Admin",
                            tint = GeoBluePrimary
                        )
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
            // 1. Critical Fake GPS Warning Banner (if detected)
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
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⚠️ PERINGATAN: TERDETEKSI FAKE GPS!",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Sistem mendeteksi aplikasi manipulasi lokasi. Matikan Fake GPS/Mock Location di Opsi Pengembang untuk membagikan lokasi sah.",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.95f)
                            )
                        }
                    }
                }
            }

            // 2. Standard Geofence Activity Banner (if any)
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
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Notifikasi Lokasi: ${alert.eventType} AREA",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = alert.description,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.95f)
                                )
                            }
                            IconButton(
                                onClick = { viewModel.dismissAlertBanner() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup", tint = Color.White)
                            }
                        }
                    }
                }
            }

            // 3. Google Maps SDK / Map View (Interactive Map Composable)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
            ) {
                AppMapContainer(
                    currentPoint = currentPt,
                    routePoints = historyPoints,
                    geofences = geofences,
                    userLabel = user.fullName,
                    isLiveTracking = isSharing,
                    vehicleType = user.vehicleType,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // 4. Lower Dashboard Controls Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Share Location Master Switch
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isFakeGpsDetected) StatusRed.copy(alpha = 0.08f)
                        else if (isSharing) GeoBluePrimary.copy(alpha = 0.08f)
                        else MaterialTheme.colorScheme.surfaceVariant,
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
                                        text = if (isFakeGpsDetected) "Lokasi Ditolak (Fake GPS)"
                                        else if (isSharing) "Sharelok Real-Time Aktif"
                                        else "Sharelok Dinonaktifkan",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFakeGpsDetected) StatusRed else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (isFakeGpsDetected) "Matikan Fake GPS untuk membagikan lokasi"
                                        else if (isSharing) "Setiap gerak Anda dipantau real-time oleh perusahaan"
                                        else "Nyalakan untuk mulai pelacakan rute",
                                        style = MaterialTheme.typography.bodySmall,
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

                    // Anti-Fake GPS Detection Security Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isFakeGpsDetected) StatusRed.copy(alpha = 0.12f) else StatusGreen.copy(alpha = 0.08f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isFakeGpsDetected) Icons.Default.GpsOff else Icons.Default.Security,
                                    contentDescription = null,
                                    tint = if (isFakeGpsDetected) StatusRed else StatusGreen,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isFakeGpsDetected) "Deteksi: Teridentifikasi Mock Provider" else "Anti-Fake GPS: Aman (GPS Asli)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isFakeGpsDetected) StatusRed else StatusGreen
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Telemetry Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryPill(
                            label = "Kecepatan",
                            value = "${(currentPt?.speedKmh ?: 0f).toInt()} km/h",
                            icon = Icons.Default.Speed,
                            tint = GeoBluePrimary,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryPill(
                            label = "Akurasi",
                            value = "±${(currentPt?.accuracyMeters ?: 3f).toInt()}m",
                            icon = Icons.Default.GpsFixed,
                            tint = GeoCyanAccent,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryPill(
                            label = "Elevasi",
                            value = "${(currentPt?.altitude ?: 25.0).toInt()} mdpl",
                            icon = Icons.Default.Terrain,
                            tint = StatusGreen,
                            modifier = Modifier.weight(1f)
                        )
                        val batteryVal = "${realBattery.percentage}%" + if (realBattery.isCharging) " ⚡" else ""
                        val batteryTint = when {
                            realBattery.isCharging -> StatusGreen
                            realBattery.percentage <= 20 -> StatusRed
                            realBattery.percentage <= 50 -> StatusYellow
                            else -> GeoBluePrimary
                        }
                        TelemetryPill(
                            label = if (realBattery.isCharging) "Baterai ⚡" else "Baterai HP",
                            value = batteryVal,
                            icon = if (realBattery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                            tint = batteryTint,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Chat Admin button (User only chats with Company Admin)
                    Button(
                        onClick = {
                            viewModel.openChatWithUser(user.userId)
                            showChatSheet = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("btn_open_chat_dispatcher"),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoBluePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("💬 Hubungi / Chat Admin Perusahaan", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Export History & Clear Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportHistory(historyPoints, "CSV") },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_export_csv"),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoBluePrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ekspor CSV", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.exportHistory(historyPoints, "GeoJSON") },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("btn_export_geojson"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("GeoJSON", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.clearHistory() },
                            modifier = Modifier
                                .height(46.dp)
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

    // 1-on-1 Chat Sheet with Admin
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
}

@Composable
private fun TelemetryPill(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
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
