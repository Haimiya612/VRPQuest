package com.lex.vrpquest

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lex.vrpquest.Utils.ShizukuShell
import kotlinx.coroutines.launch
import java.util.Locale
import android.content.pm.ApplicationInfo
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

private data class DeviceSnapshot(
    val model: String,
    val androidVersion: String,
    val batteryPercent: Int,
    val batteryHealth: String,
    val connection: String,
    val storageTotal: Long,
    val storageUsed: Long,
    val storageFree: Long
)

private data class InstalledPackage(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean
)

@Composable
fun ToolsPage() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var snapshot by remember { mutableStateOf<DeviceSnapshot?>(null) }
    var packages by remember {
        mutableStateOf<List<InstalledPackage>>(emptyList())
    }

    var loading by remember { mutableStateOf(false) }
    var diagnosticRunning by remember { mutableStateOf(false) }
    var diagnosticOutput by remember { mutableStateOf("No diagnostic has been run.") }
    var refreshKey by remember { mutableIntStateOf(0) }

    val shizukuAvailable = remember(refreshKey) {
        ShizukuShell.isAvailable()
    }

    LaunchedEffect(refreshKey) {
        loading = true

        try {
            snapshot = readDeviceSnapshot(context)
            packages = readInstalledPackages(context)
        } finally {
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Device utilities",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = { refreshKey++ },
                enabled = !loading
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Accent
                )
            }
        }

        if (loading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Accent
            )
        }

        // DEVICE INFORMATION
        ToolSection(
            title = "Device information",
            subtitle = "Quest hardware and current status",
            icon = Icons.Default.Headset
        ) {
            snapshot?.let { device ->
                InfoRow("Model", device.model)
                InfoRow("Android", device.androidVersion)
                InfoRow("Battery", "${device.batteryPercent}%")
                InfoRow("Battery health", device.batteryHealth)
                InfoRow("Connection", device.connection)
            } ?: Text(
                "Reading device information...",
                color = TextSecondary
            )
        }

        // STORAGE MANAGER
        ToolSection(
            title = "Storage manager",
            subtitle = "Internal device storage usage",
            icon = Icons.Default.Storage
        ) {
            snapshot?.let { device ->
                val usedRatio =
                    if (device.storageTotal > 0L) {
                        device.storageUsed.toFloat() /
                            device.storageTotal.toFloat()
                    } else 0f

                Text(
                    "${formatBytes(device.storageUsed)} used of " +
                        formatBytes(device.storageTotal),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold
                )

                LinearProgressIndicator(
                    progress = { usedRatio.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    color = Accent,
                    trackColor = PanelLight
                )

                InfoRow("Used", formatBytes(device.storageUsed))
                InfoRow("Available", formatBytes(device.storageFree))
                InfoRow("Total", formatBytes(device.storageTotal))
            }
        }

        // PACKAGE MANAGER
        ToolSection(
            title = "Package manager",
            subtitle = "${packages.size} installed packages detected",
            icon = Icons.Default.Apps
        ) {
            if (packages.isEmpty()) {
                Text(
                    "No installed packages found.",
                    color = TextSecondary
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(
                        items = packages,
                        key = { it.packageName }
                    ) { app ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = PanelLight,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(11.dp),
                                verticalArrangement =
                                    Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    app.packageName,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    "Version ${app.versionName} " +
                                        "(${app.versionCode})",
                                    color = TextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // SHIZUKU / ADB DIAGNOSTICS
        ToolSection(
            title = "ADB / Shizuku diagnostics",
            subtitle = "Connection and read-only system checks",
            icon = Icons.Default.Terminal
        ) {
            InfoRow(
                "Shizuku",
                if (shizukuAvailable) "Connected and authorized"
                else "Unavailable or not authorized"
            )

            Button(
                onClick = {
                    val requested = ShizukuShell.requestPermission(4201)

                    diagnosticOutput = if (requested) {
                        "Shizuku permission is already granted."
                    } else {
                        "Permission request sent, or Shizuku is unavailable. Refresh after responding."
                    }

                    refreshKey++
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent
                )
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null
                )
                Spacer(Modifier.width(7.dp))
                Text("Check / request permission")
            }

            Button(
                onClick = {
                    scope.launch {
                        diagnosticRunning = true
                        diagnosticOutput = "Running diagnostics..."

                        try {
                            val result = ShizukuShell.execute(
                                """
                                id
                                getprop ro.build.version.release
                                getprop ro.product.model
                                df -h /data
                                """.trimIndent()
                            )

                            diagnosticOutput =
                                "Exit code: ${result.exitCode}\n\n" +
                                    result.output
                        } catch (e: Exception) {
                            diagnosticOutput =
                                "Diagnostic failed: ${e.message}"
                        } finally {
                            diagnosticRunning = false
                            refreshKey++
                        }
                    }
                },
                enabled = shizukuAvailable && !diagnosticRunning,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PanelLight
                )
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    if (diagnosticRunning) "Testing..."
                    else "Run connection diagnostics"
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    diagnosticOutput,
                    modifier = Modifier.padding(12.dp),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ToolSection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel,
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier.padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Accent
                )

                Column {
                    Text(
                        title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    Text(
                        subtitle,
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            HorizontalDivider(color = PanelLight)

            content()
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextSecondary, fontSize = 12.sp)
        Text(
            value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun readDeviceSnapshot(
    context: Context
): DeviceSnapshot {
    val batteryIntent = context.registerReceiver(
        null,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED)
    )

    val level = batteryIntent?.getIntExtra(
        BatteryManager.EXTRA_LEVEL, -1
    ) ?: -1

    val scale = batteryIntent?.getIntExtra(
        BatteryManager.EXTRA_SCALE, -1
    ) ?: -1

    val percentage = if (level >= 0 && scale > 0) {
        (level * 100) / scale
    } else 0

    val healthValue = batteryIntent?.getIntExtra(
        BatteryManager.EXTRA_HEALTH,
        BatteryManager.BATTERY_HEALTH_UNKNOWN
    ) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN

    val health = when (healthValue) {
        BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
        BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
        BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
        BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
        BatteryManager.BATTERY_HEALTH_COLD -> "Too cold"
        BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE ->
            "Unspecified failure"
        else -> "Unknown"
    }

    val connectivity = context.getSystemService(
        Context.CONNECTIVITY_SERVICE
    ) as ConnectivityManager

    val network = connectivity.activeNetwork
    val capabilities = network?.let {
        connectivity.getNetworkCapabilities(it)
    }

    val connection = when {
        capabilities == null -> "Disconnected"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
            "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
            "Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
            "Mobile data"
        else -> "Connected"
    }

    val stat = StatFs(Environment.getDataDirectory().path)
    val total = stat.totalBytes
    val free = stat.availableBytes

    return DeviceSnapshot(
        model = "${Build.MANUFACTURER} ${Build.MODEL}",
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        batteryPercent = percentage,
        batteryHealth = health,
        connection = connection,
        storageTotal = total,
        storageUsed = (total - free).coerceAtLeast(0L),
        storageFree = free
    )
}

@Suppress("DEPRECATION")
private fun readInstalledPackages(
    context: Context
): List<InstalledPackage> {
    val manager = context.packageManager

    return manager.getInstalledPackages(0)
        .map { info ->
            InstalledPackage(
                packageName = info.packageName,
                versionName = info.versionName ?: "Unknown",
                versionCode = if (Build.VERSION.SDK_INT >= 28) {
                    info.longVersionCode
                } else {
                    info.versionCode.toLong()
                }
            )
        }
        .sortedBy { it.packageName.lowercase(Locale.ROOT) }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0

    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }

    return String.format(Locale.getDefault(), "%.2f %s", value, units[index])
}
