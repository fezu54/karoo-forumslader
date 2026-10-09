package org.happycode.karoo.forumslader.adapters.ble

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.happycode.karoo.forumslader.domain.DfuState
import org.happycode.karoo.forumslader.domain.DfuUpdateService
import java.net.URI

class NordicDfuAdapter : DfuUpdateService {

    private val _state = MutableStateFlow<DfuState>(DfuState.Idle)
    override val state: StateFlow<DfuState> = _state.asStateFlow()

    override fun startUpdate(fileUri: URI) {
        // TODO: Implement Nordic DfuServiceInitiator logic here
        // Blocked pending Jens' feedback on Failsafe Bootloader and DFU Entry Mode
        // e.g. DfuServiceInitiator(deviceAddress).setZip(fileUri)...
        _state.value = DfuState.Connecting
    }

    override fun cancelUpdate() {
        // TODO: Implement cancellation logic using Nordic DfuManager/Service
        _state.value = DfuState.Idle
    }
}
