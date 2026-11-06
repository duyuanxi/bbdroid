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
    val name = queryDisplayName(context, uri) ?: "input"
    val ext = name.substringAfterLast('.', "bin").filter { it.isLetterOrDigit() }.take(12).ifBlank { "bin" }
    val dest = File.createTempFile("input-", ".$ext", context.cacheDir)
    try {
        val stream = context.contentResolver.openInputStream(uri) ?: error("无法读取所选文件")
        stream.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
    } catch (e: Exception) { dest.delete(); throw e }
    return dest
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
    var n = name.replace("/", "_").replace(":", "_").replace("*", "_")
        .replace("?", "_").replace("<", "_").replace(">", "_").replace("|", "_").trim()
    if (n.isEmpty()) n = "video"
    if (n.length > 80) n = n.take(80)
    return n
}
