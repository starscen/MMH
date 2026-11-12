package com.mmh.app.ui.reports

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.mmh.app.data.local.TokenProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * 报表页沿用网页端的 /reports（服务端渲染的完整报表），App 侧只做壳：
 * 把登录时拿到的会话 Cookie 注入 WebView，直接免登录打开。
 */
@HiltViewModel
class ReportsWebViewModel @Inject constructor(
    tokenProvider: TokenProvider
) : ViewModel() {
    val serverUrl: String = tokenProvider.getServerUrl().trimEnd('/')
    val sessionCookie: String = tokenProvider.getSessionCookie()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReportsScreen(viewModel: ReportsWebViewModel = hiltViewModel()) {
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isLoading = false
                        }
                    }
                    installSessionCookies(viewModel.serverUrl, viewModel.sessionCookie)
                    webViewRef = this
                    loadUrl("${viewModel.serverUrl}/reports")
                }
            }
        )
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

private val COOKIE_ATTRIBUTE_NAMES = setOf(
    "path", "domain", "expires", "max-age", "httponly", "secure", "samesite", "priority", "version", "comment"
)

/**
 * App 的登录态是 OkHttp 截下来的 Set-Cookie 串（可能带 Path/HttpOnly 等属性），
 * WebView 需要的是裸的 name=value，所以这里拆出成对的部分逐个 setCookie。
 */
private fun cookiePairs(raw: String): List<String> =
    raw.split(';', ',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.contains('=') }
        .filter { it.substringBefore('=').trim().lowercase() !in COOKIE_ATTRIBUTE_NAMES }
        .map { it.replace("\r", "").replace("\n", "") }
        .distinctBy { it.substringBefore('=').trim() }

private fun installSessionCookies(serverUrl: String, rawCookie: String) {
    if (rawCookie.isBlank()) return
    val manager = CookieManager.getInstance()
    manager.setAcceptCookie(true)
    cookiePairs(rawCookie).forEach { pair -> manager.setCookie(serverUrl, pair) }
    manager.flush()
}
