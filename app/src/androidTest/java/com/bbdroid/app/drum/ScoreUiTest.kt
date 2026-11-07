package com.bbdroid.app.drum

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.bbdroid.app.BbdroidTheme
import com.bbdroid.app.GlassSceneHost
import com.bbdroid.app.ThemeMode
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ScoreUiTest {
    @get:Rule val ui = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun submitsPreviewsReflowsAndSavesPdfFromNativeUi() {
        MockWebServer().use { server ->
            server.dispatcher = ScoreDispatcher(); server.start()
            val storageKey = "score-test-${UUID.randomUUID()}"
            lateinit var session: ScoreSession
            ui.setContent {
                session = androidx.compose.runtime.remember { ScoreSession(context, ScoreApi(server.url("/")), storageKey) }
                BbdroidTheme(ThemeMode.LIGHT) { GlassSceneHost(Modifier.fillMaxSize()) {
                    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) { ScorePanel(context, Modifier.fillMaxSize(), session) }
                } }
            }
            try {
                ui.waitUntil(10000) { session.ready }
                ui.onNodeWithText("B站鼓谱链接 / BV 号").performTextInput(TEST_SOURCE)
                ui.onNodeWithText("整理鼓谱").assertIsEnabled()
                screenshot("score-input")
                ui.onNodeWithText("整理鼓谱").performScrollTo().performClick()
                ui.waitUntil(15000) { session.document != null }
                ui.onNodeWithText("A4 打印预览").performScrollTo().assertIsDisplayed()
                ui.onNodeWithContentDescription("下一页").performScrollTo().performClick()
                ui.waitUntil(10000) { ui.onAllNodesWithContentDescription("鼓谱第 2 页，共 2 页").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithContentDescription("鼓谱第 2 页，共 2 页").performScrollTo()
                screenshot("score-preview")
                ui.onNodeWithText("A4").performScrollTo().performClick()
                ui.onNodeWithText("A5").performClick()
                ui.onNodeWithText("应用排版").performScrollTo().performClick()
                ui.waitUntil(15000) { session.document?.paper == "A5" && session.busy.isEmpty() }
                assertEquals(1, session.document!!.pages)
                ui.waitUntil(10000) { ui.onAllNodesWithContentDescription("鼓谱第 1 页，共 1 页").fetchSemanticsNodes().isNotEmpty() }
                ui.onNodeWithText("A5 打印预览").performScrollTo().assertIsDisplayed()
                ui.onNodeWithContentDescription("下一页").assertIsNotEnabled()
                screenshot("score-layout")
                ui.onNodeWithText("保存 PDF").performScrollTo().performClick()
                ui.waitUntil(10000) { session.selected?.savedUri?.isNotEmpty() == true && session.busy.isEmpty() }
                val uri = android.net.Uri.parse(session.selected!!.savedUri)
                try { assertEquals("application/pdf", context.contentResolver.getType(uri)) }
                finally { context.contentResolver.delete(uri, null, null) }
                val output = File(context.getExternalFilesDir(null), "score-validation").apply { mkdirs() }
                session.document!!.file.copyTo(File(output, "score-a5.pdf"), overwrite = true)
                File(output, "score-a4.pdf").writeBytes(fixturePdf())
            } finally {
                ui.runOnIdle { session.close() }
                File(context.noBackupFilesDir, "$storageKey-tasks.json").delete()
                File(context.cacheDir, storageKey).deleteRecursively()
            }
        }
    }

    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(300); ui.waitForIdle()
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
