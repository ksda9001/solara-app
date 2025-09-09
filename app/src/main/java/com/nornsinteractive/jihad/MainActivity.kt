package com.nornsinteractive.jihad

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nornsinteractive.jihad.ui.theme.TestTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullScreenContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(true)

        fullScreenContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        setContent {
            AndroidView(factory = {
                fullScreenContainer.apply {
                    removeAllViews()
                    addView(ComposeView(context).apply {
                        setContent {
                            TestTheme {
                                WebPage(
                                    onBackPressed = {
                                        if (::webView.isInitialized && webView.canGoBack()) {
                                            webView.goBack()
                                        } else {
                                            finish()
                                        }
                                    },
                                    getWebView = { instance -> webView = instance },
                                    onShowCustomView = { view, callback ->
                                        showFullScreenView(view, callback)
                                    },
                                    onHideCustomView = {
                                        hideFullScreenView()
                                    }
                                )
                            }
                        }
                    })
                }
            })
        }
    }

    private fun showFullScreenView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        fullScreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        webView.visibility = View.GONE
    }

    private fun hideFullScreenView() {
        customView?.let {
            fullScreenContainer.removeView(it)
            customView = null
        }
        customViewCallback?.onCustomViewHidden()
        webView.visibility = View.VISIBLE
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d("Orientation", "横屏")
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d("Orientation", "竖屏")
        }
    }

    // 同步获取链接（放子线程用）
    private fun fetchLink(apiUrl: String): String {
        val client = OkHttpClient()
        val request = Request.Builder().url(apiUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("请求失败: ${response.code}")
            return response.body?.string()?.trim() ?: ""
        }
    }

    // 供 WebPage 调用，异步加载 URL
    fun loadUrlFromApi(webView: WebView) {
        lifecycleScope.launch {
            try {
                val url = withContext(Dispatchers.IO) {
                    fetchLink("https://jihadurl.kurama-tiny.workers.dev")
                }
                Log.d("MainActivity", "获取到的URL: $url")
                webView.loadUrl(url)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("MainActivity", "获取链接失败: ${e.message}")
            }
        }
    }
}

@Composable
fun WebPage(
    onBackPressed: () -> Unit,
    getWebView: (WebView) -> Unit,
    onShowCustomView: (View?, WebChromeClient.CustomViewCallback?) -> Unit,
    onHideCustomView: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance: WebView? = remember { null }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler {
        onBackPressed()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AndroidView(
                factory = {
                    WebView(context).apply {
                        getWebView(this)
                        webViewInstance = this
                        clearCache(true)
                        clearHistory()
                        setupWebSettings()

                        webViewClient = WebViewClient()

                        webChromeClient = object : WebChromeClient() {
                            override fun onJsAlert(
                                view: WebView?,
                                url: String?,
                                message: String?,
                                result: JsResult?
                            ): Boolean {
                                AlertDialog.Builder(context)
                                    .setMessage(message)
                                    .setPositiveButton(android.R.string.ok) { _, _ -> result?.confirm() }
                                    .setCancelable(false)
                                    .create()
                                    .show()
                                return true
                            }

                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d("WebViewConsole", consoleMessage?.message() ?: "")
                                return true
                            }

                            override fun onShowCustomView(
                                view: View?,
                                callback: CustomViewCallback?
                            ) {
                                onShowCustomView(view, callback)
                            }

                            override fun onHideCustomView() {
                                onHideCustomView()
                            }
                        }

                        // 这里不直接 loadUrl，而是交给 MainActivity 异步加载
                        (context as? MainActivity)?.loadUrlFromApi(this)

                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        DisposableEffect(Unit) {
            onDispose {
                webViewInstance?.apply {
                    stopLoading()
                    loadUrl("about:blank")
                    removeAllViews()
                    destroy()
                }
            }
        }
    }
}

private fun WebView.setupWebSettings() {
    settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = true
        javaScriptCanOpenWindowsAutomatically = true
        loadsImagesAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        useWideViewPort = true
        loadWithOverviewMode = true

        userAgentString =
            "Mozilla/5.0 (Linux; Android 15; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36 JihadAndroid/1.0"
    }
    setInitialScale(100)
}
