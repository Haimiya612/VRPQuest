
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lex.vrpquest.Utils.MirrorConfig
import com.lex.vrpquest.Utils.MirrorConfigStore
import kotlinx.coroutines.launch

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

            listOf("Home", "Library", "Queue", "Settings").forEach {
                NavigationButton(
                    title = it,
                    selected = selected == it
                ) {
                    selected = it
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                "Version 0.1.0",
                color = Color.Gray,
                fontSize = 12.sp
            )
        }

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
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
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
                            "Browse the online game catalog."
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
                }

                "Queue" -> {
                    Text(
                        "No active downloads.",
                        color = Color.LightGray
                    )
                }

                "Settings" -> {
                    MirrorSettings()
                }
            }
        }
    }
}

@Composable
fun MirrorSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var configUrl by remember { mutableStateOf("") }
    var jsonInput by remember { mutableStateOf("") }

    var savedConfig by remember {
        mutableStateOf(
            runCatching {
                MirrorConfigStore.load(context)
            }.getOrNull()
        )
    }

    var status by remember {
        mutableStateOf(
            if (savedConfig != null) {
                "A mirror configuration is saved."
            } else {
                "No mirror configured."
            }
        )
    }

    var busy by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        Text(
            "Mirror Configuration",
            fontSize = 23.sp,
            color = Color.White
        )

        Text(
            "Connect your VRP-compatible public mirror.",
            color = Color.LightGray
        )

        HorizontalDivider(color = Color.DarkGray)

        Text(
            "Load configuration from URL",
            color = Accent,
            fontSize = 17.sp
        )

        OutlinedTextField(
            value = configUrl,
            onValueChange = { configUrl = it },
            label = { Text("Configuration URL") },
            placeholder = {
                Text("https://example.com/config.json")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            enabled = !busy && configUrl.isNotBlank(),
            onClick = {
                scope.launch {
                    busy = true
                    status = "Downloading configuration..."

                    try {
                        val config =
                            MirrorConfigStore.fetchFromUrl(configUrl)

                        MirrorConfigStore.save(context, config)
                        savedConfig = config
                        status = "Configuration downloaded and saved."
                    } catch (e: Exception) {
                        status = "Failed: ${e.message}"
                    }

                    busy = false
                }
            }
        ) {
            Text("Load URL and Save")
        }

        HorizontalDivider(color = Color.DarkGray)

        Text(
            "Or paste configuration JSON",
            color = Accent,
            fontSize = 17.sp
        )

        OutlinedTextField(
            value = jsonInput,
            onValueChange = { jsonInput = it },
            label = { Text("Mirror JSON") },
            placeholder = {
                Text(
                    """
                    {
                      "baseUri": "https://example.com/",
                      "password": "BASE64_PASSWORD"
                    }
                    """.trimIndent()
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                enabled = !busy && jsonInput.isNotBlank(),
                onClick = {
                    try {
                        MirrorConfigStore.parseJson(jsonInput)
                        status = "JSON is valid. Ready to save."
                    } catch (e: Exception) {
                        status = "Invalid JSON: ${e.message}"
                    }
                }
            ) {
                Text("Validate")
            }

            Button(
                enabled = !busy && jsonInput.isNotBlank(),
                onClick = {
                    try {
                        val config =
                            MirrorConfigStore.parseJson(jsonInput)

                        MirrorConfigStore.save(context, config)
                        savedConfig = config
                        status = "Mirror configuration saved securely."
                    } catch (e: Exception) {
                        status = "Save failed: ${e.message}"
                    }
                }
            ) {
                Text("Save Configuration")
            }
        }

        HorizontalDivider(color = Color.DarkGray)

        Text(
            "Connection status",
            color = Accent,
            fontSize = 17.sp
        )

        Text(
            status,
            color = Color.LightGray
        )

        if (savedConfig != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Panel
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Saved mirror",
                        color = Color.White,
                        fontSize = 18.sp
                    )

                    Text(
                        savedConfig!!.baseUri,
                        color = Color.LightGray
                    )

                    Text(
                        "Password: encrypted and stored",
                        color = Color.Gray
                    )

                    OutlinedButton(
                        onClick = {
                            try {
                                MirrorConfigStore.clear(context)
                                savedConfig = null
                                jsonInput = ""
                                configUrl = ""
                                status = "Mirror disconnected."
                            } catch (e: Exception) {
                                status = "Disconnect failed: ${e.message}"
                            }
                        }
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }

        if (busy) {
            CircularProgressIndicator()
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