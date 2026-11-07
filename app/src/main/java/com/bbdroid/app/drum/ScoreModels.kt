package com.bbdroid.app.drum

import org.json.JSONArray
import org.json.JSONObject

/** HTTP contract verified against kenqia/bilibili-drum-score-to-pdf, 7327301. */
data class ScoreOptions(
    val startSeconds: Double = 0.0,
    val endSeconds: Double? = null,
    val regionPercent: List<Double>? = null,
    val ignoreFixedOverlays: Boolean = true,
) {
    fun toJson(): JSONObject {
        require(startSeconds.isFinite() && startSeconds >= 0) { "开始时间须为不小于零的秒数" }
        require(endSeconds == null || (endSeconds.isFinite() && endSeconds > startSeconds)) { "结束时间须大于开始时间" }
        regionPercent?.let {
            require(it.size == 4 && it.all { n -> n.isFinite() && n in 0.0..100.0 } && it[0] < it[2] && it[1] < it[3]) {
                "谱区按左、上、右、下填写 0–100%，右、下须分别大于左、上"
            }
        }
        return JSONObject().put("start_seconds", startSeconds).put("end_seconds", endSeconds ?: JSONObject.NULL)
            .put("region_percent", regionPercent?.let(::JSONArray) ?: JSONObject.NULL)
            .put("ignore_fixed_overlays", ignoreFixedOverlays)
    }
}

object ScoreLinks {
    const val SERVICE = "https://drum.kenqia.top/"
    const val PROJECT = "https://github.com/kenqia/bilibili-drum-score-to-pdf"
    // Adapted from the upstream web client's normalizeLink (MIT, kenqia).
    fun normalize(text: String): String {
        val value = text.trim()
        val bv = Regex("(?:bilibili\\.com/video/|^|[^A-Za-z0-9])(BV[0-9A-Za-z]{10})(?![0-9A-Za-z])").find(value)
        if (bv != null) {
            val page = Regex("[?&]p=([1-9][0-9]{0,5})(?![0-9])").find(value)?.groupValues?.get(1)
            return "https://www.bilibili.com/video/${bv.groupValues[1]}" + (page?.let { "?p=$it" } ?: "")
        }
        val short = Regex("(?:^|[\\s（(【])(?:https?://)?b23\\.tv/([0-9A-Za-z]{1,64})(?=$|[\\s/?#）)】。，！!])", RegexOption.IGNORE_CASE).find(value)
        if (short != null) return "https://b23.tv/${short.groupValues[1]}"
        throw IllegalArgumentException("请粘贴 BV 号、完整 B站视频链接或 b23.tv 分享链接")
    }
}

data class ScoreTask(
    val id: String,
    val capability: String,
    val sourceUrl: String,
    val state: String = "queued",
    val stage: String = "queued",
    val queuePosition: Int? = null,
    val pageCount: Int = 0,
    val expiresAt: Double? = null,
    val errorCode: String = "",
    val errorMessage: String = "",
    val warnings: List<String> = emptyList(),
    val savedUri: String = "",
    val savedName: String = "",
) {
    init {
        require(Regex("[A-Za-z0-9_-]{1,128}").matches(id)) { "任务 ID 无效" }
        require(Regex("[A-Za-z0-9_-]{43}").matches(capability)) { "任务凭据无效" }
    }
    val active get() = state in setOf("queued", "running", "cancelling", "finishing")
    val retryable get() = state in setOf("failed", "cancelled", "interrupted") && errorCode != "unsupported_source"
    val link get() = "${ScoreLinks.SERVICE}task/$id#$capability"
    val label get() = sourceUrl.substringAfter("/video/", sourceUrl.substringAfter("b23.tv/")).replace("?p=", " · P")

    fun withStatus(json: JSONObject): ScoreTask {
        require(json.getString("id") == id) { "服务返回了不匹配的任务" }
        val newState = json.getString("state")
        require(newState in setOf("queued", "running", "cancelling", "finishing", "complete", "failed", "cancelled", "interrupted")) { "服务返回了无法识别的任务状态" }
        val result = json.optJSONObject("result")
        val count = result?.getInt("page_count") ?: 0
        if (newState == "complete") require(count in 1..64) { "服务返回的 PDF 页数无效" }
        val error = json.optJSONObject("error")
        val notices = result?.optJSONArray("warnings")
        return copy(state = newState, stage = json.getString("stage"),
            sourceUrl = json.getJSONObject("source").getString("url"),
            queuePosition = if (json.isNull("queue_position")) null else json.getInt("queue_position"),
            expiresAt = if (json.isNull("expires_at")) null else json.getDouble("expires_at"), pageCount = count,
            errorCode = error?.getString("code").orEmpty(), errorMessage = error?.getString("message").orEmpty(),
            warnings = if (notices == null) emptyList() else (0 until notices.length()).map { notices.getJSONObject(it).getString("message") })
    }

    fun toJson(): JSONObject = JSONObject().put("id", id).put("capability", capability).put("source", sourceUrl)
        .put("state", state).put("stage", stage).put("pages", pageCount).put("expires", expiresAt ?: JSONObject.NULL)
        .put("errorCode", errorCode).put("errorMessage", errorMessage).put("warnings", JSONArray(warnings))
        .put("savedUri", savedUri).put("savedName", savedName)

    companion object {
        fun fromJson(json: JSONObject): ScoreTask {
            val warnings = json.getJSONArray("warnings")
            return ScoreTask(json.getString("id"), json.getString("capability"), json.getString("source"),
                state = json.getString("state"), stage = json.getString("stage"), pageCount = json.getInt("pages"),
                expiresAt = if (json.isNull("expires")) null else json.getDouble("expires"),
                errorCode = json.getString("errorCode"), errorMessage = json.getString("errorMessage"),
                warnings = (0 until warnings.length()).map(warnings::getString),
                savedUri = json.getString("savedUri"), savedName = json.getString("savedName"))
        }
    }
}

fun ScoreTask.title(): String = when (state) {
    "queued" -> "正在排队"
    "running" -> "正在整理鼓谱"
    "cancelling" -> "正在停止"
    "finishing" -> "正在保存结果"
    "complete" -> "鼓谱整理完成"
    "failed" -> "整理失败"
    "cancelled" -> "已取消"
    "interrupted" -> "处理被中断"
    "expired" -> "结果已过期"
    "unavailable" -> "任务不可用"
    else -> "读取任务"
}

fun ScoreTask.description(): String = when {
    state == "queued" -> queuePosition?.let { if (it > 1) "前面还有 ${it - 1} 个任务" else "即将开始处理" } ?: "正在等待服务处理"
    state == "complete" -> "$pageCount 页 A4 · 可预览、保存或重新排版"
    else -> when (stage) {
        "preflight", "starting", "source_wait", "cache_hit" -> "正在确认来源与准备视频"
        "downloading" -> "正在获取公开视频"
        "probing", "sampling" -> "正在读取视频中的谱面"
        "registering", "composing" -> "正在对齐画面并拼接谱行"
        "expert_fallback" -> "正在使用备用方法，完成后请仔细核对谱面"
        "paginating" -> "正在将完整谱行排成打印页面"
        "cancelling" -> "正在取消并清理临时文件"
        else -> errorMessage
    }
}
