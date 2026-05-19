package com.kamini.latencyanalyzer.toolwindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.kamini.latencyanalyzer.model.AnalysisResult
import com.kamini.latencyanalyzer.model.CallType
import com.kamini.latencyanalyzer.model.LatencyNode
import java.awt.*
import javax.swing.*
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.DefaultTableModel

/**
 * Main UI panel rendered inside the "Latency Analyzer" tool window.
 *
 * Layout:
 *   ┌─────────────────────────────────────────────────┐
 *   │  Header (method name + summary)                  │
 *   ├───────────────┬─────────────────────────────────┤
 *   │  Summary      │  P99 Budget Gauge                │
 *   ├───────────────┴─────────────────────────────────┤
 *   │  Call Chain Table (sortable)                     │
 *   ├─────────────────────────────────────────────────┤
 *   │  AI Optimization Suggestions (scrollable text)   │
 *   └─────────────────────────────────────────────────┘
 */
class LatencyResultPanel : JPanel(BorderLayout(0, 8)) {

    // ── Widgets ────────────────────────────────────────────────────────────

    private val headerLabel =
            JLabel("🔍 Right-click a method → Analyze Latency Budget").apply {
                font = Font(Font.SANS_SERIF, Font.BOLD, 13)
                border = BorderFactory.createEmptyBorder(4, 0, 4, 0)
            }

    private val methodLabel = metaLabel("Method:")
    private val callCountLabel = metaLabel("Calls:")
    private val minLabel = metaLabel("Min:")
    private val maxLabel = metaLabel("Max:")
    private val p99Label = metaLabel("P99:")
    private val slaLabel = metaLabel("SLA:")

    private val TABLE_COLS =
            arrayOf("", "Type", "Operation", "Line", "Min ms", "Max ms", "P99 ms", "🔒")

    private val tableModel = object : DefaultTableModel(TABLE_COLS, 0) {
        override fun isCellEditable(row: Int, column: Int) = false

        // Must use boxed Long
        override fun getColumnClass(col: Int): Class<*> =
                if (col in listOf(3, 4, 5, 6))
                    Long::class.javaObjectType
                else
                    String::class.java
    }

    private val table = JBTable(tableModel).apply {
        setShowGrid(true)
        intercellSpacing = Dimension(1, 1)
        rowHeight = 22
        autoCreateRowSorter = true

        columnModel.apply {
            getColumn(0).preferredWidth = 28
            getColumn(0).maxWidth = 28

            getColumn(1).preferredWidth = 70
            getColumn(2).preferredWidth = 220
            getColumn(3).preferredWidth = 50
            getColumn(4).preferredWidth = 60
            getColumn(5).preferredWidth = 60
            getColumn(6).preferredWidth = 65

            getColumn(7).preferredWidth = 30
            getColumn(7).maxWidth = 30
        }
    }

    private val aiTextArea = JBTextArea(8, 40).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        background = JBColor.PanelBackground
        text = "AI suggestions will appear here after analysis."
        margin = Insets(6, 8, 6, 8)
    }

    // ── Init ───────────────────────────────────────────────────────────────

    init {
        background = JBColor.PanelBackground
        border = BorderFactory.createEmptyBorder(8, 10, 8, 10)
        setupLayout()
        installRowRenderer()
    }

    // ── Layout ─────────────────────────────────────────────────────────────

    private fun setupLayout() {
        val summaryGrid = JPanel(GridLayout(3, 4, 12, 4)).apply {
            background = JBColor.PanelBackground
            border = BorderFactory.createCompoundBorder(
                    BorderFactory.createTitledBorder("📊 Summary"),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)
            )

            fun addPair(key: String, value: JLabel) {
                add(JLabel(key).apply {
                    font = Font(Font.SANS_SERIF, Font.BOLD, 11)
                })
                add(value)
            }

            addPair("Method:", methodLabel)
            addPair("Calls:", callCountLabel)
            addPair("Min:", minLabel)
            addPair("Max:", maxLabel)
            addPair("P99:", p99Label)
            addPair("SLA:", slaLabel)
        }

        val topPanel = JPanel(BorderLayout(0, 6)).apply {
            background = JBColor.PanelBackground
            add(headerLabel, BorderLayout.NORTH)
            add(summaryGrid, BorderLayout.CENTER)
        }

        val tablePanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("🔗 Call Chain")
            add(JBScrollPane(table), BorderLayout.CENTER)
        }

        val aiPanel = JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder("🤖 AI Optimization Suggestions")
            add(JBScrollPane(aiTextArea), BorderLayout.CENTER)
        }

        val splitPane = JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                tablePanel,
                aiPanel
        ).apply {
            resizeWeight = 0.55
            isContinuousLayout = true
            dividerSize = 5
        }

        add(topPanel, BorderLayout.NORTH)
        add(splitPane, BorderLayout.CENTER)
    }

    // ── Public API ─────────────────────────────────────────────────────────

    fun displayResult(result: AnalysisResult) {
        headerLabel.text = "✅  ${result.methodName}  |  ${result.summaryLine()}"

        methodLabel.text = result.methodName
        callCountLabel.text = "${result.callChain.size}"
        minLabel.text = "${result.totalMinMs} ms"
        maxLabel.text = "${result.totalMaxMs} ms"

        p99Label.text = "${result.totalP99Ms} ms"
        p99Label.foreground = p99Color(result.totalP99Ms)
        p99Label.font = Font(Font.SANS_SERIF, Font.BOLD, 12)

        slaLabel.text =
                if (result.hotspots.isEmpty()) "✅ OK"
                else "⚠️  ${result.hotspots.size} hotspot(s)"

        slaLabel.foreground =
                if (result.hotspots.isEmpty()) JBColor.GREEN else JBColor.ORANGE

        populateTable(result)

        aiTextArea.text =
                result.aiSuggestions.ifBlank {
                    "No AI suggestions (check your API key in Settings)."
                }
        aiTextArea.caretPosition = 0
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun populateTable(result: AnalysisResult) {
        tableModel.rowCount = 0
        val hotspotLines = result.hotspots.map { it.lineNumber }.toSet()

        result.callChain.forEach { node ->
            val hotspotMarker =
                    if (node.lineNumber in hotspotLines) "🔥" else ""

            tableModel.addRow(
                    arrayOf(
                            node.callType.icon,
                            node.callType.displayName,
                            node.name,
                            node.lineNumber.toLong(),
                            node.estimatedMinMs,
                            node.estimatedMaxMs,
                            node.estimatedP99Ms,
                            if (node.isBlocking) "🔴$hotspotMarker" else "🟢$hotspotMarker"
                    )
            )
        }
    }

    private fun installRowRenderer() {
        val renderer = object : DefaultTableCellRenderer() {
            override fun getTableCellRendererComponent(
                    t: JTable,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    col: Int
            ): Component {
                val c = super.getTableCellRendererComponent(
                        t, value, isSelected, hasFocus, row, col
                )

                if (!isSelected) {
                    val p99Cell = t.getValueAt(row, 6) as? Long ?: 0L

                    c.background = when {
                        p99Cell >= 1_000L -> JBColor(Color(255, 220, 220), Color(80, 30, 30))
                        p99Cell >= 200L   -> JBColor(Color(255, 245, 200), Color(70, 55, 10))
                        else              -> JBColor.PanelBackground
                    }
                }

                return c
            }
        }

        for (i in 0 until TABLE_COLS.size) {
            table.columnModel.getColumn(i).cellRenderer = renderer
        }
    }

    private fun p99Color(ms: Long) = when {
        ms > 1_000L -> JBColor.RED
        ms > 500L   -> JBColor.ORANGE
        else        -> JBColor(Color(0, 150, 0), Color(80, 200, 80))
    }

    private fun metaLabel(default: String) = JLabel(default).apply {
        font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
    }
}
