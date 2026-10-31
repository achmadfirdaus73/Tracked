package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CompanyProfile
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.UserProfile
import com.example.data.model.VehicleType
import com.example.data.model.ZoneType
import com.example.util.StopPointDetector
import com.example.util.RoadNetworkEngine
import com.example.ui.components.AdminUserChatSheet
import com.example.ui.components.AppMapContainer
import com.example.ui.components.InteractiveMapView
import com.example.ui.components.LocationSearchBar
import com.example.ui.components.RouteAnalyticsCharts
import com.example.ui.theme.GeoBluePrimary
import com.example.ui.theme.GeoCyanAccent
import com.example.ui.theme.StatusGreen
import com.example.ui.theme.StatusRed
import com.example.ui.theme.StatusYellow
import com.example.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    company: CompanyProfile,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val users by viewModel.companyUsers.collectAsState()
    val geofences by viewModel.companyGeofences.collectAsState()
    val alerts by viewModel.companyAlerts.collectAsState()
    val selectedUserId by viewModel.selectedUserId.collectAsState()
    val historyPoints by viewModel.targetUserHistory.collectAsState()
    val highlightedIndex by viewModel.highlightedPointIndex.collectAsState()
    val activeUserTask by viewModel.activeUserTask.collectAsState()
    val mqttConnected by viewModel.mqttIsConnected.collectAsState()

    var dashboardTab by remember { mutableIntStateOf(0) } // 0 = Peta Armada, 1 = Visualisasi Rute & Grafik, 2 = Geofencing, 3 = Karyawan
    var showAddGeofenceDialog by remember { mutableStateOf(false) }
    var showAddEmployeeDialog by remember { mutableStateOf(false) }
    var userToEditVehicle by remember { mutableStateOf<UserProfile?>(null) }
    var chatUser by remember { mutableStateOf<UserProfile?>(null) }

    // Destination Pinning & Stop Points state
    var isPinningDestinationMode by remember { mutableStateOf(false) }
    var showSetDestinationDialog by remember { mutableStateOf(false) }
    var showStopPointsSheet by remember { mutableStateOf(false) }
    var focusedGeoPoint by remember { mutableStateOf<org.osmdroid.util.GeoPoint?>(null) }
    var destNameInput by remember { mutableStateOf("") }
    var destNotesInput by remember { mutableStateOf("") }
    var pendingDestinationLat by remember { mutableDoubleStateOf(-6.2088) }
    var pendingDestinationLng by remember { mutableDoubleStateOf(106.8456) }

    val detectedStops = remember(historyPoints) {
        StopPointDetector.detectStopPoints(historyPoints, minDurationMinutes = 5)
    }

    // Auto select first user if none selected
    val activeSelectedUser = users.find { it.userId == selectedUserId } ?: users.firstOrNull()

    // Determine current point of selected user
    val currentSelectedPoint = if (historyPoints.isNotEmpty()) {
        historyPoints.last()
    } else if (activeSelectedUser != null) {
        LocationPoint(
            userId = activeSelectedUser.userId,
            companyId = company.companyId,
            latitude = activeSelectedUser.lastLatitude,
            longitude = activeSelectedUser.lastLongitude,
            speedKmh = activeSelectedUser.lastSpeedKmh,
            geofenceStatus = if (activeSelectedUser.isSharingLocation) "Live Aktif" else "Offline"
        )
    } else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = company.companyName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = GeoBluePrimary.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = company.companyId,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoBluePrimary
                                )
                            }
                        }
                        Text(
                            text = "Dashboard Pengawas Armada & Geofencing",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // MQTT Real-time Status Badge
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (mqttConnected) GeoCyanAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (mqttConnected) "⚡ MQTT Live" else "⚡ MQTT...",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (mqttConnected) GeoCyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    // Fleet Status Pill (Online / Standby Counts moved to TopAppBar)
                    val onlineCount = users.count { it.isSharingLocation }
                    val standbyCount = users.size - onlineCount
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🟢 $onlineCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StatusGreen)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("⚪ $standbyCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { viewModel.logout() },
                        modifier = Modifier.testTag("btn_admin_logout")
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Keluar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            if (dashboardTab == 2) {
                FloatingActionButton(
                    onClick = { showAddGeofenceDialog = true },
                    containerColor = GeoBluePrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_geofence")
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = "Tambah Geofence")
                }
            } else if (dashboardTab == 3) {
                FloatingActionButton(
                    onClick = { showAddEmployeeDialog = true },
                    containerColor = GeoBluePrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_add_employee")
                ) {
                    Icon(Icons.Default.Person, contentDescription = "Tambah Karyawan")
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Dashboard Tabs
            TabRow(
                selectedTabIndex = dashboardTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = dashboardTab == 0,
                    onClick = { dashboardTab = 0 },
                    text = { Text("Peta Armada", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_fleet_map")
                )
                Tab(
                    selected = dashboardTab == 1,
                    onClick = { dashboardTab = 1 },
                    text = { Text("Grafik Rute", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_route_graphs")
                )
                Tab(
                    selected = dashboardTab == 2,
                    onClick = { dashboardTab = 2 },
                    text = { Text("Geofencing (${geofences.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_geofencing")
                )
                Tab(
                    selected = dashboardTab == 3,
                    onClick = { dashboardTab = 3 },
                    text = { Text("Karyawan (${users.size})", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_employees")
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
            ) {
                if (dashboardTab == 0) {
                    // TAB 0: FLEET MONITORING MAP
                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        AppMapContainer(
                            currentPoint = currentSelectedPoint,
                            routePoints = historyPoints,
                            geofences = geofences,
                            destinationTask = activeUserTask,
                            focusedGeoPoint = focusedGeoPoint,
                            showTopStatusPill = false,
                            onMapTapped = if (isPinningDestinationMode) {
                                { lat, lng ->
                                    pendingDestinationLat = lat
                                    pendingDestinationLng = lng
                                    isPinningDestinationMode = false
                                    showSetDestinationDialog = true
                                }
                            } else null,
                            userLabel = if (activeSelectedUser?.hasFakeGpsAlert == true) "⚠️ ${activeSelectedUser.fullName} [FAKE GPS]" else activeSelectedUser?.fullName ?: "Armada",
                            vehicleType = activeSelectedUser?.vehicleType ?: VehicleType.MOTORCYCLE,
                            isLiveTracking = activeSelectedUser?.isSharingLocation ?: true,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Top Floating Overlay Container (Guaranteed NO overlap!)
                        Column(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Floating Top Banner when Pinning Destination Mode is Active
                            if (isPinningDestinationMode) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(14.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                            Text("🎯", fontSize = 24.sp)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Klik Langsung di Peta!",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                Text(
                                                    text = "Ketuk sembarang titik di peta untuk menaruh pin tujuan bagi ${activeSelectedUser?.fullName ?: "driver"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                                )
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { isPinningDestinationMode = false },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Batal", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                // Quick Floating Driver Info, Mission & Action Card with Integrated Search
                                activeSelectedUser?.let { selectedEmp ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                    // Vehicle Pin Avatar (Tapping allows changing the vehicle type)
                                                    Surface(
                                                        modifier = Modifier
                                                            .size(42.dp)
                                                            .clickable { userToEditVehicle = selectedEmp },
                                                        shape = RoundedCornerShape(12.dp),
                                                        color = GeoBluePrimary.copy(alpha = 0.12f)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(selectedEmp.vehicleType.iconEmoji, fontSize = 22.sp)
                                                        }
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                text = selectedEmp.fullName,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Surface(
                                                                shape = RoundedCornerShape(4.dp),
                                                                color = if (selectedEmp.isSharingLocation) StatusGreen.copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f)
                                                            ) {
                                                                Text(
                                                                    text = if (selectedEmp.isSharingLocation) "ONLINE" else "OFFLINE",
                                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                                    fontWeight = FontWeight.ExtraBold,
                                                                    color = if (selectedEmp.isSharingLocation) StatusGreen else Color.Gray
                                                                )
                                                            }
                                                        }

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            val batColor = when {
                                                                selectedEmp.isCharging -> StatusGreen
                                                                selectedEmp.batteryLevel <= 20 -> StatusRed
                                                                selectedEmp.batteryLevel <= 50 -> StatusYellow
                                                                else -> StatusGreen
                                                            }
                                                            Icon(
                                                                imageVector = if (selectedEmp.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                                                                contentDescription = null,
                                                                tint = batColor,
                                                                modifier = Modifier.size(13.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(3.dp))
                                                            Text(
                                                                text = "${selectedEmp.batteryLevel}%${if (selectedEmp.isCharging) " ⚡" else ""} • Unit ${selectedEmp.unitCode} • ${selectedEmp.vehicleType.label}",
                                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }

                                                // Action Buttons: Change Vehicle Icon & Chat
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    OutlinedButton(
                                                        onClick = { userToEditVehicle = selectedEmp },
                                                        shape = RoundedCornerShape(10.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        Text("Icon Pin", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                    }

                                                    Button(
                                                        onClick = {
                                                            chatUser = selectedEmp
                                                            viewModel.openChatWithUser(selectedEmp.userId)
                                                        },
                                                        shape = RoundedCornerShape(10.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = GeoBluePrimary),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        modifier = Modifier.testTag("btn_tab0_chat_driver")
                                                    ) {
                                                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(15.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }

                                            // Active Destination Mission Status (if assigned)
                                            activeUserTask?.let { task ->
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (task.status == "ARRIVED") StatusGreen.copy(alpha = 0.12f) else GeoBluePrimary.copy(alpha = 0.08f),
                                                    border = BorderStroke(1.dp, if (task.status == "ARRIVED") StatusGreen else GeoBluePrimary.copy(alpha = 0.4f))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                                            Text(if (task.status == "ARRIVED") "✅" else "🎯", fontSize = 18.sp)
                                                            Spacer(modifier = Modifier.width(8.dp))
                                                            Column {
                                                                Text(
                                                                    text = if (task.status == "ARRIVED") "Telah Tiba: ${task.destinationName}" else "Tujuan: ${task.destinationName}",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = if (task.status == "ARRIVED") StatusGreen else MaterialTheme.colorScheme.onSurface
                                                                )
                                                                if (task.status == "ARRIVED") {
                                                                    val dur = task.actualDurationMinutes ?: 0
                                                                    val timeStr = task.arrivedAt?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it)) } ?: "-"
                                                                    Text(
                                                                        text = "Tiba jam $timeStr • Waktu tempuh: $dur menit",
                                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                                        color = StatusGreen
                                                                    )
                                                                } else {
                                                                    val distKm = String.format(Locale.US, "%.1f km", task.distanceMeters / 1000.0)
                                                                    Text(
                                                                        text = "Jarak: $distKm • Estimasi: ~${task.estimatedMinutes} menit",
                                                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                                    )
                                                                }
                                                            }
                                                        }

                                                        if (task.status == "ASSIGNED") {
                                                            TextButton(
                                                                onClick = { viewModel.cancelDestinationTask(task.taskId) },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("Batalkan", color = StatusRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        } else if (task.status == "ARRIVED") {
                                                            TextButton(
                                                                onClick = {
                                                                    destNameInput = "Tujuan ${selectedEmp.fullName}"
                                                                    destNotesInput = ""
                                                                    isPinningDestinationMode = true
                                                                },
                                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                                            ) {
                                                                Text("Tugas Baru", color = GeoBluePrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            // Integrated Location Search Bar (For assigning destination by searching place)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LocationSearchBar(
                                                vehicleType = selectedEmp.vehicleType,
                                                anchorLat = currentSelectedPoint?.latitude ?: -6.2088,
                                                anchorLng = currentSelectedPoint?.longitude ?: 106.8456,
                                                placeholderText = "Cari tujuan ${selectedEmp.fullName} (Mall, Pos, Jalan)...",
                                                modifier = Modifier.fillMaxWidth(),
                                                onLocationSelected = { result ->
                                                    destNameInput = result.name
                                                    pendingDestinationLat = result.latitude
                                                    pendingDestinationLng = result.longitude
                                                    focusedGeoPoint = org.osmdroid.util.GeoPoint(result.latitude, result.longitude)
                                                    isPinningDestinationMode = false
                                                    showSetDestinationDialog = true
                                                }
                                            )

                                            // Mission Pin & Stop Points Action Buttons Row
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        destNameInput = "Tujuan ${selectedEmp.fullName}"
                                                        destNotesInput = ""
                                                        isPinningDestinationMode = true
                                                    },
                                                    modifier = Modifier.weight(1f).testTag("btn_tab0_set_destination"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        if (activeUserTask != null && activeUserTask?.status == "ASSIGNED") "Ubah di Peta" else "Tandai di Peta",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                OutlinedButton(
                                                    onClick = { showStopPointsSheet = true },
                                                    modifier = Modifier.weight(1f).testTag("btn_tab0_view_stops"),
                                                    shape = RoundedCornerShape(10.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Text("🛑", fontSize = 14.sp)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        "Titik Berhenti (${detectedStops.size})",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (dashboardTab == 1) {
                    // TAB 1: ROUTE ANALYTICS & CHARTS
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(vertical = 12.dp)
                    ) {
                        // User Switcher Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Analisis Perjalanan: ${activeSelectedUser?.fullName ?: "Pilih Karyawan"}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${activeSelectedUser?.unitCode ?: "-"} • Total ${historyPoints.size} titik koordinat terekam",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = { viewModel.exportHistory(historyPoints, "CSV") },
                                        modifier = Modifier.testTag("btn_admin_export_csv"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = GeoBluePrimary)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CSV", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = { viewModel.exportHistory(historyPoints, "GeoJSON") },
                                        modifier = Modifier.testTag("btn_admin_export_geojson"),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("GeoJSON", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // High-fidelity Informative & Responsive Graphs
                        RouteAnalyticsCharts(
                            points = historyPoints,
                            selectedIndex = highlightedIndex,
                            onPointSelected = { viewModel.setHighlightedIndex(it) }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Detected Stop Points Section (Titik Berhenti 5-10+ Menit)
                        val detectedStops = remember(historyPoints) {
                            StopPointDetector.detectStopPoints(historyPoints, minDurationMinutes = 5)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Text(
                                    text = "🛑 Riwayat Titik Berhenti (${detectedStops.size} Titik)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Mendeteksi secara otomatis saat armada berhenti 5 hingga 10+ menit",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                if (detectedStops.isEmpty()) {
                                    Text(
                                        text = "Belum ada titik henti melebihi 5 menit pada rute ini.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        detectedStops.forEachIndexed { idx, stop ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                color = StatusRed.copy(alpha = 0.08f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier.size(34.dp).background(StatusRed, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("${idx + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = "🛑 Berhenti: ${stop.durationMinutes} Menit",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp,
                                                            color = StatusRed
                                                        )
                                                        Text(
                                                            text = "Waktu: ${StopPointDetector.formatTime(stop.startTimestamp)} - ${StopPointDetector.formatTime(stop.endTimestamp)}",
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = String.format(Locale.US, "Koordinat: %.5f, %.5f", stop.latitude, stop.longitude),
                                                            fontSize = 10.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                } else if (dashboardTab == 2) {
                    // TAB 2: GEOFENCING MANAGEMENT & ALERT LOGS
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Text(
                                text = "Daftar Wilayah Geofence Perusahaan",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Perangkat akan mendeteksi otomatis saat user masuk atau keluar radius zona",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        items(geofences) { fence ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("geofence_card_${fence.id}"),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    try {
                                                        Color(android.graphics.Color.parseColor(fence.colorHex)).copy(alpha = 0.18f)
                                                    } catch (e: Exception) {
                                                        GeoBluePrimary.copy(alpha = 0.18f)
                                                    },
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Security,
                                                contentDescription = null,
                                                tint = try {
                                                    Color(android.graphics.Color.parseColor(fence.colorHex))
                                                } catch (e: Exception) {
                                                    GeoBluePrimary
                                                },
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = fence.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Radius: ${fence.radiusMeters.toInt()}m • ${fence.zoneType.label}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Pusat: ${String.format(Locale.US, "%.4f, %.4f", fence.latitude, fence.longitude)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }

                                    IconButton(
                                        onClick = { viewModel.deleteGeofence(fence.id) },
                                        modifier = Modifier.testTag("btn_delete_geofence_${fence.id}")
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Hapus", tint = StatusRed)
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Riwayat Peringatan & Aktivitas Geofence (${alerts.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (alerts.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        text = "Belum ada insiden atau pelanggaran batas geofence yang tercatat.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(alerts) { alt ->
                                val isMockAlert = alt.eventType == "FAKE_GPS_TERDETEKSI" || alt.isSecurityWarning
                                val alertColor = if (isMockAlert) StatusRed else if (alt.eventType == "MASUK") StatusGreen else StatusYellow
                                val alertIcon = if (isMockAlert) Icons.Default.Warning else if (alt.eventType == "MASUK") Icons.Default.CheckCircle else Icons.Default.Warning

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isMockAlert) StatusRed.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = alertIcon,
                                            contentDescription = null,
                                            tint = alertColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (isMockAlert) "⚠️ FAKE GPS: ${alt.userName}" else "${alt.eventType} - ${alt.geofenceName}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (isMockAlert) StatusRed else MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(alt.timestamp)),
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                            Text(
                                                text = alt.description,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // TAB 3: EMPLOYEE MANAGEMENT
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Text(
                                text = "Daftar Karyawan / Driver Terdaftar",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Karyawan masuk menggunakan Email dan ID Perusahaan (${company.companyId})",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (users.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Text(
                                        text = "Belum ada karyawan terdaftar. Ketuk tombol + di bawah untuk mendaftarkan karyawan baru.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(users) { usr ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("employee_card_${usr.userId}"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .background(GeoBluePrimary.copy(alpha = 0.12f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(usr.vehicleType.iconEmoji, fontSize = 22.sp)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = usr.fullName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${usr.email} • ${usr.unitCode}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    text = "Tipe: ${usr.vehicleType.iconEmoji} ${usr.vehicleType.label}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = GeoBluePrimary,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Baterai HP: ${usr.batteryLevel}%${if (usr.isCharging) " ⚡ (Charging)" else ""}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = when {
                                                        usr.isCharging -> StatusGreen
                                                        usr.batteryLevel <= 20 -> StatusRed
                                                        usr.batteryLevel <= 50 -> StatusYellow
                                                        else -> GeoBluePrimary
                                                    },
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = if (usr.hasFakeGpsAlert) "⚠️ FAKE GPS TERDETEKSI" else if (usr.isSharingLocation) "🟢 Live Aktif" else "🔴 Offline",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (usr.hasFakeGpsAlert) StatusRed else if (usr.isSharingLocation) StatusGreen else Color.Gray,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                TextButton(
                                                    onClick = {
                                                        chatUser = usr
                                                        viewModel.openChatWithUser(usr.userId)
                                                    },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = GeoBluePrimary),
                                                    modifier = Modifier.testTag("btn_chat_employee_${usr.userId}")
                                                ) {
                                                    Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                TextButton(
                                                    onClick = {
                                                        viewModel.selectUser(usr.userId)
                                                        if (usr.lastLatitude != 0.0 && usr.lastLongitude != 0.0) {
                                                            focusedGeoPoint = org.osmdroid.util.GeoPoint(usr.lastLatitude, usr.lastLongitude)
                                                        }
                                                        dashboardTab = 0
                                                    },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = GeoCyanAccent),
                                                    modifier = Modifier.testTag("btn_map_employee_${usr.userId}")
                                                ) {
                                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Peta", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                                TextButton(
                                                    onClick = { userToEditVehicle = usr },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                                                ) {
                                                    Text("Icon", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add Geofence Dialog
    if (showAddGeofenceDialog) {
        var newFenceName by remember { mutableStateOf("") }
        var newFenceLat by remember { mutableStateOf("-6.2100") }
        var newFenceLng by remember { mutableStateOf("106.8480") }
        var newFenceRadius by remember { mutableFloatStateOf(400f) }
        var selectedZoneType by remember { mutableStateOf(ZoneType.OFFICE) }

        AlertDialog(
            onDismissRequest = { showAddGeofenceDialog = false },
            title = { Text("Tambah Zona Geofence Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = newFenceName,
                        onValueChange = { newFenceName = it },
                        label = { Text("Nama Zona / Titik Pantau") },
                        placeholder = { Text("Contoh: Gudang Barat Cikarang") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_fence_name")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newFenceLat,
                        onValueChange = { newFenceLat = it },
                        label = { Text("Latitude Pusat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = newFenceLng,
                        onValueChange = { newFenceLng = it },
                        label = { Text("Longitude Pusat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Radius Zona: ${newFenceRadius.toInt()} meter",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Slider(
                        value = newFenceRadius,
                        onValueChange = { newFenceRadius = it },
                        valueRange = 100f..2500f,
                        steps = 23,
                        modifier = Modifier.testTag("slider_fence_radius")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Kategori Wilayah",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ZoneType.entries.forEach { zType ->
                            FilterChip(
                                selected = selectedZoneType == zType,
                                onClick = { selectedZoneType = zType },
                                label = { Text(zType.label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lat = newFenceLat.toDoubleOrNull() ?: -6.2088
                        val lng = newFenceLng.toDoubleOrNull() ?: 106.8456
                        if (newFenceName.isNotBlank()) {
                            viewModel.addGeofence(
                                name = newFenceName,
                                lat = lat,
                                lng = lng,
                                radiusM = newFenceRadius,
                                zoneType = selectedZoneType
                            )
                            showAddGeofenceDialog = false
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_add_geofence")
                ) {
                    Text("Simpan Zona")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGeofenceDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Add Employee Dialog
    if (showAddEmployeeDialog) {
        var empName by remember { mutableStateOf("") }
        var empEmail by remember { mutableStateOf("") }
        var empUnit by remember { mutableStateOf("Armada Unit-01") }
        var selectedVehicle by remember { mutableStateOf(VehicleType.MOTORCYCLE) }

        AlertDialog(
            onDismissRequest = { showAddEmployeeDialog = false },
            title = { Text("Daftarkan Karyawan / Driver Baru", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "Karyawan dapat masuk menggunakan Email ini dan ID Perusahaan (${company.companyId})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = empName,
                        onValueChange = { empName = it },
                        label = { Text("Nama Lengkap Karyawan") },
                        placeholder = { Text("Contoh: Andi Wijaya") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_emp_name")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = empEmail,
                        onValueChange = { empEmail = it },
                        label = { Text("Email Karyawan") },
                        placeholder = { Text("andi@perusahaan.com") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_emp_email")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = empUnit,
                        onValueChange = { empUnit = it },
                        label = { Text("Kode Armada / Unit") },
                        placeholder = { Text("Contoh: Truk Box C-04") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_emp_unit")
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Pilih Jenis Kendaraan / Icon Pin:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(VehicleType.entries) { vType ->
                            FilterChip(
                                selected = selectedVehicle == vType,
                                onClick = { selectedVehicle = vType },
                                label = { Text("${vType.iconEmoji} ${vType.label}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GeoBluePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (empName.isNotBlank() && empEmail.isNotBlank()) {
                            viewModel.addEmployee(empName, empEmail, empUnit, selectedVehicle)
                            showAddEmployeeDialog = false
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_add_employee")
                ) {
                    Text("Daftarkan Karyawan")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEmployeeDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Edit Employee Vehicle Icon Dialog
    userToEditVehicle?.let { targetUser ->
        var selectedVehicle by remember(targetUser) { mutableStateOf(targetUser.vehicleType) }

        AlertDialog(
            onDismissRequest = { userToEditVehicle = null },
            title = {
                Text(
                    text = "Pilih Icon Kendaraan: ${targetUser.fullName}",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Icon pin armada ini akan tampil di peta pemantauan admin dan rute live karyawan.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VehicleType.entries.forEach { vType ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (selectedVehicle == vType) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (selectedVehicle == vType) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedVehicle = vType }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(vType.iconEmoji, fontSize = 24.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(vType.label, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            when (vType) {
                                                VehicleType.MOTORCYCLE -> "Armada Sepeda Motor"
                                                VehicleType.CAR -> "Armada Mobil"
                                                VehicleType.BICYCLE -> "Armada Sepeda"
                                                VehicleType.TRUCK -> "Armada Truk Logistik"
                                                VehicleType.WALK -> "Pejalan Kaki"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (selectedVehicle == vType) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateEmployeeVehicleType(targetUser.userId, selectedVehicle)
                        userToEditVehicle = null
                    }
                ) {
                    Text("Simpan Icon")
                }
            },
            dismissButton = {
                TextButton(onClick = { userToEditVehicle = null }) {
                    Text("Batal")
                }
            }
        )
    }

    // 1-on-1 Chat Sheet with Driver (Admin <-> Specific Driver only)
    chatUser?.let { targetUser ->
        val chatMessages by viewModel.activeChatMessages.collectAsState()
        AdminUserChatSheet(
            title = "Chat Driver: ${targetUser.fullName}",
            subtitle = "${targetUser.unitCode} • ${targetUser.vehicleType.label} • ${company.companyName}",
            messages = chatMessages,
            currentSenderId = "admin",
            currentSenderRole = "ADMIN",
            onSendMessage = { text ->
                viewModel.sendChatMessage(text, targetUser.userId)
            },
            onDismiss = {
                chatUser = null
                viewModel.closeChat()
            }
        )
    }

    // Set Destination Dialog (Penugasan Titik Tujuan Driver & Pilihan Rute Jalan)
    if (showSetDestinationDialog && activeSelectedUser != null) {
        val selectedEmp = activeSelectedUser
        val currLat = currentSelectedPoint?.latitude ?: -6.2088
        val currLng = currentSelectedPoint?.longitude ?: 106.8456
        val distMeters = RoadNetworkEngine.calculateDistanceMeters(
            currLat, currLng,
            pendingDestinationLat, pendingDestinationLng
        )

        var routeOptions by remember { mutableStateOf<List<RoadNetworkEngine.RouteOption>>(emptyList()) }
        var selectedRouteIndex by remember { mutableStateOf(0) }
        var isLoadingRoutes by remember { mutableStateOf(true) }

        LaunchedEffect(pendingDestinationLat, pendingDestinationLng) {
            isLoadingRoutes = true
            routeOptions = RoadNetworkEngine.fetchAlternativeRoutes(
                startLat = currLat,
                startLng = currLng,
                destLat = pendingDestinationLat,
                destLng = pendingDestinationLng,
                vehicleType = selectedEmp.vehicleType
            )
            selectedRouteIndex = 0
            isLoadingRoutes = false
        }

        val activeRoute = routeOptions.getOrNull(selectedRouteIndex)
        val routeDistanceMeters = activeRoute?.distanceMeters ?: distMeters
        val routeEstimatedMinutes = if (activeRoute != null) {
            (activeRoute.durationSeconds / 60.0).toInt().coerceAtLeast(1)
        } else {
            val speedKmh = when (selectedEmp.vehicleType) {
                VehicleType.MOTORCYCLE -> 28.0
                VehicleType.CAR -> 24.0
                VehicleType.TRUCK -> 20.0
                VehicleType.BICYCLE -> 14.0
                VehicleType.WALK -> 4.5
            }
            ((distMeters / 1000.0) / speedKmh * 60.0).toInt().coerceAtLeast(1)
        }

        AlertDialog(
            onDismissRequest = { showSetDestinationDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Konfirmasi Tujuan & Pilihan Rute", fontWeight = FontWeight.Bold)
                        Text(
                            text = "Titik berhasil ditandai dari peta!",
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusGreen
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Driver & Selected Route ETA Summary Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = GeoBluePrimary.copy(alpha = 0.08f),
                        border = BorderStroke(1.dp, GeoBluePrimary.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Driver: ${selectedEmp.fullName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = GeoBluePrimary
                                )
                                Text(
                                    text = "${selectedEmp.vehicleType.iconEmoji} ${selectedEmp.vehicleType.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Jarak via Rute Pilihan:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    String.format(Locale.US, "%.1f km", routeDistanceMeters / 1000.0),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Estimasi Waktu Sampai:", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "~$routeEstimatedMinutes menit",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = StatusGreen
                                )
                            }
                        }
                    }

                    // Pilihan Rute Jalan (Hingga 3 Rute Alternatif)
                    Text(
                        text = "Pilih Rute Jalan (${routeOptions.size.coerceAtLeast(1)} Pilihan Tersedia):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (isLoadingRoutes) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = GeoBluePrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Menghitung rute alternatif...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            routeOptions.forEachIndexed { idx, option ->
                                val isSelected = selectedRouteIndex == idx
                                val isNonToll = selectedEmp.vehicleType in listOf(VehicleType.MOTORCYCLE, VehicleType.BICYCLE, VehicleType.WALK)
                                val distKm = String.format(Locale.US, "%.1f km", option.distanceMeters / 1000.0)
                                val durMin = (option.durationSeconds / 60.0).toInt().coerceAtLeast(1)

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedRouteIndex = idx },
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) GeoBluePrimary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) GeoBluePrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { selectedRouteIndex = idx },
                                            colors = RadioButtonDefaults.colors(selectedColor = GeoBluePrimary)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = option.label,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) GeoBluePrimary else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (idx == 0) {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = StatusGreen.copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = "TERCEPAT",
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = StatusGreen
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "${option.summaryRoads} • $distKm • ~$durMin mnt",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (isNonToll) {
                                                Text(
                                                    text = "🛡️ Bebas Jalan Tol (${selectedEmp.vehicleType.iconEmoji})",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF15803D)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = destNameInput,
                        onValueChange = { destNameInput = it },
                        label = { Text("Nama Lokasi Tujuan") },
                        placeholder = { Text("Contoh: Gudang Logistik / Klien / Kantor") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_destination_name")
                    )

                    // Preset Quick Names
                    Text("Pilih Nama Cepat (Preset):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { destNameInput = "Kantor Pusat Sudirman" },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Kantor", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { destNameInput = "Gudang Logistik" },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Gudang", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { destNameInput = "Lokasi Klien" },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Klien", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OutlinedTextField(
                        value = destNotesInput,
                        onValueChange = { destNotesInput = it },
                        label = { Text("Catatan / Instruksi (Opsional)") },
                        placeholder = { Text("Contoh: Antar berkas atau hubungi saat tiba") },
                        maxLines = 2,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_destination_notes")
                    )

                    // Button to re-tap map if needed
                    OutlinedButton(
                        onClick = {
                            showSetDestinationDialog = false
                            isPinningDestinationMode = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("📍 Ubah Titik Pin di Peta (Ketuk Ulang)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = if (destNameInput.isNotBlank()) destNameInput else "Titik Tujuan Tugas"
                        viewModel.assignDestinationTask(
                            userId = selectedEmp.userId,
                            destinationName = name,
                            latitude = pendingDestinationLat,
                            longitude = pendingDestinationLng,
                            notes = destNotesInput,
                            selectedRouteIndex = selectedRouteIndex,
                            selectedRouteName = activeRoute?.label ?: "Rute 1 (Tercepat)",
                            customDistanceMeters = routeDistanceMeters.toFloat(),
                            customEtaMinutes = routeEstimatedMinutes
                        )
                        showSetDestinationDialog = false
                    },
                    modifier = Modifier.testTag("btn_confirm_assign_task")
                ) {
                    Text("🚀 Kirim Tugas ke Driver", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSetDestinationDialog = false }) {
                    Text("Batal")
                }
            }
        )
    }

    // Stop Points Inspection Dialog / Sheet
    if (showStopPointsSheet && activeSelectedUser != null) {
        val selectedEmp = activeSelectedUser
        AlertDialog(
            onDismissRequest = { showStopPointsSheet = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛑", fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Titik Berhenti Driver", fontWeight = FontWeight.Bold)
                        Text(
                            text = "${selectedEmp.fullName} (${selectedEmp.unitCode}) • ${detectedStops.size} Titik",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                ) {
                    Text(
                        text = "Lokasi terdeteksi di mana driver berhenti 5 menit atau lebih:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (detectedStops.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🟢", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Tidak Ada Titik Berhenti Lama",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    "Armada bergerak lancar tanpa henti melebihi 5 menit pada rute ini.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(detectedStops) { stop ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = StatusRed.copy(alpha = 0.08f),
                                    border = BorderStroke(1.dp, StatusRed.copy(alpha = 0.3f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .background(StatusRed, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Timer,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Berhenti: ${stop.durationMinutes} Menit",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = StatusRed
                                                )
                                                Text(
                                                    text = "${StopPointDetector.formatTime(stop.startTimestamp)} - ${StopPointDetector.formatTime(stop.endTimestamp)}",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = String.format(Locale.US, "%.5f, %.5f", stop.latitude, stop.longitude),
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                focusedGeoPoint = org.osmdroid.util.GeoPoint(stop.latitude, stop.longitude)
                                                showStopPointsSheet = false
                                            },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text("Pusatkan", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showStopPointsSheet = false }) {
                    Text("Tutup")
                }
            }
        )
    }
}
