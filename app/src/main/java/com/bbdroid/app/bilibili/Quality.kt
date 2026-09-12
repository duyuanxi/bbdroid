package com.bbdroid.app.bilibili

object Quality {
    private val dfnMap = mapOf(
        "127" to "8K 超高清", "126" to "杜比视界", "125" to "HDR 真彩",
        "120" to "4K 超清", "116" to "1080P 高帧率", "112" to "1080P 高码率",
        "80" to "1080P 高清", "74" to "720P 高帧率", "64" to "720P 高清",
        "48" to "720P 高清", "32" to "480P 清晰", "16" to "360P 流畅",
        "6" to "240P 流畅", "5" to "144P 流畅",
    )

    fun dfn(id: String): String = dfnMap[id] ?: id

    fun videoCodec(codecid: String): String = when (codecid) {
        "13" -> "AV1"
        "12" -> "HEVC"
        "7" -> "AVC"
        else -> "UNKNOWN"
    }

    fun audioCodec(codecs: String): String = when (codecs) {
        "mp4a.40.2", "mp4a.40.5" -> "M4A"
        "ec-3" -> "E-AC-3"
        "fLaC" -> "FLAC"
        else -> codecs
    }
}
