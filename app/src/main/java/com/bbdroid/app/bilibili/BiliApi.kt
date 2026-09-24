package com.bbdroid.app.bilibili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicLongArray

// 全局下载取消/暂停开关（停止按钮、暂停/继续按钮）
object DownloadControl {
    @kotlin.jvm.Volatile
    var cancelled = false
    @kotlin.jvm.Volatile
    var paused = false
    fun reset() { cancelled = false; paused = false }
    fun cancel() { cancelled = true }
    fun pause() { paused = true }
    fun resume() { paused = false }
}

object BiliApi {
    var cookie: String = ""
    private var wbiKey: String = ""
    private var buvid3: String = ""
    private var buvid4: String = ""

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // 并行分段下载用：整个请求最长 45s，卡住就超时回退
    private val parallelClient = client.newBuilder().callTimeout(45, TimeUnit.SECONDS).build()

    private fun get(url: String): String {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://www.bilibili.com/")
        val ch = buildCookieHeader()
        if (ch.isNotEmpty()) b.header("Cookie", ch)
        val resp = client.newCall(b.build()).execute()
        return resp.use {
            if (!it.isSuccessful) throw Exception("HTTP " + it.code)
            it.body?.string() ?: ""
        }
    }

    private fun buildCookieHeader(): String {
        val parts = mutableListOf<String>()
        if (buvid3.isNotEmpty()) parts.add("buvid3=" + buvid3)
        if (buvid4.isNotEmpty()) parts.add("buvid4=" + buvid4)
        if (cookie.isNotEmpty()) parts.add(cookie)
        return parts.joinToString("; ")
    }

    // 获取设备指纹 buvid3/buvid4，避免风控 412
    private fun ensureBuvid() {
        if (buvid3.isNotEmpty()) return
        try {
            val json = JSONObject(get("https://api.bilibili.com/x/frontend/finger/spi"))
            val data = json.optJSONObject("data")
            if (data != null) {
                buvid3 = data.optString("b_3", "")
                buvid4 = data.optString("b_4", "")
            }
        } catch (e: Exception) {
            // 拿不到 buvid 也不致命
        }
    }

    // 短链接(b23.tv 等)跟随重定向得到真实地址（手动跟随 Location，兼容多级跳转）
    private fun resolveFinalUrl(url: String): String {
        var current = url
        val noRedirect = client.newBuilder().followRedirects(false).build()
        for (i in 0 until 6) {
            val req = Request.Builder().url(current)
                .header("User-Agent", userAgent)
                .header("Referer", "https://www.bilibili.com/")
                .build()
            noRedirect.newCall(req).execute().use { resp ->
                val loc = resp.header("Location")
                if (resp.code in 300..399 && loc != null) {
                    current = if (loc.startsWith("http")) loc
                    else java.net.URL(java.net.URL(current), loc).toString()
                } else {
                    return current
                }
            }
        }
        return current
    }

    // 校验接口返回，code != 0 时抛出带原因的异常
    private fun apiData(json: JSONObject, context: String): JSONObject {
        val code = json.optInt("code", -1)
        if (code != 0) {
            val msg = json.optString("message", "")
            throw Exception(context + " 失败(code=" + code + " " + msg + ")")
        }
        return json.optJSONObject("data")
            ?: throw Exception(context + " 返回里没有 data")
    }

    private fun updateWbiKey() {
        try {
            val nav = JSONObject(get("https://api.bilibili.com/x/web-interface/nav"))
            val data = nav.optJSONObject("data")
            val wbi = data?.optJSONObject("wbi_img")
            if (wbi != null) {
                wbiKey = WbiSign.mixinKeyFrom(wbi.optString("img_url"), wbi.optString("sub_url"))
            }
        } catch (e: Exception) {
            // 拿不到 wbi 也不致命，部分接口仍可用
            wbiKey = ""
        }
    }

    // 从输入里提取 bvid 或 aid：支持完整链接 / 短链接 / BV号 / av号 / 纯数字 / 带多余文字的分享文本
    private fun extractId(input: String): Pair<String, String> {
        val s = input.trim()

        // 1. 文本里的链接优先（分享文本常带链接）
        val urlMatch = Regex("https?://[^ ]+").find(s)
        if (urlMatch != null) {
            var url = urlMatch.value
            if (url.contains("b23.tv") || url.contains("bili2233.cn") || url.contains("bili22.cn")) {
                url = try { resolveFinalUrl(url) } catch (e: Exception) { url }
            }
            Regex("BV[0-9A-Za-z]{10}").find(url)?.let { return "bvid" to it.value }
            Regex("av([0-9]+)", RegexOption.IGNORE_CASE).find(url)?.let { return "aid" to it.groupValues[1] }
            return "" to ""
        }

        // 2. 直接是 BV 号
        Regex("BV[0-9A-Za-z]{10}").find(s)?.let { return "bvid" to it.value }

        // 3. av 号
        Regex("av([0-9]+)", RegexOption.IGNORE_CASE).find(s)?.let { return "aid" to it.groupValues[1] }

        // 4. 纯数字
        if (s.matches(Regex("[0-9]+"))) return "aid" to s

        return "" to ""
    }

    private fun fetchVideoInfo(type: String, id: String): VideoInfo {
        val json = JSONObject(get("https://api.bilibili.com/x/web-interface/view?" + type + "=" + id))
        val data = apiData(json, "获取视频信息")
        val title = data.optString("title", "").trim()
        val desc = data.optString("desc", "").trim()
        val pic = data.optString("pic", "")
        val pubTime = data.optLong("pubdate", 0)
        val bvid = data.optString("bvid", "")
        val aid = data.optLong("aid", 0).toString()
        val pages = mutableListOf<Page>()
        val arr = data.optJSONArray("pages")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val dim = p.optJSONObject("dimension")
                val dimStr = if (dim != null) dim.optInt("width").toString() + "x" + dim.optInt("height") else ""
                pages.add(Page(
                    index = p.optInt("page", 0),
                    aid = aid,
                    cid = p.optLong("cid", 0).toString(),
                    title = p.optString("part", "").trim(),
                    duration = p.optInt("duration", 0),
                    dimension = dimStr,
                ))
            }
        }
        val ugc = data.optJSONObject("ugc_season")
        val seasonId = if (ugc != null) ugc.optLong("id", 0).toString() else ""
        val seasonTitle = if (ugc != null) ugc.optString("title", "") else ""
        val owner = data.optJSONObject("owner")?.optString("name", "") ?: ""
        return VideoInfo(title, desc, pic, pubTime, bvid, aid, pages, owner, seasonId, seasonTitle)
    }

    fun fetchTracks(aid: String, cid: String): Pair<List<VideoTrack>, List<AudioTrack>> {
        val params = "avid=" + aid + "&cid=" + cid +
            "&fnval=4048&fnver=0&fourk=1&otype=json&qn=0" +
            (if (cookie.isEmpty()) "&try_look=1" else "") +
            "&wts=" + (System.currentTimeMillis() / 1000)
        val url = "https://api.bilibili.com/x/player/wbi/playurl?" + WbiSign.sign(params, wbiKey)
        val json = JSONObject(get(url))
        val data = apiData(json, "获取视频流")
        val dash = data.optJSONObject("dash")
        val videos = mutableListOf<VideoTrack>()
        val audios = mutableListOf<AudioTrack>()
        if (dash != null) {
            val vArr = dash.optJSONArray("video")
            if (vArr != null) {
                for (i in 0 until vArr.length()) {
                    val v = vArr.getJSONObject(i)
                    val id = v.optString("id")
                    val u = v.optString("base_url", "")
                    val backup = v.optJSONArray("backup_url")
                    val baseUrl = if (u.isNotEmpty()) u else (backup?.optString(0) ?: "")
                    val urls = mutableListOf<String>()
                    if (u.isNotEmpty()) urls.add(u)
                    if (backup != null) for (j in 0 until backup.length()) { val bu = backup.optString(j); if (bu.isNotEmpty()) urls.add(bu) }
                    videos.add(VideoTrack(
                        id = id,
                        dfn = Quality.dfn(id),
                        baseUrl = baseUrl,
                        codecs = Quality.videoCodec(v.optString("codecid", "")),
                        bandwidth = v.optLong("bandwidth", 0) / 1000,
                        resolution = v.optInt("width").toString() + "x" + v.optInt("height"),
                        frameRate = v.optString("frame_rate", ""),
                        urls = urls,
                    ))
                }
            }
            val aArr = dash.optJSONArray("audio")
            if (aArr != null) {
                for (i in 0 until aArr.length()) {
                    val a = aArr.getJSONObject(i)
                    val id = a.optString("id")
                    val u = a.optString("base_url", "")
                    val backup = a.optJSONArray("backup_url")
                    val baseUrl = if (u.isNotEmpty()) u else (backup?.optString(0) ?: "")
                    val urls = mutableListOf<String>()
                    if (u.isNotEmpty()) urls.add(u)
                    if (backup != null) for (j in 0 until backup.length()) { val bu = backup.optString(j); if (bu.isNotEmpty()) urls.add(bu) }
                    audios.add(AudioTrack(
                        id = id,
                        baseUrl = baseUrl,
                        codecs = Quality.audioCodec(a.optString("codecs", "")),
                        bandwidth = a.optLong("bandwidth", 0) / 1000,
                        urls = urls,
                    ))
                }
            }
        }
        return videos to audios
    }

    // 下载文件（带 Referer，B站 CDN 需要），onProgress 回调 (已下载字节, 总字节，-1 表示未知)
    fun downloadFile(url: String, dest: File, onProgress: ((Long, Long) -> Unit)? = null) {
        val b = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://www.bilibili.com/")
        val ch = buildCookieHeader()
        if (ch.isNotEmpty()) b.header("Cookie", ch)
        client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("下载失败 HTTP " + resp.code)
            dest.parentFile?.mkdirs()
            val total = resp.body?.contentLength() ?: -1L
            dest.outputStream().use { out ->
                resp.body?.byteStream()?.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var read = input.read(buf)
                    while (read != -1) {
                        if (DownloadControl.cancelled) throw Exception("已取消下载")
                        while (DownloadControl.paused && !DownloadControl.cancelled) Thread.sleep(100)
                        out.write(buf, 0, read)
                        downloaded += read
                        onProgress?.invoke(downloaded, total)
                        read = input.read(buf)
                    }
                }
            }
        }
    }

    // 取文件大小（用 1 字节 Range 请求读 Content-Range）
    private fun getLength(url: String): Long {
        return try {
            val b = Request.Builder().url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", userAgent)
                .header("Referer", "https://www.bilibili.com/")
            val ch = buildCookieHeader()
            if (ch.isNotEmpty()) b.header("Cookie", ch)
            client.newCall(b.build()).execute().use { resp ->
                val cr = resp.header("Content-Range")
                if (cr != null && cr.contains("/")) cr.substringAfterLast("/").toLongOrNull() ?: -1L
                else resp.body?.contentLength() ?: -1L
            }
        } catch (e: Exception) { -1L }
    }

    // 下载一个字节区间（Range），边读边上报字节数
    private fun downloadRange(url: String, start: Long, end: Long, dest: File, onRead: ((Long) -> Unit)? = null) {
        val b = Request.Builder().url(url)
            .header("Range", "bytes=" + start + "-" + end)
            .header("User-Agent", userAgent)
            .header("Referer", "https://www.bilibili.com/")
        val ch = buildCookieHeader()
        if (ch.isNotEmpty()) b.header("Cookie", ch)
        parallelClient.newCall(b.build()).execute().use { resp ->
            if (resp.code != 206) throw Exception("RANGE_UNSUPPORTED " + resp.code)
            dest.outputStream().use { out ->
                resp.body?.byteStream()?.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var read = input.read(buf)
                    while (read != -1) {
                        if (DownloadControl.cancelled) throw Exception("已取消下载")
                        while (DownloadControl.paused && !DownloadControl.cancelled) Thread.sleep(100)
                        out.write(buf, 0, read)
                        onRead?.invoke(read.toLong())
                        read = input.read(buf)
                    }
                }
            }
        }
    }

    // 分段并行下载（绕过单连接限速），带备用 CDN 节点自动切换
    suspend fun downloadFileParallel(urls: List<String>, dest: File, numChunks: Int = 4, onProgress: ((Long, Long) -> Unit)? = null) = withContext(Dispatchers.IO) {
        if (urls.isEmpty()) throw Exception("没有可用的下载地址")
        var lastErr: Exception? = null
        // 先尝试用每个节点做分段并行下载，任一失败就切下一个节点
        for (url in urls) {
            if (DownloadControl.cancelled) throw Exception("已取消下载")
            if (downloadFileParallelOne(url, dest, numChunks, onProgress)) return@withContext
            dest.delete()
            lastErr = Exception("CDN 节点不可用")
        }
        // 所有节点分段都失败（多为 DNS/连接问题），用单连接重试每个节点
        for (url in urls) {
            try {
                dest.delete()
                downloadFile(url, dest, onProgress)
                return@withContext
            } catch (e: Exception) {
                lastErr = e
                dest.delete()
            }
        }
        throw lastErr ?: Exception("下载失败")
    }

    // 用单个 CDN 节点做分段并行下载，成功返回 true，失败返回 false（不抛出）
    private suspend fun downloadFileParallelOne(url: String, dest: File, numChunks: Int, onProgress: ((Long, Long) -> Unit)?): Boolean {
        if (DownloadControl.cancelled) return false
        val total = try { getLength(url) } catch (e: Exception) { return false }
        if (total <= 0) return false
        if (total <= 2 * 1024 * 1024 || numChunks <= 1) {
            return try { downloadFile(url, dest, onProgress); true } catch (e: Exception) { false }
        }
        val chunkSize = (total + numChunks - 1) / numChunks
        val parts = (0 until numChunks).map { File(dest.parentFile, dest.name + ".part" + it) }
        val bytes = AtomicLongArray(numChunks)
        val ok = AtomicBoolean(true)
        coroutineScope {
            repeat(numChunks) { i ->
                launch(Dispatchers.IO) {
                    try {
                        val start = i * chunkSize
                        val end = minOf((i + 1) * chunkSize - 1, total - 1)
                        downloadRange(url, start, end, parts[i]) { read ->
                            bytes.addAndGet(i, read)
                            var d = 0L
                            for (j in 0 until numChunks) d += bytes.get(j)
                            onProgress?.invoke(d, total)
                        }
                    } catch (e: Exception) {
                        ok.set(false)
                    }
                }
            }
        }
        if (!ok.get()) { parts.forEach { it.delete() }; return false }
        try {
            dest.outputStream().use { out ->
                for (p in parts) { if (p.exists()) { p.inputStream().use { it.copyTo(out) }; p.delete() } }
            }
            onProgress?.invoke(total, total)
            return true
        } catch (e: Exception) { parts.forEach { it.delete() }; return false }
    }

    // 生成登录二维码，返回 (qrcodeKey, 二维码内容url)
    fun qrGenerate(): Pair<String, String> {
        ensureBuvid()
        val json = JSONObject(get("https://passport.bilibili.com/x/passport-login/web/qrcode/generate?source=main-fe-header"))
        val data = json.getJSONObject("data")
        val url = data.getString("url")
        val key = data.optString("qrcode_key", url.substringAfter("qrcode_key=").substringBefore("&"))
        return key to url
    }

    data class QrPollResult(val code: Int, val cookie: String, val url: String)

    // 轮询扫码状态
    // 状态码: 86038=过期 86101=等待扫码 86090=已扫码待确认 0=成功
    // 成功时 cookie 从 Set-Cookie 响应头抓取（新版接口改成这样返回 SESSDATA）
    fun qrPoll(qrcodeKey: String): QrPollResult {
        val url = "https://passport.bilibili.com/x/passport-login/web/qrcode/poll?qrcode_key=" + qrcodeKey + "&source=main-fe-header"
        val b = Request.Builder().url(url)
            .header("User-Agent", userAgent)
            .header("Referer", "https://www.bilibili.com/")
        val ch = buildCookieHeader()
        if (ch.isNotEmpty()) b.header("Cookie", ch)
        val resp = client.newCall(b.build()).execute()
        resp.use {
            val body = it.body?.string() ?: ""
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
            val code = data?.optInt("code", -1) ?: -1
            val u = data?.optString("url", "") ?: ""
            // 从 Set-Cookie 响应头拼 cookie
            val cookie = it.headers("Set-Cookie").joinToString("; ") { h -> h.substringBefore(";") }
            return QrPollResult(code, cookie, u)
        }
    }

    data class ParsedData(val info: VideoInfo, val videos: List<VideoTrack>, val audios: List<AudioTrack>)

    // 解析并返回结构化数据（供下载用）
    suspend fun parseDetailed(input: String): ParsedData = withContext(Dispatchers.IO) {
        ensureBuvid()
        updateWbiKey()
        val (type, id) = extractId(input)
        if (id.isEmpty()) throw Exception("没识别出视频ID，请直接粘贴 BV号 / av号 或完整 bilibili 链接")
        val info = fetchVideoInfo(type, id)
        val page = info.pages.firstOrNull() ?: throw Exception("该视频没有分P")
        val (videos, audios) = fetchTracks(info.aid, page.cid)
        ParsedData(info, videos, audios)
    }

    data class CollectionItem(val aid: String, val cid: String, val title: String)

    data class UserInfo(val name: String, val face: String)

    // 获取当前登录用户的昵称和头像
    fun fetchUserInfo(): UserInfo {
        val nav = JSONObject(get("https://api.bilibili.com/x/web-interface/nav"))
        val data = nav.optJSONObject("data")
        return UserInfo(data?.optString("uname", "") ?: "", data?.optString("face", "") ?: "")
    }

    // 识别合集/系列链接，返回 (type, sid)；type: 8=合集 5=系列
    fun extractCollectionId(input: String): Pair<String, String>? {
        val s = input.trim()
        var url = Regex("https?://[^ ]+").find(s)?.value ?: s
        // 手机端/TV 分享的短链接先解析成真实地址
        if (url.contains("b23.tv") || url.contains("bili2233.cn") || url.contains("bili22.cn")) {
            url = try { resolveFinalUrl(url) } catch (e: Exception) { url }
        }
        // 合集/系列 id：老格式 sid=，新格式 business_id=
        val sid = Regex("sid=([0-9]+)").find(url)?.groupValues?.get(1)
            ?: Regex("business_id=([0-9]+)").find(url)?.groupValues?.get(1)
            ?: return null
        val type = when {
            url.contains("collection") -> "8"
            url.contains("series") -> "5"
            else -> return null
        }
        return type to sid
    }

    // 拉取合集/系列里所有视频，返回 (合集标题, 视频列表)
    fun fetchCollection(type: String, sid: String): Pair<String, List<CollectionItem>> {
        val info = JSONObject(get("https://api.bilibili.com/x/v1/medialist/info?type=" + type + "&biz_id=" + sid + "&tid=0"))
        val listTitle = info.getJSONObject("data").optString("title", "合集")
        val items = mutableListOf<CollectionItem>()
        var hasMore = true
        var oid = ""
        var guard = 0
        while (hasMore && guard < 200) {
            guard++
            val desc = if (type == "5") "true" else "false"
            val listUrl = "https://api.bilibili.com/x/v2/medialist/resource/list?type=" + type +
                "&oid=" + oid + "&otype=2&biz_id=" + sid +
                "&with_current=true&mobi_app=web&ps=20&direction=false&sort_field=1&tid=0&desc=" + desc
            val json = JSONObject(get(listUrl))
            val data = json.optJSONObject("data") ?: break
            hasMore = data.optBoolean("has_more", false)
            val mediaList = data.optJSONArray("media_list") ?: break
            var newOid = oid
            for (i in 0 until mediaList.length()) {
                val m = mediaList.getJSONObject(i)
                val aid = m.optLong("id", 0).toString()
                val title = m.optString("title", "")
                val pageCount = m.optInt("page", 1)
                val pages = m.optJSONArray("pages")
                if (pages != null) {
                    for (j in 0 until pages.length()) {
                        val p = pages.getJSONObject(j)
                        val cid = p.optLong("id", 0).toString()
                        val pageTitle = if (pageCount == 1) title
                            else title + "_P" + p.optInt("page", 1) + "_" + p.optString("title", "")
                        items.add(CollectionItem(aid, cid, pageTitle))
                    }
                }
                newOid = aid
            }
            if (newOid == oid && hasMore) break
            oid = newOid
        }
        return listTitle to items
    }

    // 番剧：ep_id 或 season_id 查整季
    fun fetchBangumi(id: String, isSeason: Boolean): Pair<String, List<CollectionItem>> {
        val param = (if (isSeason) "season_id=" else "ep_id=") + id
        val json = JSONObject(get("https://api.bilibili.com/pgc/view/web/season?" + param))
        val result = json.optJSONObject("result") ?: throw Exception("番剧接口异常")
        val title = result.optString("title", "番剧")
        val items = mutableListOf<CollectionItem>()
        val eps = result.optJSONArray("episodes")
        if (eps != null) {
            for (i in 0 until eps.length()) {
                val e = eps.getJSONObject(i)
                if (e.optString("badge", "") == "预告") continue
                val aid = e.optString("aid", "0")
                val cid = e.optString("cid", "0")
                val t = (e.optString("title", "") + " " + e.optString("long_title", "")).trim()
                if (aid != "0" && cid != "0") items.add(CollectionItem(aid, cid, t))
            }
        }
        return title to items
    }

    // 收藏夹：media_id(fid) + mid
    fun fetchFavList(fid: String, mid: String): Pair<String, List<CollectionItem>> {
        var favId = fid
        if (favId.isEmpty()) {
            val lj = JSONObject(get("https://api.bilibili.com/x/v3/fav/folder/created/list-all?up_mid=" + mid))
            val list = lj.optJSONObject("data")?.optJSONArray("list")
            if (list != null && list.length() > 0) favId = list.getJSONObject(0).optString("id", "")
        }
        if (favId.isEmpty()) throw Exception("找不到收藏夹")
        val items = mutableListOf<CollectionItem>()
        val first = JSONObject(get("https://api.bilibili.com/x/v3/fav/resource/list?media_id=" + favId + "&pn=1&ps=20&order=mtime&type=2&tid=0&platform=web"))
        val data = first.optJSONObject("data") ?: throw Exception("收藏夹接口异常")
        val title = data.optJSONObject("info")?.optString("title", "收藏夹") ?: "收藏夹"
        val totalCount = data.optJSONObject("info")?.optInt("media_count", 0) ?: 0
        val totalPage = if (totalCount > 0) (totalCount + 19) / 20 else 1
        for (pn in 1..totalPage) {
            val pageJson = if (pn == 1) first else JSONObject(get("https://api.bilibili.com/x/v3/fav/resource/list?media_id=" + favId + "&pn=" + pn + "&ps=20&order=mtime&type=2&tid=0&platform=web"))
            val d = pageJson.optJSONObject("data") ?: continue
            val medias = d.optJSONArray("medias") ?: continue
            for (i in 0 until medias.length()) {
                val m = medias.getJSONObject(i)
                if (m.optInt("attr", 0) != 0) continue
                val aid = m.optString("id", "0")
                val mt = m.optString("title", "")
                val pageCount = m.optInt("page", 1)
                if (pageCount > 1) {
                    val vi = fetchVideoInfo("aid", aid)
                    for (p in vi.pages) items.add(CollectionItem(aid, p.cid, mt + "_P" + p.index + "_" + p.title))
                } else {
                    val cid = m.optJSONObject("ugc")?.optString("first_cid", "0") ?: "0"
                    if (aid != "0" && cid != "0") items.add(CollectionItem(aid, cid, mt))
                }
            }
        }
        return title to items
    }

    // 投稿列表：mid -> 所有视频（cid 后续懒加载）
    fun fetchSpaceVideos(mid: String): Pair<String, List<CollectionItem>> {
        val items = mutableListOf<CollectionItem>()
        var title = "UP主" + mid + " 的投稿"
        try {
            val u = JSONObject(get("https://api.live.bilibili.com/live_user/v1/Master/info?uid=" + mid))
            title = u.optJSONObject("data")?.optJSONObject("info")?.optString("uname", title) ?: title
        } catch (e: Exception) {}
        var pn = 1
        var totalPage = 1
        while (pn <= totalPage && pn <= 200) {
            val params = "mid=" + mid + "&order=pubdate&pn=" + pn + "&ps=50&tid=0&wts=" + (System.currentTimeMillis() / 1000)
            val url = "https://api.bilibili.com/x/space/wbi/arc/search?" + WbiSign.sign(params, wbiKey)
            val json = JSONObject(get(url))
            val data = json.optJSONObject("data") ?: break
            val count = data.optJSONObject("page")?.optInt("count", 0) ?: 0
            totalPage = if (count > 0) (count + 49) / 50 else 1
            val vlist = data.optJSONObject("list")?.optJSONArray("vlist") ?: break
            for (i in 0 until vlist.length()) {
                val v = vlist.getJSONObject(i)
                val aid = v.optString("aid", "0")
                if (aid != "0") items.add(CollectionItem(aid, "", v.optString("title", "")))
            }
            pn++
        }
        return title to items
    }

    // 取某个 aid 的第一条 cid（投稿列表下载时懒加载）
    fun getCid(aid: String): String {
        val info = fetchVideoInfo("aid", aid)
        return info.pages.firstOrNull()?.cid ?: "0"
    }

    // 获取字幕列表 (语言, 地址)
    fun fetchSubtitles(aid: String, cid: String): List<Pair<String, String>> {
        val params = "avid=" + aid + "&cid=" + cid + "&fnval=4048&fnver=0&fourk=1&otype=json&qn=0&wts=" + (System.currentTimeMillis() / 1000)
        val url = "https://api.bilibili.com/x/player/wbi/playurl?" + WbiSign.sign(params, wbiKey)
        val json = JSONObject(get(url))
        val list = json.optJSONObject("data")?.optJSONObject("subtitle")?.optJSONArray("list") ?: return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (i in 0 until list.length()) {
            val s = list.getJSONObject(i)
            val lan = s.optString("lan_doc", s.optString("lan", ""))
            val u = s.optString("subtitle_url", "")
            if (u.isNotEmpty()) result.add(lan to (if (u.startsWith("//")) "https:" + u else u))
        }
        return result
    }

    // 下载弹幕 XML
    fun downloadDanmaku(cid: String, dest: File) {
        downloadFile("https://comment.bilibili.com/" + cid + ".xml", dest)
    }

    // B站字幕 JSON 转 SRT
    fun subtitleJsonToSrt(jsonText: String): String {
        val body = JSONObject(jsonText).optJSONArray("body") ?: return ""
        val sb = StringBuilder()
        var idx = 1
        for (i in 0 until body.length()) {
            val item = body.getJSONObject(i)
            val from = item.optDouble("from", 0.0)
            val to = item.optDouble("to", 0.0)
            val content = item.optString("content", "")
            sb.append(idx).append("\n")
            sb.append(formatSrtTime(from)).append(" --> ").append(formatSrtTime(to)).append("\n")
            sb.append(content).append("\n\n")
            idx++
        }
        return sb.toString()
    }

    private fun formatSrtTime(seconds: Double): String {
        val ms = (seconds * 1000).toLong()
        val h = ms / 3600000
        val m = (ms % 3600000) / 60000
        val s = (ms % 60000) / 1000
        val millis = ms % 1000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, millis)
    }

    // 统一识别各类列表链接，返回 (标题, 视频列表)，不是列表则返回 null
    fun detectAndFetchList(input: String): Pair<String, List<CollectionItem>>? {
        ensureBuvid()
        updateWbiKey()
        val s = input.trim()
        var url = Regex("https?://[^ ]+").find(s)?.value ?: s
        if (url.contains("b23.tv") || url.contains("bili2233.cn") || url.contains("bili22.cn")) {
            url = try { resolveFinalUrl(url) } catch (e: Exception) { url }
        }
        // 合集/系列
        val sid = Regex("sid=([0-9]+)").find(url)?.groupValues?.get(1)
            ?: Regex("business_id=([0-9]+)").find(url)?.groupValues?.get(1)
        if (sid != null && (url.contains("collection") || url.contains("series"))) {
            val type = if (url.contains("series")) "5" else "8"
            return fetchCollection(type, sid)
        }
        // 番剧
        Regex("bangumi/play/ep([0-9]+)").find(url)?.groupValues?.get(1)?.let { return fetchBangumi(it, false) }
        Regex("bangumi/play/ss([0-9]+)").find(url)?.groupValues?.get(1)?.let { return fetchBangumi(it, true) }
        // 收藏夹
        if (url.contains("favlist")) {
            val fid = Regex("fid=([0-9]+)").find(url)?.groupValues?.get(1) ?: ""
            val mid = Regex("space[.]bilibili[.]com/([0-9]+)").find(url)?.groupValues?.get(1) ?: ""
            if (mid.isNotEmpty()) return fetchFavList(fid, mid)
        }
        // 投稿列表
        Regex("space[.]bilibili[.]com/([0-9]+)").find(url)?.groupValues?.get(1)?.let { return fetchSpaceVideos(it) }
        return null
    }

    // 一次性解析并格式化成文本
    suspend fun parse(input: String): String = withContext(Dispatchers.IO) {
        try {
            val d = parseDetailed(input)
            val sb = StringBuilder()
            sb.append("标题: ").append(d.info.title).append("\n")
            sb.append("BV: ").append(d.info.bvid).append("   aid: ").append(d.info.aid).append("\n")
            sb.append("分P数: ").append(d.info.pages.size).append("\n\n")
            if (d.info.pages.isNotEmpty()) {
                val page = d.info.pages[0]
                sb.append("第1P: ").append(page.title)
                    .append("  (cid=").append(page.cid)
                    .append(", ").append(page.duration).append("s)").append("\n\n")
                sb.append("--- 视频流 (").append(d.videos.size).append(") ---\n")
                for (v in d.videos) {
                    sb.append("  [").append(v.id).append("] ").append(v.dfn)
                        .append(" ").append(v.resolution)
                        .append(" ").append(v.codecs)
                        .append(" ").append(v.bandwidth).append("kbps\n")
                }
                sb.append("--- 音频流 (").append(d.audios.size).append(") ---\n")
                for (a in d.audios) {
                    sb.append("  [").append(a.id).append("] ")
                        .append(a.codecs).append(" ")
                        .append(a.bandwidth).append("kbps\n")
                }
            }
            sb.toString()
        } catch (e: Exception) {
            "❌ " + (e.message ?: e.toString())
        }
    }
}
