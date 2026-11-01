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
