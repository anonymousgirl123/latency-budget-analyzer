package com.kamini.latencyanalyzer.analyzer

import com.kamini.latencyanalyzer.model.AnalysisResult
import com.kamini.latencyanalyzer.model.LatencyNode

class LatencyEstimator {

    private val hotspotThresholdFraction = 0.20
    private val hotspotAbsoluteMs = 100L   // absolute threshold (important)

    fun buildAnalysisResult(
            methodName: String,
            filePath: String,
            nodes: List<LatencyNode>
    ): AnalysisResult {

        val sequential = nodes.filter { it.isBlocking }
        val parallel = nodes.filterNot { it.isBlocking }

        val totalMinMs = sequential.sumOf { it.estimatedMinMs } +
                (parallel.maxOfOrNull { it.estimatedMinMs } ?: 0)

        val totalMaxMs = sequential.sumOf { it.estimatedMaxMs } +
                (parallel.maxOfOrNull { it.estimatedMaxMs } ?: 0)

        val totalP99Ms = sequential.sumOf { it.estimatedP99Ms } +
                (parallel.maxOfOrNull { it.estimatedP99Ms } ?: 0)

        val hotspots = identifyHotspots(nodes, totalP99Ms)

        return AnalysisResult(
                methodName = methodName,
                filePath = filePath,
                callChain = nodes,
                totalMinMs = totalMinMs,
                totalMaxMs = totalMaxMs,
                totalP99Ms = totalP99Ms,
                hotspots = hotspots
        )
    }

    private fun identifyHotspots(
            nodes: List<LatencyNode>,
            totalP99Ms: Long
    ): List<LatencyNode> {
        if (totalP99Ms == 0L) return emptyList()

        return nodes
                .filter { node ->
                    val fraction = node.budgetFraction(totalP99Ms)
                    node.estimatedP99Ms >= hotspotAbsoluteMs ||
                            fraction >= hotspotThresholdFraction
                }
                .sortedByDescending { it.estimatedP99Ms }
    }
}
