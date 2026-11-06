package com.bbdroid.app

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import java.io.File

// A successful save always refers to a complete, published file.
data class SavedFile(val display: String, val uri: Uri?)

object Storage {
    fun saveToMovies(context: Context, file: File, displayName: String): SavedFile =
        savePublic(context, file, displayName, Environment.DIRECTORY_MOVIES, "BBDroid")

    fun saveToDownloads(context: Context, file: File, displayName: String): SavedFile =
        savePublic(context, file, displayName, Environment.DIRECTORY_DOWNLOADS, "BBDroid/附赠")

    fun saveVideoDefault(context: Context, file: File, displayName: String): SavedFile {
        val category = when {
            mimeOf(displayName).startsWith("audio/") -> "音频"
            mimeOf(displayName).startsWith("image/") -> "图片"
            else -> "视频"
        }
        return savePublic(context, file, displayName, Environment.DIRECTORY_DOWNLOADS, "BBDroid/$category")
    }

    suspend fun saveVideo(context: Context, file: File, displayName: String): SavedFile {
        require(file.isFile && file.length() > 0) { "输出文件不存在或为空" }
        val dir = SettingsStore.saveDir(context)
        if (dir.isEmpty()) return saveVideoDefault(context, file, displayName)
        var created: DocumentFile? = null
        return try {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(dir))
            val doc = tree?.createFile(mimeOf(displayName), sanitizeFilename(displayName)) ?: error("无法写入所选文件夹")
            created = doc
            val output = context.contentResolver.openOutputStream(doc.uri) ?: error("无法打开保存位置")
            output.use { out -> file.inputStream().use { it.copyTo(out) } }
            SavedFile("自定义文件夹/${doc.name ?: displayName}", doc.uri)
        } catch (e: CancellationException) {
            created?.delete()
            throw e
        } catch (_: Exception) {
            created?.delete()
            saveVideoDefault(context, file, displayName)
        }
    }

    private fun savePublic(context: Context, source: File, name: String, directory: String, subfolder: String): SavedFile {
        require(source.isFile && source.length() > 0) { "输出文件不存在或为空" }
        val displayName = sanitizeFilename(name)
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= 29) {
            val collection = if (directory == Environment.DIRECTORY_MOVIES) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(displayName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/$subfolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: error("无法创建保存文件")
            try {
                val stream = resolver.openOutputStream(uri) ?: error("无法打开保存文件")
                stream.use { out -> source.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return SavedFile("$directory/$subfolder/$displayName", uri)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        val base = Environment.getExternalStoragePublicDirectory(directory)
        val folder = File(base, subfolder).apply { check(isDirectory || mkdirs()) { "无法创建保存目录" } }
        val stem = displayName.substringBeforeLast('.', displayName)
        val extension = displayName.substringAfterLast('.', "")
        var dest = File(folder, displayName)
        var suffix = 1
        while (dest.exists()) {
            dest = File(folder, "$stem (${suffix++})" + if (extension.isEmpty()) "" else ".$extension")
        }
        try { source.copyTo(dest); return SavedFile(dest.absolutePath, androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", dest)) }
        catch (e: Exception) { dest.delete(); throw e }
    }

    internal fun mimeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "flv" -> "video/x-flv"
        "wmv" -> "video/x-ms-wmv"
        "webm" -> "video/webm"
        "ts", "m2ts" -> "video/mp2t"
        "mp3" -> "audio/mpeg"
        "aac" -> "audio/aac"
        "m4a" -> "audio/mp4"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "ogg" -> "audio/ogg"
        "gif" -> "image/gif"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "xml" -> "application/xml"
        "srt" -> "application/x-subrip"
        "ass" -> "text/plain"
        else -> "application/octet-stream"
    }
}
