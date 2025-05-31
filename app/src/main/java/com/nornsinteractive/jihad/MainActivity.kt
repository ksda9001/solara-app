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

class MainActivity : ComponentActivity() {

    // 用于控制 WebView 返回、全屏状态等
    private lateinit var webView: WebView
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullScreenContainer: FrameLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        WebView.setWebContentsDebuggingEnabled(true)

        // 全屏视频容器
        fullScreenContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        setContent {
            // 将 fullScreenContainer 作为根容器（供视频全屏使用）
            AndroidView(factory = {
                fullScreenContainer.apply {
                    removeAllViews()

                    // 将 ComposeView 加入容器中（非全屏时显示）
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

    // 显示全屏视频视图
    private fun showFullScreenView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback

        // 添加视频播放视图
        fullScreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // 隐藏 WebView 原视图
        webView.visibility = View.GONE
    }

    // 隐藏全屏视频视图
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
        // 可选：根据需要做横竖屏切换时的处理（如调整 UI 布局）
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Log.d("Orientation", "横屏")
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {
            Log.d("Orientation", "竖屏")
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


    // 拦截返回键
    BackHandler {
        onBackPressed()
    }

    Column(modifier = Modifier.fillMaxSize()) {

        if (!isLandscape) {
            // 顶部 Banner 区域（可用于放广告）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(0xFF6200EE)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✨ 欢迎访问 Kirakira ✨\n顶部内容，用于后期放 banner 广告",
                    color = Color.White
                )
            }
        }

        // WebView 组件
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
                        // JS Alert 弹窗支持
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

                        // JS 控制台打印输出
                        override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                            Log.d("WebViewConsole", consoleMessage?.message() ?: "")
                            return true
                        }

                        // 播放器进入全屏
                        override fun onShowCustomView(
                            view: View?,
                            callback: CustomViewCallback?
                        ) {
                            onShowCustomView(view, callback)
                        }

                        // 播放器退出全屏
                        override fun onHideCustomView() {
                            onHideCustomView()
                        }
                    }

                    // 加载网页地址
                    loadUrl("https://kirakira.buzz/")

                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // 占满剩余空间
        )
    }

    // 组件卸载时清理 WebView
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

// 扩展函数，配置 WebView 参数
private fun WebView.setupWebSettings() {
    settings.apply {
        //测试用，清除webview浏览器的缓存
//        clearHistory()
//        clearCache(true)

        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = true
        javaScriptCanOpenWindowsAutomatically = true
        loadsImagesAutomatically = true
        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        useWideViewPort = true
        loadWithOverviewMode = true
    }
    setInitialScale(100)
}
