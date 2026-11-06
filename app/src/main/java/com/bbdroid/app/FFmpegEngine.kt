package com.bbdroid.app

import android.os.SystemClock
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

object FFmpegEngine {
    data class Result(val success: Boolean, val command: String, val output: String, val durationMs: Long)

    suspend fun run(command: String): Result = withContext(Dispatchers.IO) {
        try { run(FFmpegKitConfig.parseArguments(command).toList()) }
        catch (e: LinkageError) { Result(false, command, "FFmpeg 原生引擎不可用：${e.message}", 0) }
    }

    suspend fun run(arguments: List<String>): Result = withContext(Dispatchers.IO) {
        val start = SystemClock.elapsedRealtime()
        val command = arguments.joinToString(" ")
        try {
            suspendCancellableCoroutine { continuation ->
                val sessionId = AtomicLong(-1)
                continuation.invokeOnCancellation { sessionId.get().takeIf { it >= 0 }?.let(FFmpegKit::cancel) }
                val session = FFmpegKit.executeWithArgumentsAsync(arguments.toTypedArray()) { completed ->
                    val result = Result(ReturnCode.isSuccess(completed.returnCode), command,
                        completed.allLogsAsString.orEmpty(), SystemClock.elapsedRealtime() - start)
                    if (continuation.isActive) continuation.resume(result)
                }
                sessionId.set(session.sessionId)
                if (!continuation.isActive) FFmpegKit.cancel(session.sessionId)
            }
        } catch (e: LinkageError) {
            Result(false, command, "FFmpeg 原生引擎不可用：${e.message}", SystemClock.elapsedRealtime() - start)
        }
    }
}
