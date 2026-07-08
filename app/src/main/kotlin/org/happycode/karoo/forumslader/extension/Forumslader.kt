package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.happycode.karoo.forumslader.model.ForumsladerParser

class Forumslader(
    private val context: Context,
    val address: String,
    displayName: String? = null
) {
    private val parser = ForumsladerParser()
    private var bluetoothGatt: BluetoothGatt? = null
    private var uartCharacteristicUuid: java.util.UUID? = null
    private var isClosed = false
    private var isConnecting = false
    private var currentEmitter: Emitter<DeviceEvent>? = null
    private val mainHandler by lazy { android.os.Handler(android.os.Looper.getMainLooper()) }
    private var reconnectRunnable: Runnable? = null
    private var connectionTimeoutRunnable: Runnable? = null
    private var parameterRequestRunnable: Runnable? = null
    private var cccdRetryCount = 0

    val device: Device = Device(
        extension = "karoo-forumslader",
        uid = "fl-$address",
        dataTypes = listOf(
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_battery_level"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_consumer_current"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_speed"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_trip_distance")
        ),
        displayName = displayName ?: "Forumslader"
    )

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mainHandler.post {
                Log.d("FL_BLE", "onConnectionStateChange: status=$status, newState=$newState")
                val emitter = currentEmitter ?: return@post

                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i("FL_BLE", "Connected to Forumslader, discovering services...")
                    isConnecting = false
                    emitter.onNext(OnConnectionStatus(status = ConnectionStatus.CONNECTED))
                    gatt.discoverServices()
                } else {
                    Log.w("FL_BLE", "Disconnected or connection failed: status=$status, newState=$newState")
                    cancelConnectionTimeout()
                    cleanupConnection()
                    emitter.onNext(OnConnectionStatus(status = ConnectionStatus.SEARCHING))

                    if (!isClosed) {
                        scheduleReconnect()
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            mainHandler.post {
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    Log.e("FL_BLE", "Service discovery failed with status $status, disconnecting...")
                    cancelConnectionTimeout()
                    cleanupConnection()
                    if (!isClosed) {
                        scheduleReconnect()
                    }
                    return@post
                }

                Log.i("FL_BLE", "Services discovered on device:")
                gatt.services.forEach { s ->
                    val charsInfo = s.characteristics.joinToString { c -> "${c.uuid.toString().substring(0, 8)}(props=${c.properties})" }
                    Log.i("FL_BLE", "Service: ${s.uuid.toString().substring(0, 8)} | Chars: $charsInfo")
                }

                enableNotifications(gatt)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            mainHandler.post {
                if (descriptor.uuid != CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR) return@post

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    handleCccdWriteSuccess(gatt)
                } else {
                    handleCccdWriteFailure(gatt, status)
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            mainHandler.post {
                if (characteristic.uuid == uartCharacteristicUuid) {
                    val emitter = currentEmitter ?: return@post
                    parser.processIncomingBytes(value)?.let { metrics ->
                        emitMetrics(emitter, metrics)
                    }
                }
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            onCharacteristicChanged(gatt = gatt, characteristic = characteristic, value = characteristic.value)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(emitter: Emitter<DeviceEvent>) {
        Log.i("FL_BLE", "connect() called for Forumslader at $address")
        mainHandler.post {
            isClosed = false
            currentEmitter = emitter

            emitter.setCancellable {
                mainHandler.post {
                    Log.i("FL_BLE", "Disconnecting from Forumslader (cancelled by Karoo)...")
                    isClosed = true
                    reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
                    cleanupConnection()
                    currentEmitter = null
                }
            }

            doConnect()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun doConnect() {
        if (isClosed || isConnecting || bluetoothGatt != null) return

        isConnecting = true
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothDevice = bluetoothManager.adapter.getRemoteDevice(address)

        Log.i("FL_BLE", "Initiating connection to GATT at $address...")
        
        scheduleConnectionTimeout()
        
        bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun scheduleReconnect() {
        if (isClosed) return

        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable {
            if (!isClosed && !isConnecting && bluetoothGatt == null) {
                Log.i("FL_BLE", "Retrying connection to Forumslader at $address...")
                doConnect()
            }
        }
        reconnectRunnable = runnable
        Log.i("FL_BLE", "Scheduling reconnection attempt in 5 seconds...")
        mainHandler.postDelayed(runnable, 5000)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableNotifications(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID_V5) ?: gatt.getService(SERVICE_UUID_V6)
        val characteristic = service?.getCharacteristic(CHARACTERISTIC_UART_TX_RX)
            ?: service?.getCharacteristic(CHARACTERISTIC_UART_RX_V6)
            ?: service?.characteristics?.firstOrNull { char ->
                (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            }

        characteristic?.also { char ->
            uartCharacteristicUuid = char.uuid
            Log.i("FL_BLE", "Subscribing to UART characteristic: ${char.uuid}")
            gatt.setCharacteristicNotification(char, true)
        }
            ?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR)
            ?.let { descriptor ->
                val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                Log.i("FL_BLE", "Writing CCCD descriptor...")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, value)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            } ?: Log.e("FL_BLE", "Forumslader UART Characteristic or Descriptor not found.")
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleCccdWriteSuccess(gatt: BluetoothGatt) {
        cancelConnectionTimeout()
        cccdRetryCount = 0
        Log.i("FL_BLE", "Descriptor write successful, starting parameter request loop...")
        startParameterRequestLoop(gatt)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleCccdWriteFailure(gatt: BluetoothGatt, status: Int) {
        Log.w("FL_BLE", "Descriptor write failed with status $status (retry $cccdRetryCount/3)")
        if (cccdRetryCount >= 3) {
            Log.e("FL_BLE", "CCCD write max retries reached, reconnecting...")
            cancelConnectionTimeout()
            cleanupConnection()
            if (!isClosed) {
                scheduleReconnect()
            }
            return
        }

        cccdRetryCount++
        mainHandler.postDelayed({
            if (!isClosed && bluetoothGatt == gatt) {
                enableNotifications(gatt)
            }
        }, 1000)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun cleanupConnection() {
        cancelConnectionTimeout()
        stopParameterRequestLoop()
        isConnecting = false
        cccdRetryCount = 0
        bluetoothGatt?.run {
            disconnect()
            close()
        }
        bluetoothGatt = null
        parser.resetConfigLoaded()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun scheduleConnectionTimeout() {
        cancelConnectionTimeout()
        val runnable = Runnable {
            if (isConnecting || bluetoothGatt != null) {
                Log.w("FL_BLE", "Connection attempt timed out after 15 seconds. Cleaning up and retrying...")
                cleanupConnection()
                currentEmitter?.onNext(OnConnectionStatus(status = ConnectionStatus.SEARCHING))
                if (!isClosed) {
                    scheduleReconnect()
                }
            }
        }
        connectionTimeoutRunnable = runnable
        mainHandler.postDelayed(runnable, 15000)
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            connectionTimeoutRunnable = null
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startParameterRequestLoop(gatt: BluetoothGatt) {
        stopParameterRequestLoop()
        val runnable = object : Runnable {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun run() {
                if (isClosed || bluetoothGatt != gatt) return
                if (parser.isConfigLoaded) {
                    Log.i("FL_BLE", "Forumslader config is loaded, stopping parameter request loop.")
                    return
                }

                Log.i("FL_BLE", "Requesting parameters (FLP) from Forumslader...")
                sendParameterRequest(gatt)

                mainHandler.postDelayed(this, 5000)
            }
        }
        parameterRequestRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopParameterRequestLoop() {
        parameterRequestRunnable?.let {
            mainHandler.removeCallbacks(it)
            parameterRequestRunnable = null
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendParameterRequest(gatt: BluetoothGatt) {
        val service = gatt.getService(SERVICE_UUID_V5) ?: gatt.getService(SERVICE_UUID_V6)
        val isV6 = service?.uuid == SERVICE_UUID_V6
        val rxChar = service?.let { s ->
            if (isV6) {
                s.getCharacteristic(CHARACTERISTIC_UART_TX_V6)
            } else {
                s.getCharacteristic(CHARACTERISTIC_UART_TX_RX)
            }
        }
        rxChar?.let { char ->
            val cmdBytes = $$"$FLT,5*47\n".toByteArray(Charsets.US_ASCII)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, cmdBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                char.value = cmdBytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
            Log.d("FL_BLE", "Forumslader parameter request command bytes written.")
        } ?: Log.w("FL_BLE", "Command characteristic not found for parameter request.")
    }

    private fun emitMetrics(emitter: Emitter<DeviceEvent>, metrics: ForumsladerMetrics) {
        emitter.onNext(
            OnDataPoint(
                dataPoint = DataPoint(
                    dataTypeId = DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_battery_level"),
                    values = mapOf(DataType.Field.SINGLE to metrics.batteryLevelPct.toDouble()),
                    sourceId = device.uid
                )
            )
        )
        emitter.onNext(
            OnDataPoint(
                dataPoint = DataPoint(
                    dataTypeId = DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_consumer_current"),
                    values = mapOf(DataType.Field.SINGLE to metrics.consumerCurrent.toDouble()),
                    sourceId = device.uid
                )
            )
        )
        emitter.onNext(
            OnDataPoint(
                dataPoint = DataPoint(
                    dataTypeId = DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_speed"),
                    values = mapOf(DataType.Field.SINGLE to metrics.speedKmh.toDouble()),
                    sourceId = device.uid
                )
            )
        )
        emitter.onNext(
            OnDataPoint(
                dataPoint = DataPoint(
                    dataTypeId = DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_trip_distance"),
                    values = mapOf(DataType.Field.SINGLE to metrics.tripDistanceKm.toDouble()),
                    sourceId = device.uid
                )
            )
        )
    }
}
