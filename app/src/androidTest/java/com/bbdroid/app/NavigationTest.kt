package com.bbdroid.app

import android.graphics.Bitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class NavigationTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun tab(text: String) = ui.onNode(hasText(text) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
    @Before fun setup() = runBlocking {
        SettingsStore.setThemeMode(context, ThemeMode.LIGHT)
        SettingsStore.setGlassEnabled(context, true)
    }
    @After fun cleanup() {
        ui.runOnIdle {
            ToolboxImport.path = ""
            ToolboxImport.name = ""
            ToolboxImport.id = 0
            com.bbdroid.app.drum.ScoreImport.id = 0
            com.bbdroid.app.drum.ScoreImport.source = ""
            ToolboxDestination.id = 0
            ToolboxDestination.tool = 0
            ToolboxSession.outputState.value = null
            ToolboxSession.statusState.value = ""
        }
    }
    @Test fun tabsRetainInputAndExposeAdvancedOptions() {
        ui.waitForIdle(); screenshot("download")
        ui.onNodeWithText("链接 / BV 号 / av 号").performTextInput("BV1qt4y1X7TW")
        tab("工具箱").performClick()
        ui.onNodeWithText("开始转换").assertIsNotEnabled()
        screenshot("toolbox")
        ui.onNodeWithText("视频参数").performScrollTo().performClick()
        ui.onNodeWithText("视频编码").assertExists()
        tab("历史").performClick()
        ui.onNodeWithText("暂无下载记录").assertIsDisplayed()
        screenshot("history")
        tab("下载").performClick()
        ui.onNodeWithText("BV1qt4y1X7TW").assertExists()
        ui.onNodeWithContentDescription("设置").performClick()
        ui.onNodeWithText("液态玻璃皮肤").assertExists()
        screenshot("settings")
    }
    @Test fun conversionRunsFromUiAndSurvivesTabSwitch() {
        val source = File(context.cacheDir, "ui-conversion.mp4")
        instrumentation.context.assets.open("sample.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
        try {
            ui.runOnIdle { ToolboxImport.request(source.path, source.name) }
            ui.waitForIdle()
            ui.onNodeWithText("MP4").performClick()
            ui.onNodeWithText("MP3").performClick()
            ui.onNodeWithText("开始转换").performScrollTo().performClick()
            tab("历史").performClick()
            ui.waitUntil(30000) { !ToolboxSession.runningState.value && ToolboxSession.outputState.value != null }
            val saved = ToolboxSession.outputState.value!!
            assertEquals("audio/mpeg", context.contentResolver.getType(saved.uri!!))
            tab("工具箱").performClick()
            ui.onNodeWithText("打开文件").performScrollTo().assertIsDisplayed()
            screenshot("conversion-done")
            context.contentResolver.delete(saved.uri!!, null, null)
        } finally { source.delete() }
    }
    @Test fun darkGlassRemainsReadable() {
        runBlocking { SettingsStore.setThemeMode(context, ThemeMode.DARK) }
        ui.waitForIdle()
        ui.onNodeWithText("解析视频").assertIsDisplayed()
        screenshot("dark")
    }
    @Test fun downloadLinkOpensScoreToolWithoutLosingConversion() {
        ui.onNodeWithText("链接 / BV 号 / av 号").performTextInput("https://www.bilibili.com/video/BV1qt4y1X7TW?p=2")
        ui.onNodeWithText("将链接整理为鼓谱 PDF").performClick()
        ui.onNodeWithText("把鼓谱带到谱架上").assertIsDisplayed()
        ui.onNode(hasText("https://www.bilibili.com/video/BV1qt4y1X7TW?p=2") and hasText("B站鼓谱链接 / BV 号")).assertIsDisplayed()
        ui.onNodeWithText("音视频转换").performClick()
        ui.onNodeWithText("开始转换").assertExists()
    }
    @Test fun recreationKeepsEditedScoreLinkAndSelectedPage() {
        ui.onNodeWithText("链接 / BV 号 / av 号").performTextInput("BV1qt4y1X7TW")
        ui.onNodeWithText("将链接整理为鼓谱 PDF").performClick()
        ui.onNodeWithText("B站鼓谱链接 / BV 号").performTextReplacement("https://www.bilibili.com/video/BV1qt4y1X7TW?p=3")
        tab("历史").performClick()
        ui.activityRule.scenario.recreate()
        tab("历史").assertIsSelected()
        tab("工具箱").performClick()
        ui.onNode(hasText("https://www.bilibili.com/video/BV1qt4y1X7TW?p=3") and hasText("B站鼓谱链接 / BV 号")).assertIsDisplayed()
    }
    private fun screenshot(name: String) {
        ui.mainClock.advanceTimeBy(300)
        ui.waitForIdle()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
