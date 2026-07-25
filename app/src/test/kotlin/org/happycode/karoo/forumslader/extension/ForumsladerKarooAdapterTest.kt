package org.happycode.karoo.forumslader.extension

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.util.Log
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DeviceEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForumsladerKarooAdapterTest {
    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var emitter: Emitter<DeviceEvent>

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        mockkStatic(android.os.Looper::class)
        every { android.os.Looper.getMainLooper() } returns mockk(relaxed = true)

        mockkConstructor(Handler::class)
        every { anyConstructed<Handler>().post(any()) } answers {
            val runnable = firstArg<Runnable>()
            runnable.run()
            true
        }
        every { anyConstructed<Handler>().postDelayed(any(), any()) } returns true
        every { anyConstructed<Handler>().removeCallbacks(any<Runnable>()) } returns Unit

        val mockScanFilterBuilder = mockk<ScanFilter.Builder>()
        every { mockScanFilterBuilder.setDeviceAddress(any()) } returns mockScanFilterBuilder
        every { mockScanFilterBuilder.build() } returns mockk(relaxed = true)
        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setDeviceAddress(any()) } returns mockScanFilterBuilder

        val mockScanSettingsBuilder = mockk<ScanSettings.Builder>()
        every { mockScanSettingsBuilder.setScanMode(any()) } returns mockScanSettingsBuilder
        every { mockScanSettingsBuilder.build() } returns mockk(relaxed = true)
        mockkConstructor(ScanSettings.Builder::class)
        every { anyConstructed<ScanSettings.Builder>().setScanMode(any()) } returns mockScanSettingsBuilder

        context = mockk(relaxed = true)
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns mockPrefs

        bluetoothManager = mockk(relaxed = true)
        bluetoothAdapter = mockk(relaxed = true)
        bluetoothDevice = mockk(relaxed = true)
        emitter = mockk(relaxed = true)

        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.getRemoteDevice(any<String>()) } returns bluetoothDevice
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should set correct metadata on device initialization`() {
        val address = "00:11:22:33:44:55"
        val forumslader = ForumsladerKarooAdapter(context, address, "My Forumslader")

        assertEquals("karoo-forumslader", forumslader.device.extension)
        assertEquals("fl-$address", forumslader.device.uid)
        assertEquals("My Forumslader", forumslader.device.displayName)
    }

    @Test
    fun `should initiate gatt connection on connect`() {
        val address = "00:11:22:33:44:55"
        val forumslader = ForumsladerKarooAdapter(context, address)

        forumslader.connect(emitter)

        verify { bluetoothDevice.connectGatt(context, false, any(), any()) }
    }

    @Test
    fun `should initiate fallback scan on connection timeout if mac is locked`() {
        val address = "00:11:22:33:44:55"
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getString("locked_mac_address", null) } returns address
        every {
            context.getSharedPreferences(
                "forumslader_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockPrefs

        val scanner = mockk<android.bluetooth.le.BluetoothLeScanner>(relaxed = true)
        every { bluetoothAdapter.bluetoothLeScanner } returns scanner
        every { bluetoothAdapter.isEnabled } returns true

        // Capture postDelayed runnables to simulate timeout
        val runnables = mutableListOf<Runnable>()
        every { anyConstructed<Handler>().postDelayed(capture(runnables), any()) } returns true

        val forumslader = ForumsladerKarooAdapter(context, address)
        forumslader.connect(emitter)

        // Timeout runnable is scheduled
        val timeoutRunnable = runnables.firstOrNull()
        timeoutRunnable?.run()

        // After timeout, it should schedule reconnect.
        val reconnectRunnable = runnables.lastOrNull()
        reconnectRunnable?.run()

        // During reconnect, because shouldFallbackToScan became true, it should start scan
        verify {
            scanner.startScan(
                any<List<ScanFilter>>(),
                any(),
                any<android.bluetooth.le.ScanCallback>()
            )
        }
    }

    @Test
    fun `should lock mac address on first successful data reception`() {
        val address = "00:11:22:33:44:55"
        val mockEditor = mockk<SharedPreferences.Editor>(relaxed = true)
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.edit() } returns mockEditor
        every { mockPrefs.getString("locked_mac_address", null) } returns null
        every {
            context.getSharedPreferences(
                "forumslader_prefs",
                Context.MODE_PRIVATE
            )
        } returns mockPrefs

        val forumslader = ForumsladerKarooAdapter(context, address)
        forumslader.connect(emitter)

        val callbackSlot = io.mockk.slot<android.bluetooth.BluetoothGattCallback>()
        verify { bluetoothDevice.connectGatt(context, false, capture(callbackSlot), any()) }

        val gattCallback = callbackSlot.captured
        val gatt = mockk<android.bluetooth.BluetoothGatt>(relaxed = true)
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        every { characteristic.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX

        gattCallback.onConnectionStateChange(
            gatt,
            android.bluetooth.BluetoothGatt.GATT_SUCCESS,
            android.bluetooth.BluetoothProfile.STATE_CONNECTED
        )

        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)
        every { service.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
        every { service.getCharacteristic(any()) } returns characteristic
        every { gatt.getService(org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6) } returns null
        every { gatt.getService(org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5) } returns service

        gattCallback.onServicesDiscovered(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS)

        val telemetryBytes = "\$FL5,1,2,3,4,5,6,7,8,9,10,11,12,13\n".toByteArray(Charsets.US_ASCII)
        gattCallback.onCharacteristicChanged(gatt, characteristic, telemetryBytes)

        verify { mockEditor.putString("locked_mac_address", address) }
    }

    @Test
    fun `should emit correctly mapped DataPoints on telemetry reception`() {
        val address = "00:11:22:33:44:55"
        val forumslader = ForumsladerKarooAdapter(context, address)

        val gatt = mockk<android.bluetooth.BluetoothGatt>(relaxed = true)
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        every { characteristic.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX

        val callbackSlot = io.mockk.slot<android.bluetooth.BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(callbackSlot),
                any()
            )
        } returns gatt

        forumslader.connect(emitter)

        verify { bluetoothDevice.connectGatt(context, false, any(), any()) }
        val gattCallback = callbackSlot.captured

        gattCallback.onConnectionStateChange(
            gatt,
            android.bluetooth.BluetoothGatt.GATT_SUCCESS,
            android.bluetooth.BluetoothProfile.STATE_CONNECTED
        )

        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)
        every { service.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
        every { service.getCharacteristic(any()) } returns characteristic
        every { gatt.getService(org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6) } returns null
        every { gatt.getService(org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5) } returns service

        gattCallback.onServicesDiscovered(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS)

        val eventSlot = mutableListOf<DeviceEvent>()
        every { emitter.onNext(capture(eventSlot)) } returns Unit

        val telemetryBytes = "\$FL5,1,2,3,4,5,6,7,8,9,10,11,12,13\n".toByteArray(Charsets.US_ASCII)
        gattCallback.onCharacteristicChanged(gatt, characteristic, telemetryBytes)

        val dataPoints = eventSlot.filterIsInstance<io.hammerhead.karooext.models.OnDataPoint>()
            .map { it.dataPoint }
        assert(dataPoints.isNotEmpty())

        val voltagePoint = dataPoints.find {
            it.dataTypeId == io.hammerhead.karooext.models.DataType.dataTypeId(
                "karoo-forumslader",
                "fl_battery_voltage"
            )
        }
        assertEquals(
            0.015,
            voltagePoint?.values?.get(io.hammerhead.karooext.models.DataType.Field.SINGLE) ?: 0.0,
            0.001
        )
    }

    @Test
    fun `should stop parameter request loop when config is loaded`() {
        val address = "00:11:22:33:44:55"
        val forumslader = ForumsladerKarooAdapter(context, address)

        val gatt = mockk<android.bluetooth.BluetoothGatt>(relaxed = true)
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        every { characteristic.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX

        val callbackSlot = io.mockk.slot<android.bluetooth.BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(callbackSlot),
                any()
            )
        } returns gatt

        forumslader.connect(emitter)
        verify { bluetoothDevice.connectGatt(context, false, any(), any()) }
        val gattCallback = callbackSlot.captured

        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)
        every { service.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
        every { service.getCharacteristic(any()) } returns characteristic
        every { gatt.getService(org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6) } returns null
        every { gatt.getService(org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5) } returns service

        val descriptor = mockk<android.bluetooth.BluetoothGattDescriptor>(relaxed = true)
        every { descriptor.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR
        every { characteristic.getDescriptor(any()) } returns descriptor

        gattCallback.onConnectionStateChange(
            gatt,
            android.bluetooth.BluetoothGatt.GATT_SUCCESS,
            android.bluetooth.BluetoothProfile.STATE_CONNECTED
        )
        gattCallback.onServicesDiscovered(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS)

        // Simulate CCCD write success to start protocol polling loop
        val delayedRunnables = mutableListOf<Runnable>()
        every { anyConstructed<Handler>().postDelayed(capture(delayedRunnables), any()) } returns true

        gattCallback.onDescriptorWrite(
            gatt,
            descriptor,
            android.bluetooth.BluetoothGatt.GATT_SUCCESS
        )

        // writeCharacteristic should be called synchronously via inline Handler.post
        try {
            verify { gatt.writeCharacteristic(any(), any<ByteArray>(), any<Int>()) }
        } catch (_: AssertionError) {
            verify { gatt.writeCharacteristic(any()) }
        }

        // Simulate receiving FLP (config loaded)
        val flpBytes = $$"$FLP,2200,14\n".toByteArray(Charsets.US_ASCII)
        gattCallback.onCharacteristicChanged(gatt, characteristic, flpBytes)

        // Clear mocks and wait for next tick.
        io.mockk.clearMocks(gatt, answers = false, recordedCalls = true)
        
        // Execute the delayed parameter request runnable
        delayedRunnables.lastOrNull()?.run()
        
        verify(exactly = 0) { gatt.writeCharacteristic(any(), any<ByteArray>(), any<Int>()) }
        verify(exactly = 0) { gatt.writeCharacteristic(any()) }
    }

    @Test
    fun `should retry cccd write up to 3 times on failure before disconnecting`() {
        val address = "00:11:22:33:44:55"
        val forumslader = ForumsladerKarooAdapter(context, address)

        val gatt = mockk<android.bluetooth.BluetoothGatt>(relaxed = true)
        val characteristic = mockk<android.bluetooth.BluetoothGattCharacteristic>(relaxed = true)
        every { characteristic.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX

        val callbackSlot = io.mockk.slot<android.bluetooth.BluetoothGattCallback>()
        every {
            bluetoothDevice.connectGatt(
                any(),
                any(),
                capture(callbackSlot),
                any()
            )
        } returns gatt

        forumslader.connect(emitter)
        verify { bluetoothDevice.connectGatt(context, false, any(), any()) }
        val gattCallback = callbackSlot.captured
        val service = mockk<android.bluetooth.BluetoothGattService>(relaxed = true)
        every { service.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
        every { service.getCharacteristic(any()) } returns characteristic
        every { gatt.getService(org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6) } returns null
        every { gatt.getService(org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5) } returns service

        val descriptor = mockk<android.bluetooth.BluetoothGattDescriptor>(relaxed = true)
        every { descriptor.uuid } returns org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CLIENT_CHARACTERISTIC_CONFIGURATION_DESCRIPTOR
        every { characteristic.getDescriptor(any()) } returns descriptor

        gattCallback.onConnectionStateChange(
            gatt,
            android.bluetooth.BluetoothGatt.GATT_SUCCESS,
            android.bluetooth.BluetoothProfile.STATE_CONNECTED
        )
        gattCallback.onServicesDiscovered(gatt, android.bluetooth.BluetoothGatt.GATT_SUCCESS)

        val runnables = mutableListOf<Runnable>()
        every { anyConstructed<Handler>().postDelayed(capture(runnables), any()) } returns true

        // First failure
        gattCallback.onDescriptorWrite(
            gatt,
            descriptor,
            android.bluetooth.BluetoothGatt.GATT_FAILURE
        )
        runnables.lastOrNull()?.run()

        // Second failure
        gattCallback.onDescriptorWrite(
            gatt,
            descriptor,
            android.bluetooth.BluetoothGatt.GATT_FAILURE
        )
        runnables.lastOrNull()?.run()

        // Third failure
        gattCallback.onDescriptorWrite(
            gatt,
            descriptor,
            android.bluetooth.BluetoothGatt.GATT_FAILURE
        )
        runnables.lastOrNull()?.run()

        // Fourth failure should trigger disconnect
        gattCallback.onDescriptorWrite(
            gatt,
            descriptor,
            android.bluetooth.BluetoothGatt.GATT_FAILURE
        )

        verify { gatt.disconnect() }
    }
}
