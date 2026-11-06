package org.happycode.karoo.forumslader.extension

import android.content.Context
import com.juul.kable.KableInitializer
import fi.nikosavola.karooext.testing.DeviceRecorder
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBle
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBlePeripheral
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnDataPoint
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_RX
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Telemetry bytes arriving as notifications are parsed by the real parser and surface as data
// points on the device session, including the framing, checksum and version differences.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerBleProtocolFakeTest {
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
    fun `should emit every metric when a full telemetry batch arrives from the device`() {
        val events = connectV6()

        forumslader.notify(
            CHARACTERISTIC_UART_RX_V6,
            bleFrame($$"$FLB,255,0,1005") +
                bleFrame($$"$FLC,5,0,85") +
                bleFrame($$"$FL6,200,3,100,500,500,500,2500,3500,0,0,0,1000"),
        )

        // Await the last registry type so the earlier points are guaranteed to be recorded too.
        events.awaitDataPoint(flTypeId(DataFieldId.BATTERY_LEVEL))
        val values = events.dataPointsById()

        assertEquals(1.5, values.getValue(flTypeId(DataFieldId.BATTERY_VOLTAGE)), 0.001)
        assertEquals(100.0, values.getValue(flTypeId(DataFieldId.FREQUENCY)), 0.001)
        assertEquals(25.5, values.getValue(flTypeId(DataFieldId.TEMPERATURE)), 0.001)
        assertEquals(3.0, values.getValue(flTypeId(DataFieldId.GENERATOR_GEAR)), 0.001)
        assertEquals(85.0, values.getValue(flTypeId(DataFieldId.BATTERY_LEVEL)), 0.001)
        assertEquals(ChargeState.CHARGING.ordinal.toDouble(), values.getValue(flTypeId(DataFieldId.CHARGE_STATE)), 0.001)
        assertEquals(9.0, values.getValue(flTypeId(DataFieldId.DYNAMO_POWER)), 0.001)
        assertEquals(frequencyToSpeed(100.0, FREQUENCY_SCALE_V6), values.getValue(flTypeId(DataFieldId.SPEED)), 0.001)
        assertEquals(1000.0 * distancePerImpulse(IMPULSE_SCALE_V6), values.getValue(flTypeId(DataFieldId.TRIP_DISTANCE)), 0.001)
        val batteryLevel = events.items.filterIsInstance<OnDataPoint>()
            .last { it.dataPoint.dataTypeId == flTypeId(DataFieldId.BATTERY_LEVEL) }.dataPoint
        assertEquals(
            ChargeState.CHARGING.ordinal.toDouble(),
            batteryLevel.values.getValue(DataFieldId.FIELD_CHARGE_STATE),
            0.001,
        )
    }

    @Test
    fun `should parse a sentence that is split across two notifications`() {
        val events = connectV6()
        val frame = bleFrame($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345")

        forumslader.notify(CHARACTERISTIC_UART_RX_V6, frame.copyOfRange(0, 7))
        forumslader.notify(
            CHARACTERISTIC_UART_RX_V6,
            frame.copyOfRange(7, frame.size),
        )

        val voltage = events.awaitDataPoint(flTypeId(DataFieldId.BATTERY_VOLTAGE))
        assertEquals(12.33, voltage.singleValue!!, 0.01)
    }

    @Test
    fun `should drop a sentence with a bad checksum and still parse the next good one`() {
        val events = connectV6()
        val body = $$"$FL6,0,0,100,9000,9000,9000,-150,250,0,0,0,0"
        val wrongChecksum = (body.substring(1).fold(0) { acc, c -> acc xor c.code } xor 0xFF) and 0xFF
        val corrupted = "$body*${wrongChecksum.toString(16).uppercase().padStart(2, '0')}\n".toByteArray()

        forumslader.notify(CHARACTERISTIC_UART_RX_V6, corrupted)
        forumslader.notify(CHARACTERISTIC_UART_RX_V6, bleFrame($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"))

        events.awaitDataPoint(flTypeId(DataFieldId.BATTERY_VOLTAGE))
        // Only the good sentence produced a point; the corrupted one never reached the metrics.
        val voltages = events.dataPoints(flTypeId(DataFieldId.BATTERY_VOLTAGE))
        assertEquals(1, voltages.size)
        assertEquals(12.33, voltages.single().singleValue!!, 0.01)
    }

    @Test
    fun `should read speed distance and coarse battery level from an FLD sentence`() {
        val events = connectV6()

        forumslader.notify(CHARACTERISTIC_UART_RX_V6, bleFrame($$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,1.0"))

        val speed = events.awaitDataPoint(flTypeId(DataFieldId.SPEED))
        assertEquals(frequencyToSpeed(50.0, FREQUENCY_SCALE_V6), speed.singleValue!!, 0.001)
        assertEquals(1000.0, events.dataPoints(flTypeId(DataFieldId.TRIP_DISTANCE)).last().singleValue!!, 0.001)
        // Level index 7 maps to 95 % when no fine-grained FLC value has been seen.
        assertEquals(95.0, events.dataPoints(flTypeId(DataFieldId.BATTERY_LEVEL)).last().singleValue!!, 0.001)
    }

    @Test
    fun `should apply the V5 scaling when the device exposes the legacy service`() {
        val legacy = ble.peripheral("AA:BB:CC:DD:EE:05", name = "Forumslader V5") {
            service(SERVICE_UUID_V5, advertised = true) {
                characteristic(CHARACTERISTIC_UART_TX_RX, notify = true, writeNoResponse = true)
            }
        }
        val events = karoo.host<ForumsladerExtension>().connectDevice("fl-AA:BB:CC:DD:EE:05")
        legacy.advertise()
        legacy.connect()
        legacy.awaitSubscribed(CHARACTERISTIC_UART_TX_RX)
        events.awaitConnected()

        legacy.notify(
            CHARACTERISTIC_UART_TX_RX,
            bleFrame($$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000"),
        )

        val speed = events.awaitDataPoint(flTypeId(DataFieldId.SPEED))
        assertEquals(frequencyToSpeed(100.0, FREQUENCY_SCALE_V5), speed.singleValue!!, 0.001)
        assertEquals(1000.0 * distancePerImpulse(IMPULSE_SCALE_V5), events.dataPoints(flTypeId(DataFieldId.TRIP_DISTANCE)).last().singleValue!!, 0.001)
    }

    private fun connectV6(): DeviceRecorder {
        val events = karoo.host<ForumsladerExtension>().connectDevice(FL_UID)
        forumslader.advertise()
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected()
        return events
    }

    private fun DeviceRecorder.dataPointsById(): Map<String, Double> =
        items.filterIsInstance<OnDataPoint>()
            .associate { it.dataPoint.dataTypeId to it.dataPoint.values.getValue(DataType.Field.SINGLE) }

    private fun clearPrefs() {
        karoo.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun frequencyToSpeed(frequency: Double, scale: Double) = frequency * 2200.0 / 14.0 / 1000.0 * scale

    private fun distancePerImpulse(scale: Double) = 2200.0 / 14.0 / 1000.0 * scale

    private companion object {
        const val FREQUENCY_SCALE_V6 = 0.1
        const val FREQUENCY_SCALE_V5 = 1.0
        const val IMPULSE_SCALE_V6 = 1.0
        const val IMPULSE_SCALE_V5 = 4096.0
    }
}
