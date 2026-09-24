package com.bbdroid.app.bilibili

data class VideoInfo(
    val title: String,
    val desc: String,
    val pic: String,
    val pubTime: Long,
    val bvid: String,
    val aid: String,
    val pages: List<Page>,
    val owner: String = "",
    val seasonId: String = "",
    val seasonTitle: String = "",
)

data class Page(
    val index: Int,
    val aid: String,
    val cid: String,
    val title: String,
    val duration: Int,
    val dimension: String,
)

data class VideoTrack(
    val id: String,
    val dfn: String,
    val baseUrl: String,
    val codecs: String,
    val bandwidth: Long,
    val resolution: String,
    val frameRate: String,
    val urls: List<String> = emptyList(),
)

data class AudioTrack(
    val id: String,
    val baseUrl: String,
    val codecs: String,
    val bandwidth: Long,
    val urls: List<String> = emptyList(),
)
