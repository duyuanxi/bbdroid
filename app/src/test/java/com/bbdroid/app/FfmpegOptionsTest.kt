package com.bbdroid.app

import org.junit.Assert.*
import org.junit.Test

class FfmpegOptionsTest {
    @Test fun mp3OutputUsesAudioOnlyMp3Encoding() {
        val command = Ffmpeg.buildCommand(FfmpegOptions(inputPath = "/cache/source.mp4", outputPath = "/cache/out.mp3"))
        assertTrue(command, command.contains("-vn"))
        assertTrue(command, command.contains("libmp3lame"))
        assertFalse(command, command.contains("-c:v"))
    }
    @Test fun audioContainersUseCompatibleEncoders() {
        for ((format, codec) in mapOf("mp3" to "libmp3lame", "m4a" to "aac", "aac" to "aac", "wav" to "pcm_s16le", "flac" to "flac", "ogg" to "libvorbis")) {
            val args = Ffmpeg.buildArguments(FfmpegOptions(inputPath = "in.mp4", outputPath = "out.$format"))
            assertTrue(args.contains("-vn"))
            assertEquals(codec, args[args.indexOf("-c:a") + 1])
        }
    }
    @Test fun fileNamesRemainOneArgument() {
        val input = "/cache/中文 \"引号\" 输入.mp4"
        val output = "/cache/输出 文件.mp4"
        val args = Ffmpeg.buildArguments(FfmpegOptions(inputPath = input, outputPath = output))
        assertEquals(input, args[args.indexOf("-i") + 1])
        assertEquals(output, args.last())
    }
    @Test fun copyModeDoesNotApplyFilters() {
        val args = Ffmpeg.buildArguments(FfmpegOptions(inputPath = "in.mp4", outputPath = "out.mkv", videoCodec = "copy", audioCodec = "copy", resolution = "1280x720", fps = "60", volume = "2"))
        assertFalse(args.contains("-vf")); assertFalse(args.contains("-af")); assertFalse(args.contains("-r"))
    }
    @Test fun validatesTimesAndPreventsOverwrite() {
        assertThrows(IllegalArgumentException::class.java) { Ffmpeg.buildArguments(FfmpegOptions(inputPath = "in.mp4", outputPath = "in.mp4")) }
        for (value in listOf("-1", "abc", "00:99:00", "1 -y")) {
            assertThrows(IllegalArgumentException::class.java) { Ffmpeg.buildArguments(FfmpegOptions(inputPath = "in.mp4", outputPath = "out.mp4", startTime = value)) }
        }
        assertThrows(IllegalArgumentException::class.java) { Ffmpeg.buildArguments(FfmpegOptions(inputPath = "in.mp4", outputPath = "out.mp4", duration = "0")) }
    }
    @Test fun containerPresetsMatchWebmAndGif() {
        assertEquals("libvpx-vp9" to "libopus", Ffmpeg.defaultsFor("webm"))
        val gif = Ffmpeg.buildArguments(FfmpegOptions(inputPath = "in.mp4", outputPath = "out.gif"))
        assertTrue(gif.contains("-an")); assertEquals("gif", gif[gif.indexOf("-c:v") + 1])
    }
    @Test fun sanitizesFileNamesWithoutLosingExtension() {
        assertFalse(sanitizeFilename("../a\u0000b:c?.mp4").any { it == '/' || it == '\\' || it.code < 32 })
        assertTrue(sanitizeFilename("x".repeat(300) + ".mp4").endsWith(".mp4"))
        assertEquals("video", sanitizeFilename(".."))
    }
    @Test fun savesCorrectMimeForEveryFormat() {
        assertEquals("audio/mpeg", Storage.mimeOf("sample.mp3"))
        assertEquals("audio/mp4", Storage.mimeOf("sample.m4a"))
        assertEquals("video/webm", Storage.mimeOf("sample.webm"))
        assertEquals("image/gif", Storage.mimeOf("sample.gif"))
    }
}
