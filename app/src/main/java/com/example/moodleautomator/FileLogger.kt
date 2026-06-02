package com.example.moodleautomator

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileLogger {
    private const val TAG = "FileLogger"
    private const val LOG_FILE_NAME = "debug_logs.txt"
    private var logFile: File? = null
    
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        // Rotasi log file jika melebihi 2MB
        if (logFile?.exists() == true && logFile!!.length() > 2 * 1024 * 1024) {
            logFile?.delete()
        }
        log("INFO", "FileLogger", "FileLogger diinisialisasi. Aplikasi dimulai.")
    }
    
    fun log(level: String, tag: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val cleanMessage = message.replace(Regex("AIza[a-zA-Z0-9_-]{35}"), "AIza...[API_KEY_HIDDEN]") // Sensor API Key demi keamanan
        val formattedLine = "[$timestamp] [$level] [$tag]: $cleanMessage\n"
        
        // Print ke Logcat bawaan Android
        when (level) {
            "ERROR" -> Log.e(tag, cleanMessage)
            "WARNING" -> Log.w(tag, cleanMessage)
            else -> Log.d(tag, cleanMessage)
        }
        
        // Tulis ke file log lokal di penyimpanan internal
        logFile?.let { file ->
            try {
                FileWriter(file, true).use { writer ->
                    writer.write(formattedLine)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Gagal menulis log ke file", e)
            }
        }
    }
    
    fun getLogFile(): File? = logFile
    
    fun getLogContent(): String {
        return logFile?.let { file ->
            if (file.exists()) {
                try {
                    file.readText()
                } catch (e: Exception) {
                    "Gagal membaca file log: ${e.message}"
                }
            } else {
                "File log belum dibuat."
            }
        } ?: "Logger belum diinisialisasi."
    }
    
    fun clearLogs() {
        try {
            logFile?.let { file ->
                if (file.exists()) {
                    file.delete()
                }
                file.createNewFile()
            }
            log("INFO", "FileLogger", "File log dibersihkan oleh pengguna.")
        } catch (e: Exception) {
            Log.e(TAG, "Gagal membersihkan file log", e)
        }
    }
}
