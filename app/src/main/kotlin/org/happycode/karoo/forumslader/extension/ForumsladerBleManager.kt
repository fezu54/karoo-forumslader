package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.happycode.karoo.forumslader.model.ForumsladerConfig
import org.happycode.karoo.forumslader.model.ForumsladerVersion
import java.util.UUID

interface ForumsladerBleListener {
    fun onConnectionStateChanged(connected: Boolean, searching: Boolean)
    fun onVersionDetected(version: ForumsladerVersion)
    fun onNotificationsEnabled()
    fun onDataReceived(data: ByteArray)
}

class ForumsladerBleManager(
    private val context: Context,
    private val address: String,
    private val config: ForumsladerConfig,
    private val listener: ForumsladerBleListener
) {
    private var bluetoothGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private var uartCharacteristicUuid: UUID? = null
    private var isClosed = false
    private var isConnecting = false
    private var shouldFallbackToScan = false
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var reconnectRunnable: Runnable? = null
    private var connectionTimeoutRunnable: Runnable? = null
    private var cccdRetryCount = 0

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            mainHandler.post {
                Log.d("FL_BLE", "onConnectionStateChange: status=$status, newState=$newState")
                if (isClosed) return@post
                if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                    isConnecting = false
                    shouldFallbackToScan = false
                    listener.onConnectionStateChanged(connected = true, searching = false)
                    gatt.discoverServices()
                } else {
                    cancelConnectionTimeout()
                    cleanupConnection()
                    listener.onConnectionStateChanged(connected = false, searching = true)
                    scheduleReconnect()
                }
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            mainHandler.post {
                if (isClosed) return@post
                if (status != BluetoothGatt.GATT_SUCCESS) {
                    cancelConnectionTimeout()
                    cleanupConnection()
                    scheduleReconnect()
                    return@post
                }
                enableNotifications(gatt)
            }
        }

        @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            mainHandler.post {
                if (isClosed) return@post
                if (descriptor.uuid != CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    cancelConnectionTimeout()
                    cccdRetryCount = 0
                    listener.onNotificationsEnabled()
                } else {
                    handleCccdWriteFailure(gatt)
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            mainHandler.post {
                if (isClosed) return@post
                if (characteristic.uuid == uartCharacteristicUuid) {
                    listener.onDataReceived(value)
                }
            }
        }

        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            onCharacteristicChanged(gatt, characteristic, characteristic.value)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun start() {
        isClosed = false
        doConnect()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun stop() {
        isClosed = true
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        cleanupConnection()
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun doConnect() {
        if (isClosed || isConnecting || bluetoothGatt != null || scanCallback != null) return

        isConnecting = true
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothDevice = bluetoothManager.adapter.getRemoteDevice(address)

        if (config.lockedMacAddress == address && shouldFallbackToScan) {
            startScanForDevice()
        } else {
            scheduleConnectionTimeout()
            @Suppress("DEPRECATION")
            bluetoothGatt = bluetoothDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun scheduleReconnect() {
        if (isClosed) return
        reconnectRunnable?.let { mainHandler.removeCallbacks(it) }
        reconnectRunnable = Runnable {
            if (!isClosed && !isConnecting && bluetoothGatt == null) {
                doConnect()
            }
        }.also { mainHandler.postDelayed(it, 5000) }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableNotifications(gatt: BluetoothGatt) {
        val serviceV6 = gatt.getService(SERVICE_UUID_V6)
        val serviceV5 = gatt.getService(SERVICE_UUID_V5)
        val service = serviceV6 ?: serviceV5

        val detectedVersion = if (serviceV6 != null) ForumsladerVersion.V6 else if (serviceV5 != null) ForumsladerVersion.V5 else null
        detectedVersion?.let { listener.onVersionDetected(it) }

        val characteristic = service?.getCharacteristic(CHARACTERISTIC_UART_TX_RX)
            ?: service?.getCharacteristic(CHARACTERISTIC_UART_RX_V6)
            ?: service?.characteristics?.firstOrNull { char ->
                (char.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                (char.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            }

        characteristic?.also { char ->
            uartCharacteristicUuid = char.uuid
            gatt.setCharacteristicNotification(char, true)
        }?.getDescriptor(CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR)?.let { descriptor ->
            val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, value)
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = value
                @Suppress("DEPRECATION")
                gatt.writeDescriptor(descriptor)
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun handleCccdWriteFailure(gatt: BluetoothGatt) {
        if (cccdRetryCount >= 3) {
            cancelConnectionTimeout()
            cleanupConnection()
            scheduleReconnect()
            return
        }
        cccdRetryCount++
        mainHandler.postDelayed({
            if (!isClosed && bluetoothGatt == gatt) {
                enableNotifications(gatt)
            }
        }, 1000)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun cleanupConnection() {
        cancelConnectionTimeout()
        stopScan()
        isConnecting = false
        cccdRetryCount = 0
        bluetoothGatt?.run {
            disconnect()
            close()
        }
        bluetoothGatt = null
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun scheduleConnectionTimeout() {
        cancelConnectionTimeout()
        connectionTimeoutRunnable = Runnable {
            if (isConnecting || bluetoothGatt != null || scanCallback != null) {
                shouldFallbackToScan = true
                cleanupConnection()
                listener.onConnectionStateChanged(connected = false, searching = true)
                if (!isClosed) scheduleReconnect()
            }
        }.also { mainHandler.postDelayed(it, 15000) }
    }

    private fun cancelConnectionTimeout() {
        connectionTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            connectionTimeoutRunnable = null
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    private fun startScanForDevice() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) {
            shouldFallbackToScan = false
            doConnect()
            return
        }

        scanCallback = object : ScanCallback() {
            @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
            override fun onScanResult(callbackType: Int, result: android.bluetooth.le.ScanResult) {
                if (result.device.address == address) {
                    stopScan()
                    scheduleConnectionTimeout()
                    @Suppress("DEPRECATION")
                    bluetoothGatt = result.device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                }
            }
            @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
            override fun onScanFailed(errorCode: Int) {
                shouldFallbackToScan = false
                isConnecting = false
                scanCallback = null
                scheduleReconnect()
            }
        }
        val filters = listOf(ScanFilter.Builder().setDeviceAddress(address).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scheduleConnectionTimeout()
        scanner.startScan(filters, settings, scanCallback)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun stopScan() {
        scanCallback?.let {
            try {
                val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
                val scanner = bluetoothManager.adapter?.takeIf { adapter -> adapter.isEnabled }?.bluetoothLeScanner
                scanner?.stopScan(it)
            } catch (e: Exception) {
                Log.e("FL_BLE", "Error stopping fallback scan", e)
            }
            scanCallback = null
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun writeCommand(cmdBytes: ByteArray) {
        println("writeCommand CALLED with bytes size ${cmdBytes.size}")
        println("bluetoothGatt is $bluetoothGatt")
        val service = bluetoothGatt?.getService(SERVICE_UUID_V5) ?: bluetoothGatt?.getService(SERVICE_UUID_V6)
        println("service is $service")
        val isV6 = service?.uuid == SERVICE_UUID_V6
        val txChar = service?.let { s ->
            if (isV6) s.getCharacteristic(CHARACTERISTIC_UART_TX_V6) else s.getCharacteristic(CHARACTERISTIC_UART_TX_RX)
        }
        println("txChar is $txChar")
        txChar?.let { char ->
            println("SDK_INT is ${Build.VERSION.SDK_INT}, TIRAMISU is ${Build.VERSION_CODES.TIRAMISU}")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                println("Calling modern writeCharacteristic")
                bluetoothGatt?.writeCharacteristic(char, cmdBytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                println("Calling legacy writeCharacteristic")
                @Suppress("DEPRECATION")
                char.value = cmdBytes
                @Suppress("DEPRECATION")
                bluetoothGatt?.writeCharacteristic(char)
            }
        }
    }
}
