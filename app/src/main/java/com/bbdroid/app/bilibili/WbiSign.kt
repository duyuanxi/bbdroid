package com.bbdroid.app.bilibili

import java.security.MessageDigest

object WbiSign {
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
    )

    fun md5Hex(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    fun getMixinKey(orig: String): String {
        val sb = StringBuilder()
        for (idx in mixinKeyEncTab) sb.append(orig[idx])
        return sb.toString()
    }

    // img_url / sub_url 取文件名(去扩展名)
    private fun fileName(url: String): String {
        val afterSlash = url.substringAfterLast("/")
        return afterSlash.substringBeforeLast(".")
    }

    fun mixinKeyFrom(imgUrl: String, subUrl: String): String =
        getMixinKey(fileName(imgUrl) + fileName(subUrl))

    fun sign(params: String, mixinKey: String): String =
        params + "&w_rid=" + md5Hex(params + mixinKey)
}
