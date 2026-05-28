package com.example.moodleautomator.ui.main

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation3.runtime.NavKey
import com.example.moodleautomator.MoodleBridge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // SharedPreferences for saving configurations and usage stats
    val sharedPrefs = remember { 
        context.getSharedPreferences("moodle_automator_prefs", android.content.Context.MODE_PRIVATE) 
    }
    
    // Automation state (Saved in SharedPreferences)
    var apiKey by remember { 
        mutableStateOf(sharedPrefs.getString("api_key", "") ?: "") 
    }
    var newKeyInput by remember { mutableStateOf("") }
    var selectedModel by remember { 
        mutableStateOf(sharedPrefs.getString("selected_model", "gemini-3.5-flash") ?: "gemini-3.5-flash") 
    }
    var thinkingBudget by remember { 
        mutableStateOf(sharedPrefs.getInt("thinking_budget", 0)) 
    }
    var enableSearch by remember { 
        mutableStateOf(sharedPrefs.getBoolean("enable_search", false)) 
    }
    
    var isAutomationEnabled by remember { mutableStateOf(false) }
    var logs by remember { mutableStateOf(listOf("Sistem siap. Silakan login di kuis Moodle.")) }
    var showApiKey by remember { mutableStateOf(false) }
    var isPanelExpanded by remember { mutableStateOf(true) }

    // Quota tracking
    val currentDayKey = remember { 
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) 
    }
    
    var dailyRequests by remember { 
        mutableStateOf(
            if (sharedPrefs.getString("last_date", "") == currentDayKey) {
                sharedPrefs.getInt("daily_requests", 0)
            } else {
                0
            }
        )
    }
    
    var dailyTokens by remember { 
        mutableStateOf(
            if (sharedPrefs.getString("last_date", "") == currentDayKey) {
                sharedPrefs.getInt("daily_tokens", 0)
            } else {
                0
            }
        )
    }

    // Save configurations helper
    fun saveConfigs() {
        sharedPrefs.edit().apply {
            putString("api_key", apiKey)
            putString("selected_model", selectedModel)
            putInt("thinking_budget", thinkingBudget)
            putBoolean("enable_search", enableSearch)
            apply()
        }
    }

    // Sync usage updates helper
    val onUsageUpdated: (Int, Int) -> Unit = { reqInc, tokenInc ->
        dailyRequests += reqInc
        dailyTokens += tokenInc
        sharedPrefs.edit().apply {
            putString("last_date", currentDayKey)
            putInt("daily_requests", dailyRequests)
            putInt("daily_tokens", dailyTokens)
            apply()
        }
    }

    fun addLog(message: String) {
        logs = (logs + message).takeLast(20) // Keep last 20 logs
    }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F1A))) {
        
        // Android WebView inside compose
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d("MoodleWebView", "Loaded page: $url")
                            addLog("Memuat halaman: ${url?.substringAfter("://")?.take(40)}...")
                            
                            // Inject automation script
                            try {
                                val assetManager = ctx.assets
                                val inputStream = assetManager.open("automation.js")
                                val jsCode = inputStream.bufferedReader().use { it.readText() }
                                view?.evaluateJavascript("javascript:(function(){$jsCode})()", null)
                                Log.d("MoodleWebView", "Injected automation.js successfully.")
                            } catch (e: Exception) {
                                Log.e("MoodleWebView", "Failed to inject script", e)
                                addLog("ERROR: Gagal menginjeksi skrip kuis.")
                            }
                        }
                    }

                    // Add JavaScript Bridge
                    val bridge = MoodleBridge(
                        webView = this,
                        scope = coroutineScope,
                        getApiKey = { apiKey },
                        getModelName = { selectedModel },
                        isAutomationEnabled = { isAutomationEnabled },
                        getThinkingBudget = { thinkingBudget },
                        getEnableSearch = { enableSearch },
                        onUsageUpdated = { reqInc, tokenInc -> onUsageUpdated(reqInc, tokenInc) },
                        pauseAutomation = { isAutomationEnabled = false },
                        onLog = { msg -> addLog(msg) }
                    )
                    addJavascriptInterface(bridge, "MoodleBridge")
                    
                    loadUrl("https://belajar.smkn4bdg.sch.id")
                    webViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Premium floating control panel (Glassmorphism & Gradient Glow)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = Color(0xDD17172B) // Transparent dark background
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF8A2BE2), // Electric Purple
                                Color(0x3300FFFF)  // Cyan glow
                            )
                        ),
                        shape = RoundedCornerShape(24.dp)
                    )
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        clip = false,
                        ambientColor = Color(0xFF8A2BE2),
                        spotColor = Color(0xFF00FFFF)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Header with toggle collapse and Automation state badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isPanelExpanded = !isPanelExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "⚙",
                                color = Color(0xFF00FFFF),
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gemini AI Control Panel",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // Status Badge
                        val badgeBg = if (isAutomationEnabled) Color(0xFF00FF87) else Color(0xFFFF5252)
                        val badgeText = if (isAutomationEnabled) "ACTIVE" else "PAUSED"
                        
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    if (isAutomationEnabled) {
                                        scaleX = scale
                                        scaleY = scale
                                    }
                                }
                                .clip(RoundedCornerShape(12.dp))
                                .background(badgeBg.copy(alpha = 0.2f))
                                .border(1.dp, badgeBg, RoundedCornerShape(12.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeBg,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Collapsible Body
                    AnimatedVisibility(
                        visible = isPanelExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Warning Banner if requests exceed 70% (70% of 1500 = 1050)
                            if (dailyRequests >= 1050) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x33FF9800))
                                        .border(1.dp, Color(0xFFFF9800), RoundedCornerShape(12.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "⚠️ PERINGATAN: Penggunaan kuota harian Anda telah mencapai ${((dailyRequests.toFloat() / 1500f) * 100).toInt()}% (${dailyRequests}/1500 request). Kuota akan di-refresh pukul 15:00 WIB (00:00 PST).",
                                        color = Color(0xFFFF9800),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Quota usage indicators
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Kuota Hari Ini: $dailyRequests / 1500 request ($dailyTokens tokens)",
                                    color = Color(0xBBFFFFFF),
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Reset: 15:00 WIB",
                                    color = Color(0x88FFFFFF),
                                    fontSize = 10.sp
                                )
                            }
                            
                            // Linear progress indicator for request usage
                            LinearProgressIndicator(
                                progress = { (dailyRequests.toFloat() / 1500f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                color = if (dailyRequests >= 1050) Color(0xFFFF9800) else Color(0xFF00FFFF),
                                trackColor = Color(0x22FFFFFF)
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // API Key List Management Section
                            val apiKeysList = remember(apiKey) {
                                apiKey.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            }

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newKeyInput,
                                        onValueChange = { newKeyInput = it },
                                        label = { Text("Tambah API Key Gemini", color = Color(0x88FFFFFF), fontSize = 11.sp) },
                                        placeholder = { Text("Masukkan kunci API baru...", color = Color(0x44FFFFFF)) },
                                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                                Text(
                                                    text = if (showApiKey) "👁" else "🙈",
                                                    color = Color.White,
                                                    fontSize = 16.sp
                                                )
                                            }
                                        },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF8A2BE2),
                                            unfocusedBorderColor = Color(0x44FFFFFF),
                                            focusedLabelColor = Color(0xFF8A2BE2),
                                            cursorColor = Color(0xFF8A2BE2)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Button(
                                        onClick = {
                                            val trimmed = newKeyInput.trim()
                                            if (trimmed.isNotEmpty()) {
                                                val currentKeys = apiKey.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                                if (!currentKeys.contains(trimmed)) {
                                                    val updatedKeys = currentKeys + trimmed
                                                    apiKey = updatedKeys.joinToString(",")
                                                    saveConfigs()
                                                    newKeyInput = ""
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8A2BE2)),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.height(56.dp)
                                    ) {
                                        Text("➕", color = Color.White, fontSize = 14.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Horizontal Scrollable Row for API Key Chips
                                if (apiKeysList.isNotEmpty()) {
                                    Text("Daftar API Key Anda (${apiKeysList.size}):", color = Color(0x88FFFFFF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState())
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        apiKeysList.forEachIndexed { index, key ->
                                            val truncatedKey = if (key.length > 10) "${key.take(4)}...${key.takeLast(4)}" else key
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0x22FFFFFF))
                                                    .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(
                                                        text = "#${index + 1}: $truncatedKey",
                                                        color = Color.White,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                    Text(
                                                        text = "❌",
                                                        color = Color(0xFFFF5252),
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.clickable {
                                                            val updatedKeys = apiKeysList.toMutableList().apply { removeAt(index) }
                                                            apiKey = updatedKeys.joinToString(",")
                                                            saveConfigs()
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Text("Belum ada API Key aktif. Masukkan & klik + untuk menambahkan.", color = Color(0xFFFF5252), fontSize = 10.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            // Google Search Grounding Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Google Search Grounding", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Mencari info real-time di internet", color = Color(0x88FFFFFF), fontSize = 10.sp)
                                }
                                Switch(
                                    checked = enableSearch,
                                    onCheckedChange = { 
                                        enableSearch = it 
                                        saveConfigs()
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color(0xFF00FFFF),
                                        checkedTrackColor = Color(0x3300FFFF),
                                        uncheckedThumbColor = Color(0xFF888888),
                                        uncheckedTrackColor = Color(0x11FFFFFF)
                                    )
                                )
                            }

                            // Reasoning Config
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Reasoning / Thinking Level", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text("Gunakan berpikir mendalam (2.0+)", color = Color(0x88FFFFFF), fontSize = 10.sp)
                                }
                                var isThinkingMenuExpanded by remember { mutableStateOf(false) }
                                val thinkingText = when(thinkingBudget) {
                                    0 -> "Off"
                                    1024 -> "Standard (1K)"
                                    2048 -> "High (2K)"
                                    4096 -> "Ultra (4K)"
                                    else -> "Custom ($thinkingBudget)"
                                }
                                Box {
                                    Button(
                                        onClick = { isThinkingMenuExpanded = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(thinkingText, color = Color.White, fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("▼", color = Color.White, fontSize = 8.sp)
                                    }
                                    DropdownMenu(
                                        expanded = isThinkingMenuExpanded,
                                        onDismissRequest = { isThinkingMenuExpanded = false },
                                        modifier = Modifier.background(Color(0xFF17172B))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Off (Cepat & Hemat)", color = Color.White, fontSize = 12.sp) },
                                            onClick = { 
                                                thinkingBudget = 0 
                                                isThinkingMenuExpanded = false
                                                saveConfigs()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Standard (1024 tokens)", color = Color.White, fontSize = 12.sp) },
                                            onClick = { 
                                                thinkingBudget = 1024 
                                                isThinkingMenuExpanded = false
                                                saveConfigs()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("High (2048 tokens)", color = Color.White, fontSize = 12.sp) },
                                            onClick = { 
                                                thinkingBudget = 2048 
                                                isThinkingMenuExpanded = false
                                                saveConfigs()
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Ultra (4096 tokens)", color = Color.White, fontSize = 12.sp) },
                                            onClick = { 
                                                thinkingBudget = 4096 
                                                isThinkingMenuExpanded = false
                                                saveConfigs()
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            /* Settings added */
// Model selector and Active/Pause button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Editable model selector dropdown
                                var isDropdownExpanded by remember { mutableStateOf(false) }
                                Box(modifier = Modifier.fillMaxWidth(0.48f)) {
                                    OutlinedTextField(
                                        value = selectedModel,
                                        onValueChange = { 
                                            selectedModel = it 
                                            saveConfigs()
                                        },
                                        label = { Text("Model Name", color = Color(0x88FFFFFF), fontSize = 10.sp) },
                                        trailingIcon = {
                                            IconButton(onClick = { isDropdownExpanded = !isDropdownExpanded }) {
                                                Text("▼", color = Color.White, fontSize = 8.sp)
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF8A2BE2),
                                            unfocusedBorderColor = Color(0x22FFFFFF),
                                            focusedLabelColor = Color(0xFF8A2BE2)
                                        ),
                                        textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = 12.sp),
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    DropdownMenu(
                                        expanded = isDropdownExpanded,
                                        onDismissRequest = { isDropdownExpanded = false },
                                        modifier = Modifier.background(Color(0xFF17172B))
                                    ) {
                                        listOf(
                                            "gemini-3.5-flash",
                                            "gemini-3.1-pro",
                                            "gemini-3-flash",
                                            "gemini-3.1-flash-lite",
                                            "gemini-2.5-pro",
                                            "gemini-2.5-flash",
                                            "gemini-2.5-flash-lite"
                                        ).forEach { modelName ->
                                            DropdownMenuItem(
                                                text = { Text(modelName, color = Color.White, fontSize = 12.sp) },
                                                onClick = {
                                                    selectedModel = modelName
                                                    isDropdownExpanded = false
                                                    saveConfigs()
                                                }
                                            )
                                        }
                                    }
                                }

                                // Toggle Automation Button
                                Button(
                                    onClick = { 
                                        if (apiKey.isEmpty() && !isAutomationEnabled) {
                                            addLog("Harap masukkan API Key terlebih dahulu!")
                                        } else {
                                            isAutomationEnabled = !isAutomationEnabled
                                            addLog(if (isAutomationEnabled) "Otomatisasi diaktifkan." else "Otomatisasi dijeda.")
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isAutomationEnabled) Color(0xFFFF5252) else Color(0xFF8A2BE2)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(12.dp),
                                            spotColor = if (isAutomationEnabled) Color(0xFFFF5252) else Color(0xFF8A2BE2)
                                        )
                                ) {
                                    Text(
                                        text = if (isAutomationEnabled) "⏸" else "▶",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isAutomationEnabled) "Pause" else "Start",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live Log Monitor Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Log Monitor:",
                            color = Color(0xFF00FFFF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Bersihkan Log",
                            color = Color(0x66FFFFFF),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { logs = listOf("Log dibersihkan.") }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))

                    // Console-like Log Monitor
                    val listState = rememberLazyListState()
                    
                    LaunchedEffect(logs.size) {
                        if (logs.isNotEmpty()) {
                            listState.animateScrollToItem(logs.size - 1)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(90.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0A0A14))
                            .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(logs) { logMsg ->
                                Text(
                                    text = "> $logMsg",
                                    color = if (logMsg.startsWith("ERROR")) Color(0xFFFF5252) else if (logMsg.contains("memilih")) Color(0xFF00FF87) else Color(0xFFB0B0C3),
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
