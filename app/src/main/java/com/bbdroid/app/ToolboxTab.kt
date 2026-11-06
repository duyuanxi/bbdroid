package com.bbdroid.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.Locale

object ToolboxImport {
    var path by mutableStateOf("")
    var name by mutableStateOf("")
    var id by mutableStateOf(0)
    fun request(p: String, n: String) { path = p; name = n; id++ }
}

private data class MediaInput(val path: String, val name: String)

@Composable
fun Dropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label + " · " + (options.firstOrNull { it.second == selected }?.first ?: selected), modifier = Modifier.weight(1f))
            Text("⌄")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (name, value) -> DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false }) }
        }
    }
}

@Composable
private fun ParameterSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value.toInt().toString(), color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ToolboxTab(context: Context, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<MediaInput>>(emptyList()) }
    var metadata by remember { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf("mp4") }
    var videoCodec by rememberSaveable { mutableStateOf("libx264") }
    var audioCodec by rememberSaveable { mutableStateOf("aac") }
    var crf by rememberSaveable { mutableFloatStateOf(23f) }
    var useCrf by rememberSaveable { mutableStateOf(true) }
    var bitrate by rememberSaveable { mutableFloatStateOf(2000f) }
    var resolution by rememberSaveable { mutableStateOf("") }
    var fps by rememberSaveable { mutableStateOf("") }
    var audioBitrate by rememberSaveable { mutableStateOf("128k") }
    var channels by rememberSaveable { mutableStateOf("") }
    var sampleRate by rememberSaveable { mutableStateOf("") }
    var volume by rememberSaveable { mutableFloatStateOf(1f) }
    var startTime by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }

    fun selectFormat(value: String) {
        format = value
        if (Ffmpeg.videoCodecsFor(value).none { it.second == videoCodec }) videoCodec = if (value == "webm") "libvpx-vp9" else Ffmpeg.videoCodecsFor(value).first().second
        if (Ffmpeg.audioCodecsFor(value).none { it.second == audioCodec }) audioCodec = Ffmpeg.audioCodecsFor(value).first().second
    }
    fun importFiles(uris: List<Uri>) {
        if (running || importing || uris.isEmpty()) return
        importing = true
        scope.launch {
            val imported = mutableListOf<MediaInput>()
            try {
                withContext(Dispatchers.IO) {
                    uris.forEach { uri ->
                        val name = queryDisplayName(context, uri) ?: "媒体文件"
                        imported += MediaInput(copyToCache(context, uri).absolutePath, name)
                    }
                }
                files = imported.toList()
                metadata = if (files.size == 1) describeMedia(files.first()) else "已导入 ${files.size} 个文件"
                status = ""
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                withContext(Dispatchers.IO) { imported.forEach { File(it.path).delete() } }
                status = "导入失败：${e.message}"
            } finally { importing = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> importFiles(listOf(uri)) } }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { importFiles(it) }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null && !running && !importing) {
            importing = true
            scope.launch {
                try {
                    val uris = withContext(Dispatchers.IO) {
                        val tree = DocumentFile.fromTreeUri(context, uri) ?: error("无法读取文件夹")
                        tree.listFiles().filter { it.isFile && it.name?.substringAfterLast('.', "")?.lowercase() in mediaExtensions }.map { it.uri }
                    }
                    importing = false
                    if (uris.isEmpty()) status = "文件夹中没有可导入的媒体文件" else importFiles(uris)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { importing = false; status = "读取文件夹失败：${e.message}" }
            }
        }
    }
    LaunchedEffect(ToolboxImport.id) {
        if (ToolboxImport.id > 0 && ToolboxImport.path.isNotBlank() && !running) {
            files = listOf(MediaInput(ToolboxImport.path, ToolboxImport.name))
            metadata = describeMedia(files.first()); status = ""
        }
    }

    fun start() {
        if (running || importing || files.isEmpty()) return
        val inputs = files.toList()
        val options = FfmpegOptions(videoCodec = videoCodec, useCrf = useCrf, crf = crf.toInt().toString(), videoBitrate = "${bitrate.toInt()}k",
            resolution = resolution, fps = fps, audioCodec = audioCodec, audioBitrate = audioBitrate, channels = channels, sampleRate = sampleRate,
            volume = String.format(Locale.US, "%.1f", volume), startTime = startTime.trim(), duration = duration.trim())
        val outputFormat = format
        running = true
        job = scope.launch {
            var completed = 0
            val logs = mutableListOf<String>()
            try {
                for ((index, input) in inputs.withIndex()) {
                    status = "正在处理 ${index + 1}/${inputs.size} · ${input.name}"
                    val result = transcodeOne(context, input, outputFormat, options)
                    if (result.first) completed++
                    logs += result.second
                }
                status = "完成 $completed/${inputs.size} 个文件\n" + logs.joinToString("\n\n")
            } catch (e: CancellationException) { status = "已停止 · 完成 $completed/${inputs.size} 个文件"; throw e }
            finally { running = false; job = null }
        }
    }

    Column(modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = LocalPageBottomInset.current + 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageHeading("媒体工具箱", "转码、提取音频与无损封装。")
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("01  导入媒体", style = MaterialTheme.typography.titleMedium)
                Text(if (files.isEmpty()) "从设备中选择视频或音频" else files.first().name + if (files.size > 1) " 等 ${files.size} 个文件" else "", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }, enabled = !running && !importing, modifier = Modifier.fillMaxWidth()) { Text(if (importing) "正在导入…" else "选择文件") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { multiPicker.launch(arrayOf("video/*", "audio/*")) }, enabled = !running && !importing) { Text("批量导入") }
                    OutlinedButton(onClick = { treePicker.launch(null) }, enabled = !running && !importing) { Text("选择文件夹") }
                }
                if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("02  输出设置", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = format == "mp4" && videoCodec == "libx264", enabled = !running, onClick = { selectFormat("mp4"); videoCodec = "libx264"; audioCodec = "aac" }, label = { Text("通用视频") })
                    FilterChip(selected = videoCodec == "copy" && audioCodec == "copy", enabled = !running, onClick = { selectFormat("mkv"); videoCodec = "copy"; audioCodec = "copy" }, label = { Text("无损封装") })
                    FilterChip(selected = format == "mp3", enabled = !running, onClick = { selectFormat("mp3") }, label = { Text("提取音频") })
                }
                Dropdown("格式", Ffmpeg.formats, format) { if (!running) selectFormat(it) }
                Text("${Ffmpeg.videoCodecsFor(format).firstOrNull { it.second == videoCodec }?.first.orEmpty()} · ${Ffmpeg.audioCodecsFor(format).firstOrNull { it.second == audioCodec }?.first.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "收起详细参数" else "调整编码与剪辑参数") }
            }
        }
        if (advanced) {
            if (format !in Ffmpeg.audioFormats && format != "gif") AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("视频", style = MaterialTheme.typography.titleMedium)
                    Dropdown("编码", Ffmpeg.videoCodecsFor(format), videoCodec) { if (!running) videoCodec = it }
                    if (videoCodec != "copy" && videoCodec != "none") {
                        if (videoCodec in setOf("libx264", "libx265", "libaom-av1", "libvpx-vp9")) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("使用恒定质量 CRF", modifier = Modifier.weight(1f))
                                Switch(checked = useCrf, onCheckedChange = { if (!running) useCrf = it })
                            }
                            if (useCrf) ParameterSlider("质量（数值越小越清晰）", crf, 0f..51f) { if (!running) crf = it }
                        }
                        if (!useCrf || videoCodec.contains("mediacodec")) ParameterSlider("码率 kbps", bitrate, 500f..20000f) { if (!running) bitrate = it }
                        Dropdown("分辨率", Ffmpeg.resolutions, resolution) { if (!running) resolution = it }
                        Dropdown("帧率", Ffmpeg.fpsList, fps) { if (!running) fps = it }
                        if (videoCodec.contains("mediacodec")) Text("硬件编码取决于设备支持；失败时可切换 H.264 软件编码。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (format != "gif") AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("音频", style = MaterialTheme.typography.titleMedium)
                    Dropdown("编码", Ffmpeg.audioCodecsFor(format), audioCodec) { if (!running) audioCodec = it }
                    if (audioCodec !in setOf("none", "copy")) {
                        if (audioCodec !in setOf("flac", "pcm_s16le")) Dropdown("码率", listOf("64 kbps" to "64k", "128 kbps" to "128k", "192 kbps" to "192k", "256 kbps" to "256k", "320 kbps" to "320k"), audioBitrate) { if (!running) audioBitrate = it }
                        Dropdown("声道", Ffmpeg.channelsList, channels) { if (!running) channels = it }
                        Dropdown("采样率", Ffmpeg.sampleRates, sampleRate) { if (!running) sampleRate = it }
                        Text("音量 ${String.format(Locale.US, "%.1f", volume)} 倍", style = MaterialTheme.typography.bodyMedium)
                        Slider(value = volume, onValueChange = { if (!running) volume = it }, valueRange = 0f..3f)
                    }
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("剪辑", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(startTime, { startTime = it }, enabled = !running, label = { Text("开始时间，秒或 00:01:30") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(duration, { duration = it }, enabled = !running, label = { Text("持续秒数，留空到结尾") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (videoCodec == "copy") Text("复制流时按关键帧剪辑，起点可能有偏差。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = { start() }, enabled = files.isNotEmpty() && !running && !importing, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (running) "正在处理…" else if (files.size > 1) "处理 ${files.size} 个文件" else "开始处理") }
        if (running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(onClick = { job?.cancel() }, modifier = Modifier.fillMaxWidth()) { Text("停止处理") }
        }
        if (status.isNotBlank()) AppCard(Modifier.fillMaxWidth()) {
            Text(status, modifier = Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private suspend fun transcodeOne(context: Context, input: MediaInput, format: String, options: FfmpegOptions): Pair<Boolean, String> = withContext(Dispatchers.IO) {
    var output: File? = null
    try {
        val out = File.createTempFile("transcode-", ".$format", context.cacheDir)
        output = out
        val arguments = Ffmpeg.buildArguments(options.copy(inputPath = input.path, outputPath = out.absolutePath))
        val result = FFmpegEngine.run(arguments)
        if (!result.success || out.length() == 0L) return@withContext false to "${input.name}：处理失败\n${result.output.takeLast(1800)}"
        currentCoroutineContext().ensureActive()
        val saved = Storage.saveVideo(context, out, sanitizeFilename(input.name.substringBeforeLast('.')) + ".$format")
        true to "已保存 · ${saved.display}"
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { false to "${input.name}：${e.message}" }
    finally { output?.delete() }
}

private suspend fun describeMedia(input: MediaInput): String {
    val result = FFmpegEngine.probe(input.path)
    if (!result.success) return "媒体信息读取失败：${result.output.takeLast(1400)}"
    return try {
        val json = JSONObject(result.output)
        val format = json.optJSONObject("format")
        val streams = json.optJSONArray("streams")
        val details = mutableListOf<String>()
        details += format?.optString("format_name").orEmpty()
        val seconds = format?.optString("duration")?.toDoubleOrNull()
        if (seconds != null) details += String.format(Locale.US, "%.1f 秒", seconds)
        if (streams != null) for (i in 0 until streams.length()) {
            val stream = streams.getJSONObject(i)
            if (stream.optString("codec_type") in setOf("video", "audio")) details += stream.optString("codec_name") +
                if (stream.optString("codec_type") == "video") " ${stream.optInt("width")} × ${stream.optInt("height")}" else ""
        }
        details.filter { it.isNotBlank() }.joinToString(" · ")
    } catch (e: Exception) { "媒体信息解析失败：${e.message}" }
}
private val mediaExtensions = setOf("mp4", "mkv", "mov", "avi", "flv", "wmv", "webm", "ts", "m2ts", "mp3", "aac", "flac", "wav", "ogg", "m4a")
