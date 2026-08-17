package org.happycode.karoo.forumslader.application

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global store for application-level state that needs to be accessed across layers,
 * particularly for UI observation of technical adapter state.
 */
object ForumsladerStateStore {
    private val _isConfigLoadedFlow = MutableStateFlow(false)
    val isConfigLoadedFlow = _isConfigLoadedFlow.asStateFlow()

    fun setConfigLoaded(loaded: Boolean) {
        _isConfigLoadedFlow.value = loaded
    }

    fun clear() {
        _isConfigLoadedFlow.value = false
    }
}
