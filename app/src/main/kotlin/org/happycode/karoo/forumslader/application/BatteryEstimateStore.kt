package org.happycode.karoo.forumslader.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.happycode.karoo.forumslader.domain.BatteryEstimate

object BatteryEstimateStore {
    private val _estimateFlow = MutableStateFlow<BatteryEstimate?>(null)
    val estimateFlow: StateFlow<BatteryEstimate?> = _estimateFlow.asStateFlow()

    fun updateEstimate(estimate: BatteryEstimate?) {
        _estimateFlow.value = estimate
    }

    fun clear() {
        _estimateFlow.value = null
    }
}
