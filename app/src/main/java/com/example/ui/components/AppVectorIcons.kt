package com.example.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.VehicleType
import com.example.util.TurnType

fun VehicleType.getVectorIcon(): ImageVector = when (this) {
    VehicleType.MOTORCYCLE -> Icons.Default.TwoWheeler
    VehicleType.CAR -> Icons.Default.DirectionsCar
    VehicleType.BICYCLE -> Icons.AutoMirrored.Filled.DirectionsBike
    VehicleType.TRUCK -> Icons.Default.LocalShipping
    VehicleType.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
}

fun TurnType.getVectorIcon(): ImageVector = when (this) {
    TurnType.DEPART -> Icons.Default.NearMe
    TurnType.STRAIGHT -> Icons.Default.Straight
    TurnType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    TurnType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    TurnType.TURN_LEFT -> Icons.Default.TurnLeft
    TurnType.TURN_RIGHT -> Icons.Default.TurnRight
    TurnType.SHARP_LEFT -> Icons.Default.TurnSharpLeft
    TurnType.SHARP_RIGHT -> Icons.Default.TurnSharpRight
    TurnType.UTURN -> Icons.AutoMirrored.Filled.Undo
    TurnType.ARRIVE -> Icons.Default.Place
}

@Composable
fun VehicleIcon(
    vehicleType: VehicleType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 20.dp
) {
    Icon(
        imageVector = vehicleType.getVectorIcon(),
        contentDescription = vehicleType.label,
        tint = tint,
        modifier = modifier.size(size)
    )
}

@Composable
fun TurnManeuverIcon(
    turnType: TurnType,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp
) {
    Icon(
        imageVector = turnType.getVectorIcon(),
        contentDescription = turnType.defaultText,
        tint = tint,
        modifier = modifier.size(size)
    )
}
