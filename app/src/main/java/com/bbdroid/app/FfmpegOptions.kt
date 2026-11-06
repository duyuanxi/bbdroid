package com.bbdroid.app

data class FfmpegOptions(
    val inputPath: String = "", val outputPath: String = "",
    val videoCodec: String = "libx264", val useCrf: Boolean = true, val crf: String = "23",
    val videoBitrate: String = "2000k", val resolution: String = "", val fps: String = "",
    val audioCodec: String = "aac", val audioBitrate: String = "128k", val channels: String = "",
    val sampleRate: String = "", val volume: String = "", val startTime: String = "", val duration: String = "",
)

object Ffmpeg {
    val formats = listOf("MP4" to "mp4", "MKV" to "mkv", "MOV" to "mov", "AVI" to "avi", "FLV" to "flv", "WMV" to "wmv", "WebM" to "webm", "MPEG-TS" to "ts",
        "MP3" to "mp3", "AAC" to "aac", "FLAC" to "flac", "WAV" to "wav", "OGG" to "ogg", "M4A" to "m4a", "GIF" to "gif")
    val videoCodecs = listOf("H.264" to "libx264", "H.265 / HEVC" to "libx265", "AV1" to "libaom-av1", "VP9" to "libvpx-vp9", "MPEG4" to "mpeg4",
        "WMV" to "wmv2", "GIF" to "gif", "H.264 硬件加速" to "h264_mediacodec", "H.265 硬件加速" to "hevc_mediacodec", "复制视频流" to "copy", "关闭视频" to "none")
    val audioCodecs = listOf("AAC" to "aac", "MP3" to "libmp3lame", "FLAC" to "flac", "Opus" to "libopus", "Vorbis" to "libvorbis", "PCM" to "pcm_s16le", "WMA" to "wmav2", "复制音频流" to "copy", "关闭音频" to "none")
    val resolutions = listOf("保持原尺寸" to "", "720P" to "1280x720", "1080P" to "1920x1080", "2K" to "2560x1440", "4K" to "3840x2160")
    val fpsList = listOf("保持" to "", "15" to "15", "24" to "24", "30" to "30", "60" to "60")
    val channelsList = listOf("保持" to "", "单声道" to "1", "立体声" to "2", "5.1 环绕" to "6")
    val sampleRates = listOf("保持" to "", "44100 Hz" to "44100", "48000 Hz" to "48000")
    val audioFormats = setOf("mp3", "aac", "flac", "wav", "ogg", "m4a")

    fun videoCodecsFor(format: String): List<Pair<String, String>> {
        val allowed = when (format) {
            in audioFormats -> setOf("none")
            "gif" -> setOf("gif")
            "webm" -> setOf("libvpx-vp9", "libaom-av1", "copy", "none")
            "wmv" -> setOf("wmv2", "copy", "none")
            "flv" -> setOf("libx264", "h264_mediacodec", "copy", "none")
            else -> videoCodecs.map { it.second }.toSet() - setOf("gif", "wmv2")
        }
        return videoCodecs.filter { it.second in allowed }
    }
    fun audioCodecsFor(format: String): List<Pair<String, String>> {
        val allowed = when (format) {
            "gif" -> setOf("none")
            "mp3" -> setOf("libmp3lame", "copy")
            "aac", "m4a" -> setOf("aac", "copy")
            "flac" -> setOf("flac", "copy")
            "wav" -> setOf("pcm_s16le", "copy")
            "ogg" -> setOf("libvorbis", "libopus", "flac", "copy")
            "webm" -> setOf("libopus", "libvorbis", "copy", "none")
            "wmv" -> setOf("wmav2", "copy", "none")
            "flv" -> setOf("aac", "libmp3lame", "copy", "none")
            "mp4", "mov", "ts" -> setOf("aac", "libmp3lame", "copy", "none")
            else -> audioCodecs.map { it.second }.toSet() - "wmav2"
        }
        return audioCodecs.filter { it.second in allowed }
    }

    fun buildArguments(o: FfmpegOptions): List<String> {
        require(o.inputPath.isNotBlank() && o.outputPath.isNotBlank()) { "请选择输入文件和输出位置" }
        require(o.inputPath != o.outputPath) { "输出文件不能覆盖输入文件" }
        val format = o.outputPath.substringAfterLast('.', "").lowercase()
        require(format in formats.map { it.second }) { "不支持的输出格式：$format" }
        val v = if (format in audioFormats) "none" else if (format == "gif") "gif" else o.videoCodec
        val a = when (format) {
            "gif" -> "none"
            "mp3" -> if (o.audioCodec == "copy") "copy" else "libmp3lame"
            "aac", "m4a" -> if (o.audioCodec == "copy") "copy" else "aac"
            "flac" -> if (o.audioCodec == "copy") "copy" else "flac"
            "wav" -> if (o.audioCodec == "copy") "copy" else "pcm_s16le"
            else -> if (o.audioCodec == "mp3") "libmp3lame" else o.audioCodec
        }
        require(v != "none" || a != "none") { "视频和音频不能同时关闭" }
        require(videoCodecsFor(format).any { it.second == v }) { "此容器不支持所选视频编码，请更换编码或使用 MKV" }
        require(audioCodecsFor(format).any { it.second == a }) { "此容器不支持所选音频编码" }
        val args = mutableListOf("-y")
        if (o.startTime.isNotBlank()) { validateTime(o.startTime); args += listOf("-ss", o.startTime) }
        args += listOf("-i", o.inputPath)
        if (o.duration.isNotBlank()) { validateTime(o.duration); args += listOf("-t", o.duration) }
        if (v == "none") args += "-vn" else {
            args += listOf("-c:v", v)
            if (v != "copy") {
                when (v) {
                    "libx264", "libx265", "libaom-av1", "libvpx-vp9" -> {
                        if (o.useCrf) {
                            val max = if (v in setOf("libx264", "libx265")) 51 else 63
                            require(o.crf.toIntOrNull() in 0..max) { "CRF 应为 0–$max 的整数" }
                            args += listOf("-crf", o.crf)
                            if (v in setOf("libaom-av1", "libvpx-vp9")) args += listOf("-b:v", "0")
                        } else { validateBitrate(o.videoBitrate); args += listOf("-b:v", o.videoBitrate) }
                        if (v in setOf("libx264", "libx265")) args += listOf("-preset", "medium")
                    }
                    "h264_mediacodec", "hevc_mediacodec" -> { validateBitrate(o.videoBitrate); args += listOf("-b:v", o.videoBitrate) }
                    "mpeg4", "wmv2" -> args += listOf("-q:v", "5")
                }
                if (v in setOf("libx264", "libx265")) args += listOf("-pix_fmt", "yuv420p")
                if (o.resolution.isNotBlank()) {
                    require(Regex("[1-9][0-9]*x[1-9][0-9]*").matches(o.resolution)) { "分辨率格式错误" }
                    args += listOf("-vf", "scale=${o.resolution.replace('x', ':')}:force_original_aspect_ratio=decrease:force_divisible_by=2")
                }
                if (o.fps.isNotBlank()) { require((o.fps.toDoubleOrNull() ?: 0.0) in 1.0..240.0) { "帧率应为 1–240" }; args += listOf("-r", o.fps) }
            }
        }
        if (a == "none") args += "-an" else {
            args += listOf("-c:a", a)
            if (a != "copy") {
                if (a !in setOf("flac", "pcm_s16le")) { validateBitrate(o.audioBitrate); args += listOf("-b:a", o.audioBitrate) }
                if (o.channels.isNotBlank()) { require(o.channels in setOf("1", "2", "6")) { "声道参数错误" }; args += listOf("-ac", o.channels) }
                if (o.sampleRate.isNotBlank()) { require(o.sampleRate in setOf("44100", "48000")) { "采样率参数错误" }; args += listOf("-ar", o.sampleRate) }
                if (o.volume.isNotBlank()) { require((o.volume.toDoubleOrNull() ?: -1.0) in 0.0..3.0) { "音量应为 0–3" }; args += listOf("-af", "volume=${o.volume}") }
            }
        }
        if (format in setOf("mp4", "mov", "m4a")) args += listOf("-movflags", "+faststart")
        args += o.outputPath
        return args
    }
    private fun validateTime(time: String) { require(Regex("(?:[0-9]+:)?(?:[0-5]?[0-9]:)?[0-9]+(?:\\.[0-9]+)?").matches(time)) { "时间格式应为秒数或 时:分:秒" } }
    private fun validateBitrate(value: String) { require(Regex("[1-9][0-9]*(?:[kKmM])?").matches(value)) { "码率格式错误，例如 2000k" } }
}
