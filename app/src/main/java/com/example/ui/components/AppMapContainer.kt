package com.example.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.VehicleType
import com.example.ui.theme.GeoBluePrimary

enum class MapEngine(val label: String) {
    OPEN_STREET_MAP("OpenStreetMap (OSM)"),
    TACTICAL_VECTOR("Peta Vektor Taktis")
}

@Composable
fun AppMapContainer(
    currentPoint: LocationPoint?,
    routePoints: List<LocationPoint>,
    geofences: List<GeofenceZone>,
    userLabel: String,
    isLiveTracking: Boolean,
    modifier: Modifier = Modifier,
    vehicleType: VehicleType = VehicleType.MOTORCYCLE,
    highlightPointIndex: Int? = null,
    onMapCenterRequested: () -> Unit = {}
) {
    var selectedEngine by remember { mutableStateOf(MapEngine.OPEN_STREET_MAP) }

    Box(modifier = modifier.fillMaxSize()) {
        when (selectedEngine) {
            MapEngine.OPEN_STREET_MAP -> {
                OpenStreetMapTrackerView(
                    currentPoint = currentPoint,
                    routePoints = routePoints,
                    geofences = geofences,
                    userLabel = userLabel,
                    vehicleType = vehicleType,
                    isLiveTracking = isLiveTracking,
                    onMapCenterRequested = onMapCenterRequested,
                    modifier = Modifier.fillMaxSize()
                )
            }
            MapEngine.TACTICAL_VECTOR -> {
                InteractiveMapView(
                    currentPoint = currentPoint,
                    routePoints = routePoints,
                    geofences = geofences,
                    userLabel = userLabel,
                    vehicleType = vehicleType,
                    isLiveTracking = isLiveTracking,
                    highlightPointIndex = highlightPointIndex,
                    onMapCenterRequested = onMapCenterRequested,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Map Engine Selector Pills (OSM vs Vector vs Google Maps)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // OSM Chip
                FilterChip(
                    selected = selectedEngine == MapEngine.OPEN_STREET_MAP,
                    onClick = { selectedEngine = MapEngine.OPEN_STREET_MAP },
                    label = {
                        Text(
                            text = "OSM",
                            fontSize = 11.sp,
                            fontWeight = if (selectedEngine == MapEngine.OPEN_STREET_MAP) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoBluePrimary,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier.testTag("chip_map_osm")
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Tactical Vector Chip
                FilterChip(
                    selected = selectedEngine == MapEngine.TACTICAL_VECTOR,
                    onClick = { selectedEngine = MapEngine.TACTICAL_VECTOR },
                    label = {
                        Text(
                            text = "Vektor",
                            fontSize = 11.sp,
                            fontWeight = if (selectedEngine == MapEngine.TACTICAL_VECTOR) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GeoBluePrimary,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    ),
                    modifier = Modifier.testTag("chip_map_vector")
                )
            }
        }
    }
}
