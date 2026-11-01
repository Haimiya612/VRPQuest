package com.lex.vrpquest

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lex.vrpquest.Utils.IsShizukuGranted
import com.lex.vrpquest.Utils.MirrorConfigStore
import com.lex.vrpquest.Utils.SettingGetBoolean
import com.lex.vrpquest.Utils.SettingStoreBoolean
import com.lex.vrpquest.Utils.UpdateShizukuState
import com.lex.vrpquest.Utils.checkPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import com.lex.vrpquest.CatalogLibraryPage
import com.lex.vrpquest.Managers.CatalogApp
import com.lex.vrpquest.Managers.CatalogRepository
import androidx.compose.foundation.lazy.items

private val Background = Color(0xFF0C0E13)
private val Sidebar = Color(0xFF11151D)
private val Panel = Color(0xFF171C26)
private val PanelLight = Color(0xFF202735)
private val Accent = Color(0xFF9B8CFF)
private val TextPrimary = Color(0xFFF2F3F8)
private val TextSecondary = Color(0xFF929BAE)
private val Success = Color(0xFF69D6A2)
private val Warning = Color(0xFFFFC66D)

private const val SHIZUKU_REQUEST_CODE = 4201

private const val PREF_ANIMATIONS = "rookie_animations_enabled"
private const val PREF_CONFIRM_INSTALL = "rookie_confirm_install"
private const val PREF_COMPACT_CARDS = "rookie_compact_cards"

private data class NavItem(
    val title: String,
    val icon: ImageVector
)

private val navigationItems = listOf(
    NavItem("Home", Icons.Default.Home),
    NavItem("Library", Icons.Default.GridView),
    NavItem("Queue", Icons.Default.Download),
    NavItem("Installed", Icons.Default.Apps),
    NavItem("Tools", Icons.Default.Build),
    NavItem("Settings", Icons.Default.Settings)
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Background,
                    background = Background,
                    surface = Panel,
                    surfaceVariant = PanelLight,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    RookieDashboard(
                        onInstallApk = { uri ->
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(
                                        uri,
                                        "application/vnd.android.package-archive"
                                    )
                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
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
}

@Composable
fun RookieDashboard(
    onInstallApk: (android.net.Uri) -> Unit
) {
    var selected by remember { mutableStateOf("Home") }
    var search by remember { mutableStateOf("") }
    var shizukuRefresh by remember { mutableIntStateOf(0) }

    UpdateShizukuState {
        shizukuRefresh++
    }

    val shizukuRunning = remember(shizukuRefresh) {
        runCatching { Shizuku.pingBinder() }.getOrDefault(false)
    }

    val shizukuGranted = remember(shizukuRefresh) {
        runCatching { IsShizukuGranted() }.getOrDefault(false)
    }

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
        // SIDEBAR
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Sidebar)
                .padding(horizontal = 15.dp, vertical = 22.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(Accent, RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = Background,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        "ROOKIE",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "QUEST EDITION",
                        fontSize = 9.sp,
                        color = TextSecondary,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            Spacer(Modifier.height(35.dp))

            Text(
                "WORKSPACE",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(start = 12.dp, bottom = 12.dp)
            )

            navigationItems.forEach { item ->
                SidebarItem(
                    item = item,
                    selected = selected == item.title,
                    onClick = { selected = item.title }
                )
            }

            Spacer(Modifier.weight(1f))

            HorizontalDivider(color = Color.White.copy(alpha = 0.07f))

            Spacer(Modifier.height(14.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                modifier = Modifier.padding(horizontal = 9.dp)
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(
                            if (shizukuRunning && shizukuGranted) Success
                            else Warning,
                            RoundedCornerShape(50)
                        )
                )

                Column {
                    Text(
                        "Shizuku",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        when {
                            shizukuRunning && shizukuGranted -> "Connected"
                            shizukuRunning -> "Permission needed"
                            else -> "Not running"
                        },
                        color = TextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(Modifier.height(15.dp))

            Text(
                "VRP QUEST  •  0.3.0",
                color = TextSecondary.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 9.dp)
            )
        }

        // MAIN CONTENT
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp, vertical = 23.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        selected.uppercase(),
                        color = TextPrimary,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    Text(
                        when (selected) {
                            "Home" -> "Your Quest management workspace."
                            "Library" -> "Browse your available applications."
                            "Queue" -> "Manage your downloads."
                            "Installed" -> "Manage applications on your headset."
                            "Tools" -> "Quest utilities and device management."
                            "Settings" -> "Configure Rookie and device access."
                            else -> "Manage your Quest applications."
                        },
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 13.dp,
                            vertical = 9.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Icon(
                            Icons.Default.Headset,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(17.dp)
                        )

                        Text(
                            "Quest 3",
                            color = TextPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            AnimatedContent(
                targetState = selected,
                transitionSpec = {
                    (
                        fadeIn(tween(180)) +
                            slideInHorizontally(
                                animationSpec = tween(220),
                                initialOffsetX = { it / 18 }
                            )
                    ).togetherWith(
                        fadeOut(tween(120)) +
                            slideOutHorizontally(
                                animationSpec = tween(180),
                                targetOffsetX = { -it / 22 }
                            )
                    ).using(SizeTransform(clip = false))
                },
                label = "page-transition"
            ) { page ->
                when (page) {
                    "Home" -> HomePage(
                        onInstall = {
                            apkPicker.launch(
                                arrayOf(
                                    "application/vnd.android.package-archive",
                                    "application/octet-stream"
                                )
                            )
                        },
                        onLibrary = { selected = "Library" },
                        onSettings = { selected = "Settings" },
                        shizukuRunning = shizukuRunning,
                        shizukuGranted = shizukuGranted
                    )

                    "Library" -> LibraryPage(
                        search = search,
                        onSearchChange = { search = it }
                    )

                    "Queue" -> PlaceholderPage(
                        icon = Icons.Default.Download,
                        title = "Download queue",
                        description = "Your active and completed downloads will appear here."
                    )

                    "Installed" -> PlaceholderPage(
                        icon = Icons.Default.Apps,
                        title = "Installed applications",
                        description = "Your installed Quest applications will appear here."
                    )

                    "Tools" -> PlaceholderPage(
                        icon = Icons.Default.Build,
                        title = "Quest tools",
                        description = "Useful device utilities and management tools."
                    )

                    "Settings" -> SettingsPage(
                        shizukuRunning = shizukuRunning,
                        shizukuGranted = shizukuGranted,
                        onShizukuPermission = {
                            checkPermission(SHIZUKU_REQUEST_CODE)
                        },
                        onRefreshShizuku = {
                            shizukuRefresh++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SidebarItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val indicatorWidth by animateFloatAsState(
        targetValue = if (selected) 4f else 0f,
        animationSpec = tween(180),
        label = "selection-indicator"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (selected) Accent.copy(alpha = 0.13f)
                else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .height(46.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth.dp)
                .fillMaxHeight()
                .background(Accent, RoundedCornerShape(5.dp))
        )

        Spacer(Modifier.width(11.dp))

        Icon(
            item.icon,
            contentDescription = null,
            tint = if (selected) Accent else TextSecondary,
            modifier = Modifier.size(19.dp)
        )

        Spacer(Modifier.width(12.dp))

        Text(
            item.title,
            color = if (selected) TextPrimary else TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun HomePage(
    onInstall: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    shizukuRunning: Boolean,
    shizukuGranted: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(25.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Text(
                        "Welcome to Rookie.",
                        color = TextPrimary,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        "Your Quest 3 management workspace.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(4.dp))

                    Button(
                        onClick = onLibrary,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Accent
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Explore library", color = Background)
                        Spacer(Modifier.width(7.dp))
                        Icon(
                            Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = Background,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Icon(
                    Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = Accent.copy(alpha = 0.8f),
                    modifier = Modifier.size(85.dp)
                )
            }
        }

        SectionLabel("QUICK ACTIONS")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            ActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.InstallMobile,
                title = "Local installation",
                description = "Choose an APK file from your headset.",
                actionLabel = "Choose APK",
                onClick = onInstall
            )

            ActionCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.GridView,
                title = "Online library",
                description = "Browse available catalog entries.",
                actionLabel = "Open library",
                onClick = onLibrary
            )
        }

        SectionLabel("DEVICE STATUS")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            StatusCard(
                modifier = Modifier.weight(1f),
                title = "Headset",
                value = "Quest 3",
                detail = "Rookie is running",
                icon = Icons.Default.Headset,
                positive = true
            )

            StatusCard(
                modifier = Modifier.weight(1f),
                title = "Shizuku",
                value = when {
                    shizukuRunning && shizukuGranted -> "Connected"
                    shizukuRunning -> "Permission required"
                    else -> "Offline"
                },
                detail = when {
                    shizukuRunning && shizukuGranted -> "Privileged access available"
                    shizukuRunning -> "Grant Rookie permission"
                    else -> "Start Shizuku to connect"
                },
                icon = Icons.Default.Security,
                positive = shizukuRunning && shizukuGranted
            )

            StatusCard(
                modifier = Modifier.weight(1f),
                title = "Mirror",
                value = "Configuration",
                detail = "Manage in settings",
                icon = Icons.Default.Cloud,
                positive = true
            )
        }

        TextButton(onClick = onSettings) {
            Text("Open settings", color = Accent)
            Spacer(Modifier.width(5.dp))
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = TextSecondary,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun ActionCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(110),
        label = "card-scale"
    )

    Card(
        modifier = modifier
            .height(190.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = Panel)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(19.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(27.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title,
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    description,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                actionLabel.uppercase(),
                color = Accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun StatusCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    detail: String,
    icon: ImageVector,
    positive: Boolean
) {
    Surface(
        modifier = modifier.height(112.dp),
        color = Panel,
        shape = RoundedCornerShape(15.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(title, color = TextSecondary, fontSize = 11.sp)
            }

            Text(
                value,
                color = if (positive) Success else Warning,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                detail,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }
    }
}


@Composable
private fun LibraryPage(
    search: String,
    onSearchChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { CatalogRepository() }

    var catalogUrl by remember {
        mutableStateOf(
            context.getSharedPreferences(
                "rookie_catalog",
                android.content.Context.MODE_PRIVATE
            ).getString("catalog_url", "") ?: ""
        )
    }

    var apps by remember {
        mutableStateOf<List<CatalogApp>>(emptyList())
    }

    var loading by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun loadCatalog() {
        val requestedUrl = catalogUrl.trim()

        if (requestedUrl.isBlank()) {
            error = "Enter your catalog HTTPS address first."
            return
        }

        scope.launch {
            loading = true
            error = null

            try {
                val result = withContext(Dispatchers.IO) {
                    repository.fetchCatalog(requestedUrl)
                }

                context.getSharedPreferences(
                    "rookie_catalog",
                    android.content.Context.MODE_PRIVATE
                ).edit()
                    .putString("catalog_url", requestedUrl)
                    .apply()

                apps = result
                loaded = true
            } catch (e: Exception) {
                error = e.message ?: "Unable to load catalog."
            } finally {
                loading = false
            }
        }
    }

    val filteredApps = remember(apps, search) {
        apps.filter {
            it.title.contains(search, ignoreCase = true) ||
            it.packageName.contains(search, ignoreCase = true) ||
            it.description.contains(search, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(0.65f),
            singleLine = true,
            placeholder = { Text("Search applications...") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = PanelLight
            )
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.Cloud,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(24.dp)
                    )

                    Text(
                        "Online catalog",
                        color = TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    "Connect an authorized JSON catalog to browse its applications.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                OutlinedTextField(
                    value = catalogUrl,
                    onValueChange = { catalogUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Catalog HTTPS URL") },
                    placeholder = {
                        Text("https://example.com/catalog.json")
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                Button(
                    onClick = { loadCatalog() },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = PanelLight
                    )
                ) {
                    Text(
                        if (loading) "Connecting..."
                        else if (loaded) "Refresh catalog"
                        else "Connect catalog",
                        color = TextPrimary
                    )
                }

                if (loading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent
                    )
                }

                error?.let {
                    Text(
                        it,
                        color = Warning,
                        fontSize = 11.sp
                    )
                }

                if (loaded && error == null) {
                    Text(
                        "${apps.size} applications loaded",
                        color = Success,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (loaded && filteredApps.isEmpty() && !loading) {
            Text(
                if (apps.isEmpty()) "The catalog is empty."
                else "No matching applications found.",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
    items = filteredApps,
    key = { it.id }
) { app ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Panel,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            app.title,
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Text(
                            app.packageName,
                            color = Accent,
                            fontSize = 11.sp
                        )

                        Text(
                            "Version ${app.versionName}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )

                        if (app.description.isNotBlank()) {
                            Text(
                                app.description,
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderPage(
    icon: ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .background(Panel, RoundedCornerShape(18.dp))
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(38.dp)
            )

            Text(
                title,
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                description,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SettingsPage(
    shizukuRunning: Boolean,
    shizukuGranted: Boolean,
    onShizukuPermission: () -> Unit,
    onRefreshShizuku: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var expanded by remember { mutableStateOf("Connection") }

    var animationsEnabled by remember { mutableStateOf(true) }
    var confirmInstall by remember { mutableStateOf(true) }
    var compactCards by remember { mutableStateOf(false) }
    var preferencesLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        val preferences = withContext(Dispatchers.IO) {
            Triple(
                SettingGetBoolean(context, PREF_ANIMATIONS),
                SettingGetBoolean(context, PREF_CONFIRM_INSTALL),
                SettingGetBoolean(context, PREF_COMPACT_CARDS)
            )
        }

        animationsEnabled = preferences.first ?: true
        confirmInstall = preferences.second ?: true
        compactCards = preferences.third ?: false
        preferencesLoaded = true
    }

    fun savePreference(key: String, value: Boolean) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                SettingStoreBoolean(context, key, value)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        SettingsCategory(
            title = "Connection",
            subtitle = "Shizuku and device access",
            icon = Icons.Default.Security,
            expanded = expanded == "Connection",
            onClick = {
                expanded =
                    if (expanded == "Connection") "" else "Connection"
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Shizuku status",
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        when {
                            shizukuRunning && shizukuGranted ->
                                "Connected and authorized"

                            shizukuRunning ->
                                "Running, permission not granted"

                            else ->
                                "Shizuku is not running"
                        },
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Box(
                    Modifier
                        .size(9.dp)
                        .background(
                            if (shizukuRunning && shizukuGranted) Success
                            else Warning,
                            RoundedCornerShape(50)
                        )
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Button(
                    onClick = onShizukuPermission,
                    enabled = shizukuRunning && !shizukuGranted,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        disabledContainerColor = PanelLight
                    )
                ) {
                    Text(
                        if (shizukuGranted) "Authorized"
                        else "Grant permission",
                        color = TextPrimary
                    )
                }

                OutlinedButton(onClick = onRefreshShizuku) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Refresh")
                }
            }

            if (!shizukuRunning) {
                Text(
                    "Start Shizuku on your Quest 3 first. Rookie will detect it automatically.",
                    color = Warning,
                    fontSize = 11.sp
                )
            } else if (shizukuGranted) {
                Text(
                    "Rookie has Shizuku permission. Features requiring elevated access can now use it.",
                    color = Success,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    "Grant Rookie permission in the Shizuku authorization prompt.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }

        SettingsCategory(
            title = "Mirror configuration",
            subtitle = "Manage saved connection settings",
            icon = Icons.Default.Cloud,
            expanded = expanded == "Mirror",
            onClick = {
                expanded = if (expanded == "Mirror") "" else "Mirror"
            }
        ) {
            MirrorSettings()
        }

        SettingsCategory(
            title = "Application",
            subtitle = "Rookie interface and behavior",
            icon = Icons.Default.Tune,
            expanded = expanded == "Application",
            onClick = {
                expanded =
                    if (expanded == "Application") "" else "Application"
            }
        ) {
            if (!preferencesLoaded) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Accent
                )
            } else {
                PreferenceSwitch(
                    title = "Interface animations",
                    description = "Enable animated transitions and visual feedback.",
                    checked = animationsEnabled,
                    onCheckedChange = {
                        animationsEnabled = it
                        savePreference(PREF_ANIMATIONS, it)
                    }
                )

                HorizontalDivider(color = PanelLight)

                PreferenceSwitch(
                    title = "Confirm before installation",
                    description = "Ask before opening the Android package installer.",
                    checked = confirmInstall,
                    onCheckedChange = {
                        confirmInstall = it
                        savePreference(PREF_CONFIRM_INSTALL, it)
                    }
                )

                HorizontalDivider(color = PanelLight)

                PreferenceSwitch(
                    title = "Compact cards",
                    description = "Use a denser layout where supported.",
                    checked = compactCards,
                    onCheckedChange = {
                        compactCards = it
                        savePreference(PREF_COMPACT_CARDS, it)
                    }
                )
            }

            Text(
                "Preferences are saved on this device.",
                color = TextSecondary,
                fontSize = 10.sp
            )
        }

        SettingsCategory(
            title = "Storage",
            subtitle = "Local files and installation",
            icon = Icons.Default.Folder,
            expanded = expanded == "Storage",
            onClick = {
                expanded = if (expanded == "Storage") "" else "Storage"
            }
        ) {
            Text(
                "APK files are selected through Android's document picker. Rookie does not need broad storage access to install a file you choose.",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(17.dp)
                )
                Text(
                    "Local APK installation is available",
                    color = Success,
                    fontSize = 11.sp
                )
            }
        }

        SettingsCategory(
            title = "About",
            subtitle = "Version and project information",
            icon = Icons.Default.Info,
            expanded = expanded == "About",
            onClick = {
                expanded = if (expanded == "About") "" else "About"
            }
        ) {
            Text(
                "Rookie Quest",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                "Version 0.3.0",
                color = TextSecondary,
                fontSize = 12.sp
            )

            Text(
                "Built for Meta Quest.",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun PreferenceSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                description,
                color = TextSecondary,
                fontSize = 10.sp
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Background,
                checkedTrackColor = Accent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = PanelLight
            )
        )
    }
}

@Composable
private fun SettingsCategory(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Panel,
        shape = RoundedCornerShape(15.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .padding(17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(Modifier.width(13.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )

                    Text(
                        subtitle,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Icon(
                    if (expanded) Icons.Default.ExpandLess
                    else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(160)) +
                    expandVertically(tween(200)),
                exit = fadeOut(tween(100)) +
                    shrinkVertically(tween(160))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 17.dp,
                            end = 17.dp,
                            bottom = 17.dp
                        ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
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
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Load configuration from URL",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )

        OutlinedTextField(
            value = configUrl,
            onValueChange = { configUrl = it },
            label = { Text("Configuration URL") },
            placeholder = { Text("https://example.com/config.json") },
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
                        val config = withContext(Dispatchers.IO) {
                            MirrorConfigStore.fetchFromUrl(configUrl)
                        }

                        withContext(Dispatchers.IO) {
                            MirrorConfigStore.save(context, config)
                        }

                        savedConfig = config
                        status = "Configuration downloaded and saved."
                    } catch (e: Exception) {
                        status = "Failed: ${e.message}"
                    } finally {
                        busy = false
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Accent
            )
        ) {
            Text("Load URL and save", color = Background)
        }

        HorizontalDivider(color = PanelLight)

        Text(
            "Or paste configuration JSON",
            color = Accent,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
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
                .height(145.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp)
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
                    scope.launch {
                        busy = true

                        try {
                            val config =
                                MirrorConfigStore.parseJson(jsonInput)

                            withContext(Dispatchers.IO) {
                                MirrorConfigStore.save(context, config)
                            }

                            savedConfig = config
                            status = "Configuration saved securely."
                        } catch (e: Exception) {
                            status = "Save failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent
                )
            ) {
                Text("Save configuration", color = Background)
            }
        }

        Text(
            status,
            color = TextSecondary,
            fontSize = 12.sp
        )

        if (savedConfig != null) {
            Surface(
                color = PanelLight,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        "Saved mirror",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        savedConfig!!.baseUri,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Text(
                        "Password: encrypted and stored",
                        color = Success,
                        fontSize = 11.sp
                    )

                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            scope.launch {
                                busy = true

                                try {
                                    withContext(Dispatchers.IO) {
                                        MirrorConfigStore.clear(context)
                                    }

                                    savedConfig = null
                                    jsonInput = ""
                                    configUrl = ""
                                    status = "Mirror disconnected."
                                } catch (e: Exception) {
                                    status = "Disconnect failed: ${e.message}"
                                } finally {
                                    busy = false
                                }
                            }
                        }
                    ) {
                        Text("Disconnect")
                    }
                }
            }
        }

        if (busy) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Accent
            )
        }
    }
}