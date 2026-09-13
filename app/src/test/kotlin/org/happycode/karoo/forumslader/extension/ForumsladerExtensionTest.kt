package org.happycode.karoo.forumslader.extension

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.ParcelUuid
import android.util.Log
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.FitEffect
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.MANUFACTURER_ID_FORUMSLADER
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6_ALT
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ForumsladerExtensionTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var extension: ForumsladerExtension
    private lateinit var mockPrefs: SharedPreferences

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        context = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        every { mockPrefs.getString(any(), any()) } returns null
        every { context.getSharedPreferences(any(), any()) } returns mockPrefs
        every { context.filesDir } returns tempDir

        extension = ForumsladerExtension(
            adapterFactory = { _, addr, name ->
                mockk(relaxed = true) {
                    every { device } returns Device(
                        extension = "karoo-forumslader",
                        uid = "fl-$addr",
                        dataTypes = emptyList(),
                        displayName = name ?: "Forumslader"
                    )
                }
            },
            defaultScope = CoroutineScope(UnconfinedTestDispatcher()),
            scanSettingsFactory = { mockk(relaxed = true) },
            bluetoothStateFlowFactory = { flowOf(true) }
        )

        val applicationContext = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns applicationContext
        every { applicationContext.filesDir } returns tempDir
        every { applicationContext.getSharedPreferences(any(), any()) } returns mockPrefs
    }

    private fun setupScanningEnvironment(
        spyExtension: ForumsladerExtension
    ): Pair<ScanCallback, Emitter<Device>> {
        setupMockExtension(spyExtension)
        val bluetoothManager = mockk<BluetoothManager>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val scanner = mockk<BluetoothLeScanner>(relaxed = true)
        val emitter = mockk<Emitter<Device>>(relaxed = true)
        val scanCallbackSlot = slot<ScanCallback>()

        every { spyExtension.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        every { spyExtension.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { spyExtension.getSystemService(LocationManager::class.java) } returns null
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.bluetoothLeScanner } returns scanner
        every { scanner.startScan(any<List<ScanFilter>>(), any(), capture(scanCallbackSlot)) } returns Unit

        spyExtension.startScan(emitter)
        return scanCallbackSlot.captured to emitter
    }

    private fun setupMockExtension(spyExtension: ForumsladerExtension) {
        val applicationContext = mockk<Context>(relaxed = true)
        every { spyExtension.applicationContext } returns applicationContext
        every { spyExtension.getSharedPreferences(any(), any()) } returns mockPrefs
        every { spyExtension.filesDir } returns tempDir
        every { applicationContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { applicationContext.filesDir } returns tempDir
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should manage fit emitter lifecycle and propagate to devices when start fit is called`() {
        // given
        val mockAdapter = mockk<ForumsladerKarooAdapter>(relaxed = true)
        val customExtension = ForumsladerExtension(
            adapterFactory = { _, _, _ -> mockAdapter },
            defaultScope = CoroutineScope(UnconfinedTestDispatcher()),
            scanSettingsFactory = { mockk(relaxed = true) },
            bluetoothStateFlowFactory = { flowOf(true) }
        )
        val deviceEmitter = mockk<Emitter<DeviceEvent>>(relaxed = true)
        customExtension.connectDevice("fl-00:11:22:33:44:55", deviceEmitter)

        val fitEmitter = mockk<Emitter<FitEffect>>(relaxed = true)
        val cancelSlot = slot<() -> Unit>()
        every { fitEmitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        customExtension.startFit(fitEmitter)

        // then
        verify { mockAdapter.setFitEmitter(fitEmitter) }
        verify { fitEmitter.setCancellable(any()) }

        // when cancelled
        cancelSlot.captured.invoke()

        // then
        verify { mockAdapter.setFitEmitter(null) }
    }

    @Test
    fun `should propagate active fit emitter when device connects after start fit`() {
        // given
        val mockAdapter = mockk<ForumsladerKarooAdapter>(relaxed = true)
        val customExtension = ForumsladerExtension(
            adapterFactory = { _, _, _ -> mockAdapter },
            defaultScope = CoroutineScope(UnconfinedTestDispatcher()),
            scanSettingsFactory = { mockk(relaxed = true) },
            bluetoothStateFlowFactory = { flowOf(true) }
        )
        val fitEmitter = mockk<Emitter<FitEffect>>(relaxed = true)
        customExtension.startFit(fitEmitter)

        // when
        val deviceEmitter = mockk<Emitter<DeviceEvent>>(relaxed = true)
        customExtension.connectDevice("fl-00:11:22:33:44:55", deviceEmitter)

        // then
        verify { mockAdapter.setFitEmitter(fitEmitter) }
    }

    @Test
    fun `should provide all supported data types`() {
        // when
        val types = extension.types

        // then
        assertEquals(17, types.size, "Should provide exactly 17 data types")
        val typeIds = types.map { it.typeId }
        assertTrue(DataFieldId.BATTERY_LEVEL in typeIds, "Should include battery level type")
        assertTrue(DataFieldId.BATTERY_RANGE in typeIds, "Should include battery range type")
        assertTrue(DataFieldId.SPEED in typeIds, "Should include speed type")
    }

    @Test
    fun `should handle missing permission when start scan is called`() {
        // given
        val spyExtension = spyk(extension)
        every { spyExtension.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
        val emitter = mockk<Emitter<Device>>(relaxed = true)

        // when
        spyExtension.startScan(emitter)

        // then
        verify { emitter.setCancellable(any()) }
    }

    @Test
    fun `should match and emit device when scan result has alternative V6 service UUID`() {
        // given
        val spyExtension = spyk(extension)
        val (scanCallback, emitter) = setupScanningEnvironment(spyExtension)

        val parcelUuid = mockk<ParcelUuid> {
            every { uuid } returns SERVICE_UUID_V6_ALT
        }
        val result = mockk<ScanResult>(relaxed = true) {
            every { device.address } returns "11:22:33:44:55:66"
            every { device.name } returns null
            every { scanRecord?.deviceName } returns null
            every { scanRecord?.serviceUuids } returns listOf(parcelUuid)
            every { scanRecord?.getManufacturerSpecificData(any()) } returns null
        }

        // when
        scanCallback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)

        // then
        verify { emitter.onNext(match { it.uid == "fl-11:22:33:44:55:66" }) }
    }

    @Test
    fun `should match and emit device when scan result has Forumslader manufacturer ID`() {
        // given
        val spyExtension = spyk(extension)
        val (scanCallback, emitter) = setupScanningEnvironment(spyExtension)

        val result = mockk<ScanResult>(relaxed = true) {
            every { device.address } returns "22:33:44:55:66:77"
            every { device.name } returns null
            every { scanRecord?.deviceName } returns null
            every { scanRecord?.serviceUuids } returns null
            every { scanRecord?.getManufacturerSpecificData(MANUFACTURER_ID_FORUMSLADER) } returns byteArrayOf(1, 2)
        }

        // when
        scanCallback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)

        // then
        verify { emitter.onNext(match { it.uid == "fl-22:33:44:55:66:77" }) }
    }

    @Test
    fun `should not emit device when scan result does not match Forumslader criteria`() {
        // given
        val spyExtension = spyk(extension)
        val (scanCallback, emitter) = setupScanningEnvironment(spyExtension)

        val result = mockk<ScanResult>(relaxed = true) {
            every { device.address } returns "99:99:99:99:99:99"
            every { device.name } returns "OtherDevice"
            every { scanRecord?.deviceName } returns "OtherDevice"
            every { scanRecord?.serviceUuids } returns null
            every { scanRecord?.getManufacturerSpecificData(any()) } returns null
        }

        // when
        scanCallback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result)

        // then
        verify(exactly = 0) { emitter.onNext(match { it.uid == "fl-99:99:99:99:99:99" }) }
    }

    @Test
    fun `should wait and start scan when bluetooth transitions from disabled to enabled`() {
        // given
        val bluetoothStateFlow = MutableStateFlow(false)
        val extensionWithFlow = ForumsladerExtension(
            adapterFactory = { _, addr, name ->
                mockk(relaxed = true) {
                    every { device } returns Device(
                        extension = "karoo-forumslader",
                        uid = "fl-$addr",
                        dataTypes = emptyList(),
                        displayName = name ?: "Forumslader"
                    )
                }
            },
            defaultScope = CoroutineScope(Dispatchers.Unconfined),
            scanSettingsFactory = { mockk(relaxed = true) },
            bluetoothStateFlowFactory = { bluetoothStateFlow }
        )
        val spyExtension = spyk(extensionWithFlow)
        setupMockExtension(spyExtension)
        val bluetoothManager = mockk<BluetoothManager>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val scanner = mockk<BluetoothLeScanner>(relaxed = true)
        val emitter = mockk<Emitter<Device>>(relaxed = true)

        every { spyExtension.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        every { spyExtension.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { spyExtension.getSystemService(LocationManager::class.java) } returns null
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.bluetoothLeScanner } returns scanner
        every { scanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) } returns Unit

        // when
        spyExtension.startScan(emitter)

        // then
        verify(exactly = 0) { scanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }

        // when (Bluetooth turns ON)
        bluetoothStateFlow.value = true

        // then
        verify(exactly = 1) { scanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }
    }

    @Test
    fun `should pause scan when bluetooth transitions to disabled`() {
        // given
        val bluetoothStateFlow = MutableStateFlow(true)
        val extensionWithFlow = ForumsladerExtension(
            adapterFactory = { _, addr, name ->
                mockk(relaxed = true) {
                    every { device } returns Device(
                        extension = "karoo-forumslader",
                        uid = "fl-$addr",
                        dataTypes = emptyList(),
                        displayName = name ?: "Forumslader"
                    )
                }
            },
            defaultScope = CoroutineScope(Dispatchers.Unconfined),
            scanSettingsFactory = { mockk(relaxed = true) },
            bluetoothStateFlowFactory = { bluetoothStateFlow }
        )
        val spyExtension = spyk(extensionWithFlow)
        setupMockExtension(spyExtension)
        val bluetoothManager = mockk<BluetoothManager>()
        val bluetoothAdapter = mockk<BluetoothAdapter>()
        val scanner = mockk<BluetoothLeScanner>(relaxed = true)
        val emitter = mockk<Emitter<Device>>(relaxed = true)

        every { spyExtension.checkSelfPermission(any()) } returns PackageManager.PERMISSION_GRANTED
        every { spyExtension.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { spyExtension.getSystemService(LocationManager::class.java) } returns null
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.isEnabled } returns true
        every { bluetoothAdapter.bluetoothLeScanner } returns scanner
        every { scanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) } returns Unit
        every { scanner.stopScan(any<ScanCallback>()) } returns Unit

        // when
        spyExtension.startScan(emitter)

        // then
        verify(exactly = 1) { scanner.startScan(any<List<ScanFilter>>(), any<ScanSettings>(), any<ScanCallback>()) }

        // when (Bluetooth turns OFF)
        bluetoothStateFlow.value = false

        // then
        verify(exactly = 1) { scanner.stopScan(any<ScanCallback>()) }
    }
}
