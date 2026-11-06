package com.bbdroid.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

object ToolboxImport {
    var path by mutableStateOf("")
    var name by mutableStateOf("")
    var id by mutableStateOf(0)
    fun request(p: String, n: String) { path = p; name = n; id++ }
}

data class ProbeInfo(val fileName: String = "", val container: String = "", val videoCodec: String = "",
    val resolution: String = "", val duration: String = "", val size: String = "", val audioCodec: String = "")

@Composable
fun Dropdown(label: String, options: List<Pair<String, String>>, selected: String, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val display = options.firstOrNull { it.second == selected }?.first ?: selected
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(display, Modifier.weight(1f)); Icon(Icons.Outlined.ExpandMore, null, Modifier.size(19.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (name, value) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Composable
private fun SliderRow(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onValue: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value, onValue, enabled = enabled, valueRange = range)
    }
}

@Composable
private fun ParameterSection(title: String, summary: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, if (expanded) "收起$title" else "展开$title")
        }
        AnimatedVisibility(expanded) { Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content) }
    }
}

@Composable
fun ToolboxTab(context: Context, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var inputPath by rememberSaveable { mutableStateOf("") }
    var inputName by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf("mp4") }
    var videoCodec by rememberSaveable { mutableStateOf("libx264") }
    var audioCodec by rememberSaveable { mutableStateOf("aac") }
    var useCrf by rememberSaveable { mutableStateOf(true) }
    var crf by rememberSaveable { mutableStateOf(23f) }
    var bitrate by rememberSaveable { mutableStateOf(2000f) }
    var resolution by rememberSaveable { mutableStateOf("") }
    var fps by rememberSaveable { mutableStateOf("") }
    var audioBitrate by rememberSaveable { mutableStateOf("128k") }
    var channels by rememberSaveable { mutableStateOf("") }
    var sampleRate by rememberSaveable { mutableStateOf("") }
    var volume by rememberSaveable { mutableStateOf(1f) }
    var startTime by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ProbeInfo?>(null) }
    var batchFiles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var status by ToolboxSession.statusState
    val running by ToolboxSession.runningState
    val output by ToolboxSession.outputState
    val enabled = !running && !importing

    LaunchedEffect(ToolboxImport.id) {
        if (ToolboxImport.id > 0 && ToolboxImport.path.isNotEmpty() && !running) {
            inputPath = ToolboxImport.path; inputName = ToolboxImport.name; batchFiles = emptyList(); status = ""
            probe = withContext(Dispatchers.IO) { probeFile(File(inputPath)) }
        }
    }

    fun importFiles(load: () -> List<Pair<String, String>>) {
        if (!enabled) return
        importing = true
        scope.launch {
            try {
                val files = withContext(Dispatchers.IO) { load() }
                require(files.isNotEmpty()) { "没有找到可导入的媒体文件" }
                inputPath = files.first().first
                inputName = files.first().second
                batchFiles = if (files.size > 1) files else emptyList()
                status = "已导入 ${files.size} 个文件"
                probe = withContext(Dispatchers.IO) { probeFile(File(inputPath)) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { status = "导入失败：${e.message}" }
            finally { importing = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importFiles { val file = copyToCache(context, it); listOf(file.absolutePath to file.name) } }
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) importFiles { uris.map { val file = copyToCache(context, it); file.absolutePath to file.name } }
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { importFiles { scanFolder(context, it) } } }
    fun launchPicker(action: () -> Unit) {
        try { action() } catch (_: ActivityNotFoundException) { status = "系统文件选择器不可用，请启用系统「文件」应用后重试" }
    }
    fun request(path: String, name: String) = ConversionRequest(path, name, format, FfmpegOptions(
        videoCodec = videoCodec, audioCodec = audioCodec, useCrf = useCrf, crf = crf.toInt().toString(),
        videoBitrate = "${bitrate.toInt()}k", resolution = resolution, fps = fps, audioBitrate = audioBitrate,
        channels = channels, sampleRate = sampleRate, volume = String.format(Locale.ROOT, "%.1f", volume), startTime = startTime, duration = duration))

    Column(modifier.padding(horizontal = 22.dp).verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        PageIntro("让文件适合每一种场景", "转格式、提取音频、压缩或剪辑，一处完成。")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("源文件", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { launchPicker { picker.launch(arrayOf("video/*", "audio/*", "image/*")) } }, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text("选文件") }
                    OutlinedButton(onClick = { launchPicker { multiPicker.launch(arrayOf("video/*", "audio/*", "image/*")) } }, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text("多选") }
                    OutlinedButton(onClick = { launchPicker { treePicker.launch(null) } }, enabled = enabled, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) { Text("文件夹") }
                }
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(if (inputName.isEmpty()) "尚未选择媒体文件" else if (batchFiles.isNotEmpty()) "${batchFiles.size} 个文件 · $inputName" else inputName,
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                probe?.let { p ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    InfoLine("封装", p.container)
                    if (p.videoCodec.isNotEmpty()) InfoLine("视频", "${p.videoCodec} · ${p.resolution}")
                    if (p.audioCodec.isNotEmpty()) InfoLine("音频", p.audioCodec)
                    InfoLine("时长 / 大小", "${p.duration} · ${p.size}")
                }
                if (inputPath.isNotEmpty()) TextButton(onClick = { scope.launch { probe = withContext(Dispatchers.IO) { probeFile(File(inputPath)) } } }, enabled = enabled) { Text("重新读取元信息") }
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("输出格式", style = MaterialTheme.typography.titleMedium)
                Dropdown("文件格式", Ffmpeg.formats, format, enabled) { value ->
                    format = value
                    val defaults = Ffmpeg.defaultsFor(value)
                    videoCodec = defaults.first; audioCodec = defaults.second
                }
                Text("切换格式时自动匹配编码器，可在下方进一步调整。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (format !in Ffmpeg.audioFormats) ParameterSection("视频参数", "${Ffmpeg.videoCodecs.firstOrNull { it.second == videoCodec }?.first.orEmpty()} · ${if (useCrf) "CRF ${crf.toInt()}" else "${bitrate.toInt()} kbps"}") {
            Dropdown("视频编码", Ffmpeg.videoCodecs, videoCodec, enabled && format != "gif") { videoCodec = it }
            val encodeVideo = enabled && videoCodec !in setOf("copy", "none")
            val qualityEnabled = encodeVideo && videoCodec != "gif"
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(useCrf, { useCrf = true }, { Text("质量 CRF") }, enabled = qualityEnabled)
                FilterChip(!useCrf, { useCrf = false }, { Text("固定码率") }, enabled = qualityEnabled)
            }
            if (useCrf) SliderRow("质量（越小越清晰）", crf.toInt().toString(), crf, 0f..51f, qualityEnabled) { crf = it }
            else SliderRow("视频码率", "${bitrate.toInt()} kbps", bitrate, 500f..20000f, qualityEnabled) { bitrate = it }
            Dropdown("分辨率", Ffmpeg.resolutions, resolution, encodeVideo) { resolution = it }
            Dropdown("帧率", Ffmpeg.fpsList, fps, encodeVideo) { fps = it }
        }
        if (format != "gif") ParameterSection("音频参数", "${Ffmpeg.audioCodecs.firstOrNull { it.second == audioCodec }?.first.orEmpty()} · $audioBitrate") {
            Dropdown("音频编码", if (format in Ffmpeg.audioFormats) Ffmpeg.audioCodecs.filter { it.second in setOf(Ffmpeg.defaultsFor(format).second, "copy") } else Ffmpeg.audioCodecs, audioCodec, enabled) { audioCodec = it }
            val encodeAudio = enabled && audioCodec !in setOf("none", "copy")
            Dropdown("音频码率", listOf("64k", "96k", "128k", "192k", "256k", "320k").map { it to it }, audioBitrate, encodeAudio) { audioBitrate = it }
            Dropdown("声道", Ffmpeg.channelsList, channels, encodeAudio) { channels = it }
            Dropdown("采样率", Ffmpeg.sampleRates, sampleRate, encodeAudio) { sampleRate = it }
            SliderRow("音量", String.format(Locale.ROOT, "%.1fx", volume), volume, 0f..3f, encodeAudio) { volume = it }
        }
        ParameterSection("剪辑", if (startTime.isBlank() && duration.isBlank()) "完整文件，可选设置开始时间与时长" else "从 ${startTime.ifBlank { "0" }} 开始 · ${duration.ifBlank { "至结尾" }}") {
            OutlinedTextField(startTime, { startTime = it }, enabled = enabled, label = { Text("开始时间 · 秒或 00:01:30") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(duration, { duration = it }, enabled = enabled, label = { Text("保留时长 · 留空至结尾") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (running) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("正在处理", style = MaterialTheme.typography.titleMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(status, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = ToolboxSession::cancel) { Text("取消转换") }
                }
            }
        } else {
            Button(onClick = { ToolboxSession.start(context, if (batchFiles.isEmpty()) listOf(request(inputPath, inputName)) else batchFiles.map { request(it.first, it.second) }) },
                enabled = inputPath.isNotEmpty() && enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
                Icon(Icons.Outlined.SwapHoriz, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                Text(if (batchFiles.isEmpty()) "开始转换" else "批量转换 ${batchFiles.size} 个文件")
            }
            if (batchFiles.isNotEmpty()) TextButton(onClick = { ToolboxSession.start(context, listOf(request(inputPath, inputName))) }, enabled = enabled) { Text("仅转换当前文件") }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp).verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surfaceContainerHighest, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(14.dp))
            if (output != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { ToolboxSession.open(context, false) }, modifier = Modifier.weight(1f)) { Text("打开文件") }
                FilledTonalButton(onClick = { ToolboxSession.open(context, true) }, modifier = Modifier.weight(1f)) { Text("分享文件") }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.width(80.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
private val mediaExts = setOf("mp4", "mkv", "mov", "avi", "flv", "wmv", "webm", "ts", "m2ts", "mp3", "aac", "flac", "wav", "ogg", "m4a")

private fun scanFolder(context: Context, treeUri: Uri): List<Pair<String, String>> {
    val result = mutableListOf<Pair<String, String>>()
    val tree = DocumentFile.fromTreeUri(context, treeUri) ?: return result
    for (f in tree.listFiles()) {
        if (f.isFile) {
            val name = f.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in mediaExts) { val c = copyToCache(context, f.uri); result.add(c.absolutePath to c.name) }
        }
    }
    return result
}

private fun probeFile(path: File): ProbeInfo {
    return try {
        val session = FFprobeKit.executeWithArguments(arrayOf("-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", path.absolutePath))
        val json = JSONObject(session.output)
        val format = json.optJSONObject("format")
        val container = format?.optString("format_name", "?") ?: "?"
        val duration = format?.optDouble("duration", 0.0) ?: 0.0
        val size = format?.optLong("size", 0) ?: 0
        var videoCodec = ""; var resolution = ""; var audioCodec = ""; var sampleRate = ""; var channels = ""
        val streams = json.optJSONArray("streams")
        if (streams != null) for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            when (s.optString("codec_type", "")) {
                "video" -> { videoCodec = codecName(s.optString("codec_name")); resolution = s.optInt("width").toString() + "x" + s.optInt("height") }
                "audio" -> { audioCodec = codecName(s.optString("codec_name")); sampleRate = s.optString("sample_rate", "") + "Hz"; channels = s.optInt("channels").toString() + "ch" }
            }
        }
        val audioDesc = if (audioCodec.isNotEmpty()) audioCodec + (if (sampleRate.isNotEmpty()) " · " + sampleRate else "") + (if (channels.isNotEmpty()) " · " + channels else "") else ""
        ProbeInfo(
            fileName = path.name,
            container = container,
            videoCodec = videoCodec,
            resolution = resolution,
            duration = fmtDuration(duration),
            size = fmtSize(size),
            audioCodec = audioDesc,
        )
    } catch (e: Exception) { ProbeInfo(fileName = path.name, container = "读取失败：${e.message}") }
    catch (e: LinkageError) { ProbeInfo(fileName = path.name, container = "FFmpeg 引擎不可用：${e.message}") }
}

private fun fmtDuration(seconds: Double): String {
    val t = seconds.toLong(); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
private fun fmtSize(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
private fun codecName(codec: String): String = when (codec.lowercase()) {
    "h264" -> "H.264"; "hevc" -> "H.265"; "av1" -> "AV1"; "vp9" -> "VP9"; "mpeg4" -> "MPEG4"; "aac" -> "AAC"; "mp3" -> "MP3"; "flac" -> "FLAC"; "opus" -> "Opus"; "pcm_s16le" -> "PCM"; else -> codec.uppercase()
}
