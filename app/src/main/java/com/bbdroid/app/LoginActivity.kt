package com.bbdroid.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import com.bbdroid.app.bilibili.BiliApi

class LoginActivity : Activity() {

    private lateinit var webView: WebView

    private fun collectCookie(): String {
        val cm = CookieManager.getInstance()
        val domains = listOf("https://bilibili.com", "https://www.bilibili.com", "https://passport.bilibili.com")
        val seen = LinkedHashSet<String>()
        for (d in domains) {
            val c = cm.getCookie(d) ?: ""
            if (c.isNotEmpty()) c.split("; ").forEach { seen.add(it) }
        }
        return seen.joinToString("; ")
    }

    private fun tryFinishLogin(): Boolean {
        val cookie = collectCookie()
        if (cookie.contains("SESSDATA")) {
            Auth.save(this, cookie)
            BiliApi.cookie = cookie
            Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
            return true
        }
        return false
    }

    private val loginCheck = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            if (!tryFinishLogin()) webView.postDelayed(this, 1500)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/100.0 Safari/537.36"
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl("https://passport.bilibili.com/login")

        val doneBtn = Button(this)
        doneBtn.text = "我已完成登录，返回"
        doneBtn.setOnClickListener {
            if (!tryFinishLogin()) {
                Toast.makeText(this, "还没检测到登录，请先在网页里完成登录", Toast.LENGTH_LONG).show()
            }
        }

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        layout.addView(doneBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(layout)

        webView.postDelayed(loginCheck, 1500)
    }
}
