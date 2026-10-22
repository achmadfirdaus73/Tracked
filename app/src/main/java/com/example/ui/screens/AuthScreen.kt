package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GeoBluePrimary
import com.example.ui.theme.GeoCyanAccent
import com.example.ui.theme.GeoDarkNavy
import com.example.ui.theme.StatusGreen
import com.example.viewmodel.MainViewModel

@Composable
fun AuthScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var mainTab by remember { mutableIntStateOf(0) } // 0 = Masuk Karyawan, 1 = Portal Perusahaan
    var companySubTab by remember { mutableIntStateOf(0) } // 0 = Login Perusahaan, 1 = Daftar Perusahaan Baru

    // User Form state
    var userCompanyId by remember { mutableStateOf("") }
    var userEmail by remember { mutableStateOf("") }

    // Company Login state
    var adminIdentifier by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var showAdminPassword by remember { mutableStateOf(false) }

    // Company Register state
    var regCompanyName by remember { mutableStateOf("") }
    var regAdminEmail by remember { mutableStateOf("") }
    var regPassword by remember { mutableStateOf("") }
    var regCustomId by remember { mutableStateOf("") }
    var regSector by remember { mutableStateOf("Logistik & Pengiriman") }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GeoDarkNavy,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // Hero Logo & Title
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(GeoCyanAccent, GeoBluePrimary)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MyLocation,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(38.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "GeoTrack Enterprise",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            ),
            color = Color.White
        )
        Text(
            text = "Platform Pelacakan Real-Time & Geofencing Perusahaan",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF94A3B8)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Top Navigation Tabs
        TabRow(
            selectedTabIndex = mainTab,
            containerColor = Color(0xFF1E293B),
            contentColor = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
        ) {
            Tab(
                selected = mainTab == 0,
                onClick = { mainTab = 0 },
                text = {
                    Text(
                        "Masuk Karyawan",
                        fontWeight = if (mainTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                },
                icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("tab_user_login")
            )
            Tab(
                selected = mainTab == 1,
                onClick = { mainTab = 1 },
                text = {
                    Text(
                        "Portal Perusahaan",
                        fontWeight = if (mainTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                },
                icon = { Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(18.dp)) },
                modifier = Modifier.testTag("tab_company_portal")
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(22.dp)) {
                if (mainTab == 0) {
                    // USER LOGIN FORM
                    Text(
                        text = "Masuk Sebagai Petugas / Driver",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Bagikan lokasi Anda secara real-time kepada perusahaan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = userCompanyId,
                        onValueChange = { userCompanyId = it.uppercase() },
                        label = { Text("ID Perusahaan") },
                        placeholder = { Text("Contoh: CORP-1234") },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_user_company_id")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = userEmail,
                        onValueChange = { userEmail = it },
                        label = { Text("Email Karyawan Terdaftar") },
                        placeholder = { Text("nama@perusahaan.com") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_user_email")
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            viewModel.loginUser(userCompanyId, userEmail)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("btn_user_login_submit"),
                        colors = ButtonDefaults.buttonColors(containerColor = GeoBluePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Masuk & Aktifkan Sharelok", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }

                    } else {
                    // COMPANY PORTAL (Admin Login vs Register)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .padding(4.dp)
                    ) {
                        Button(
                            onClick = { companySubTab = 0 },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("subtab_company_login"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (companySubTab == 0) GeoBluePrimary else Color.Transparent,
                                contentColor = if (companySubTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = null,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Masuk Admin", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        Button(
                            onClick = { companySubTab = 1 },
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .testTag("subtab_company_register"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (companySubTab == 1) GeoBluePrimary else Color.Transparent,
                                contentColor = if (companySubTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = null,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Daftar Baru", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (companySubTab == 0) {
                        // COMPANY ADMIN LOGIN
                        Text(
                            text = "Login Dashboard Administrator",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = StatusGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Kredensial Aman & Terenkripsi SHA-256",
                                style = MaterialTheme.typography.labelSmall,
                                color = StatusGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = adminIdentifier,
                            onValueChange = { adminIdentifier = it },
                            label = { Text("ID Perusahaan atau Email Admin") },
                            placeholder = { Text("CORP-DEMO atau admin@nusantara.co.id") },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_admin_identifier")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = adminPassword,
                            onValueChange = { adminPassword = it },
                            label = { Text("Password Kredensial") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            trailingIcon = {
                                IconButton(onClick = { showAdminPassword = !showAdminPassword }) {
                                    Icon(
                                        imageVector = if (showAdminPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (showAdminPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_admin_password")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.loginCompany(adminIdentifier, adminPassword)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_admin_login_submit"),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoBluePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Buka Dashboard Perusahaan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }

                        } else {
                        // REGISTER COMPANY FORM
                        Text(
                            text = "Daftarkan Perusahaan Anda",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Buat akun platform pelacakan armada & tim lapangan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = regCompanyName,
                            onValueChange = { regCompanyName = it },
                            label = { Text("Nama Perusahaan / Organisasi") },
                            placeholder = { Text("PT Logistik Pintar Nusantara") },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_reg_company_name")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regAdminEmail,
                            onValueChange = { regAdminEmail = it },
                            label = { Text("Email Administrator Resmi") },
                            placeholder = { Text("admin@perusahaan.co.id") },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_reg_admin_email")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regPassword,
                            onValueChange = { regPassword = it },
                            label = { Text("Password Kredensial Baru") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_reg_password")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regCustomId,
                            onValueChange = { regCustomId = it.uppercase() },
                            label = { Text("ID Perusahaan Kustom (Opsional)") },
                            placeholder = { Text("Contoh: CORP-JAKARTA") },
                            leadingIcon = { Icon(Icons.Default.Pin, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_reg_custom_id")
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = regSector,
                            onValueChange = { regSector = it },
                            label = { Text("Sektor Bisnis") },
                            placeholder = { Text("Logistik, Ekspedisi, Sales, Konstruksi") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_reg_sector")
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.registerCompany(
                                    name = regCompanyName,
                                    email = regAdminEmail,
                                    password = regPassword,
                                    customId = if (regCustomId.isNotBlank()) regCustomId else null,
                                    sector = regSector
                                )
                            },
                            enabled = regCompanyName.isNotBlank() && regAdminEmail.isNotBlank() && regPassword.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_register_company_submit"),
                            colors = ButtonDefaults.buttonColors(containerColor = GeoBluePrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Daftarkan & Mulai Tracking", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}
