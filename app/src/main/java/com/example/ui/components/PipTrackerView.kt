package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocationPoint
import com.example.data.model.UserProfile
import com.example.ui.theme.GeoBluePrimary
import com.example.ui.theme.StatusGreen
import com.example.util.BatteryStatus
import com.example.util.rememberCompassHeading

/**
 * Dedicated Picture-in-Picture (PiP) Floating Mini-Window.
 * Displays a clean, distraction-free live map with user orientation, current speed,
 * live status, and battery percentage when the user switches to other apps (WhatsApp, Instagram, etc.).
 */
@Composable
fun PipTrackerView(
    user: UserProfile,
    currentPoint: LocationPoint?,
    routePoints: List<LocationPoint>,
    batteryStatus: BatteryStatus,
    modifier: Modifier = Modifier
) {
    val compassHeading by rememberCompassHeading(enabled = true)

    // Pulsing live indicator dot
    val infiniteTransition = rememberInfiniteTransition(label = "pip_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pip_dot_alpha"
    )

    val currentSpeed = currentPoint?.speedKmh ?: user.lastSpeedKmh
    val displaySpeed = currentSpeed.toInt().coerceAtLeast(0)

    Box(modifier = modifier.fillMaxSize()) {
        // Base: Clean full-bleed OpenStreetMap tracker
        OpenStreetMapTrackerView(
            currentPoint = currentPoint,
            routePoints = routePoints,
            geofences = emptyList(),
            userLabel = user.fullName,
            vehicleType = user.vehicleType,
            isLiveTracking = true,
            compassHeading = compassHeading,
            isUserMode = true,
            showTopStatusPill = false,
            modifier = Modifier.fillMaxSize()
        )

        // Top Status Bar: Live badge & Speed badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live badge with pulsing green indicator
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.78f),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .alpha(pulseAlpha)
                            .background(StatusGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "LIVE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = StatusGreen
                    )
                }
            }

            // Speed badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.78f),
                shadowElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = GeoBluePrimary,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$displaySpeed km/h",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // Bottom Info Bar: Vehicle type, unit code / name, and battery level
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black.copy(alpha = 0.82f),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.vehicleType.iconEmoji,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = user.unitCode.ifBlank { user.fullName.take(12) },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (batteryStatus.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                        contentDescription = null,
                        tint = if (batteryStatus.percentage <= 20) Color(0xFFFF5252) else StatusGreen,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${batteryStatus.percentage}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
