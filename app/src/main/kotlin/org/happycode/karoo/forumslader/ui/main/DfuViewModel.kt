package org.happycode.karoo.forumslader.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import org.happycode.karoo.forumslader.domain.DfuState
import org.happycode.karoo.forumslader.domain.DfuUpdateService
import java.net.URI

class DfuViewModel(private val updateService: DfuUpdateService) : ViewModel() {
    val state: StateFlow<DfuState> = updateService.state

    fun startUpdate(firmwareUri: Uri) {
        runCatching { URI(firmwareUri.toString()) }
            .onSuccess { updateService.startUpdate(it) }
    }

    fun cancelUpdate() = updateService.cancelUpdate()

    fun reset() = updateService.reset()
}
