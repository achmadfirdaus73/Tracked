package com.example

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AuthSession
import com.example.ui.components.PipTrackerView
import com.example.ui.screens.AdminDashboardScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.UserTrackerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppSessionManager
import com.example.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val _isInPipMode = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.util.FirebaseInitializer.init(this)
        AppSessionManager.init(this)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainApp(
                    isInPipMode = _isInPipMode.value,
                    onEnterPip = { enterPipWindow() }
                )
            }
        }
    }

    /**
     * Enter Picture-in-Picture floating mini-window (like Google Maps navigation).
     */
    fun enterPipWindow() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                try {
                    enterPictureInPictureMode()
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Automatically enter PiP mode when user navigates away (switches to WhatsApp, Instagram, or presses Home).
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val session = AppSessionManager.currentSession
        if (session is AuthSession.UserSession) {
            enterPipWindow()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
    }
}

@Composable
fun MainApp(
    isInPipMode: Boolean,
    onEnterPip: () -> Unit,
    viewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(
            LocalContext.current.applicationContext as android.app.Application
        )
    )
) {
    val context = LocalContext.current
    val session by viewModel.session.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val currentPt by viewModel.currentPoint.collectAsState()
    val historyPoints by viewModel.targetUserHistory.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Runtime Permission Launcher for GPS and Notifications
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fineGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!fineGranted) {
            Toast.makeText(
                context,
                "Akses GPS diperlukan untuk pelacakan rute dengan akurasi tinggi. Mode simulasi tetap aktif.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    // Handle feedback messages when not in PiP
    LaunchedEffect(errorMessage) {
        if (!isInPipMode) {
            errorMessage?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessages()
            }
        }
    }
    LaunchedEffect(successMessage) {
        if (!isInPipMode) {
            successMessage?.let {
                snackbarHostState.showSnackbar(it)
                viewModel.clearMessages()
            }
        }
    }

    // If currently in Picture-in-Picture floating window mode:
    if (isInPipMode) {
        when (val s = session) {
            is AuthSession.UserSession -> {
                PipTrackerView(
                    user = s.user,
                    currentPoint = currentPt,
                    routePoints = historyPoints,
                    batteryStatus = viewModel.getDeviceBatteryStatus()
                )
            }
            else -> {
                // If not user session, show minimal fallback
                Box(modifier = Modifier.fillMaxSize())
            }
        }
        return
    }

    // Standard Fullscreen App Layout
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (val s = session) {
                is AuthSession.None -> {
                    AuthScreen(viewModel = viewModel)
                }
                is AuthSession.UserSession -> {
                    // Back button moves the app to background without logging out
                    BackHandler {
                        (context as? Activity)?.moveTaskToBack(true)
                    }
                    UserTrackerScreen(
                        user = s.user,
                        viewModel = viewModel,
                        onEnterPip = onEnterPip
                    )
                }
                is AuthSession.AdminSession -> {
                    // Back button moves the app to background without logging out
                    BackHandler {
                        (context as? Activity)?.moveTaskToBack(true)
                    }
                    AdminDashboardScreen(company = s.company, viewModel = viewModel)
                }
            }
        }
    }
}
