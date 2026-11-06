package com.bbdroid.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.*
import java.io.File

data class ConversionRequest(val path: String, val name: String, val format: String, val options: FfmpegOptions)

object ToolboxSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val runningState = mutableStateOf(false)
    val statusState = mutableStateOf("")
    val outputState = mutableStateOf<SavedFile?>(null)
    val outputNameState = mutableStateOf("")
    private var job: Job? = null

    fun start(context: Context, requests: List<ConversionRequest>) {
        if (runningState.value || requests.isEmpty()) return
        val app = context.applicationContext
        runningState.value = true
        statusState.value = "正在准备…"
        outputState.value = null
        job = scope.launch {
            var completed = 0
            val errors = mutableListOf<String>()
            try {
                for ((index, request) in requests.withIndex()) {
                    ensureActive()
                    statusState.value = if (requests.size > 1) "${index + 1} / ${requests.size} · ${request.name}" else "正在转码…"
                    try {
                        val saved = convert(app, request)
                        completed++
                        outputState.value = saved
                        outputNameState.value = request.name.substringBeforeLast('.', request.name) + ".${request.format}"
                        statusState.value = "已保存 · ${saved.display}"
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { errors.add("${request.name}：${e.message ?: "转换失败"}") }
                }
                if (errors.isNotEmpty()) statusState.value = "完成 $completed / ${requests.size}，失败 ${errors.size}\n${errors.joinToString("\n").takeLast(2400)}"
                else if (requests.size > 1) statusState.value = "批量转换完成 · $completed 个文件"
            } catch (e: CancellationException) {
                statusState.value = "已取消转换" + if (completed > 0) "，已保存 $completed 个文件" else ""
                throw e
            } finally { runningState.value = false }
        }
    }

    private suspend fun convert(context: Context, request: ConversionRequest): SavedFile = withContext(Dispatchers.IO) {
        require(File(request.path).isFile) { "源文件已不存在，请重新导入" }
        val out = File.createTempFile("conversion-", ".${request.format}", context.cacheDir)
        try {
            val arguments = Ffmpeg.buildArguments(request.options.copy(inputPath = request.path, outputPath = out.absolutePath))
            val result = FFmpegEngine.run(arguments)
            check(result.success && out.length() > 0) { result.output.takeLast(1800).ifBlank { "未生成有效输出" } }
            ensureActive()
            statusState.value = "正在保存…"
            Storage.saveVideo(context, out, sanitizeFilename(request.name.substringBeforeLast('.', request.name)) + ".${request.format}")
        } finally { out.delete() }
    }

    fun cancel() { job?.cancel() }

    fun open(context: Context, share: Boolean) {
        val uri = outputState.value?.uri ?: return
        try {
            val mime = Storage.mimeOf(outputNameState.value)
            val intent = if (share) Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri)
                else Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(if (share) Intent.createChooser(intent, "分享文件") else intent)
        } catch (e: Exception) { statusState.value = "无法打开文件：${e.message}" }
    }
}
