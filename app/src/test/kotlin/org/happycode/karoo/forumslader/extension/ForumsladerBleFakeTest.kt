package org.happycode.karoo.forumslader.extension

import com.juul.kable.KableInitializer
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBle
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBlePeripheral
import fi.nikosavola.karooext.testing.DeviceRecorder
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.InRideAlert
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The real extension, Kable and the parser run against a simulated Forumslader over fake BLE.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerBleFakeTest {
    @get:Rule val karoo = FakeKarooRule()

    private val ble = FakeBle(karoo.app)
    private lateinit var forumslader: FakeBlePeripheral

    private fun frame(payload: String): ByteArray {
        val checksum = payload.substring(1).fold(0) { acc, c -> acc xor c.code }
        return "$payload*${checksum.toString(16).uppercase().padStart(2, '0')}\n".toByteArray()
    }

    @Before
    fun setUp() {
        // Robolectric does not run App Startup providers, and Kable reads its context from one.
        KableInitializer().create(karoo.app)
        ble.grantPermissions()
        forumslader = ble.peripheral("AA:BB:CC:DD:EE:01", name = "Forumslader") {
            service(SERVICE_UUID_V6, advertised = true) {
                characteristic(CHARACTERISTIC_UART_RX_V6, notify = true)
                characteristic(CHARACTERISTIC_UART_TX_V6, writeNoResponse = true)
            }
        }
    }

    @Test
    fun `scan reports a forumslader and ignores other devices`() {
        val scan = karoo.host<ForumsladerExtension>().startScan()
        val other = ble.peripheral("AA:BB:CC:DD:EE:02", name = "Garmin HRM")

        other.advertise()
        forumslader.advertise()

        val device = scan.await(10_000) { true }
        assertEquals("fl-AA:BB:CC:DD:EE:01", device.uid)
        assertEquals(1, scan.items.size)
    }

    @Test
    fun `telemetry from the device becomes data points`() {
        val host = karoo.host<ForumsladerExtension>()
        val events = host.connectDevice("fl-AA:BB:CC:DD:EE:01")

        forumslader.advertise()
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected()
        forumslader.notify(CHARACTERISTIC_UART_RX_V6, frame($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"))

        val voltage = events.awaitDataPoint(DataType.dataTypeId("karoo-forumslader", "fl_battery_voltage"))
        assertEquals(12.33, voltage.singleValue!!, 0.01)
    }

    private fun connect(): DeviceRecorder {
        val events = karoo.host<ForumsladerExtension>().connectDevice("fl-AA:BB:CC:DD:EE:01")
        forumslader.advertise()
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected()
        return events
    }

    @Test
    fun `the extension asks the device for its parameters once notifications are on`() {
        connect()

        val command = forumslader.awaitWrite(CHARACTERISTIC_UART_TX_V6)

        assertEquals($$"$FLT,5*47\r\n", String(command, Charsets.US_ASCII))
    }

    @Test
    fun `a short circuit status raises an in-ride alert`() {
        connect()

        forumslader.notify(CHARACTERISTIC_UART_RX_V6, frame($$"$FL6,8,0,100,4100,4120,4110,-150,250,0,0,0,12345"))

        val alert = karoo.awaitEffect<InRideAlert>()
        assertEquals("fl_short", alert.id)
    }

    @Test
    fun `losing the link reports disconnected and a reconnect resumes data`() {
        val events = connect()

        forumslader.disconnect()
        events.awaitStatus(ConnectionStatus.DISCONNECTED)
        val resumeAt = events.items.size

        // The manager waits five seconds before it scans again.
        forumslader.advertise(timeoutMs = 30_000)
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected(after = resumeAt)
    }
}
