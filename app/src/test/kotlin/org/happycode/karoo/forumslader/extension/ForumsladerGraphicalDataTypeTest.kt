package org.happycode.karoo.forumslader.extension

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_BATTERY_LOW_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.estimation.BatteryEstimate

private const val EXTENSION_ID = "karoo-forumslader"

class ForumsladerGraphicalDataTypeTest : ShouldSpec({

    var context = mockk<Context>(relaxed = true)
    var prefs = mockk<SharedPreferences>(relaxed = true)
    var emitter = mockk<ViewEmitter>(relaxed = true)

    fun setupStringMocks() {
        every { context.packageName } returns "org.happycode.karoo.forumslader"
        every { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns prefs
        every { prefs.getInt(KEY_BATTERY_LOW_THRESHOLD, any()) } returns 20
        every { context.getString(R.string.charge_state_charging) } returns "Charging"
        every { context.getString(R.string.charge_state_full) } returns "Full"
        every { context.getString(R.string.status_searching) } returns "Searching"
        every { context.getString(R.string.status_not_available) } returns "N/A"
        every { context.getString(R.string.battery_range_calc_short) } returns "Calc…"
        every { context.getString(R.string.unit_km) } returns "km"
        every { context.getString(R.string.unit_mi) } returns "mi"

        mockkStatic(ContextCompat::class)
        every { ContextCompat.getColor(any(), any()) } returns 0xFF000000.toInt()
    }

    beforeEach {
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        emitter = mockk(relaxed = true)
        setupStringMocks()

        mockkConstructor(KarooSystemService::class)
        every { anyConstructed<KarooSystemService>().connect(any()) } answers {
            val callback = firstArg<(Boolean) -> Unit>()
            callback(true)
        }
    }

    afterEach {
        unmockkAll()
    }

    should("immediately emit preview RemoteViews when config preview is true") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val config = ViewConfig(
            gridSize = Pair(60, 15),
            viewSize = Pair(400, 100),
            textSize = 32,
            preview = true
        )

        // when
        dataType.startView(context, config, emitter)

        // then
        verify(exactly = 1) { emitter.updateView(any()) }
    }

    should("resolve battery color dynamically based on low threshold") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val lowThreshold = 25

        // when & then
        dataType.resolveBatteryColor(level = 20, lowThreshold = lowThreshold) shouldBe R.color.karoo_battery_low
        dataType.resolveBatteryColor(level = 25, lowThreshold = lowThreshold) shouldBe R.color.karoo_battery_low
        dataType.resolveBatteryColor(level = 35, lowThreshold = lowThreshold) shouldBe R.color.karoo_battery_medium
        dataType.resolveBatteryColor(level = 50, lowThreshold = lowThreshold) shouldBe R.color.karoo_battery_medium
        dataType.resolveBatteryColor(level = 51, lowThreshold = lowThreshold) shouldBe R.color.karoo_battery_good
        dataType.resolveBatteryColor(level = 95, lowThreshold = lowThreshold) shouldBe R.color.karoo_battery_good
    }

    should("create view state with searching status when stream state is searching") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)

        // when
        val state = dataType.createViewState(context, StreamState.Searching, isImperial = false, lowThreshold = 20)

        // then
        state.levelText shouldBe "---"
        state.rangeText shouldBe "Searching"
        state.rangeUnitText shouldBe ""
        state.isRangeNumeric shouldBe false
    }

    should("create view state with not available status when stream state is not available") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)

        // when
        val state = dataType.createViewState(context, StreamState.NotAvailable, isImperial = false, lowThreshold = 20)

        // then
        state.levelText shouldBe "---"
        state.rangeText shouldBe "N/A"
        state.rangeUnitText shouldBe ""
        state.isRangeNumeric shouldBe false
    }

    should("create view state with idle status when stream state is idle") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)

        // when
        val state = dataType.createViewState(context, StreamState.Idle, isImperial = false, lowThreshold = 20)

        // then
        state.levelText shouldBe "---"
        state.rangeText shouldBe "---"
        state.isRangeNumeric shouldBe false
    }

    should("format range in km when preferred unit is metric") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(
                DataFieldId.FIELD_BATTERY_LEVEL to 85.0,
                DataFieldId.FIELD_RANGE_KM to 42.4,
                DataFieldId.FIELD_CHARGE_STATE to ChargeState.DISCHARGING.ordinal.toDouble()
            )
        )

        // when
        val state = dataType.createViewState(context, StreamState.Streaming(dataPoint), isImperial = false, lowThreshold = 20)

        // then
        state.levelText shouldBe "85"
        state.levelUnitText shouldBe "%"
        state.rangeText shouldBe "42"
        state.rangeUnitText shouldBe "km"
        state.isRangeNumeric shouldBe true
        state.levelColorResId shouldBe R.color.karoo_battery_good
    }

    should("format range in miles when preferred unit is imperial") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(
                DataFieldId.FIELD_BATTERY_LEVEL to 45.0,
                DataFieldId.FIELD_RANGE_KM to 42.4,
                DataFieldId.FIELD_CHARGE_STATE to ChargeState.DISCHARGING.ordinal.toDouble()
            )
        )

        // when
        val state = dataType.createViewState(context, StreamState.Streaming(dataPoint), isImperial = true, lowThreshold = 20)

        // then
        state.levelText shouldBe "45"
        state.rangeText shouldBe "26" // 42.4 * 0.621371 = 26.346 -> 26
        state.rangeUnitText shouldBe "mi"
        state.isRangeNumeric shouldBe true
        state.levelColorResId shouldBe R.color.karoo_battery_medium
    }

    should("format range as charging and bolt indicator when charge state is charging") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(
                DataFieldId.FIELD_BATTERY_LEVEL to 15.0,
                DataFieldId.FIELD_CHARGE_STATE to ChargeState.CHARGING.ordinal.toDouble()
            )
        )

        // when
        val state = dataType.createViewState(context, StreamState.Streaming(dataPoint), isImperial = false, lowThreshold = 20)

        // then
        state.levelText shouldBe "15"
        state.levelUnitText shouldBe "⚡%"
        state.rangeText shouldBe "Charging"
        state.rangeUnitText shouldBe ""
        state.isRangeNumeric shouldBe false
        state.levelColorResId shouldBe R.color.karoo_battery_low
    }

    should("format range as full when charge state is full") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(
                DataFieldId.FIELD_BATTERY_LEVEL to 100.0,
                DataFieldId.FIELD_CHARGE_STATE to ChargeState.FULL.ordinal.toDouble()
            )
        )

        // when
        val state = dataType.createViewState(context, StreamState.Streaming(dataPoint), isImperial = false, lowThreshold = 20)

        // then
        state.levelText shouldBe "100"
        state.rangeText shouldBe "Full"
        state.rangeUnitText shouldBe ""
        state.isRangeNumeric shouldBe false
    }

    should("format range as calc when range is missing during discharging") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(
                DataFieldId.FIELD_BATTERY_LEVEL to 70.0,
                DataFieldId.FIELD_CHARGE_STATE to ChargeState.DISCHARGING.ordinal.toDouble()
            )
        )

        // when
        val state = dataType.createViewState(context, StreamState.Streaming(dataPoint), isImperial = false, lowThreshold = 20)

        // then
        state.rangeText shouldBe "Calc…"
        state.rangeUnitText shouldBe ""
        state.isRangeNumeric shouldBe false
    }

    should("include checkmark in unit when route is sufficient") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(
                DataFieldId.FIELD_BATTERY_LEVEL to 80.0,
                DataFieldId.FIELD_RANGE_KM to 50.0,
                DataFieldId.FIELD_ROUTE_SUFFICIENT to 1.0,
                DataFieldId.FIELD_CHARGE_STATE to ChargeState.DISCHARGING.ordinal.toDouble()
            )
        )

        // when
        val state = dataType.createViewState(context, StreamState.Streaming(dataPoint), isImperial = false, lowThreshold = 20)

        // then
        state.rangeUnitText shouldBe "km ✓"
    }

    should("include exclamation mark in unit when route is insufficient") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(
                DataFieldId.FIELD_BATTERY_LEVEL to 80.0,
                DataFieldId.FIELD_RANGE_KM to 50.0,
                DataFieldId.FIELD_ROUTE_SUFFICIENT to 0.0,
                DataFieldId.FIELD_CHARGE_STATE to ChargeState.DISCHARGING.ordinal.toDouble()
            )
        )

        // when
        val state = dataType.createViewState(context, StreamState.Streaming(dataPoint), isImperial = false, lowThreshold = 20)

        // then
        state.rangeUnitText shouldBe "km !"
    }

    should("fall back to BatteryEstimateStore when dataPoint does not contain custom range or charge state fields") {
        // given
        val estimate = BatteryEstimate(
            remainingCapacityPct = 75,
            avgDischargeRatePctPerKm = 1.0f,
            estimatedRangeKm = 55.0f,
            routeRemainingKm = 30.0f,
            isSufficientForRoute = true,
            chargeState = ChargeState.DISCHARGING
        )
        val dataPoint = DataPoint(
            dataTypeId = DataType.dataTypeId(EXTENSION_ID, DataFieldId.BATTERY_LEVEL),
            values = mapOf(DataType.Field.SINGLE to 75.0)
        )
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)

        // when
        val state = dataType.createViewState(
            context = context,
            streamState = StreamState.Streaming(dataPoint),
            isImperial = false,
            lowThreshold = 20,
            estimate = estimate
        )

        // then
        state.levelText shouldBe "75"
        state.rangeText shouldBe "55"
        state.rangeUnitText shouldBe "km ✓"
        state.isRangeNumeric shouldBe true
    }

    should("create RemoteViews without crashing and apply view state") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val config = ViewConfig(
            gridSize = Pair(60, 15),
            viewSize = Pair(400, 100),
            textSize = 32
        )
        val state = ForumsladerGraphicalDataType.BatteryViewState(
            levelText = "85",
            levelUnitText = "%",
            levelColorResId = R.color.karoo_battery_good,
            rangeText = "120",
            rangeUnitText = "km",
            isRangeNumeric = true
        )

        // when
        val views = dataType.createRemoteViews(context, config, state)

        // then
        views shouldNotBe null
    }

    should("disconnect KarooSystemService when view cancellation is invoked") {
        // given
        val mockKarooSystem = mockk<KarooSystemService>(relaxed = true)
        every { mockKarooSystem.connect(any()) } answers {
            firstArg<(Boolean) -> Unit>().invoke(true)
        }
        every { mockKarooSystem.addConsumer(any()) } returns "stream-id" andThen "profile-id"

        val dataType = ForumsladerGraphicalDataType(
            extension = EXTENSION_ID,
            karooSystemFactory = { mockKarooSystem }
        )
        val config = ViewConfig(
            gridSize = Pair(60, 15),
            viewSize = Pair(400, 100),
            textSize = 32,
            preview = false
        )
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        dataType.startView(context, config, emitter)
        cancelSlot.captured.invoke()

        // then
        verify { mockKarooSystem.removeConsumer("stream-id") }
        verify { mockKarooSystem.removeConsumer("profile-id") }
        verify { mockKarooSystem.disconnect() }
    }

    should("update view and state when handleStreamState is executed") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val config = ViewConfig(Pair(60, 15), Pair(400, 100), 32)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(
                DataFieldId.FIELD_BATTERY_LEVEL to 95.0,
                DataFieldId.FIELD_RANGE_KM to 80.0,
                DataFieldId.FIELD_CHARGE_STATE to ChargeState.DISCHARGING.ordinal.toDouble()
            )
        )

        // when
        dataType.handleStreamState(OnStreamState(StreamState.Streaming(dataPoint)), context, config, emitter)

        // then
        dataType.lastStreamState shouldBe StreamState.Streaming(dataPoint)
        verify { emitter.updateView(any()) }
    }

    should("update imperial setting and emit view when handleUserProfile changes unit") {
        // given
        val dataType = ForumsladerGraphicalDataType(EXTENSION_ID)
        val config = ViewConfig(Pair(60, 15), Pair(400, 100), 32)
        val profile = mockk<UserProfile>(relaxed = true) {
            every { preferredUnit.distance } returns UserProfile.PreferredUnit.UnitType.IMPERIAL
        }

        // when
        dataType.handleUserProfile(profile, context, config, emitter)

        // then
        dataType.isImperial shouldBe true
        verify { emitter.updateView(any()) }

        // when called with same unit, no extra emission
        dataType.handleUserProfile(profile, context, config, emitter)
        verify(exactly = 1) { emitter.updateView(any()) }
    }

    should("queue throttled update when views arrive within throttle window") {
        // given
        var fakeTime = 1000L
        val dataType = ForumsladerGraphicalDataType(
            extension = EXTENSION_ID,
            timeProvider = { fakeTime }
        )
        val config = ViewConfig(Pair(60, 15), Pair(400, 100), 32)
        val point1 = DataPoint(dataType.dataTypeId, mapOf(DataFieldId.FIELD_BATTERY_LEVEL to 90.0))

        // when first event arrives
        dataType.handleStreamState(OnStreamState(StreamState.Streaming(point1)), context, config, emitter)

        // then emits immediately
        verify(exactly = 1) { emitter.updateView(any()) }

        // when second event arrives within 500ms
        fakeTime += 500L
        val point2 = DataPoint(dataType.dataTypeId, mapOf(DataFieldId.FIELD_BATTERY_LEVEL to 89.0))
        dataType.handleStreamState(OnStreamState(StreamState.Streaming(point2)), context, config, emitter)

        // then queued, not emitted immediately
        verify(exactly = 1) { emitter.updateView(any()) }
    }
})
