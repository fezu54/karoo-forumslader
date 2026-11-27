package org.happycode.karoo.forumslader.extension

import android.content.Context
import android.os.Handler
import androidx.core.content.ContextCompat
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.extension.ChargeStateGraphicalDataType.ChargeStateViewState

private const val EXTENSION_ID = "karoo-forumslader"

class ChargeStateGraphicalDataTypeTest : ShouldSpec({

    var context = mockk<Context>(relaxed = true)
    var emitter = mockk<ViewEmitter>(relaxed = true)
    var karooSystem = mockk<KarooSystemService>(relaxed = true)
    var handler = mockk<Handler>(relaxed = true)
    var currentTimeMs = 10_000L

    val defaultConfig = ViewConfig(
        gridSize = Pair(30, 15),
        viewSize = Pair(240, 100),
        textSize = 32,
        preview = false
    )

    fun singleValueDataPoint(dataTypeId: String, value: Double?): DataPoint = DataPoint(
        dataTypeId = dataTypeId,
        values = value?.let { mapOf(DataType.Field.SINGLE to it) } ?: emptyMap()
    )

    fun createDataType(): ChargeStateGraphicalDataType = ChargeStateGraphicalDataType(
        extension = EXTENSION_ID,
        timeProvider = { currentTimeMs },
        karooSystemFactory = { karooSystem },
        handlerFactory = { handler }
    )

    beforeEach {
        context = mockk(relaxed = true)
        emitter = mockk(relaxed = true)
        karooSystem = mockk(relaxed = true)
        handler = mockk(relaxed = true)
        currentTimeMs = 10_000L

        every { context.packageName } returns "org.happycode.karoo.forumslader"
        every { context.getString(R.string.charge_state_standby) } returns "Standby"
        every { context.getString(R.string.charge_state_charging) } returns "Charging"
        every { context.getString(R.string.charge_state_discharging) } returns "Discharging"
        every { context.getString(R.string.charge_state_full) } returns "Full"
        every { context.getString(R.string.status_searching) } returns "Searching"
        every { context.getString(R.string.status_not_available) } returns "N/A"

        mockkStatic(ContextCompat::class)
        every { ContextCompat.getColor(any(), any()) } returns 0xFF000000.toInt()

        every { karooSystem.connect(any()) } answers {
            firstArg<(Boolean) -> Unit>().invoke(true)
        }
        every { handler.post(any()) } answers {
            firstArg<Runnable>().run()
            true
        }
    }

    afterEach {
        unmockkAll()
    }

    should("immediately emit preview RemoteViews when config preview is true") {
        // given
        val dataType = createDataType()
        val previewConfig = defaultConfig.copy(preview = true)

        // when
        dataType.startView(context, previewConfig, emitter)

        // then
        verify(exactly = 1) { emitter.updateView(any()) }
        verify(exactly = 0) { karooSystem.connect(any()) }
    }

    should("map streaming ChargeState ordinals to localized labels and colors when streaming") {
        // given
        val dataType = createDataType()

        // when & then
        dataType.createViewState(context, StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, ChargeState.STANDBY.ordinal.toDouble()))) shouldBe
            ChargeStateViewState("Standby", R.color.karoo_text_primary)
        dataType.createViewState(context, StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, ChargeState.CHARGING.ordinal.toDouble()))) shouldBe
            ChargeStateViewState("Charging", R.color.karoo_battery_good)
        dataType.createViewState(context, StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, ChargeState.DISCHARGING.ordinal.toDouble()))) shouldBe
            ChargeStateViewState("Discharging", R.color.karoo_battery_medium)
        dataType.createViewState(context, StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, ChargeState.FULL.ordinal.toDouble()))) shouldBe
            ChargeStateViewState("Full", R.color.karoo_battery_good)
        dataType.createViewState(context, StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, 99.0))) shouldBe
            ChargeStateViewState("---", R.color.karoo_text_primary)
        dataType.createViewState(context, StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, null))) shouldBe
            ChargeStateViewState("---", R.color.karoo_text_primary)
    }

    should("map non-streaming states to expected status labels when stream is not active") {
        // given
        val dataType = createDataType()

        // when & then
        dataType.createViewState(context, StreamState.Searching) shouldBe
            ChargeStateViewState("Searching", R.color.karoo_text_primary, isStatus = true)
        dataType.createViewState(context, StreamState.NotAvailable) shouldBe
            ChargeStateViewState("N/A", R.color.karoo_text_primary, isStatus = true)
        dataType.createViewState(context, StreamState.Idle) shouldBe
            ChargeStateViewState("---", R.color.karoo_text_primary)
    }

    should("only emit RemoteViews when view state changes") {
        // given
        val dataType = createDataType()
        val session = dataType.ViewSession(context, defaultConfig, emitter, handler)
        val fullEvent = OnStreamState(StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, ChargeState.FULL.ordinal.toDouble())))
        val chargingEvent = OnStreamState(StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, ChargeState.CHARGING.ordinal.toDouble())))

        // when
        session.onStreamState(fullEvent)
        currentTimeMs += 2_000L
        session.onStreamState(fullEvent)
        currentTimeMs += 2_000L
        session.onStreamState(chargingEvent)

        // then
        verify(exactly = 2) { emitter.updateView(any()) }
    }

    should("defer rapid state transitions inside throttle window so Karoo does not drop update") {
        // given
        val dataType = createDataType()
        val session = dataType.ViewSession(context, defaultConfig, emitter, handler)
        val runnableSlot = slot<Runnable>()
        val delaySlot = slot<Long>()
        every { handler.postDelayed(capture(runnableSlot), capture(delaySlot)) } returns true

        val idleEvent = OnStreamState(StreamState.Idle)
        val dischargingEvent = OnStreamState(StreamState.Streaming(singleValueDataPoint(dataType.dataTypeId, ChargeState.DISCHARGING.ordinal.toDouble())))

        // when: Idle arrives at t=10000ms, Discharging arrives 600ms later (inside 1050ms window)
        session.onStreamState(idleEvent)
        currentTimeMs += 600L
        session.onStreamState(dischargingEvent)

        // then: only Idle emitted immediately; Discharging is scheduled for 450ms later
        verify(exactly = 1) { emitter.updateView(any()) }
        delaySlot.captured shouldBe 450L

        // when: scheduled runnable fires
        currentTimeMs += 450L
        runnableSlot.captured.run()

        // then: deferred Discharging RemoteViews is emitted
        verify(exactly = 2) { emitter.updateView(any()) }
    }

    should("register independent consumers when multiple views are started") {
        // given
        val dataType = createDataType()
        every { karooSystem.addConsumer(any()) } returns "listener-1" andThen "listener-2"
        val emitterA = mockk<ViewEmitter>(relaxed = true)
        val emitterB = mockk<ViewEmitter>(relaxed = true)

        // when
        dataType.startView(context, defaultConfig, emitterA)
        dataType.startView(context, defaultConfig, emitterB)

        // then
        verify(exactly = 2) { karooSystem.addConsumer(any()) }
    }

    should("disconnect KarooSystemService when view is cancelled after connecting") {
        // given
        val dataType = createDataType()
        every { karooSystem.addConsumer(any()) } returns "listener-id"
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        dataType.startView(context, defaultConfig, emitter)
        cancelSlot.captured()

        // then
        verify { karooSystem.removeConsumer("listener-id") }
        verify { karooSystem.disconnect() }
    }

    should("skip registering consumer and disconnect when cancelled before connect completes") {
        // given
        val dataType = createDataType()
        val connectSlot = slot<(Boolean) -> Unit>()
        every { karooSystem.connect(capture(connectSlot)) } returns Unit
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        dataType.startView(context, defaultConfig, emitter)
        cancelSlot.captured()
        connectSlot.captured(true)

        // then
        verify(exactly = 0) { karooSystem.addConsumer(any()) }
        verify(atLeast = 1) { karooSystem.disconnect() }
    }

    should("emit immediately on first render when monotonic uptime is below throttle window") {
        // given
        currentTimeMs = 200L
        val dataType = createDataType()
        val session = dataType.ViewSession(context, defaultConfig, emitter, handler)
        val idleEvent = OnStreamState(StreamState.Idle)

        // when
        session.onStreamState(idleEvent)

        // then
        verify(exactly = 1) { emitter.updateView(any()) }
        verify(exactly = 0) { handler.postDelayed(any(), any()) }
    }

    should("emit immediately when clock moves backward") {
        // given
        val dataType = createDataType()
        val session = dataType.ViewSession(context, defaultConfig, emitter, handler)
        val idleEvent = OnStreamState(StreamState.Idle)
        val chargingEvent = OnStreamState(
            StreamState.Streaming(
                singleValueDataPoint(dataType.dataTypeId, ChargeState.CHARGING.ordinal.toDouble())
            )
        )

        // when
        session.onStreamState(idleEvent)
        currentTimeMs = 5_000L
        session.onStreamState(chargingEvent)

        // then
        verify(exactly = 2) { emitter.updateView(any()) }
        verify(exactly = 0) { handler.postDelayed(any(), any()) }
    }

    should("replace pending runnable when another rapid state transition arrives inside throttle window") {
        // given
        val dataType = createDataType()
        val session = dataType.ViewSession(context, defaultConfig, emitter, handler)
        val runnables = mutableListOf<Runnable>()
        val delays = mutableListOf<Long>()
        every { handler.postDelayed(capture(runnables), capture(delays)) } returns true

        val idleEvent = OnStreamState(StreamState.Idle)
        val dischargingEvent = OnStreamState(
            StreamState.Streaming(
                singleValueDataPoint(dataType.dataTypeId, ChargeState.DISCHARGING.ordinal.toDouble())
            )
        )
        val chargingEvent = OnStreamState(
            StreamState.Streaming(
                singleValueDataPoint(dataType.dataTypeId, ChargeState.CHARGING.ordinal.toDouble())
            )
        )

        // when
        session.onStreamState(idleEvent)
        currentTimeMs += 300L
        session.onStreamState(dischargingEvent)
        currentTimeMs += 300L
        session.onStreamState(chargingEvent)

        // then
        delays shouldBe listOf(750L, 450L)
        verify(exactly = 1) { handler.removeCallbacks(runnables[0]) }
        verify(exactly = 1) { emitter.updateView(any()) }

        // when
        currentTimeMs += 450L
        runnables[1].run()

        // then
        verify(exactly = 2) { emitter.updateView(any()) }
    }

    should("remove pending callbacks and skip update when session is cancelled during throttle window") {
        // given
        val dataType = createDataType()
        var cancelled = false
        val session = dataType.ViewSession(
            context,
            defaultConfig,
            emitter,
            handler,
            isCancelled = { cancelled }
        )
        val runnableSlot = slot<Runnable>()
        every { handler.postDelayed(capture(runnableSlot), any()) } returns true

        val idleEvent = OnStreamState(StreamState.Idle)
        val dischargingEvent = OnStreamState(
            StreamState.Streaming(
                singleValueDataPoint(dataType.dataTypeId, ChargeState.DISCHARGING.ordinal.toDouble())
            )
        )

        // when
        session.onStreamState(idleEvent)
        currentTimeMs += 600L
        session.onStreamState(dischargingEvent)
        cancelled = true
        session.cancel()
        runnableSlot.captured.run()

        // then
        verify(exactly = 1) { handler.removeCallbacks(runnableSlot.captured) }
        verify(exactly = 1) { emitter.updateView(any()) }
    }

    should("deliver stream events through handler and ignore events received after cancellation") {
        // given
        var capturedConsumer: ((OnStreamState) -> Unit)? = null
        val dataType = ChargeStateGraphicalDataType(
            extension = EXTENSION_ID,
            timeProvider = { currentTimeMs },
            karooSystemFactory = { karooSystem },
            handlerFactory = { handler },
            streamConsumerRegistrar = { _, _, onEvent ->
                capturedConsumer = onEvent
                "listener-id"
            }
        )
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        val chargingEvent = OnStreamState(
            StreamState.Streaming(
                singleValueDataPoint(dataType.dataTypeId, ChargeState.CHARGING.ordinal.toDouble())
            )
        )
        val fullEvent = OnStreamState(
            StreamState.Streaming(
                singleValueDataPoint(dataType.dataTypeId, ChargeState.FULL.ordinal.toDouble())
            )
        )

        // when
        dataType.startView(context, defaultConfig, emitter)
        capturedConsumer?.invoke(chargingEvent)
        cancelSlot.captured()
        currentTimeMs += 2_000L
        capturedConsumer?.invoke(fullEvent)

        // then
        capturedConsumer shouldNotBe null
        verify(atLeast = 1) { handler.post(any()) }
        verify(exactly = 1) { emitter.updateView(any()) }
    }

    should("disconnect and skip consumer registration when connect fails") {
        // given
        val dataType = createDataType()
        every { karooSystem.connect(any()) } answers {
            firstArg<(Boolean) -> Unit>().invoke(false)
        }

        // when
        dataType.startView(context, defaultConfig, emitter)

        // then
        verify(exactly = 0) { karooSystem.addConsumer(any()) }
        verify(exactly = 1) { karooSystem.disconnect() }
    }

    should("remove consumer immediately when cancelled while addConsumer is registering") {
        // given
        val dataType = createDataType()
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit
        every { karooSystem.addConsumer(any()) } answers {
            cancelSlot.captured()
            "raced-listener-id"
        }

        // when
        dataType.startView(context, defaultConfig, emitter)

        // then
        verify(exactly = 1) { karooSystem.removeConsumer("raced-listener-id") }
        verify(atLeast = 1) { karooSystem.disconnect() }
    }

    should("render RemoteViews with standard non-compact layout when grid width is at least 50") {
        // given
        val dataType = createDataType()
        val wideConfig = defaultConfig.copy(
            gridSize = Pair(60, 15),
            viewSize = Pair(400, 100)
        )
        val state = ChargeStateViewState("Charging", R.color.karoo_battery_good)

        // when
        val valueViews = dataType.createRemoteViews(context, wideConfig, state)
        val statusViews = dataType.createRemoteViews(
            context,
            wideConfig,
            ChargeStateViewState("Searching", R.color.karoo_text_primary, isStatus = true)
        )
        val compactStatusViews = dataType.createRemoteViews(
            context,
            defaultConfig,
            ChargeStateViewState("Searching", R.color.karoo_text_primary, isStatus = true)
        )

        // then
        valueViews shouldNotBe null
        statusViews shouldNotBe null
        compactStatusViews shouldNotBe null
    }
})
