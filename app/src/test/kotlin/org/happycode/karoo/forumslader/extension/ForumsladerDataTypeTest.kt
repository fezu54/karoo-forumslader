package org.happycode.karoo.forumslader.extension

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import io.kotest.core.spec.style.ShouldSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId

private const val EXTENSION_ID = "karoo-forumslader"

class ForumsladerDataTypeTest : ShouldSpec({

    var context = mockk<Context>(relaxed = true)
    var config = mockk<ViewConfig>(relaxed = true)
    var emitter = mockk<ViewEmitter>(relaxed = true)

    fun setupStringMocks() {
        every { context.getString(R.string.charge_state_standby) } returns "Standby"
        every { context.getString(R.string.charge_state_charging) } returns "Charging"
        every { context.getString(R.string.charge_state_discharging) } returns "Discharging"
        every { context.getString(R.string.charge_state_full) } returns "Full"
        every { context.getString(R.string.status_searching) } returns "Searching"
        every { context.getString(R.string.status_not_available) } returns "N/A"
    }

    fun singleValueDataPoint(dataTypeId: String, value: Double?): DataPoint = DataPoint(
        dataTypeId = dataTypeId,
        values = value?.let { mapOf(DataType.Field.SINGLE to it) } ?: emptyMap()
    )

    beforeEach {
        context = mockk(relaxed = true)
        config = mockk(relaxed = true)
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

    should("emit UpdateNumericConfig for standard fields when startView is executed") {
        // given
        val dataType = ForumsladerDataType(
            EXTENSION_ID, "fl_battery_voltage",
            formatDataTypeId = null
        )
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        dataType.startView(context, config, emitter)
        cancelSlot.captured()

        // then
        verify { emitter.onNext(UpdateNumericConfig(dataType.dataTypeId)) }
    }

    should("use formatDataTypeId when provided for standard fields on startView execution") {
        // given
        val formatId = DataType.Type.POWER
        val dataType = ForumsladerDataType(EXTENSION_ID, "fl_dynamo_power", formatId)

        // when
        dataType.startView(context, config, emitter)

        // then
        verify { emitter.onNext(UpdateNumericConfig(formatId)) }
    }

    should("emit searching status when stream state is searching") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)

        // when
        dataType.handleStreamState(OnStreamState(StreamState.Searching), emitter, context)

        // then
        verify { emitter.onNext(ShowCustomStreamState("Searching", color = null)) }
    }

    should("emit not available status when stream state is not available") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)

        // when
        dataType.handleStreamState(OnStreamState(StreamState.NotAvailable), emitter, context)

        // then
        verify { emitter.onNext(ShowCustomStreamState("N/A", color = null)) }
    }

    should("emit standby status when streaming 0") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)
        val dataPoint = singleValueDataPoint(dataType.dataTypeId, 0.0)

        // when
        dataType.handleStreamState(
            OnStreamState(StreamState.Streaming(dataPoint)),
            emitter,
            context
        )

        // then
        verify { emitter.onNext(ShowCustomStreamState("Standby", color = null)) }
    }

    should("emit charging status when streaming valid charging value") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)
        val dataPoint = singleValueDataPoint(dataType.dataTypeId, 1.0)

        // when
        dataType.handleStreamState(
            OnStreamState(StreamState.Streaming(dataPoint)),
            emitter,
            context
        )

        // then
        verify { emitter.onNext(ShowCustomStreamState("Charging", color = null)) }
    }

    should("emit discharging status when streaming 2") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)
        val dataPoint = singleValueDataPoint(dataType.dataTypeId, 2.0)

        // when
        dataType.handleStreamState(
            OnStreamState(StreamState.Streaming(dataPoint)),
            emitter,
            context
        )

        // then
        verify { emitter.onNext(ShowCustomStreamState("Discharging", color = null)) }
    }

    should("emit full status when streaming 3") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)
        val dataPoint = singleValueDataPoint(dataType.dataTypeId, 3.0)

        // when
        dataType.handleStreamState(
            OnStreamState(StreamState.Streaming(dataPoint)),
            emitter,
            context
        )

        // then
        verify { emitter.onNext(ShowCustomStreamState("Full", color = null)) }
    }

    should("emit placeholder when streaming unknown value") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)
        val dataPoint = singleValueDataPoint(dataType.dataTypeId, 5.0)

        // when
        dataType.handleStreamState(
            OnStreamState(StreamState.Streaming(dataPoint)),
            emitter,
            context
        )

        // then
        verify { emitter.onNext(ShowCustomStreamState("---", color = null)) }
    }

    should("not emit anything when single value is missing") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)
        val dataPoint = singleValueDataPoint(dataType.dataTypeId, value = null)

        // when
        dataType.handleStreamState(
            OnStreamState(StreamState.Streaming(dataPoint)),
            emitter,
            context
        )

        // then
        verify(exactly = 0) { emitter.onNext(any()) }
    }

    should("disconnect KarooSystemService when view is cancelled") {
        // given
        val dataType = ForumsladerDataType(EXTENSION_ID, DataFieldId.CHARGE_STATE)
        every { anyConstructed<KarooSystemService>().addConsumer(any()) } returns "listener-id"

        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        dataType.startView(context, config, emitter)
        cancelSlot.captured()

        // then
        verify { anyConstructed<KarooSystemService>().removeConsumer("listener-id") }
        verify { anyConstructed<KarooSystemService>().disconnect() }
    }
})
