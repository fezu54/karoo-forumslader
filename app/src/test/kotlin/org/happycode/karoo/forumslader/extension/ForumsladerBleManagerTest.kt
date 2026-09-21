package org.happycode.karoo.forumslader.extension

import android.util.Log
import com.juul.kable.Advertisement
import com.juul.kable.DiscoveredCharacteristic
import com.juul.kable.DiscoveredService
import com.juul.kable.Peripheral
import com.juul.kable.Scanner
import com.juul.kable.State
import com.juul.kable.WriteType
import io.hammerhead.karooext.models.ConnectionStatus
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_RX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V5
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6_ALT
import org.happycode.karoo.forumslader.model.ForumsladerVersion
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ForumsladerBleManagerTest : ShouldSpec({

    val address = "00:11:22:33:44:55"

    lateinit var scanner: Scanner<Advertisement>
    lateinit var peripheral: Peripheral
    lateinit var advertisementsFlow: MutableSharedFlow<Advertisement>
    lateinit var peripheralStateFlow: MutableStateFlow<State>
    lateinit var servicesFlow: MutableStateFlow<List<DiscoveredService>?>
    lateinit var observeFlow: MutableSharedFlow<ByteArray>

    beforeEach {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>(), any<Throwable>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        advertisementsFlow = MutableSharedFlow(replay = 1)
        peripheralStateFlow = MutableStateFlow(mockk<State.Disconnected>())
        servicesFlow = MutableStateFlow(null)
        observeFlow = MutableSharedFlow(replay = 1, extraBufferCapacity = 64)

        scanner = mockk {
            every { advertisements } returns advertisementsFlow
        }

        peripheral = mockk(relaxed = true) {
            every { state } returns peripheralStateFlow
            every { services } returns servicesFlow
            every { observe(any(), any()) } returns observeFlow
            every { observe(any()) } returns observeFlow
        }
    }

    afterEach {
        unmockkAll()
    }

    fun mockService(
        serviceUuid: UUID,
        characteristics: List<DiscoveredCharacteristic> = emptyList()
    ): DiscoveredService {
        val uuidString = serviceUuid.toString()
        return mockk {
            every { this@mockk.serviceUuid.toString() } returns uuidString
            every { this@mockk.characteristics } returns characteristics
        }
    }

    fun mockCharacteristic(characteristicUuid: UUID): DiscoveredCharacteristic {
        val uuidString = characteristicUuid.toString()
        return mockk {
            every { this@mockk.characteristicUuid.toString() } returns uuidString
        }
    }

    should("start in disconnected state") {
        runTest {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })

            // then
            manager.connectionState.value shouldBe ConnectionStatus.DISCONNECTED
        }
    }

    should("transition to searching state when manager is started") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })

            // when
            manager.start()

            // then
            manager.connectionState.value shouldBe ConnectionStatus.SEARCHING
        }
    }

    should("ignore subsequent start calls if manager is already running") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            manager.start()

            // when
            manager.start()

            // then
            manager.connectionState.value shouldBe ConnectionStatus.SEARCHING
        }
    }

    should("connect and detect V6 version when primary service UUID is discovered") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            val detectedVersions = mutableListOf<ForumsladerVersion>()
            backgroundScope.launch { manager.versionDetected.toList(detectedVersions) }

            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()

            val serviceV6 = mockService(SERVICE_UUID_V6)

            // when
            servicesFlow.value = listOf(serviceV6)

            // then
            detectedVersions shouldBe listOf(ForumsladerVersion.V6)
            manager.connectionState.value shouldBe ConnectionStatus.CONNECTED
        }
    }

    should("connect and detect V6 version when alternative service UUID is discovered") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            val detectedVersions = mutableListOf<ForumsladerVersion>()
            backgroundScope.launch { manager.versionDetected.toList(detectedVersions) }

            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()

            val serviceV6Alt = mockService(SERVICE_UUID_V6_ALT)

            // when
            servicesFlow.value = listOf(serviceV6Alt)

            // then
            detectedVersions shouldBe listOf(ForumsladerVersion.V6)
            manager.connectionState.value shouldBe ConnectionStatus.CONNECTED
        }
    }

    should("connect and detect V5 version when V5 service UUID is discovered") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            val detectedVersions = mutableListOf<ForumsladerVersion>()
            backgroundScope.launch { manager.versionDetected.toList(detectedVersions) }

            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()

            val serviceV5 = mockService(SERVICE_UUID_V5)

            // when
            servicesFlow.value = listOf(serviceV5)

            // then
            detectedVersions shouldBe listOf(ForumsladerVersion.V5)
            manager.connectionState.value shouldBe ConnectionStatus.CONNECTED
        }
    }

    should("enable notifications and receive incoming data when RX characteristic is configured") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            backgroundScope.launch { manager.versionDetected.collect {} }

            var notificationsEnabledCount = 0
            backgroundScope.launch { manager.notificationsEnabled.collect { notificationsEnabledCount++ } }

            val receivedData = mutableListOf<ByteArray>()
            backgroundScope.launch { manager.incomingData.toList(receivedData) }

            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()

            val rxChar = mockCharacteristic(CHARACTERISTIC_UART_RX_V6)
            val serviceV6 = mockService(SERVICE_UUID_V6, listOf(rxChar))

            // when
            servicesFlow.value = listOf(serviceV6)
            val incomingPacket = byteArrayOf(10, 20, 30)
            observeFlow.emit(incomingPacket)

            // then
            notificationsEnabledCount shouldBe 1
            receivedData.map { it.toList() } shouldBe listOf(incomingPacket.toList())
        }
    }

    should("write command through peripheral when command is issued") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            manager.start()

            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()

            val txChar = mockCharacteristic(CHARACTERISTIC_UART_TX_V6)
            val serviceV6 = mockService(SERVICE_UUID_V6, listOf(txChar))
            servicesFlow.value = listOf(serviceV6)

            val command = byteArrayOf(1, 2, 3)

            // when
            manager.writeCommand(command)

            // then
            coVerify { peripheral.write(any(), command, WriteType.WithoutResponse) }
        }
    }

    should("cleanup session when stopped") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()

            coEvery { peripheral.disconnect() } returns Unit

            // when
            manager.stop()

            // then
            manager.connectionState.value shouldBe ConnectionStatus.DISCONNECTED
            coVerify { peripheral.disconnect() }
        }
    }

    should("update connection state on peripheral state changes") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            manager.start()
            advertisementsFlow.emit(mockk())

            // when
            peripheralStateFlow.value = mockk<State.Connecting>()

            // then
            manager.connectionState.value shouldBe ConnectionStatus.SEARCHING

            // when
            peripheralStateFlow.value = mockk<State.Connected>()

            // then
            manager.connectionState.value shouldBe ConnectionStatus.CONNECTED

            // when
            peripheralStateFlow.value = mockk<State.Disconnected>()

            // then
            manager.connectionState.value shouldBe ConnectionStatus.DISCONNECTED
        }
    }

    should("not write command to peripheral when peripheral is disconnected") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            manager.start()

            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Disconnected>()

            val command = byteArrayOf(1, 2, 3)

            // when
            manager.writeCommand(command)

            // then
            coVerify(exactly = 0) { peripheral.write(any(), any(), any()) }
        }
    }

    should("not detect version when discovered services do not match Forumslader profile") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val manager = ForumsladerBleManager(address, scope = backgroundScope, scanner, peripheralFactory = { peripheral })
            val detectedVersions = mutableListOf<ForumsladerVersion>()
            backgroundScope.launch { manager.versionDetected.toList(detectedVersions) }

            manager.start()
            advertisementsFlow.emit(mockk())
            peripheralStateFlow.value = mockk<State.Connected>()

            val unknownService = mockService(UUID.randomUUID())

            // when
            servicesFlow.value = listOf(unknownService)

            // then
            detectedVersions shouldBe emptyList()
            manager.connectionState.value shouldBe ConnectionStatus.CONNECTED
        }
    }
})
