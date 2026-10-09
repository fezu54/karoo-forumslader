package org.happycode.karoo.forumslader.extension

import com.juul.kable.KableInitializer
import fi.nikosavola.karooext.testing.DeviceRecorder
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBle
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBlePeripheral
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// A FIT session started on the extension receives the developer fields built from telemetry that
// arrived over fake BLE.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerFitFakeTest {
    @get:Rule val karoo = FakeKarooRule()

    private val ble = FakeBle(karoo.app)
    private lateinit var forumslader: FakeBlePeripheral

    @Before
    fun setUp() {
        KableInitializer().create(karoo.app)
        ble.grantPermissions()
        forumslader = ble.peripheral(FL_ADDRESS, name = "Forumslader") {
            service(SERVICE_UUID_V6, advertised = true) {
                characteristic(CHARACTERISTIC_UART_RX_V6, notify = true)
                characteristic(CHARACTERISTIC_UART_TX_V6, writeNoResponse = true)
            }
        }
    }

    @Test
    fun `should write the telemetry developer fields when a fit session is active`() {
        val host = karoo.host<ForumsladerExtension>()
        val fit = host.startFit()
        connect(host)

        notifyTelemetry()

        val record = fit.awaitRecord(5_000) { it.containsKey("Battery Voltage") }
        assertEquals(1.5, record.getValue("Battery Voltage"), 0.001)
        assertEquals(2.5, record.getValue("Battery Current"), 0.001)
        assertEquals(9.0, record.getValue("Dynamo Power"), 0.001)
        assertEquals(25.5, record.getValue("Temperature"), 0.001)
        // Frequency 100 Hz at the metric wheel size is about 1.571 m/s, written as km/h.
        assertEquals(1.571 * 3.6, record.getValue("Speed"), 0.01)
    }

    @Test
    fun `should stop writing fit records when the fit session is cancelled`() {
        val host = karoo.host<ForumsladerExtension>()
        val fit = host.startFit()
        connect(host)
        notifyTelemetry()
        fit.awaitRecord(5_000) { it.containsKey("Battery Voltage") }

        host.stopFit(fit)
        val mark = fit.mark()

        notifyTelemetry()
        assertThrows(IllegalStateException::class.java) {
            fit.await(500, after = mark) { true }
        }
    }

    private fun connect(host: FakeKarooHost): DeviceRecorder {
        val events = host.connectDevice(FL_UID)
        forumslader.advertise()
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected()
        return events
    }

    private fun notifyTelemetry() {
        forumslader.notify(
            CHARACTERISTIC_UART_RX_V6,
            bleFrame($$"$FLB,255,0,1005") +
                bleFrame($$"$FLC,5,0,85") +
                bleFrame($$"$FL6,200,3,100,500,500,500,2500,3500,0,0,0,1000"),
        )
    }
}
