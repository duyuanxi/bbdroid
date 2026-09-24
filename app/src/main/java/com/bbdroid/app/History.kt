package com.bbdroid.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

object History {
    data class Item(
        val id: Long = 0,
        val title: String,
        val cover: String,
        val quality: String,
        val size: Long = 0,
        val path: String = "",
        val time: Long,
        val uri: String = "",
        val bvid: String = "",
    )

    // 显示用的条目：item + 文件是否仍存在
    data class Entry(val item: Item, val exists: Boolean)

    // 观察历史（Room Flow，新增/删除自动刷新）
    fun observeEntries(context: Context): Flow<List<Entry>> =
        BbdroidDatabase.get(context).historyDao().observeAll()
            .map { list ->
                list.map { e ->
                    Entry(e.toItem(), e.uri.isEmpty() || uriExists(context, e.uri))
                }
            }
            .flowOn(Dispatchers.IO)

    suspend fun loadEntries(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        BbdroidDatabase.get(context).historyDao().loadAll().map { e ->
            Entry(e.toItem(), e.uri.isEmpty() || uriExists(context, e.uri))
        }
    }

    suspend fun add(context: Context, item: Item) {
        withContext(Dispatchers.IO) {
            BbdroidDatabase.get(context).historyDao().insert(item.toEntity())
        }
    }

    suspend fun remove(context: Context, item: Item) {
        withContext(Dispatchers.IO) {
            val dao = BbdroidDatabase.get(context).historyDao()
            if (item.id > 0) dao.deleteById(item.id)
            else dao.deleteByTitleTime(item.title, item.time)
        }
    }

    // 删除本地视频文件
    fun deleteLocalFile(context: Context, uriStr: String): Boolean {
        if (uriStr.isEmpty()) return false
        return try {
            val u = Uri.parse(uriStr)
            when (u.scheme) {
                "content" -> context.contentResolver.delete(u, null, null) > 0
                "file" -> File(u.path ?: "").delete()
                else -> false
            }
        } catch (e: Exception) { false }
    }

    private fun uriExists(context: Context, uriStr: String): Boolean {
        return try {
            val u = Uri.parse(uriStr)
            val stream = context.contentResolver.openInputStream(u)
            if (stream == null) false else { stream.close(); true }
        } catch (e: Exception) { false }
    }
}

fun History.Item.toEntity() = HistoryEntity(
    id = id,
    title = title,
    cover = cover,
    quality = quality,
    size = size,
    path = path,
    time = time,
    uri = uri,
    bvid = bvid,
)

fun HistoryEntity.toItem() = History.Item(
    id = id,
    title = title,
    cover = cover,
    quality = quality,
    size = size,
    path = path,
    time = time,
    uri = uri,
    bvid = bvid,
)
