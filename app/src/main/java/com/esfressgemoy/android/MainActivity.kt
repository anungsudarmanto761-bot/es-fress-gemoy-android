package com.esfressgemoy.android

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var webView: WebView? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraRequest: PermissionRequest? = null

    companion object {
        private const val URL = "https://es-fress-gemoy.hatchable.site"
        private const val FILE_PICKER = 1001
        private const val CAMERA = 1002
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        try {
            createWebView()
            webView?.loadUrl(URL)
        } catch (e: Throwable) {
            showError()
        }
    }

    private fun createWebView() {
        val w = WebView(this)
        webView = w
        setContentView(w)

        w.setBackgroundColor(Color.WHITE)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)

        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean = false

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.postDelayed({ view.loadUrl(URL) }, 800)
                }
            }

            override fun onRenderProcessGone(
                view: WebView,
                detail: android.webkit.RenderProcessGoneDetail
            ): Boolean {
                try {
                    view.destroy()
                } catch (_: Exception) {}
                createWebView()
                webView?.postDelayed({ webView?.loadUrl(URL) }, 300)
                return true
            }
        }

        w.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(
                        params.createIntent().addCategory(Intent.CATEGORY_OPENABLE),
                        FILE_PICKER
                    )
                    true
                } catch (_: Exception) {
                    fileCallback = null
                    false
                }
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (!request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        request.deny()
                        return@runOnUiThread
                    }
                    if (checkSelfPermission(Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED) {
                        request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    } else {
                        cameraRequest?.deny()
                        cameraRequest = request
                        requestPermissions(arrayOf(Manifest.permission.CAMERA), CAMERA)
                    }
                }
            }
        }

        w.setDownloadListener { url, userAgent, disposition, mimeType, _ ->
            try {
                val name = android.webkit.URLUtil.guessFileName(url, disposition, mimeType)
                val req = DownloadManager.Request(Uri.parse(url))
                    .setTitle(name)
                    .setDescription("Es Fress Gemoy")
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                CookieManager.getInstance().getCookie(url)?.let {
                    req.addRequestHeader("Cookie", it)
                }
                (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                Toast.makeText(this, "Download dimulai", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Download gagal", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showError() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
            setBackgroundColor(Color.WHITE)
        }
        val title = TextView(this).apply {
            text = "Es Fress Gemoy"
            textSize = 28f
            setTextColor(Color.rgb(21, 128, 61))
        }
        val msg = TextView(this).apply {
            text = "\nAplikasi gagal memuat halaman.\nPeriksa koneksi internet lalu buka kembali."
            textSize = 17f
            setTextColor(Color.DKGRAY)
        }
        box.addView(title)
        box.addView(msg)
        setContentView(box)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        results: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == CAMERA) {
            val r = cameraRequest
            cameraRequest = null
            if (results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
                r?.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
            } else {
                r?.deny()
            }
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_PICKER) {
            val result = if (resultCode == RESULT_OK && data != null)
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            else null
            fileCallback?.onReceiveValue(result)
            fileCallback = null
        }
    }

    @Deprecated("Deprecated in Android API")
    override fun onBackPressed() {
        val w = webView
        if (w != null && w.canGoBack()) w.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        cameraRequest?.deny()
        cameraRequest = null
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = null
            destroy()
        }
        webView = null
        super.onDestroy()
    }
}
