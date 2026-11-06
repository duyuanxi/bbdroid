package com.bbdroid.app

import com.bbdroid.app.bilibili.BiliApi
import com.bbdroid.app.bilibili.DownloadControl
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class DownloadTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun parallelRangesPreserveAllBytes() = runBlocking {
        DownloadControl.reset()
        val source = ByteArray(4 * 1024 * 1024 + 11) { (it * 31).toByte() }
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(source)
            server.start()
            val output = File(temp.root, "source.mp4")
            var last = 0L
            BiliApi.downloadFileParallel(listOf(server.url("/media").toString()), output, onProgress = { count, total ->
                assertTrue(count >= last); assertTrue(count <= total); last = count
            })
            assertArrayEquals(source, output.readBytes())
            assertEquals(source.size.toLong(), last)
            assertEquals(1, temp.root.listFiles()!!.size)
        }
    }
    @Test fun wrongRangeFallsBackWithoutPublishingCorruptData() = runBlocking {
        DownloadControl.reset()
        val source = ByteArray(3 * 1024 * 1024 + 1) { it.toByte() }
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(source, wrongRange = true)
            server.start()
            val output = File(temp.root, "fallback.mp4")
            BiliApi.downloadFileParallel(listOf(server.url("/media").toString()), output)
            assertArrayEquals(source, output.readBytes())
            assertEquals(1, temp.root.listFiles()!!.size)
        }
    }
    @Test fun cancellationClosesInFlightNetworkRequests() = runBlocking {
        DownloadControl.reset()
        val source = ByteArray(3 * 1024 * 1024)
        MockWebServer().use { server ->
            server.dispatcher = rangeDispatcher(source, slow = true)
            server.start()
            val started = CompletableDeferred<Unit>()
            val output = File(temp.root, "cancel.mp4")
            val worker = async(Dispatchers.IO) {
                BiliApi.downloadFileParallel(listOf(server.url("/media").toString()), output, onProgress = { _, _ -> started.complete(Unit) })
            }
            withTimeout(5000) { started.await() }
            DownloadControl.cancel()
            try { withTimeout(3000) { worker.await() }; fail("取消后不能返回成功") }
            catch (_: CancellationException) { }
            assertFalse(output.exists())
            assertTrue(temp.root.listFiles()!!.isEmpty())
            DownloadControl.reset()
        }
    }
    private fun rangeDispatcher(source: ByteArray, wrongRange: Boolean = false, slow: Boolean = false) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            val range = request.getHeader("Range") ?: return MockResponse().setBody(Buffer().write(source))
            val limits = range.removePrefix("bytes=").split('-').map { it.toInt() }
            val start = limits[0]; val end = limits[1]
            val rangeHeader = if (wrongRange && end > 0) "bytes 0-0/${source.size}" else "bytes $start-$end/${source.size}"
            val response = MockResponse().setResponseCode(206).setHeader("Content-Range", rangeHeader)
                .setBody(Buffer().write(source, start, end - start + 1))
            if (slow && end > 0) response.throttleBody(1024, 100, TimeUnit.MILLISECONDS)
            return response
        }
    }
}
