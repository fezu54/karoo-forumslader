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
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID
import org.happycode.karoo.forumslader.model.ForumsladerData
import org.happycode.karoo.forumslader.model.ForumsladerParser

class Forumslader(
    private val context: Context,
    val address: String,
    displayName: String? = null
) {
    private val parser = ForumsladerParser()
    private var bluetoothGatt: BluetoothGatt? = null

    val device: Device = Device(
        extension = "karoo-forumslader",
        uid = "fl-$address",
        dataTypes = listOf(
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_battery_pct"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_speed"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_voltage")
        ),
        displayName = displayName ?: "Forumslader"
    )

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connect(emitter: Emitter<DeviceEvent>) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothDevice = bluetoothManager.adapter.getRemoteDevice(address)

        val gattCallback = object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when {
                    status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED -> {
                        Log.i("FL_BLE", "Connected to Forumslader, discovering services...")
                        emitter.onNext(OnConnectionStatus(status = ConnectionStatus.CONNECTED))
                        gatt.discoverServices()
                    }
                    newState == BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.w("FL_BLE", "Disconnected from Forumslader")
                        emitter.onNext(OnConnectionStatus(status = ConnectionStatus.SEARCHING))
                        bluetoothGatt = null
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return

                gatt.getService(SERVICE_UUID)
                    ?.getCharacteristic(CHARACTERISTIC_UART_TX_RX)
                    ?.also { gatt.setCharacteristicNotification(it, true) }
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

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                if (characteristic.uuid == CHARACTERISTIC_UART_TX_RX) {
                    parser.processIncomingBytes(value)?.let { data ->
                        emitData(emitter, data)
                    }
                }
            }

            @Suppress("DEPRECATION")
            @Deprecated("Deprecated in Android 13")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                onCharacteristicChanged(gatt = gatt, characteristic = characteristic, value = characteristic.value)
            }
        }

        bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        emitter.setCancellable {
            Log.i("FL_BLE", "Disconnecting from Forumslader...")
            bluetoothGatt?.run {
                disconnect()
                close()
            }
            bluetoothGatt = null
        }
    }

    private fun emitData(emitter: Emitter<DeviceEvent>, data: ForumsladerData) {
        val batteryDataPoint = OnDataPoint(
            dataPoint = DataPoint(
                dataTypeId = DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_battery_pct"),
                values = mapOf(DataType.Field.SINGLE to data.batteryLevelPct.toDouble()),
                sourceId = device.uid
            )
        )
        val speedDataPoint = OnDataPoint(
            dataPoint = DataPoint(
                dataTypeId = DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_speed"),
                values = mapOf(DataType.Field.SINGLE to data.speedKmh.toDouble()),
                sourceId = device.uid
            )
        )
        val voltageDataPoint = OnDataPoint(
            dataPoint = DataPoint(
                dataTypeId = DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_voltage"),
                values = mapOf(DataType.Field.SINGLE to data.batteryVoltage.toDouble()),
                sourceId = device.uid
            )
        )

        emitter.onNext(batteryDataPoint)
        emitter.onNext(speedDataPoint)
        emitter.onNext(voltageDataPoint)
    }
}
