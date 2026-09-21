package org.happycode.karoo.forumslader.extension

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
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

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothStateFlowTest : ShouldSpec({

    lateinit var context: Context
    lateinit var bluetoothManager: BluetoothManager
    lateinit var bluetoothAdapter: BluetoothAdapter
    lateinit var receiverSlot: CapturingSlot<BroadcastReceiver>

    beforeEach {
        mockkStatic(ContextCompat::class)
        context = mockk(relaxed = true)
        bluetoothManager = mockk(relaxed = true)
        bluetoothAdapter = mockk(relaxed = true)
        receiverSlot = slot()

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

    afterEach {
        unmockkAll()
    }

    should("emit true when bluetooth adapter is initially enabled") {
        runTest {
            // given
            every { bluetoothAdapter.isEnabled } returns true
            val emissions = mutableListOf<Boolean>()

            // when
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                context.bluetoothStateFlow().toList(emissions)
            }

            // then
            emissions shouldBe listOf(true)
        }
    }

    should("emit false when bluetooth adapter is initially disabled") {
        runTest {
            // given
            every { bluetoothAdapter.isEnabled } returns false
            val emissions = mutableListOf<Boolean>()

            // when
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                context.bluetoothStateFlow().toList(emissions)
            }

            // then
            emissions shouldBe listOf(false)
        }
    }

    should("emit true when ACTION_STATE_CHANGED broadcast is received with STATE_ON") {
        runTest {
            // given
            every { bluetoothAdapter.isEnabled } returns false
            val emissions = mutableListOf<Boolean>()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                context.bluetoothStateFlow().toList(emissions)
            }

            val intent = mockk<Intent>(relaxed = true) {
                every { action } returns BluetoothAdapter.ACTION_STATE_CHANGED
                every { getIntExtra(BluetoothAdapter.EXTRA_STATE, any()) } returns BluetoothAdapter.STATE_ON
            }

            // when
            receiverSlot.captured.onReceive(context, intent)

            // then
            emissions shouldBe listOf(false, true)
        }
    }

    should("emit false when ACTION_STATE_CHANGED broadcast is received with STATE_OFF") {
        runTest {
            // given
            every { bluetoothAdapter.isEnabled } returns true
            val emissions = mutableListOf<Boolean>()

            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                context.bluetoothStateFlow().toList(emissions)
            }

            val intent = mockk<Intent>(relaxed = true) {
                every { action } returns BluetoothAdapter.ACTION_STATE_CHANGED
                every { getIntExtra(BluetoothAdapter.EXTRA_STATE, any()) } returns BluetoothAdapter.STATE_OFF
            }

            // when
            receiverSlot.captured.onReceive(context, intent)

            // then
            emissions shouldBe listOf(true, false)
        }
    }

    should("unregister receiver when flow collection is cancelled") {
        runTest {
            // given
            every { bluetoothAdapter.isEnabled } returns true

            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                context.bluetoothStateFlow().toList()
            }

            // when
            job.cancel()

            // then
            verify { context.unregisterReceiver(receiverSlot.captured) }
        }
    }
})
