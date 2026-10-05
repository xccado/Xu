package com.xse.sesame.ui.miuix

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 通用异步头像加载组件：OkHttp + 支付宝 UA/Referer + Bitmap 内存缓存。
 * 供好友统计页、好友选择列表等所有展示好友头像的入口复用。
 */
private val avatarCache = HashMap<String, Bitmap>()

@Composable
fun MiuixAsyncAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12,
    circle: Boolean = false,
) {
    var bitmap by remember(url) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            bitmap = null
            return@LaunchedEffect
        }
        val cached = avatarCache[url]
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        bitmap = withContext(Dispatchers.IO) {
            try {
                val finalUrl = if (url.startsWith("//")) "https:$url" else url
                val req = Request.Builder()
                    .url(finalUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 12; M2007J3SC Build/SKQ1.211006.001) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/89.0.4389.72 Mobile Safari/537.36 AlipayClient/12.12.12.8000")
                    .header("Referer", "https://render.alipay.com/")
                    .build()
                OkHttpClient().newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.bytes()?.let {
                            BitmapFactory.decodeByteArray(it, 0, it.size)?.also { bmp ->
                                avatarCache[url] = bmp
                            }
                        }
                    } else {
                        null
                    }
                }
            } catch (t: Throwable) {
                null
            }
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(if (circle) CircleShape else RoundedCornerShape(cornerRadius.dp))
        )
    } else {
        Box(modifier)
    }
}
