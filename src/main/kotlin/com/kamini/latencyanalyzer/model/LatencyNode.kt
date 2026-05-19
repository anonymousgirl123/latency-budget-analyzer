package com.kamini.latencyanalyzer.model

/**
 * Represents the type of an outbound call detected in the code.
 */
enum class CallType(val displayName: String, val icon: String) {
    HTTP_CALL("HTTP", "🌐"),
    DATABASE_QUERY("DB", "🗄️"),
    KAFKA_PUBLISH("Kafka", "📨"),
    REDIS_CACHE("Redis", "⚡"),
    THREAD_SLEEP("Sleep", "💤"),
    BLOCKING_IO("I/O", "📁"),
    EXTERNAL_API("API", "🔌"),
    INTERNAL_METHOD("Method", "🔧")
}

/**
 * A single detected latency-contributing node in the call chain.
 * Represents one outbound call (HTTP, DB, Kafka, etc.) found in the method body.
 */
data class LatencyNode(
        val name: String,
        val callType: CallType,

        /** Optimistic latency — fast path, warm cache, healthy service */
        val estimatedMinMs: Long,

        /** Pessimistic latency — slow DB, cold start, high load */
        val estimatedMaxMs: Long,

        /** 99th percentile estimate — worst-case for SLA calculations */
        val estimatedP99Ms: Long,

        /** The actual code snippet (truncated) triggering this call */
        val codeSnippet: String,

        val lineNumber: Int,
        val filePath: String,

        /** True if this call blocks the calling thread (bad for throughput) */
        val isBlocking: Boolean = false,

        /** Nested calls within this operation (e.g., DB call inside a loop) */
        val children: MutableList<LatencyNode> = mutableListOf()
) {
    val estimatedAvgMs: Long get() = (estimatedMinMs + estimatedMaxMs) / 2

    /** Fraction of a given total p99 budget consumed by this node */
    fun budgetFraction(totalP99Ms: Long): Double =
            if (totalP99Ms == 0L) 0.0 else estimatedP99Ms.toDouble() / totalP99Ms
}
