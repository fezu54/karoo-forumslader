package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import com.juul.kable.Advertisement
import com.juul.kable.Characteristic
import com.juul.kable.DiscoveredService
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import io.hammerhead.karooext.models.ConnectionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.launch
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.happycode.karoo.forumslader.model.ForumsladerVersion
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

class ForumsladerBleManager(
    private val address: String,
    private val scope: CoroutineScope,
    scanner: Scanner<Advertisement>? = null,
    private val peripheralFactory: (Advertisement) -> Peripheral = { Peripheral(it) }
) {
    private val internalScanner = scanner ?: Scanner {
        filters { match { address = this@ForumsladerBleManager.address } }
    }

    private val _connectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    val connectionState: StateFlow<ConnectionStatus> = _connectionState.asStateFlow()

    private val _incomingData = MutableSharedFlow<ByteArray>()
    val incomingData: SharedFlow<ByteArray> = _incomingData.asSharedFlow()

    private val _versionDetected = MutableSharedFlow<ForumsladerVersion>()
    val versionDetected: SharedFlow<ForumsladerVersion> = _versionDetected.asSharedFlow()

    private val _notificationsEnabled = MutableSharedFlow<Unit>()
    val notificationsEnabled: SharedFlow<Unit> = _notificationsEnabled.asSharedFlow()

    private var connectionJob: Job? = null
    private var peripheral: Peripheral? = null
    private var txCharacteristic: Characteristic? = null

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun start() {
        if (connectionJob?.isActive == true) return
        connectionJob = scope.launch {
            connectionFlow()
                .retry { e ->
                    if (e is CancellationException) return@retry false
                    if (e !is ConnectionRestartException) {
                        Log.e(TAG, "Connection loop error for $address", e)
                    }
                    delay(RECONNECT_DELAY)
                    true
                }
                .collect()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun connectionFlow() = flow<Unit> {
        val advertisement = scanForDevice()
        coroutineScope {
            val currentPeripheral = peripheralFactory(advertisement).also { peripheral = it }
            try {
                handleSession(currentPeripheral)
            } finally {
                cleanupSession()
            }
        }
        throw ConnectionRestartException()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private suspend fun scanForDevice(): Advertisement {
        _connectionState.value = ConnectionStatus.SEARCHING
        return internalScanner.advertisements.first()
    }

    private class ConnectionRestartException : Exception()

    private suspend fun CoroutineScope.handleSession(peripheral: Peripheral) {
        // Tie state mapping to this session's scope
        peripheral.state
            .onEach { _connectionState.value = it.toConnectionStatus() }
            .launchIn(this)

        peripheral.connect()

        val services = peripheral.services.first { it != null } ?: return
        
        discoverForumslader(services)?.let { (version, service) ->
            _versionDetected.emit(version)
            setupUart(peripheral, service)
        }

        // Keep session alive until disconnected
        peripheral.state.first { it is State.Disconnected }
    }

    private fun discoverForumslader(services: List<DiscoveredService>): Pair<ForumsladerVersion, DiscoveredService>? =
        listOf(
            ForumsladerVersion.V6 to SERVICE_UUID_V6,
            ForumsladerVersion.V5 to SERVICE_UUID_V5
        ).firstNotNullOfOrNull { (version, uuid) ->
            services.findService(uuid)?.let { version to it }
        }

    private suspend fun CoroutineScope.setupUart(peripheral: Peripheral, service: DiscoveredService) {
        val rx = service.findCharacteristic(CHARACTERISTIC_UART_TX_RX)
            ?: service.findCharacteristic(CHARACTERISTIC_UART_RX_V6)

        txCharacteristic = service.findCharacteristic(CHARACTERISTIC_UART_TX_V6)
            ?: service.findCharacteristic(CHARACTERISTIC_UART_TX_RX)

        rx?.let { char ->
            _notificationsEnabled.emit(Unit)
            peripheral.observe(char)
                .onEach { _incomingData.emit(it) }
                .catch { Log.e(TAG, "Notification error", it) }
                .launchIn(this)
        }
    }

    private fun cleanupSession() {
        txCharacteristic = null
        peripheral = null
        _connectionState.value = ConnectionStatus.DISCONNECTED
    }

    fun stop() {
        val p = peripheral
        connectionJob?.cancel()
        connectionJob = null
        cleanupSession()
        scope.launch {
            try {
                p?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Disconnect error", e)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCommand(cmdBytes: ByteArray) {
        val char = txCharacteristic ?: return
        val p = peripheral ?: return
        scope.launch {
            try {
                p.write(char, cmdBytes, WriteType.WithoutResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Error writing command", e)
            }
        }
    }

    // Helper Extensions
    private fun State.toConnectionStatus() = when (this) {
        is State.Connected -> ConnectionStatus.CONNECTED
        is State.Connecting -> ConnectionStatus.SEARCHING
        else -> ConnectionStatus.DISCONNECTED
    }

    private fun List<DiscoveredService>.findService(uuid: UUID) =
        find { it.serviceUuid.toString() == uuid.toString() }

    private fun DiscoveredService.findCharacteristic(uuid: UUID) =
        characteristics.find { it.characteristicUuid.toString() == uuid.toString() }

    companion object {
        private const val TAG = "FL_BLE"
        private val RECONNECT_DELAY = 5.seconds
    }
}
