package com.bbdroid.app

import org.junit.Assert.*
import org.junit.Test

class FfmpegOptionsTest {
    private fun options(ext: String = "mp4") = FfmpegOptions(inputPath = "/cache/input.mp4", outputPath = "/cache/out.$ext")
    private fun List<String>.value(flag: String) = this[indexOf(flag) + 1]

    @Test fun fileNamesArePassedAsSingleArguments() {
        val input = "/cache/中文 \"双引号\" and 'single'.mp4"
        val output = "/cache/新 文件.mp4"
        val args = Ffmpeg.buildArguments(options().copy(inputPath = input, outputPath = output))
        assertEquals(input, args.value("-i"))
        assertEquals(output, args.last())
    }
    @Test fun mp3UsesAnAudioEncoderAndDisablesVideo() {
        val args = Ffmpeg.buildArguments(options("mp3"))
        assertTrue("-vn" in args)
        assertFalse("-c:v" in args)
        assertEquals("libmp3lame", args.value("-c:a"))
    }
    @Test fun losslessAudioDoesNotReceiveBitrate() {
        for ((format, encoder) in listOf("flac" to "flac", "wav" to "pcm_s16le")) {
            val args = Ffmpeg.buildArguments(options(format))
            assertEquals(encoder, args.value("-c:a"))
            assertFalse("-b:a" in args)
        }
    }
    @Test fun gifDoesNotReceiveAudioOrCrf() {
        val args = Ffmpeg.buildArguments(options("gif"))
        assertTrue("-an" in args)
        assertEquals("gif", args.value("-c:v"))
        assertFalse("-crf" in args)
    }
    @Test fun scalingUsesWidthHeightAndPreservesAspectRatio() {
        val args = Ffmpeg.buildArguments(options().copy(resolution = "1280x720"))
        assertEquals("scale=1280:720:force_original_aspect_ratio=decrease:force_divisible_by=2", args.value("-vf"))
    }
    @Test fun streamCopyDoesNotApplyFilters() {
        val args = Ffmpeg.buildArguments(options().copy(videoCodec = "copy", audioCodec = "copy", resolution = "1280x720", fps = "60", volume = "2"))
        assertFalse("-vf" in args); assertFalse("-af" in args); assertFalse("-r" in args); assertFalse("-crf" in args)
    }
    @Test fun webmUsesCompatibleCodecs() {
        val args = Ffmpeg.buildArguments(options("webm").copy(videoCodec = "libvpx-vp9", audioCodec = "libopus"))
        assertEquals("libvpx-vp9", args.value("-c:v"))
        assertEquals("libopus", args.value("-c:a"))
        assertEquals("0", args.value("-b:v"))
    }
    @Test(expected = IllegalArgumentException::class) fun webmRejectsH264() { Ffmpeg.buildArguments(options("webm")) }
    @Test fun hardwareAlwaysGetsABitrate() {
        val args = Ffmpeg.buildArguments(options().copy(videoCodec = "h264_mediacodec"))
        assertEquals("2000k", args.value("-b:v")); assertFalse("-crf" in args)
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsOverwritingInput() {
        Ffmpeg.buildArguments(options().copy(outputPath = "/cache/input.mp4"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsBothStreamsDisabled() {
        Ffmpeg.buildArguments(options().copy(videoCodec = "none", audioCodec = "none"))
    }
    @Test(expected = IllegalArgumentException::class) fun rejectsArgumentsInsideTime() {
        Ffmpeg.buildArguments(options().copy(startTime = "1 -i evil.mp4"))
    }
    @Test fun acceptsTimestampAndDecimalDuration() {
        val args = Ffmpeg.buildArguments(options().copy(startTime = "00:01:30", duration = "2.5"))
        assertEquals("00:01:30", args.value("-ss")); assertEquals("2.5", args.value("-t"))
    }
    @Test fun formatMenusHaveCompatibleDefaults() {
        for ((_, format) in Ffmpeg.formats) {
            Ffmpeg.buildArguments(options(format).copy(videoCodec = Ffmpeg.videoCodecsFor(format).first().second, audioCodec = Ffmpeg.audioCodecsFor(format).first().second))
        }
    }
}
