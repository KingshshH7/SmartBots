package com.smartbot.app

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * A connection to any LLM backend SmartBot can talk to.
 * kind drives which wire format is used:
 *  - "anthropic"        -> api.anthropic.com/v1/messages format
 *  - "openai_compatible" -> {baseUrl}/chat/completions format
 *                            (works for FreeLLMAPI, OpenRouter, Ollama,
 *                             LM Studio, Groq, together.ai, vLLM, etc —
 *                             anything that speaks the OpenAI schema)
 *  - "local"            -> reserved for an on-device runtime you wire up
 *                            yourself (e.g. a local Ollama server at
 *                            127.0.0.1, or Android's on-device Gemini Nano
 *                            via ML Kit GenAI on supported Pixel devices).
 *                            Ships as a clearly-marked stub below.
 */
data class ProviderConfig(
    val id: String,
    val name: String,
    val kind: String, // "anthropic" | "openai_compatible" | "local"
    val baseUrl: String, // e.g. https://freellmapi.co/v1  or http://localhost:3001/v1
    val apiKey: String,
    val model: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("kind", kind)
        put("baseUrl", baseUrl); put("apiKey", apiKey); put("model", model)
    }
    companion object {
        fun fromJson(o: JSONObject) = ProviderConfig(
            o.getString("id"), o.getString("name"), o.getString("kind"),
            o.optString("baseUrl"), o.optString("apiKey"), o.optString("model")
        )
        fun defaults(): List<ProviderConfig> = listOf(
            ProviderConfig("anthropic", "Claude (Anthropic)", "anthropic",
                "https://api.anthropic.com", "", "claude-sonnet-4-6"),
            ProviderConfig("freellmapi", "FreeLLMAPI", "openai_compatible",
                "https://freellmapi.co/v1", "", "gpt-4o-mini"),
            ProviderConfig("local", "Local (on-device)", "local", "http://localhost:11434/v1", "", "llama3")
        )
    }
}

sealed class LLMResult {
    data class Success(val text: String) : LLMResult()
    data class Error(val message: String) : LLMResult()
}

object LLM {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    fun send(
        config: ProviderConfig,
        userMessage: String,
        history: List<Pair<String, String>>,
        systemPrompt: String
    ): LLMResult {
        return when (config.kind) {
            "anthropic" -> sendAnthropic(config, userMessage, history, systemPrompt)
            "openai_compatible" -> sendOpenAICompatible(config, userMessage, history, systemPrompt)
            "local" -> sendOpenAICompatible(config, userMessage, history, systemPrompt) // same schema, local host
            else -> LLMResult.Error("Unknown provider kind: ${config.kind}")
        }
    }

    private fun sendAnthropic(
        config: ProviderConfig, userMessage: String,
        history: List<Pair<String, String>>, systemPrompt: String
    ): LLMResult {
        val messages = JSONArray()
        history.forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text)) }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val body = JSONObject().apply {
            put("model", config.model.ifBlank { "claude-sonnet-4-6" })
            put("max_tokens", 1024)
            put("system", systemPrompt)
            put("messages", messages)
        }
        val url = config.baseUrl.trimEnd('/') + "/v1/messages"
        val req = Request.Builder().url(url)
            .addHeader("x-api-key", config.apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val str = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return LLMResult.Error("Anthropic API error ${resp.code}: $str")
                val json = JSONObject(str)
                val content = json.getJSONArray("content")
                val text = (0 until content.length()).map { content.getJSONObject(it) }
                    .filter { it.optString("type") == "text" }
                    .joinToString("") { it.getString("text") }
                LLMResult.Success(text)
            }
        } catch (e: Exception) { LLMResult.Error(e.message ?: "network error") }
    }

    /** OpenAI-compatible /chat/completions — works for FreeLLMAPI, OpenRouter, Ollama, LM Studio, etc. */
    private fun sendOpenAICompatible(
        config: ProviderConfig, userMessage: String,
        history: List<Pair<String, String>>, systemPrompt: String
    ): LLMResult {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        history.forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text)) }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val body = JSONObject().apply {
            put("model", config.model)
            put("messages", messages)
            put("max_tokens", 1024)
        }
        val base = config.baseUrl.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val reqBuilder = Request.Builder().url(url)
            .addHeader("content-type", "application/json")
        if (config.apiKey.isNotBlank()) {
            reqBuilder.addHeader("Authorization", "Bearer ${config.apiKey}")
        }
        val req = reqBuilder.post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return try {
            client.newCall(req).execute().use { resp ->
                val str = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return LLMResult.Error("${config.name} error ${resp.code}: $str")
                val json = JSONObject(str)
                val text = json.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                LLMResult.Success(text)
            }
        } catch (e: Exception) { LLMResult.Error(e.message ?: "network error — is the endpoint reachable?") }
    }
}
