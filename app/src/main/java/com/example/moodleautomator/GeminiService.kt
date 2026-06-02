package com.example.moodleautomator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class GeminiError {
    NONE,
    NETWORK_ERROR,
    RATE_LIMIT_EXCEEDED,
    BAD_REQUEST,
    UNAUTHORIZED,
    SERVER_ERROR,
    UNKNOWN
}

data class GeminiResponse(
    val answer: String?,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val errorType: GeminiError = GeminiError.NONE,
    val errorMessage: String? = null
)

object GeminiService {
    private const val TAG = "GeminiService"

    suspend fun getAnswer(
        apiKey: String,
        modelName: String,
        question: String,
        choicesJson: String,
        thinkingBudget: Int = 0,
        enableSearch: Boolean = false,
        imageBase64: String? = null,
        imageMimeType: String? = null
    ): GeminiResponse = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Anda adalah asisten AI kuis sekolah yang cerdas, jujur, dan berakurasi tinggi. 
                
                Tugas Anda:
                Pilihlah SATU opsi jawaban yang paling akurat dan benar secara akademis dari pilihan yang disediakan untuk menjawab pertanyaan di bawah.
                
                Pertanyaan:
                ${question.ifEmpty() { "[Soal dalam bentuk gambar - lihat lampiran gambar]" }}

                Pilihan Jawaban (format JSON):
                $choicesJson
                
                Instruksi Penting untuk Menghindari Halusinasi:
                1. Jika ada gambar yang dilampirkan, analisis gambar tersebut dengan teliti untuk membantu mengidentifikasi soal atau pilihan jawaban.
                2. Lakukan penalaran langkah-demi-langkah secara logis sebelum menentukan pilihan.
                3. Jangan berasumsi atau menebak informasi yang tidak ada. Jika ragu, pilihlah opsi yang secara logis dan ilmiah paling mendekati kebenaran berdasarkan data yang ada atau hasil pencarian Google Search.
                4. Anda HARUS memberikan output hanya berupa JSON yang valid dengan format berikut, tanpa penjelasan tambahan apa pun di luar JSON:
                {
                  "selected_value": "nilai_dari_value_pilihan"
                }
                Pastikan properti "selected_value" berisi nilai string dari value opsi terpilih (contoh: "0", "1", "2").
            """.trimIndent()

            val model = if (modelName.isEmpty()) "gemini-2.5-flash" else modelName
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000 // 8 seconds timeout
            conn.readTimeout = 15000 // 15 seconds timeout
            conn.doOutput = true

            // Create JSON Request
            val requestBody = JSONObject()
            val contents = JSONArray()
            val contentObj = JSONObject()
            val parts = JSONArray()
            
            val partObj = JSONObject()
            partObj.put("text", prompt)
            parts.put(partObj)
            
            if (!imageBase64.isNullOrEmpty() && !imageMimeType.isNullOrEmpty()) {
                Log.d(TAG, "Attaching image to Gemini API request. Mime: $imageMimeType")
                val imgPart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mimeType", imageMimeType)
                inlineData.put("data", imageBase64)
                imgPart.put("inlineData", inlineData)
                parts.put(imgPart)
            }

            contentObj.put("parts", parts)
            contents.put(contentObj)
            requestBody.put("contents", contents)

            if (enableSearch) {
                val tools = JSONArray()
                val toolObj = JSONObject()
                toolObj.put("googleSearch", JSONObject())
                tools.put(toolObj)
                requestBody.put("tools", tools)
            }

            val generationConfig = JSONObject()
            generationConfig.put("responseMimeType", "application/json")
            
            if (thinkingBudget > 0) {
                val thinkingConfig = JSONObject()
                thinkingConfig.put("thinkingBudget", thinkingBudget)
                generationConfig.put("thinkingConfig", thinkingConfig)
            }
            
            requestBody.put("generationConfig", generationConfig)

            // Write payload
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                Log.d(TAG, "Gemini Response: $responseText")
                
                val jsonResponse = JSONObject(responseText)
                
                val usageMetadata = jsonResponse.optJSONObject("usageMetadata")
                val promptTokens = usageMetadata?.optInt("promptTokenCount", 0) ?: 0
                val completionTokens = usageMetadata?.optInt("candidatesTokenCount", 0) ?: 0
                val totalTokens = usageMetadata?.optInt("totalTokenCount", 0) ?: 0
                
                val candidates = jsonResponse.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.getJSONObject("content")
                    val resParts = content.getJSONArray("parts")
                    if (resParts.length() > 0) {
                        val text = resParts.getJSONObject(0).getString("text")
                        Log.d(TAG, "Extracted text: $text")
                        
                        val cleanText = text.trim()
                        val resultJson = JSONObject(cleanText)
                        val answer = resultJson.optString("selected_value", null)
                        
                        return@withContext GeminiResponse(answer, promptTokens, completionTokens, totalTokens)
                    }
                }
                return@withContext GeminiResponse(null, promptTokens, completionTokens, totalTokens)
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "HTTP Error $responseCode: $errorText")
                val errorType = when (responseCode) {
                    400 -> GeminiError.BAD_REQUEST
                    401, 403 -> GeminiError.UNAUTHORIZED
                    429 -> GeminiError.RATE_LIMIT_EXCEEDED
                    in 500..599 -> GeminiError.SERVER_ERROR
                    else -> GeminiError.UNKNOWN
                }
                
                var cleanMessage = errorText
                try {
                    val errorObj = JSONObject(errorText).getJSONObject("error")
                    cleanMessage = errorObj.getString("message")
                } catch (e: Exception) {}
                
                return@withContext GeminiResponse(null, errorType = errorType, errorMessage = cleanMessage)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API", e)
            val errorType = if (e is java.io.IOException || e is java.net.SocketTimeoutException || e is java.net.UnknownHostException) {
                GeminiError.NETWORK_ERROR
            } else {
                GeminiError.UNKNOWN
            }
            return@withContext GeminiResponse(null, errorType = errorType, errorMessage = e.message)
        }
    }
}

// Updated comments on rate limits and usage metrics.
// Updated comments on rate limits and usage metrics.
// Updated comments on rate limits and usage metrics.