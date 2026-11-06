package com.bbdroid.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadSessionTest {
    @Test fun stopWaitsForOldTaskCleanupBeforeUnlockingDownloads() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cleaning = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        val worker = launch(Dispatchers.Default) {
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) { cleaning.complete(Unit); allowCleanup.await() }
            }
        }
        try {
            started.await()
            withContext(Dispatchers.Main) {
                DownloadSession.downloadJobState.value = worker
                DownloadSession.downloadingState.value = true
                DownloadSession.upsertTask(DownloadTask("stop-test", "测试任务"))
                DownloadSession.stopAll()
                assertTrue(DownloadSession.stoppingState.value)
            }
            withTimeout(5000) { cleaning.await() }
            withContext(Dispatchers.Main) {
                assertTrue(DownloadSession.stoppingState.value)
                assertTrue(DownloadSession.downloadingState.value)
            }
            allowCleanup.complete(Unit)
            withTimeout(5000) {
                while (withContext(Dispatchers.Main) { DownloadSession.stoppingState.value }) delay(20)
            }
            withContext(Dispatchers.Main) {
                assertFalse(DownloadSession.downloadingState.value)
                assertNull(DownloadSession.downloadJobState.value)
                assertTrue(DownloadSession.tasks.isEmpty())
            }
            assertTrue(worker.isCancelled)
        } finally {
            allowCleanup.complete(Unit)
            worker.cancelAndJoin()
        }
    }
}
