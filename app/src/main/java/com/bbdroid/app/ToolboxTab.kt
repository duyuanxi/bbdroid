package com.bbdroid.app

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

@Composable
fun Dropdown(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label2 = options.firstOrNull { it.second == selected }?.first ?: selected
    Column {
        Text(label, style = MaterialTheme.typography.bodySmall)
        GlassButton(filled = false, onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(label2) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (name, value) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
fun CollapsibleHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleMedium)
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
    var crf by remember { mutableStateOf("23") }
    var videoBitrate by remember { mutableStateOf("2000k") }
    var resolution by remember { mutableStateOf("") }
    var fps by remember { mutableStateOf("") }
    var audioCodec by remember { mutableStateOf("aac") }
    var audioBitrate by remember { mutableStateOf("128k") }
    var channels by remember { mutableStateOf("") }
    var sampleRate by remember { mutableStateOf("") }
    var volume by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var probeInfo by remember { mutableStateOf("") }
    var batchFiles by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var showVideo by remember { mutableStateOf(true) }
    var showAudio by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { val f = copyToCache(context, it); inputPath = f.absolutePath; inputName = f.name; batchFiles = emptyList(); status = "" }
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

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("FFmpeg 转码", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(filled = false, onClick = { picker.launch(arrayOf("video/*", "audio/*", "image/*")) }) { Text("选文件") }
            GlassButton(filled = false, onClick = { multiPicker.launch(arrayOf("video/*", "audio/*", "image/*")) }) { Text("批量导入") }
            GlassButton(filled = false, onClick = { treePicker.launch(null) }) { Text("选文件夹") }
        }
        Text("输入: " + inputName, style = MaterialTheme.typography.bodySmall)

        Dropdown("输出格式", Ffmpeg.formats, format) { format = it }

        CollapsibleHeader("视频参数", showVideo) { showVideo = !showVideo }
        if (showVideo) {
            Dropdown("视频编码器", Ffmpeg.videoCodecs, videoCodec) { videoCodec = it }
            if (videoCodec != "none" && videoCodec != "copy") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useCrf, onCheckedChange = { useCrf = it })
                    Text("CRF 质量模式")
                }
                if (useCrf) OutlinedTextField(value = crf, onValueChange = { crf = it }, label = { Text("CRF（18~28，越小越清晰）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                else OutlinedTextField(value = videoBitrate, onValueChange = { videoBitrate = it }, label = { Text("码率（如 2000k）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Dropdown("分辨率", Ffmpeg.resolutions, resolution) { resolution = it }
                Dropdown("帧率", Ffmpeg.fpsList, fps) { fps = it }
            }
        }

        CollapsibleHeader("音频参数", showAudio) { showAudio = !showAudio }
        if (showAudio) {
            Dropdown("音频编码器", Ffmpeg.audioCodecs, audioCodec) { audioCodec = it }
            if (audioCodec != "none" && audioCodec != "copy") {
                if (audioCodec != "flac" && audioCodec != "pcm_s16le") OutlinedTextField(value = audioBitrate, onValueChange = { audioBitrate = it }, label = { Text("音频码率（如 128k）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Dropdown("声道", Ffmpeg.channelsList, channels) { channels = it }
                Dropdown("采样率", Ffmpeg.sampleRates, sampleRate) { sampleRate = it }
                OutlinedTextField(value = volume, onValueChange = { volume = it }, label = { Text("音量（1.5 放大 / 0.5 降低，留空不调）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton(filled = false, onClick = { scope.launch { probeInfo = withContext(Dispatchers.IO) { if (batchFiles.isNotEmpty()) batchFiles.joinToString("\n\n") { (p, n) -> "【" + n + "】\n" + probe(p) } else if (inputPath.isNotEmpty()) probe(inputPath) else "" } } }) { Text("读元信息") }
            GlassButton(enabled = inputPath.isNotEmpty() && !running, onClick = { scope.launch { running = true; status = transcodeOne(context, inputPath, inputName, format, videoCodec, useCrf, crf, videoBitrate, resolution, fps, audioCodec, audioBitrate, channels, sampleRate, volume) { status = it }; running = false } }) { Text("转码") }
        }
        if (batchFiles.isNotEmpty()) {
            GlassButton(enabled = !running, onClick = {
                scope.launch {
                    running = true; var done = 0
                    withContext(Dispatchers.IO) {
                        for ((p, n) in batchFiles) {
                            status = "[" + (done + 1) + "/" + batchFiles.size + "] " + n
                            val r = transcodeOne(context, p, n, format, videoCodec, useCrf, crf, videoBitrate, resolution, fps, audioCodec, audioBitrate, channels, sampleRate, volume) { }
                            if (r.startsWith("✅")) done++
                        }
                    }
                    status = "✅ 批量完成 " + done + " 个"; running = false
                }
            }) { Text("批量转码 " + batchFiles.size + " 个") }
        }
        if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall)
        if (probeInfo.isNotEmpty()) Text(probeInfo, style = MaterialTheme.typography.bodySmall)
    }
}

private suspend fun transcodeOne(
    context: Context, path: String, name: String, format: String,
    videoCodec: String, useCrf: Boolean, crf: String, videoBitrate: String, resolution: String, fps: String,
    audioCodec: String, audioBitrate: String, channels: String, sampleRate: String, volume: String,
    onProgress: (String) -> Unit,
): String {
    return try {
        val out = File(context.cacheDir, "out." + format)
        val opt = FfmpegOptions(path, out.absolutePath, videoCodec, useCrf, crf, videoBitrate, resolution, fps, audioCodec, audioBitrate, channels, sampleRate, volume)
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

private val mediaExts = setOf("mp4", "mkv", "mov", "avi", "flv", "wmv", "webm", "ts", "m2ts", "mp3", "aac", "flac", "wav", "ogg", "m4a", "jpg", "jpeg", "png", "gif", "bmp")

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

private fun probe(path: String): String {
    return try {
        val session = FFprobeKit.execute("-v quiet -print_format json -show_format -show_streams \"" + path + "\"")
        val json = JSONObject(session.output)
        val sb = StringBuilder()
        val format = json.optJSONObject("format")
        sb.append("封装: ").append(format?.optString("format_name", "?") ?: "?")
        sb.append("   时长: ").append(fmtDuration(format?.optDouble("duration", 0.0) ?: 0.0))
        sb.append("   大小: ").append(fmtSize(format?.optLong("size", 0) ?: 0)).append("\n")
        val streams = json.optJSONArray("streams")
        if (streams != null) for (i in 0 until streams.length()) {
            val s = streams.getJSONObject(i)
            when (s.optString("codec_type", "")) {
                "video" -> sb.append("视频格式: ").append(codecName(s.optString("codec_name"))).append("   分辨率: ").append(s.optInt("width")).append("x").append(s.optInt("height")).append("   帧率: ").append(fmtFps(s.optString("r_frame_rate"))).append("   码率: ").append(fmtBitrate(s.optLong("bit_rate", 0))).append("\n")
                "audio" -> sb.append("音频格式: ").append(codecName(s.optString("codec_name"))).append("   采样率: ").append(s.optString("sample_rate")).append("Hz   声道: ").append(s.optInt("channels")).append("   码率: ").append(fmtBitrate(s.optLong("bit_rate", 0))).append("\n")
            }
        }
        if (sb.isEmpty()) "元信息为空" else sb.toString().trim()
    } catch (e: Exception) { "读取失败" }
}

private fun fmtDuration(seconds: Double): String {
    val t = seconds.toLong(); val h = t / 3600; val m = (t % 3600) / 60; val s = t % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
private fun fmtSize(bytes: Long): String = String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
private fun fmtBitrate(bps: Long): String = if (bps <= 0) "" else if (bps / 1000.0 / 1000.0 >= 1.0) String.format(Locale.US, "%.1fMbps", bps / 1000.0 / 1000.0) else String.format(Locale.US, "%dkbps", bps / 1000)
private fun fmtFps(rate: String): String {
    if (rate.isBlank()) return ""
    val parts = rate.split("/")
    return if (parts.size == 2) { val n = parts[0].toDoubleOrNull() ?: 0.0; val d = parts[1].toDoubleOrNull() ?: 1.0; if (d > 0) String.format(Locale.US, "%.0ffps", n / d) else rate } else rate
}
private fun codecName(codec: String): String = when (codec.lowercase()) {
    "h264" -> "H.264"; "hevc" -> "H.265"; "av1" -> "AV1"; "vp9" -> "VP9"; "mpeg4" -> "MPEG4"; "aac" -> "AAC"; "mp3" -> "MP3"; "flac" -> "FLAC"; "opus" -> "Opus"; "pcm_s16le" -> "PCM"; else -> codec.uppercase()
}
