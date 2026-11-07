package com.bbdroid.app.drum

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bbdroid.app.AppCard
import com.bbdroid.app.Dropdown
import com.bbdroid.app.PageIntro
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScoreImport {
    var source by mutableStateOf("")
    var id by mutableIntStateOf(0)
    fun request(value: String) { source = value; id++; com.bbdroid.app.ToolboxDestination.open(1) }
}

@Composable
fun ScorePanel(context: Context, modifier: Modifier = Modifier, session: ScoreSession = remember { DrumScores.get(context) }) {
    var source by rememberSaveable { mutableStateOf("") }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("") }
    var end by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    var left by rememberSaveable { mutableStateOf("0") }
    var top by rememberSaveable { mutableStateOf("0") }
    var right by rememberSaveable { mutableStateOf("100") }
    var bottom by rememberSaveable { mutableStateOf("100") }
    var ignoreOverlays by rememberSaveable { mutableStateOf(true) }
    var validation by rememberSaveable { mutableStateOf("") }
    var restoreLink by rememberSaveable { mutableStateOf("") }
    var showRestore by rememberSaveable { mutableStateOf(false) }
    var showLicense by remember { mutableStateOf(false) }
    var paper by rememberSaveable { mutableStateOf("A4") }
    var autoRows by rememberSaveable { mutableStateOf(true) }
    var rows by rememberSaveable { mutableFloatStateOf(6f) }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val available = session.ready && session.busy.isEmpty()
    val task = session.selected
    val document = session.document
    var handledImport by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(ScoreImport.id) { if (ScoreImport.id > handledImport) { source = ScoreImport.source; handledImport = ScoreImport.id } }
    LaunchedEffect(task?.id) { paper = "A4"; autoRows = true }

    Column(modifier.padding(horizontal = 22.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageIntro("把鼓谱带到谱架上", "从滚动视频到打印页面，先预览，再保存。")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                    Text("鼓谱视频 → PDF", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(source, { source = it; validation = "" }, enabled = available,
                    label = { Text("B站鼓谱链接 / BV 号") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Text("支持分享文案、b23.tv 短链接和 ?p=2 分 P 链接。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showOptions = !showOptions }) {
                    Text("时间与谱区"); Spacer(Modifier.width(6.dp)); Icon(if (showOptions) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                }
                if (showOptions) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(start, { start = it }, label = { Text("开始 · 秒") }, singleLine = true, enabled = available, modifier = Modifier.weight(1f))
                        OutlinedTextField(end, { end = it }, label = { Text("结束 · 留空至结尾") }, singleLine = true, enabled = available, modifier = Modifier.weight(1f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("手动指定谱区", Modifier.weight(1f)); Switch(manual, { manual = it }, enabled = available)
                    }
                    if (manual) {
                        Text("相对于完整画面的百分比，原点在左上角。", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(left, { left = it }, label = { Text("左 %") }, singleLine = true, enabled = available, modifier = Modifier.weight(1f))
                            OutlinedTextField(top, { top = it }, label = { Text("上 %") }, singleLine = true, enabled = available, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(right, { right = it }, label = { Text("右 %") }, singleLine = true, enabled = available, modifier = Modifier.weight(1f))
                            OutlinedTextField(bottom, { bottom = it }, label = { Text("下 %") }, singleLine = true, enabled = available, modifier = Modifier.weight(1f))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("对齐时忽略固定覆盖物", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(ignoreOverlays, { ignoreOverlays = it }, enabled = available)
                    }
                }
                Button(onClick = {
                    try {
                        fun number(value: String) = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() } ?: error("请填写有效的秒数和百分比")
                        val options = ScoreOptions(if (start.isBlank()) 0.0 else number(start), if (end.isBlank()) null else number(end),
                            if (manual) listOf(left, top, right, bottom).map(::number) else null, ignoreOverlays)
                        options.toJson(); ScoreLinks.normalize(source)
                        validation = ""; focus.clearFocus(); session.submit(source, options)
                    } catch (e: Exception) { validation = e.message.orEmpty() }
                }, enabled = available && source.isNotBlank() && session.tasks.none { it.active }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("整理鼓谱")
                }
                if (validation.isNotBlank()) Text(validation, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Text("由 kenqia「鼓谱整理」在线服务处理。提交视频链接及上述选项；支持 10 分钟以内的公开白底纵向滚谱，服务结果保留 24 小时。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("自动拼接原画面，不重新识别音符。打印前请逐页核对；模糊、水印和拼接问题可能保留。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { openWebsite(context, ScoreLinks.PROJECT) }) { Text("开源项目") }
                    TextButton(onClick = { showLicense = true }) { Text("MIT 许可") }
                }
            }
        }
        if (session.busy.isNotEmpty()) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(session.busy, style = MaterialTheme.typography.bodySmall) }
        if (session.error.isNotBlank()) Text(session.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        if (session.notice.isNotBlank()) Text(session.notice, style = MaterialTheme.typography.bodyMedium)

        if (task != null) AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(task.title(), style = MaterialTheme.typography.titleLarge)
                Text(task.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.active) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (task.state == "complete" && document == null && session.error.isBlank()) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在下载 PDF 并准备预览…", style = MaterialTheme.typography.bodySmall)
                }
                if (task.description().isNotBlank()) Text(task.description(), style = MaterialTheme.typography.bodyMedium)
                task.expiresAt?.takeIf { it.isFinite() }?.let {
                    Text("服务保留至 " + SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date((it * 1000).toLong())) + "；已保存到本机的文件不受此限制。", style = MaterialTheme.typography.bodySmall)
                }
                task.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = session::refresh, enabled = available) { Text("刷新任务") }
                    if (task.state in setOf("queued", "running")) TextButton(onClick = session::cancel, enabled = available) { Text("取消整理") }
                    if (task.retryable) TextButton(onClick = session::retry, enabled = available) { Text("重试整理") }
                }
                TextButton(onClick = { clipboard.setText(AnnotatedString(task.link)) }, enabled = available) { Text("复制任务链接") }
                Text("任务链接包含访问凭据，请只分享给信任的人。关闭应用后服务继续处理，可从最近任务返回。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (task.savedUri.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { session.openSaved(false) }, Modifier.weight(1f), enabled = available) { Text("打开已保存 PDF") }
                    FilledTonalButton(onClick = { session.openSaved(true) }, Modifier.weight(1f), enabled = available) { Text("分享 PDF") }
                }
            }
        }

        if (document != null) {
            ScorePreview(document)
            Button(onClick = session::save, enabled = available, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.SaveAlt, null); Spacer(Modifier.width(8.dp)); Text("保存 PDF")
            }
            if (task?.state == "complete") AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("打印排版", style = MaterialTheme.typography.titleMedium)
                    Dropdown("纸张", listOf("A4" to "A4", "A3" to "A3", "A5" to "A5"), paper, available) { paper = it }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("自动安排每页行数", Modifier.weight(1f)); Switch(autoRows, { autoRows = it }, enabled = available) }
                    if (!autoRows) {
                        Text("每页目标 ${rows.toInt()} 行", style = MaterialTheme.typography.bodyMedium)
                        Slider(rows, { rows = it }, enabled = available, valueRange = 1f..16f, steps = 14)
                    }
                    Text("保持完整谱行与原比例；目标行数会按纸张和可读性自动调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { session.relayout(paper, if (autoRows) null else rows.toInt()) }, enabled = available) { Text("应用排版") }
                        TextButton(onClick = { paper = "A4"; autoRows = true; session.restoreDefault() }, enabled = available) { Text("恢复自动") }
                    }
                }
            }
        }

        if (session.tasks.isNotEmpty()) AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("最近鼓谱任务", style = MaterialTheme.typography.titleMedium)
                session.tasks.forEach { item -> TextButton(onClick = { session.select(item.id) }, enabled = available && item.id != task?.id) {
                    Text("${item.label} · ${item.title()}", maxLines = 2)
                } }
            }
        }
        TextButton(onClick = { showRestore = !showRestore }) { Text("恢复已有任务链接") }
        if (showRestore) {
            OutlinedTextField(restoreLink, { restoreLink = it }, label = { Text("完整任务链接（含 # 后内容）") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
            OutlinedButton(onClick = { focus.clearFocus(); session.restore(restoreLink) }, enabled = available && restoreLink.isNotBlank()) { Text("恢复任务") }
        }
    }
    if (showLicense) AlertDialog(onDismissRequest = { showLicense = false }, title = { Text("鼓谱整理 · MIT 许可") },
        text = { Text(remember { context.assets.open("licenses/drum-score-MIT.txt").bufferedReader().use { it.readText() } },
            modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = { showLicense = false }) { Text("关闭") } })
}

@Composable
private fun ScorePreview(document: ScoreDocument) {
    var page by remember(document.file.path) { mutableIntStateOf(0) }
    var expanded by remember(document.file.path) { mutableStateOf(false) }
    var failure by remember(document.file.path, page) { mutableStateOf("") }
    val bitmap by produceState<Bitmap?>(null, document.file.path, page) {
        value = null
        try { value = withContext(Dispatchers.IO) { renderScorePage(document, page) } }
        catch (e: Exception) { failure = "预览加载失败：${e.message}" }
    }
    DisposableEffect(bitmap) { val old = bitmap; onDispose { old?.recycle() } }
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${document.paper} 打印预览", style = MaterialTheme.typography.titleMedium)
            if (document.message.isNotBlank()) Text(document.message, style = MaterialTheme.typography.bodySmall)
            if (bitmap != null) Image(bitmap!!.asImageBitmap(), "鼓谱第 ${page + 1} 页，共 ${document.pages} 页", Modifier.fillMaxWidth().background(Color.White))
            else if (failure.isEmpty()) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failure.isNotEmpty()) Text(failure, color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { page-- }, enabled = page > 0) { Icon(Icons.Outlined.ChevronLeft, "上一页") }
                Text("${page + 1} / ${document.pages}")
                IconButton(onClick = { page++ }, enabled = page < document.pages - 1) { Icon(Icons.Outlined.ChevronRight, "下一页") }
            }
            TextButton(onClick = { expanded = true }, enabled = bitmap != null) { Icon(Icons.Outlined.ZoomIn, null); Spacer(Modifier.width(6.dp)); Text("放大预览") }
        }
    }
    if (expanded && bitmap != null) Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Surface(Modifier.fillMaxSize(), color = Color(0xFF101820)) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset = if (scale <= 1f) Offset.Zero else offset + pan
                } }, contentAlignment = Alignment.Center) {
                    Image(bitmap!!.asImageBitmap(), "放大鼓谱预览", Modifier.fillMaxWidth().graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                    })
                }
                TextButton(onClick = { expanded = false }, Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)) { Text("关闭", color = Color.White) }
                Text("双指缩放与移动", color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp))
            }
        }
    }
}

private fun openWebsite(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
