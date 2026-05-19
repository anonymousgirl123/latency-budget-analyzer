package com.kamini.latencyanalyzer.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

import com.kamini.latencyanalyzer.ai.AIService
import com.kamini.latencyanalyzer.analyzer.CodeAnalyzer
import com.kamini.latencyanalyzer.analyzer.LatencyEstimator
import com.kamini.latencyanalyzer.model.AnalysisResult
import com.kamini.latencyanalyzer.model.LatencyNode
import com.kamini.latencyanalyzer.services.LatencyAnalyzerService
import com.kamini.latencyanalyzer.settings.PluginSettings
import com.kamini.latencyanalyzer.toolwindow.LatencyResultPanel

class AnalyzeLatencyAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return showError("No project context found.")

        val editor = e.getData(CommonDataKeys.EDITOR)
                ?: e.getData(CommonDataKeys.EDITOR_EVEN_IF_INACTIVE)
                ?: return showInfo(project, "Open a file and place cursor inside a method.")

        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
                ?: return showInfo(project, "No PSI file found.")

        val method = getMethodAtCaret(editor, psiFile)
                ?: return showInfo(project, "Place cursor inside a method body.")

        runLatencyAnalysis(project, method, psiFile)
    }

    private fun getMethodAtCaret(editor: Editor, psiFile: PsiFile): PsiMethod? {
        val element = psiFile.findElementAt(editor.caretModel.offset) ?: return null
        return PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
    }

    private fun runLatencyAnalysis(project: Project, method: PsiMethod, psiFile: PsiFile) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "Analyzing latency for '${method.name}'...",
                true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                val nodes = analyzeCode(indicator, method)
                val result = estimateLatency(indicator, method, psiFile, nodes)
                val finalResult = enrichWithAI(indicator, result)

                storeResult(project, finalResult)
                updateUI(project, finalResult)
            }
        })
    }

    private fun analyzeCode(
            indicator: ProgressIndicator,
            method: PsiMethod
    ): List<LatencyNode> = runReadAction {
        indicator.text = "Scanning call chain..."
        indicator.fraction = 0.2
        CodeAnalyzer().analyzeMethod(method)
    }

    private fun estimateLatency(
            indicator: ProgressIndicator,
            method: PsiMethod,
            psiFile: PsiFile,
            nodes: List<LatencyNode>
    ): AnalysisResult = runReadAction {
        indicator.text = "Estimating latency..."
        indicator.fraction = 0.5

        val filePath = psiFile.virtualFile?.path.orEmpty()
        LatencyEstimator().buildAnalysisResult(method.name, filePath, nodes)
    }

    private fun enrichWithAI(
            indicator: ProgressIndicator,
            result: AnalysisResult
    ): AnalysisResult {
        indicator.text = "Fetching AI suggestions..."
        indicator.fraction = 0.7

        val apiKey = PluginSettings.getInstance().apiKey
        val suggestions = AIService().getOptimizationSuggestions(result, apiKey)

        return result.copy(aiSuggestions = suggestions)
    }

    private fun storeResult(project: Project, result: AnalysisResult) {
        project.getService(LatencyAnalyzerService::class.java)?.addResult(result)
    }

    private fun updateUI(project: Project, result: AnalysisResult) {
        ApplicationManager.getApplication().invokeLater {
            val service = project.getService(LatencyAnalyzerService::class.java)
            val toolWindow = ToolWindowManager.getInstance(project)
                    .getToolWindow("Latency Analyzer")

            if (toolWindow == null) {
                showNotification(project, result.summaryLine())
                return@invokeLater
            }

            toolWindow.show {
                val panel = service?.resultPanel
                        ?: toolWindow.contentManager?.getContent(0)?.component?.let { findPanel(it) }

                panel?.displayResult(result)
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private fun findPanel(component: java.awt.Component?): LatencyResultPanel? {
        if (component is LatencyResultPanel) return component
        if (component is java.awt.Container) {
            component.components.forEach {
                findPanel(it)?.let { return it }
            }
        }
        return null
    }

    private fun showNotification(project: Project, message: String) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Latency Analyzer")
                .createNotification("⚡ Latency Analysis Complete", message, NotificationType.INFORMATION)
                .notify(project)
    }

    private fun showError(message: String) {
        com.intellij.openapi.ui.Messages.showErrorDialog(message, "Latency Analyzer")
    }

    private fun showInfo(project: Project, message: String) {
        com.intellij.openapi.ui.Messages.showInfoMessage(project, message, "Latency Analyzer")
    }
}
