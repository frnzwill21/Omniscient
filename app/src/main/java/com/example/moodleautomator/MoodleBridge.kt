package com.example.moodleautomator

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64

class MoodleBridge(
    private val webView: WebView,
    private val scope: CoroutineScope,
    private val getApiKey: () -> String,
    private val getModelName: () -> String,
    private val isAutomationEnabled: () -> Boolean,
    private val getThinkingBudget: () -> Int,
    private val getEnableSearch: () -> Boolean,
    private val getEnableSearchFallback: () -> Boolean,
    private val getTavilyApiKey: () -> String,
    private val pauseAutomation: () -> Unit, // Force pause on network failure
    private val onLog: (String) -> Unit
) {
    companion object {
        private const val TAG = "MoodleBridge"
    }

    @JavascriptInterface
    fun onQuestionScraped(question: String, choicesJson: String) {
        processQuestion(question, choicesJson, null)
    }

    @JavascriptInterface
    fun onQuestionScrapedWithImage(question: String, choicesJson: String, imageUrl: String) {
        processQuestion(question, choicesJson, imageUrl)
    }

    private fun processQuestion(
        question: String,
        choicesJson: String,
        imageUrl: String?
    ) {
        Log.d(TAG, "Question Scraped: $question")
        Log.d(TAG, "Choices Scraped: $choicesJson")

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
            var activeImageBase64: String? = null
            var activeImageMimeType: String? = null

            if (!imageUrl.isNullOrEmpty()) {
                onLog("-> Mengunduh gambar kuis secara aman...")
                try {
                    val downloaded = downloadImageAsBase64(imageUrl)
                    if (downloaded != null) {
                        activeImageBase64 = downloaded.first
                        activeImageMimeType = downloaded.second
                        onLog("-> Gambar kuis sukses diunduh (${activeImageMimeType}).")
                    } else {
                        onLog("⚠️ Gagal mengunduh gambar kuis. Melanjutkan dengan teks saja...")
                    }
                } catch (e: Exception) {
                    onLog("⚠️ Gagal mengunduh gambar kuis: ${e.message}. Melanjutkan dengan teks saja...")
                }
            }

            var success = false
            var isInternetDown = false
            var globalRetryCount = 0
            val maxGlobalRetries = 3

            var activeQuestion = question
            var isSearchPerformed = false

            while (globalRetryCount < maxGlobalRetries && !success && isAutomationEnabled()) {
                if (globalRetryCount > 0) {
                    onLog("⚠️ Semua API Key terkena rate limit (429). Mencoba kembali seluruh kunci (Percobaan $globalRetryCount/$maxGlobalRetries) dalam 60 detik...")
                    for (i in 60 downTo 1) {
                        if (!isAutomationEnabled()) break
                        if (i % 10 == 0 || i <= 5) {
                            onLog("-> Antrean: Mencoba kembali dalam $i detik...")
                        }
                        delay(1000)
                    }
                    if (!isAutomationEnabled()) {
                        onLog("Otomatisasi kuis dijeda. Antrean dibatalkan.")
                        break
                    }
                }

                var apiKeyIndex = 0
                var anyKeyRateLimited = false

                while (apiKeyIndex < apiKeys.size && !success && isAutomationEnabled()) {
                    val key = apiKeys[apiKeyIndex]
                    val keyLogName = "Key #${apiKeyIndex + 1} (${key.take(6)}...${key.takeLast(4)})"
                    
                    var networkRetryCount = 0
                    val maxNetworkRetries = 100
                    var currentDelayMs = 5000L
                    
                    while (networkRetryCount < maxNetworkRetries && isAutomationEnabled()) {
                        if (networkRetryCount > 0) {
                            onLog("-> Mencoba kembali secara otomatis dalam ${currentDelayMs / 1000} detik...")
                            delay(currentDelayMs)
                            currentDelayMs = (currentDelayMs + 5000L).coerceAtMost(30000L)
                            
                            if (!isAutomationEnabled()) {
                                onLog("Otomatisasi kuis dijeda. Percobaan ulang dibatalkan.")
                                break
                            }
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
                        if (!activeImageBase64.isNullOrEmpty()) {
                            onLog("-> Menyertakan gambar soal (Multimodal).")
                        }
                        
                        val startTime = System.currentTimeMillis()
                        val geminiResponse = GeminiService.getAnswer(
                            apiKey = key,
                            modelName = model,
                            question = activeQuestion,
                            choicesJson = choicesJson,
                            thinkingBudget = thinkingBudget,
                            enableSearch = enableSearch,
                            imageBase64 = activeImageBase64,
                            imageMimeType = activeImageMimeType
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
                        } else if (geminiResponse.needSearch && getEnableSearchFallback() && !isSearchPerformed) {
                            val query = geminiResponse.searchQuery
                            val searchKey = getTavilyApiKey()
                            if (query.isNullOrEmpty()) {
                                onLog("⚠️ Gemini meminta pencarian, tapi kueri kosong. Melanjutkan...")
                            } else if (searchKey.isEmpty()) {
                                onLog("❌ ERROR: Gemini meminta pencarian web cadangan, tetapi API Key Tavily belum diatur!")
                            } else {
                                onLog("-> Gemini meminta informasi real-time. Mencari di internet via Tavily untuk: \"$query\"...")
                                val results = TavilyService.search(query, searchKey)
                                isSearchPerformed = true
                                if (results.isNotEmpty()) {
                                    onLog("-> Berhasil menemukan ${results.size} referensi web. Mengirim ulang soal ke Gemini dengan konteks...")
                                    val contextBuilder = StringBuilder()
                                    contextBuilder.append("HASIL PENCARIAN INTERNET:\n")
                                    results.forEach { res ->
                                        contextBuilder.append("- [${res.title}]: ${res.content}\n")
                                    }
                                    contextBuilder.append("\nGunakan hasil pencarian di atas untuk menjawab pertanyaan berikut secara akurat.\n\n")
                                    activeQuestion = contextBuilder.toString() + question
                                    continue // Re-run immediately with the same API key and enriched question context
                                } else {
                                    onLog("⚠️ Pencarian tidak menemukan hasil. Melanjutkan...")
                                }
                            }
                        }

                        // Handle specific error types or fallback when search is not requested or not enabled
                        if (!success) {
                            when (geminiResponse.errorType) {
                                GeminiError.NETWORK_ERROR -> {
                                    networkRetryCount++
                                    onLog("⚠️ Gangguan Jaringan (Percobaan $networkRetryCount/$maxNetworkRetries): ${geminiResponse.errorMessage ?: "Koneksi terputus/timeout"}")
                                    onLog("-> Menunggu koneksi aktif kembali...")
                                }
                                GeminiError.RATE_LIMIT_EXCEEDED -> {
                                    onLog("⚠️ $keyLogName Limit Tercapai (429): ${geminiResponse.errorMessage ?: "Kuota habis atau terlalu banyak request"}.")
                                    anyKeyRateLimited = true
                                    break // Stop retrying this key, move to rotation
                                }
                                GeminiError.BAD_REQUEST -> {
                                    if (!activeImageBase64.isNullOrEmpty()) {
                                        onLog("⚠️ ERROR 400 (Bad Request). Gambar kuis kemungkinan rusak atau tidak didukung. Mencoba kembali tanpa gambar...")
                                        activeImageBase64 = null
                                        activeImageMimeType = null
                                        continue
                                    } else {
                                        onLog("❌ ERROR $keyLogName (400 Bad Request): ${geminiResponse.errorMessage ?: "Format request salah / tipe model tidak cocok"}.")
                                        break // Rotate key / stop
                                    }
                                }
                                GeminiError.UNAUTHORIZED -> {
                                    onLog("❌ ERROR $keyLogName (401 Unauthorized): Kunci API tidak valid atau tidak memiliki akses.")
                                    break // Rotate key
                                }
                                GeminiError.SERVER_ERROR -> {
                                    networkRetryCount++
                                    onLog("⚠️ Gemini Server Down (503) (Percobaan $networkRetryCount/$maxNetworkRetries): ${geminiResponse.errorMessage ?: "Layanan tidak tersedia"}.")
                                }
                                else -> {
                                    networkRetryCount++
                                    onLog("⚠️ Kesalahan Tidak Dikenal (Percobaan $networkRetryCount/$maxNetworkRetries): ${geminiResponse.errorMessage ?: "Tidak ada detail"}")
                                }
                            }
                        }
                    }

                    if (success) break
                    
                    if (networkRetryCount >= maxNetworkRetries) {
                        onLog("❌ ERROR: Percobaan jaringan gagal setelah $maxNetworkRetries kali. Otomatisasi kuis DIJEDA.")
                        isInternetDown = true
                        withContext(Dispatchers.Main) {
                            pauseAutomation()
                        }
                        break
                    }
                    
                    apiKeyIndex++
                }

                if (success || isInternetDown) break
                
                if (!success) {
                    if (anyKeyRateLimited) {
                        globalRetryCount++
                    } else {
                        onLog("❌ ERROR: Semua API Key gagal memberikan respon (bukan rate limit).")
                        withContext(Dispatchers.Main) {
                            pauseAutomation()
                        }
                        break
                    }
                }
            }

            if (!success && !isInternetDown && isAutomationEnabled()) {
                onLog("❌ ERROR: Semua API Key terkena limit harian permanen (RPD habis) atau batas cooldown terlampaui. Otomatisasi kuis DIJEDA.")
                withContext(Dispatchers.Main) {
                    pauseAutomation()
                }
            }
        }
    }

    private suspend fun downloadImageAsBase64(imageUrl: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        try {
            val url = URL(imageUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            
            // Salin cookies untuk autentikasi Moodle
            val cookies = android.webkit.CookieManager.getInstance().getCookie(imageUrl)
            if (!cookies.isNullOrEmpty()) {
                conn.setRequestProperty("Cookie", cookies)
            }
            
            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val contentType = conn.contentType ?: "image/png"
                val bytes = conn.inputStream.use { it.readBytes() }
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                return@withContext Pair(base64, contentType)
            } else {
                Log.e(TAG, "Failed to download image, HTTP code: $responseCode")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading image: $imageUrl", e)
            return@withContext null
        }
    }
}
