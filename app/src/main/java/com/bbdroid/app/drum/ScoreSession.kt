package com.bbdroid.app.drum

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import androidx.compose.runtime.*
import com.bbdroid.app.Storage
import com.bbdroid.app.sanitizeFilename
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.UUID

data class ScoreDocument(val file: File, val pages: Int, val paper: String = "A4", val message: String = "")

object DrumScores {
    private var session: ScoreSession? = null
    fun get(context: Context): ScoreSession = session ?: ScoreSession(context.applicationContext).also { session = it }
}

class ScoreSession(context: Context, private val api: ScoreApi = ScoreApi(), private val storageKey: String = "drum-score") {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val catalog = AtomicFile(File(context.noBackupFilesDir, "$storageKey-tasks.json"))
    private val root = File(context.cacheDir, storageKey)
    private var polling: Job? = null
    var ready by mutableStateOf(false); private set
    var tasks by mutableStateOf<List<ScoreTask>>(emptyList()); private set
    var selectedId by mutableStateOf(""); private set
    var busy by mutableStateOf(""); private set
    var error by mutableStateOf(""); private set
    var notice by mutableStateOf(""); private set
    var document by mutableStateOf<ScoreDocument?>(null); private set
    val selected get() = tasks.firstOrNull { it.id == selectedId }

    init {
        require(Regex("[A-Za-z0-9-]{1,64}").matches(storageKey))
        scope.launch {
            try {
                tasks = withContext(Dispatchers.IO) {
                    if (!catalog.baseFile.exists()) emptyList() else {
                        val array = JSONArray(catalog.openRead().use { it.readBytes().toString(Charsets.UTF_8) })
                        (0 until minOf(array.length(), 10)).map { ScoreTask.fromJson(array.getJSONObject(it)) }
                    }
                }
            } catch (_: Exception) { error = "本机鼓谱任务记录无法读取，可以重新提交或用完整任务链接恢复" }
            finally { ready = true }
            tasks.firstOrNull()?.let { select(it.id) }
        }
    }

    private suspend fun remember(task: ScoreTask) {
        val previous = tasks
        tasks = (listOf(task) + tasks.filter { it.id != task.id }).take(10)
        val removed = previous.filter { old -> tasks.none { it.id == old.id } }
        val json = JSONArray(tasks.map { it.toJson() }).toString()
        withContext(Dispatchers.IO) {
            val stream = catalog.startWrite()
            try { stream.write(json.toByteArray(Charsets.UTF_8)); catalog.finishWrite(stream) }
            catch (e: Exception) { catalog.failWrite(stream); throw e }
            removed.forEach { File(root, it.id).deleteRecursively() }
        }
    }

    fun submit(source: String, options: ScoreOptions) = operation("正在提交…") {
        require(ready) { "正在读取本机任务记录" }
        require(tasks.none { it.active }) { "已有鼓谱任务在处理，请先等待完成或取消" }
        val task = api.submit(source, options)
        // Keep credentials in noBackupFilesDir; never pass Bilibili cookies to this service.
        selectedId = task.id; document = null
        try { remember(task) } catch (_: IOException) { notice = "本机记录保存失败，请复制任务链接以免丢失结果" }
        poll(task)
    }

    fun restore(link: String) = operation("正在恢复任务…") {
        val uri = Uri.parse(link.trim())
        require(uri.scheme == "https" && uri.host == "drum.kenqia.top" && uri.pathSegments.size == 2 && uri.pathSegments[0] == "task") { "请填写原服务完整任务链接" }
        val id = uri.pathSegments[1]
        val token = uri.fragment ?: error("任务链接须包含 # 后的访问凭据")
        val task = api.status(ScoreTask(id, token, ""))
        remember(task); selectedId = task.id; document = null; poll(task)
    }

    fun select(id: String) {
        if (busy.isNotEmpty()) return
        val task = tasks.firstOrNull { it.id == id } ?: return
        selectedId = id; document = null; error = ""; notice = ""
        poll(task)
    }

    fun refresh() { if (busy.isEmpty()) selected?.let(::poll) }

    private fun poll(initial: ScoreTask) {
        polling?.cancel()
        polling = scope.launch {
            var task = initial
            val cached = File(root, "${task.id}/default.pdf")
            if (cached.isFile) runCatching { withContext(Dispatchers.IO) { readDocument(cached) } }.getOrNull()?.let { document = it }
            while (isActive && selectedId == task.id) {
                try {
                    val updated = api.status(task)
                    if (updated != task) remember(updated)
                    task = updated; error = ""
                    if (task.state == "complete") {
                        if (document == null) downloadDefault(task)
                        break
                    }
                    if (!task.active) break
                    delay(2000)
                } catch (e: CancellationException) { throw e }
                catch (e: ScoreApiException) {
                    error = e.message.orEmpty()
                    if (e.httpStatus in setOf(403, 404, 410)) {
                        remember(task.copy(state = if (e.httpStatus == 410) "expired" else "unavailable", errorCode = e.code, errorMessage = error))
                        break
                    }
                    delay(5000)
                } catch (e: Exception) {
                    error = if (task.state == "complete") "PDF 加载失败：${e.message}，可点击刷新重试" else "网络连接异常，稍后自动重连：${e.message}"
                    if (task.state == "complete") break
                    delay(5000)
                }
            }
        }
    }

    fun cancel() = operation("正在请求取消…") {
        val task = selected ?: return@operation
        polling?.cancelAndJoin()
        val updated = api.cancel(task)
        remember(updated); poll(updated)
    }

    fun retry() = operation("正在重试…") {
        val task = selected ?: return@operation
        require(task.retryable) { "当前任务不能重试" }
        polling?.cancelAndJoin()
        val updated = api.retry(task)
        remember(updated); document = null; poll(updated)
    }

    private suspend fun downloadDefault(task: ScoreTask) {
        val dir = File(root, task.id).apply { mkdirs() }
        val temporary = File(dir, "${UUID.randomUUID()}.part")
        try {
            api.pdf(task, temporary)
            val result = withContext(Dispatchers.IO) { readDocument(temporary) }
            require(result.pages == task.pageCount) { "PDF 与任务页数不一致" }
            currentCoroutineContext().ensureActive()
            val final = File(dir, "default.pdf")
            check(temporary.renameTo(final)) { "无法缓存 PDF" }
            document = result.copy(file = final)
        } finally { temporary.delete() }
    }

    fun relayout(paper: String, rows: Int?) = operation("正在重新排版…") {
        val task = selected ?: return@operation
        require(task.state == "complete") { "鼓谱还未整理完成" }
        polling?.cancelAndJoin()
        val temporary = File(File(root, task.id).apply { mkdirs() }, "layout-${UUID.randomUUID()}.pdf")
        val previous = document
        var committed = false
        try {
            val result = api.layout(task, paper, rows, temporary)
            val doc = withContext(Dispatchers.IO) { readDocument(temporary, paper, result.message) }
            require(doc.pages == result.pages) { "排版 PDF 与返回页数不一致" }
            document = doc; committed = true
            if (previous?.file?.name?.startsWith("layout-") == true) previous.file.delete()
        } finally { if (!committed) temporary.delete() }
    }

    fun restoreDefault() = operation("正在读取默认排版…") {
        val task = selected ?: return@operation
        val cached = File(root, "${task.id}/default.pdf")
        if (cached.isFile) document = withContext(Dispatchers.IO) { readDocument(cached) }
        else downloadDefault(task)
    }

    fun save() = operation("正在保存 PDF…") {
        val task = selected ?: return@operation
        val doc = document ?: error("请先等待 PDF 加载完成")
        val name = sanitizeFilename("鼓谱-${task.label}-${doc.paper}.pdf")
        val saved = withContext(Dispatchers.IO) { Storage.saveDocument(context, doc.file, name) }
        remember(task.copy(savedUri = saved.uri?.toString().orEmpty(), savedName = name))
        notice = "已保存 · ${saved.display}"
    }

    fun openSaved(share: Boolean) {
        val task = selected ?: return
        if (task.savedUri.isBlank()) return
        try {
            val uri = Uri.parse(task.savedUri)
            val intent = if (share) Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
                else Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(if (share) Intent.createChooser(intent, "分享鼓谱 PDF").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) else intent)
        } catch (_: Exception) { error = "无法打开已保存的 PDF，请确认文件仍存在且已安装 PDF 阅读器" }
    }

    private fun operation(label: String, block: suspend () -> Unit) {
        if (busy.isNotEmpty()) return
        busy = label; error = ""; notice = ""
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "请求失败，请稍后重试" }
            finally { busy = "" }
        }
    }

    fun close() { scope.cancel() }
}

internal fun readDocument(file: File, paper: String = "A4", message: String = ""): ScoreDocument {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer ->
            require(renderer.pageCount in 1..64) { "PDF 页数超出 1–64 页范围" }
            return ScoreDocument(file, renderer.pageCount, paper, message)
        }
    }
}

internal fun renderScorePage(document: ScoreDocument, page: Int): Bitmap =
    ParcelFileDescriptor.open(document.file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
        PdfRenderer(fd).use { renderer -> renderer.openPage(page).use { source ->
            require(source.width in 1..20000 && source.height in 1..20000) { "PDF 页面尺寸超出预览范围" }
            val scale = minOf(2f, 1400f / source.width, 2000f / source.height)
            val bitmap = Bitmap.createBitmap((source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                source.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (e: Exception) { bitmap.recycle(); throw e }
        } }
    }
