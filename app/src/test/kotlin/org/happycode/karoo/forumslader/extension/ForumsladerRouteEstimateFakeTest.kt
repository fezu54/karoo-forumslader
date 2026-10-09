package org.happycode.karoo.forumslader.extension

import com.juul.kable.KableInitializer
import fi.nikosavola.karooext.testing.DeviceRecorder
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBle
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBlePeripheral
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnNavigationState
import org.happycode.karoo.forumslader.application.estimation.BatteryEstimateStore
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The adapter subscribes to the route, headwind and navigation streams on the fake system, and the
// values it receives shape the published battery estimate.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerRouteEstimateFakeTest {
    @get:Rule val karoo = FakeKarooRule()

    private val ble = FakeBle(karoo.app)
    private lateinit var forumslader: FakeBlePeripheral

    @Before
    fun setUp() {
        BatteryEstimateStore.clear()
        KableInitializer().create(karoo.app)
        ble.grantPermissions()
        forumslader = ble.peripheral(FL_ADDRESS, name = "Forumslader") {
            service(SERVICE_UUID_V6, advertised = true) {
                characteristic(CHARACTERISTIC_UART_RX_V6, notify = true)
                characteristic(CHARACTERISTIC_UART_TX_V6, writeNoResponse = true)
            }
        }
    }

    @After
    fun tearDown() = BatteryEstimateStore.clear()

    @Test
    fun `should subscribe to the route headwind and navigation inputs when a device connects`() {
        val host = karoo.host<ForumsladerExtension>()
        connect(host)

        karoo.awaitStreamConsumer(
            DataType.Type.DISTANCE_TO_DESTINATION,
            DataType.Type.ELEVATION_REMAINING,
            DataType.dataTypeId("karoo-headwind", "headwindSpeed"),
        )
        karoo.awaitConsumer(OnNavigationState.Params)

        assertTrue(karoo.system.hasStreamConsumer(DataType.dataTypeId("karoo-headwind", "headwindSpeed")))
    }

    @Test
    fun `should surface the remaining route distance from the distance stream`() {
        val host = karoo.host<ForumsladerExtension>()
        connect(host)
        karoo.awaitStreamConsumer(DataType.Type.DISTANCE_TO_DESTINATION, DataType.Type.ELEVATION_REMAINING)

        karoo.system.setDataPoint(DataType.Type.DISTANCE_TO_DESTINATION, 25_000.0)
        karoo.system.setDataPoint(DataType.Type.ELEVATION_REMAINING, 300.0)
        notifyTelemetry()

        val estimate = karoo.awaitValue(5_000) {
            BatteryEstimateStore.estimateFlow.value?.takeIf { it.routeRemainingKm != null }
        }
        assertEquals(25.0f, estimate.routeRemainingKm!!)
    }

    @Test
    fun `should clear the route estimate when navigation becomes idle`() {
        val host = karoo.host<ForumsladerExtension>()
        connect(host)
        karoo.awaitStreamConsumer(DataType.Type.DISTANCE_TO_DESTINATION, DataType.Type.ELEVATION_REMAINING)
        karoo.system.setDataPoint(DataType.Type.DISTANCE_TO_DESTINATION, 25_000.0)
        notifyTelemetry()
        karoo.awaitValue(5_000) {
            BatteryEstimateStore.estimateFlow.value?.takeIf { it.routeRemainingKm != null }
        }

        karoo.system.setNavigation(OnNavigationState.NavigationState.Idle)

        val estimate = karoo.awaitValue(5_000) {
            BatteryEstimateStore.estimateFlow.value?.takeIf { it.routeRemainingKm == null }
        }
        assertNull(estimate.routeRemainingKm)
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
            bleFrame($$"$FLC,5,0,85") +
                bleFrame($$"$FL6,200,3,100,500,500,500,2500,3500,0,0,0,1000"),
        )
    }
}
