package com.bbdroid.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

// 从下载页导入到转码页的请求
object ToolboxImport {
    var path by mutableStateOf("")
    var name by mutableStateOf("")
    var id by mutableStateOf(0)
    fun request(p: String, n: String) { path = p; name = n; id++ }
}

data class ProbeInfo(
    val fileName: String = "",
    val container: String = "",
    val videoCodec: String = "",
    val resolution: String = "",
    val duration: String = "",
    val size: String = "",
    val audioCodec: String = "",
)

@Composable
fun Dropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label2 = options.firstOrNull { it.second == selected }?.first ?: selected
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label2) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (name, value) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun SliderRow(label: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onValue: (Float) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium)
        }
        Slider(value = value, onValueChange = onValue, valueRange = range)
    }
}

@Composable
fun ToolboxTab(context: Context, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    var inputPath by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("（未选择文件）") }
    var format by remember { mutableStateOf("mp4") }
    var videoCodec by remember { mutableStateOf("libx264") }
    var useCrf by remember { mutableStateOf(true) }
    var crf by remember { mutableStateOf(23f) }
    var bitrate by remember { mutableStateOf(2000f) } // kbps
    var resolution by remember { mutableStateOf("") }
    var fps by remember { mutableStateOf("") }
    var audioCodec by remember { mutableStateOf("aac") }
    var audioBitrate by remember { mutableStateOf("128k") }
    var channels by remember { mutableStateOf("") }
    var sampleRate by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf(1f) }
    var startTime by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ProbeInfo?>(null) }
    var batchFiles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    // 接收从下载页导入的视频
    LaunchedEffect(ToolboxImport.id) {
        if (ToolboxImport.id > 0 && ToolboxImport.path.isNotEmpty()) {
            inputPath = ToolboxImport.path
            inputName = ToolboxImport.name
            batchFiles = emptyList()
            status = ""
            probe = withContext(Dispatchers.IO) { probeFile(File(ToolboxImport.path)) }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { val f = copyToCache(context, it); inputPath = f.absolutePath; inputName = f.name; batchFiles = emptyList(); status = ""; scope.launch { probe = probeFile(f) } }
    }
    val multiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            val files = uris.map { val f = copyToCache(context, it); f.absolutePath to f.name }
            batchFiles = files; inputPath = files.first().first; inputName = "批量导入 " + files.size + " 个"; status = ""
        }
    }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val files = scanFolder(context, it)
            if (files.isNotEmpty()) { batchFiles = files; inputPath = files.first().first; inputName = "扫描到 " + files.size + " 个"; status = "" }
            else status = "文件夹里没找到媒体文件"
        }
    }

    val audioBitrateOptions = listOf("64k" to "64k", "96k" to "96k", "128k" to "128k", "192k" to "192k", "256k" to "256k", "320k" to "320k")

    val doTranscode: (String, String) -> Unit = { p, n ->
        scope.launch {
            running = true; status = ""
            val out = withContext(Dispatchers.IO) {
                transcodeOne(
                    context, p, n, format, videoCodec, useCrf, crf.toInt(), bitrate.toInt(),
                    resolution, fps, audioCodec, audioBitrate, channels, sampleRate, volume, startTime, duration,
                ) { status = it }
            }
            status = out
            running = false
        }
    }

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FFmpeg 工具箱", style = MaterialTheme.typography.titleLarge)

        // 来源区
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(arrayOf("video/*", "audio/*")) }, modifier = Modifier.weight(1f)) { Text("选文件") }
            OutlinedButton(onClick = { multiPicker.launch(arrayOf("video/*", "audio/*")) }, modifier = Modifier.weight(1f)) { Text("批量导入") }
            OutlinedButton(onClick = { treePicker.launch(null) }, modifier = Modifier.weight(1f)) { Text("选文件夹") }
        }
        Text("输入: " + inputName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // 探测结果卡
        if (probe != null) {
            Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("探测结果", style = MaterialTheme.typography.titleSmall)
                    val p = probe!!
                    InfoLine("文件名", p.fileName)
                    InfoLine("容器", p.container)
                    InfoLine("视频", if (p.videoCodec.isNotEmpty()) p.videoCodec + (if (p.resolution.isNotEmpty()) " · " + p.resolution else "") else "无视频轨")
                    InfoLine("音频", if (p.audioCodec.isNotEmpty()) p.audioCodec else "无音频轨")
                    InfoLine("时长", p.duration)
                    InfoLine("大小", p.size)
                }
            }
        }

        // 输出格式
        Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("输出格式", style = MaterialTheme.typography.titleSmall)
                Dropdown("容器格式", Ffmpeg.formats, format) { format = it }
            }
        }

        // 视频参数
        Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("视频参数", style = MaterialTheme.typography.titleSmall)
                Dropdown("视频编码", Ffmpeg.videoCodecs, videoCodec) { videoCodec = it }
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val items = listOf("质量(CRF)" to true, "码率" to false)
                    items.forEachIndexed { i, (name, v) ->
                        SegmentedButton(
                            selected = useCrf == v,
                            onClick = { useCrf = v },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = items.size),
                        ) { Text(name) }
                    }
                }
                if (useCrf) SliderRow("CRF 值（越小越清晰）", crf.toInt().toString(), crf, 0f..51f) { crf = it }
                else SliderRow("视频码率", bitrate.toInt().toString() + "k", bitrate, 500f..20000f) { bitrate = it }
                Dropdown("分辨率", Ffmpeg.resolutions, resolution) { resolution = it }
                Dropdown("帧率", Ffmpeg.fpsList, fps) { fps = it }
            }
        }

        // 音频参数
        Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("音频参数", style = MaterialTheme.typography.titleSmall)
                Dropdown("音频编码", Ffmpeg.audioCodecs, audioCodec) { audioCodec = it }
                Dropdown("音频码率", audioBitrateOptions, audioBitrate) { audioBitrate = it }
                Dropdown("声道", Ffmpeg.channelsList, channels) { channels = it }
                Dropdown("采样率", Ffmpeg.sampleRates, sampleRate) { sampleRate = it }
                SliderRow("音量", String.format(Locale.US, "%.1fx", volume), volume, 0f..3f) { volume = it }
            }
        }

        // 剪辑（可选）
        Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("剪辑（可选）", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("开始时间，如 10 或 00:01:30（留空=从头）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = duration, onValueChange = { duration = it }, label = { Text("时长，如 30（留空=到结尾）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        // 主操作
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { scope.launch { probe = withContext(Dispatchers.IO) { if (inputPath.isNotEmpty()) probeFile(File(inputPath)) else null } } }) { Text("读元信息") }
            Button(enabled = inputPath.isNotEmpty() && !running, onClick = { doTranscode(inputPath, inputName) }, modifier = Modifier.weight(1f)) { Text(if (running) "转码中…" else "开始转码") }
        }
        if (batchFiles.isNotEmpty()) {
            Button(enabled = !running, onClick = {
                scope.launch {
                    running = true; var done = 0
                    withContext(Dispatchers.IO) {
                        for ((i, f) in batchFiles.withIndex()) {
                            status = "[" + (i + 1) + "/" + batchFiles.size + "] " + f.second
                            val r = transcodeOne(context, f.first, f.second, format, videoCodec, useCrf, crf.toInt(), bitrate.toInt(), resolution, fps, audioCodec, audioBitrate, channels, sampleRate, volume, startTime, duration) { }
                            if (r.startsWith("✅")) done++
                        }
                    }
                    status = "✅ 批量完成 " + done + " 个"; running = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("批量转码 " + batchFiles.size + " 个") }
        }

        // 进行中进度 + 日志
        if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandPink)
        if (status.isNotEmpty()) {
            Text(
                status,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 160.dp)
                    .verticalScroll(rememberScrollState())
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                    .padding(10.dp),
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 1.dp))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

private suspend fun transcodeOne(
    context: Context, path: String, name: String, format: String,
    videoCodec: String, useCrf: Boolean, crf: Int, videoBitrateKbps: Int,
    resolution: String, fps: String,
    audioCodec: String, audioBitrate: String, channels: String, sampleRate: String, volume: Float,
    startTime: String, duration: String,
    onProgress: (String) -> Unit,
): String {
    return try {
        val out = File(context.cacheDir, "out." + format)
        val opt = FfmpegOptions(
            inputPath = path, outputPath = out.absolutePath,
            videoCodec = videoCodec, useCrf = useCrf, crf = crf.toString(), videoBitrate = videoBitrateKbps.toString() + "k",
            resolution = resolution, fps = fps,
            audioCodec = audioCodec, audioBitrate = audioBitrate, channels = channels, sampleRate = sampleRate,
            volume = String.format(Locale.US, "%.1f", volume),
            startTime = startTime, duration = duration,
        )
        onProgress("转码中…")
        val r = FFmpegEngine.run(Ffmpeg.buildCommand(opt))
        if (!r.success) return "❌ 失败: " + r.output.takeLast(300)
        onProgress("保存中…")
        val base = name.substringBeforeLast(".")
        val saved = Storage.saveVideo(context, out, base + "." + format).display
        out.delete()
        "✅ 已保存 " + saved
    } catch (e: Exception) {
        "❌ " + (e.message ?: e.toString())
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
        val session = FFprobeKit.execute("-v quiet -print_format json -show_format -show_streams \"" + path.absolutePath + "\"")
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
    } catch (e: Exception) { ProbeInfo(fileName = path.name, container = "读取失败") }
}

private fun fmtDuration(seconds: Double): String {
    val t = seconds.toLong(); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
private fun fmtSize(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
private fun codecName(codec: String): String = when (codec.lowercase()) {
    "h264" -> "H.264"; "hevc" -> "H.265"; "av1" -> "AV1"; "vp9" -> "VP9"; "mpeg4" -> "MPEG4"; "aac" -> "AAC"; "mp3" -> "MP3"; "flac" -> "FLAC"; "opus" -> "Opus"; "pcm_s16le" -> "PCM"; else -> codec.uppercase()
}
