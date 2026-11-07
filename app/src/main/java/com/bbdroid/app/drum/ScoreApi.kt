package com.bbdroid.app.drum

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ScoreApiException(val code: String, message: String, val httpStatus: Int) : IOException(message)

class ScoreApi(
    private val base: HttpUrl = ScoreLinks.SERVICE.toHttpUrl(),
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(100, TimeUnit.SECONDS).callTimeout(110, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build(),
) {
    private fun request(path: List<String>, task: ScoreTask? = null): Request.Builder {
        val url = base.newBuilder().apply { path.forEach { addPathSegment(it) } }.build()
        return Request.Builder().url(url).header("Accept", "application/json").header("Cache-Control", "no-store").apply {
            if (task != null) header("Authorization", "Bearer ${task.capability}")
        }
    }
    private fun post(builder: Request.Builder, json: JSONObject = JSONObject()): Request =
        builder.post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()

    suspend fun submit(source: String, options: ScoreOptions): ScoreTask {
        val normalized = ScoreLinks.normalize(source)
        val json = json(post(request(listOf("api", "tasks")), JSONObject().put("url", normalized).put("options", options.toJson())))
        return ScoreTask(json.getString("id"), json.getString("capability"), normalized)
    }
    suspend fun status(task: ScoreTask): ScoreTask = task.withStatus(json(request(listOf("api", "tasks", task.id), task).build()))
    suspend fun cancel(task: ScoreTask): ScoreTask = action(task, "cancel")
    suspend fun retry(task: ScoreTask): ScoreTask = action(task, "retry")
    private suspend fun action(task: ScoreTask, action: String): ScoreTask =
        task.withStatus(json(post(request(listOf("api", "tasks", task.id, action), task))))

    suspend fun pdf(task: ScoreTask, destination: File) {
        try {
            execute(request(listOf("api", "tasks", task.id, "pdf"), task).header("Accept", "application/pdf").build()) { response ->
                val body = response.body ?: throw IOException("PDF 响应为空")
                val limit = 64L * 1024 * 1024
                if (body.contentLength() > limit) throw IOException("PDF 超过手机端 64 MiB 下载限制")
                var count = 0L
                destination.outputStream().use { out -> body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        count += n
                        if (count > limit) throw IOException("PDF 超过手机端 64 MiB 下载限制")
                        out.write(buffer, 0, n)
                    }
                } }
                requirePdf(destination)
            }
        } catch (e: Exception) { destination.delete(); throw e }
    }

    data class Layout(val file: File, val pages: Int, val paper: String, val message: String)
    suspend fun layout(task: ScoreTask, paper: String, rows: Int?, destination: File): Layout {
        require(paper in setOf("A4", "A3", "A5") && (rows == null || rows in 1..16)) { "打印设置无效" }
        try {
            val json = json(post(request(listOf("api", "tasks", task.id, "layout"), task),
                JSONObject().put("paper", paper).put("rows", rows ?: JSONObject.NULL)), 32L * 1024 * 1024)
            val count = json.getInt("page_count")
            require(count in 1..64 && json.getJSONArray("pages").length() == count && json.getString("paper") == paper) { "排版结果不完整" }
            val bytes = android.util.Base64.decode(json.getString("pdf"), android.util.Base64.DEFAULT)
            destination.writeBytes(bytes)
            requirePdf(destination)
            return Layout(destination, count, paper, json.getString("message"))
        } catch (e: Exception) { destination.delete(); throw e }
    }

    private suspend fun json(request: Request, limit: Long = 1024 * 1024): JSONObject = execute(request) { response ->
        val body = response.body ?: throw IOException("服务返回了空响应")
        val source = body.source()
        source.request(limit + 1)
        require(source.buffer.size <= limit) { "服务响应超过大小限制" }
        JSONObject(source.readUtf8())
    }

    private suspend fun <T> execute(request: Request, read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val value = response.use {
                        if (!it.isSuccessful) {
                            val source = it.body?.source()
                            source?.request(8192)
                            val body = source?.buffer?.readUtf8(minOf(source.buffer.size, 8192)).orEmpty()
                            val error = runCatching { JSONObject(body).getJSONObject("error") }.getOrNull()
                            throw ScoreApiException(error?.optString("code").orEmpty(), error?.optString("message")?.take(600)
                                ?: "鼓谱服务暂时不可用（HTTP ${it.code}）", it.code)
                        }
                        read(it)
                    }
                    if (continuation.isActive) continuation.resume(value)
                } catch (e: Exception) { if (continuation.isActive) continuation.resumeWithException(e) }
            }
        })
    }

    private fun requirePdf(file: File) {
        val header = ByteArray(5)
        val n = file.inputStream().use { it.read(header) }
        require(n == 5 && header.contentEquals("%PDF-".toByteArray(Charsets.US_ASCII))) { "服务未返回有效的 PDF 文件" }
    }
}
