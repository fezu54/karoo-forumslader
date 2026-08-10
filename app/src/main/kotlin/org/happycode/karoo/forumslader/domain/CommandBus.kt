package org.happycode.karoo.forumslader.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object CommandBus {
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 10)
    val commands = _commands.asSharedFlow()

    fun sendCommand(command: String) {
        _commands.tryEmit(command)
    }
}
