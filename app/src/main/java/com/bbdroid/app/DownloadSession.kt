package com.bbdroid.app

import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

// 进行中的下载任务（历史页显示进度，批量/合集为层级：父任务总进度 + 子任务单条进度）
data class DownloadTask(
    val key: String,
    val title: String,
    val quality: String = "",
    val progress: Float = 0f,        // 0..1，-1 表示不确定（合并中）
    val status: String = "",          // 下载中 / 合并中 / 保存中
    val parentKey: String? = null,    // 批量/合集子任务的父 key
    val isParent: Boolean = false,    // 是否批量/合集父任务
    val childTotal: Int = 0,          // 父任务的子任务总数
)

// 下载会话：状态与协程放到单例，App 切后台 / Activity 被系统重建后，下载仍继续、进度不丢失
object DownloadSession {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val downloadingState = mutableStateOf(false)
    val progressPctState = mutableStateOf(-1f)
    val progressInfoState = mutableStateOf("")
    val downloadStatusState = mutableStateOf("")
    val batchRunningState = mutableStateOf(false)
    val batchStatusState = mutableStateOf("")
    val collectionRunningState = mutableStateOf(false)
    val collectionStatusState = mutableStateOf("")
    val openUriState = mutableStateOf<Uri?>(null)
    val openNameState = mutableStateOf("")
    val showDialogState = mutableStateOf(false)
    val dialogMsgState = mutableStateOf("")
    val dialogUriState = mutableStateOf<Uri?>(null)
    val dialogNameState = mutableStateOf("")
    val downloadJobState = mutableStateOf<Job?>(null)
    val batchJobState = mutableStateOf<Job?>(null)
    val collectionJobState = mutableStateOf<Job?>(null)

    // 进行中的下载任务（历史页显示进度）
    val tasks = mutableStateListOf<DownloadTask>()

    fun upsertTask(t: DownloadTask) {
        val i = tasks.indexOfFirst { it.key == t.key }
        if (i >= 0) tasks[i] = t else tasks.add(t)
    }

    fun updateTask(key: String, progress: Float? = null, status: String? = null) {
        val i = tasks.indexOfFirst { it.key == key }
        if (i >= 0) {
            val o = tasks[i]
            tasks[i] = o.copy(progress = progress ?: o.progress, status = status ?: o.status)
        }
    }

    fun removeTask(key: String) { tasks.removeAll { it.key == key } }

    fun clearTasks() { tasks.clear() }
}
