package org.happycode.karoo.forumslader.adapters.ble

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuProgressListener
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import org.happycode.karoo.forumslader.domain.CommandBus
import org.happycode.karoo.forumslader.domain.DfuState
import org.happycode.karoo.forumslader.domain.DfuUpdateService
import org.happycode.karoo.forumslader.domain.ForumsladerCommand
import java.net.URI
import kotlin.time.Duration.Companion.milliseconds

class NordicDfuAdapter(
    private val context: Context,
    private val deviceAddressProvider: () -> String?,
    private val serviceInitiatorFactory: (String) -> DfuServiceInitiator = { DfuServiceInitiator(it) },
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val handshakeTimeoutMs: Long = 5000L,
    private val notificationChannelCreator: (Context) -> Unit = {
        DfuServiceInitiator.createDfuNotificationChannel(it)
    }
) : DfuUpdateService {

    private val _state = MutableStateFlow<DfuState>(DfuState.Idle)
    override val state: StateFlow<DfuState> = _state.asStateFlow()

    @Volatile
    private var controller: DfuServiceController? = null
    private var startJob: Job? = null

    private val progressListener: DfuProgressListener = object : DfuProgressListenerAdapter() {
        override fun onDeviceConnecting(deviceAddress: String) {
            _state.value = DfuState.Connecting
        }

        override fun onDfuProcessStarting(deviceAddress: String) {
            _state.value = DfuState.Connecting
        }

        override fun onEnablingDfuMode(deviceAddress: String) {
            _state.value = DfuState.Connecting
        }

        override fun onFirmwareValidating(deviceAddress: String) {
            _state.value = DfuState.Validating
        }

        override fun onProgressChanged(
            deviceAddress: String,
            percent: Int,
            speed: Float,
            avgSpeed: Float,
            currentPart: Int,
            partsTotal: Int
        ) {
            _state.value = DfuState.Uploading(percent)
        }

        override fun onDeviceDisconnecting(deviceAddress: String) {
            _state.value = DfuState.Verifying
        }

        override fun onDfuCompleted(deviceAddress: String) {
            controller = null
            _state.value = DfuState.Success
            unregisterListener()
        }

        override fun onDfuAborted(deviceAddress: String) {
            controller = null
            _state.value = DfuState.Idle
            unregisterListener()
        }

        override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String) {
            controller = null
            _state.value = DfuState.Error(message)
            unregisterListener()
        }
    }

    override fun startUpdate(fileUri: URI) {
        if (_state.value !is DfuState.Idle && _state.value !is DfuState.Error) {
            return
        }

        val address = deviceAddressProvider()
        if (address.isNullOrBlank()) {
            _state.value = DfuState.Error("No device connected")
            return
        }

        val enterDfuCommand = ForumsladerCommand.EnterDfuMode()
        if (CommandBus.subscriberCount == 0 || !CommandBus.sendCommand(enterDfuCommand)) {
            _state.value = DfuState.Error("No device connected")
            return
        }

        _state.value = DfuState.Connecting

        startJob = scope.launch {
            val disconnected = runCatching {
                withTimeoutOrNull(handshakeTimeoutMs.milliseconds) {
                    enterDfuCommand.onDisconnected.await()
                    true
                } ?: false
            }.getOrDefault(false)

            if (!disconnected) {
                _state.value = DfuState.Error("Failed to enter DFU mode")
                return@launch
            }

            notificationChannelCreator(context)

            DfuServiceListenerHelper.registerProgressListener(context, progressListener)

            val androidUri = fileUri.toString().toUri()
            val isZip = isZipFile(androidUri, fileUri)

            val initiator = serviceInitiatorFactory(address)
                .setKeepBond(false)
                .setForceDfu(false)
                .setForceScanningForNewAddressInLegacyDfu(true)
                .setScanTimeout(DfuServiceInitiator.DEFAULT_SCAN_TIMEOUT)
                .setPacketsReceiptNotificationsEnabled(true)
                .setPacketsReceiptNotificationsValue(DfuServiceInitiator.DEFAULT_PRN_VALUE)
                .apply {
                    if (isZip) {
                        setZip(androidUri)
                    } else {
                        @Suppress("DEPRECATION")
                        setBinOrHex(DfuBaseService.TYPE_APPLICATION, androidUri)
                    }
                }

            try {
                controller = initiator.start(context, ForumsladerDfuService::class.java)
            } catch (e: Exception) {
                _state.value = DfuState.Error(e.message ?: "Failed to start DFU service")
                unregisterListener()
            }
        }
    }

    override fun cancelUpdate() {
        startJob?.cancel()
        startJob = null
        controller?.abort()
        controller = null
        _state.value = DfuState.Idle
        unregisterListener()
    }

    override fun reset() {
        _state.value = DfuState.Idle
    }

    private fun isZipFile(androidUri: Uri, fileUri: URI): Boolean {
        return fileUri.path?.endsWith(".zip", ignoreCase = true) == true ||
                fileUri.toString().endsWith(".zip", ignoreCase = true) || runCatching {
            if (androidUri.scheme == "content") {
                val mime = context.contentResolver?.getType(androidUri)
                if (mime == "application/zip" || mime == "application/x-zip-compressed") {
                    return@runCatching true
                }
                context.contentResolver?.query(androidUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            return@runCatching cursor.getString(nameIndex)?.endsWith(".zip", ignoreCase = true) == true
                        }
                    }
                }
            }
            false
        }.getOrDefault(false)
    }

    private fun unregisterListener() {
        DfuServiceListenerHelper.unregisterProgressListener(context, progressListener)
    }
}
