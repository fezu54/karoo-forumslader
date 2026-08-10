package org.happycode.karoo.forumslader.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommandBusTest {

    @Test
    fun `should emit command to subscribers`() = runTest {
        val commands = mutableListOf<String>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            CommandBus.commands.collect { commands.add(it) }
        }

        CommandBus.sendCommand("test_command")

        assertEquals(1, commands.size)
        assertEquals("test_command", commands[0])

        job.cancel()
    }
}
