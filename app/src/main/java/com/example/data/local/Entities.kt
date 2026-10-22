package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.CompanyProfile
import com.example.data.model.GeofenceAlert
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.UserProfile
import com.example.data.model.ZoneType

@Entity(tableName = "companies")
data class CompanyEntity(
    @PrimaryKey val companyId: String,
    val companyName: String,
    val adminEmail: String,
    val passwordHash: String,
    val passwordSalt: String,
    val sector: String,
    val registeredAt: Long = System.currentTimeMillis()
) {
    fun toProfile() = CompanyProfile(
        companyId = companyId,
        companyName = companyName,
        adminEmail = adminEmail,
        sector = sector,
        registeredAt = registeredAt
    )
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val userId: String,
    val companyId: String,
    val email: String,
    val fullName: String,
    val unitCode: String,
    val isSharingLocation: Boolean = false,
    val lastLatitude: Double = -6.2088,
    val lastLongitude: Double = 106.8456,
    val lastSpeedKmh: Float = 0f,
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val hasFakeGpsAlert: Boolean = false
) {
    fun toProfile() = UserProfile(
        userId = userId,
        companyId = companyId,
        email = email,
        fullName = fullName,
        unitCode = unitCode,
        isSharingLocation = isSharingLocation,
        lastLatitude = lastLatitude,
        lastLongitude = lastLongitude,
        lastSpeedKmh = lastSpeedKmh,
        lastUpdatedAt = lastUpdatedAt,
        hasFakeGpsAlert = hasFakeGpsAlert
    )
}

@Entity(tableName = "location_points")
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val companyId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speedKmh: Float,
    val accuracyMeters: Float,
    val bearing: Float,
    val timestamp: Long,
    val geofenceStatus: String,
    val batteryLevel: Int,
    val isMock: Boolean = false
) {
    fun toDomain() = LocationPoint(
        id = id,
        userId = userId,
        companyId = companyId,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speedKmh = speedKmh,
        accuracyMeters = accuracyMeters,
        bearing = bearing,
        timestamp = timestamp,
        geofenceStatus = geofenceStatus,
        batteryLevel = batteryLevel,
        isMock = isMock
    )
}

@Entity(tableName = "geofences")
data class GeofenceEntity(
    @PrimaryKey val id: String,
    val companyId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val zoneType: String,
    val colorHex: String
) {
    fun toDomain() = GeofenceZone(
        id = id,
        companyId = companyId,
        name = name,
        latitude = latitude,
        longitude = longitude,
        radiusMeters = radiusMeters,
        zoneType = runCatching { ZoneType.valueOf(zoneType) }.getOrDefault(ZoneType.OFFICE),
        colorHex = colorHex
    )
}

@Entity(tableName = "geofence_alerts")
data class GeofenceAlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val companyId: String,
    val userId: String,
    val userName: String,
    val geofenceName: String,
    val eventType: String,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val description: String,
    val isSecurityWarning: Boolean = false
) {
    fun toDomain() = GeofenceAlert(
        id = id,
        companyId = companyId,
        userId = userId,
        userName = userName,
        geofenceName = geofenceName,
        eventType = eventType,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        description = description,
        isSecurityWarning = isSecurityWarning
    )
}
