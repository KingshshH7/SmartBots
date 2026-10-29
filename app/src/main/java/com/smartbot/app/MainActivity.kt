package com.smartbot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val role: String, val text: String) // "user" | "assistant" | "sys"

private val CYAN = Color(0xFF36D9E6)
private val BLUE = Color(0xFF4F8CFF)
private val BG = Color(0xFF0B0F14)
private val SURFACE = Color(0xFF121923)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SmartBotTheme { SmartBotApp() } }
    }
}

@Composable
fun SmartBotTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        background = BG, surface = SURFACE, primary = CYAN, secondary = BLUE,
        onBackground = Color(0xFFE8EDF3), onSurface = Color(0xFFE8EDF3),
    )
    MaterialTheme(colorScheme = colors, content = content)
}

/** The animated pulsing core — SmartBot's "face." */
@Composable
fun Core(size: Int = 34, active: Boolean = false) {
    val infinite = rememberInfiniteTransition(label = "core")
    val pulse by infinite.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(if (active) 500 else 1400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(pulse)
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(CYAN, BLUE)))
    )
}

@Composable
fun SmartBotApp() {
    val context = LocalContext.current
    val brain = remember { Brain(context) }
    var showBrain by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(brain.activeProvider()?.apiKey.isNullOrBlank()) }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        delay(600)
        messages.add(ChatMessage("sys", "⚡ SmartBot core online. Systems nominal."))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Core(size = 22, active = sending)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("SmartBot", fontWeight = FontWeight.Bold)
                            Text(
                                brain.activeProvider()?.name ?: "No provider set",
                                fontSize = 11.sp, color = Color(0xFF8A9BB0)
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showBrain = true }) { Text("🧠") }
                    TextButton(onClick = { showSettings = true }) { Text("⚙️") }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask SmartBot anything…") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = !sending && input.isNotBlank() && brain.activeProvider() != null,
                    onClick = {
                        val userText = input
                        val config = brain.activeProvider() ?: return@Button
                        input = ""
                        messages.add(ChatMessage("user", userText))
                        sending = true
                        scope.launch {
                            val history = messages
                                .filter { it.role == "user" || it.role == "assistant" }
                                .dropLast(1).map { it.role to it.text }
                            val sys = SYSTEM_PROMPT + "\n\n" + brain.asContext()
                            val result = withContext(Dispatchers.IO) {
                                LLM.send(config, userText, history, sys)
                            }
                            when (result) {
                                is LLMResult.Success -> {
                                    var reply = result.text
                                    val idx = reply.lastIndexOf("REMEMBER:")
                                    if (idx != -1) {
                                        val fact = reply.substring(idx + 9).trim()
                                        reply = reply.substring(0, idx).trim()
                                        if (fact.isNotBlank()) brain.remember(fact)
                                    }
                                    messages.add(ChatMessage("assistant", reply))
                                }
                                is LLMResult.Error -> messages.add(ChatMessage("sys", "⚠ ${result.message}"))
                            }
                            sending = false
                        }
                    }
                ) { Text(if (sending) "…" else "Send") }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.size <= 1) {
                item {
                    Column(Modifier.padding(top = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Core(size = 70, active = true)
                        Spacer(Modifier.height(14.dp))
                        Text("SmartBot", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Set a provider in ⚙️ Settings — FreeLLMAPI, Anthropic, Ollama, or any OpenAI-compatible endpoint.",
                            color = Color(0xFF8A9BB0), fontSize = 13.sp,
                            modifier = Modifier.padding(top = 6.dp, start = 24.dp, end = 24.dp)
                        )
                    }
                }
            }
            items(messages) { m -> MessageBubble(m) }
        }
    }

    if (showBrain) BrainDialog(brain = brain, onDismiss = { showBrain = false })
    if (showSettings) ProvidersDialog(brain = brain, onDismiss = { showSettings = false })
}

private const val SYSTEM_PROMPT = """
You are SmartBot, a witty, concise, highly capable personal AI assistant in
the spirit of Jarvis from Iron Man. Be warm, a little dry-witted, genuinely
useful. Keep replies tight unless the user asks for detail.
If the user shares a durable fact worth remembering (a preference, plan,
name, or recurring detail), end your reply on a new final line starting
with 'REMEMBER:' followed by that fact in a few words. Omit that line if
there's nothing worth remembering.
"""

@Composable
fun MessageBubble(m: ChatMessage) {
    val isUser = m.role == "user"
    val bg = when (m.role) { "user" -> Color(0xFF1F3A52); "sys" -> Color.Transparent; else -> Color(0xFF16202C) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Surface(color = bg, shape = RoundedCornerShape(14.dp), modifier = Modifier.widthIn(max = 300.dp)) {
            Text(m.text, modifier = Modifier.padding(12.dp),
                color = if (m.role == "sys") Color(0xFF8A9BB0) else Color(0xFFE8EDF3))
        }
    }
}

@Composable
fun BrainDialog(brain: Brain, onDismiss: () -> Unit) {
    val facts = remember { brain.allFacts() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { TextButton(onClick = { brain.clear(); onDismiss() }) { Text("Clear brain") } },
        title = { Text("🧠 SmartBot's Brain") },
        text = {
            if (facts.isEmpty()) {
                Text("Nothing learned yet — chat with SmartBot and it'll start remembering things here.")
            } else {
                Column { facts.take(50).forEach { Text("• ${it.text}", modifier = Modifier.padding(vertical = 4.dp)) } }
            }
        }
    )
}

/** Manage one or more LLM connections: FreeLLMAPI, Anthropic, Ollama, any OpenAI-compatible host, local. */
@Composable
fun ProvidersDialog(brain: Brain, onDismiss: () -> Unit) {
    var providers by remember { mutableStateOf(brain.providers()) }
    var activeId by remember { mutableStateOf(brain.activeProviderId()) }
    var editing by remember { mutableStateOf<ProviderConfig?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            TextButton(onClick = {
                editing = ProviderConfig(
                    id = "custom_" + System.currentTimeMillis(), name = "New connection",
                    kind = "openai_compatible", baseUrl = "", apiKey = "", model = ""
                )
            }) { Text("+ Add connection") }
        },
        title = { Text("⚙️ Connections") },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                Text(
                    "Pick which backend SmartBot talks to. Works with FreeLLMAPI, Anthropic, Ollama, LM Studio, OpenRouter — anything OpenAI- or Anthropic-compatible.",
                    fontSize = 12.sp, color = Color(0xFF8A9BB0), modifier = Modifier.padding(bottom = 10.dp)
                )
                providers.forEach { p ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = activeId == p.id, onClick = {
                            activeId = p.id; brain.setActiveProviderId(p.id)
                        })
                        Column(Modifier.weight(1f).clickable { editing = p }) {
                            Text(p.name, fontWeight = FontWeight.Medium)
                            Text(
                                "${p.kind}${if (p.baseUrl.isNotBlank()) " · ${p.baseUrl}" else ""}",
                                fontSize = 11.sp, color = Color(0xFF8A9BB0)
                            )
                        }
                        TextButton(onClick = {
                            brain.removeProvider(p.id)
                            providers = brain.providers(); activeId = brain.activeProviderId()
                        }) { Text("✕") }
                    }
                }
            }
        }
    )

    editing?.let { cfg ->
        ProviderEditDialog(
            initial = cfg,
            onSave = { saved ->
                brain.upsertProvider(saved)
                if (brain.activeProviderId().isBlank()) brain.setActiveProviderId(saved.id)
                providers = brain.providers(); activeId = brain.activeProviderId()
                editing = null
            },
            onCancel = { editing = null }
        )
    }
}

@Composable
fun ProviderEditDialog(initial: ProviderConfig, onSave: (ProviderConfig) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial.name) }
    var kind by remember { mutableStateOf(initial.kind) }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var apiKey by remember { mutableStateOf(initial.apiKey) }
    var model by remember { mutableStateOf(initial.model) }

    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = {
                onSave(initial.copy(name = name, kind = kind, baseUrl = baseUrl, apiKey = apiKey, model = model))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        title = { Text("Edit connection") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                Row {
                    listOf("anthropic" to "Anthropic", "openai_compatible" to "OpenAI-compatible", "local" to "Local").forEach { (k, label) ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(label, fontSize = 11.sp) }, modifier = Modifier.padding(end = 4.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(baseUrl, { baseUrl = it }, label = { Text("Base URL") }, singleLine = true,
                    placeholder = { Text("https://freellmapi.co/v1 or http://localhost:11434/v1") })
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key (blank if none)") }, singleLine = true)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(model, { model = it }, label = { Text("Model name") }, singleLine = true,
                    placeholder = { Text("e.g. gpt-4o-mini, llama3, claude-sonnet-4-6") })
            }
        }
    )
}
