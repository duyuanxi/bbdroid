package com.bbdroid.app

import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FFmpegEngine {

    data class Result(
        val success: Boolean,
        val command: String,
        val output: String,
        val durationMs: Long,
    )

    suspend fun run(command: String): Result = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val session = FFmpegKit.execute(command)
            val elapsed = System.currentTimeMillis() - start
            Result(
                success = ReturnCode.isSuccess(session.returnCode),
                command = command,
                output = session.allLogsAsString,
                durationMs = elapsed,
            )
        } catch (t: Throwable) {
            Result(
                success = false,
                command = command,
                output = "异常: " + (t.message ?: t.toString()),
                durationMs = System.currentTimeMillis() - start,
            )
        }
    }
}
