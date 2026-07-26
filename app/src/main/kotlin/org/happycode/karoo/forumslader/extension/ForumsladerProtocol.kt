package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.os.Handler
import androidx.annotation.RequiresPermission
import org.happycode.karoo.forumslader.model.ForumsladerParser

class ForumsladerProtocol(
    private val bleManager: ForumsladerBleManager,
    private val parser: ForumsladerParser,
    private val mainHandler: Handler
) {
    private var parameterRequestRunnable: Runnable? = null
    private var isClosed = false

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startParameterRequestLoop() {
        stopParameterRequestLoop()
        isClosed = false
        parameterRequestRunnable = object : Runnable {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun run() {
                if (isClosed) return
                if (parser.isConfigLoaded) return

                val cmdBytes = "\$FLT,5*47\r\n".toByteArray(Charsets.US_ASCII)
                bleManager.writeCommand(cmdBytes)

                mainHandler.postDelayed(this, 5000)
            }
        }.also { mainHandler.post(it) }
    }

    fun stopParameterRequestLoop() {
        isClosed = true
        parameterRequestRunnable?.let {
            mainHandler.removeCallbacks(it)
            parameterRequestRunnable = null
        }
    }
}
