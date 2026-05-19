package com.kamini.latencyanalyzer.analyzer

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.kamini.latencyanalyzer.model.CallType
import com.kamini.latencyanalyzer.model.LatencyNode
import com.kamini.latencyanalyzer.settings.PluginSettings
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastVisitor

class CodeAnalyzer(
    private val settings: PluginSettings = PluginSettings.getInstance()
) {

    // ── Pattern banks ────────────────────────────────────────────────

    private val httpPatterns = setOf(
        "resttemplate", "webclient", "okhttpclient", "httpclient",
        "restclient", "feignclient", "exchange", "getforobject",
        "postforobject", "getforentity", "postforentity",
        "getformono", "postformono", "retrieve", "bodytomono", "bodytoflux"
    )

    private val dbPatterns = setOf(
        "jdbctemplate", "namedparameterjdbctemplate", "entitymanager",
        "repository", "save", "saveall", "findall", "findbyid",
        "findone", "query", "execute", "executequery", "executeupdate",
        "nativequery", "createquery", "flush"
    )

    private val kafkaPatterns = setOf(
        "kafkatemplate", "kafkaproducer", "send", "senddefault",
        "sendsync", "sendoffsetstotransaction"
    )

    private val redisPatterns = setOf(
        "redistemplate", "valueoperations", "hashoperations",
        "listoperations", "setoperations", "opsforvalue",
        "opsforhash", "opsforlist", "opsforzset",
        "jedis", "lettuce", "rediscache",
        "cacheput", "cacheevict", "cacheable"
    )

    private val sleepPattern = "thread.sleep"

    // ── Public API ───────────────────────────────────────────────────

    fun analyzeMethod(method: PsiMethod): List<LatencyNode> {

        // ✅ UAST path (Java + Kotlin)
        val uMethod = method.toUElement(UMethod::class.java)
        if (uMethod != null) {
            val nodes = mutableListOf<LatencyNode>()

            uMethod.accept(object : AbstractUastVisitor() {
                override fun visitCallExpression(node: UCallExpression): Boolean {
                    classifyUCall(node)?.let { nodes.add(it) }
                    return super.visitCallExpression(node)
                }
            })

            return nodes.sortedBy { it.lineNumber }
        }

        // ✅ PSI fallback (Java only)
        val body = method.body ?: return emptyList()

        return PsiTreeUtil
            .findChildrenOfType(body, PsiMethodCallExpression::class.java)
            .mapNotNull { classifyCall(it) }
            .sortedBy { it.lineNumber }
    }

    // ── UAST Classification ──────────────────────────────────────────

    private fun classifyUCall(call: UCallExpression): LatencyNode? {
        val sourcePsi = call.sourcePsi ?: return null

        val callTextLower = sourcePsi.text.lowercase()
        val methodName = call.methodName ?: return null
        val lineNumber = resolveLineNumber(sourcePsi)
        val filePath = sourcePsi.containingFile?.virtualFile?.path ?: ""
        val snippet = sourcePsi.text.take(120)

        val s = settings.pluginState

        return when {
            httpPatterns.any { callTextLower.contains(it) } ->
                buildNode(
                    "HTTP: $methodName()",
                    CallType.HTTP_CALL,
                    s.httpBaselineMinMs,
                    s.httpBaselineMaxMs,
                    s.httpBaselineP99Ms,
                    snippet,
                    lineNumber,
                    filePath,
                    isBlockingReactive(callTextLower)
                )

            dbPatterns.any { callTextLower.contains(it) } ->
                buildNode(
                    "DB: $methodName()",
                    CallType.DATABASE_QUERY,
                    s.dbBaselineMinMs,
                    s.dbBaselineMaxMs,
                    s.dbBaselineP99Ms,
                    snippet,
                    lineNumber,
                    filePath,
                    true
                )

            kafkaPatterns.any { callTextLower.contains(it) } ->
                buildNode(
                    "Kafka: $methodName()",
                    CallType.KAFKA_PUBLISH,
                    s.kafkaBaselineMinMs,
                    s.kafkaBaselineMaxMs,
                    s.kafkaBaselineP99Ms,
                    snippet,
                    lineNumber,
                    filePath,
                    callTextLower.contains(".get()") || callTextLower.contains(".join()")
                )

            redisPatterns.any { callTextLower.contains(it) } ->
                buildNode(
                    "Redis: $methodName()",
                    CallType.REDIS_CACHE,
                    s.redisBaselineMinMs,
                    s.redisBaselineMaxMs,
                    s.redisBaselineP99Ms,
                    snippet,
                    lineNumber,
                    filePath,
                    true
                )

            callTextLower.contains(sleepPattern) ->
                buildNode(
                    "Thread.sleep()",
                    CallType.THREAD_SLEEP,
                    100,
                    100,
                    100,
                    snippet,
                    lineNumber,
                    filePath,
                    true
                )

            else -> null
        }
    }

    // ── PSI fallback ─────────────────────────────────────────────────

    private fun classifyCall(call: PsiMethodCallExpression): LatencyNode? {
        val callTextLower = call.text.lowercase()
        val methodName = call.methodExpression.referenceName ?: return null
        val lineNumber = resolveLineNumber(call)
        val filePath = call.containingFile?.virtualFile?.path ?: ""
        val snippet = call.text.take(120)

        val s = settings.pluginState

        return when {
            httpPatterns.any { callTextLower.contains(it) } ->
                buildNode("HTTP: $methodName()", CallType.HTTP_CALL,
                    s.httpBaselineMinMs, s.httpBaselineMaxMs, s.httpBaselineP99Ms,
                    snippet, lineNumber, filePath, true)

            else -> null
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun buildNode(
        name: String,
        type: CallType,
        minMs: Long,
        maxMs: Long,
        p99Ms: Long,
        snippet: String,
        line: Int,
        file: String,
        blocking: Boolean
    ) = LatencyNode(
        name = name,
        callType = type,
        estimatedMinMs = minMs,
        estimatedMaxMs = maxMs,
        estimatedP99Ms = p99Ms,
        codeSnippet = snippet,
        lineNumber = line,
        filePath = file,
        isBlocking = blocking
    )

    private fun isBlockingReactive(text: String): Boolean =
        !text.contains("mono") && !text.contains("flux")

    private fun resolveLineNumber(element: PsiElement): Int =
        element.containingFile?.viewProvider?.document
            ?.getLineNumber(element.textOffset)
            ?.plus(1) ?: 0
}
