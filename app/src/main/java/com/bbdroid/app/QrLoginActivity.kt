package com.bbdroid.app

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.bbdroid.app.bilibili.BiliApi
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrLoginActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                QrLoginScreen(context = this, onDone = { finish() })
            }
        }
    }
}

@Composable
fun QrLoginScreen(context: Context, onDone: () -> Unit) {
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var status by remember { mutableStateOf("正在获取二维码…") }
    var qrKey by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun pollOnce() {
        scope.launch {
            withContext(Dispatchers.IO) {
                if (qrKey.isEmpty()) return@withContext
                val r = BiliApi.qrPoll(qrKey)
                when {
                    r.code == 86038 -> status = "二维码已过期 (86038)，请返回重试"
                    r.code == 86101 -> status = "等待扫码 (86101)"
                    r.code == 86090 -> status = "已扫码，请在手机上确认 (86090)"
                    else -> {
                        var cookie = r.cookie
                        if (!cookie.contains("SESSDATA")) {
                            cookie = r.url.substringAfter("?").replace("&", ";").replace(",", "%2C")
                        }
                        if (cookie.contains("SESSDATA")) {
                            Auth.save(context, cookie)
                            BiliApi.cookie = cookie
                            status = "登录成功！"
                            delay(400)
                            withContext(Dispatchers.Main) { onDone() }
                        } else {
                            status = "登录响应异常，缺少 SESSDATA，请重试"
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val (key, url) = BiliApi.qrGenerate()
                qrKey = key
                qr = generateQrBitmap(url, 640)
                status = "请用 B站App 扫描二维码 (等待扫码)"
            } catch (e: Exception) {
                status = "获取二维码失败: " + (e.message ?: e.toString())
            }
        }
        // 自动轮询
        while (true) {
            delay(1500)
            if (qrKey.isNotEmpty()) pollOnce()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("扫码登录 B站", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        val b = qr
        if (b != null) {
            Image(
                bitmap = b.asImageBitmap(),
                contentDescription = "登录二维码",
                modifier = Modifier.size(280.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(status, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(20.dp))
        Button(onClick = { pollOnce() }) {
            Text("我已扫码，检查登录")
        }
    }
}

fun generateQrBitmap(content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix: BitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val w = matrix.width
    val h = matrix.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            pixels[y * w + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    return bitmap
}
