package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CompanyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompany(company: CompanyEntity)

    @Query("SELECT * FROM companies WHERE companyId = :companyId LIMIT 1")
    suspend fun getCompanyById(companyId: String): CompanyEntity?

    @Query("SELECT * FROM companies WHERE adminEmail = :email LIMIT 1")
    suspend fun getCompanyByEmail(email: String): CompanyEntity?

    @Query("SELECT * FROM companies")
    fun getAllCompanies(): Flow<List<CompanyEntity>>
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE userId = :userId LIMIT 1")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE companyId = :companyId AND email = :email LIMIT 1")
    suspend fun getUserByCompanyAndEmail(companyId: String, email: String): UserEntity?

    @Query("SELECT * FROM users WHERE companyId = :companyId ORDER BY lastUpdatedAt DESC")
    fun getUsersForCompany(companyId: String): Flow<List<UserEntity>>

    @Query("UPDATE users SET isSharingLocation = :isSharing WHERE userId = :userId")
    suspend fun setLocationSharing(userId: String, isSharing: Boolean)

    @Query("""
        UPDATE users 
        SET lastLatitude = :lat, lastLongitude = :lng, lastSpeedKmh = :speed, lastUpdatedAt = :timestamp, hasFakeGpsAlert = :hasFakeGps 
        WHERE userId = :userId
    """)
    suspend fun updateLocation(userId: String, lat: Double, lng: Double, speed: Float, timestamp: Long, hasFakeGps: Boolean)
}

@Dao
interface LocationPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPoint(point: LocationPointEntity): Long

    @Query("SELECT * FROM location_points WHERE userId = :userId ORDER BY timestamp ASC")
    fun getPointsForUser(userId: String): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE companyId = :companyId ORDER BY timestamp DESC LIMIT 200")
    fun getRecentPointsForCompany(companyId: String): Flow<List<LocationPointEntity>>

    @Query("SELECT * FROM location_points WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestPointForUser(userId: String): LocationPointEntity?

    @Query("DELETE FROM location_points WHERE userId = :userId")
    suspend fun clearHistoryForUser(userId: String)
}

@Dao
interface GeofenceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGeofence(geofence: GeofenceEntity)

    @Query("SELECT * FROM geofences WHERE companyId = :companyId")
    fun getGeofencesForCompany(companyId: String): Flow<List<GeofenceEntity>>

    @Query("SELECT * FROM geofences WHERE companyId = :companyId")
    suspend fun getGeofencesList(companyId: String): List<GeofenceEntity>

    @Query("DELETE FROM geofences WHERE id = :id")
    suspend fun deleteGeofence(id: String)
}

@Dao
interface GeofenceAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: GeofenceAlertEntity)

    @Query("SELECT * FROM geofence_alerts WHERE companyId = :companyId ORDER BY timestamp DESC LIMIT 100")
    fun getAlertsForCompany(companyId: String): Flow<List<GeofenceAlertEntity>>
}
