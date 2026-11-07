package com.bbdroid.app.drum

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

class ScoreApiTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val task = ScoreTask(TEST_ID, TEST_TOKEN, TEST_SOURCE)

    @Test fun normalizesShareMessagesAndPreservesPage() {
        assertEquals(TEST_SOURCE, ScoreLinks.normalize("【分享】$TEST_SOURCE&share_source=copy"))
        assertEquals("https://www.bilibili.com/video/BV1qt4y1X7TW", ScoreLinks.normalize("BV1qt4y1X7TW"))
        assertEquals("https://b23.tv/Abc123", ScoreLinks.normalize("分享 https://b23.tv/Abc123。"))
        assertThrows(IllegalArgumentException::class.java) { ScoreLinks.normalize("https://example.com/") }
    }

    @Test fun validatesExactOptionsContract() {
        val json = ScoreOptions(15.0, 75.0, listOf(5.0, 10.0, 95.0, 90.0), false).toJson()
        assertEquals(setOf("start_seconds", "end_seconds", "region_percent", "ignore_fixed_overlays"), json.keys().asSequence().toSet())
        assertEquals(15.0, json.getDouble("start_seconds"), 0.0)
        assertEquals(4, json.getJSONArray("region_percent").length())
        assertFalse(json.getBoolean("ignore_fixed_overlays"))
        assertThrows(IllegalArgumentException::class.java) { ScoreOptions(Double.NaN).toJson() }
        assertThrows(IllegalArgumentException::class.java) { ScoreOptions(5.0, 4.0).toJson() }
        assertThrows(IllegalArgumentException::class.java) { ScoreOptions(regionPercent = listOf(50.0, 0.0, 40.0, 100.0)).toJson() }
    }

    @Test fun usesOnlyTaskCapabilityAndSupportsCancelRetry() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = ScoreDispatcher(); server.start()
            val api = ScoreApi(server.url("/"))
            val created = api.submit(TEST_SOURCE, ScoreOptions())
            val post = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("POST", post.method); assertEquals("/api/tasks", post.path)
            assertNull(post.getHeader("Authorization")); assertNull(post.getHeader("Cookie"))
            assertEquals(TEST_SOURCE, JSONObject(post.body.readUtf8()).getString("url"))
            assertEquals("complete", api.status(created).state)
            val status = server.takeRequest(3, TimeUnit.SECONDS)!!
            assertEquals("Bearer $TEST_TOKEN", status.getHeader("Authorization")); assertNull(status.getHeader("Cookie"))
            assertEquals("cancelled", api.cancel(created).state)
            assertEquals("queued", api.retry(created).state)
        }
    }

    @Test fun handlesQuotaExpiryAndMismatchedTask() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = ScoreApi(server.url("/"))
            for ((status, code) in listOf(429 to "ip_busy", 410 to "expired")) {
                server.enqueue(jsonResponse(JSONObject().put("error", JSONObject().put("code", code).put("message", "明确的服务错误"))).setResponseCode(status).setChunkedBody("{\"error\":{\"code\":\"$code\",\"message\":\"明确的服务错误\"}}", 9))
                try { api.status(task); fail("应返回服务错误") } catch (e: ScoreApiException) { assertEquals(code, e.code); assertEquals(status, e.httpStatus) }
            }
            server.enqueue(jsonResponse(statusJson().put("id", "another-task")))
            try { api.status(task); fail("不能接受其他任务") } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun downloadsValidPdfAndRejectsHtmlOrIncompleteLayout() = runBlocking {
        MockWebServer().use { server ->
            server.start(); val api = ScoreApi(server.url("/"))
            val file = File(context.cacheDir, "score-api-test.pdf")
            try {
                server.enqueue(MockResponse().setBody(okio.Buffer().write(fixturePdf())).setHeader("Content-Type", "application/pdf"))
                api.pdf(task, file); assertEquals(2, readDocument(file).pages)
                server.enqueue(MockResponse().setBody("<html>unavailable</html>"))
                try { api.pdf(task, file); fail("不能保存 HTML 为 PDF") } catch (_: IllegalArgumentException) { }
                assertFalse(file.exists())
                server.enqueue(jsonResponse(JSONObject().put("page_count", 2).put("paper", "A4").put("pages", org.json.JSONArray()).put("pdf", "")))
                try { api.layout(task, "A4", null, file); fail("不能发布不完整排版") } catch (_: IllegalArgumentException) { }
                assertFalse(file.exists())
            } finally { file.delete() }
        }
    }

    @Test fun cancellationStopsInFlightRequest() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE)); server.start()
            val api = ScoreApi(server.url("/"))
            val pending = async(Dispatchers.IO) { api.status(task) }
            assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(3, TimeUnit.SECONDS) })
            withTimeout(3000) { pending.cancelAndJoin() }
            assertTrue(pending.isCancelled)
        }
    }
}
