package com.vibeplayer.app

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        webView = WebView(this).apply {
            setBackgroundColor(0xFF07070D.toInt())
            settings.javaScriptEnabled = true
            // allow the synth engine to start/resume audio without a fresh gesture
            settings.mediaPlaybackRequiresUserGesture = false
            addJavascriptInterface(Bridge(), "AndroidBridge")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(webView)

        // BT headset / remote-control buttons, relayed from the MediaSession
        // in PlaybackService.
        MediaCommandBus.listener = { cmd ->
            runOnUiThread { webView.evaluateJavascript("mediaCommand('$cmd')", null) }
        }
    }

    inner class Bridge {
        /** Called from JS when playback starts/stops. The foreground service
         *  keeps the process (and therefore the WebView's audio thread) alive
         *  when the screen is off or the app is in the background. */
        @JavascriptInterface
        fun setPlaying(playing: Boolean) {
            val intent = Intent(this@MainActivity, PlaybackService::class.java)
            if (playing) {
                ContextCompat.startForegroundService(this@MainActivity, intent)
            } else {
                stopService(intent)
            }
        }
    }

    // Intentionally NOT forwarding onPause() to the WebView:
    // webView.onPause() would suspend timers and kill audio in the background.

    override fun onDestroy() {
        MediaCommandBus.listener = null
        stopService(Intent(this, PlaybackService::class.java))
        webView.destroy()
        super.onDestroy()
    }
}
