package com.example.moodleautomator.ui.main

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation3.runtime.NavKey

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen(onItemClick: (NavKey) -> Unit, modifier: Modifier = Modifier) {
    var isAutomationEnabled by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(onClick = { isAutomationEnabled = !isAutomationEnabled }) {
                Text(if (isAutomationEnabled) "Stop" else "Start")
            }
        }
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.javaScriptEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl("https://belajar.smkn4bdg.sch.id")
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
