package com.bbdroid.app

import java.io.File

data class FfmpegOptions(
    val inputPath: String = "",
    val outputPath: String = "",
    val videoCodec: String = "libx264",
    val useCrf: Boolean = true,
    val crf: String = "23",
    val videoBitrate: String = "2000k",
    val resolution: String = "",
    val fps: String = "",
    val audioCodec: String = "aac",
    val audioBitrate: String = "128k",
    val channels: String = "",
    val sampleRate: String = "",
    val volume: String = "",
    val startTime: String = "",
    val duration: String = "",
)

object Ffmpeg {
    val formats = listOf(
        "MP4" to "mp4", "MKV" to "mkv", "MOV" to "mov", "AVI" to "avi",
        "FLV" to "flv", "WMV" to "wmv", "WebM" to "webm", "MPEG-TS" to "ts",
        "MP3" to "mp3", "AAC" to "aac", "FLAC" to "flac", "WAV" to "wav",
        "OGG" to "ogg", "M4A" to "m4a", "GIF" to "gif",
    )
    val videoCodecs = listOf(
        "H.264" to "libx264", "H.265 / HEVC" to "libx265", "AV1" to "libaom-av1",
        "VP9" to "libvpx-vp9", "MPEG-4" to "mpeg4", "WMV" to "wmv2", "GIF" to "gif",
        "H.264 硬件加速" to "h264_mediacodec", "H.265 硬件加速" to "hevc_mediacodec",
        "复制视频流" to "copy", "关闭视频" to "none",
    )
    val resolutions = listOf("保持原尺寸" to "", "720P" to "1280x720", "1080P" to "1920x1080", "2K" to "2560x1440", "4K" to "3840x2160")
    val fpsList = listOf("保持" to "", "15" to "15", "24" to "24", "30" to "30", "60" to "60")
    val audioCodecs = listOf(
        "AAC" to "aac", "MP3" to "libmp3lame", "FLAC" to "flac", "Opus" to "libopus",
        "WAV / PCM" to "pcm_s16le", "Vorbis" to "libvorbis", "WMA" to "wmav2",
        "复制音频流" to "copy", "关闭音频" to "none",
    )
    val channelsList = listOf("保持" to "", "单声道" to "1", "立体声" to "2", "5.1 环绕" to "6")
    val sampleRates = listOf("保持" to "", "44100 Hz" to "44100", "48000 Hz" to "48000")
    val audioFormats = setOf("mp3", "aac", "flac", "wav", "ogg", "m4a")

    fun defaultsFor(format: String): Pair<String, String> = when (format) {
        "mp3" -> "none" to "libmp3lame"
        "aac", "m4a" -> "none" to "aac"
        "flac" -> "none" to "flac"
        "wav" -> "none" to "pcm_s16le"
        "ogg" -> "none" to "libvorbis"
        "webm" -> "libvpx-vp9" to "libopus"
        "wmv" -> "wmv2" to "wmav2"
        "avi" -> "mpeg4" to "libmp3lame"
        "gif" -> "gif" to "none"
        else -> "libx264" to "aac"
    }

    fun buildArguments(options: FfmpegOptions): List<String> {
        require(options.inputPath.isNotBlank()) { "请先选择源文件" }
        require(options.outputPath.isNotBlank()) { "输出路径为空" }
        require(File(options.inputPath).absolutePath != File(options.outputPath).absolutePath) { "输出文件不能覆盖源文件" }
        val format = options.outputPath.substringAfterLast('.', "").lowercase()
        val defaults = defaultsFor(format)
        val audioOnly = format in audioFormats
        val gif = format == "gif"
        val video = if (audioOnly) "none" else if (gif) "gif" else options.videoCodec
        val audio = if (gif) "none" else if (audioOnly && options.audioCodec != "copy" && options.audioCodec != "none") defaults.second else options.audioCodec
        require(video != "none" || audio != "none") { "视频与音频不能同时关闭" }
        validateTime(options.startTime, "开始时间")
        validateTime(options.duration, "时长", positive = true)
        if (format == "webm") {
            require(video in setOf("none", "copy", "libvpx-vp9", "libaom-av1")) { "WebM 请选择 VP9／AV1 视频编码" }
            require(audio in setOf("none", "copy", "libopus", "libvorbis")) { "WebM 请选择 Opus／Vorbis 音频编码" }
        }
        val args = mutableListOf("-hide_banner", "-y")
        if (options.startTime.isNotBlank()) args.addAll(listOf("-ss", options.startTime.trim()))
        args.addAll(listOf("-i", options.inputPath))
        if (options.duration.isNotBlank()) args.addAll(listOf("-t", options.duration.trim()))
        when (video) {
            "none" -> args.add("-vn")
            "copy" -> args.addAll(listOf("-c:v", "copy"))
            else -> {
                args.addAll(listOf("-c:v", video))
                when (video) {
                    "libx264", "libx265", "libaom-av1", "libvpx-vp9" -> {
                        if (options.useCrf) {
                            val crf = options.crf.toIntOrNull()
                            require(crf != null && crf in 0..51) { "CRF 必须为 0 到 51" }
                            args.addAll(listOf("-crf", crf.toString()))
                            if (video == "libx264" || video == "libx265") args.addAll(listOf("-preset", "medium"))
                            else args.addAll(listOf("-b:v", "0"))
                        } else {
                            requireBitrate(options.videoBitrate)
                            args.addAll(listOf("-b:v", options.videoBitrate))
                        }
                    }
                    "h264_mediacodec", "hevc_mediacodec", "wmv2" -> {
                        requireBitrate(options.videoBitrate)
                        args.addAll(listOf("-b:v", options.videoBitrate))
                    }
                    "mpeg4" -> args.addAll(listOf("-q:v", "5"))
                }
                if (options.resolution.isNotBlank()) {
                    require(Regex("[1-9][0-9]*x[1-9][0-9]*").matches(options.resolution)) { "分辨率格式应为宽x高" }
                    args.addAll(listOf("-vf", "scale=" + options.resolution.replace('x', ':')))
                }
                if (options.fps.isNotBlank()) {
                    require((options.fps.toDoubleOrNull() ?: 0.0) > 0) { "帧率必须大于零" }
                    args.addAll(listOf("-r", options.fps))
                }
                if (video == "libx264") args.addAll(listOf("-pix_fmt", "yuv420p"))
            }
        }
        when (audio) {
            "none" -> args.add("-an")
            "copy" -> args.addAll(listOf("-c:a", "copy"))
            else -> {
                args.addAll(listOf("-c:a", audio))
                if (audio != "flac" && audio != "pcm_s16le") {
                    requireBitrate(options.audioBitrate)
                    args.addAll(listOf("-b:a", options.audioBitrate))
                }
                if (options.channels.isNotBlank()) {
                    require((options.channels.toIntOrNull() ?: 0) in 1..8) { "声道数应为 1 到 8" }
                    args.addAll(listOf("-ac", options.channels))
                }
                if (options.sampleRate.isNotBlank()) {
                    require((options.sampleRate.toIntOrNull() ?: 0) > 0) { "采样率必须大于零" }
                    args.addAll(listOf("-ar", options.sampleRate))
                }
                if (options.volume.isNotBlank()) {
                    val volume = options.volume.toDoubleOrNull()
                    require(volume != null && volume.isFinite() && volume >= 0) { "音量必须为非负数" }
                    args.addAll(listOf("-af", "volume=${options.volume}"))
                }
            }
        }
        if (format == "mp4" || format == "mov" || format == "m4a") args.addAll(listOf("-movflags", "+faststart"))
        args.add(options.outputPath)
        return args
    }

    fun buildCommand(options: FfmpegOptions): String = buildArguments(options).joinToString(" ") { value ->
        if (value.any { it.isWhitespace() || it == '"' || it == '\'' }) "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" else value
    }

    private fun requireBitrate(value: String) {
        require(Regex("[1-9][0-9]*[kKmM]?").matches(value)) { "码率格式不正确" }
    }

    private fun validateTime(value: String, label: String, positive: Boolean = false) {
        if (value.isBlank()) return
        val text = value.trim()
        require(Regex("[0-9]+(?:\\.[0-9]+)?|[0-9]+(?::[0-5][0-9]){1,2}(?:\\.[0-9]+)?").matches(text)) { "$label 格式应为秒或 时:分:秒" }
        val seconds = text.split(':').fold(0.0) { total, part -> total * 60 + part.toDouble() }
        require(seconds.isFinite() && (!positive || seconds > 0)) { "$label 必须大于零" }
    }
}
