package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresPermission
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.models.DataType
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId

class ForumsladerExtension : KarooExtension(extension = "karoo-forumslader", version = "1.0") {
    private val devices = mutableMapOf<String, Forumslader>()

    override val types: List<DataTypeImpl> by lazy {
        listOf(
            ForumsladerDataType(extension, DataFieldId.BATTERY_LEVEL, DataType.Type.BATTERY_PERCENT),
            ForumsladerDataType(extension, DataFieldId.CONSUMER_CURRENT),
            ForumsladerDataType(extension, DataFieldId.SPEED, DataType.Type.SPEED),
            ForumsladerDataType(extension, DataFieldId.TRIP_DISTANCE, DataType.Type.DISTANCE)
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun startScan(emitter: Emitter<Device>) {
        val job = Job()
        val scope = CoroutineScope(context = Dispatchers.Main + job)

        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasScanPermission) {
            emitter.setCancellable { job.cancel() }
            return
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: run {
            android.util.Log.w("FL_SCAN", "startScan() failed: bluetooth scanner not available or disabled")
            emitter.setCancellable { job.cancel() }
            return
        }

        val callback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                if (!hasConnectPermission) return

                val name = result.device.name ?: result.scanRecord?.deviceName
                val uuids = result.scanRecord?.serviceUuids

                val hasForumsladerName = name?.run {
                    contains(other = "Forumslader", ignoreCase = true) ||
                    contains(other = "FL_BLE", ignoreCase = true) ||
                    contains(other = "FLV", ignoreCase = true)
                } ?: false

                val hasForumsladerService = uuids?.run {
                    contains(ParcelUuid(SERVICE_UUID_V5)) || contains(ParcelUuid(SERVICE_UUID_V6))
                } ?: false

                if (hasForumsladerName || hasForumsladerService) {
                    val displayName = name ?: "Forumslader"
                    val forumslader = devices.getOrPut(key = result.device.address) {
                        Forumslader(context = this@ForumsladerExtension, address = result.device.address, displayName = displayName)
                    }
                    emitter.onNext(forumslader.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.e("FL_SCAN", "onScanFailed() called with error code: $errorCode")
            }
        }

        val filters = listOf(
            ScanFilter.Builder().build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scope.launch {
            scanner.startScan(filters, settings, callback)
        }

        emitter.setCancellable {
            val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else {
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            }
            if (hasPermission) {
                scanner.stopScan(callback)
            }
            job.cancel()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasConnectPermission) return

        val address = uid.removePrefix(prefix = "fl-")
        devices.getOrPut(key = address) {
            Forumslader(context = this, address = address, displayName = null)
        }.connect(emitter = emitter)
    }
}
