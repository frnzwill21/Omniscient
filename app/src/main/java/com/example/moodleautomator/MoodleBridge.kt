package com.example.moodleautomator

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class MoodleBridge(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val getApiKey: () -> String,
    private val getModelName: () -> String,
    private val isAutomationEnabled: () -> Boolean,
    private val getThinkingBudget: () -> Int,
    private val getEnableSearch: () -> Boolean,
    private val pauseAutomation: () -> Unit, // Force pause on network failure
    private val onLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "MoodleBridge"
    }

    @JavascriptInterface
    fun onQuestionScraped(question: String, choicesJson: String) {
        processQuestion(question, choicesJson, null, null)
    }

    @JavascriptInterface
    fun onQuestionScrapedWithImage(question: String, choicesJson: String, imageBase64: String, imageMimeType: String) {
        processQuestion(question, choicesJson, imageBase64, imageMimeType)
    }

    private fun processQuestion(
        question: String,
        choicesJson: String,
        imageBase64: String?,
        imageMimeType: String?
    ) {
        Log.d(TAG, "Question Scraped: $question")
        Log.d(TAG, "Choices Scraped: $choicesJson")
        val hasImage = !imageBase64.isNullOrEmpty()

        if (!isAutomationEnabled()) {
            onLog("Otomatisasi kuis dijeda. Soal terdeteksi tapi tidak dijawab.")
            return
        }

        val apiKeyString = getApiKey()
        val apiKeys = apiKeyString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        
        if (apiKeys.isEmpty()) {
            onLog("ERROR: API Key Gemini belum diatur!")
            return
        }

        val model = getModelName()
        val thinkingBudget = getThinkingBudget()
        val enableSearch = getEnableSearch()

        scope.launch {
            var apiKeyIndex = 0
            var success = false
            var isInternetDown = false

            while (apiKeyIndex < apiKeys.size && !success) {
                val key = apiKeys[apiKeyIndex]
                val keyLogName = "Key #${apiKeyIndex + 1} (${key.take(6)}...${key.takeLast(4)})"
                
                var networkRetryCount = 0
                val maxNetworkRetries = 3
                
                while (networkRetryCount < maxNetworkRetries) {
                    if (networkRetryCount > 0) {
                        onLog("-> Gangguan internet. Percobaan kembali (${networkRetryCount + 1}/$maxNetworkRetries) dalam 5 detik...")
                        delay(5000)
                    } else if (apiKeyIndex > 0) {
                        onLog("-> Beralih ke API Key cadangan #${apiKeyIndex + 1}...")
                    } else {
                        onLog("Mengirim soal ke Gemini ($model)...")
                    }

                    if (enableSearch) {
                        onLog("-> Fitur Google Search diaktifkan.")
                    }
                    if (thinkingBudget > 0) {
                        onLog("-> Mode penalaran aktif (budget: $thinkingBudget tokens).")
                    }
                    if (hasImage) {
                        onLog("-> Menyertakan gambar soal (Multimodal).")
                    }
                    
                    val startTime = System.currentTimeMillis()
                    val geminiResponse = GeminiService.getAnswer(
                        apiKey = key,
                        modelName = model,
                        question = question,
                        choicesJson = choicesJson,
                        thinkingBudget = thinkingBudget,
                        enableSearch = enableSearch,
                        imageBase64 = imageBase64,
                        imageMimeType = imageMimeType
                    )
                    val duration = System.currentTimeMillis() - startTime

                    val answerValue = geminiResponse.answer
                    if (answerValue != null) {
                        onLog("Gemini memilih opsi value: $answerValue (${duration}ms) via $keyLogName")
                        onLog("-> Penggunaan token: ${geminiResponse.totalTokens} tokens (Prompt: ${geminiResponse.promptTokens}, Completion: ${geminiResponse.completionTokens})")
                        
                        withContext(Dispatchers.Main) {
                            webView.evaluateJavascript("javascript:window.selectAnswerAndNext('$answerValue');", null)
                        }
                        success = true
                        break // Succeeded! Stop retry loop
                    } else {
                        // Handle specific error types
                        when (geminiResponse.errorType) {
                            GeminiError.NETWORK_ERROR -> {
                                networkRetryCount++
                                onLog("⚠️ Gangguan Jaringan: ${geminiResponse.errorMessage ?: "Koneksi terputus/timeout"}")
                            }
                            GeminiError.RATE_LIMIT_EXCEEDED -> {
                                onLog("⚠️ $keyLogName Limit Tercapai (429): ${geminiResponse.errorMessage ?: "Kuota habis atau terlalu banyak request"}.")
                                break // Stop retrying this key, move to rotation
                            }
                            GeminiError.BAD_REQUEST -> {
                                onLog("❌ ERROR $keyLogName (400 Bad Request): ${geminiResponse.errorMessage ?: "Format request salah / tipe model tidak cocok"}.")
                                break // Rotate key / stop
                            }
                            GeminiError.UNAUTHORIZED -> {
                                onLog("❌ ERROR $keyLogName (401 Unauthorized): Kunci API tidak valid atau tidak memiliki akses.")
                                break // Rotate key
                            }
                            GeminiError.SERVER_ERROR -> {
                                onLog("⚠️ Gemini Server Down (503): ${geminiResponse.errorMessage ?: "Layanan tidak tersedia"}. Mencoba kembali...")
                                networkRetryCount++
                            }
                            else -> {
                                onLog("⚠️ Kesalahan Tidak Dikenal: ${geminiResponse.errorMessage ?: "Tidak ada detail"}")
                                networkRetryCount++
                            }
                        }
                    }
                }

                if (success) break
                
                if (networkRetryCount >= maxNetworkRetries) {
                    onLog("❌ ERROR: Percobaan jaringan gagal permanen. Otomatisasi kuis DIJEDA.")
                    isInternetDown = true
                    withContext(Dispatchers.Main) {
                        pauseAutomation()
                    }
                    break
                }
                
                apiKeyIndex++
            }

            if (!success && !isInternetDown) {
                onLog("ERROR: Semua API Key cadangan Anda gagal memberikan respon.")
                withContext(Dispatchers.Main) {
                    pauseAutomation()
                }
            }
        }
    }
}
