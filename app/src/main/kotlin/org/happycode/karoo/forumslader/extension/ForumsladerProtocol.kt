package org.happycode.karoo.forumslader.extension

import android.Manifest
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.isActive
import org.happycode.karoo.forumslader.model.ForumsladerParser
import kotlin.time.Duration.Companion.seconds

class ForumsladerProtocol(
    private val bleManager: ForumsladerBleManager,
    private val parser: ForumsladerParser,
    private val scope: CoroutineScope
) {
    private var requestJob: Job? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startParameterRequestLoop() {
        stopParameterRequestLoop()

        requestJob = flow {
            while (currentCoroutineContext().isActive) {
                emit(Unit)
                delay(5.seconds)
            }
        }
            .takeWhile { !parser.isConfigLoadedFlow.value }
            .onEach {
                val cmdBytes = $$"$FLT,5*47\r\n".toByteArray(Charsets.US_ASCII)
                bleManager.writeCommand(cmdBytes)
            }
            .launchIn(scope)
    }

    fun stopParameterRequestLoop() {
        requestJob?.cancel()
        requestJob = null
    }
}

