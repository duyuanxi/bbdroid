package com.bbdroid.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.flow.first
import java.io.File

data class SavedFile(val display: String, val uri: Uri?)

object Storage {
    // 把文件保存到公开的 Movies/BBDroid 目录，返回显示用的位置
    fun saveToMovies(context: Context, file: File, displayName: String): SavedFile {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeOf(displayName))
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/BBDroid")
            }
            val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("无法写入电影目录")
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            SavedFile("Movies/BBDroid/" + displayName, uri)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "BBDroid")
            dir.mkdirs()
            val dest = File(dir, displayName)
            file.copyTo(dest, overwrite = true)
            SavedFile(dest.absolutePath, Uri.fromFile(dest))
        }
    }

    // 保存任意文件到公开 Downloads 目录（封面/弹幕/字幕等）
    fun saveToDownloads(context: Context, file: File, displayName: String): SavedFile {
        return if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(displayName))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BBDroid/附赠")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("无法写入下载目录")
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            SavedFile("Download/BBDroid/附赠/" + displayName, uri)
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "BBDroid")
            dir.mkdirs()
            val dest = File(dir, displayName)
            file.copyTo(dest, overwrite = true)
            SavedFile(dest.absolutePath, Uri.fromFile(dest))
        }
    }

    // 保存视频到默认的专属目录（Download/BBDroid/视频，独立于相册）
    fun saveVideoDefault(context: Context, file: File, displayName: String): SavedFile {
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(displayName))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/BBDroid/视频")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("无法写入视频目录")
            context.contentResolver.openOutputStream(uri)!!.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            return SavedFile("Download/BBDroid/视频/" + displayName, uri)
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "BBDroid/视频")
        dir.mkdirs()
        val dest = File(dir, displayName)
        file.copyTo(dest, overwrite = true)
        return SavedFile(dest.absolutePath, Uri.fromFile(dest))
    }

    // 保存视频：有自定义目录则写自定义目录，否则写专属视频目录（自定义目录来自 DataStore）
    suspend fun saveVideo(context: Context, file: File, displayName: String): SavedFile {
        val dir = SettingsStore.saveDir(context)
        if (dir.isEmpty()) return saveVideoDefault(context, file, displayName)
        return try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(dir))
            val doc = tree?.createFile(mimeOf(displayName), displayName) ?: throw Exception("无法写入所选文件夹")
            context.contentResolver.openOutputStream(doc.uri)!!.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            SavedFile("自定义文件夹/" + displayName, doc.uri)
        } catch (e: Exception) {
            saveVideoDefault(context, file, displayName)
        }
    }

    private fun mimeOf(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            "webm" -> "video/webm"
            "ts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "gif" -> "image/gif"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "xml" -> "application/xml"
            "srt" -> "application/x-subrip"
            "ass" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
}
