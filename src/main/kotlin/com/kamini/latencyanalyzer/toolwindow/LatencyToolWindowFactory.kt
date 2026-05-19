package com.kamini.latencyanalyzer.toolwindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.kamini.latencyanalyzer.services.LatencyAnalyzerService

/**
 * Registers the "Latency Analyzer" tool window and seeds it with a
 * fresh [LatencyResultPanel]. Also registers the panel with the
 * project service so actions can reliably push results to it.
 */
class LatencyToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow
    ) {
        val panel = LatencyResultPanel()
        val service = project.getService(LatencyAnalyzerService::class.java)

        // Register panel in service so actions can push results directly
        service?.resultPanel = panel

        // ── Lazy-init catch-up ────────────────────────────────────────────
        // The tool window is created lazily — on first show. If the action ran
        // before this window was ever opened, the result was stored in the service
        // (Step 4 in AnalyzeLatencyAction) but the panel didn't exist yet.
        // Display the waiting result now so the user sees it immediately.
        service?.latestResult()?.let { pending ->
            panel.displayResult(pending)
        }

        val content = ContentFactory.getInstance()
            .createContent(panel, "", false)

        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
