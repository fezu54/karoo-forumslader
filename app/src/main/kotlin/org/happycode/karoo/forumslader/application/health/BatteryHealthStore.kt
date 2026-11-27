package org.happycode.karoo.forumslader.application.health

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSummary

object BatteryHealthStore {
    private val _summaryFlow = MutableStateFlow<BatteryHealthSummary?>(null)
    val summaryFlow: StateFlow<BatteryHealthSummary?> = _summaryFlow.asStateFlow()

    fun updateSummary(summary: BatteryHealthSummary?) {
        _summaryFlow.value = summary
    }

    fun clear() {
        _summaryFlow.value = null
    }
}
