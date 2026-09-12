package com.bbdroid.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

object ImageUtil {
    private val client = OkHttpClient.Builder().build()
    private val cache = HashMap<String, Bitmap>()

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (url.isEmpty()) return@withContext null
        cache[url]?.let { return@withContext it }
        try {
            val u = if (url.startsWith("http://")) url.replaceFirst("http://", "https://") else url
            val resp = client.newCall(Request.Builder().url(u).header("Referer", "https://www.bilibili.com/").build()).execute()
            val bmp = resp.body?.byteStream()?.use { BitmapFactory.decodeStream(it) }
            if (bmp != null) cache[url] = bmp
            bmp
        } catch (e: Exception) {
            null
        }
    }
}
