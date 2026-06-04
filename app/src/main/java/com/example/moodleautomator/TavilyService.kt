package com.example.moodleautomator

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class TavilyResult(
    val title: String,
    val url: String,
    val content: String
)

object TavilyService {
    private const val TAG = "TavilyService"

    suspend fun search(query: String, apiKey: String): List<TavilyResult> = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty()) {
            Log.e(TAG, "Tavily API Key is empty")
            return@withContext emptyList()
        }
        try {
            val url = URL("https://api.tavily.com/search")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10000
            conn.readTimeout = 15000
            conn.doOutput = true

            val requestBody = JSONObject().apply {
                put("api_key", apiKey)
                put("query", query)
                put("search_depth", "basic")
                put("max_results", 4)
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(requestBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseText)
                val resultsArray = jsonResponse.optJSONArray("results") ?: JSONArray()
                
                val resultsList = mutableListOf<TavilyResult>()
                for (i in 0 until resultsArray.length()) {
                    val obj = resultsArray.getJSONObject(i)
                    resultsList.add(
                        TavilyResult(
                            title = obj.optString("title", ""),
                            url = obj.optString("url", ""),
                            content = obj.optString("content", "")
                        )
                    )
                }
                return@withContext resultsList
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "Tavily API Error $responseCode: $errorText")
                return@withContext emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Tavily search", e)
            return@withContext emptyList()
        }
    }
}
