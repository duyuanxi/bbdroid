package com.bbdroid.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ctx = this
            var themeMode by remember { mutableStateOf(ThemePrefs.get(ctx)) }
            var glassIntensity by remember { mutableStateOf(ThemePrefs.getGlassIntensity(ctx)) }
            BbdroidTheme(mode = themeMode, glassIntensity = glassIntensity) {
                MainScreen(
                    context = ctx,
                    themeMode = themeMode,
                    glassIntensity = glassIntensity,
                    onThemeChange = { themeMode = it },
                    onGlassIntensityChange = { glassIntensity = it },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    context: android.content.Context,
    themeMode: ThemeMode,
    glassIntensity: Float,
    onThemeChange: (ThemeMode) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    // 启动时自动申请存储权限（仅 Android 9 及以下需要；Android 10+ 用 MediaStore 无需权限）
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

    val isGlass = themeMode == ThemeMode.GLASS
    val barColor = if (isGlass) Color(0x2AFFFFFF) else MaterialTheme.colorScheme.surface
    val settingsBg = if (isGlass) Color(0xFF241A5E) else MaterialTheme.colorScheme.background

    Box(Modifier.fillMaxSize()) {
        if (isGlass) GlassBackdrop(glassIntensity)

        Scaffold(
            containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(if (selectedTab == 0) "下载" else if (selectedTab == 1) "工具箱" else "历史") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isGlass) Color.Transparent else MaterialTheme.colorScheme.surface),
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar(containerColor = barColor) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(if (selectedTab == 0) Icons.Filled.Download else Icons.Outlined.Download, contentDescription = null) },
                        label = { Text("下载") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(if (selectedTab == 1) Icons.Filled.Build else Icons.Outlined.Build, contentDescription = null) },
                        label = { Text("工具箱") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(if (selectedTab == 2) Icons.Filled.History else Icons.Outlined.History, contentDescription = null) },
                        label = { Text("历史") }
                    )
                }
            }
        ) { padding ->
            val base = Modifier.padding(padding)
            // 三个 tab 始终保持在组合中（隐藏的置为 0 尺寸），切换时下载状态与协程不丢失
            Box(Modifier.fillMaxSize()) {
                DownloadTab(context, base.then(if (selectedTab == 0) Modifier.fillMaxSize() else Modifier.size(0.dp)))
                ToolboxTab(context, base.then(if (selectedTab == 1) Modifier.fillMaxSize() else Modifier.size(0.dp)))
                HistoryTab(context, base.then(if (selectedTab == 2) Modifier.fillMaxSize() else Modifier.size(0.dp)), isVisible = selectedTab == 2)
            }
        }

        // 设置页作为覆盖层，Scaffold（含三个 tab 与下载状态）始终保持组合
        if (showSettings) {
            Box(Modifier.fillMaxSize().background(settingsBg)) {
                SettingsScreen(
                    context = context,
                    themeMode = themeMode,
                    glassIntensity = glassIntensity,
                    onThemeChange = onThemeChange,
                    onGlassIntensityChange = onGlassIntensityChange,
                    onBack = { showSettings = false },
                )
            }
        }
    }
}
