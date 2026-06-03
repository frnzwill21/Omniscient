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
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Responsive scaling variables (Enlarged size set)
    val paddingSmall = (screenWidth * 0.025f).coerceAtLeast(10.dp)
    val paddingMedium = (screenWidth * 0.045f).coerceAtLeast(18.dp)
    val paddingLarge = (screenWidth * 0.07f).coerceAtLeast(28.dp)
    
    val buttonSize = (screenWidth * 0.13f).coerceIn(46.dp, 54.dp)
    
    val textTitleSize = (screenWidth * 0.042f).value.coerceIn(16f, 20f).sp
    val textNormalSize = (screenWidth * 0.038f).value.coerceIn(14f, 17f).sp
    val textLogSize = (screenWidth * 0.032f).value.coerceIn(12f, 15f).sp
    val badgeTextSize = (screenWidth * 0.028f).value.coerceIn(10f, 12f).sp
    
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
    var isMinimized by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

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

    fun addLog(message: String) {
        logs = (logs + message).takeLast(20) // Keep last 20 logs
        val level = if (message.startsWith("ERROR") || message.startsWith("❌")) "ERROR"
                    else if (message.startsWith("⚠️") || message.startsWith("warning")) "WARNING"
                    else "INFO"
        com.example.moodleautomator.FileLogger.log(level, "Automation", message)
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

                    val bridge = MoodleBridge(
                        webView = this,
                        scope = coroutineScope,
                        getApiKey = { apiKey },
                        getModelName = { selectedModel },
                        isAutomationEnabled = { isAutomationEnabled },
                        getThinkingBudget = { thinkingBudget },
                        getEnableSearch = { enableSearch },
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

        // Settings Overlay / Custom Bottom Sheet Scrim
        AnimatedVisibility(
            visible = showSettings,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showSettings = false }
            )
        }

        // Slide-Up Settings Panel (Custom Bottom Sheet)
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = Color(0xF20F0F1E) // Premium dark obsidian glass
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = screenHeight * 0.6f, max = screenHeight * 0.82f)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF8A2BE2), // Electric Purple
                                Color(0x2200FFFF)  // Faded Cyan
                            )
                        ),
                        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = paddingMedium, vertical = paddingMedium)
                ) {
                    // Pull/Drag Indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(screenWidth * 0.1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0x44FFFFFF))
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Title Header inside settings
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Konfigurasi Otomatisasi",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = (textTitleSize.value + 2f).sp
                        )
                        IconButton(
                            onClick = { showSettings = false },
                            modifier = Modifier
                                .size(buttonSize)
                                .background(Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
                        ) {
                            Text("✕", color = Color.White, fontSize = textNormalSize, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Scrollable Configs Area
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = true)
                            .verticalScroll(rememberScrollState())
                    ) {
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
                                    label = { Text("Tambah API Key Gemini", color = Color(0x88FFFFFF), fontSize = textLogSize) },
                                    placeholder = { Text("AIzaSy...", color = Color(0x44FFFFFF)) },
                                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showApiKey = !showApiKey }) {
                                            Text(
                                                text = if (showApiKey) "👁" else "🙈",
                                                color = Color.White,
                                                fontSize = textTitleSize
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
                                    textStyle = LocalTextStyle.current.copy(color = Color.White, fontSize = textNormalSize),
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
                                    Text("➕", color = Color.White, fontSize = textNormalSize)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Horizontal Scrollable Row for API Key Chips
                            if (apiKeysList.isNotEmpty()) {
                                Text("Daftar API Key Anda (${apiKeysList.size}):", color = Color(0x88FFFFFF), fontSize = textLogSize, fontWeight = FontWeight.Bold)
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
                                                    fontSize = textLogSize,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "❌",
                                                    color = Color(0xFFFF5252),
                                                    fontSize = textLogSize,
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
                                Text("Belum ada API Key aktif. Masukkan & klik + untuk menambahkan.", color = Color(0xFFFF5252), fontSize = textLogSize)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Model Selector (Horizontal Alignment)
                        var isDropdownExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Model Gemini", color = Color.White, fontSize = textNormalSize, fontWeight = FontWeight.Bold)
                                Text("Pilih model kecerdasan buatan", color = Color(0x88FFFFFF), fontSize = textLogSize)
                            }
                            Box {
                                Button(
                                    onClick = { isDropdownExpanded = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(selectedModel, color = Color.White, fontSize = textNormalSize)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("▼", color = Color.White, fontSize = textLogSize)
                                }
                                DropdownMenu(
                                    expanded = isDropdownExpanded,
                                    onDismissRequest = { isDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF17172B))
                                ) {
                                    listOf(
                                        "gemini-3.5-flash",
                                        "gemini-3.1-pro",
                                        "gemini-3.1-flash-lite",
                                        "gemini-3-flash",
                                        "gemini-3-pro-preview",
                                        "gemini-2.5-flash",
                                        "gemini-2.5-pro",
                                        "gemini-2.0-flash",
                                        "gemini-2.0-flash-thinking-exp",
                                        "gemini-2.0-pro-exp",
                                        "gemini-1.5-flash",
                                        "gemini-1.5-flash-8b",
                                        "gemini-1.5-pro",
                                        "gemini-exp-1206",
                                        "learnlm-1.5-pro-experimental"
                                    ).forEach { modelName ->
                                        DropdownMenuItem(
                                            text = { Text(modelName, color = Color.White, fontSize = textNormalSize) },
                                            onClick = {
                                                selectedModel = modelName
                                                isDropdownExpanded = false
                                                saveConfigs()
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Google Search Grounding Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Google Search Grounding", color = Color.White, fontSize = textNormalSize, fontWeight = FontWeight.Bold)
                                Text("Mencari info real-time di internet", color = Color(0x88FFFFFF), fontSize = textLogSize)
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Reasoning Config
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Reasoning / Thinking Level", color = Color.White, fontSize = textNormalSize, fontWeight = FontWeight.Bold)
                                Text("Gunakan berpikir mendalam (2.0+)", color = Color(0x88FFFFFF), fontSize = textLogSize)
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
                                    Text(thinkingText, color = Color.White, fontSize = textNormalSize)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("▼", color = Color.White, fontSize = textLogSize)
                                }
                                DropdownMenu(
                                    expanded = isThinkingMenuExpanded,
                                    onDismissRequest = { isThinkingMenuExpanded = false },
                                    modifier = Modifier.background(Color(0xFF17172B))
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Off (Cepat & Hemat)", color = Color.White, fontSize = textNormalSize) },
                                        onClick = { 
                                            thinkingBudget = 0 
                                            isThinkingMenuExpanded = false
                                            saveConfigs()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Standard (1024 tokens)", color = Color.White, fontSize = textNormalSize) },
                                        onClick = { 
                                            thinkingBudget = 1024 
                                            isThinkingMenuExpanded = false
                                            saveConfigs()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("High (2048 tokens)", color = Color.White, fontSize = textNormalSize) },
                                        onClick = { 
                                            thinkingBudget = 2048 
                                            isThinkingMenuExpanded = false
                                            saveConfigs()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Ultra (4096 tokens)", color = Color.White, fontSize = textNormalSize) },
                                        onClick = { 
                                            thinkingBudget = 4096 
                                            isThinkingMenuExpanded = false
                                            saveConfigs()
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Full Log Console inside Settings Bottom Sheet
                        Text(
                            text = "Konsol Log Lengkap:",
                            color = Color(0xFF00FFFF),
                            fontSize = textNormalSize,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF07070F))
                                .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(8.dp))
                                .padding(6.dp)
                        ) {
                            val listState = rememberLazyListState()
                            LaunchedEffect(logs.size) {
                                if (logs.isNotEmpty()) {
                                    listState.animateScrollToItem(logs.size - 1)
                                }
                            }
                            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                                items(logs) { logMsg ->
                                    Text(
                                        text = "> $logMsg",
                                        color = if (logMsg.startsWith("ERROR") || logMsg.startsWith("❌")) Color(0xFFFF5252) else if (logMsg.contains("memilih") || logMsg.contains("aktif")) Color(0xFF00FF87) else Color(0xFFB0B0C3),
                                        fontSize = textLogSize,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Debug actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val logContent = com.example.moodleautomator.FileLogger.getLogContent()
                                    val shareIntent = android.content.Intent().apply {
                                        action = android.content.Intent.ACTION_SEND
                                        putExtra(android.content.Intent.EXTRA_TEXT, logContent)
                                        type = "text/plain"
                                    }
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Log Debug"))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Bagikan Log", color = Color.White, fontSize = textNormalSize)
                            }
                            Button(
                                onClick = {
                                    logs = listOf("Log dibersihkan.") 
                                    com.example.moodleautomator.FileLogger.clearLogs()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0x11FFFFFF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Bersihkan Log", color = Color(0xFF888888), fontSize = textNormalSize)
                            }
                        }
                    }
                }
            }
        }

        // Compact Control Bar (Tinggi kurang dari 110.dp)
        AnimatedVisibility(
            visible = !isMinimized,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(screenWidth * 0.94f)
                .padding(bottom = paddingMedium)
        ) {
            ElevatedCard(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = Color(0xEE09090F) // Translucent deep obsidian
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 140.dp)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF8A2BE2), // Electric Purple
                                Color(0xFF00FFFF)  // Cyan glow
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        clip = false,
                        ambientColor = Color(0xFF8A2BE2),
                        spotColor = Color(0xFF00FFFF)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    // Control elements in a horizontal Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Title & Status Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🤖 Gemini",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = textTitleSize
                            )
                            
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeBg.copy(alpha = 0.2f))
                                    .border(1.dp, badgeBg, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeBg,
                                    fontSize = badgeTextSize,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        // Right side: Action Buttons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Quick Play/Pause Toggle Button
                            IconButton(
                                onClick = {
                                    if (apiKey.isEmpty() && !isAutomationEnabled) {
                                        addLog("Harap masukkan API Key!")
                                        showSettings = true
                                    } else {
                                        isAutomationEnabled = !isAutomationEnabled
                                        addLog(if (isAutomationEnabled) "Otomatisasi diaktifkan." else "Otomatisasi dijeda.")
                                        if (isAutomationEnabled) {
                                            webViewInstance?.evaluateJavascript("javascript:if(typeof window.startScan === 'function') window.startScan();", null)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(buttonSize)
                                    .background(
                                        if (isAutomationEnabled) Color(0xFFFF5252) else Color(0xFF8A2BE2),
                                        RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Text(
                                    text = if (isAutomationEnabled) "⏸" else "▶",
                                    color = Color.White,
                                    fontSize = textNormalSize,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Settings Button ⚙️
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier
                                    .size(buttonSize)
                                    .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "⚙️",
                                    color = Color.White,
                                    fontSize = textNormalSize
                                )
                            }

                            // Minimize Button ➡️
                            IconButton(
                                onClick = { isMinimized = true },
                                modifier = Modifier
                                    .size(buttonSize)
                                    .background(Color(0x1AFFFFFF), RoundedCornerShape(8.dp))
                            ) {
                                Text(
                                    text = "➡️",
                                    color = Color.White,
                                    fontSize = textNormalSize
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Mini Log display (last 1-2 items)
                    val displayLogs = remember(logs) {
                        logs.takeLast(2)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(75.dp)
                            .background(Color(0xFF04040A), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        displayLogs.forEach { logMsg ->
                            Text(
                                text = "> $logMsg",
                                color = if (logMsg.startsWith("ERROR") || logMsg.startsWith("❌")) Color(0xFFFF5252) else if (logMsg.contains("memilih") || logMsg.contains("aktif")) Color(0xFF00FF87) else Color(0xFF90909A),
                                fontSize = textLogSize,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // Minimized handle on the right side of the screen
        AnimatedVisibility(
            visible = isMinimized,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            val dragStart = rememberDraggableState { delta ->
                if (delta < -10) isMinimized = false
            }
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(Color(0xDD17172B))
                    .border(1.5.dp, Brush.linearGradient(listOf(Color(0xFF8A2BE2), Color(0xFF00FFFF))), RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .draggable(state = dragStart, orientation = Orientation.Horizontal)
                    .clickable { isMinimized = false }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("◀", color = Color(0xFF00FFFF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("🤖", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}
