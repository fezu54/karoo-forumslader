package org.happycode.karoo.forumslader.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface ForumsladerCommand {
    data object ResetDayDistance : ForumsladerCommand
    data object ResetTourDistance : ForumsladerCommand
    data class UpdateConfig(val wheelsize: Int, val poles: Int) : ForumsladerCommand
    data class SetConsumerState(val enabled: Boolean) : ForumsladerCommand
    data class EnterDfuMode(
        val onDisconnected: CompletableDeferred<Unit> = CompletableDeferred()
    ) : ForumsladerCommand
}

object CommandBus {
    private val _commands = MutableSharedFlow<ForumsladerCommand>(extraBufferCapacity = 10)
    val commands = _commands.asSharedFlow()

    fun sendCommand(command: ForumsladerCommand): Boolean {
        return _commands.tryEmit(command)
    }

    val subscriberCount: Int
        get() = _commands.subscriptionCount.value
}
