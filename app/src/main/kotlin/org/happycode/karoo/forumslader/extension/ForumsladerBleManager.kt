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
import kotlinx.coroutines.channels.Channel
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
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6_ALT
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
    private val writeChannel = Channel<ByteArray>(Channel.UNLIMITED)

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun start() {
        if (connectionJob?.isActive == true) return
        Log.d(TAG, "Starting ForumsladerBleManager for address $address")
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
        Log.d(TAG, "Waiting for advertisement from $address...")
        return internalScanner.advertisements.first().also {
            Log.i(TAG, "Matched advertisement for $address")
        }
    }

    private class ConnectionRestartException : Exception()

    private suspend fun CoroutineScope.handleSession(peripheral: Peripheral) {
        peripheral.state
            .onEach {
                val status = it.toConnectionStatus()
                _connectionState.value = status
                Log.d(TAG, "Peripheral state for $address: $it -> $status")
            }
            .launchIn(this)

        Log.i(TAG, "Connecting to peripheral $address...")
        peripheral.connect()
        Log.i(TAG, "Connected to peripheral $address, waiting for discovered services...")

        val services = peripheral.services.first { it != null } ?: run {
            Log.w(TAG, "Discovered services were null for $address")
            return
        }
        val servicesUuids = services.map { runCatching { it.serviceUuid.toString() }.getOrDefault("unknown") }
        Log.d(TAG, "Discovered ${services.size} services for $address: $servicesUuids")

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
            ForumsladerVersion.V6 to SERVICE_UUID_V6_ALT,
            ForumsladerVersion.V5 to SERVICE_UUID_V5
        ).firstNotNullOfOrNull { (version, uuid) ->
            services.findService(uuid)?.let { version to it }
        }.also { result ->
            if (result != null) {
                val matchedUuid = runCatching { result.second.serviceUuid.toString() }.getOrDefault("unknown")
                Log.i(TAG, "Matched Forumslader version ${result.first} with service $matchedUuid for $address")
            } else {
                val servicesUuids = services.map { runCatching { it.serviceUuid.toString() }.getOrDefault("unknown") }
                Log.w(TAG, "No Forumslader service matched for $address. Services found: $servicesUuids")
            }
        }

    private suspend fun CoroutineScope.setupUart(peripheral: Peripheral, service: DiscoveredService) {
        val serviceUuidStr = runCatching { service.serviceUuid.toString() }.getOrDefault("unknown")
        val charUuids = service.characteristics.map { runCatching { it.characteristicUuid.toString() }.getOrDefault("unknown") }
        Log.d(TAG, "Setting up UART for $address with service $serviceUuidStr")
        Log.d(TAG, "Characteristics in service $serviceUuidStr: $charUuids")

        val rx = service.findCharacteristic(CHARACTERISTIC_UART_TX_RX)
            ?: service.findCharacteristic(CHARACTERISTIC_UART_RX_V6)
            ?: service.findCharacteristic(CHARACTERISTIC_UART_TX_V6)

        val tx = service.findCharacteristic(CHARACTERISTIC_UART_TX_V6)
            ?: service.findCharacteristic(CHARACTERISTIC_UART_TX_RX)
        txCharacteristic = tx

        val rxUuidStr = rx?.let { runCatching { it.characteristicUuid.toString() }.getOrDefault("unknown") }
        val txUuidStr = tx?.let { runCatching { it.characteristicUuid.toString() }.getOrDefault("unknown") }
        Log.i(TAG, "Configured UART for $address: rx=$rxUuidStr, tx=$txUuidStr")

        rx?.let { char ->
            _notificationsEnabled.emit(Unit)
            Log.i(TAG, "Starting notifications on characteristic $rxUuidStr for $address")
            peripheral.observe(char)
                .onEach { _incomingData.emit(it) }
                .catch { Log.e(TAG, "Notification error on characteristic $rxUuidStr for $address", it) }
                .launchIn(this)
        } ?: Log.e(TAG, "RX characteristic not found for $address")

        tx?.let { char ->
            launch {
                for (cmdBytes in writeChannel) {
                    if (peripheral.state.value is State.Connected) {
                        try {
                            peripheral.write(char, cmdBytes, WriteType.WithoutResponse)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error writing command to $address", e)
                        }
                    }
                }
            }
        } ?: Log.e(TAG, "TX characteristic not found for $address")
    }

    private fun cleanupSession() {
        Log.d(TAG, "Cleaning up session for $address")
        txCharacteristic = null
        peripheral = null
        _connectionState.value = ConnectionStatus.DISCONNECTED
        drainWriteChannel()
    }

    private fun drainWriteChannel() {
        while (writeChannel.tryReceive().isSuccess) { /* drain */ }
    }

    fun stop() {
        Log.d(TAG, "Stopping ForumsladerBleManager for $address")
        val p = peripheral
        connectionJob?.cancel()
        connectionJob = null
        cleanupSession()
        scope.launch {
            try {
                p?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Disconnect error for $address", e)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCommand(cmdBytes: ByteArray) {
        writeChannel.trySend(cmdBytes)
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
