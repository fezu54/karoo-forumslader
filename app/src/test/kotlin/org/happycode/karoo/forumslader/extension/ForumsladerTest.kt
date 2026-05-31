package org.happycode.karoo.forumslader.extension

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DeviceEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForumsladerTest {
    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothDevice: BluetoothDevice
    private lateinit var emitter: Emitter<DeviceEvent>

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        bluetoothAdapter = mockk(relaxed = true)
        bluetoothDevice = mockk(relaxed = true)
        emitter = mockk(relaxed = true)

        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every { bluetoothAdapter.getRemoteDevice(any<String>()) } returns bluetoothDevice
    }

    @Test
    fun `should set correct metadata on device initialization`() {
        val address = "00:11:22:33:44:55"
        val forumslader = Forumslader(context, address, "My Forumslader")

        assertEquals("karoo-forumslader", forumslader.device.extension)
        assertEquals("fl-$address", forumslader.device.uid)
        assertEquals("My Forumslader", forumslader.device.displayName)
    }

    @Test
    fun `should initiate gatt connection on connect`() {
        val address = "00:11:22:33:44:55"
        val forumslader = Forumslader(context, address)

        forumslader.connect(emitter)

        verify { bluetoothDevice.connectGatt(context, false, any(), any()) }
    }
}
