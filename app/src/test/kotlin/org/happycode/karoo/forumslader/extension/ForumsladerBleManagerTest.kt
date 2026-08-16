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
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.CHARACTERISTIC_UART_TX_V6
import org.happycode.karoo.forumslader.model.ForumsladerBleProfile.SERVICE_UUID_V6
import org.happycode.karoo.forumslader.model.ForumsladerVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForumsladerBleManagerTest {

    private val address = "00:11:22:33:44:55"
    
    private lateinit var scanner: Scanner<Advertisement>
    private lateinit var peripheral: Peripheral
    
    private val advertisementsFlow = MutableSharedFlow<Advertisement>(replay = 1)
    private val peripheralStateFlow = MutableStateFlow<State>(mockk<State.Disconnected>())
    private val servicesFlow = MutableStateFlow<List<DiscoveredService>?>(null)

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        scanner = mockk {
            every { advertisements } returns advertisementsFlow
        }
        
        peripheral = mockk(relaxed = true) {
            every { state } returns peripheralStateFlow
            every { services } returns servicesFlow
            every { observe(any()) } returns MutableSharedFlow<ByteArray>()
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `should start in disconnected state`() = runTest {
        // given
        val manager = ForumsladerBleManager(address, this, scanner, { peripheral })

        // then
        assertEquals(ConnectionStatus.DISCONNECTED, manager.connectionState.value)
    }

    @Test
    fun `should transition to searching when started and waiting for scan`() = runTest(UnconfinedTestDispatcher()) {
        // given
        val manager = ForumsladerBleManager(address, this, scanner, { peripheral })

        // when
        manager.start()

        // then
        assertEquals(ConnectionStatus.SEARCHING, manager.connectionState.value)

        manager.stop()
    }

    @Test
    fun `should connect and detect V6 version`() = runTest(UnconfinedTestDispatcher()) {
        // given
        val manager = ForumsladerBleManager(address, this, scanner, { peripheral })
        manager.start()
        
        advertisementsFlow.emit(mockk())
        peripheralStateFlow.value = mockk<State.Connected>()

        val serviceV6 = mockk<DiscoveredService> {
            every { serviceUuid.toString() } returns SERVICE_UUID_V6.toString()
            every { characteristics } returns emptyList()
        }
        
        val versionDeferred = async { manager.versionDetected.first() }

        // when
        servicesFlow.value = listOf(serviceV6)

        // then
        assertEquals(ForumsladerVersion.V6, versionDeferred.await())
        assertEquals(ConnectionStatus.CONNECTED, manager.connectionState.value)
        
        manager.stop()
    }

    @Test
    fun `should write command through peripheral`() = runTest(UnconfinedTestDispatcher()) {
        // given
        val manager = ForumsladerBleManager(address, this, scanner, { peripheral })
        manager.start()
        advertisementsFlow.emit(mockk())
        peripheralStateFlow.value = mockk<State.Connected>()
        
        val characteristicV6 = mockk<DiscoveredCharacteristic> {
            every { characteristicUuid.toString() } returns CHARACTERISTIC_UART_TX_V6.toString()
        }
        val serviceV6 = mockk<DiscoveredService> {
            every { serviceUuid.toString() } returns SERVICE_UUID_V6.toString()
            every { characteristics } returns listOf(characteristicV6)
        }
        servicesFlow.value = listOf(serviceV6)

        val command = byteArrayOf(1, 2, 3)

        // when
        manager.writeCommand(command)

        // then
        coVerify { peripheral.write(any(), command, WriteType.WithoutResponse) }
        
        manager.stop()
    }

    @Test
    fun `should cleanup on stop`() = runTest(UnconfinedTestDispatcher()) {
        // given
        val manager = ForumsladerBleManager(address, this, scanner, { peripheral })
        manager.start()
        advertisementsFlow.emit(mockk())
        peripheralStateFlow.value = mockk<State.Connected>()

        coEvery { peripheral.disconnect() } returns Unit

        // when
        manager.stop()

        // then
        assertEquals(ConnectionStatus.DISCONNECTED, manager.connectionState.value)
        coVerify { peripheral.disconnect() }
    }
}
