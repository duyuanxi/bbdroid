package com.bbdroid.app

import android.content.Context
import com.bbdroid.app.bilibili.AudioTrack
import com.bbdroid.app.bilibili.BiliApi
import com.bbdroid.app.bilibili.DownloadControl
import com.bbdroid.app.bilibili.VideoInfo
import com.bbdroid.app.bilibili.VideoTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object Downloader {
    // 下载音视频分片并合并成 mp4，返回合并后的临时文件
    suspend fun downloadAndMerge(
        video: VideoTrack,
        audio: AudioTrack,
        outputDir: File,
        onProgress: (String) -> Unit,
        onBytes: ((Long, Long) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val v = File(outputDir, "dl_video.m4s")
        val a = File(outputDir, "dl_audio.m4s")
        onProgress("下载视频流 (" + video.dfn + " " + video.resolution + ")...")
        var vidTotal = -1L
        downloadStream(video.urls.ifEmpty { listOf(video.baseUrl) }, v) { d, t ->
            vidTotal = t
            onBytes?.invoke(d, t)
        }
        onProgress("下载音频流...")
        downloadStream(audio.urls.ifEmpty { listOf(audio.baseUrl) }, a) { d, t ->
            if (vidTotal > 0 && t > 0) onBytes?.invoke(vidTotal + d, vidTotal + t)
            else onBytes?.invoke(d, t)
        }
        onProgress("合并音视频...")
        onBytes?.invoke(-1L, -1L)
        val out = File(outputDir, "merged.mp4")
        val cmd = "-i \"" + v.absolutePath + "\" -i \"" + a.absolutePath + "\" -c copy \"" + out.absolutePath + "\" -y"
        val r = FFmpegEngine.run(cmd)
        v.delete()
        a.delete()
        if (!r.success) throw Exception("合并失败 (流无效或数据损坏): " + r.output.takeLast(300))
        out
    }

    // 用并行分段下载音视频流，若文件无效（CDN 返回错误页或数据损坏）则回退到可靠的单连接重下
    private suspend fun downloadStream(urls: List<String>, dest: File, onProgress: ((Long, Long) -> Unit)? = null) {
        BiliApi.downloadFileParallel(urls, dest, onProgress = onProgress)
        if (isValidMedia(dest)) return
        dest.delete()
        for (u in urls) {
            if (DownloadControl.cancelled) throw Exception("已取消下载")
            try {
                BiliApi.downloadFile(u, dest, onProgress)
                if (isValidMedia(dest)) return
                dest.delete()
            } catch (e: Exception) { dest.delete() }
        }
        throw Exception("视频流下载无效（可能是链接过期或CDN异常），请重试或换清晰度")
    }

    // 校验是有效的 MP4/m4s 流（检查 box 头/关键字，避免把 CDN 错误页当媒体下下来）
    private fun isValidMedia(f: File): Boolean {
        if (!f.exists() || f.length() < 64) return false
        val head = ByteArray(1024)
        val n = f.inputStream().use { it.read(head) }
        if (n < 4) return false
        val type = String(head, 4, 4, Charsets.ISO_8859_1)
        val s = String(head, 0, n, Charsets.ISO_8859_1)
        return type in setOf("ftyp", "styp", "moov", "moof", "mdat", "free", "skip", "wide", "pdin", "emsg", "sidx") ||
            s.contains("ftyp") || s.contains("styp") || s.contains("moov") || s.contains("moof") || s.contains("mdat")
    }

    // 下载封面、弹幕、字幕，保存到 Downloads/BBDroid/附赠（按开关逐项跳过）
    suspend fun downloadExtras(
        context: Context,
        info: VideoInfo,
        cid: String,
        outputDir: File,
        cover: Boolean = true,
        danmaku: Boolean = true,
        subtitle: Boolean = true,
        onProgress: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        if (!cover && !danmaku && !subtitle) return@withContext "未下载附赠内容"
        val base = sanitize(info.title)
        val msgs = mutableListOf<String>()
        // 封面
        if (cover && info.pic.isNotEmpty()) {
            onProgress("下载封面...")
            try {
                val f = File(outputDir, "cover.jpg")
                BiliApi.downloadFile(info.pic, f)
                msgs.add(Storage.saveToDownloads(context, f, base + ".jpg").display)
                f.delete()
            } catch (e: Exception) {}
        }
        // 弹幕
        if (danmaku) {
            onProgress("下载弹幕...")
            try {
                val dm = File(outputDir, "danmaku.xml")
                BiliApi.downloadDanmaku(cid, dm)
                msgs.add(Storage.saveToDownloads(context, dm, base + ".xml").display)
                dm.delete()
            } catch (e: Exception) {}
        }
        // 字幕
        if (subtitle) {
            onProgress("下载字幕...")
            try {
                val subs = BiliApi.fetchSubtitles(info.aid, cid)
                for ((lan, u) in subs) {
                    val sj = File(outputDir, "sub.json")
                    BiliApi.downloadFile(u, sj)
                    val srt = BiliApi.subtitleJsonToSrt(sj.readText())
                    sj.delete()
                    if (srt.isNotEmpty()) {
                        val sf = File(outputDir, "sub.srt")
                        sf.writeText(srt)
                        msgs.add(Storage.saveToDownloads(context, sf, base + "_" + lan + ".srt").display)
                        sf.delete()
                    }
                }
            } catch (e: Exception) {}
        }
        if (msgs.isEmpty()) "无封面/弹幕/字幕" else msgs.joinToString(", ")
    }

    private fun sanitize(name: String): String {
        val n = name.replace("/", "_").replace(":", "_").replace("*", "_")
            .replace("?", "_").replace("<", "_").replace(">", "_").replace("|", "_").trim()
        return if (n.isEmpty()) "video" else if (n.length > 80) n.take(80) else n
    }
}
