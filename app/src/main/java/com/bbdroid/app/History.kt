package com.bbdroid.app

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object History {
    data class Item(
        val title: String,
        val cover: String,
        val quality: String,
        val time: Long,
        val uri: String = "",
        val bvid: String = "",
    )

    // 显示用的条目：item + 文件是否仍存在
    data class Entry(val item: Item, val exists: Boolean)

    private const val PREFS = "bbdroid"
    private const val KEY = "history"

    fun load(context: Context): List<Item> {
        val s = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(s)
            val list = mutableListOf<Item>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Item(
                    o.optString("title"),
                    o.optString("cover"),
                    o.optString("quality"),
                    o.optLong("time"),
                    o.optString("uri"),
                    o.optString("bvid"),
                ))
            }
            list
        } catch (e: Exception) { emptyList() }
    }

    // 加载并标注文件是否仍存在（uri 为空的老记录视为存在）
    suspend fun loadEntries(context: Context): List<Entry> = withContext(Dispatchers.IO) {
        load(context).map { Entry(it, it.uri.isEmpty() || uriExists(context, it.uri)) }
    }

    fun add(context: Context, item: Item) {
        val list = load(context).toMutableList()
        list.removeAll { it.title == item.title && it.quality == item.quality }
        list.add(0, item)
        if (list.size > 50) list.subList(50, list.size).clear()
        save(context, list)
    }

    fun remove(context: Context, item: Item) {
        val list = load(context).toMutableList()
        list.removeAll { it.title == item.title && it.time == item.time }
        save(context, list)
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

    private fun save(context: Context, list: List<Item>) {
        val arr = JSONArray()
        for (it in list) {
            val o = JSONObject()
            o.put("title", it.title)
            o.put("cover", it.cover)
            o.put("quality", it.quality)
            o.put("time", it.time)
            o.put("uri", it.uri)
            o.put("bvid", it.bvid)
            arr.put(o)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).commit()
    }
}
