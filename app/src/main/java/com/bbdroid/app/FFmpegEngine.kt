package com.bbdroid.app

import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.arthenica.ffmpegkit.AbstractSession
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/** All native access goes through this boundary, including FFprobe and diagnostics. */
object FFmpegEngine {
    val supportedAbis = listOf("arm64-v8a", "x86_64")
    data class Result(val success: Boolean, val command: String, val output: String, val durationMs: Long, val exitCode: Int? = null)
    data class Status(val available: Boolean, val description: String)
    @Volatile private var nativeStatus: Status? = null

    private fun initialize(): Status = synchronized(this) {
        nativeStatus ?: try {
            require(Process.is64Bit() && Build.SUPPORTED_ABIS.any { it in supportedAbis }) {
                "此安装包只支持 arm64-v8a / x86_64 的 64 位系统。设备 ABI：${Build.SUPPORTED_ABIS.joinToString()}"
            }
            // Preload the AAR's dependency graph before FFmpegKit's static initializer.
            // Keep useLegacyPackaging=true so DT_NEEDED libraries exist in nativeLibraryDir.
            listOf("c++_shared", "avutil", "swresample", "swscale", "avcodec", "avformat", "avfilter", "avdevice")
                .forEach(System::loadLibrary)
            Status(true, "已加载 · FFmpeg ${FFmpegKitConfig.getVersion()}")
        } catch (t: Throwable) {
            rethrowFatal(t)
            Status(false, "引擎加载失败：${describe(t)}\n安装包 ABI：${supportedAbis.joinToString()}；设备 ABI：${Build.SUPPORTED_ABIS.joinToString()}")
        }.also { nativeStatus = it }
    }

    suspend fun status(): Status = withContext(Dispatchers.IO) { initialize() }

    suspend fun run(arguments: List<String>): Result = execute(arguments, probe = false)
    suspend fun probe(path: String): Result = execute(listOf("-v", "error", "-print_format", "json", "-show_format", "-show_streams", path), probe = true)

    private suspend fun execute(arguments: List<String>, probe: Boolean): Result = withContext(Dispatchers.IO) {
        val started = SystemClock.elapsedRealtime()
        val command = arguments.joinToString(" ")
        val status = initialize()
        if (!status.available) return@withContext Result(false, command, status.description, 0)
        try {
            suspendCancellableCoroutine { continuation ->
                val running = AtomicReference<AbstractSession?>()
                continuation.invokeOnCancellation { running.get()?.cancel() }
                fun complete(session: AbstractSession) {
                    val success = ReturnCode.isSuccess(session.returnCode)
                    val output = if (probe && success) session.output.orEmpty() else listOfNotNull(
                        session.allLogsAsString?.takeIf { it.isNotBlank() },
                        session.failStackTrace?.takeIf { it.isNotBlank() },
                        if (!success) "退出码：${session.returnCode?.value ?: "未返回"}" else null,
                    ).joinToString("\n")
                    continuation.resume(Result(success, command, output, SystemClock.elapsedRealtime() - started, session.returnCode?.value))
                }
                val session: AbstractSession = if (probe) FFprobeKit.executeWithArgumentsAsync(arguments.toTypedArray()) { complete(it) }
                    else FFmpegKit.executeWithArgumentsAsync(arguments.toTypedArray()) { complete(it) }
                running.set(session)
                if (!continuation.isActive) session.cancel()
            }
        } catch (t: Throwable) {
            rethrowFatal(t)
            Result(false, command, describe(t), SystemClock.elapsedRealtime() - started)
        }
    }

    /** No user file is required: really encode, probe and remux a short synthetic clip. */
    suspend fun selfTest(cacheDir: File, onLog: suspend (String) -> Unit) = withContext(Dispatchers.IO) {
        val dir = File(cacheDir, "ffmpeg-check-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        try {
            val clip = File(dir, "source.mp4")
            val encoded = run(listOf("-y", "-f", "lavfi", "-i", "color=c=gray:s=128x128:r=10:d=0.5", "-f", "lavfi", "-i", "sine=frequency=440:duration=0.5", "-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac", "-shortest", clip.absolutePath))
            onLog(if (encoded.success && clip.length() > 0) "✓ 视频和音频编码通过" else "✕ 编码失败\n${encoded.output.takeLast(1800)}")
            if (!encoded.success || clip.length() == 0L) return@withContext
            val metadata = probe(clip.absolutePath)
            val valid = metadata.success && metadata.output.contains("\"streams\"")
            onLog(if (valid) "✓ FFprobe 读取通过" else "✕ FFprobe 失败\n${metadata.output.takeLast(1800)}")
            val mux = File(dir, "remux.mp4")
            val merged = run(listOf("-y", "-i", clip.absolutePath, "-map", "0:v:0", "-map", "0:a:0", "-c", "copy", mux.absolutePath))
            onLog(if (merged.success && mux.length() > 0) "✓ 音视频无损封装通过" else "✕ 封装失败\n${merged.output.takeLast(1800)}")
        } finally { dir.deleteRecursively() }
    }

    private fun describe(t: Throwable): String = generateSequence(t) { it.cause }.take(8)
        .joinToString("\n") { "${it.javaClass.simpleName}: ${it.message.orEmpty()}" }
    private fun rethrowFatal(t: Throwable) {
        if (t is CancellationException || t is VirtualMachineError || t is ThreadDeath) throw t
    }
}
