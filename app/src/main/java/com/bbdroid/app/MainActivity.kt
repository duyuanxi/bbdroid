package com.bbdroid.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp

object AppSnackbar { val host = SnackbarHostState() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by SettingsStore.settings(this).collectAsState(initial = AppSettings())
            BbdroidTheme(settings.themeMode, GlassConfig(settings.glassEnabled, settings.glassIntensity)) {
                MainScreen(this)
            }
        }
    }
}

private data class Destination(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)
private val destinations = listOf(
    Destination("下载", Icons.Outlined.Download, Icons.Filled.Download),
    Destination("工具箱", Icons.Outlined.Build, Icons.Filled.Build),
    Destination("历史", Icons.Outlined.History, Icons.Filled.History),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) { if (Build.VERSION.SDK_INT < 29) permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    LaunchedEffect(ReDownloadRequest.id) { if (ReDownloadRequest.id > 0) selectedTab = 0 }
    LaunchedEffect(ToolboxImport.id) { if (ToolboxImport.id > 0) selectedTab = 1 }

    GlassScene {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 700.dp
            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    NavigationRail(
                        modifier = Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp).then(glassMaterial()),
                        containerColor = if (LocalGlassEnabled.current) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerLow,
                        header = { Text("BB", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp)) },
                    ) {
                        Spacer(Modifier.height(24.dp))
                        destinations.forEachIndexed { index, destination ->
                            NavigationRailItem(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                icon = { Icon(if (selectedTab == index) destination.selectedIcon else destination.icon, null) },
                                label = { Text(destination.label) },
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text("BBDroid", style = MaterialTheme.typography.titleLarge)
                                    Text("视频下载与媒体工作台", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            actions = {
                                IconButton(onClick = { showSettings = true }, modifier = Modifier.padding(end = 12.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)) {
                                    Icon(Icons.Filled.Settings, "设置")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                        )
                    },
                    bottomBar = {
                        if (!wide) Box(Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            NavigationBar(
                                modifier = Modifier.clip(CircleShape).then(glassMaterial(CircleShape)),
                                containerColor = if (LocalGlassEnabled.current) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                tonalElevation = 0.dp,
                            ) {
                                destinations.forEachIndexed { index, destination ->
                                    NavigationBarItem(
                                        selected = selectedTab == index,
                                        onClick = { selectedTab = index },
                                        icon = { Icon(if (selectedTab == index) destination.selectedIcon else destination.icon, null) },
                                        label = { Text(destination.label) },
                                        colors = NavigationBarItemDefaults.colors(
                                            indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                            selectedIconColor = MaterialTheme.colorScheme.primary,
                                            selectedTextColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    )
                                }
                            }
                        }
                    },
                    snackbarHost = { SnackbarHost(AppSnackbar.host) },
                ) { padding ->
                    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
                    val contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        start = padding.calculateStartPadding(direction),
                        end = padding.calculateEndPadding(direction),
                    )
                    CompositionLocalProvider(LocalPageBottomInset provides padding.calculateBottomPadding()) {
                        Box(
                            Modifier.padding(contentPadding).consumeWindowInsets(contentPadding)
                                .imePadding().fillMaxSize().then(captureGlassContent()),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            Box(Modifier.widthIn(max = 960.dp).fillMaxSize()) {
                                // Keep inactive panes composed without measuring or placing them.
                                TabPane(selectedTab == 0) { DownloadTab(context, Modifier.fillMaxSize()) }
                                TabPane(selectedTab == 1) { ToolboxTab(context, Modifier.fillMaxSize()) }
                                TabPane(selectedTab == 2) { HistoryTab(context, Modifier.fillMaxSize()) { selectedTab = 0 } }
                            }
                        }
                    }
                }
            }
        }
        if (showSettings) ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            shape = DialogShape,
            containerColor = MaterialTheme.colorScheme.surface,
        ) { SettingsContent(context) { showSettings = false } }
    }
}

@Composable
private fun TabPane(active: Boolean, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        if (active) {
            val children = measurables.map { it.measure(constraints) }
            layout(constraints.maxWidth, constraints.maxHeight) { children.forEach { it.placeRelative(0, 0) } }
        } else layout(0, 0) { }
    }
}

@Composable
fun PageHeading(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
