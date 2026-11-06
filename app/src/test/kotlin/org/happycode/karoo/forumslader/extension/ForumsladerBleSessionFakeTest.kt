package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.content.Context
import com.juul.kable.KableInitializer
import fi.nikosavola.karooext.testing.DeviceRecorder
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBle
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBlePeripheral
import io.hammerhead.karooext.models.ConnectionStatus
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_LOCKED_MAC_ADDRESS
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.CommandBus
import org.happycode.karoo.forumslader.domain.ForumsladerCommand
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.UUID

// Scan, connect, command and teardown paths driven through the real extension against fake BLE.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerBleSessionFakeTest {
    @get:Rule val karoo = FakeKarooRule()

    private val ble = FakeBle(karoo.app)
    private lateinit var forumslader: FakeBlePeripheral

    @Before
    fun setUp() {
        KableInitializer().create(karoo.app)
        ble.grantPermissions()
        clearPrefs()
        forumslader = ble.peripheral(FL_ADDRESS, name = "Forumslader") {
            service(SERVICE_UUID_V6, advertised = true) {
                characteristic(CHARACTERISTIC_UART_RX_V6, notify = true)
                characteristic(CHARACTERISTIC_UART_TX_V6, writeNoResponse = true)
            }
        }
    }

    @Test
    fun `should advertise every data type when the scanner finds a forumslader`() {
        val scan = karoo.host<ForumsladerExtension>().startScan()

        forumslader.advertise()
        val device = scan.await(5_000) { it.uid == FL_UID }

        assertEquals("Forumslader", device.displayName)
        assertEquals(16, device.dataTypes.size)
        assertTrue(device.dataTypes.contains(flTypeId(DataFieldId.BATTERY_VOLTAGE)))
    }

    @Test
    fun `should skip the radio scan and emit the locked device when a MAC address is stored`() {
        karoo.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LOCKED_MAC_ADDRESS, FL_ADDRESS)
            .commit()
        val scan = karoo.host<ForumsladerExtension>().startScan()

        assertEquals(FL_UID, scan.await(5_000) { true }.uid)
        assertFalse(ble.isScanning)
    }

    @Test
    fun `should not start a scan when the scan permission is missing`() {
        shadowOf(karoo.app).denyPermissions(Manifest.permission.BLUETOOTH_SCAN)
        val scan = karoo.host<ForumsladerExtension>().startScan()

        assertFalse(ble.isScanning)
        assertTrue(scan.items.isEmpty())
    }

    @Test
    fun `should pause and resume scanning when the bluetooth adapter toggles`() {
        val scan = karoo.host<ForumsladerExtension>().startScan()
        ble.awaitScan()
        assertTrue(ble.isScanning)

        ble.setEnabled(false)
        karoo.awaitValue(5_000) { true.takeIf { !ble.isScanning } }

        ble.setEnabled(true)
        forumslader.advertise()
        assertEquals(FL_UID, scan.await(5_000) { true }.uid)
    }

    @Test
    fun `should forward a command from the bus to the device and request parameters`() {
        val events = connect()
        val mark = forumslader.mark()

        CommandBus.sendCommand(ForumsladerCommand.ResetTourDistance)

        forumslader.awaitWrite(CHARACTERISTIC_UART_TX_V6, after = mark) {
            String(it, Charsets.US_ASCII).startsWith($$"$FLT,6")
        }
        val request = forumslader.awaitWrite(CHARACTERISTIC_UART_TX_V6, after = mark) {
            String(it, Charsets.US_ASCII).startsWith($$"$FLT,5")
        }
        assertEquals($$"$FLT,5*47\r\n", String(request, Charsets.US_ASCII))
        assertEquals(ConnectionStatus.CONNECTED, events.connectionStatus)
    }

    @Test
    fun `should release the karoo consumers when the device session is disconnected`() {
        val host = karoo.host<ForumsladerExtension>()
        val baseline = karoo.system.consumerCount
        val events = connect(host)
        assertTrue(karoo.system.consumerCount > baseline)

        host.disconnectDevice(events)

        karoo.awaitValue(5_000) { true.takeIf { karoo.system.consumerCount == baseline } }
    }

    @Test
    fun `should connect but stay silent when the device exposes no forumslader service`() {
        val stranger = ble.peripheral("AA:BB:CC:DD:EE:09", name = "Some Sensor") {
            service(HEART_RATE_SERVICE, advertised = true) {}
        }
        val events = karoo.host<ForumsladerExtension>().connectDevice("fl-AA:BB:CC:DD:EE:09")

        stranger.advertise()
        stranger.connect()
        events.awaitConnected()

        assertTrue(stranger.subscribedCharacteristics.isEmpty())
        assertTrue(events.dataPoints(flTypeId(DataFieldId.BATTERY_VOLTAGE)).isEmpty())
    }

    @Test
    fun `should not connect when the connect permission is missing`() {
        shadowOf(karoo.app).denyPermissions(Manifest.permission.BLUETOOTH_CONNECT)
        val events = karoo.host<ForumsladerExtension>().connectDevice(FL_UID)

        assertTrue(events.items.isEmpty())
        assertEquals(0, forumslader.connectionAttempts)
    }

    @Test
    fun `should reconnect after a failed connection attempt`() {
        val events = karoo.host<ForumsladerExtension>().connectDevice(FL_UID)
        forumslader.advertise()
        forumslader.awaitConnectionRequest()
        forumslader.failConnection(133)

        // The manager waits before it scans again, so keep advertising until it does.
        forumslader.advertise(untilMatched = true, timeoutMs = 30_000)
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected()

        assertTrue(forumslader.connectionAttempts >= 2)
    }

    private fun connect(host: FakeKarooHost = karoo.host<ForumsladerExtension>()): DeviceRecorder {
        val events = host.connectDevice(FL_UID)
        forumslader.advertise()
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected()
        return events
    }

    private fun clearPrefs() {
        karoo.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private companion object {
        val HEART_RATE_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    }
}
