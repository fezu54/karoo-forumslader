package org.happycode.karoo.forumslader.application

import kotlinx.coroutines.flow.StateFlow

interface LogServerGateway {
    val isRunning: StateFlow<Boolean>
    val serverUrl: StateFlow<String?>
    fun start(port: Int = 8080): Result<String>
    fun stop()
}
