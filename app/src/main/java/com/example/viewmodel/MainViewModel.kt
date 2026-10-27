package com.example.viewmodel

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.AuthSession
import com.example.data.model.ChatMessage
import com.example.data.model.CompanyProfile
import com.example.data.model.GeofenceAlert
import com.example.data.model.GeofenceZone
import com.example.data.model.LocationPoint
import com.example.data.model.UserProfile
import com.example.data.model.ZoneType
import com.example.data.repository.TrackingRepository
import com.example.util.BatteryHelper
import com.example.util.BatteryStatus
import com.example.util.ExportUtil
import com.example.util.LocationEngine
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(private val application: Application) : ViewModel() {
    init {
        com.example.util.FirebaseInitializer.init(application)
    }

    val repository = TrackingRepository()
    val locationEngine = LocationEngine(application, repository, viewModelScope)

    private val _session = MutableStateFlow<AuthSession>(AuthSession.None)
    val session: StateFlow<AuthSession> = _session.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Live Tracking State for Field User
    private val _isSharingLocation = MutableStateFlow(false)
    val isSharingLocation: StateFlow<Boolean> = _isSharingLocation.asStateFlow()

    private val _currentPoint = MutableStateFlow<LocationPoint?>(null)
    val currentPoint: StateFlow<LocationPoint?> = _currentPoint.asStateFlow()

    // Admin Dashboard states
    private val _selectedUserId = MutableStateFlow<String?>(null)
    val selectedUserId: StateFlow<String?> = _selectedUserId.asStateFlow()

    private val _activeAlertBanner = MutableStateFlow<GeofenceAlert?>(null)
    val activeAlertBanner: StateFlow<GeofenceAlert?> = _activeAlertBanner.asStateFlow()

    // Scrubber / Inspection index on chart
    private val _highlightedPointIndex = MutableStateFlow<Int?>(null)
    val highlightedPointIndex: StateFlow<Int?> = _highlightedPointIndex.asStateFlow()

    private val _installedFakeApps = MutableStateFlow<List<String>>(emptyList())
    val installedFakeApps: StateFlow<List<String>> = _installedFakeApps.asStateFlow()

    // Reactive streams scoped to current session
    val companyUsers: StateFlow<List<UserProfile>> = _session.flatMapLatest { sess ->
        when (sess) {
            is AuthSession.AdminSession -> repository.observeUsersForCompany(sess.company.companyId)
            is AuthSession.UserSession -> repository.observeUsersForCompany(sess.user.companyId)
            else -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companyGeofences: StateFlow<List<GeofenceZone>> = _session.flatMapLatest { sess ->
        when (sess) {
            is AuthSession.AdminSession -> repository.observeGeofences(sess.company.companyId)
            is AuthSession.UserSession -> repository.observeGeofences(sess.user.companyId)
            else -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val companyAlerts: StateFlow<List<GeofenceAlert>> = _session.flatMapLatest { sess ->
        when (sess) {
            is AuthSession.AdminSession -> repository.observeAlerts(sess.company.companyId)
            is AuthSession.UserSession -> repository.observeAlerts(sess.user.companyId)
            else -> flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // History points for selected user in admin view OR current user in user view
    val targetUserHistory: StateFlow<List<LocationPoint>> = _selectedUserId.flatMapLatest { uid ->
        val effectiveUid = uid ?: (_session.value as? AuthSession.UserSession)?.user?.userId
        if (effectiveUid != null) {
            repository.observeUserHistory(effectiveUid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 1-on-1 Chat Thread State (Admin <-> Employee). Users can ONLY chat with Admin.
    private val _activeChatUserId = MutableStateFlow<String?>(null)
    val activeChatUserId: StateFlow<String?> = _activeChatUserId.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeChatMessages: StateFlow<List<ChatMessage>> = _activeChatUserId.flatMapLatest { targetUid ->
        val sess = _session.value
        val effectiveUid = when (sess) {
            is AuthSession.UserSession -> sess.user.userId
            is AuthSession.AdminSession -> targetUid
            else -> null
        }
        val companyId = when (sess) {
            is AuthSession.UserSession -> sess.user.companyId
            is AuthSession.AdminSession -> sess.company.companyId
            else -> null
        }
        if (effectiveUid != null && companyId != null) {
            repository.observeChatMessages(companyId, effectiveUid)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openChatWithUser(userId: String?) {
        _activeChatUserId.value = userId
    }

    fun closeChat() {
        _activeChatUserId.value = null
    }

    fun sendChatMessage(text: String, targetUserId: String? = null) {
        if (text.isBlank()) return
        val sess = _session.value
        viewModelScope.launch {
            when (sess) {
                is AuthSession.UserSession -> {
                    // User only chats with their Company Admin (never with other users)
                    repository.sendChatMessage(
                        companyId = sess.user.companyId,
                        userId = sess.user.userId,
                        senderId = sess.user.userId,
                        senderName = sess.user.fullName,
                        senderRole = "USER",
                        text = text
                    )
                }
                is AuthSession.AdminSession -> {
                    val uid = targetUserId ?: _activeChatUserId.value
                    if (uid != null) {
                        repository.sendChatMessage(
                            companyId = sess.company.companyId,
                            userId = uid,
                            senderId = "admin",
                            senderName = "Admin (${sess.company.companyName})",
                            senderRole = "ADMIN",
                            text = text
                        )
                    }
                }
                else -> {}
            }
        }
    }

    fun getDeviceBatteryStatus(): BatteryStatus {
        return BatteryHelper.getBatteryStatus(application)
    }

    fun selectUser(userId: String?) {
        _selectedUserId.value = userId
        _highlightedPointIndex.value = null
    }

    fun setHighlightedIndex(index: Int?) {
        _highlightedPointIndex.value = index
    }

    fun dismissAlertBanner() {
        _activeAlertBanner.value = null
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun registerCompany(
        name: String,
        email: String,
        password: String,
        customId: String?,
        sector: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.registerCompany(name, email, password, customId, sector)
            _isLoading.value = false
            result.onSuccess { company ->
                _session.value = AuthSession.AdminSession(company)
                _successMessage.value = "Perusahaan '${company.companyName}' (${company.companyId}) berhasil didaftarkan!"
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal mendaftarkan perusahaan"
            }
        }
    }

    fun loginCompany(identifier: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.loginCompany(identifier, pass)
            _isLoading.value = false
            result.onSuccess { company ->
                _session.value = AuthSession.AdminSession(company)
                _successMessage.value = "Selamat datang, Administrator ${company.companyName}!"
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Login perusahaan gagal"
            }
        }
    }

    fun loginUser(companyId: String, email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            val result = repository.loginUser(companyId, email)
            _isLoading.value = false
            result.onSuccess { user ->
                _session.value = AuthSession.UserSession(user)
                _successMessage.value = "Berhasil masuk sebagai ${user.fullName} (${user.companyId})"
                // Auto start location sharing on user sign in
                toggleLocationSharing(true)
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Login user gagal"
            }
        }
    }

    fun toggleLocationSharing(enabled: Boolean) {
        val userSession = _session.value as? AuthSession.UserSession ?: return
        _isSharingLocation.value = enabled
        viewModelScope.launch {
            repository.setLocationSharing(userSession.user.userId, enabled)
            if (enabled) {
                val newSessionId = UUID.randomUUID().toString()
                locationEngine.startTracking(
                    user = userSession.user,
                    sessionId = newSessionId,
                    onPointRecorded = { pt ->
                        _currentPoint.value = pt
                    },
                    onAlertTriggered = { alert ->
                        _activeAlertBanner.value = alert
                    }
                )
            } else {
                locationEngine.stopTracking()
            }
        }
    }

    fun scanDeviceForFakeGps() {
        viewModelScope.launch {
            val detected = com.example.util.FakeGpsDetector.findInstalledFakeGpsApps(application)
            _installedFakeApps.value = detected
            if (detected.isNotEmpty()) {
                _errorMessage.value = "⚠️ Terdeteksi ${detected.size} aplikasi Fake GPS pada perangkat: ${detected.joinToString(", ")}"
            } else {
                _successMessage.value = "✓ Perangkat Bersih: Tidak ditemukan aplikasi Fake GPS terpasang."
            }
        }
    }

    fun addGeofence(
        name: String,
        lat: Double,
        lng: Double,
        radiusM: Float,
        zoneType: ZoneType
    ) {
        val sess = _session.value
        val companyId = when (sess) {
            is AuthSession.AdminSession -> sess.company.companyId
            is AuthSession.UserSession -> sess.user.companyId
            else -> return
        }

        viewModelScope.launch {
            val newZone = GeofenceZone(
                id = UUID.randomUUID().toString(),
                companyId = companyId,
                name = name.trim(),
                latitude = lat,
                longitude = lng,
                radiusMeters = radiusM,
                zoneType = zoneType,
                colorHex = zoneType.defaultColor
            )
            repository.addGeofence(newZone)
            _successMessage.value = "Zona geofence '${newZone.name}' berhasil ditambahkan!"
        }
    }

    fun addEmployee(
        fullName: String,
        email: String,
        unitCode: String,
        vehicleType: com.example.data.model.VehicleType = com.example.data.model.VehicleType.MOTORCYCLE
    ) {
        val sess = _session.value
        val companyId = when (sess) {
            is AuthSession.AdminSession -> sess.company.companyId
            else -> return
        }

        viewModelScope.launch {
            val result = repository.addEmployee(companyId, email, fullName, unitCode, vehicleType)
            result.onSuccess { user ->
                _successMessage.value = "Karyawan '${user.fullName}' (${user.unitCode}) berhasil didaftarkan!"
            }.onFailure { err ->
                _errorMessage.value = err.message ?: "Gagal mendaftarkan karyawan"
            }
        }
    }

    fun updateEmployeeVehicleType(userId: String, vehicleType: com.example.data.model.VehicleType) {
        viewModelScope.launch {
            repository.updateUserVehicleType(userId, vehicleType)
            _successMessage.value = "Tipe kendaraan berhasil diubah menjadi ${vehicleType.iconEmoji} ${vehicleType.label}"
        }
    }

    fun deleteGeofence(id: String) {
        viewModelScope.launch {
            repository.deleteGeofence(id)
            _successMessage.value = "Zona geofence dihapus."
        }
    }

    fun exportHistory(points: List<LocationPoint>, format: String) {
        if (points.isEmpty()) {
            Toast.makeText(application, "Tidak ada data lokasi untuk diekspor", Toast.LENGTH_SHORT).show()
            return
        }
        val app = application
        if (format.equals("CSV", ignoreCase = true)) {
            val csv = ExportUtil.generateCsv(points)
            ExportUtil.shareExport(
                context = app,
                content = csv,
                title = "Ekspor_Histori_Lokasi_${System.currentTimeMillis()}.csv",
                mimeType = "text/csv"
            )
        } else {
            val geoJson = ExportUtil.generateGeoJson(points)
            ExportUtil.shareExport(
                context = app,
                content = geoJson,
                title = "Ekspor_Rute_GeoJSON_${System.currentTimeMillis()}.geojson",
                mimeType = "application/geo+json"
            )
        }
    }

    fun clearHistory() {
        val userSession = _session.value as? AuthSession.UserSession ?: return
        viewModelScope.launch {
            repository.clearHistory(userSession.user.userId)
            _successMessage.value = "Riwayat perjalanan berhasil direset."
        }
    }

    fun logout() {
        locationEngine.stopTracking()
        _isSharingLocation.value = false
        _currentPoint.value = null
        _selectedUserId.value = null
        _session.value = AuthSession.None
    }

    override fun onCleared() {
        super.onCleared()
        locationEngine.stopTracking()
    }

    companion object {
        fun provideFactory(application: Application): androidx.lifecycle.ViewModelProvider.Factory =
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                        return MainViewModel(application) as T
                    }
                    throw IllegalArgumentException("Unknown ViewModel class")
                }
            }
    }
}
