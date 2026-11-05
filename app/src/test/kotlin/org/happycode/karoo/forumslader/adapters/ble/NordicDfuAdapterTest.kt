package org.happycode.karoo.forumslader.adapters.ble

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nordicsemi.android.dfu.DfuBaseService
import no.nordicsemi.android.dfu.DfuProgressListener
import no.nordicsemi.android.dfu.DfuServiceController
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import org.happycode.karoo.forumslader.domain.CommandBus
import org.happycode.karoo.forumslader.domain.DfuState
import org.happycode.karoo.forumslader.domain.ForumsladerCommand
import java.net.URI
import kotlin.time.Duration.Companion.seconds

class NordicDfuAdapterTest : StringSpec({

    lateinit var context: Context
    lateinit var contentResolver: ContentResolver
    lateinit var controller: DfuServiceController
    lateinit var initiator: DfuServiceInitiator
    lateinit var adapter: NordicDfuAdapter
    var deviceAddress: String?
    val testScope = CoroutineScope(Dispatchers.Unconfined)
    var commandJob: Job? = null

    beforeTest {
        context = mockk<Context>(relaxed = true)
        contentResolver = mockk<ContentResolver>(relaxed = true)
        controller = mockk<DfuServiceController>(relaxed = true)
        initiator = mockk<DfuServiceInitiator>(relaxed = true)

        mockkStatic(Uri::class)
        mockkStatic(DfuServiceListenerHelper::class)
        mockkStatic(DfuServiceInitiator::class)
        val mockUri = mockk<Uri>(relaxed = true) {
            every { scheme } returns "file"
        }
        every { Uri.parse(any<String>()) } returns mockUri
        every { context.contentResolver } returns contentResolver
        every { DfuServiceListenerHelper.registerProgressListener(any<Context>(), any<DfuProgressListener>()) } returns Unit
        every { DfuServiceListenerHelper.unregisterProgressListener(any<Context>(), any<DfuProgressListener>()) } returns Unit
        every { DfuServiceInitiator.createDfuNotificationChannel(any<Context>()) } returns Unit
        every { initiator.setKeepBond(any<Boolean>()) } returns initiator
        every { initiator.setForceDfu(any<Boolean>()) } returns initiator
        every { initiator.setForceScanningForNewAddressInLegacyDfu(any<Boolean>()) } returns initiator
        every { initiator.setScanTimeout(any<Long>()) } returns initiator
        every { initiator.setPacketsReceiptNotificationsEnabled(any<Boolean>()) } returns initiator
        every { initiator.setPacketsReceiptNotificationsValue(any<Int>()) } returns initiator
        every { initiator.setZip(any<Uri>()) } returns initiator
        @Suppress("DEPRECATION")
        every { initiator.setBinOrHex(any<Int>(), any<Uri>()) } returns initiator
        every { initiator.start(any<Context>(), any<Class<out DfuBaseService>>()) } returns controller

        deviceAddress = "AA:BB:CC:DD:EE:FF"
        adapter = NordicDfuAdapter(
            context = context,
            deviceAddressProvider = { deviceAddress },
            serviceInitiatorFactory = { initiator },
            scope = testScope,
            handshakeTimeoutMs = 50L,
            notificationChannelCreator = { DfuServiceInitiator.createDfuNotificationChannel(it) }
        )
    }

    afterTest {
        commandJob?.cancel()
        commandJob = null
        unmockkAll()
    }

    fun startListeningForDfuCommand(onCommand: (ForumsladerCommand.EnterDfuMode) -> Unit = { it.onDisconnected.complete(Unit) }): Job {
        val job = testScope.launch {
            CommandBus.commands.collect { command ->
                if (command is ForumsladerCommand.EnterDfuMode) {
                    onCommand(command)
                }
            }
        }
        commandJob = job
        return job
    }

    "should start in Idle state" {
        adapter.state.value shouldBe DfuState.Idle
    }

    "should change state to Error when startUpdate is called without device address" {
        // given
        deviceAddress = null

        // when
        adapter.startUpdate(URI("file:///test.hex"))

        // then
        adapter.state.value shouldBe DfuState.Error("No device connected")
    }

    "should change state to Error when startUpdate is called with blank device address" {
        // given
        deviceAddress = "   "

        // when
        adapter.startUpdate(URI("file:///test.hex"))

        // then
        adapter.state.value shouldBe DfuState.Error("No device connected")
    }

    "should change state to Error and not start DFU when DFU command is not handled" {
        // given - no command listener registered, subscriberCount == 0

        // when
        adapter.startUpdate(URI("file:///path/to/firmware.hex"))

        // then
        adapter.state.value shouldBe DfuState.Error("No device connected")
        verify(exactly = 0) { initiator.start(any(), any()) }
    }

    "should change state to Error and not start DFU when disconnect handshake times out" {
        // given - listener collects but does not complete onDisconnected
        startListeningForDfuCommand { /* do not complete onDisconnected */ }

        // when
        adapter.startUpdate(URI("file:///path/to/firmware.hex"))

        // then
        eventually(1.seconds) {
            adapter.state.value shouldBe DfuState.Error("Failed to enter DFU mode")
        }
        verify(exactly = 0) { initiator.start(any(), any()) }
    }

    "should only start DFU after disconnect happens" {
        // given
        var capturedCommand: ForumsladerCommand.EnterDfuMode? = null
        startListeningForDfuCommand { command ->
            capturedCommand = command
        }

        // when
        adapter.startUpdate(URI("file:///path/to/firmware.hex"))

        // then
        verify(exactly = 0) { initiator.start(any(), any()) }

        // when disconnect happens
        capturedCommand?.onDisconnected?.complete(Unit)

        // then
        verify(exactly = 1) { initiator.start(eq(context), eq(ForumsladerDfuService::class.java)) }
        adapter.state.value shouldBe DfuState.Connecting
    }

    "should configure initiator for legacy DFU new address scanning and PRN" {
        // given
        startListeningForDfuCommand()

        // when
        adapter.startUpdate(URI("file:///path/to/firmware.hex"))

        // then
        verify { initiator.setForceScanningForNewAddressInLegacyDfu(true) }
        verify { initiator.setScanTimeout(DfuServiceInitiator.DEFAULT_SCAN_TIMEOUT) }
        verify { initiator.setPacketsReceiptNotificationsEnabled(true) }
        verify { initiator.setPacketsReceiptNotificationsValue(DfuServiceInitiator.DEFAULT_PRN_VALUE) }
        verify { DfuServiceInitiator.createDfuNotificationChannel(context) }
    }

    "should ignore second startUpdate when update is already running" {
        // given
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///path/to/firmware.hex"))

        // when
        adapter.startUpdate(URI("file:///path/to/firmware.zip"))

        // then
        verify(exactly = 1) { initiator.start(any(), any()) }
    }

    "should configure hex file when URI ends with hex" {
        // given
        startListeningForDfuCommand()

        // when
        adapter.startUpdate(URI("file:///path/to/firmware.hex"))

        // then
        @Suppress("DEPRECATION")
        verify { initiator.setBinOrHex(eq(DfuBaseService.TYPE_APPLICATION), any<Uri>()) }
    }

    "should configure zip file when URI ends with zip" {
        // given
        startListeningForDfuCommand()

        // when
        adapter.startUpdate(URI("file:///path/to/firmware.zip"))

        // then
        verify { initiator.setZip(any<Uri>()) }
    }

    "should configure zip file when content URI mime type is zip" {
        // given
        val contentMockUri = mockk<Uri>(relaxed = true) {
            every { scheme } returns "content"
        }
        every { Uri.parse(eq("content://providers/firmware/456")) } returns contentMockUri
        every { contentResolver.getType(eq(contentMockUri)) } returns "application/zip"
        startListeningForDfuCommand()

        // when
        adapter.startUpdate(URI("content://providers/firmware/456"))

        // then
        verify { initiator.setZip(any<Uri>()) }
    }

    "should configure zip file when content URI display name ends with zip" {
        // given
        val contentMockUri = mockk<Uri>(relaxed = true) {
            every { scheme } returns "content"
        }
        every { Uri.parse(eq("content://providers/firmware/123")) } returns contentMockUri

        val mockCursor = mockk<Cursor>(relaxed = true) {
            every { moveToFirst() } returns true
            every { getColumnIndex(OpenableColumns.DISPLAY_NAME) } returns 0
            every { getString(0) } returns "fw-latest.zip"
        }
        every { contentResolver.query(eq(contentMockUri), any(), any(), any(), any()) } returns mockCursor
        startListeningForDfuCommand()

        // when
        adapter.startUpdate(URI("content://providers/firmware/123"))

        // then
        verify { initiator.setZip(any<Uri>()) }
    }

    "should not crash when URI path is null" {
        // given
        startListeningForDfuCommand()
        val opaqueUri = URI("content:opaque_data")

        // when
        adapter.startUpdate(opaqueUri)

        // then
        adapter.state.value shouldBe DfuState.Connecting
    }

    "should change state to Error and unregister listener when initiator start throws exception" {
        // given
        startListeningForDfuCommand()
        every { initiator.start(any(), any()) } throws SecurityException("Missing connected device permission")

        // when
        adapter.startUpdate(URI("file:///test.hex"))

        // then
        adapter.state.value shouldBe DfuState.Error("Missing connected device permission")
        verify { DfuServiceListenerHelper.unregisterProgressListener(eq(context), any()) }
    }

    "should update state to Connecting when onDeviceConnecting is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onDeviceConnecting("AA:BB:CC:DD:EE:FF")
        adapter.state.value shouldBe DfuState.Connecting
    }

    "should update state to Connecting when onDfuProcessStarting is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onDfuProcessStarting("AA:BB:CC:DD:EE:FF")
        adapter.state.value shouldBe DfuState.Connecting
    }

    "should update state to Connecting when onEnablingDfuMode is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onEnablingDfuMode("AA:BB:CC:DD:EE:FF")
        adapter.state.value shouldBe DfuState.Connecting
    }

    "should update state to Validating when onFirmwareValidating is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onFirmwareValidating("AA:BB:CC:DD:EE:FF")
        adapter.state.value shouldBe DfuState.Validating
    }

    "should update state to Uploading when onProgressChanged is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onProgressChanged("AA:BB:CC:DD:EE:FF", 75, 1.0f, 1.0f, 1, 1)
        adapter.state.value shouldBe DfuState.Uploading(75)
    }

    "should update state to Verifying when onDeviceDisconnecting is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onDeviceDisconnecting("AA:BB:CC:DD:EE:FF")
        adapter.state.value shouldBe DfuState.Verifying
    }

    "should update state to Success and unregister listener when onDfuCompleted is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onDfuCompleted("AA:BB:CC:DD:EE:FF")
        adapter.state.value shouldBe DfuState.Success
        verify { DfuServiceListenerHelper.unregisterProgressListener(eq(context), slot.captured) }
    }

    "should update state to Idle and unregister listener when onDfuAborted is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onDfuAborted("AA:BB:CC:DD:EE:FF")
        adapter.state.value shouldBe DfuState.Idle
        verify { DfuServiceListenerHelper.unregisterProgressListener(eq(context), slot.captured) }
    }

    "should update state to Error and unregister listener when onError is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        slot.captured.onError("AA:BB:CC:DD:EE:FF", 1, 0, "GATT Error")
        adapter.state.value shouldBe DfuState.Error("GATT Error")
        verify { DfuServiceListenerHelper.unregisterProgressListener(eq(context), slot.captured) }
    }

    "should abort controller, unregister listener, and change state to Idle when cancelUpdate is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))

        adapter.cancelUpdate()

        verify { controller.abort() }
        verify { DfuServiceListenerHelper.unregisterProgressListener(eq(context), slot.captured) }
        adapter.state.value shouldBe DfuState.Idle
    }

    "should reset state to Idle when reset is called" {
        val slot = slot<DfuProgressListener>()
        every { DfuServiceListenerHelper.registerProgressListener(eq(context), capture(slot)) } returns Unit
        startListeningForDfuCommand()
        adapter.startUpdate(URI("file:///test.hex"))
        slot.captured.onDfuCompleted("AA:BB:CC:DD:EE:FF")
        adapter.state.value shouldBe DfuState.Success

        adapter.reset()

        adapter.state.value shouldBe DfuState.Idle
    }
})
