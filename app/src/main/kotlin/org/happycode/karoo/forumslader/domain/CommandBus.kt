package org.happycode.karoo.forumslader.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface ForumsladerCommand {
    data object ResetDayDistance : ForumsladerCommand
    data object ResetTourDistance : ForumsladerCommand
    data class UpdateConfig(val wheelsize: Int, val poles: Int) : ForumsladerCommand
}

object CommandBus {
    private val _commands = MutableSharedFlow<ForumsladerCommand>(extraBufferCapacity = 10)
    val commands = _commands.asSharedFlow()

    fun sendCommand(command: ForumsladerCommand) {
        _commands.tryEmit(command)
    }
}
