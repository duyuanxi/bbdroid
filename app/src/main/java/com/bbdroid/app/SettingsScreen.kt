package com.bbdroid.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bbdroid.app.bilibili.BiliApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(context: Context, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings(context).collectAsState(initial = AppSettings())

    // 登录态
    var loginStatus by remember { mutableStateOf("未登录") }
    var userName by remember { mutableStateOf("") }
    var testLog by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var ffmpegDiag by remember { mutableStateOf("正在检查…") }
    var materialStrength by remember(settings.glassIntensity) { mutableStateOf(settings.glassIntensity) }

    fun refreshLogin() {
        val c = Auth.load(context)
        BiliApi.cookie = c
        loginStatus = if (Auth.isLoggedIn(c)) "已登录" else "未登录"
        if (Auth.isLoggedIn(c)) {
            scope.launch {
                val u = withContext(Dispatchers.IO) { runCatching { BiliApi.fetchUserInfo() }.getOrNull() }
                userName = u?.name ?: ""
            }
        } else userName = ""
    }

    LaunchedEffect(Unit) { refreshLogin() }

    val webLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshLogin() }
    val qrLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshLogin() }
    val saveDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    SettingsStore.setSaveDir(context, it.toString())
                } catch (e: SecurityException) { AppSnackbar.host.showSnackbar("无法保存目录授权，请重新选择") }
            }
        }
    }

    LaunchedEffect(Unit) { ffmpegDiag = FFmpegEngine.status().description }
    val deviceAbis = remember { android.os.Build.SUPPORTED_ABIS.joinToString(", ") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * 0.9f)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onBack) { Icon(Icons.Filled.Close, contentDescription = "关闭") }
        }

        // 账号
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("账号", style = MaterialTheme.typography.titleMedium)
                Text("状态: " + loginStatus + (if (userName.isNotEmpty()) "（" + userName + "）" else ""), style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { webLoginLauncher.launch(Intent(context, LoginActivity::class.java)) }) { Text("网页登录") }
                    OutlinedButton(onClick = { qrLoginLauncher.launch(Intent(context, QrLoginActivity::class.java)) }) { Text("扫码登录") }
                }
            }
        }

        // 主题
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("主题模式", style = MaterialTheme.typography.titleMedium)
                val modes = listOf("跟随系统" to ThemeMode.SYSTEM, "浅色" to ThemeMode.LIGHT, "深色" to ThemeMode.DARK)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { i, (name, mode) ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { scope.launch { SettingsStore.setThemeMode(context, mode) } },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = modes.size),
                        ) { Text(name) }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("液态玻璃皮肤", style = MaterialTheme.typography.bodyMedium)
                        Text(if (android.os.Build.VERSION.SDK_INT >= 31) "悬浮导航 · 实时背景模糊 · 边缘高光" else "当前系统使用清晰的半透明材质", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.glassEnabled,
                        onCheckedChange = { scope.launch { SettingsStore.setGlassEnabled(context, it) } },
                    )
                }
                if (settings.glassEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("材质强度", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = materialStrength,
                            onValueChange = { materialStrength = it },
                            onValueChangeFinished = { scope.launch { SettingsStore.setGlassIntensity(context, materialStrength) } },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        Text((materialStrength * 100).toInt().toString() + "%", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 下载
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("下载", style = MaterialTheme.typography.titleMedium)
                Text(
                    "保存目录: " + (if (settings.saveDir.isEmpty()) "Download/BBDroid/视频（默认）" else "自定义文件夹"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { saveDirPicker.launch(null) }) { Text("选择文件夹") }
                    if (settings.saveDir.isNotEmpty()) {
                        OutlinedButton(onClick = { scope.launch { SettingsStore.setSaveDir(context, "") } }) { Text("恢复默认") }
                    }
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("同时下载数", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(settings.concurrentDownloads.toString(), style = MaterialTheme.typography.labelMedium)
                }
                Slider(
                    value = settings.concurrentDownloads.toFloat(),
                    onValueChange = { scope.launch { SettingsStore.setConcurrentDownloads(context, it.toInt()) } },
                    valueRange = 1f..8f,
                    steps = 6,
                )
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("仅 Wi-Fi 下载", style = MaterialTheme.typography.bodyMedium)
                        Text("移动数据下不自动下载", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.wifiOnly,
                        onCheckedChange = { scope.launch { SettingsStore.setWifiOnly(context, it) } },
                    )
                }
            }
        }

        // FFmpeg 引擎自检
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FFmpeg 引擎自检", style = MaterialTheme.typography.titleMedium)
                Text("引擎: " + ffmpegDiag, style = MaterialTheme.typography.bodySmall)
                Text("设备 ABI: " + deviceAbis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("安装包支持：arm64-v8a、x86_64（64 位）", style = MaterialTheme.typography.bodySmall)
                Text("自动生成短片，验证编码、媒体探测和音视频封装，无需选择文件。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        testLog = ""
                        scope.launch {
                            try {
                                ffmpegDiag = FFmpegEngine.status().description
                                FFmpegEngine.selfTest(context.cacheDir) { line ->
                                    withContext(Dispatchers.Main) { testLog += line + "\n" }
                                }
                            } finally { checking = false }
                        }
                    },
                ) { Text(if (checking) "正在自检…" else "运行引擎自检") }
                if (testLog.isNotEmpty()) Text(testLog, style = MaterialTheme.typography.labelSmall)
            }
        }

        HorizontalDivider()
        Text("BBDroid · " + context.packageManager.getPackageInfo(context.packageName, 0).versionName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
