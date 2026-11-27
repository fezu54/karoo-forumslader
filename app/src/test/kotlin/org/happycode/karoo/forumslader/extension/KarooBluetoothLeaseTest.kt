package org.happycode.karoo.forumslader.extension

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.ReleaseBluetooth
import io.hammerhead.karooext.models.RequestBluetooth
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class KarooBluetoothLeaseTest : ShouldSpec({

    lateinit var karooSystem: KarooSystemService
    val resourceId = "karoo-forumslader:test-lease"

    beforeEach {
        mockkStatic(Log::class)
        every { Log.i(any<String>(), any<String>()) } returns 0
        karooSystem = mockk(relaxed = true)
    }

    afterEach {
        unmockkAll()
    }

    should("request Bluetooth lease when connected and release it on release") {
        // given
        every { karooSystem.connect(any()) } answers {
            firstArg<(Boolean) -> Unit>().invoke(true)
        }
        val lease = KarooBluetoothLease(karooSystem, resourceId)

        // when
        lease.acquire()

        // then
        verify(exactly = 1) { karooSystem.dispatch(RequestBluetooth(resourceId)) }

        // when
        lease.release()

        // then
        verify(exactly = 1) { karooSystem.dispatch(ReleaseBluetooth(resourceId)) }
    }

    should("not request or release Bluetooth lease when connect reports false") {
        // given
        every { karooSystem.connect(any()) } answers {
            firstArg<(Boolean) -> Unit>().invoke(false)
        }
        val lease = KarooBluetoothLease(karooSystem, resourceId)

        // when
        lease.acquire()
        lease.release()

        // then
        verify(exactly = 0) { karooSystem.dispatch(ofType<RequestBluetooth>()) }
        verify(exactly = 0) { karooSystem.dispatch(ofType<ReleaseBluetooth>()) }
    }

    should("not request Bluetooth lease when connect completes after release") {
        // given
        val connectCallbackSlot = slot<(Boolean) -> Unit>()
        every { karooSystem.connect(capture(connectCallbackSlot)) } returns Unit
        val lease = KarooBluetoothLease(karooSystem, resourceId)

        // when
        lease.acquire()
        lease.release()
        connectCallbackSlot.captured(true)

        // then
        verify(exactly = 0) { karooSystem.dispatch(ofType<RequestBluetooth>()) }
        verify(exactly = 0) { karooSystem.dispatch(ofType<ReleaseBluetooth>()) }
    }

    should("release Bluetooth lease only once when release is called multiple times") {
        // given
        every { karooSystem.connect(any()) } answers {
            firstArg<(Boolean) -> Unit>().invoke(true)
        }
        val lease = KarooBluetoothLease(karooSystem, resourceId)
        lease.acquire()

        // when
        lease.release()
        lease.release()

        // then
        verify(exactly = 1) { karooSystem.dispatch(ReleaseBluetooth(resourceId)) }
    }

    should("serialize concurrent connect callback and release so lease is never leaked") {
        // given
        val connectCallbackSlot = slot<(Boolean) -> Unit>()
        every { karooSystem.connect(capture(connectCallbackSlot)) } returns Unit
        val inRequestLatch = CountDownLatch(1)
        val allowRequestFinishLatch = CountDownLatch(1)
        every { karooSystem.dispatch(RequestBluetooth(resourceId)) } answers {
            inRequestLatch.countDown()
            allowRequestFinishLatch.await(2, TimeUnit.SECONDS)
            true
        }
        val lease = KarooBluetoothLease(karooSystem, resourceId)
        lease.acquire()

        // when
        val connectThread = thread(start = true) {
            connectCallbackSlot.captured(true)
        }
        inRequestLatch.await(2, TimeUnit.SECONDS) shouldBe true

        val releaseThread = thread(start = true) {
            lease.release()
        }
        val deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (releaseThread.state != Thread.State.BLOCKED && System.nanoTime() < deadlineNanos) {
            Thread.yield()
        }
        releaseThread.state shouldBe Thread.State.BLOCKED

        allowRequestFinishLatch.countDown()
        connectThread.join(2_000)
        releaseThread.join(2_000)

        // then
        connectThread.isAlive shouldBe false
        releaseThread.isAlive shouldBe false
        verifyOrder {
            karooSystem.dispatch(RequestBluetooth(resourceId))
            karooSystem.dispatch(ReleaseBluetooth(resourceId))
        }
    }
})
