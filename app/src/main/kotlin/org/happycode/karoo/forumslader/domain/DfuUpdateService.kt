package org.happycode.karoo.forumslader.domain

import kotlinx.coroutines.flow.StateFlow
import java.net.URI

sealed interface DfuState {
    data object Idle : DfuState
    data object Validating : DfuState
    data object Connecting : DfuState
    data class Uploading(val progressPercent: Int) : DfuState
    data object Verifying : DfuState
    data object Success : DfuState
    data class Error(val message: String) : DfuState
}

interface DfuUpdateService {
    val state: StateFlow<DfuState>
    
    /**
     * Starts the DFU process for the given firmware file URI.
     * The implementation will handle device discovery/connection to the DFU service.
     */
    fun startUpdate(fileUri: URI)
    
    /**
     * Cancels any ongoing update.
     */
    fun cancelUpdate()
}
