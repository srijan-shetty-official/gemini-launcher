package com.example.geminilauncher

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

data class AppItem(
    val label: String,
    val packageName: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Keep user locked gracefully on Home Screen
            }
        })

        setContent {
            // Material You Dynamic Color scheme mapping seamlessly into Material 3 Expressive spec
            MaterialTheme(
                colorScheme = dynamicDarkColorScheme(LocalContext.current)
            ) {
                GeminiExpressiveLauncherScreen()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GeminiExpressiveLauncherScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("gemini_launcher_prefs", Context.MODE_PRIVATE) }

    var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var geminiPrompt by remember { mutableStateOf("") }
    var geminiResponse by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var batteryPct by remember { mutableIntStateOf(100) }
    val apps = remember { loadInstalledApps(context) }

    // System clock & battery sync loop
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)
            batteryPct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            delay(10_000)
        }
    }

    fun sendToGemini(promptText: String) {
        if (promptText.isBlank()) return
        if (apiKey.isBlank()) {
            showApiKeyDialog = true
            Toast.makeText(context, "Please set your Gemini API Key first", Toast.LENGTH_SHORT).show()
            return
        }
        isLoading = true
        geminiResponse = "Thinking..."
        scope.launch {
            val reply = callGeminiApi(apiKey, promptText)
            geminiResponse = reply
            isLoading = false
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    val colors = MaterialTheme.colorScheme

    // Expressive container layout
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Expressive Material You Header (Clock & Quick Settings Actions)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = currentTime.ifEmpty { "12:00 PM" },
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.onBackground,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "$currentDate  •  $batteryPct% Battery",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = { showApiKeyDialog = true },
                        shape = CircleShape,
                        color = colors.surfaceContainerHigh,
                        contentColor = colors.onSurfaceVariant
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(if (apiKey.isBlank()) "🔑 Set Key" else "🔑 Key", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        onClick = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) },
                        shape = CircleShape,
                        color = colors.surfaceContainerHigh,
                        contentColor = colors.onSurfaceVariant
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text("🏠 Default", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 2. Expressive Material 3 AI Workspace Card
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colors.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(colors.primary)
                            )
                            Text(
                                text = "Gemini Expressive AI",
                                color = colors.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        TextButton(onClick = { openExternalGeminiApp(context) }) {
                            Text("Full App ↗", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Prompt Input Field
                    OutlinedTextField(
                        value = geminiPrompt,
                        onValueChange = { geminiPrompt = it },
                        placeholder = { Text("Ask Gemini or execute smart query...") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outlineVariant,
                            focusedContainerColor = colors.surfaceContainerHighest,
                            unfocusedContainerColor = colors.surfaceContainerHighest
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Action Chips Row
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { triggerVoiceAssistant(context) },
                            label = { Text("🎙️ Voice Assistant") }
                        )
                        AssistChip(
                            onClick = {
                                val clip = getClipboardText(context)
                                if (clip.isBlank()) Toast.makeText(context, "Clipboard empty!", Toast.LENGTH_SHORT).show()
                                else sendToGemini("Summarize cleanly:\n\n$clip")
                            },
                            label = { Text("📋 Summarize Clip") }
                        )
                        AssistChip(
                            onClick = {
                                val clip = getClipboardText(context)
                                if (clip.isBlank()) Toast.makeText(context, "Clipboard empty!", Toast.LENGTH_SHORT).show()
                                else sendToGemini("Refine and fix grammar:\n\n$clip")
                            },
                            label = { Text("✍️ Fix Grammar") }
                        )
                        AssistChip(
                            onClick = { sendToGemini("Give me an optimized focus plan and 3 key insights for today.") },
                            label = { Text("⚡ Productivity Boost") }
                        )
                    }

                    // Live Expandable AI Response Container
                    if (geminiResponse.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = colors.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(14.dp)
                                    .heightIn(min = 60.dp, max = 180.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = geminiResponse,
                                    color = colors.onSurface,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("AI Response", geminiResponse))
                                Toast.makeText(context, "Copied text!", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copy Text", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { geminiResponse = "" }) {
                                Text("Clear", fontSize = 12.sp, color = colors.error)
                            }
                        }
                    }
                }
            }

            // 3. Expressive App Drawer Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search installed applications...") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    unfocusedBorderColor = colors.outlineVariant,
                    focusedContainerColor = colors.surfaceContainer,
                    unfocusedContainerColor = colors.surfaceContainer
                ),
                modifier = Modifier.fillMaxWidth()
            )

            // 4. Smooth Material 3 App Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceContainer,
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    context.packageManager
                                        .getLaunchIntentForPackage(app.packageName)
                                        ?.let { context.startActivity(it) }
                                },
                                onLongClick = {
                                    val infoIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.parse("package:${app.packageName}")
                                    }
                                    context.startActivity(infoIntent)
                                }
                            )
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 18.dp, horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.label,
                                color = colors.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal API Key Dialog with Expressive Material 3 Design
    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = colors.surfaceContainerHigh,
            title = { Text("Gemini API Key Config", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste your API key from Google AI Studio. It is stored securely on your device.",
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it.trim() },
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        apiKey = tempKey
                        prefs.edit().putString("api_key", tempKey).apply()
                        showApiKeyDialog = false
                        Toast.makeText(context, "API Key saved successfully!", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Save Key")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// Direct REST client wrapper for Gemini 2.5 Flash
suspend fun callGeminiApi(apiKey: String, prompt: String): String = withContext(Dispatchers.IO) {
    try {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
        val url = URL(endpoint)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
        }

        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt))
                    )
                )
            )
        }

        OutputStreamWriter(conn.outputStream).use { writer ->
            writer.write(payload.toString())
            writer.flush()
        }

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val rawResponse = BufferedReader(InputStreamReader(stream)).use { it.readText() }

        if (responseCode !in 200..299) {
            return@withContext "API Error ($responseCode): $rawResponse"
        }

        val json = JSONObject(rawResponse)
        val candidates = json.optJSONArray("candidates")
        val firstCandidate = candidates?.optJSONObject(0)
        val content = firstCandidate?.optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        parts?.optJSONObject(0)?.optString("text") ?: "No text output returned."
    } catch (e: Exception) {
        "Network transmission error: ${e.localizedMessage}"
    }
}

fun loadInstalledApps(context: Context): List<AppItem> {
    val pm = context.packageManager
    val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    return pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(0L))
        .map { resolveInfo ->
            AppItem(
                label = resolveInfo.loadLabel(pm).toString(),
                packageName = resolveInfo.activityInfo.packageName
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

fun openExternalGeminiApp(context: Context) {
    val geminiPackage = "com.google.android.apps.bard"
    val launchIntent = context.packageManager.getLaunchIntentForPackage(geminiPackage)
    if (launchIntent != null) {
        context.startActivity(launchIntent)
    } else {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com/app")))
    }
}

fun triggerVoiceAssistant(context: Context) {
    try {
        context.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        })
    } catch (e: Exception) {
        openExternalGeminiApp(context)
    }
}

fun getClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
}
