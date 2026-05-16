package com.example.moodleautomator

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MoodleBridge(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val getApiKey: () -> String,
    private val getModelName: () -> String,
    private val isAutomationEnabled: () -> Boolean,
    private val getThinkingBudget: () -> Int,
    private val getEnableSearch: () -> Boolean,
    private val onUsageUpdated: (Int, Int) -> Unit,
    private val pauseAutomation: () -> Unit,
    private val onLog: (String) -> Unit
) {
    @JavascriptInterface
    fun onQuestionScraped(question: String, choicesJson: String) {
        if (!isAutomationEnabled()) return
        scope.launch {
            onLog("Menjawab soal: " + question.take(30) + "...")
            val resp = GeminiService.getAnswer(
                apiKey = getApiKey(),
                modelName = getModelName(),
                question = question,
                choicesJson = choicesJson
            )
            if (resp.answer != null) {
                onLog("Jawaban terpilih: " + resp.answer)
                webView.post {
                    webView.evaluateJavascript("javascript:window.selectAnswerAndNext('" + resp.answer + "')", null)
                }
            } else {
                onLog("Gagal menjawab.")
            }
        }
    }
}
