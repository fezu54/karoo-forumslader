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
            Log.d("FL_BLE", "onConnectionStateChange: status=$status, newState=$newState")
            val emitter = currentEmitter ?: return

            when {
                status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> {
                    Log.i("FL_BLE", "Connected to Forumslader, discovering services...")
                    isConnecting = false
                    emitter.onNext(OnConnectionStatus(status = ConnectionStatus.CONNECTED))
                    gatt.discoverServices()
                }
                else -> {
                    Log.w("FL_BLE", "Disconnected or connection failed: status=$status, newState=$newState")

                    gatt.close()
                    if (bluetoothGatt == gatt) {
                        bluetoothGatt = null
                    }
                    isConnecting = false

                    emitter.onNext(OnConnectionStatus(status = ConnectionStatus.SEARCHING))

                    if (!isClosed) {
                        scheduleReconnect()
                    }
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return

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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeDescriptor(descriptor, value)
                    } else {
                        @Suppress("DEPRECATION")
                        descriptor.value = value
                        @Suppress("DEPRECATION")
                        gatt.writeDescriptor(descriptor)
                    }
                    Log.i("FL_BLE", "Forumslader UART Stream subscribed.")
                } ?: Log.e("FL_BLE", "Forumslader UART Characteristic or Descriptor not found.")
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && descriptor.uuid == CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR) {
                Log.i("FL_BLE", "Descriptor write successful, requesting parameters...")
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
                    Log.i("FL_BLE", "Forumslader parameter request command sent.")
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (characteristic.uuid == uartCharacteristicUuid) {
                val emitter = currentEmitter ?: return
                parser.processIncomingBytes(value)?.let { metrics ->
                    emitMetrics(emitter, metrics)
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
        isClosed = false
        currentEmitter = emitter

        emitter.setCancellable {
            Log.i("FL_BLE", "Disconnecting from Forumslader (cancelled by Karoo)...")
            isClosed = true
            reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
            bluetoothGatt?.run {
                disconnect()
                close()
            }
            bluetoothGatt = null
            currentEmitter = null
        }

        doConnect()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun doConnect() {
        if (isClosed || isConnecting || bluetoothGatt != null) return

        isConnecting = true
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothDevice = bluetoothManager.adapter.getRemoteDevice(address)

        Log.i("FL_BLE", "Initiating connection to GATT at $address...")
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
