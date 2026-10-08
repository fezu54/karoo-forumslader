package org.happycode.karoo.forumslader.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class CommandBusTest : ShouldSpec({

    should("emit commands to subscribers when commands are sent") {
        runTest {
            // given
            val commands = mutableListOf<ForumsladerCommand>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                CommandBus.commands.collect { commands.add(it) }
            }

            // when
            CommandBus.sendCommand(ForumsladerCommand.ResetDayDistance)
            CommandBus.sendCommand(ForumsladerCommand.UpdateConfig(2100, 20))

            // then
            commands shouldBe listOf(
                ForumsladerCommand.ResetDayDistance,
                ForumsladerCommand.UpdateConfig(2100, 20)
            )
        }
    }
})

