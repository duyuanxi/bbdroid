package com.bbdroid.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bbdroid.app.bilibili.BiliApi
import com.bbdroid.app.bilibili.DownloadControl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// 重新下载请求（历史记录触发，切到下载页自动解析/下载）
object ReDownloadRequest {
    var bvid = ""
    var id by mutableStateOf(0)
    fun request(b: String) { bvid = b; id++ }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DownloadTab(context: Context, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var biliInput by remember { mutableStateOf("") }
    var biliResult by remember { mutableStateOf("") }
    var biliRunning by remember { mutableStateOf(false) }
    var parseJob by remember { mutableStateOf<Job?>(null) }
    var parsed by remember { mutableStateOf<BiliApi.ParsedData?>(null) }
    var selectedVideoIdx by remember { mutableStateOf(0) }
    var downloadStatus by DownloadSession.downloadStatusState
    var downloading by DownloadSession.downloadingState
    var progressPct by DownloadSession.progressPctState
    var progressInfo by DownloadSession.progressInfoState
    var batchStatus by DownloadSession.batchStatusState
    var batchRunning by DownloadSession.batchRunningState
    var collectionTitle by remember { mutableStateOf("") }
    var collectionItems by remember { mutableStateOf<List<BiliApi.CollectionItem>>(emptyList()) }
    var collectionStatus by DownloadSession.collectionStatusState
    var collectionRunning by DownloadSession.collectionRunningState
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var openUri by DownloadSession.openUriState
    var openName by DownloadSession.openNameState
    var downloadExtras by remember { mutableStateOf(true) }
    var showDownloadDialog by DownloadSession.showDialogState
    var dialogMsg by DownloadSession.dialogMsgState
    var dialogUri by DownloadSession.dialogUriState
    var dialogName by DownloadSession.dialogNameState
    var downloadJob by DownloadSession.downloadJobState
    var batchJob by DownloadSession.batchJobState
    var collectionJob by DownloadSession.collectionJobState
    var autoDownload by remember { mutableStateOf(false) }

    var loginStatus by remember { mutableStateOf("未登录") }
    var userName by remember { mutableStateOf("") }
    var userFace by remember { mutableStateOf<Bitmap?>(null) }

    fun refreshLogin() {
        val c = Auth.load(context)
        BiliApi.cookie = c
        loginStatus = if (Auth.isLoggedIn(c)) "已登录" else "未登录"
        if (Auth.isLoggedIn(c)) {
            scope.launch {
                val u = withContext(Dispatchers.IO) { runCatching { BiliApi.fetchUserInfo() }.getOrNull() }
                userName = u?.name ?: ""
                if (u != null && u.face.isNotEmpty()) userFace = ImageUtil.load(u.face)
            }
        } else { userName = ""; userFace = null }
    }

    LaunchedEffect(Unit) { refreshLogin() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val ob = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refreshLogin() }
        lifecycleOwner.lifecycle.addObserver(ob)
        onDispose { lifecycleOwner.lifecycle.removeObserver(ob) }
    }

    val webLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshLogin() }
    val qrLoginLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshLogin() }

    val doDownload: () -> Unit = {
        DownloadControl.reset()
        downloadJob = DownloadSession.scope.launch {
            downloading = true; downloadStatus = ""; progressPct = -1f; progressInfo = ""; openUri = null; openName = ""
            val d = parsed
            if (d == null) { downloadStatus = "❌ 请先解析视频"; downloading = false; return@launch }
            val video = d.videos.getOrNull(selectedVideoIdx) ?: d.videos.firstOrNull()
            val audio = d.audios.firstOrNull()
            if (video == null || audio == null) { downloadStatus = "❌ 没有可下载的流"; downloading = false; return@launch }
            DownloadSession.clearTasks()
            DownloadSession.upsertTask(DownloadTask(key = "single", title = d.info.title, quality = video.dfn, progress = 0f, status = "下载中"))
            var okMsg: String? = null
            withContext(Dispatchers.IO) {
                try {
                    val tracker = SpeedTracker()
                    val merged = Downloader.downloadAndMerge(
                        video, audio, context.cacheDir,
                        onProgress = { p -> downloadStatus = p },
                        onBytes = { dl, total ->
                            if (total > 0) {
                                progressPct = dl.toFloat() / total.toFloat()
                                DownloadSession.updateTask("single", progress = dl.toFloat() / total.toFloat(), status = "下载中")
                                val now = System.currentTimeMillis()
                                val speed = tracker.update(now, dl)
                                if (speed > 0) {
                                    val remain = (total - dl) / speed
                                    progressInfo = (progressPct * 100).toInt().toString() + "% · " + fmtSpeed(speed) + " · 剩余 " + fmtEta(remain)
                                }
                            } else {
                                progressPct = -1f; progressInfo = ""
                                DownloadSession.updateTask("single", progress = -1f, status = "合并中")
                            }
                        }
                    )
                    downloadStatus = "保存到 BBDroid/视频"
                    DownloadSession.updateTask("single", status = "保存中")
                    val name = sanitizeFilename(d.info.title) + ".mp4"
                    val stored = Storage.saveVideo(context, merged, name)
                    val savedTo = stored.display
                    openUri = stored.uri; openName = name
                    merged.delete()
                    History.add(context, History.Item(d.info.title, d.info.pic, video.dfn, System.currentTimeMillis(), stored.uri?.toString() ?: "", d.info.bvid))
                    val cid = d.info.pages.firstOrNull()?.cid ?: ""
                    okMsg = if (cid.isNotEmpty()) {
                        val extra = Downloader.downloadExtras(context, d.info, cid, context.cacheDir, enabled = downloadExtras) { p -> downloadStatus = p }
                        "✅ 视频: " + savedTo + "；附赠: " + extra
                    } else "✅ 已保存 " + savedTo
                    downloadStatus = okMsg!!
                } catch (e: Exception) { downloadStatus = if (DownloadControl.cancelled) "⏹ 已停止" else "❌ " + (e.message ?: e.toString()) }
            }
            DownloadSession.clearTasks()
            if (okMsg != null) {
                dialogMsg = okMsg!!; dialogUri = openUri; dialogName = openName
                showDownloadDialog = true
            }
            downloading = false
            downloadJob = null
        }
    }

    val doBatchDownload: () -> Unit = {
        DownloadControl.reset()
        batchJob = DownloadSession.scope.launch {
            batchRunning = true; batchStatus = ""
            val d = parsed
            if (d == null) { batchStatus = "❌ 请先解析视频"; batchRunning = false; return@launch }
            val pages = d.info.pages
            if (pages.isEmpty()) { batchStatus = "❌ 没有分P"; batchRunning = false; return@launch }
            try {
                var done = 0
                DownloadSession.clearTasks()
                DownloadSession.upsertTask(DownloadTask(key = "batch", title = d.info.title, progress = 0f, status = "0/" + pages.size, isParent = true, childTotal = pages.size))
                withContext(Dispatchers.IO) {
                    for ((idx, page) in pages.withIndex()) {
                        val childKey = "batch-" + idx
                        DownloadSession.upsertTask(DownloadTask(key = childKey, title = page.title, progress = 0f, status = "等待", parentKey = "batch"))
                        batchStatus = "[" + page.index + "/" + pages.size + "] " + page.title
                        val (vs, as_) = BiliApi.fetchTracks(d.info.aid, page.cid)
                        val v = vs.firstOrNull() ?: continue; val a = as_.firstOrNull() ?: continue
                        DownloadSession.updateTask(childKey, progress = 0f, status = "下载中")
                        val m = Downloader.downloadAndMerge(v, a, context.cacheDir,
                            onProgress = { p -> batchStatus = "[" + page.index + "/" + pages.size + "] " + p },
                            onBytes = { dl, total ->
                                if (total > 0) {
                                    DownloadSession.updateTask(childKey, progress = dl.toFloat() / total.toFloat(), status = "下载中")
                                    DownloadSession.updateTask("batch", progress = (done + dl.toFloat() / total.toFloat()) / pages.size)
                                } else {
                                    DownloadSession.updateTask(childKey, progress = -1f, status = "合并中")
                                }
                            })
                        val name = sanitizeFilename(d.info.title) + "_P" + page.index + ".mp4"
                        DownloadSession.updateTask(childKey, progress = 1f, status = "保存中")
                        Storage.saveVideo(context, m, name); m.delete(); done++
                        DownloadSession.updateTask("batch", progress = done.toFloat() / pages.size, status = done.toString() + "/" + pages.size)
                    }
                }
                DownloadSession.clearTasks()
                batchStatus = "✅ 批量完成 " + done + " 个"
                dialogMsg = batchStatus; dialogUri = null; dialogName = ""
                showDownloadDialog = true
            } catch (e: Exception) { DownloadSession.clearTasks(); batchStatus = if (DownloadControl.cancelled) "⏹ 已停止" else "❌ " + (e.message ?: e.toString()) }
            batchRunning = false
            batchJob = null
        }
    }

    val doCollectionDownload: () -> Unit = {
        DownloadControl.reset()
        collectionJob = DownloadSession.scope.launch {
            collectionRunning = true; collectionStatus = ""
            val items = collectionItems
            if (items.isEmpty()) { collectionStatus = "❌ 列表为空"; collectionRunning = false; return@launch }
            try {
                var done = 0
                DownloadSession.clearTasks()
                DownloadSession.upsertTask(DownloadTask(key = "collection", title = collectionTitle.ifEmpty { "合集下载" }, progress = 0f, status = "0/" + items.size, isParent = true, childTotal = items.size))
                withContext(Dispatchers.IO) {
                    for ((idx, item) in items.withIndex()) {
                        val childKey = "collection-" + idx
                        DownloadSession.upsertTask(DownloadTask(key = childKey, title = item.title, progress = 0f, status = "等待", parentKey = "collection"))
                        val n = done + 1
                        collectionStatus = "[" + n + "/" + items.size + "] " + item.title
                        var cid = item.cid
                        if (cid.isEmpty()) cid = BiliApi.getCid(item.aid)
                        if (cid == "0") { collectionStatus = "跳过 " + item.title; continue }
                        val (vs, as_) = BiliApi.fetchTracks(item.aid, cid)
                        val v = vs.firstOrNull() ?: continue; val a = as_.firstOrNull() ?: continue
                        DownloadSession.updateTask(childKey, progress = 0f, status = "下载中")
                        val m = Downloader.downloadAndMerge(v, a, context.cacheDir,
                            onProgress = { p -> collectionStatus = "[" + n + "/" + items.size + "] " + p },
                            onBytes = { dl, total ->
                                if (total > 0) {
                                    DownloadSession.updateTask(childKey, progress = dl.toFloat() / total.toFloat(), status = "下载中")
                                    DownloadSession.updateTask("collection", progress = (done + dl.toFloat() / total.toFloat()) / items.size)
                                } else {
                                    DownloadSession.updateTask(childKey, progress = -1f, status = "合并中")
                                }
                            })
                        val name = sanitizeFilename(item.title) + ".mp4"
                        DownloadSession.updateTask(childKey, progress = 1f, status = "保存中")
                        Storage.saveVideo(context, m, name); m.delete(); done++
                        DownloadSession.updateTask("collection", progress = done.toFloat() / items.size, status = done.toString() + "/" + items.size)
                    }
                }
                DownloadSession.clearTasks()
                collectionStatus = "✅ 合集完成 " + done + " 个"
                dialogMsg = collectionStatus; dialogUri = null; dialogName = ""
                showDownloadDialog = true
            } catch (e: Exception) { DownloadSession.clearTasks(); collectionStatus = if (DownloadControl.cancelled) "⏹ 已停止" else "❌ " + (e.message ?: e.toString()) }
            collectionRunning = false
            collectionJob = null
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) doDownload() else downloadStatus = "❌ 需要存储权限"
    }

    val stopAll: () -> Unit = {
        DownloadControl.cancel()
        downloadJob?.cancel()
        batchJob?.cancel()
        collectionJob?.cancel()
        downloading = false; batchRunning = false; collectionRunning = false
        downloadStatus = "⏹ 已停止"; batchStatus = ""; collectionStatus = ""
    }

    // 解析（解析完成后若来自重新下载则自动开始下载）
    fun parseUrl(url: String) {
        biliInput = url
        keyboardController?.hide()
        parseJob = scope.launch {
            biliRunning = true; biliResult = "解析中…"; parsed = null; collectionItems = emptyList(); cover = null
            downloadStatus = ""; batchStatus = ""; collectionStatus = ""
            withContext(Dispatchers.IO) {
                try {
                    val list = BiliApi.detectAndFetchList(url)
                    if (!isActive) return@withContext
                    if (list != null) {
                        val (t, items) = list; collectionTitle = t; collectionItems = items
                        biliResult = "列表: " + t + "（共 " + items.size + " 个视频）"
                    } else {
                        val d = BiliApi.parseDetailed(url)
                        if (!isActive) return@withContext
                        parsed = d; selectedVideoIdx = 0; biliResult = formatParsed(d)
                        if (d.info.pic.isNotEmpty()) cover = ImageUtil.load(d.info.pic)
                        if (d.info.seasonId.isNotEmpty()) {
                            val (ct, ci) = BiliApi.fetchCollection("8", d.info.seasonId)
                            if (!isActive) return@withContext
                            collectionTitle = ct; collectionItems = ci
                            biliResult = biliResult + "\n\n[合集] " + ct + "（共 " + ci.size + " 个视频）"
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) { parsed = null; collectionItems = emptyList(); biliResult = "❌ " + (e.message ?: e.toString()) }
                }
            }
            if (isActive) biliRunning = false
            if (autoDownload) { autoDownload = false; doDownload() }
        }
    }

    // 历史记录触发重新下载
    LaunchedEffect(ReDownloadRequest.id) {
        if (ReDownloadRequest.id > 0 && ReDownloadRequest.bvid.isNotEmpty()) {
            autoDownload = true
            parseUrl(ReDownloadRequest.bvid)
        }
    }

    Column(modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 登录卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (userFace != null) Image(userFace!!.asImageBitmap(), contentDescription = "头像", modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f)) {
                    Text(if (Auth.isLoggedIn(BiliApi.cookie)) userName.ifEmpty { "已登录" } else "未登录", style = MaterialTheme.typography.titleSmall)
                    Text(if (Auth.isLoggedIn(BiliApi.cookie)) "已解锁高清/大会员内容" else "登录后解锁高清/番剧", style = MaterialTheme.typography.bodySmall)
                }
                if (!Auth.isLoggedIn(BiliApi.cookie)) {
                    GlassButton(onClick = { qrLoginLauncher.launch(Intent(context, QrLoginActivity::class.java)) }, filled = false) { Text("扫码登录") }
                }
            }
        }

        // 输入
        OutlinedTextField(value = biliInput, onValueChange = { biliInput = it }, label = { Text("粘贴 B站链接 / BV号 / av号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        GlassButton(enabled = biliInput.isNotBlank(), onClick = {
            if (biliRunning) {
                parseJob?.cancel()
                biliRunning = false
                biliResult = "已停止解析"
            } else {
                keyboardController?.hide()
                parseJob = scope.launch {
                    biliRunning = true; biliResult = "解析中…"; parsed = null; collectionItems = emptyList(); cover = null
                    downloadStatus = ""; batchStatus = ""; collectionStatus = ""
                    withContext(Dispatchers.IO) {
                        try {
                            val list = BiliApi.detectAndFetchList(biliInput)
                            if (!isActive) return@withContext
                            if (list != null) {
                                val (t, items) = list; collectionTitle = t; collectionItems = items
                                biliResult = "列表: " + t + "（共 " + items.size + " 个视频）"
                            } else {
                                val d = BiliApi.parseDetailed(biliInput)
                                if (!isActive) return@withContext
                                parsed = d; selectedVideoIdx = 0; biliResult = formatParsed(d)
                                if (d.info.pic.isNotEmpty()) cover = ImageUtil.load(d.info.pic)
                                if (d.info.seasonId.isNotEmpty()) {
                                    val (ct, ci) = BiliApi.fetchCollection("8", d.info.seasonId)
                                    if (!isActive) return@withContext
                                    collectionTitle = ct; collectionItems = ci
                                    biliResult = biliResult + "\n\n[合集] " + ct + "（共 " + ci.size + " 个视频）"
                                }
                            }
                        } catch (e: Exception) {
                            if (isActive) { parsed = null; collectionItems = emptyList(); biliResult = "❌ " + (e.message ?: e.toString()) }
                        }
                    }
                    if (isActive) biliRunning = false
                }
            }
        }) { Text(if (biliRunning) "停止解析" else "解析") }

        val busy = downloading || batchRunning || collectionRunning || biliRunning
        if (busy) {
            if (downloading && progressPct >= 0f) {
                LinearProgressIndicator(progress = { progressPct }, modifier = Modifier.fillMaxWidth())
                if (progressInfo.isNotEmpty()) Text(progressInfo, style = MaterialTheme.typography.bodySmall)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        // 停止按钮
        if (downloading || batchRunning || collectionRunning) {
            GlassButton(onClick = { stopAll() }, modifier = Modifier.fillMaxWidth()) { Text("⏹ 停止下载") }
        }

        // 解析结果
        val d = parsed
        if (d != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (cover != null) Image(cover!!.asImageBitmap(), contentDescription = "封面", modifier = Modifier.size(90.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    Column(Modifier.weight(1f)) {
                        Text(d.info.title, style = MaterialTheme.typography.titleSmall)
                        Text(d.info.bvid + " · " + d.info.pages.size + " 个分P", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            val selVideo = d.videos.getOrNull(selectedVideoIdx)
            GlassButton(
                enabled = !downloading && d.audios.isNotEmpty(),
                onClick = { if (Build.VERSION.SDK_INT < 29) permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else doDownload() },
                modifier = Modifier.fillMaxWidth()
            ) { Text((if (downloading) "下载中…" else "⬇ 下载并合并") + " [" + (selVideo?.dfn ?: "最高清") + "]") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = downloadExtras, onCheckedChange = { downloadExtras = it }, enabled = !downloading)
                Text("下载附赠内容（封面/弹幕/字幕）", style = MaterialTheme.typography.bodySmall)
            }
            // 合集下载也放在上方（单视频属于合集时）
            if (collectionItems.isNotEmpty()) {
                GlassButton(
                    enabled = !collectionRunning && !downloading,
                    onClick = { doCollectionDownload() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (collectionRunning) "合集下载中…" else "📂 下载整个合集 (" + collectionItems.size + ")") }
                if (collectionStatus.isNotEmpty()) Text(collectionStatus, style = MaterialTheme.typography.bodySmall)
            }
            Text("选择清晰度（默认选最高清）", style = MaterialTheme.typography.titleSmall)
            d.videos.forEachIndexed { i, v ->
                val label = v.dfn + "  " + v.resolution + "  " + v.codecs
                if (i == selectedVideoIdx) GlassButton(enabled = !downloading, onClick = { selectedVideoIdx = i }, modifier = Modifier.fillMaxWidth()) { Text(label + "  ✓") }
                else GlassButton(enabled = !downloading, onClick = { selectedVideoIdx = i }, modifier = Modifier.fillMaxWidth(), filled = false) { Text(label) }
            }
            if (d.info.pages.size > 1) GlassButton(enabled = !batchRunning && !downloading, onClick = { doBatchDownload() }) { Text(if (batchRunning) "批量中…" else "批量下载 " + d.info.pages.size + " 个分P") }
            if (downloadStatus.isNotEmpty()) Text(downloadStatus, style = MaterialTheme.typography.bodySmall)
            // 打开已下载的视频
            if (openUri != null) {
                GlassButton(onClick = { openFile(context, openUri!!, openName) }, modifier = Modifier.fillMaxWidth()) { Text("打开视频 ▶") }
            }
            if (batchStatus.isNotEmpty()) Text(batchStatus, style = MaterialTheme.typography.bodySmall)
        }

        // 合集下载（纯列表，无单视频解析结果时显示在下方）
        if (collectionItems.isNotEmpty() && parsed == null) {
            HorizontalDivider()
            Text("合集/列表: " + collectionTitle + "（" + collectionItems.size + " 个）", style = MaterialTheme.typography.titleSmall)
            GlassButton(enabled = !collectionRunning && !downloading, onClick = { doCollectionDownload() }) { Text(if (collectionRunning) "下载中…" else "下载整个合集") }
            if (collectionStatus.isNotEmpty()) Text(collectionStatus, style = MaterialTheme.typography.bodySmall)
        }
        if (biliResult.isNotEmpty() && parsed == null && collectionItems.isEmpty()) Text(biliResult, style = MaterialTheme.typography.bodySmall)
    }

    // 下载完成弹窗
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("下载完成") },
            text = { Text(dialogMsg) },
            confirmButton = {
                if (dialogUri != null) {
                    TextButton(onClick = { shareVideo(context, dialogUri!!, dialogName); showDownloadDialog = false }) { Text("分享") }
                    TextButton(onClick = { openFile(context, dialogUri!!, dialogName); showDownloadDialog = false }) { Text("打开") }
                }
                TextButton(onClick = { showDownloadDialog = false }) { Text("知道了") }
            }
        )
    }
}

fun shareVideo(context: Context, uri: Uri, name: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享 " + name))
    } catch (e: Exception) {}
}

fun openFile(context: Context, uri: Uri, name: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "video/mp4")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: Exception) {
        // 没有播放器时回退到文件管理器
        try {
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(i)
        } catch (e2: Exception) {}
    }
}

private fun fmtSpeed(bps: Double): String {
    return when {
        bps >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB/s", bps / 1024 / 1024)
        bps >= 1024 -> String.format(Locale.US, "%.0f KB/s", bps / 1024)
        else -> String.format(Locale.US, "%.0f B/s", bps)
    }
}

private fun fmtEta(seconds: Double): String {
    val s = seconds.toLong()
    return when {
        s >= 3600 -> (s / 3600).toString() + "h" + ((s % 3600) / 60).toString() + "m"
        s >= 60 -> (s / 60).toString() + "m" + (s % 60).toString() + "s"
        else -> s.toString() + "s"
    }
}

private fun formatParsed(d: BiliApi.ParsedData): String {
    val sb = StringBuilder()
    sb.append("BV: ").append(d.info.bvid).append("  aid: ").append(d.info.aid).append("  分P: ").append(d.info.pages.size)
    return sb.toString()
}
