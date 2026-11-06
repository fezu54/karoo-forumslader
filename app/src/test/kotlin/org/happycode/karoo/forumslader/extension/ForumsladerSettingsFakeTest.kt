package org.happycode.karoo.forumslader.extension

import android.content.Context
import com.juul.kable.KableInitializer
import fi.nikosavola.karooext.testing.DeviceRecorder
import fi.nikosavola.karooext.testing.FakeKarooHost
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBle
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBlePeripheral
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_LOCKED_MAC_ADDRESS
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_VERSION
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME
import org.happycode.karoo.forumslader.application.ForumsladerStateStore
import org.happycode.karoo.forumslader.domain.CommandBus
import org.happycode.karoo.forumslader.domain.ForumsladerCommand
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.happycode.karoo.forumslader.model.ForumsladerConfig
import org.happycode.karoo.forumslader.model.ForumsladerVersion
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// What the extension writes back to shared preferences and the config-loaded store while it talks
// to a device.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerSettingsFakeTest {
    @get:Rule val karoo = FakeKarooRule()

    private val ble = FakeBle(karoo.app)
    private lateinit var forumslader: FakeBlePeripheral

    @Before
    fun setUp() {
        ForumsladerStateStore.clear()
        clearPrefs()
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
    fun tearDown() = ForumsladerStateStore.clear()

    @Test
    fun `should lock the MAC address and store the detected version on the first telemetry`() {
        connect()
        // The service UUID is enough for the manager to report V6 before any sentence is parsed.
        karoo.awaitValue(5_000) { prefs().getString(KEY_VERSION, null) }

        forumslader.notify(CHARACTERISTIC_UART_RX_V6, bleFrame($$"$FL6,0,0,100,4100,4120,4110,-150,250,0,0,0,12345"))

        karoo.awaitValue(5_000) { prefs().getString(KEY_LOCKED_MAC_ADDRESS, null)?.takeIf { it == FL_ADDRESS } }
        assertEquals(ForumsladerVersion.V6.key, prefs().getString(KEY_VERSION, null))
    }

    @Test
    fun `should persist the wheel size and poles from the FLP sentence`() {
        connect()

        forumslader.notify(CHARACTERISTIC_UART_RX_V6, bleFrame($$"$FLP,2150,7,0,0,0,0"))

        karoo.awaitValue(5_000) { true.takeIf { ForumsladerConfig(karoo.app).wheelsize == 2150 } }
        assertEquals(7, ForumsladerConfig(karoo.app).poles)
    }

    @Test
    fun `should mark the configuration loaded when the FLP sentence arrives`() {
        connect()

        forumslader.notify(CHARACTERISTIC_UART_RX_V6, bleFrame($$"$FLP,2200,14,0,0,0,0"))

        karoo.awaitValue(5_000) { true.takeIf { ForumsladerStateStore.isConfigLoadedFlow.value } }
        assertTrue(ForumsladerStateStore.isConfigLoadedFlow.value)
    }

    @Test
    fun `should clear the loaded flag when a reset command is issued`() {
        connect()
        forumslader.notify(CHARACTERISTIC_UART_RX_V6, bleFrame($$"$FLP,2200,14,0,0,0,0"))
        karoo.awaitValue(5_000) { true.takeIf { ForumsladerStateStore.isConfigLoadedFlow.value } }

        CommandBus.sendCommand(ForumsladerCommand.ResetTourDistance)

        karoo.awaitValue(5_000) { true.takeIf { !ForumsladerStateStore.isConfigLoadedFlow.value } }
        assertFalse(ForumsladerStateStore.isConfigLoadedFlow.value)
    }

    private fun connect(host: FakeKarooHost = karoo.host<ForumsladerExtension>()): DeviceRecorder {
        val events = host.connectDevice(FL_UID)
        forumslader.advertise()
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected()
        return events
    }

    private fun prefs() = karoo.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun clearPrefs() {
        prefs().edit().clear().commit()
    }
}
