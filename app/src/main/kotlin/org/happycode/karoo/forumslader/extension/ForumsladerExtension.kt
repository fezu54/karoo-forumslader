package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.getSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.FitEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import org.happycode.karoo.forumslader.BuildConfig
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.CommandBus
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.MANUFACTURER_ID_FORUMSLADER
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6_ALT
import org.happycode.karoo.forumslader.model.ForumsladerConfig

class ForumsladerExtension(
    private val adapterFactory: (Context, String, String?) -> ForumsladerKarooAdapter = { ctx, addr, name ->
        ForumsladerKarooAdapter(context = ctx, address = addr, displayName = name)
    },
    private val defaultScope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val scanSettingsFactory: () -> ScanSettings = {
        ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    },
    private val bluetoothStateFlowFactory: (Context) -> Flow<Boolean> = { it.bluetoothStateFlow() }
) : KarooExtension(extension = "karoo-forumslader", version = BuildConfig.VERSION_NAME) {
    private var fitEmitter: Emitter<FitEffect>? = null
    private val devices: ConcurrentMap<String, ForumsladerKarooAdapter> = ConcurrentHashMap()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    override fun onCreate() {
        super.onCreate()
        serviceScope.launch {
            CommandBus.commands.collect { command ->
                val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                if (hasConnectPermission) {
                    devices.values.forEach { it.sendCommand(command) }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    override val types: List<DataTypeImpl> by lazy {
        listOf(
            ForumsladerDataType(extension, DataFieldId.BATTERY_LEVEL, DataType.Type.BATTERY_PERCENT),
            ForumsladerDataType(extension, DataFieldId.BATTERY_VOLTAGE),
            ForumsladerDataType(extension, DataFieldId.BATTERY_CURRENT),
            ForumsladerDataType(extension, DataFieldId.CONSUMER_CURRENT),
            ForumsladerDataType(extension, DataFieldId.SPEED, DataType.Type.SPEED),
            ForumsladerDataType(extension, DataFieldId.TRIP_DISTANCE, DataType.Type.DISTANCE),
            ForumsladerDataType(extension, DataFieldId.FREQUENCY),
            ForumsladerDataType(extension, DataFieldId.TEMPERATURE),
            ForumsladerDataType(extension, DataFieldId.GENERATOR_GEAR),
            ForumsladerDataType(extension, DataFieldId.CHARGE_STATE),
            ForumsladerDataType(extension, DataFieldId.TRIP_ENERGY, DataType.Type.ENERGY_OUTPUT),
            ForumsladerDataType(extension, DataFieldId.TOUR_ENERGY, DataType.Type.ENERGY_OUTPUT),
            ForumsladerDataType(extension, DataFieldId.DYNAMO_POWER, DataType.Type.POWER),
            ForumsladerDataType(extension, DataFieldId.ODOMETER, DataType.Type.DISTANCE),
            ForumsladerDataType(extension, DataFieldId.DAY_DISTANCE, DataType.Type.DISTANCE),
            ForumsladerDataType(extension, DataFieldId.TOUR_DISTANCE, DataType.Type.DISTANCE),
            ForumsladerDataType(extension, DataFieldId.BATTERY_RANGE, DataType.Type.DISTANCE),
        )
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    override fun startScan(emitter: Emitter<Device>) {
        Log.d(TAG, "startScan() called")
        val job = Job(defaultScope.coroutineContext[Job])
        val scope = CoroutineScope(defaultScope.coroutineContext + job)

        val hasScanPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }

        if (!hasScanPermission) {
            Log.e(TAG, "startScan() failed: Missing BLUETOOTH_SCAN or ACCESS_FINE_LOCATION permission")
            emitter.setCancellable { job.cancel() }
            return
        }

        val locationManager = getSystemService<LocationManager>()
        val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager?.isLocationEnabled == true
        } else {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationEnabled) {
            Log.w(TAG, "startScan(): System location services (GPS) are DISABLED! BLE discovery will not return results on Android 8.")
        }

        val config = ForumsladerConfig(this)
        val lockedMac = config.lockedMacAddress?.takeIf { it.isNotBlank() }
        if (lockedMac != null) {
            Log.i(TAG, "startScan(): Found locked MAC address: $lockedMac, bypassing BLE scan")
            val forumslader = getOrCreateAdapter(lockedMac, "Forumslader")
            emitter.onNext(forumslader.device)
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
                if (!hasConnectPermission) {
                    Log.w(TAG, "onScanResult(): Ignored result due to missing BLUETOOTH_CONNECT permission")
                    return
                }

                val deviceAddress = result.device.address
                val name = result.device.name ?: result.scanRecord?.deviceName
                val uuids = result.scanRecord?.serviceUuids
                val manufacturerData = result.scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID_FORUMSLADER)
                val rssi = result.rssi

                Log.d(TAG, "onScanResult(): address=$deviceAddress, name=$name, rssi=$rssi, uuids=$uuids, hasMfgData=${manufacturerData != null}")

                val hasForumsladerName = name?.run {
                    contains(other = "Forumslader", ignoreCase = true) ||
                    startsWith(prefix = "FL", ignoreCase = true) ||
                    contains(other = "Ahead", ignoreCase = true)
                } ?: false

                val hasForumsladerService = uuids?.any { parcelUuid ->
                    parcelUuid.uuid == SERVICE_UUID_V5 ||
                    parcelUuid.uuid == SERVICE_UUID_V6 ||
                    parcelUuid.uuid == SERVICE_UUID_V6_ALT
                } ?: false

                val hasForumsladerMfg = manufacturerData != null

                if (hasForumsladerName || hasForumsladerService || hasForumsladerMfg) {
                    Log.i(TAG, "Matched Forumslader: address=$deviceAddress, name=$name, byName=$hasForumsladerName, byService=$hasForumsladerService, byMfg=$hasForumsladerMfg")
                    val displayName = name ?: "Forumslader"
                    val forumslader = getOrCreateAdapter(deviceAddress, displayName)
                    emitter.onNext(forumslader.device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "onScanFailed() called with error code: $errorCode")
            }
        }

        val filters = emptyList<ScanFilter>()
        val settings = scanSettingsFactory()
        val bluetoothManager = getSystemService<BluetoothManager>()
        var activeScanner: BluetoothLeScanner? = null

        fun stopActiveScan() {
            activeScanner?.let { scanner ->
                runCatching { scanner.stopScan(callback) }
                activeScanner = null
            }
        }

        fun startLeScan(scanner: BluetoothLeScanner) {
            Log.d(TAG, "startScan(): Bluetooth adapter is ON, starting LE scan (mode=LOW_LATENCY)")
            runCatching { scanner.startScan(filters, settings, callback) }
                .onSuccess { activeScanner = scanner }
                .onFailure { e -> Log.e(TAG, "startScan(): Failed to start LE scan", e) }
        }

        scope.launch {
            bluetoothStateFlowFactory(this@ForumsladerExtension)
                .distinctUntilChanged()
                .collect { isEnabled ->
                    val scanner = bluetoothManager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
                    when {
                        !isEnabled -> {
                            Log.w(TAG, "startScan(): Bluetooth adapter is disabled / turning off, pausing scan")
                            stopActiveScan()
                        }
                        scanner == null -> {
                            Log.w(TAG, "startScan(): Bluetooth enabled reported, but scanner is not yet available")
                        }
                        activeScanner == null -> {
                            startLeScan(scanner)
                        }
                    }
                }
        }

        emitter.setCancellable {
            Log.d(TAG, "startScan(): Scan cancelled / stopped")
            job.cancel()
            val canStop = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            if (canStop) {
                stopActiveScan()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        Log.d(TAG, "connectDevice() called for uid=$uid")
        val hasConnectPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasConnectPermission) {
            Log.w(TAG, "connectDevice() failed for uid=$uid: Missing BLUETOOTH_CONNECT permission")
            return
        }

        val address = uid.removePrefix(prefix = "fl-")
        Log.i(TAG, "connectDevice(): connecting adapter for address=$address")
        getOrCreateAdapter(address).connect(emitter = emitter)
    }

    private fun getOrCreateAdapter(address: String, displayName: String? = null): ForumsladerKarooAdapter {
        val normalizedAddress = address.uppercase()
        return devices.getOrPut(key = normalizedAddress) {
            adapterFactory(this, normalizedAddress, displayName)
        }.apply {
            setFitEmitter(fitEmitter)
        }
    }

    override fun startFit(emitter: Emitter<FitEffect>) {
        Log.i(TAG, "startFit(): registering fitEmitter to ${devices.size} device(s)")
        fitEmitter = emitter
        devices.values.forEach { it.setFitEmitter(emitter) }
        emitter.setCancellable {
            Log.i(TAG, "startFit(): fitEmitter cancelled")
            if (fitEmitter == emitter) {
                fitEmitter = null
                devices.values.forEach { it.setFitEmitter(null) }
            }
        }
    }

    companion object {
        private const val TAG = "FL_SCAN"
    }
}
