package com.bbdroid.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
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
import androidx.compose.material3.Card
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
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(context: Context, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val settings by SettingsStore.settings(context).collectAsState(initial = AppSettings())

    // 登录态
    var loginStatus by remember { mutableStateOf("未登录") }
    var userName by remember { mutableStateOf("") }
    var testLog by remember { mutableStateOf("") }
    var pickedFile by remember { mutableStateOf<File?>(null) }

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
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { val f = copyToCache(context, it); pickedFile = f; testLog = "" }
    }
    val saveDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            scope.launch { SettingsStore.setSaveDir(context, it.toString()) }
        }
    }

    val ffmpegVersion = remember { runCatching { com.arthenica.ffmpegkit.FFmpegKitConfig.getFFmpegVersion() }.getOrNull() ?: "未知" }

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
        Card(modifier = Modifier.fillMaxWidth()) {
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
        Card(modifier = Modifier.fillMaxWidth()) {
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
                        Text("半透明玻璃面板 + 渐变底色（可选）", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = settings.glassEnabled,
                        onCheckedChange = { scope.launch { SettingsStore.setGlassEnabled(context, it) } },
                    )
                }
                if (settings.glassEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("折光强度", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = settings.glassIntensity,
                            onValueChange = { scope.launch { SettingsStore.setGlassIntensity(context, it) } },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f),
                        )
                        Text((settings.glassIntensity * 100).toInt().toString() + "%", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        // 下载
        Card(modifier = Modifier.fillMaxWidth()) {
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FFmpeg 引擎自检", style = MaterialTheme.typography.titleMedium)
                Text("版本: " + ffmpegVersion + " · " + if (ffmpegVersion != "未知") "可用" else "未加载", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { filePicker.launch(arrayOf("video/*")) }) { Text("选择视频") }
                TextButton(
                    enabled = pickedFile != null,
                    onClick = {
                        scope.launch {
                            testLog = ""
                            val f = pickedFile ?: return@launch
                            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.cacheDir
                            runTests(f, dir) { l -> testLog += l + "\n" }
                        }
                    },
                ) { Text("运行 5 项测试") }
                if (testLog.isNotEmpty()) Text(testLog, style = MaterialTheme.typography.labelSmall)
            }
        }

        HorizontalDivider()
        Text("BBDroid · 自用工具", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
