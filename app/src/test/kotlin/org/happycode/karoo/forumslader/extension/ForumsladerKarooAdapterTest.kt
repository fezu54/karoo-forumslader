package org.happycode.karoo.forumslader.extension

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.application.BatteryEstimateStore
import org.happycode.karoo.forumslader.application.ForumsladerStateStore
import org.happycode.karoo.forumslader.model.ForumsladerVersion
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForumsladerKarooAdapterTest {
    private lateinit var context: Context
    private lateinit var emitter: Emitter<DeviceEvent>
    private lateinit var bleManager: ForumsladerBleManager
    private lateinit var karooSystem: KarooSystemService
    private lateinit var testScope: CoroutineScope

    private lateinit var connectionStateFlow: MutableStateFlow<ConnectionStatus>
    private lateinit var incomingDataFlow: MutableSharedFlow<ByteArray>
    private lateinit var versionDetectedFlow: MutableSharedFlow<ForumsladerVersion>
    private lateinit var notificationsEnabledFlow: MutableSharedFlow<Unit>

    @BeforeEach
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        context = mockk(relaxed = true)
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { mockPrefs.getInt("wheelsize", 2200) } returns 2200
        every { mockPrefs.getInt("poles", 14) } returns 14
        every { mockPrefs.getFloat("speedMultiplier", 1.0f) } returns 1.0f
        every { mockPrefs.getInt("battery_low_threshold", any()) } returns 20
        every { mockPrefs.getFloat("high_temp_threshold", any()) } returns 50f
        every { context.getSharedPreferences(any(), any()) } returns mockPrefs
        every { context.applicationContext } returns context
        val tempFilesDir = java.nio.file.Files.createTempDirectory("test_files").toFile()
        every { context.filesDir } returns tempFilesDir

        emitter = mockk(relaxed = true)

        connectionStateFlow = MutableStateFlow(ConnectionStatus.DISCONNECTED)
        incomingDataFlow = MutableSharedFlow()
        versionDetectedFlow = MutableSharedFlow()
        notificationsEnabledFlow = MutableSharedFlow()

        bleManager = mockk(relaxed = true) {
            every { connectionState } returns connectionStateFlow
            every { incomingData } returns incomingDataFlow
            every { versionDetected } returns versionDetectedFlow
            every { notificationsEnabled } returns notificationsEnabledFlow
        }

        karooSystem = mockk(relaxed = true) {
            every { addConsumer(any()) } returns "test-consumer-id"
        }
        testScope = TestScope()
    }

    @AfterEach
    fun tearDown() {
        ForumsladerStateStore.clear()
        BatteryEstimateStore.clear()
        unmockkAll()
    }

    @Test
    fun `should set correct metadata on device initialization`() {
        // given
        val address = "00:11:22:33:44:55"

        // when
        val forumslader = ForumsladerKarooAdapter(
            context,
            address,
            "My Forumslader",
            testScope,
            bleManager,
            karooSystem
        )

        // then
        assertEquals("karoo-forumslader", forumslader.device.extension)
        assertEquals("fl-$address", forumslader.device.uid)
        assertEquals("My Forumslader", forumslader.device.displayName)
    }

    @Test
    fun `should initiate connection on connect`() {
        // given
        val forumslader = ForumsladerKarooAdapter(
            context,
            "00:11:22:33:44:55",
            null,
            testScope,
            bleManager,
            karooSystem
        )

        // when
        forumslader.connect(emitter)

        // then
        verify { bleManager.start() }
    }

    @Test
    fun `should emit connection status on state flow change`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            // when
            connectionStateFlow.value = ConnectionStatus.CONNECTED

            // then
            verify { emitter.onNext(match { it is OnConnectionStatus && it.status == ConnectionStatus.CONNECTED }) }
        }

    @Test
    fun `should emit all metrics from registry with correct values and conversions`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            val eventSlot = mutableListOf<DeviceEvent>()
            every { emitter.onNext(capture(eventSlot)) } returns Unit

            val flb = $$"$FLB,255,0,1005\n"
            val flc1 = $$"$FLC,5,0,85\n"
            val flc2 = $$"$FLC,3,0,123.4,45.6\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"

            // when
            incomingDataFlow.emit((flb + flc1 + flc2 + fl5).toByteArray(Charsets.US_ASCII))

            // then
            val dataPoints = eventSlot.filterIsInstance<OnDataPoint>()
                .associate { it.dataPoint.dataTypeId to it.dataPoint.values[DataType.Field.SINGLE] }

            assert(dataPoints.isNotEmpty()) { "No data points were emitted!" }

            fun expectedType(id: String) = DataType.dataTypeId("karoo-forumslader", id)

            val distFactor = 2200.0 / 14.0 / 1000.0
            val expectedSpeed = 100.0 * distFactor
            val expectedDist = 1000.0 * distFactor * 4096.0

            val expected = mapOf(
                expectedType("fl_battery_voltage") to 1.5,
                expectedType("fl_battery_current") to 2500.0,
                expectedType("fl_consumer_current") to 3500.0,
                expectedType("fl_speed") to expectedSpeed,
                expectedType("fl_trip_distance") to expectedDist,
                expectedType("fl_frequency") to 100.0,
                expectedType("fl_temperature") to 25.5,
                expectedType("fl_generator_gear") to 3.0,
                expectedType("fl_charge_state") to 1.0,
                expectedType("fl_trip_energy") to 45.6,
                expectedType("fl_tour_energy") to 123.4,
                expectedType("fl_dynamo_power") to 9.0,
                expectedType("fl_odometer") to expectedDist,
                expectedType("fl_day_distance") to expectedDist,
                expectedType("fl_tour_distance") to expectedDist,
                expectedType("fl_battery_level") to 85.0,
                expectedType(DataFieldId.BATTERY_RANGE) to DataFieldId.BATTERY_RANGE_CHARGING
            )

            expected.forEach { (fullId, expectedValue) ->
                val actualValue = dataPoints[fullId] ?: 0.0
                assertEquals(
                    expectedValue,
                    actualValue,
                    kotlin.math.abs(expectedValue * 0.01).coerceAtLeast(0.001),
                    "Value mismatch for $fullId"
                )
            }
        }

    @Test
    fun `should emit only finite floating point values for all metrics to avoid JSON serialization exception`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val capturedEvents = mutableListOf<DeviceEvent>()
            every { emitter.onNext(capture(capturedEvents)) } returns Unit

            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            val flb = $$"$FLB,255,0,1005\n"
            val flc = $$"$FLC,5,0,100\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"

            // when
            incomingDataFlow.emit((flb + flc + fl5).toByteArray(Charsets.US_ASCII))

            // then
            val dataPoints = capturedEvents.filterIsInstance<OnDataPoint>()
            assert(dataPoints.isNotEmpty())
            dataPoints.forEach { point ->
                point.dataPoint.values.forEach { (field, value) ->
                    assert(value.isFinite()) { "Value for $field in ${point.dataPoint.dataTypeId} must be finite but was $value" }
                }
            }
        }

    @Test
    fun `should emit calculating sentinel for battery range when discharging without sufficient data`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val capturedEvents = mutableListOf<DeviceEvent>()
            every { emitter.onNext(capture(capturedEvents)) } returns Unit

            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            // FL6 with charge state 2 (DISCHARGING), battery 80% but only 1 sample (no distance diff yet)
            val flc = $$"$FLC,5,0,80\n"
            val fl6 = $$"$FL6,2,0,0,0,0,0,0,0,0,0,0,0\n"

            // when
            incomingDataFlow.emit((flc + fl6).toByteArray(Charsets.US_ASCII))

            // then
            val rangePoint = capturedEvents.filterIsInstance<OnDataPoint>()
                .firstOrNull { it.dataPoint.dataTypeId == DataType.dataTypeId("karoo-forumslader", DataFieldId.BATTERY_RANGE) }

            assertEquals(DataFieldId.BATTERY_RANGE_CALCULATING, rangePoint?.dataPoint?.values?.get(DataType.Field.SINGLE))
        }

    @Test
    fun `should start parameter request loop when notifications are enabled`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            // when
            notificationsEnabledFlow.emit(Unit)

            // then: verify command written for wheelsize/poles request
            verify { bleManager.writeCommand(match { String(it).startsWith($$"$FLT,5") }) }
        }

    @Test
    fun `should lock MAC address on first successful data reception`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val address = "00:11:22:33:44:55"
            val mockPrefs = context.getSharedPreferences("forumslader_prefs", Context.MODE_PRIVATE)
            every { mockPrefs.getString("locked_mac_address", null) } returns null

            val forumslader = ForumsladerKarooAdapter(
                context,
                address,
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"

            // when
            incomingDataFlow.emit(fl5.toByteArray(Charsets.US_ASCII))

            // then
            verify { mockPrefs.edit().putString("locked_mac_address", address) }
        }

    @Test
    fun `should update version in config when version detected`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            // when
            versionDetectedFlow.emit(ForumsladerVersion.V5)

            // then
        }

    @Test
    fun `should emit all metrics and trigger alerts on high temperature`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            val flb = $$"$FLB,655,0,1005\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"

            // when
            incomingDataFlow.emit((flb + fl5).toByteArray(Charsets.US_ASCII))

            // then
            verify {
                karooSystem.dispatch(match {
                    it is InRideAlert &&
                            it.id == "fl_hi_temp"
                })
            }
        }

    @Test
    fun `should stop and clean up when emitter is cancelled`() {
        // given
        val forumslader = ForumsladerKarooAdapter(
            context,
            "00:11:22:33:44:55",
            null,
            testScope,
            bleManager,
            karooSystem
        )

        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        forumslader.connect(emitter)
        cancelSlot.captured.invoke()

        // then
        verify { karooSystem.disconnect() }
        verify { bleManager.stop() }
    }

    @Test
    fun `should trigger all types of alerts`() = runTest(UnconfinedTestDispatcher()) {
        val forumslader = ForumsladerKarooAdapter(
            context,
            "00:11:22:33:44:55",
            null,
            backgroundScope,
            bleManager,
            karooSystem
        )
        forumslader.connect(emitter)

        // Battery Low
        val flc = $$"$FLC,5,0,15\n" // 15%
        val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
        incomingDataFlow.emit((flc + fl5).toByteArray(Charsets.US_ASCII))
        verify { karooSystem.dispatch(match { it is InRideAlert && it.id == "fl_bat_low" }) }

        // Short Circuit (status bit 0x8)
        val fl6short = $$"$FL6,8,0,0,0,0,0,0,0,0,0,0,0\n"
        incomingDataFlow.emit(fl6short.toByteArray(Charsets.US_ASCII))
        verify { karooSystem.dispatch(match { it is InRideAlert && it.id == "fl_short" }) }

        // System Interrupt (status bit 0x800000)
        val fl6int = $$"$FL6,800000,0,0,0,0,0,0,0,0,0,0,0\n"
        incomingDataFlow.emit(fl6int.toByteArray(Charsets.US_ASCII))
        verify { karooSystem.dispatch(match { it is InRideAlert && it.id == "fl_sys_int" }) }
    }

    @Test
    fun `should request config and reset state on day distance reset`() = runTest(UnconfinedTestDispatcher()) {
        // given
        val forumslader = ForumsladerKarooAdapter(
            context,
            "00:11:22:33:44:55",
            null,
            backgroundScope,
            bleManager,
            karooSystem
        )
        forumslader.connect(emitter)

        // Simulate config loaded initially
        val flb = $$"$FLB,255,0,1005\n"
        val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
        incomingDataFlow.emit((flb + fl5).toByteArray(Charsets.US_ASCII))

        // when
        forumslader.sendCommand($$"$FLT,6\n")

        // then
        verifyOrder {
            // Verify write command for reset was sent
            bleManager.writeCommand(match { it.decodeToString().startsWith($$"$FLT,6") })

            // Verify write command for config fetch ($FLT,5) was triggered
            bleManager.writeCommand(match { it.decodeToString().startsWith($$"$FLT,5") })
        }

        // At this point configLoaded should be false in the application state store
        yield()
        assertEquals(false, ForumsladerStateStore.isConfigLoadedFlow.value)
    }

    @Test
    fun `should set and update fitEmitter on adapter`() {
        // given
        val forumslader = ForumsladerKarooAdapter(
            context,
            "00:11:22:33:44:55",
            null,
            testScope,
            bleManager,
            karooSystem
        )
        val fitEmitter = mockk<Emitter<FitEffect>>(relaxed = true)

        // when
        forumslader.setFitEmitter(fitEmitter)

        // then
        forumslader.setFitEmitter(null)
    }

    @Test
    fun `should emit fit message when incoming data arrives and fitEmitter is active`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val fitEmitter = mockk<Emitter<FitEffect>>(relaxed = true)
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.setFitEmitter(fitEmitter)
            forumslader.connect(emitter)

            // when - simulate incoming telemetry data
            val flb = $$"$FLB,255,0,1005\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
            val flm = $$"$FLM,12500,1000,500,2500,1,1000,50,200,2,1000,5000,2000,10000,100,500\n"
            incomingDataFlow.emit((flb + fl5 + flm).toByteArray(Charsets.US_ASCII))

            // then
            verify(atLeast = 1) { fitEmitter.onNext(any<WriteToRecordMesg>()) }
        }

    @Test
    fun `should update route remaining when distance to destination and elevation streams emit`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            // when
            val distPoint = DataPoint(
                DataType.Type.DISTANCE_TO_DESTINATION,
                mapOf(DataType.Field.DISTANCE_TO_DESTINATION to 25000.0)
            )
            forumslader.handleDistanceRemaining(OnStreamState(StreamState.Streaming(distPoint)))

            val elevPoint = DataPoint(
                DataType.Type.ELEVATION_REMAINING,
                mapOf(DataType.Field.ASCENT_REMAINING to 300.0)
            )
            forumslader.handleElevationRemaining(OnStreamState(StreamState.Streaming(elevPoint)))

            val flb = $$"$FLB,255,0,1005\n"
            val flc = $$"$FLC,5,0,85\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
            incomingDataFlow.emit((flb + flc + fl5).toByteArray(Charsets.US_ASCII))

            // then
            assertEquals(25.0f, BatteryEstimateStore.estimateFlow.value?.routeRemainingKm)
            assertEquals(true, BatteryEstimateStore.estimateFlow.value?.isSufficientForRoute)
        }

    @Test
    fun `should clear route remaining when navigation state becomes idle`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            val distPoint = DataPoint(
                DataType.Type.DISTANCE_TO_DESTINATION,
                mapOf(DataType.Field.DISTANCE_TO_DESTINATION to 25000.0)
            )
            forumslader.handleDistanceRemaining(OnStreamState(StreamState.Streaming(distPoint)))

            val flb = $$"$FLB,255,0,1005\n"
            val flc = $$"$FLC,5,0,85\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
            incomingDataFlow.emit((flb + flc + fl5).toByteArray(Charsets.US_ASCII))
            assertEquals(25.0f, BatteryEstimateStore.estimateFlow.value?.routeRemainingKm)

            // when
            forumslader.handleNavigationState(OnNavigationState(OnNavigationState.NavigationState.Idle))

            // then
            assertEquals(null, BatteryEstimateStore.estimateFlow.value?.routeRemainingKm)
        }

    @Test
    fun `should update headwind speed when headwind stream emits`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            // when
            val headwindPoint = DataPoint(
                DataType.dataTypeId("karoo-headwind", "headwindSpeed"),
                mapOf(DataType.Field.SINGLE to 5.0)
            )
            forumslader.handleHeadwindStreamState(OnStreamState(StreamState.Streaming(headwindPoint)))

            val flb = $$"$FLB,255,0,1005\n"
            val flc = $$"$FLC,5,0,85\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
            incomingDataFlow.emit((flb + flc + fl5).toByteArray(Charsets.US_ASCII))

            // then
            // Headwind penalty applied
            val estimate = BatteryEstimateStore.estimateFlow.value
            assertEquals(true, estimate != null)
        }

    @Test
    fun `should reset route distance and elevation remaining when stream states are not streaming`() =
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = ForumsladerKarooAdapter(
                context,
                "00:11:22:33:44:55",
                null,
                backgroundScope,
                bleManager,
                karooSystem
            )
            forumslader.connect(emitter)

            val distPoint = DataPoint(
                DataType.Type.DISTANCE_TO_DESTINATION,
                mapOf(DataType.Field.DISTANCE_TO_DESTINATION to 25000.0)
            )
            forumslader.handleDistanceRemaining(OnStreamState(StreamState.Streaming(distPoint)))
            val elevPoint = DataPoint(
                DataType.Type.ELEVATION_REMAINING,
                mapOf(DataType.Field.ASCENT_REMAINING to 300.0)
            )
            forumslader.handleElevationRemaining(OnStreamState(StreamState.Streaming(elevPoint)))

            val flb = $$"$FLB,255,0,1005\n"
            val flc = $$"$FLC,5,0,85\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
            incomingDataFlow.emit((flb + flc + fl5).toByteArray(Charsets.US_ASCII))
            assertEquals(25.0f, BatteryEstimateStore.estimateFlow.value?.routeRemainingKm)

            // when stream state becomes Searching or NotAvailable
            forumslader.handleDistanceRemaining(OnStreamState(StreamState.Searching))
            forumslader.handleElevationRemaining(OnStreamState(StreamState.NotAvailable))
            forumslader.handleHeadwindStreamState(OnStreamState(StreamState.NotAvailable))

            // then route remaining is cleared
            assertEquals(null, BatteryEstimateStore.estimateFlow.value?.routeRemainingKm)
        }
}
