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
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.maps.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
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
import org.happycode.karoo.forumslader.application.CsvLogger
import org.happycode.karoo.forumslader.application.ForumsladerStateStore
import org.happycode.karoo.forumslader.model.ForumsladerVersion

@OptIn(ExperimentalCoroutinesApi::class)
class ForumsladerKarooAdapterTest : ShouldSpec({
    lateinit var context: Context
    lateinit var mockPrefs: SharedPreferences
    lateinit var emitter: Emitter<DeviceEvent>
    lateinit var bleManager: ForumsladerBleManager
    lateinit var karooSystem: KarooSystemService
    lateinit var csvLogger: CsvLogger
    lateinit var testScope: CoroutineScope
    lateinit var tempFilesDir: File

    lateinit var connectionStateFlow: MutableStateFlow<ConnectionStatus>
    lateinit var incomingDataFlow: MutableSharedFlow<ByteArray>
    lateinit var versionDetectedFlow: MutableSharedFlow<ForumsladerVersion>
    lateinit var notificationsEnabledFlow: MutableSharedFlow<Unit>

    fun createAdapter(
        address: String = "00:11:22:33:44:55",
        displayName: String? = null,
        scope: CoroutineScope = testScope
    ) = ForumsladerKarooAdapter(
        context = context,
        address = address,
        displayName = displayName,
        adapterScope = scope,
        bleManager = bleManager,
        karooSystem = karooSystem,
        csvLogger = csvLogger
    )

    beforeEach {
        mockkStatic(Log::class)
        every { Log.v(any<String>(), any<String>()) } returns 0
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.i(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        context = mockk(relaxed = true)
        mockPrefs = mockk(relaxed = true)
        every { mockPrefs.getInt("wheelsize", 2200) } returns 2200
        every { mockPrefs.getInt("poles", 14) } returns 14
        every { mockPrefs.getFloat("speedMultiplier", 1.0f) } returns 1.0f
        every { mockPrefs.getInt("battery_low_threshold", any()) } returns 20
        every { mockPrefs.getFloat("high_temp_threshold", any()) } returns 50f
        every { context.getSharedPreferences(any(), any()) } returns mockPrefs
        every { context.applicationContext } returns context

        tempFilesDir = Files.createTempDirectory("test_files").toFile()
        every { context.filesDir } returns tempFilesDir

        emitter = mockk(relaxed = true)
        csvLogger = mockk(relaxed = true)

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

    afterEach {
        ForumsladerStateStore.clear()
        BatteryEstimateStore.clear()
        tempFilesDir.deleteRecursively()
        unmockkAll()
    }

    should("set correct metadata when initialized") {
        // given
        val address = "00:11:22:33:44:55"

        // when
        val forumslader = createAdapter(address = address, displayName = "My Forumslader")

        // then
        forumslader.device.extension shouldBe "karoo-forumslader"
        forumslader.device.uid shouldBe "fl-$address"
        forumslader.device.displayName shouldBe "My Forumslader"
    }

    should("start ble manager when connect is called") {
        // given
        val forumslader = createAdapter()

        // when
        forumslader.connect(emitter)

        // then
        verify { bleManager.start() }
    }

    should("emit connection status when ble connection state changes") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            // when
            connectionStateFlow.value = ConnectionStatus.CONNECTED

            // then
            verify { emitter.onNext(match { it is OnConnectionStatus && it.status == ConnectionStatus.CONNECTED }) }
        }
    }

    should("emit all registry metrics with correct values and conversions when incoming data arrives") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
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

            dataPoints.shouldNotBeEmpty()

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
                val actualValue = dataPoints[fullId].shouldNotBeNull()
                actualValue shouldBe (expectedValue plusOrMinus abs(expectedValue * 0.01).coerceAtLeast(0.001))
            }
        }
    }

    should("emit only finite values for all metrics when incoming data is parsed") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val capturedEvents = mutableListOf<DeviceEvent>()
            every { emitter.onNext(capture(capturedEvents)) } returns Unit

            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            val flb = $$"$FLB,255,0,1005\n"
            val flc = $$"$FLC,5,0,100\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"

            // when
            incomingDataFlow.emit((flb + flc + fl5).toByteArray(Charsets.US_ASCII))

            // then
            val dataPoints = capturedEvents.filterIsInstance<OnDataPoint>()
            dataPoints.shouldNotBeEmpty()
            dataPoints.forEach { point ->
                point.dataPoint.values.forEach { (_, value) ->
                    value.isFinite() shouldBe true
                }
            }
        }
    }

    should("emit calculating sentinel for battery range when discharging without sufficient distance data") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val capturedEvents = mutableListOf<DeviceEvent>()
            every { emitter.onNext(capture(capturedEvents)) } returns Unit

            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            // FL6 with charge state 2 (DISCHARGING), battery 80% but only 1 sample (no distance diff yet)
            val flc = $$"$FLC,5,0,80\n"
            val fl6 = $$"$FL6,2,0,0,0,0,0,0,0,0,0,0,0\n"

            // when
            incomingDataFlow.emit((flc + fl6).toByteArray(Charsets.US_ASCII))

            // then
            val rangePoint = capturedEvents.filterIsInstance<OnDataPoint>()
                .firstOrNull { it.dataPoint.dataTypeId == DataType.dataTypeId("karoo-forumslader", DataFieldId.BATTERY_RANGE) }

            rangePoint.shouldNotBeNull()
            rangePoint.dataPoint.values[DataType.Field.SINGLE] shouldBe DataFieldId.BATTERY_RANGE_CALCULATING
        }
    }

    should("emit estimated range in meters when transitioning to discharging if previous discharge rate exists") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val capturedEvents = mutableListOf<DeviceEvent>()
            every { emitter.onNext(capture(capturedEvents)) } returns Unit

            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            // Step 1: establish initial discharge sample at 0.0km, 100% battery
            val flc1 = $$"$FLC,5,0,100\n"
            val fld1 = $$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,0.0\n"
            incomingDataFlow.emit((flc1 + fld1).toByteArray(Charsets.US_ASCII))

            // Step 2: discharge over 1.0km to 95% battery (rate = 5.0% per km)
            val flc2 = $$"$FLC,5,0,95\n"
            val fld2 = $$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,1.0\n"
            incomingDataFlow.emit((flc2 + fld2).toByteArray(Charsets.US_ASCII))

            // Step 3: intermittent charging phase at 2.0km with 98% battery
            val flc3 = $$"$FLC,5,0,98\n"
            val fld3 = $$"$FLD,19,,0,50,12.0,1.5,0.5,+,7,0,0,0,0,2.0\n"
            incomingDataFlow.emit((flc3 + fld3).toByteArray(Charsets.US_ASCII))

            // when transitioning back to discharging before minimum window is satisfied
            capturedEvents.clear()
            val flc4 = $$"$FLC,5,0,98\n"
            val fld4 = $$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,2.1\n"
            incomingDataFlow.emit((flc4 + fld4).toByteArray(Charsets.US_ASCII))

            // then battery range emits the correctly updated moving average range in meters (98 / (2.0 / 2.1) * 1000 = 102900m)
            val rangePoint = capturedEvents.filterIsInstance<OnDataPoint>()
                .firstOrNull { it.dataPoint.dataTypeId == DataType.dataTypeId("karoo-forumslader", DataFieldId.BATTERY_RANGE) }

            rangePoint.shouldNotBeNull()
            rangePoint.dataPoint.values[DataType.Field.SINGLE] shouldBe (102900.0 plusOrMinus 1.0)
        }
    }

    should("start parameter request loop when bluetooth notifications are enabled") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            // when
            notificationsEnabledFlow.emit(Unit)

            // then
            verify { bleManager.writeCommand(match { String(it).startsWith($$"$FLT,5") }) }
        }
    }

    should("lock MAC address in config when telemetry data is received for the first time") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val address = "00:11:22:33:44:55"
            every { mockPrefs.getString("locked_mac_address", null) } returns null

            val forumslader = createAdapter(address = address, scope = backgroundScope)
            forumslader.connect(emitter)

            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"

            // when
            incomingDataFlow.emit(fl5.toByteArray(Charsets.US_ASCII))

            // then
            verify { mockPrefs.edit().putString("locked_mac_address", address) }
        }
    }

    should("update version in config when version detected") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            // when
            versionDetectedFlow.emit(ForumsladerVersion.V5)

            // then
            verify { mockPrefs.edit().putString("version", ForumsladerVersion.V5.key) }
        }
    }

    should("dispatch in-ride alert when high temperature threshold is reached") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            val flb = $$"$FLB,655,0,1005\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"

            // when
            incomingDataFlow.emit((flb + fl5).toByteArray(Charsets.US_ASCII))

            // then
            verify {
                karooSystem.dispatch(match {
                    it is InRideAlert && it.id == "fl_hi_temp"
                })
            }
        }
    }

    should("stop ble manager and disconnect karoo system when emitter is cancelled") {
        // given
        val forumslader = createAdapter()

        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        forumslader.connect(emitter)
        cancelSlot.captured.invoke()

        // then
        verify { karooSystem.disconnect() }
        verify { bleManager.stop() }
    }

    should("dispatch low battery alert when battery level drops below threshold") {
        runTest(UnconfinedTestDispatcher()) {
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            val flc = $$"$FLC,5,0,15\n" // 15%
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
            incomingDataFlow.emit((flc + fl5).toByteArray(Charsets.US_ASCII))

            verify { karooSystem.dispatch(match { it is InRideAlert && it.id == "fl_bat_low" }) }
        }
    }

    should("dispatch short circuit alert when status bit 0x8 is active") {
        runTest(UnconfinedTestDispatcher()) {
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            val fl6short = $$"$FL6,8,0,0,0,0,0,0,0,0,0,0,0\n"
            incomingDataFlow.emit(fl6short.toByteArray(Charsets.US_ASCII))

            verify { karooSystem.dispatch(match { it is InRideAlert && it.id == "fl_short" }) }
        }
    }

    should("dispatch system interrupt alert when status bit 0x800000 is active") {
        runTest(UnconfinedTestDispatcher()) {
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            val fl6int = $$"$FL6,800000,0,0,0,0,0,0,0,0,0,0,0\n"
            incomingDataFlow.emit(fl6int.toByteArray(Charsets.US_ASCII))

            verify { karooSystem.dispatch(match { it is InRideAlert && it.id == "fl_sys_int" }) }
        }
    }

    should("request config and reset state when day distance reset command is sent") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            // Simulate config loaded initially
            val flb = $$"$FLB,255,0,1005\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
            incomingDataFlow.emit((flb + fl5).toByteArray(Charsets.US_ASCII))

            // when
            forumslader.sendCommand($$"$FLT,6\n")

            // then
            verifyOrder {
                bleManager.writeCommand(match { it.decodeToString().startsWith($$"$FLT,6") })
                bleManager.writeCommand(match { it.decodeToString().startsWith($$"$FLT,5") })
            }

            yield()
            ForumsladerStateStore.isConfigLoadedFlow.value shouldBe false
        }
    }

    should("stop emitting fit messages when fitEmitter is set to null") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val fitEmitter = mockk<Emitter<FitEffect>>(relaxed = true)
            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.setFitEmitter(fitEmitter)
            forumslader.connect(emitter)

            // when
            forumslader.setFitEmitter(null)
            val flb = $$"$FLB,255,0,1005\n"
            val fl5 = $$"$FL5,200,3,100,500,500,500,2500,3500,0,0,0,0,1000\n"
            val flm = $$"$FLM,12500,1000,500,2500,1,1000,50,200,2,1000,5000,2000,10000,100,500\n"
            incomingDataFlow.emit((flb + fl5 + flm).toByteArray(Charsets.US_ASCII))

            // then
            verify(exactly = 0) { fitEmitter.onNext(any()) }
        }
    }

    should("emit fit message when incoming data arrives and fitEmitter is active") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val fitEmitter = mockk<Emitter<FitEffect>>(relaxed = true)
            val forumslader = createAdapter(scope = backgroundScope)
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
    }

    should("update route remaining estimate when distance to destination and elevation streams emit") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
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
            BatteryEstimateStore.estimateFlow.value?.routeRemainingKm shouldBe 25.0f
            BatteryEstimateStore.estimateFlow.value?.isSufficientForRoute shouldBe true
        }
    }

    should("clear route remaining estimate when navigation state becomes idle") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
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
            BatteryEstimateStore.estimateFlow.value?.routeRemainingKm shouldBe 25.0f

            // when
            forumslader.handleNavigationState(OnNavigationState(OnNavigationState.NavigationState.Idle))

            // then
            BatteryEstimateStore.estimateFlow.value?.routeRemainingKm.shouldBeNull()
        }
    }

    should("update battery estimate penalty when headwind stream emits") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
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
            val estimate = BatteryEstimateStore.estimateFlow.value
            estimate.shouldNotBeNull()
        }
    }

    should("clear route remaining estimate when stream states are searching or not available") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val forumslader = createAdapter(scope = backgroundScope)
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
            BatteryEstimateStore.estimateFlow.value?.routeRemainingKm shouldBe 25.0f

            // when
            forumslader.handleDistanceRemaining(OnStreamState(StreamState.Searching))
            forumslader.handleElevationRemaining(OnStreamState(StreamState.NotAvailable))
            forumslader.handleHeadwindStreamState(OnStreamState(StreamState.NotAvailable))

            // then
            BatteryEstimateStore.estimateFlow.value?.routeRemainingKm.shouldBeNull()
        }
    }

    should("emit range in meters when charging with known discharge rate") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val capturedEvents = mutableListOf<DeviceEvent>()
            every { emitter.onNext(capture(capturedEvents)) } returns Unit

            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            // Step 1: establish initial discharge rate = 5% per km (100% -> 95% over 1km)
            val flc1 = $$"$FLC,5,0,100\n"
            val fld1 = $$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,0.0\n"
            incomingDataFlow.emit((flc1 + fld1).toByteArray(Charsets.US_ASCII))

            val flc2 = $$"$FLC,5,0,95\n"
            val fld2 = $$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,1.0\n"
            incomingDataFlow.emit((flc2 + fld2).toByteArray(Charsets.US_ASCII))

            // when: transitions to charging at 2.0km with 96% battery
            capturedEvents.clear()
            val flc3 = $$"$FLC,5,0,96\n"
            val fld3 = $$"$FLD,19,,0,50,12.0,1.5,0.5,+,7,0,0,0,0,2.0\n"
            incomingDataFlow.emit((flc3 + fld3).toByteArray(Charsets.US_ASCII))

            // then: battery range emits estimated range in meters (96 / 5.0 * 1000 = 19200m) instead of sentinel
            val rangePoint = capturedEvents.filterIsInstance<OnDataPoint>()
                .firstOrNull { it.dataPoint.dataTypeId == DataType.dataTypeId("karoo-forumslader", DataFieldId.BATTERY_RANGE) }

            rangePoint.shouldNotBeNull()
            rangePoint.dataPoint.values[DataType.Field.SINGLE] shouldBe (19200.0 plusOrMinus 1.0)
        }
    }

    should("emit range based on lastDischargeRate when discharging without sufficient window data but prior rate exists") {
        runTest(UnconfinedTestDispatcher()) {
            // given
            val capturedEvents = mutableListOf<DeviceEvent>()
            every { emitter.onNext(capture(capturedEvents)) } returns Unit

            val forumslader = createAdapter(scope = backgroundScope)
            forumslader.connect(emitter)

            // Discharge: 50% to 45% over 1km -> 5% per km
            val flc1 = $$"$FLC,5,0,50\n"
            val fld1 = $$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,0.0\n"
            incomingDataFlow.emit((flc1 + fld1).toByteArray(Charsets.US_ASCII))

            val flc2 = $$"$FLC,5,0,45\n"
            val fld2 = $$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,1.0\n"
            incomingDataFlow.emit((flc2 + fld2).toByteArray(Charsets.US_ASCII))

            // Charge up to 55% (net charge over window: 50% -> 55%, so initialLevelDiff <= 0)
            val flc3 = $$"$FLC,5,0,55\n"
            val fld3 = $$"$FLD,19,,0,50,12.0,1.5,0.5,+,7,0,0,0,0,2.0\n"
            incomingDataFlow.emit((flc3 + fld3).toByteArray(Charsets.US_ASCII))

            // when: transitions back to discharging at 2.05km (initialLevelDiff = 50 - 55 = -5 <= 0 prunes, only 50m of discharging)
            capturedEvents.clear()
            val flc4 = $$"$FLC,5,0,55\n"
            val fld4 = $$"$FLD,19,,0,50,12.0,-0.5,0.5,-,7,0,0,0,0,2.05\n"
            incomingDataFlow.emit((flc4 + fld4).toByteArray(Charsets.US_ASCII))

            // then: emits estimated range using lastDischargeRate (55 / 5.0 * 1000 = 11000m)
            val rangePoint = capturedEvents.filterIsInstance<OnDataPoint>()
                .firstOrNull { it.dataPoint.dataTypeId == DataType.dataTypeId("karoo-forumslader", DataFieldId.BATTERY_RANGE) }

            rangePoint.shouldNotBeNull()
            rangePoint.dataPoint.values[DataType.Field.SINGLE] shouldBe (11000.0 plusOrMinus 1.0)
        }
    }
})

