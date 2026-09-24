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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxTab(context: Context, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()

    var inputPath by remember { mutableStateOf("") }
    var inputName by remember { mutableStateOf("（未选择文件）") }
    var format by remember { mutableStateOf("mp4") }
    var resolution by remember { mutableStateOf("") }
    var bitrate by remember { mutableStateOf(2000f) } // kbps
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var probe by remember { mutableStateOf<ProbeInfo?>(null) }
    var batchFiles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

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

    val fmtOptions = listOf("MP4" to "mp4", "MKV" to "mkv", "WebM" to "webm")

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("FFmpeg 转码", style = MaterialTheme.typography.titleLarge)

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

        // 参数区
        Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("输出格式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    fmtOptions.forEachIndexed { i, (name, value) ->
                        SegmentedButton(
                            selected = format == value,
                            onClick = { format = value },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = fmtOptions.size),
                        ) { Text(name) }
                    }
                }
                Dropdown("分辨率", Ffmpeg.resolutions, resolution) { resolution = it }
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("码率", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(bitrate.toInt().toString() + "k", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = bitrate,
                        onValueChange = { bitrate = it },
                        valueRange = 500f..8000f,
                        steps = 14,
                    )
                }
            }
        }

        // 主操作
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { scope.launch { probe = withContext(Dispatchers.IO) { if (inputPath.isNotEmpty()) probeFile(File(inputPath)) else null } } }) { Text("读元信息") }
            Button(
                enabled = inputPath.isNotEmpty() && !running,
                onClick = {
                    scope.launch {
                        running = true; status = ""
                        val out = withContext(Dispatchers.IO) {
                            transcodeOne(context, inputPath, inputName, format, bitrate.toInt(), resolution) { status = it }
                        }
                        status = out
                        running = false
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (running) "转码中…" else "开始转码") }
        }
        if (batchFiles.isNotEmpty()) {
            Button(
                enabled = !running,
                onClick = {
                    scope.launch {
                        running = true; var done = 0
                        withContext(Dispatchers.IO) {
                            for ((i, f) in batchFiles.withIndex()) {
                                status = "[" + (i + 1) + "/" + batchFiles.size + "] " + f.second
                                val r = transcodeOne(context, f.first, f.second, format, bitrate.toInt(), resolution) { }
                                if (r.startsWith("✅")) done++
                            }
                        }
                        status = "✅ 批量完成 " + done + " 个"; running = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("批量转码 " + batchFiles.size + " 个") }
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
    context: Context, path: String, name: String, format: String, bitrateKbps: Int, resolution: String,
    onProgress: (String) -> Unit,
): String {
    return try {
        val out = File(context.cacheDir, "out." + format)
        val opt = FfmpegOptions(
            inputPath = path, outputPath = out.absolutePath,
            videoCodec = "libx264", useCrf = false, videoBitrate = bitrateKbps.toString() + "k",
            resolution = resolution, fps = "",
            audioCodec = "aac", audioBitrate = "128k", channels = "", sampleRate = "", volume = "",
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
