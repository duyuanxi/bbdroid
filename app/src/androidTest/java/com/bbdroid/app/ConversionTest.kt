package com.bbdroid.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFprobeKit
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ConversionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun sample() = File.createTempFile("sample-", ".mp4", context.cacheDir).apply {
        instrumentation.context.assets.open("sample.mp4").use { input -> outputStream().use { input.copyTo(it) } }
    }
    private fun probe(file: File): JSONObject {
        val session = FFprobeKit.executeWithArguments(arrayOf("-v", "quiet", "-print_format", "json", "-show_format", "-show_streams", file.path))
        return JSONObject(session.output)
    }
    @Test fun nativeEngineLoads() { assertTrue(com.arthenica.ffmpegkit.FFmpegKitConfig.getVersion().isNotBlank()) }

    @Test fun convertsEveryOutputFormat() = runBlocking {
        val source = sample()
        try {
            for ((_, format) in Ffmpeg.formats) {
                val output = File(context.cacheDir, "test-format.$format")
                val codecs = Ffmpeg.defaultsFor(format)
                try {
                    val result = FFmpegEngine.run(Ffmpeg.buildArguments(FfmpegOptions(inputPath = source.path, outputPath = output.path,
                        videoCodec = codecs.first, audioCodec = codecs.second, duration = "0.25")))
                    assertTrue("$format: ${result.output}", result.success)
                    val streams = probe(output).getJSONArray("streams")
                    assertTrue("$format has no tracks", streams.length() > 0)
                    if (format in Ffmpeg.audioFormats) {
                        for (index in 0 until streams.length()) assertEquals("audio", streams.getJSONObject(index).getString("codec_type"))
                    }
                } finally { output.delete() }
            }
        } finally { source.delete() }
    }

    @Test fun appliesTrimResolutionAndFrameRate() = runBlocking {
        val source = sample(); val out = File(context.cacheDir, "test-trim.mp4")
        try {
            val result = FFmpegEngine.run(Ffmpeg.buildArguments(FfmpegOptions(inputPath = source.path, outputPath = out.path,
                startTime = "0.5", duration = "0.5", resolution = "160x120", fps = "12")))
            assertTrue(result.output, result.success)
            val json = probe(out)
            val video = json.getJSONArray("streams").getJSONObject(0)
            assertEquals(160, video.getInt("width")); assertEquals(120, video.getInt("height"))
            assertEquals("12/1", video.getString("r_frame_rate"))
            assertTrue(json.getJSONObject("format").getDouble("duration") < 0.8)
        } finally { source.delete(); out.delete() }
    }

    @Test fun supportsQuotedUnicodeFileNames() = runBlocking {
        val source = sample(); val quoted = File(context.cacheDir, "中文 \"引号\" 文件.mp4")
        val out = File(context.cacheDir, "输出 \"结果\".mp4")
        try {
            source.copyTo(quoted, overwrite = true)
            val result = FFmpegEngine.run(Ffmpeg.buildArguments(FfmpegOptions(inputPath = quoted.path, outputPath = out.path, videoCodec = "copy", audioCodec = "copy")))
            assertTrue(result.output, result.success); assertTrue(probe(out).getJSONArray("streams").length() == 2)
        } finally { source.delete(); quoted.delete(); out.delete() }
    }

    @Test fun savesAudioWithCorrectMimeType() = runBlocking {
        val source = sample(); val out = File(context.cacheDir, "test-audio.mp3")
        var saved: SavedFile? = null
        try {
            val result = FFmpegEngine.run(Ffmpeg.buildArguments(FfmpegOptions(inputPath = source.path, outputPath = out.path)))
            assertTrue(result.output, result.success)
            saved = Storage.saveVideo(context, out, "bbdroid-mime-test.mp3")
            assertEquals("audio/mpeg", context.contentResolver.getType(saved.uri!!))
            assertArrayEquals(out.readBytes(), context.contentResolver.openInputStream(saved.uri!!)!!.use { it.readBytes() })
        } finally { saved?.uri?.let { context.contentResolver.delete(it, null, null) }; source.delete(); out.delete() }
    }

    @Test fun repeatedImportsUseUniqueFilesAndPreserveNames() = runBlocking {
        val source = sample()
        val saved = Storage.saveVideo(context, source, "同名导入.mp4")
        val imported = mutableListOf<File>()
        try {
            repeat(5) { imported.add(copyToCache(context, saved.uri!!)) }
            assertEquals(5, imported.map { it.path }.distinct().size)
            assertEquals(1, imported.map { it.name }.distinct().size)
            imported.forEach { assertArrayEquals(source.readBytes(), it.readBytes()) }
        } finally {
            imported.forEach { it.delete(); it.parentFile?.delete() }
            saved.uri?.let { context.contentResolver.delete(it, null, null) }; source.delete()
        }
    }

    @Test fun cancellationStopsOnlyItsOwnSession() = runBlocking {
        val job = launch(Dispatchers.IO) {
            FFmpegEngine.run(listOf("-hide_banner", "-re", "-f", "lavfi", "-i", "sine=frequency=440", "-t", "60", "-f", "null", "-"))
        }
        delay(300)
        withTimeout(5000) { job.cancelAndJoin() }
        assertTrue(job.isCancelled)
        assertTrue(FFmpegEngine.run(listOf("-hide_banner", "-version")).success)
    }
}
