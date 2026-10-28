package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AuthSession
import com.example.data.model.CompanyProfile
import com.example.data.model.UserProfile
import com.example.data.model.VehicleType
import com.example.viewmodel.AppSessionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("GeoTrack Enterprise", appName)
    }

    @Test
    fun `test AppSessionManager persistence and restore for UserSession`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val user = UserProfile(
            userId = "USER-7788",
            companyId = "CORP-LOGISTICS",
            email = "driver@express.com",
            fullName = "Budi Santoso",
            unitCode = "B-1234-XYZ",
            vehicleType = VehicleType.MOTORCYCLE,
            isSharingLocation = true,
            batteryLevel = 85
        )

        // Save session
        AppSessionManager.saveSession(context, AuthSession.UserSession(user))

        // Reset in-memory session to None to simulate app being killed by Android OS
        AppSessionManager.clear()
        assertEquals(AuthSession.None, AppSessionManager.currentSession)

        // Re-initialize from persistent storage (like on app relaunch / returning from Instagram or WhatsApp)
        val restored = AppSessionManager.init(context)
        assertTrue("Restored session must be UserSession", restored is AuthSession.UserSession)

        val restoredUser = (restored as AuthSession.UserSession).user
        assertEquals("USER-7788", restoredUser.userId)
        assertEquals("CORP-LOGISTICS", restoredUser.companyId)
        assertEquals("Budi Santoso", restoredUser.fullName)
        assertEquals("B-1234-XYZ", restoredUser.unitCode)
        assertEquals(VehicleType.MOTORCYCLE, restoredUser.vehicleType)
        assertTrue(restoredUser.isSharingLocation)

        // Clear session on explicit logout
        AppSessionManager.clearSession(context)
        val afterClear = AppSessionManager.init(context)
        assertEquals(AuthSession.None, afterClear)
    }

    @Test
    fun `test AppSessionManager persistence and restore for AdminSession`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val company = CompanyProfile(
            companyId = "CORP-DELTA",
            companyName = "PT Delta Fleet",
            adminEmail = "admin@deltafleet.com",
            sector = "Logistik & Transportasi"
        )

        AppSessionManager.saveSession(context, AuthSession.AdminSession(company))

        // Reset in-memory session to None
        AppSessionManager.clear()
        assertEquals(AuthSession.None, AppSessionManager.currentSession)

        // Reopen app
        val restored = AppSessionManager.init(context)
        assertTrue("Restored session must be AdminSession", restored is AuthSession.AdminSession)

        val restoredCompany = (restored as AuthSession.AdminSession).company
        assertEquals("CORP-DELTA", restoredCompany.companyId)
        assertEquals("PT Delta Fleet", restoredCompany.companyName)
        assertEquals("admin@deltafleet.com", restoredCompany.adminEmail)

        AppSessionManager.clearSession(context)
    }
}
