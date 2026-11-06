package com.bbdroid.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

// 下载速度平滑器：2 秒滑动窗口平均（过滤波动）
class SpeedTracker(private val windowMs: Long = 2000) {
    private val lock = Any()
    private val samples = ArrayDeque<Pair<Long, Long>>()

    fun update(nowMs: Long, bytes: Long): Double {
        synchronized(lock) {
            samples.addLast(nowMs to bytes)
            while (samples.size > 2 && nowMs - samples.first().first > windowMs) samples.removeFirst()
            val oldest = samples.first()
            val dt = nowMs - oldest.first
            val db = bytes - oldest.second
            return if (dt > 0 && db > 0) db * 1000.0 / dt else 0.0
        }
    }
}

fun copyToCache(context: Context, uri: Uri): File {
    val name = sanitizeFilename(queryDisplayName(context, uri) ?: "input.bin")
    val directory = File(context.cacheDir, "imports/" + java.util.UUID.randomUUID()).apply {
        check(mkdirs()) { "无法创建导入目录" }
    }
    val dest = File(directory, name)
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
        stream.use { input -> dest.outputStream().use { input.copyTo(it) } }
        require(dest.length() > 0) { "所选文件为空" }
        return dest
    } catch (e: Exception) {
        dest.delete()
        directory.delete()
        throw e
    }
}

fun queryDisplayName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
    }
    return name
}

fun sanitizeFilename(name: String): String {
    val safe = name.map { if (it in "\\/:*?\"<>|" || it.code < 32) '_' else it }.joinToString("").trim().trim('.')
    if (safe.isEmpty()) return "video"
    if (safe.length <= 120) return safe
    val extension = safe.substringAfterLast('.', "").take(12)
    return safe.take(100) + if (extension.isEmpty()) "" else ".$extension"
}

suspend fun runTests(input: File, dir: File, onLog: (String) -> Unit) {
    val base = input.nameWithoutExtension
    val inPath = input.absolutePath
    val v = File(dir, "out_" + base + "_video.mp4")
    val a = File(dir, "out_" + base + "_audio.m4a")
    val mux = File(dir, "out_" + base + "_mux.mp4")
    val mp3 = File(dir, "out_" + base + ".mp3")
    val cut = File(dir, "out_" + base + "_cut.mp4")
    val rot = File(dir, "out_" + base + "_rot.mp4")

    val tests = listOf(
        "转格式 mp4→mp3" to "-i \"" + inPath + "\" -vn -c:a libmp3lame \"" + mp3.absolutePath + "\" -y",
        "分离视频轨" to "-i \"" + inPath + "\" -an -c:v copy \"" + v.absolutePath + "\" -y",
        "分离音频轨" to "-i \"" + inPath + "\" -vn -c:a copy \"" + a.absolutePath + "\" -y",
        "合并音视频" to "-i \"" + v.absolutePath + "\" -i \"" + a.absolutePath + "\" -c copy \"" + mux.absolutePath + "\" -y",
        "剪切前10秒" to "-ss 0 -t 10 -i \"" + inPath + "\" -c copy \"" + cut.absolutePath + "\" -y",
        "滤镜旋转90°" to "-i \"" + inPath + "\" -vf \"transpose=1\" -c:a aac \"" + rot.absolutePath + "\" -y",
    )
    for ((name, cmd) in tests) {
        onLog("▶ " + name)
        val r = FFmpegEngine.run(cmd)
        onLog(if (r.success) "  ✅ 成功 (" + r.durationMs + "ms)" else "  ❌ 失败")
        if (!r.success && r.output.isNotBlank()) onLog(r.output.takeLast(400))
        onLog("")
    }
    onLog("全部完成，输出目录: " + dir.absolutePath)
}
