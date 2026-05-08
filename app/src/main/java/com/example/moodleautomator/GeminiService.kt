package com.example.moodleautomator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class GeminiResponse(
    val answer: String?,
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0,
    val isNetworkError: Boolean = false,
    val isQuotaError: Boolean = false
)

object GeminiService {
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
            val prompt = "Analyze quiz question and select the correct option. Respond only in JSON: {\"selected_value\": \"value\"}"
            val model = if (modelName.isEmpty()) "gemini-2.5-flash" else modelName
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val requestBody = JSONObject()
            val contents = JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt + "\n" + question + "\n" + choicesJson))))
            requestBody.put("contents", contents)

            OutputStreamWriter(conn.outputStream).use { it.write(requestBody.toString()) }

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val candText = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                val answer = JSONObject(candText.trim()).optString("selected_value", null)
                return@withContext GeminiResponse(answer)
            }
            return@withContext GeminiResponse(null, isNetworkError = true)
        } catch (e: Exception) {
            return@withContext GeminiResponse(null, isNetworkError = true)
        }
    }
}
