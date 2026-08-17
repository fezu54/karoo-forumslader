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
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForumsladerDataTypeTest {

    private lateinit var context: Context
    private lateinit var config: ViewConfig
    private lateinit var emitter: ViewEmitter

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        config = mockk(relaxed = true)
        emitter = mockk(relaxed = true)

        mockkConstructor(KarooSystemService::class)
        every { anyConstructed<KarooSystemService>().connect(any()) } answers {
            val callback = firstArg<(Boolean) -> Unit>()
            callback(true)
        }
    }

    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `should emit UpdateNumericConfig for standard fields on startView`() {
        // given
        val dataType = ForumsladerDataType("karoo-forumslader", "fl_battery_voltage", null)

        // when
        dataType.startView(context, config, emitter)

        // then
        verify { emitter.onNext(UpdateNumericConfig(dataType.dataTypeId)) }
    }

    @Test
    fun `should use formatDataTypeId when provided for standard fields on startView`() {
        // given
        val formatId = DataType.Type.POWER
        val dataType = ForumsladerDataType("karoo-forumslader", "fl_dynamo_power", formatId)

        // when
        dataType.startView(context, config, emitter)

        // then
        verify { emitter.onNext(UpdateNumericConfig(formatId)) }
    }

    @Test
    fun `should emit searching status when stream state is searching`() {
        // given
        setupStringMocks()
        val dataType = ForumsladerDataType("karoo-forumslader", DataFieldId.CHARGE_STATE)

        // when
        dataType.handleStreamState(OnStreamState(StreamState.Searching), emitter, context)

        // then
        verify { emitter.onNext(ShowCustomStreamState("Searching", null)) }
    }

    @Test
    fun `should emit not available status when stream state is not available`() {
        // given
        setupStringMocks()
        val dataType = ForumsladerDataType("karoo-forumslader", DataFieldId.CHARGE_STATE)

        // when
        dataType.handleStreamState(OnStreamState(StreamState.NotAvailable), emitter, context)

        // then
        verify { emitter.onNext(ShowCustomStreamState("N/A", null)) }
    }

    @Test
    fun `should emit charging status when streaming valid charging value`() {
        // given
        setupStringMocks()
        val dataType = ForumsladerDataType("karoo-forumslader", DataFieldId.CHARGE_STATE)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(DataType.Field.SINGLE to 1.0)
        )

        // when
        dataType.handleStreamState(OnStreamState(StreamState.Streaming(dataPoint)), emitter, context)

        // then
        verify { emitter.onNext(ShowCustomStreamState("Charging", null)) }
    }

    @Test
    fun `should emit placeholder when streaming unknown value`() {
        // given
        setupStringMocks()
        val dataType = ForumsladerDataType("karoo-forumslader", DataFieldId.CHARGE_STATE)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(DataType.Field.SINGLE to 5.0)
        )

        // when
        dataType.handleStreamState(OnStreamState(StreamState.Streaming(dataPoint)), emitter, context)

        // then
        verify { emitter.onNext(ShowCustomStreamState("---", null)) }
    }

    @Test
    fun `should not emit anything when single value is missing`() {
        // given
        val dataType = ForumsladerDataType("karoo-forumslader", DataFieldId.CHARGE_STATE)
        val dataPoint = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = emptyMap()
        )

        // when
        dataType.handleStreamState(OnStreamState(StreamState.Streaming(dataPoint)), emitter, context)

        // then
        verify(exactly = 0) { emitter.onNext(any()) }
    }

    @Test
    fun `should disconnect KarooSystemService when view is cancelled`() {
        // given
        val dataType = ForumsladerDataType("karoo-forumslader", DataFieldId.CHARGE_STATE)
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

    private fun setupStringMocks() {
        every { context.getString(R.string.charge_state_standby) } returns "Standby"
        every { context.getString(R.string.charge_state_charging) } returns "Charging"
        every { context.getString(R.string.charge_state_discharging) } returns "Discharging"
        every { context.getString(R.string.charge_state_full) } returns "Full"
        every { context.getString(R.string.status_searching) } returns "Searching"
        every { context.getString(R.string.status_not_available) } returns "N/A"
    }
}
