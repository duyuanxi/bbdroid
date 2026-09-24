package com.bbdroid.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
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
    var cover by remember { mutableStateOf<Bitmap?>(null) }
    var collectionTitle by remember { mutableStateOf("") }
    var collectionItems by remember { mutableStateOf<List<BiliApi.CollectionItem>>(emptyList()) }

    var extraDanmaku by remember { mutableStateOf(true) }
    var extraCover by remember { mutableStateOf(true) }
    var extraSubtitle by remember { mutableStateOf(true) }

    var downloadStatus by DownloadSession.downloadStatusState
    var downloading by DownloadSession.downloadingState
    var progressPct by DownloadSession.progressPctState
    var progressInfo by DownloadSession.progressInfoState
    var batchStatus by DownloadSession.batchStatusState
    var batchRunning by DownloadSession.batchRunningState
    var collectionStatus by DownloadSession.collectionStatusState
    var collectionRunning by DownloadSession.collectionRunningState
    var openUri by DownloadSession.openUriState
    var openName by DownloadSession.openNameState
    var showDownloadDialog by DownloadSession.showDialogState
    var dialogMsg by DownloadSession.dialogMsgState
    var dialogUri by DownloadSession.dialogUriState
    var dialogName by DownloadSession.dialogNameState
    var downloadJob by DownloadSession.downloadJobState
    var batchJob by DownloadSession.batchJobState
    var collectionJob by DownloadSession.collectionJobState

    var paused by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    var autoDownload by remember { mutableStateOf(false) }

    // 登录态
    var userName by remember { mutableStateOf("") }
    var userFace by remember { mutableStateOf<Bitmap?>(null) }

    fun refreshLogin() {
        val c = Auth.load(context)
        BiliApi.cookie = c
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
                    val size = merged.length()
                    openUri = stored.uri; openName = name
                    merged.delete()
                    History.add(context, History.Item(
                        title = d.info.title, cover = d.info.pic, quality = video.dfn,
                        size = size, path = savedTo, time = System.currentTimeMillis(),
                        uri = stored.uri?.toString() ?: "", bvid = d.info.bvid,
                    ))
                    val cid = d.info.pages.firstOrNull()?.cid ?: ""
                    okMsg = if (cid.isNotEmpty()) {
                        val extra = Downloader.downloadExtras(context, d.info, cid, context.cacheDir, cover = extraCover, danmaku = extraDanmaku, subtitle = extraSubtitle) { p -> downloadStatus = p }
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
                // 队列持久化
                val dao = BbdroidDatabase.get(context).queueDao()
                dao.clear()
                dao.insertAll(pages.map { QueueEntity(title = d.info.title + " P" + it.index, aid = it.aid, cid = it.cid, bvid = d.info.bvid, quality = "最高清", status = "PENDING") })
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
                        val stored = Storage.saveVideo(context, m, name); val size = m.length(); m.delete()
                        History.add(context, History.Item(title = d.info.title + " P" + page.index, cover = d.info.pic, quality = v.dfn, size = size, path = stored.display, time = System.currentTimeMillis(), uri = stored.uri?.toString() ?: "", bvid = d.info.bvid))
                        done++
                        DownloadSession.updateTask("batch", progress = done.toFloat() / pages.size, status = done.toString() + "/" + pages.size)
                    }
                }
                DownloadSession.clearTasks()
                BbdroidDatabase.get(context).queueDao().clear()
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
                val dao = BbdroidDatabase.get(context).queueDao()
                dao.clear()
                dao.insertAll(items.map { QueueEntity(title = it.title, aid = it.aid, cid = it.cid, bvid = "", quality = "最高清", status = "PENDING") })
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
                        val stored = Storage.saveVideo(context, m, name); val size = m.length(); m.delete()
                        History.add(context, History.Item(title = item.title, cover = "", quality = v.dfn, size = size, path = stored.display, time = System.currentTimeMillis(), uri = stored.uri?.toString() ?: "", bvid = ""))
                        done++
                        DownloadSession.updateTask("collection", progress = done.toFloat() / items.size, status = done.toString() + "/" + items.size)
                    }
                }
                DownloadSession.clearTasks()
                BbdroidDatabase.get(context).queueDao().clear()
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
        DownloadControl.resume()
        downloadJob?.cancel()
        batchJob?.cancel()
        collectionJob?.cancel()
        downloading = false; batchRunning = false; collectionRunning = false
        paused = false
        downloadStatus = "⏹ 已停止"; batchStatus = ""; collectionStatus = ""
    }

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
                        parsed = d; selectedVideoIdx = 0; biliResult = ""
                        if (d.info.pic.isNotEmpty()) cover = ImageUtil.load(d.info.pic)
                        if (d.info.seasonId.isNotEmpty()) {
                            val (ct, ci) = BiliApi.fetchCollection("8", d.info.seasonId)
                            if (!isActive) return@withContext
                            collectionTitle = ct; collectionItems = ci
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

    LaunchedEffect(ReDownloadRequest.id) {
        if (ReDownloadRequest.id > 0 && ReDownloadRequest.bvid.isNotEmpty()) {
            autoDownload = true
            parseUrl(ReDownloadRequest.bvid)
        }
    }

    val loggedIn = Auth.isLoggedIn(BiliApi.cookie)

    Column(
        modifier = modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 账号行（紧凑）
        LoginRow(userFace = userFace, loggedIn = loggedIn, userName = userName, onLogin = { qrLoginLauncher.launch(Intent(context, QrLoginActivity::class.java)) })

        // 链接解析卡
        Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = biliInput,
                    onValueChange = { biliInput = it },
                    label = { Text("粘贴链接 / BV号 / av号") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    enabled = biliInput.isNotBlank(),
                    onClick = {
                        if (biliRunning) { parseJob?.cancel(); biliRunning = false; biliResult = "已停止解析" }
                        else parseUrl(biliInput)
                    }
                ) { Text(if (biliRunning) "停止" else "解析") }
            }
        }

        // 解析结果卡
        val d = parsed
        if (d != null) {
            Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (cover != null) {
                        Image(
                            cover!!.asImageBitmap(),
                            contentDescription = "封面",
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(d.info.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val totalDur = d.info.pages.sumOf { it.duration }
                        val meta = listOfNotNull(
                            d.info.owner.ifEmpty { null },
                            if (totalDur > 0) fmtDuration(totalDur) else null,
                        ).joinToString(" · ")
                        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                    // 清晰度 2 列 FilterChip 网格
                    Text("清晰度", style = MaterialTheme.typography.titleSmall)
                    QualityGrid(videos = d.videos, selectedIdx = selectedVideoIdx, enabled = !downloading, onSelect = { selectedVideoIdx = it })
                    // 附赠选项
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ExtraCheck("弹幕", extraDanmaku, { extraDanmaku = it })
                        ExtraCheck("封面", extraCover, { extraCover = it })
                        ExtraCheck("字幕", extraSubtitle, { extraSubtitle = it })
                    }
                    // 主按钮 + 批量队列
                    Button(
                        enabled = !downloading && d.audios.isNotEmpty(),
                        onClick = { if (Build.VERSION.SDK_INT < 29) permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else doDownload() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (downloading) "下载中…" else "下载并合并") }
                    OutlinedButton(
                        enabled = !batchRunning && !collectionRunning && !downloading,
                        onClick = {
                            when {
                                collectionItems.isNotEmpty() -> doCollectionDownload()
                                d.info.pages.size > 1 -> doBatchDownload()
                                else -> doDownload()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("加入批量队列") }
                }
            }
        }

        // 纯合集/列表（无单视频解析结果）
        if (collectionItems.isNotEmpty() && parsed == null) {
            Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(collectionTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("共 " + collectionItems.size + " 个视频", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        enabled = !collectionRunning && !downloading,
                        onClick = { doCollectionDownload() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (collectionRunning) "合集下载中…" else "下载整个合集") }
                    if (collectionStatus.isNotEmpty()) Text(collectionStatus, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // 进行中任务卡（单集）
        if (downloading) {
            InProgressCard(
                title = d?.info?.title ?: "下载中",
                pct = progressPct,
                info = progressInfo,
                paused = paused,
                onTogglePause = {
                    paused = !paused
                    if (paused) DownloadControl.pause() else DownloadControl.resume()
                },
                onStop = { showStopConfirm = true },
            )
        }

        // 批量任务卡
        if (batchRunning || collectionRunning) {
            BatchTaskCard(tasks = DownloadSession.tasks, status = if (batchRunning) batchStatus else collectionStatus, onStop = { showStopConfirm = true })
        }

        // 打开视频
        if (openUri != null) {
            OutlinedButton(onClick = { openFile(context, openUri!!, openName) }, modifier = Modifier.fillMaxWidth()) { Text("打开视频 ▶") }
        }

        // 状态 / 错误
        if (biliResult.isNotEmpty() && parsed == null && collectionItems.isEmpty()) {
            Text(biliResult, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    // 停止确认
    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("停止下载") },
            text = { Text("确定要停止当前下载吗？已下载的部分不会被删除。") },
            confirmButton = { TextButton(onClick = { showStopConfirm = false; stopAll() }) { Text("停止") } },
            dismissButton = { TextButton(onClick = { showStopConfirm = false }) { Text("取消") } },
        )
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

// ---------- 子组件 ----------

@Composable
private fun LoginRow(userFace: Bitmap?, loggedIn: Boolean, userName: String, onLogin: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (userFace != null) {
            Image(userFace.asImageBitmap(), contentDescription = "头像", modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
        }
        Column(Modifier.weight(1f)) {
            Text(if (loggedIn) userName.ifEmpty { "已登录" } else "未登录", style = MaterialTheme.typography.titleSmall)
            Text(if (loggedIn) "已解锁高清/番剧" else "登录解锁高清/番剧", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!loggedIn) {
            FilledTonalButton(onClick = onLogin) { Text("登录") }
        }
    }
}

@Composable
private fun QualityGrid(videos: List<com.bbdroid.app.bilibili.VideoTrack>, selectedIdx: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        videos.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                rowItems.forEach { v ->
                    val i = videos.indexOf(v)
                    FilterChip(
                        selected = i == selectedIdx,
                        onClick = { onSelect(i) },
                        enabled = enabled,
                        label = { Text(v.dfn, maxLines = 1) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ExtraCheck(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InProgressCard(title: String, pct: Float, info: String, paused: Boolean, onTogglePause: () -> Unit, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = onTogglePause) {
                    Icon(if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, contentDescription = if (paused) "继续" else "暂停")
                }
                TextButton(onClick = onStop) { Text("停止", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (pct >= 0f) {
                LinearProgressIndicator(progress = { pct.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = BrandPink)
                if (info.isNotEmpty()) Text(info, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandPink)
            }
        }
    }
}

@Composable
private fun BatchTaskCard(tasks: List<DownloadTask>, status: String, onStop: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().then(glassPanel())) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("批量队列", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onStop) { Text("停止", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            tasks.filter { it.parentKey == null }.forEach { p ->
                if (p.isParent) {
                    Text("📦 " + p.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(progress = { p.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = BrandPink)
                }
                tasks.filter { it.parentKey == p.key }.forEach { c -> ChildTaskRow(c) }
            }
        }
    }
}

@Composable
private fun ChildTaskRow(task: DownloadTask) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // 竖向连接线
        Box(
            Modifier
                .width(2.dp)
                .height(28.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Column(Modifier.weight(1f)) {
            Text("└ " + task.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.progress >= 0f) {
                LinearProgressIndicator(progress = { task.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = BrandPink)
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = BrandPink)
            }
        }
        Text(
            if (task.progress >= 0f) (task.progress * 100).toInt().toString() + "%" else task.status,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- 工具 ----------

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

private fun fmtDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
