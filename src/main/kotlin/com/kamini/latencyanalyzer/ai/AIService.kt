package com.kamini.latencyanalyzer.ai

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.util.net.HttpConfigurable
import com.kamini.latencyanalyzer.model.AnalysisResult
import com.kamini.latencyanalyzer.model.CallType
import com.kamini.latencyanalyzer.settings.PluginSettings
import okhttp3.*
import okhttp3.Credentials.basic
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/*

Calls the Anthropic Claude API to produce optimization suggestions

for an analyzed method's latency breakdown.



All network I/O is synchronous — callers must invoke this from a

background thread (e.g., inside a ProgressManager Task).



Proxy support: reads IntelliJ's HTTP proxy configuration

(Settings → Appearance & Behavior → System Settings → HTTP Proxy).

On corporate networks (e.g., ABC), configure the proxy there and

all plugin API calls will route through it automatically.
*/

class AIService {

    private val gson = Gson()
    private val json = "application/json; charset=utf-8".toMediaType()

    /**

    OkHttpClient wired up with IntelliJ's HTTP proxy settings.

    Falls back to a direct connection if proxy config is unavailable.*/
    private val client: OkHttpClient = buildClient()

    companion object {
        private const val CLAUDE_API_URL = "https://api.anthropic.com/v1/messages"
        private const val MAX_TOKENS = 1024
    }


// ── Client construction ────────────────────────────────────────────────

    private fun buildClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)


        try {
            val cfg = HttpConfigurable.getInstance()

            if (cfg.USE_HTTP_PROXY && !cfg.PROXY_HOST.isNullOrBlank()) {
                // Build the proxy — SOCKS or HTTP
                val proxyType = if (cfg.PROXY_TYPE_IS_SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP
                val proxy = Proxy(
                        proxyType,
                        InetSocketAddress.createUnresolved(cfg.PROXY_HOST, cfg.PROXY_PORT)
                )
                builder.proxy(proxy)

                // Attach credentials if the proxy requires authentication
                if (cfg.PROXY_AUTHENTICATION) {
                    val login = cfg.getProxyLogin() ?: ""
                    val password = cfg.getPlainProxyPassword() ?: ""
                    if (login.isNotBlank()) {
                        builder.proxyAuthenticator { _, response ->
                            // Avoid infinite retry loops if credentials are wrong
                            if (response.request.header("Proxy-Authorization") != null) {
                                null   // give up — already tried once
                            } else {
                                response.request.newBuilder()
                                        .header("Proxy-Authorization", basic(login, password))
                                        .build()
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // HttpConfigurable unavailable (e.g., headless env) — proceed without proxy
        }
        return builder.build()
    }

    // ── Public API ─────────────────────────────────────────────────────────

    /**

    Returns AI-generated optimization suggestions as a formatted string.

    Returns a user-facing error/hint string on failure — never throws.*/fun getOptimizationSuggestions(result: AnalysisResult, apiKey: String): String {if (apiKey.isBlank()) {return """⚠️  No API key configured.

Go to: Settings → Tools → Latency Budget Analyzer → enter your Claude API key.Get a key at: https://console.anthropic.com""".trimIndent()}

        return try {
            callClaude(buildPrompt(result), apiKey)
        } catch (e: Exception) {
            "❌ AI call failed: ${e.message}\n\nCheck your API key and network/proxy connection."
        }
    }

// ── Prompt construction ────────────────────────────────────────────────
    private fun buildPrompt(result: AnalysisResult): String {
    val callsSummary = result.callChain.joinToString("\n") { node ->
        val blockingFlag = if (node.isBlocking) " [BLOCKING THREAD]" else ""
        "  - [${node.callType.displayName}] ${node.name} @ line ${node.lineNumber}" +
                " → min=${node.estimatedMinMs}ms, max=${node.estimatedMaxMs}ms," +
                " p99=${node.estimatedP99Ms}ms$blockingFlag"
    }

    val hotspotSummary = if (result.hotspots.isEmpty()) {
        "  None (no single call exceeds 20% of p99 budget)"
    } else {
        result.hotspots.joinToString("\n") { h ->
            "  🔴 ${h.name}: ${h.estimatedP99Ms}ms p99" +
                    " (${(h.budgetFraction(result.totalP99Ms) * 100).toInt()}% of budget)"
        }
    }

    val blockingCalls = result.callChain
            .filter { it.isBlocking && it.callType != CallType.THREAD_SLEEP }
            .joinToString(", ") { it.name }
            .ifBlank { "none detected" }

    return """

You are a senior Java/Spring Boot performance engineer reviewing a latency budget analysis.

Method: ${result.methodName}Total Latency Estimate: min=${result.totalMinMs}ms | max=${result.totalMaxMs}ms | p99=${result.totalP99Ms}msCalls Detected: ${result.callChain.size}

Call Chain (in order of appearance):$callsSummary

Hotspots (≥20% of p99 budget):$hotspotSummary

Blocking calls: $blockingCalls

Please provide a structured response with these exact sections:
🔴 CRITICAL ISSUESList any blocking calls on latency-sensitive threads, N+1 query risks, or synchronous waits that should be async.

🟡 OPTIMIZATION OPPORTUNITIESSuggest caching strategies, parallel execution (CompletableFuture, reactive), batching, or connection pool tuning.

🟢 QUICK WINSLow-effort, high-impact improvements (e.g., add @Cacheable, fire-and-forget Kafka publishes).

💡 RECOMMENDED FIX (code snippet)Show the single most impactful change as a concise Java/Kotlin code snippet.

Be specific, concise, and actionable. Reference method names and line numbers where relevant.
""".trimIndent()}


// ── API call ───────────────────────────────────────────────────────────

private fun callClaude(prompt: String, apiKey: String): String {
    val messages = JsonArray().apply {
        add(JsonObject().apply {
            addProperty("role", "user")
            addProperty("content", prompt)
        })
    }

    val model = PluginSettings.getInstance().pluginState.claudeModel
    val requestBody = JsonObject().apply {
        addProperty("model", model)
        addProperty("max_tokens", MAX_TOKENS)
        add("messages", messages)
    }

    val request = Request.Builder()
        .url(CLAUDE_API_URL)
        .header("x-api-key", apiKey)
        .header("anthropic-version", "2023-06-01")
        .header("content-type", "application/json")
        .post(requestBody.toString().toRequestBody(json))
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "no body"
            return "❌ API error ${response.code}: $errorBody"
        }

        val responseText = response.body?.string()
            ?: return "❌ Empty response from API"

        val parsed = gson.fromJson(responseText, JsonObject::class.java)

        return parsed
            .getAsJsonArray("content")
            ?.firstOrNull()
            ?.asJsonObject
            ?.get("text")
            ?.asString
            ?: "❌ Could not parse AI response"
    }
}

}


