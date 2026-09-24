package com.bbdroid.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 全局 Snackbar 宿主：各屏通过 scope.launch { AppSnackbar.host.showSnackbar(...) } 提示
object AppSnackbar {
    val host = SnackbarHostState()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = this
            val settings by SettingsStore.settings(ctx).collectAsState(initial = AppSettings())
            BbdroidTheme(
                mode = settings.themeMode,
                glass = GlassConfig(settings.glassEnabled, settings.glassIntensity),
            ) {
                MainScreen(context = ctx)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(context: Context) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val settings by SettingsStore.settings(context).collectAsState(initial = AppSettings())
    val glass = settings.glassEnabled

    // 启动时自动申请存储权限（仅 Android 9 及以下；Android 10+ 用 MediaStore 无需权限）
    val storagePermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < 29) {
            storagePermLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    // 历史记录触发重新下载时，切回下载页
    LaunchedEffect(ReDownloadRequest.id) {
        if (ReDownloadRequest.id > 0) selectedTab = 0
    }

    // 下载页导入视频到转码时，切到工具箱页
    LaunchedEffect(ToolboxImport.id) {
        if (ToolboxImport.id > 0) selectedTab = 1
    }

    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onSurface,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = BrandPink,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Box(Modifier.fillMaxSize()) {
        if (glass) GlassBackdrop(settings.glassIntensity)

        Scaffold(
            containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (selectedTab) {
                                0 -> "下载"
                                1 -> "工具箱"
                                else -> "历史"
                            }
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(AppSnackbar.host) },
            bottomBar = {
                NavigationBar(containerColor = if (glass) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(if (selectedTab == 0) Icons.Filled.Download else Icons.Outlined.Download, contentDescription = null) },
                        label = { Text("下载") },
                        colors = navColors,
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(if (selectedTab == 1) Icons.Filled.Build else Icons.Outlined.Build, contentDescription = null) },
                        label = { Text("工具箱") },
                        colors = navColors,
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(if (selectedTab == 2) Icons.Filled.History else Icons.Outlined.History, contentDescription = null) },
                        label = { Text("历史") },
                        colors = navColors,
                    )
                }
            }
        ) { padding ->
            val base = Modifier.padding(padding)
            // 三个 tab 始终保持在组合中（隐藏的置为 0 尺寸），下载状态与协程不丢失
            Box(Modifier.fillMaxSize()) {
                DownloadTab(context, base.then(if (selectedTab == 0) Modifier.fillMaxSize() else Modifier.size(0.dp)))
                ToolboxTab(context, base.then(if (selectedTab == 1) Modifier.fillMaxSize() else Modifier.size(0.dp)))
                HistoryTab(context, base.then(if (selectedTab == 2) Modifier.fillMaxSize() else Modifier.size(0.dp)), onGoDownload = { selectedTab = 0 })
            }
        }

        // 设置作为底部抽屉覆盖层
        if (showSettings) {
            ModalBottomSheet(
                onDismissRequest = { showSettings = false },
                shape = DialogShape,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                dragHandle = null,
            ) {
                SettingsContent(context = context, onBack = { showSettings = false })
            }
        }
    }
}
