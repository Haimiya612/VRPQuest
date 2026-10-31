package com.lex.vrpquest

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF101319)
private val Panel = Color(0xFF1B222D)
private val Accent = Color(0xFF8B9EFF)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    background = Background,
                    surface = Panel
                )
            ) {
                RookieDashboard(
                    onInstallApk = { uri ->
                        try {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(
                                    uri,
                                    "application/vnd.android.package-archive"
                                )
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(
                                this,
                                "Unable to open APK installer: ${e.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun RookieDashboard(
    onInstallApk: (android.net.Uri) -> Unit
) {
    var selected by remember { mutableStateOf("Home") }
    var search by remember { mutableStateOf("") }

    val apkPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onInstallApk)
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {

        // Sidebar
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Panel)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "ROOKIE",
                fontSize = 27.sp,
                color = Accent
            )

            Text(
                "QUEST EDITION",
                fontSize = 11.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(25.dp))

            listOf(
                "Home",
                "Library",
                "Queue",
                "Settings"
            ).forEach { item ->

                NavigationButton(
                    title = item,
                    selected = selected == item
                ) {
                    selected = item
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Version 0.1.0",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp)
        ) {

            Text(
                selected,
                fontSize = 32.sp,
                color = Color.White
            )

            Spacer(Modifier.height(12.dp))

            when (selected) {

                "Home" -> {
                    Text(
                        "Welcome to Rookie Quest.",
                        color = Color.LightGray
                    )

                    Spacer(Modifier.height(24.dp))

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(16.dp)
                    ) {
                        DashboardCard(
                            "Local installation",
                            "Install an APK stored on your headset."
                        ) {
                            apkPicker.launch(
                                arrayOf(
                                    "application/vnd.android.package-archive",
                                    "application/octet-stream"
                                )
                            )
                        }

                        DashboardCard(
                            "Online library",
                            "Online catalog integration is coming."
                        ) {
                            selected = "Library"
                        }
                    }
                }

                "Library" -> {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Search library") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.7f)
                    )

                    Spacer(Modifier.height(24.dp))

                    Text(
                        "No catalog connected yet.",
                        color = Color.LightGray
                    )

                    Text(
                        "The online catalog engine will be added separately.",
                        color = Color.Gray
                    )
                }

                "Queue" -> {
                    Text(
                        "No active downloads.",
                        color = Color.LightGray
                    )
                }

                "Settings" -> {
                    Text(
                        "Rookie Quest settings",
                        color = Color.LightGray
                    )

                    Spacer(Modifier.height(16.dp))

                    Text("Installation: Android Package Installer")
                    Text("Target device: Meta Quest 3")
                    Text("Interface: Standalone VR")
                }
            }
        }
    }
}

@Composable
fun NavigationButton(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = {},
        label = { Text(title) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun DashboardCard(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(
            width = 280.dp,
            height = 170.dp
        ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Panel
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 19.sp
            )

            Text(
                description,
                color = Color.LightGray,
                fontSize = 13.sp
            )
        }
    }
}