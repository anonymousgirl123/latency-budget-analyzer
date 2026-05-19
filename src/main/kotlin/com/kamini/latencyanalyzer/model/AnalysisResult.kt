package com.kamini.latencyanalyzer.model
/** * The full result of analyzing a single Java/Kotlin method for latency budget. */
data class AnalysisResult(
        /** Simple name of the analyzed method */
        val methodName: String,
        /** Absolute path to the source file */
        val filePath: String,
        /** All detected latency-contributing calls in order of appearance */
        val callChain: List<LatencyNode>,
        /** Sum of all node min estimates (best-case total latency) */
        val totalMinMs: Long,
        /** Sum of all node max estimates (worst-case total latency) */
        val totalMaxMs: Long,
        /** Sum of all node p99 estimates (SLA-relevant latency) */
        val totalP99Ms: Long,
        /** Nodes contributing more than 20% of the total p99 budget */
        val hotspots: List<LatencyNode>,
        /** AI-generated optimization suggestions (populated async) */
        val aiSuggestions: String = "",
        val timestamp: Long = System.currentTimeMillis() ) {
    /** Whether the estimated p99 exceeds the user-configured SLA target */
    fun exceedsSlaTarget(targetMs: Long): Boolean = totalP99Ms > targetMs /** Summary string for display in notifications/headers */
    fun summaryLine(): String = "[$methodName] p99≈${totalP99Ms}ms | ${callChain.size} calls | ${hotspots.size} hotspots"
}