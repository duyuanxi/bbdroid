package com.bbdroid.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTab(context: Context, modifier: Modifier = Modifier, onGoDownload: () -> Unit) {
    val scope = rememberCoroutineScope()
    val entries by History.observeEntries(context).collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf<History.Entry?>(null) }
    var deleteTarget by remember { mutableStateOf<History.Entry?>(null) }
    var deleteLocal by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 标题行
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("下载历史", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(entries.size.toString() + " 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (entries.isEmpty()) {
            item {
                EmptyHistory(onGoDownload)
            }
        } else {
            items(entries, key = { it.item.id to it.item.time }) { e ->
                key(e.item.id, e.item.time) {
                    SwipeableHistoryCard(
                        entry = e,
                        onClick = { selected = e },
                        onSwipeDelete = { deleteTarget = e; deleteLocal = false },
                    )
                }
            }
        }
    }

    // 详情底部抽屉
    selected?.let { e ->
        ModalBottomSheet(
            onDismissRequest = { selected = null },
            shape = DialogShape,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            DetailSheet(context, e, onClose = { selected = null }, onDelete = { selected = null; deleteTarget = e; deleteLocal = false })
        }
    }

    // 删除确认
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
                    scope.launch { History.remove(context, t.item) }
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun EmptyHistory(onGoDownload: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("暂无下载记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalButton(onClick = onGoDownload) { Text("去下载") }
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
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.error),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(
                    Modifier.width(76.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onError)
                    Text("删除", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onError)
                }
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
            .alpha(if (exists) 1f else 0.6f),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 4:3 封面
            Box(
                Modifier
                    .width(88.dp)
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                val coverBmp = rememberCover(entry.item.cover)
                if (coverBmp != null) {
                    Image(
                        coverBmp.asImageBitmap(),
                        contentDescription = "封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = if (exists) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.item.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    entry.item.quality.ifEmpty { null },
                    if (entry.item.size > 0) fmtSize(entry.item.size) else null,
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(entry.item.time)),
                ).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!exists) {
                    Text(
                        "本地文件已删除",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailSheet(context: Context, entry: History.Entry, onClose: () -> Unit, onDelete: () -> Unit) {
    val item = entry.item
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val coverBmp = rememberCover(item.cover)
        if (coverBmp != null) {
            Image(
                coverBmp.asImageBitmap(),
                contentDescription = "封面",
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.quality.isNotEmpty()) MetaLine("清晰度", item.quality)
            if (item.size > 0) MetaLine("大小", fmtSize(item.size))
            MetaLine("时间", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(item.time)))
            if (item.path.isNotEmpty()) MetaLine("保存位置", item.path)
            if (!entry.exists) MetaLine("状态", "本地文件已删除")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entry.exists && item.uri.isNotEmpty()) {
                FilledTonalButton(onClick = { openFile(context, Uri.parse(item.uri), item.title); onClose() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("播放")
                }
                FilledTonalButton(onClick = { shareVideo(context, Uri.parse(item.uri), item.title); onClose() }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("分享")
                }
            }
            if (item.bvid.isNotEmpty()) {
                FilledTonalButton(onClick = { ReDownloadRequest.request(item.bvid); onClose() }, modifier = Modifier.weight(1f)) { Text("重新下载") }
            }
        }
        TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
            Text("删除记录", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun rememberCover(url: String): Bitmap? {
    val bmp by produceState<Bitmap?>(null, url) {
        value = if (url.isNotEmpty()) ImageUtil.load(url) else null
    }
    return bmp
}

private fun fmtSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0)
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> bytes.toString() + " B"
    }
}
