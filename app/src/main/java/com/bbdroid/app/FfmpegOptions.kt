package com.bbdroid.app

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
    // 显示名 to 值
    val formats = listOf(
        "MP4" to "mp4", "MKV" to "mkv", "MOV" to "mov", "AVI" to "avi",
        "FLV" to "flv", "WMV" to "wmv", "WebM" to "webm", "MPEG-TS" to "ts",
        "MP3" to "mp3", "AAC" to "aac", "FLAC" to "flac", "WAV" to "wav",
        "OGG" to "ogg", "M4A" to "m4a", "GIF" to "gif",
    )
    val videoCodecs = listOf(
        "H.264" to "libx264", "H.265/HEVC" to "libx265", "AV1" to "libaom-av1",
        "VP9" to "libvpx-vp9", "MPEG4" to "mpeg4",
        "H.264 硬件加速" to "h264_mediacodec", "H.265 硬件加速" to "hevc_mediacodec",
        "复制视频流" to "copy", "关闭视频" to "none",
    )
    val resolutions = listOf(
        "保持原尺寸" to "", "720P" to "1280x720", "1080P" to "1920x1080",
        "2K" to "2560x1440", "4K" to "3840x2160",
    )
    val fpsList = listOf("保持" to "", "15" to "15", "24" to "24", "30" to "30", "60" to "60")
    val audioCodecs = listOf(
        "AAC" to "aac", "MP3" to "mp3", "FLAC" to "flac", "Opus" to "libopus",
        "WAV" to "pcm_s16le", "复制音频流" to "copy", "关闭音频" to "none",
    )
    val channelsList = listOf("保持" to "", "单声道" to "1", "立体声" to "2", "5.1环绕" to "6")
    val sampleRates = listOf("保持" to "", "44100Hz" to "44100", "48000Hz" to "48000")

    private fun q(s: String) = "\"" + s + "\""

    fun buildCommand(o: FfmpegOptions): String {
        val parts = mutableListOf<String>()
        if (o.startTime.isNotEmpty()) parts.add("-ss " + o.startTime)
        parts.add("-i"); parts.add(q(o.inputPath))
        if (o.duration.isNotEmpty()) parts.add("-t " + o.duration)

        // 视频
        when {
            o.videoCodec == "none" -> parts.add("-vn")
            o.videoCodec == "copy" -> parts.add("-c:v copy")
            else -> {
                parts.add("-c:v " + o.videoCodec)
                if (o.videoCodec == "libx264" || o.videoCodec == "libx265") {
                    if (o.useCrf) {
                        parts.add("-crf " + o.crf)
                        parts.add("-preset medium")
                    } else {
                        parts.add("-b:v " + o.videoBitrate)
                    }
                } else if (o.videoCodec == "libaom-av1" || o.videoCodec == "libvpx-vp9") {
                    if (o.useCrf) {
                        parts.add("-crf " + o.crf)
                        parts.add("-b:v 0")
                    } else {
                        parts.add("-b:v " + o.videoBitrate)
                    }
                } else if (o.videoCodec == "h264_mediacodec" || o.videoCodec == "hevc_mediacodec") {
                    if (!o.useCrf && o.videoBitrate.isNotEmpty()) parts.add("-b:v " + o.videoBitrate)
                } else if (o.videoCodec == "mpeg4") {
                    parts.add("-q:v 5")
                }
            }
        }
        // 分辨率（复制/关闭时不变）
        if (o.resolution.isNotEmpty() && o.videoCodec != "copy" && o.videoCodec != "none") {
            parts.add("-vf scale=" + o.resolution)
        }
        // 帧率
        if (o.fps.isNotEmpty() && o.videoCodec != "copy" && o.videoCodec != "none") {
            parts.add("-r " + o.fps)
        }

        // 音频
        when {
            o.audioCodec == "none" -> parts.add("-an")
            o.audioCodec == "copy" -> parts.add("-c:a copy")
            else -> {
                parts.add("-c:a " + o.audioCodec)
                if (o.audioCodec != "flac" && o.audioCodec != "pcm_s16le") {
                    parts.add("-b:a " + o.audioBitrate)
                }
                if (o.channels.isNotEmpty()) parts.add("-ac " + o.channels)
                if (o.sampleRate.isNotEmpty()) parts.add("-ar " + o.sampleRate)
            }
        }
        // 音量
        if (o.volume.isNotEmpty() && o.audioCodec != "none" && o.audioCodec != "copy") {
            parts.add("-af volume=" + o.volume)
        }

        parts.add(q(o.outputPath))
        parts.add("-y")
        return parts.joinToString(" ")
    }
}
