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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                // Keep user on Home Screen when Back is pressed
            }
        })

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                GeminiLauncherScreen()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GeminiLauncherScreen() {
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

    // Clock & Battery loop
    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
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
            Toast.makeText(context, "Please enter your Gemini API Key first", Toast.LENGTH_SHORT).show()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC0F111A))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // 1. Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = currentTime,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$currentDate • 🔋 $batteryPct%",
                    fontSize = 13.sp,
                    color = Color(0xFFB0BEC5)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showApiKeyDialog = true }) {
                    Text(if (apiKey.isBlank()) "🔑 Set Key" else "🔑 Key", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
                ) {
                    Text("🏠 Default", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Inline Gemini Workspace Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2235)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "✨ Gemini Home Assistant",
                        color = Color(0xFF8AB4F8),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                    TextButton(onClick = { openExternalGeminiApp(context) }) {
                        Text("Open Full App ↗", fontSize = 12.sp)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = geminiPrompt,
                        onValueChange = { geminiPrompt = it },
                        placeholder = { Text("Ask Gemini right here...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = !isLoading,
                        onClick = {
                            sendToGemini(geminiPrompt)
                            geminiPrompt = ""
                        }
                    ) {
                        Text(if (isLoading) "..." else "Ask")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Action Chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(
                        onClick = { triggerVoiceAssistant(context) },
                        label = { Text("🎙️ Voice") }
                    )
                    AssistChip(
                        onClick = {
                            val clip = getClipboardText(context)
                            if (clip.isBlank()) {
                                Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                            } else {
                                sendToGemini("Summarize this concisely in bullet points:\n\n$clip")
                            }
                        },
                        label = { Text("📋 Summarize Clip") }
                    )
                    AssistChip(
                        onClick = {
                            val clip = getClipboardText(context)
                            if (clip.isBlank()) {
                                Toast.makeText(context, "Clipboard is empty!", Toast.LENGTH_SHORT).show()
                            } else {
                                sendToGemini("Rewrite this to be clear, polite, and grammatically correct:\n\n$clip")
                            }
                        },
                        label = { Text("✍️ Fix Grammar") }
                    )
                    AssistChip(
                        onClick = {
                            sendToGemini("Give me 3 high-impact productivity tips and a quick mental focus exercise for today.")
                        },
                        label = { Text("⚡ Focus Boost") }
                    )
                }

                // Expandable Inline Response Box
                if (geminiResponse.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = 200.dp)
                            .background(Color(0xFF141726), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = geminiResponse,
                            color = Color(0xFFE8EAED),
                            fontSize = 14.sp
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Gemini Response", geminiResponse))
                                Toast.makeText(context, "Copied response!", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Copy", fontSize = 12.sp)
                        }
                        TextButton(onClick = { geminiResponse = "" }) {
                            Text("Clear", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Instant App Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search installed apps...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. App Drawer Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredApps, key = { it.packageName }) { app ->
                Box(
                    modifier = Modifier
                        .background(Color(0xFF1A1D2D), RoundedCornerShape(14.dp))
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
                        .padding(vertical = 16.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.label,
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    // Modal Dialog to Save Gemini API Key
    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text(
                        "Get a free API key from aistudio.google.com and paste it below. It is saved locally on your device.",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempKey,
                        onValueChange = { tempKey = it.trim() },
                        placeholder = { Text("AIzaSy...") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        apiKey = tempKey
                        prefs.edit().putString("api_key", tempKey).apply()
                        showApiKeyDialog = false
                        Toast.makeText(context, "API Key saved!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
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

// Direct REST call to Gemini 2.5 Flash
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
        parts?.optJSONObject(0)?.optString("text") ?: "No response text received."
    } catch (e: Exception) {
        "Network error: ${e.localizedMessage}"
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

fn getClipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
}
