package com.smartbot.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * SmartBot's "brain": a simple on-device, persistent memory log.
 * Stored in SharedPreferences as a JSON array so it survives app restarts.
 * Nothing here ever leaves the device except as text sent to the AI API
 * for the purpose of answering the current message.
 */
class Brain(context: Context) {
    private val prefs = context.getSharedPreferences("smartbot_brain", Context.MODE_PRIVATE)

    data class Fact(val text: String, val timestamp: Long)

    fun remember(fact: String) {
        val facts = allFacts().toMutableList()
        facts.add(0, Fact(fact, System.currentTimeMillis()))
        save(facts)
    }

    fun allFacts(): List<Fact> {
        val raw = prefs.getString("facts", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Fact(o.getString("text"), o.getLong("ts"))
        }
    }

    fun clear() {
        prefs.edit().remove("facts").apply()
    }

    private fun save(facts: List<Fact>) {
        val arr = JSONArray()
        facts.forEach {
            val o = JSONObject()
            o.put("text", it.text)
            o.put("ts", it.timestamp)
            arr.put(o)
        }
        prefs.edit().putString("facts", arr.toString()).apply()
    }

    /** Facts formatted as context to feed back into the AI prompt. */
    fun asContext(limit: Int = 30): String {
        val facts = allFacts().take(limit)
        if (facts.isEmpty()) return ""
        return "Known facts about the user (from past conversations):\n" +
            facts.joinToString("\n") { "- ${it.text}" }
    }

    // --- Provider management (multiple connections: FreeLLMAPI, Anthropic, local, etc.) ---

    fun providers(): List<ProviderConfig> {
        val raw = prefs.getString("providers", null)
        if (raw == null) {
            val d = ProviderConfig.defaults()
            saveProviders(d)
            return d
        }
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { ProviderConfig.fromJson(arr.getJSONObject(it)) }
    }

    fun saveProviders(list: List<ProviderConfig>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("providers", arr.toString()).apply()
    }

    fun upsertProvider(config: ProviderConfig) {
        val list = providers().toMutableList()
        val idx = list.indexOfFirst { it.id == config.id }
        if (idx >= 0) list[idx] = config else list.add(config)
        saveProviders(list)
    }

    fun removeProvider(id: String) {
        saveProviders(providers().filterNot { it.id == id })
        if (activeProviderId() == id) setActiveProviderId(providers().firstOrNull()?.id ?: "")
    }

    fun activeProviderId(): String =
        prefs.getString("active_provider", null) ?: providers().firstOrNull()?.id ?: ""

    fun setActiveProviderId(id: String) = prefs.edit().putString("active_provider", id).apply()

    fun activeProvider(): ProviderConfig? = providers().find { it.id == activeProviderId() }
}
