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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
fun SettingsScreen(
    context: Context,
    themeMode: ThemeMode,
    glassIntensity: Float,
    onThemeChange: (ThemeMode) -> Unit,
    onGlassIntensityChange: (Float) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loginStatus by remember { mutableStateOf("未登录") }
    var userName by remember { mutableStateOf("") }
    var testLog by remember { mutableStateOf("") }
    var pickedFile by remember { mutableStateOf<File?>(null) }
    var pickedName by remember { mutableStateOf("（未选择）") }
    var saveDir by remember { mutableStateOf(Storage.getSaveDir(context)) }

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
        uri?.let { val f = copyToCache(context, it); pickedFile = f; pickedName = f.name; testLog = "" }
    }
    val saveDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            Storage.setSaveDir(context, it.toString())
            saveDir = it.toString()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
            Text("设置", style = MaterialTheme.typography.titleLarge)
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("账号", style = MaterialTheme.typography.titleMedium)
                Text("状态: " + loginStatus + (if (userName.isNotEmpty()) "（" + userName + "）" else ""))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(filled = false, onClick = { webLoginLauncher.launch(Intent(context, LoginActivity::class.java)) }) { Text("网页登录") }
                    GlassButton(filled = false, onClick = { qrLoginLauncher.launch(Intent(context, QrLoginActivity::class.java)) }) { Text("扫码登录") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("存储位置", style = MaterialTheme.typography.titleMedium)
                Text("视频: " + (if (saveDir.isEmpty()) "Download/BBDroid/视频（默认）" else "自定义文件夹"), style = MaterialTheme.typography.bodySmall)
                Text("封面/弹幕/字幕: Download/BBDroid/附赠", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(filled = false, onClick = { saveDirPicker.launch(null) }) { Text("选择文件夹") }
                    if (saveDir.isNotEmpty()) GlassButton(filled = false, onClick = { Storage.clearSaveDir(context); saveDir = "" }) { Text("恢复默认") }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("主题", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (themeMode == ThemeMode.NORMAL)
                        GlassButton(onClick = { ThemePrefs.set(context, ThemeMode.GLASS); onThemeChange(ThemeMode.GLASS) }, modifier = Modifier.weight(1f)) { Text("普通主题 ✓") }
                    else
                        GlassButton(filled = false, onClick = { ThemePrefs.set(context, ThemeMode.NORMAL); onThemeChange(ThemeMode.NORMAL) }, modifier = Modifier.weight(1f)) { Text("普通主题") }
                    if (themeMode == ThemeMode.GLASS)
                        GlassButton(onClick = { ThemePrefs.set(context, ThemeMode.NORMAL); onThemeChange(ThemeMode.NORMAL) }, modifier = Modifier.weight(1f)) { Text("液态玻璃 ✓") }
                    else
                        GlassButton(filled = false, onClick = { ThemePrefs.set(context, ThemeMode.GLASS); onThemeChange(ThemeMode.GLASS) }, modifier = Modifier.weight(1f)) { Text("液态玻璃") }
                }
                Text("液态玻璃：半透明玻璃面板 + 渐变底色，和普通主题随时切换", style = MaterialTheme.typography.bodySmall)
                if (themeMode == ThemeMode.GLASS) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("折光效果", style = MaterialTheme.typography.bodyMedium)
                        Slider(
                            value = glassIntensity,
                            onValueChange = onGlassIntensityChange,
                            onValueChangeFinished = { ThemePrefs.setGlassIntensity(context, glassIntensity) },
                            valueRange = 0f..1f,
                            modifier = Modifier.weight(1f)
                        )
                        Text((glassIntensity * 100).toInt().toString() + "%", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("0=通透 · 100=磨砂折光（改动即时生效）", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FFmpeg 引擎自检", style = MaterialTheme.typography.titleMedium)
                GlassButton(filled = false, onClick = { filePicker.launch(arrayOf("video/*")) }) { Text("选择视频") }
                Text("已选: " + pickedName, style = MaterialTheme.typography.bodySmall)
                GlassButton(
                    filled = false,
                    enabled = pickedFile != null,
                    onClick = {
                        scope.launch {
                            testLog = ""
                            val f = pickedFile ?: return@launch
                            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.cacheDir
                            runTests(f, dir) { l -> testLog += l + "\n" }
                        }
                    }
                ) { Text("运行 5 项测试") }
                if (testLog.isNotEmpty()) Text(testLog, style = MaterialTheme.typography.bodySmall)
            }
        }

        HorizontalDivider()
        Text("BBDroid · 自用工具", style = MaterialTheme.typography.bodySmall)
    }
}
