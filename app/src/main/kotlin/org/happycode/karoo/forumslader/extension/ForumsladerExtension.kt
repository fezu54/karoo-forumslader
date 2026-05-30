package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import androidx.annotation.RequiresPermission
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ForumsladerExtension : KarooExtension(extension = "karoo-forumslader", version = "1.0") {
    private val devices = mutableMapOf<String, Forumslader>()

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun startScan(emitter: Emitter<Device>) {
        val job = Job()
        val scope = CoroutineScope(context = Dispatchers.Main + job)

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val scanner = bluetoothManager.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner ?: run {
            emitter.setCancellable { job.cancel() }
            return
        }

        val callback = object : ScanCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                result.device.name?.takeIf { name ->
                    name.contains(other = "Forumslader", ignoreCase = true) ||
                    name.contains(other = "FL_BLE", ignoreCase = true)
                }?.let { name ->
                    val forumslader = devices.getOrPut(key = result.device.address) {
                        Forumslader(context = this@ForumsladerExtension, address = result.device.address, displayName = name)
                    }
                    emitter.onNext(forumslader.device)
                }
            }
        }

        scope.launch {
            scanner.startScan(callback)
        }

        emitter.setCancellable {
            scanner.stopScan(callback)
            job.cancel()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        val address = uid.removePrefix(prefix = "fl-")
        devices.getOrPut(key = address) {
            Forumslader(context = this, address = address, displayName = null)
        }.connect(emitter = emitter)
    }
}
