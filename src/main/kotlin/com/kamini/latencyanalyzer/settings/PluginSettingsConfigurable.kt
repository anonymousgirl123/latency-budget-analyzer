package com.kamini.latencyanalyzer.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import java.awt.*
import javax.swing.*

/**
 * Settings page shown under: Settings → Tools → Latency Budget Analyzer
 *
 * Allows users to configure:
 *   - Claude/OpenAI API key
 *   - Target p99 SLA threshold
 *   - Per-call-type latency baselines (HTTP, DB, Kafka, Redis)
 */
class PluginSettingsConfigurable : Configurable {

    private var apiKeyField: JPasswordField? = null
    private var claudeModelField: JBTextField? = null
    private var targetP99Field: JBTextField? = null

    private var httpMinField: JBTextField? = null
    private var httpMaxField: JBTextField? = null
    private var httpP99Field: JBTextField? = null

    private var dbMinField: JBTextField? = null
    private var dbMaxField: JBTextField? = null
    private var dbP99Field: JBTextField? = null

    private var kafkaMinField: JBTextField? = null
    private var kafkaMaxField: JBTextField? = null
    private var kafkaP99Field: JBTextField? = null

    private var redisMinField: JBTextField? = null
    private var redisMaxField: JBTextField? = null
    private var redisP99Field: JBTextField? = null

    override fun getDisplayName() = "Latency Budget Analyzer"

    override fun createComponent(): JComponent {
        val settings = PluginSettings.getInstance()

        apiKeyField      = JPasswordField(40)
        claudeModelField = JBTextField(settings.pluginState.claudeModel, 30)
        targetP99Field   = numField(settings.targetP99Ms.toString())

        httpMinField = numField(settings.myState.httpBaselineMinMs.toString())
        httpMaxField = numField(settings.myState.httpBaselineMaxMs.toString())
        httpP99Field = numField(settings.myState.httpBaselineP99Ms.toString())

        dbMinField = numField(settings.myState.dbBaselineMinMs.toString())
        dbMaxField = numField(settings.myState.dbBaselineMaxMs.toString())
        dbP99Field = numField(settings.myState.dbBaselineP99Ms.toString())

        kafkaMinField = numField(settings.myState.kafkaBaselineMinMs.toString())
        kafkaMaxField = numField(settings.myState.kafkaBaselineMaxMs.toString())
        kafkaP99Field = numField(settings.myState.kafkaBaselineP99Ms.toString())

        redisMinField = numField(settings.myState.redisBaselineMinMs.toString())
        redisMaxField = numField(settings.myState.redisBaselineMaxMs.toString())
        redisP99Field = numField(settings.myState.redisBaselineP99Ms.toString())

        // Pre-populate
        apiKeyField!!.text = settings.apiKey

        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            add(buildForm(), BorderLayout.NORTH)
        }
    }

    private fun buildForm(): JPanel {
        val panel = JPanel(GridBagLayout())
        var row = 0

        fun gbc(
                x: Int,
                y: Int,
                width: Int = 1,
                fill: Int = GridBagConstraints.HORIZONTAL
        ) = GridBagConstraints().apply {
            gridx = x
            gridy = y
            gridwidth = width
            this.fill = fill
            weightx = if (x == 1) 1.0 else 0.0
            insets = Insets(3, 6, 3, 6)
        }

        fun sectionHeader(title: String) {
            panel.add(
                    JBLabel("<html><b>$title</b></html>").apply {
                        font = Font(Font.SANS_SERIF, Font.BOLD, 12)
                    },
                    gbc(0, row, 4).also {
                        it.gridwidth = 4
                        row++
                    }
            )
        }

        fun addRow(label: String, field: JComponent, hint: String = "") {
            panel.add(JBLabel("$label:"), gbc(0, row))
            panel.add(field, gbc(1, row))
            if (hint.isNotBlank()) {
                panel.add(
                        JBLabel("<html><small>$hint</small></html>"),
                        gbc(2, row)
                )
            }
            row++
        }

        fun addBaselineRow(
                label: String,
                minF: JBTextField,
                maxF: JBTextField,
                p99F: JBTextField
        ) {
            panel.add(JBLabel("$label:"), gbc(0, row))

            panel.add(
                    JBLabel("min ms"),
                    gbc(1, row).also {
                        it.weightx = 0.0
                        it.fill = GridBagConstraints.NONE
                    }
            )
            panel.add(
                    minF.apply { preferredSize = Dimension(60, 26) },
                    gbc(2, row).also { it.weightx = 0.3 }
            )

            panel.add(
                    JBLabel("max ms"),
                    gbc(3, row).also {
                        it.weightx = 0.0
                        it.fill = GridBagConstraints.NONE
                    }
            )
            panel.add(
                    maxF.apply { preferredSize = Dimension(60, 26) },
                    gbc(4, row).also { it.weightx = 0.3 }
            )

            panel.add(
                    JBLabel("p99 ms"),
                    gbc(5, row).also {
                        it.weightx = 0.0
                        it.fill = GridBagConstraints.NONE
                    }
            )
            panel.add(
                    p99F.apply { preferredSize = Dimension(70, 26) },
                    gbc(6, row).also { it.weightx = 0.3 }
            )

            row++
        }

        // ── API ──────────────────────────────────────────────────────────
        sectionHeader("AI Integration")
        addRow("Claude API Key", apiKeyField!!, "Get key at console.anthropic.com")
        addRow("Claude Model", claudeModelField!!, "e.g. 6  (update if you get a 404)")
        addRow("Target P99 SLA (ms)", targetP99Field!!, "Highlight methods exceeding this budget")

        panel.add(JSeparator(), gbc(0, row, 7).also { row++ })

        // ── Baselines ────────────────────────────────────────────────────
        sectionHeader("Latency Baselines (calibrate for your infrastructure)")
        panel.add(
                JBLabel("<html><small>Adjust these values to match your actual P99 measurements.</small></html>"),
                gbc(0, row, 7).also { row++ }
        )

        addBaselineRow("🌐 HTTP", httpMinField!!, httpMaxField!!, httpP99Field!!)
        addBaselineRow("🗄️  DB", dbMinField!!, dbMaxField!!, dbP99Field!!)
        addBaselineRow("📨 Kafka", kafkaMinField!!, kafkaMaxField!!, kafkaP99Field!!)
        addBaselineRow("⚡ Redis", redisMinField!!, redisMaxField!!, redisP99Field!!)

        return panel
    }

    override fun isModified(): Boolean {
        val s = PluginSettings.getInstance()
        return String(apiKeyField?.password ?: charArrayOf()) != s.apiKey ||
                claudeModelField?.text != s.pluginState.claudeModel ||
                targetP99Field?.text != s.targetP99Ms.toString()
    }

    override fun apply() {
        val s = PluginSettings.getInstance()

        s.apiKey = String(apiKeyField?.password ?: charArrayOf())
        s.pluginState.claudeModel =
                claudeModelField?.text?.trim()?.ifBlank { "claude-sonnet-4-6" }
                        ?: "claude-sonnet-4-6"
        s.targetP99Ms = targetP99Field?.text?.toLongOrNull() ?: 150L

        s.myState.httpBaselineMinMs = httpMinField?.text?.toLongOrNull() ?: 20L
        s.myState.httpBaselineMaxMs = httpMaxField?.text?.toLongOrNull() ?: 500L
        s.myState.httpBaselineP99Ms = httpP99Field?.text?.toLongOrNull() ?: 2_000L

        s.myState.dbBaselineMinMs = dbMinField?.text?.toLongOrNull() ?: 5L
        s.myState.dbBaselineMaxMs = dbMaxField?.text?.toLongOrNull() ?: 200L
        s.myState.dbBaselineP99Ms = dbP99Field?.text?.toLongOrNull() ?: 1_000L

        s.myState.kafkaBaselineMinMs = kafkaMinField?.text?.toLongOrNull() ?: 1L
        s.myState.kafkaBaselineMaxMs = kafkaMaxField?.text?.toLongOrNull() ?: 50L
        s.myState.kafkaBaselineP99Ms = kafkaP99Field?.text?.toLongOrNull() ?: 200L

        s.myState.redisBaselineMinMs = redisMinField?.text?.toLongOrNull() ?: 1L
        s.myState.redisBaselineMaxMs = redisMaxField?.text?.toLongOrNull() ?: 10L
        s.myState.redisBaselineP99Ms = redisP99Field?.text?.toLongOrNull() ?: 50L
    }

    override fun reset() {
        val s = PluginSettings.getInstance()

        apiKeyField?.text = s.apiKey
        claudeModelField?.text = s.pluginState.claudeModel
        targetP99Field?.text = s.targetP99Ms.toString()

        httpMinField?.text = s.myState.httpBaselineMinMs.toString()
        httpMaxField?.text = s.myState.httpBaselineMaxMs.toString()
        httpP99Field?.text = s.myState.httpBaselineP99Ms.toString()

        dbMinField?.text = s.myState.dbBaselineMinMs.toString()
        dbMaxField?.text = s.myState.dbBaselineMaxMs.toString()
        dbP99Field?.text = s.myState.dbBaselineP99Ms.toString()

        kafkaMinField?.text = s.myState.kafkaBaselineMinMs.toString()
        kafkaMaxField?.text = s.myState.kafkaBaselineMaxMs.toString()
        kafkaP99Field?.text = s.myState.kafkaBaselineP99Ms.toString()

        redisMinField?.text = s.myState.redisBaselineMinMs.toString()
        redisMaxField?.text = s.myState.redisBaselineMaxMs.toString()
        redisP99Field?.text = s.myState.redisBaselineP99Ms.toString()
    }

    // Helper — delegates to the pluginState accessor
    private val PluginSettings.myState: PluginSettings.State
        get() = this.pluginState

    private fun numField(default: String) =
            JBTextField(default, 8).apply {
                preferredSize = Dimension(80, 26)
            }
}
