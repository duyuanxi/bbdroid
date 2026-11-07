package com.bbdroid.app.drum

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Base64
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

internal const val TEST_ID = "score-test-01"
internal val TEST_TOKEN = "s".repeat(43)
internal const val TEST_SOURCE = "https://www.bilibili.com/video/BV1qt4y1X7TW?p=2"

internal fun fixturePdf(pages: Int = 2, paper: String = "A4"): ByteArray {
    val width = if (paper == "A5") 420 else 595
    val height = if (paper == "A5") 595 else 842
    val pdf = PdfDocument()
    try {
        for (index in 0 until pages) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, index + 1).create())
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f }
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawText("Score fixture - page ${index + 1}", 28f, 40f, paint)
            paint.textSize = 11f
            page.canvas.drawText("TEST ONLY / $paper / 4-4", 28f, 60f, paint)
            for (row in 0 until 5) {
                val y = 100f + row * 85
                paint.strokeWidth = 0.8f
                repeat(5) { line -> page.canvas.drawLine(28f, y + line * 7, width - 28f, y + line * 7, paint) }
                repeat(5) { note ->
                    val x = 75f + note * (width - 120f) / 5
                    page.canvas.drawOval(x, y + 10, x + 9, y + 16, paint)
                    page.canvas.drawLine(x + 8, y + 13, x + 8, y - 16, paint)
                }
            }
            pdf.finishPage(page)
        }
        return ByteArrayOutputStream().use { pdf.writeTo(it); it.toByteArray() }
    } finally { pdf.close() }
}

internal fun statusJson(state: String = "complete", pages: Int = 2): JSONObject = JSONObject()
    .put("id", TEST_ID).put("state", state).put("stage", if (state == "complete") "complete" else "queued")
    .put("source", JSONObject().put("url", TEST_SOURCE).put("bvid", "BV1qt4y1X7TW").put("page", 2))
    .put("queue_position", if (state == "queued") 2 else JSONObject.NULL)
    .put("expires_at", System.currentTimeMillis() / 1000.0 + 86400)
    .put("result", if (state != "complete") JSONObject.NULL else JSONObject().put("page_count", pages).put("warnings", JSONArray()))
    .put("error", JSONObject.NULL)

internal fun jsonResponse(json: JSONObject) = MockResponse().setHeader("Content-Type", "application/json").setBody(json.toString())

internal class ScoreDispatcher(val pdf: ByteArray = fixturePdf()) : Dispatcher() {
    val complete = AtomicBoolean(true)
    val failLayout = AtomicBoolean(false)
    override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
        "/api/tasks" -> jsonResponse(JSONObject().put("id", TEST_ID).put("capability", TEST_TOKEN).put("link", "/task/$TEST_ID#$TEST_TOKEN")).setResponseCode(201)
        "/api/tasks/$TEST_ID" -> jsonResponse(statusJson(if (complete.get()) "complete" else "queued"))
        "/api/tasks/$TEST_ID/cancel" -> jsonResponse(statusJson("cancelled"))
        "/api/tasks/$TEST_ID/retry" -> jsonResponse(statusJson("queued"))
        "/api/tasks/$TEST_ID/pdf" -> MockResponse().setHeader("Content-Type", "application/pdf").setBody(Buffer().write(pdf))
        "/api/tasks/$TEST_ID/layout" -> if (failLayout.get()) jsonResponse(JSONObject().put("error", JSONObject().put("code", "service_busy").put("message", "排版服务正忙"))).setResponseCode(503) else {
            val paper = JSONObject(request.body.readUtf8()).getString("paper")
            jsonResponse(JSONObject().put("page_count", 1).put("paper", paper).put("message", "已自动安排为每页 5 行")
                .put("pdf", Base64.encodeToString(fixturePdf(1, paper), Base64.NO_WRAP)).put("pages", JSONArray().put("unused-by-native-pdf-preview")))
        }
        else -> MockResponse().setResponseCode(404)
    }
}
