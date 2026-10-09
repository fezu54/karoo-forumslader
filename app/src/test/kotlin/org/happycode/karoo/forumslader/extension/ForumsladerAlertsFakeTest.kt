package org.happycode.karoo.forumslader.extension

import android.content.Context
import android.content.SharedPreferences
import com.juul.kable.KableInitializer
import fi.nikosavola.karooext.testing.DeviceRecorder
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBle
import fi.nikosavola.karooext.testing.robolectric.ble.FakeBlePeripheral
import io.hammerhead.karooext.models.InRideAlert
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_BATTERY_LOW_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_HIGH_TEMP_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME
import org.happycode.karoo.forumslader.R
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

// Alert rules run on the real telemetry parsed over fake BLE, so settings, status bits and
// debouncing are covered as the ride would exercise them.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerAlertsFakeTest {
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
    fun `should raise the low battery alert with the stored threshold when the level drops below it`() {
        setLowBatteryThreshold(40)
        connect()

        notifyTelemetry(batteryLevel = 25)

        val alert = karoo.awaitEffect<InRideAlert> { it.id == "fl_bat_low" }
        assertEquals("Forumslader", alert.title)
        assertEquals(karoo.app.getString(R.string.alert_battery_low_detail, 25), alert.detail)
    }

    @Test
    fun `should not raise the low battery alert when the level is above the threshold`() {
        setLowBatteryThreshold(10)
        connect()

        notifyTelemetry(batteryLevel = 85)

        karoo.assertNoEffect<InRideAlert> { it.id == "fl_bat_low" }
    }

    @Test
    fun `should raise the high temperature alert when the stored threshold is crossed`() {
        setHighTemperatureThreshold(45f)
        connect()

        // FLB carries tenths of a degree: raw 500 is 50.0 C.
        notifyTelemetry(batteryLevel = 85, extra = bleFrame($$"$FLB,500,0,1005"))

        val alert = karoo.awaitEffect<InRideAlert> { it.id == "fl_hi_temp" }
        assertEquals(karoo.app.getString(R.string.alert_high_temperature_detail, 50), alert.detail)
    }

    @Test
    fun `should raise the system interrupt alert when the status bit is set`() {
        connect()

        // Status 0x800000 sets the system interrupt bit without changing the charge state.
        notifyTelemetry(batteryLevel = 85, statusHex = "800000")

        val alert = karoo.awaitEffect<InRideAlert> { it.id == "fl_sys_int" }
        assertEquals(karoo.app.getString(R.string.alert_system_interrupt_detail), alert.detail)
    }

    private fun connect(): DeviceRecorder {
        val events = karoo.host<ForumsladerExtension>().connectDevice(FL_UID)
        forumslader.advertise()
        forumslader.connect()
        forumslader.awaitSubscribed(CHARACTERISTIC_UART_RX_V6)
        events.awaitConnected()
        return events
    }

    private fun notifyTelemetry(batteryLevel: Int, statusHex: String = "0", extra: ByteArray = ByteArray(0)) {
        val frames = bleFrame($$"$FLC,5,0,$${batteryLevel}") +
            bleFrame($$"$FL6,$${statusHex},0,100,500,500,500,2500,3500,0,0,0,1000") +
            extra
        forumslader.notify(CHARACTERISTIC_UART_RX_V6, frames)
    }

    private fun prefs(): SharedPreferences =
        karoo.app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun setLowBatteryThreshold(percent: Int) {
        prefs().edit().putInt(KEY_BATTERY_LOW_THRESHOLD, percent).commit()
    }

    private fun setHighTemperatureThreshold(celsius: Float) {
        prefs().edit().putFloat(KEY_HIGH_TEMP_THRESHOLD, celsius).commit()
    }

    private fun clearPrefs() {
        prefs().edit().clear().commit()
    }
}
