package org.happycode.karoo.forumslader.extension

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothStateFlowTest {

    private lateinit var context: Context
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val receiverSlot = slot<BroadcastReceiver>()

    @BeforeEach
    fun setUp() {
        mockkStatic(ContextCompat::class)
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        bluetoothAdapter = mockk(relaxed = true)

        every { context.getSystemService(BluetoothManager::class.java) } returns bluetoothManager
        every { bluetoothManager.adapter } returns bluetoothAdapter
        every {
            ContextCompat.registerReceiver(
                any(),
                capture(receiverSlot),
                any(),
                any()
            )
        } returns null
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should emit true when bluetooth adapter is initially enabled`() = runTest {
        // given
        every { bluetoothAdapter.isEnabled } returns true
        val emissions = mutableListOf<Boolean>()

        // when
        val job = launch(UnconfinedTestDispatcher()) {
            context.bluetoothStateFlow().toList(emissions)
        }

        // then
        assertEquals(listOf(true), emissions)
        job.cancel()
    }

    @Test
    fun `should emit false when bluetooth adapter is initially disabled`() = runTest {
        // given
        every { bluetoothAdapter.isEnabled } returns false
        val emissions = mutableListOf<Boolean>()

        // when
        val job = launch(UnconfinedTestDispatcher()) {
            context.bluetoothStateFlow().toList(emissions)
        }

        // then
        assertEquals(listOf(false), emissions)
        job.cancel()
    }

    @Test
    fun `should emit true when ACTION_STATE_CHANGED broadcast is received with STATE_ON`() = runTest {
        // given
        every { bluetoothAdapter.isEnabled } returns false
        val emissions = mutableListOf<Boolean>()

        val job = launch(UnconfinedTestDispatcher()) {
            context.bluetoothStateFlow().toList(emissions)
        }

        val intent = mockk<Intent>(relaxed = true) {
            every { action } returns BluetoothAdapter.ACTION_STATE_CHANGED
            every { getIntExtra(BluetoothAdapter.EXTRA_STATE, any()) } returns BluetoothAdapter.STATE_ON
        }

        // when
        receiverSlot.captured.onReceive(context, intent)

        // then
        assertEquals(listOf(false, true), emissions)
        job.cancel()
    }

    @Test
    fun `should emit false when ACTION_STATE_CHANGED broadcast is received with STATE_OFF`() = runTest {
        // given
        every { bluetoothAdapter.isEnabled } returns true
        val emissions = mutableListOf<Boolean>()

        val job = launch(UnconfinedTestDispatcher()) {
            context.bluetoothStateFlow().toList(emissions)
        }

        val intent = mockk<Intent>(relaxed = true) {
            every { action } returns BluetoothAdapter.ACTION_STATE_CHANGED
            every { getIntExtra(BluetoothAdapter.EXTRA_STATE, any()) } returns BluetoothAdapter.STATE_OFF
        }

        // when
        receiverSlot.captured.onReceive(context, intent)

        // then
        assertEquals(listOf(true, false), emissions)
        job.cancel()
    }

    @Test
    fun `should unregister receiver when flow collection is cancelled`() = runTest {
        // given
        every { bluetoothAdapter.isEnabled } returns true
        val emissions = mutableListOf<Boolean>()

        val job = launch(UnconfinedTestDispatcher()) {
            context.bluetoothStateFlow().toList(emissions)
        }

        // when
        job.cancel()

        // then
        verify { context.unregisterReceiver(receiverSlot.captured) }
    }
}
