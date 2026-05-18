package com.example.moodleautomator

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MoodleBridge(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private var getApiKey: () -> String,
    private val getModelName: () -> String,
    private val isAutomationEnabled: () -> Boolean,
    private val getThinkingBudget: () -> Int,
    private val getEnableSearch: () -> Boolean,
    private val onUsageUpdated: (Int, Int) -> Unit,
    private val pauseAutomation: () -> Unit,
    private val onLog: (String) -> Unit
) {
    private var activeKeyIndex = 0

    @JavascriptInterface
    fun onQuestionScraped(question: String, choicesJson: String) {
        if (!isAutomationEnabled()) return
        scope.launch {
            val rawKeys = getApiKey().split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (rawKeys.isEmpty()) {
                onLog("ERROR: API Key kosong!")
                return@launch
            }
            if (activeKeyIndex >= rawKeys.size) activeKeyIndex = 0
            val key = rawKeys[activeKeyIndex]
            
            onLog("Menjawab soal (Key #" + (activeKeyIndex + 1) + ")...")
            val resp = GeminiService.getAnswer(key, getModelName(), question, choicesJson)
            
            if (resp.isQuotaError) {
                onLog("KEY #" + (activeKeyIndex + 1) + " LIMIT! Merotasi key...")
                activeKeyIndex++
                onQuestionScraped(question, choicesJson) // Retry with next key
            } else if (resp.answer != null) {
                onLog("Terjawab: " + resp.answer)
                webView.post {
                    webView.evaluateJavascript("javascript:window.selectAnswerAndNext('" + resp.answer + "')", null)
                }
            } else {
                onLog("Gagal memanggil Gemini API.")
            }
        }
    }
}
