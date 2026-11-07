package com.bbdroid.app.drum

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class ScoreSessionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun resumesPrivateTaskAndRetainsPreviousPdfWhenLayoutFails() = runBlocking {
        MockWebServer().use { server ->
            val dispatcher = ScoreDispatcher().apply { complete.set(false) }
            server.dispatcher = dispatcher; server.start()
            val storageKey = "score-test-${UUID.randomUUID()}"
            val api = ScoreApi(server.url("/"))
            var session = withContext(Dispatchers.Main) { ScoreSession(context, api, storageKey) }
            try {
                waitUntil { session.ready }
                withContext(Dispatchers.Main) { session.submit(TEST_SOURCE, ScoreOptions()) }
                waitUntil { session.selected?.state == "queued" && session.busy.isEmpty() }
                withContext(Dispatchers.Main) { session.close() }
                val catalog = File(context.noBackupFilesDir, "$storageKey-tasks.json")
                assertTrue(catalog.isFile); assertTrue(catalog.readText().contains(TEST_TOKEN))
                dispatcher.complete.set(true)
                session = withContext(Dispatchers.Main) { ScoreSession(context, api, storageKey) }
                waitUntil { session.document != null }
                val previous = session.document!!
                dispatcher.failLayout.set(true)
                withContext(Dispatchers.Main) { session.relayout("A5", 6) }
                waitUntil { session.busy.isEmpty() && session.error.isNotEmpty() }
                assertEquals(previous, session.document)
                withContext(Dispatchers.Main) { session.save() }
                waitUntil { session.busy.isEmpty() && session.selected!!.savedUri.isNotEmpty() }
                val uri = android.net.Uri.parse(session.selected!!.savedUri)
                try {
                    assertEquals("application/pdf", context.contentResolver.getType(uri))
                    assertArrayEquals(previous.file.readBytes(), context.contentResolver.openInputStream(uri)!!.use { it.readBytes() })
                } finally { context.contentResolver.delete(uri, null, null) }
            } finally {
                withContext(Dispatchers.Main) { session.close() }
                File(context.noBackupFilesDir, "$storageKey-tasks.json").delete()
                File(context.cacheDir, storageKey).deleteRecursively()
            }
        }
    }

    private suspend fun waitUntil(check: () -> Boolean) = withTimeout(10000) {
        while (!withContext(Dispatchers.Main) { check() }) delay(25)
    }
}
