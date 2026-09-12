package com.bbdroid.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryTab(context: Context, modifier: Modifier = Modifier, isVisible: Boolean = true) {
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf<List<History.Entry>>(emptyList()) }
    var actionTarget by remember { mutableStateOf<History.Entry?>(null) }
    var deleteTarget by remember { mutableStateOf<History.Entry?>(null) }
    var deleteLocal by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible) history = History.loadEntries(context)
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("下载历史", style = MaterialTheme.typography.titleMedium)

        // 进行中的下载任务（含批量/合集层级进度）
        val tasks = DownloadSession.tasks
        if (tasks.isNotEmpty()) {
            Text("正在下载", style = MaterialTheme.typography.titleSmall)
            tasks.filter { it.parentKey == null }.forEach { p ->
                DownloadTaskRow(p, indent = false)
                tasks.filter { it.parentKey == p.key }.forEach { c -> DownloadTaskRow(c, indent = true) }
            }
            HorizontalDivider()
        }

        if (history.isEmpty() && tasks.isEmpty()) {
            Text("暂无下载记录", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        history.forEach { e ->
            key(e.item.title, e.item.quality, e.item.time) {
                SwipeableHistoryCard(
                    entry = e,
                    onClick = { actionTarget = e },
                    onSwipeDelete = { deleteTarget = e; deleteLocal = false },
                )
            }
        }
    }

    // 点击卡片：操作弹窗
    actionTarget?.let { e ->
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(e.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (e.item.bvid.isNotEmpty()) {
                        TextButton(onClick = { ReDownloadRequest.request(e.item.bvid); actionTarget = null }) { Text("重新下载") }
                    }
                    if (e.exists && e.item.uri.isNotEmpty()) {
                        TextButton(onClick = { shareVideo(context, Uri.parse(e.item.uri), e.item.title); actionTarget = null }) { Text("分享") }
                    }
                    if (e.exists && e.item.uri.isNotEmpty()) {
                        TextButton(onClick = { openFile(context, Uri.parse(e.item.uri), e.item.title); actionTarget = null }) { Text("打开") }
                    }
                    TextButton(onClick = { deleteTarget = e; deleteLocal = false; actionTarget = null }) { Text("删除") }
                }
            },
            confirmButton = { TextButton(onClick = { actionTarget = null }) { Text("取消") } },
        )
    }

    // 删除确认弹窗
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除历史记录") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("确定删除这条历史记录吗？")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = deleteLocal,
                            onCheckedChange = { deleteLocal = it },
                            enabled = t.item.uri.isNotEmpty(),
                        )
                        Text("同时删除本地文件", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteLocal && t.item.uri.isNotEmpty()) History.deleteLocalFile(context, t.item.uri)
                    History.remove(context, t.item)
                    deleteTarget = null
                    scope.launch { history = History.loadEntries(context) }
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun DownloadTaskRow(task: DownloadTask, indent: Boolean) {
    Card(modifier = Modifier.fillMaxWidth().padding(start = if (indent) 20.dp else 0.dp)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                val prefix = if (task.isParent) "📦 " else if (indent) "└ " else ""
                Text(prefix + task.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(task.status, style = MaterialTheme.typography.labelSmall)
            }
            Text(
                if (task.progress >= 0f) (task.progress * 100).toInt().toString() + "%" else "…",
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (task.progress >= 0f) {
            LinearProgressIndicator(progress = { task.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryCard(entry: History.Entry, onClick: () -> Unit, onSwipeDelete: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) onSwipeDelete()
            false
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFE53935)),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("删除", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 24.dp))
            }
        },
    ) {
        HistoryCardContent(entry, onClick)
    }
}

@Composable
private fun HistoryCardContent(entry: History.Entry, onClick: () -> Unit) {
    val exists = entry.exists
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (exists) 1f else 0.55f)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val coverBmp = rememberCover(entry.item.cover)
                if (coverBmp != null) Image(
                    coverBmp.asImageBitmap(),
                    contentDescription = "封面",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.weight(1f)) {
                Text(entry.item.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.item.quality, style = MaterialTheme.typography.bodySmall)
                Text(
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entry.item.time)),
                    style = MaterialTheme.typography.labelSmall
                )
                if (!exists) {
                    Text("本地文件已删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun rememberCover(url: String): Bitmap? {
    val bmp by produceState<Bitmap?>(null, url) {
        value = if (url.isNotEmpty()) ImageUtil.load(url) else null
    }
    return bmp
}
