package com.kamini.latencyanalyzer.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

@State(
        name = "LatencyBudgetAnalyzerSettings",
        storages = [Storage("latency-budget-analyzer.xml")]
)
class PluginSettings : PersistentStateComponent<PluginSettings.State> {

    data class State(
            var apiKey: String = "",
            var claudeModel: String = "claude-sonnet-4-6",
            var targetP99Ms: Long = 150L,

            var httpBaselineMinMs: Long = 20L,
            var httpBaselineMaxMs: Long = 500L,
            var httpBaselineP99Ms: Long = 2_000L,

            var dbBaselineMinMs: Long = 5L,
            var dbBaselineMaxMs: Long = 200L,
            var dbBaselineP99Ms: Long = 1_000L,

            var kafkaBaselineMinMs: Long = 1L,
            var kafkaBaselineMaxMs: Long = 50L,
            var kafkaBaselineP99Ms: Long = 200L,

            var redisBaselineMinMs: Long = 1L,
            var redisBaselineMaxMs: Long = 10L,
            var redisBaselineP99Ms: Long = 50L
    )

    private var state = State()

    // ── Safe accessors ─────────────────────────────────────────────────────

    var apiKey: String
        get() = state.apiKey
        set(value) {
            state.apiKey = value.trim()
        }

    var targetP99Ms: Long
        get() = state.targetP99Ms
        set(value) {
            state.targetP99Ms = value.coerceAtLeast(1)
        }

    val pluginState: State
        get() = state

    // ── PersistentStateComponent ───────────────────────────────────────────

    override fun getState(): State = state

    override fun loadState(loaded: State) {
        XmlSerializerUtil.copyBean(loaded, state)
        sanitize()
    }

    // ── Validation / normalization ─────────────────────────────────────────

    private fun sanitize() {
        state.apply {
            targetP99Ms = targetP99Ms.coerceAtLeast(1)

            httpBaselineMinMs = httpBaselineMinMs.coerceAtLeast(0)
            httpBaselineMaxMs = httpBaselineMaxMs.coerceAtLeast(httpBaselineMinMs)
            httpBaselineP99Ms = httpBaselineP99Ms.coerceAtLeast(httpBaselineMaxMs)

            dbBaselineMinMs = dbBaselineMinMs.coerceAtLeast(0)
            dbBaselineMaxMs = dbBaselineMaxMs.coerceAtLeast(dbBaselineMinMs)
            dbBaselineP99Ms = dbBaselineP99Ms.coerceAtLeast(dbBaselineMaxMs)

            kafkaBaselineMinMs = kafkaBaselineMinMs.coerceAtLeast(0)
            kafkaBaselineMaxMs = kafkaBaselineMaxMs.coerceAtLeast(kafkaBaselineMinMs)
            kafkaBaselineP99Ms = kafkaBaselineP99Ms.coerceAtLeast(kafkaBaselineMaxMs)

            redisBaselineMinMs = redisBaselineMinMs.coerceAtLeast(0)
            redisBaselineMaxMs = redisBaselineMaxMs.coerceAtLeast(redisBaselineMinMs)
            redisBaselineP99Ms = redisBaselineP99Ms.coerceAtLeast(redisBaselineMaxMs)
        }
    }

    companion object {
        fun getInstance(): PluginSettings =
                ApplicationManager.getApplication().getService(PluginSettings::class.java)
    }
}
