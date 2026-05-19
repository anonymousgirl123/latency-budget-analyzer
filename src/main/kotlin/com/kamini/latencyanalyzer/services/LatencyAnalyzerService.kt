package com.kamini.latencyanalyzer.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.kamini.latencyanalyzer.model.AnalysisResult
import com.kamini.latencyanalyzer.toolwindow.LatencyResultPanel
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service(Service.Level.PROJECT)
class LatencyAnalyzerService(private val project: Project) {

    private val history = ArrayDeque<AnalysisResult>(MAX_HISTORY)
    private val lock = ReentrantLock()

    /**
     * Live reference to the tool window panel.
     * Volatile ensures visibility across background + EDT threads.
     */
    @Volatile
    var resultPanel: LatencyResultPanel? = null

    /** Add latest result safely (thread-safe) */
    fun addResult(result: AnalysisResult) = lock.withLock {
        history.addFirst(result)
        if (history.size > MAX_HISTORY) {
            history.removeLast()
        }
    }

    /** Snapshot copy (safe for UI iteration) */
    fun getHistory(): List<AnalysisResult> = lock.withLock {
        history.toList()
    }

    /** Latest result */
    fun latestResult(): AnalysisResult? = lock.withLock {
        history.firstOrNull()
    }

    /** Clear all stored results */
    fun clearHistory() = lock.withLock {
        history.clear()
    }

    companion object {
        private const val MAX_HISTORY = 50
    }
}
