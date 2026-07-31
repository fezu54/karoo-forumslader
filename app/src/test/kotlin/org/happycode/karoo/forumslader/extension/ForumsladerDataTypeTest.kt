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
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `startView should emit UpdateNumericConfig for standard fields`() {
        val dataType = ForumsladerDataType("karoo-forumslader", "fl_battery_voltage", null)
        dataType.startView(context, config, emitter)

        verify { emitter.onNext(UpdateNumericConfig(dataType.dataTypeId)) }
    }

    @Test
    fun `startView should use formatDataTypeId if provided for standard fields`() {
        val formatId = DataType.Type.POWER
        val dataType = ForumsladerDataType("karoo-forumslader", "fl_dynamo_power", formatId)
        dataType.startView(context, config, emitter)

        verify { emitter.onNext(UpdateNumericConfig(formatId)) }
    }

    @Test
    fun `handleStreamState should emit custom strings for stream states`() {
        every { context.getString(R.string.charge_state_standby) } returns "Standby"
        every { context.getString(R.string.charge_state_charging) } returns "Charging"
        every { context.getString(R.string.charge_state_discharging) } returns "Discharging"
        every { context.getString(R.string.charge_state_full) } returns "Full"
        every { context.getString(R.string.status_searching) } returns "Searching"
        every { context.getString(R.string.status_not_available) } returns "N/A"

        val dataType = ForumsladerDataType("karoo-forumslader", DataFieldId.CHARGE_STATE)
        
        // Test searching
        dataType.handleStreamState(OnStreamState(StreamState.Searching), emitter, context)
        verify { emitter.onNext(ShowCustomStreamState("Searching", null)) }

        // Test not available
        dataType.handleStreamState(OnStreamState(StreamState.NotAvailable), emitter, context)
        verify { emitter.onNext(ShowCustomStreamState("N/A", null)) }

        // Test charging
        val dataPointCharging = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(DataType.Field.SINGLE to 1.0)
        )
        dataType.handleStreamState(OnStreamState(StreamState.Streaming(dataPointCharging)), emitter, context)
        verify { emitter.onNext(ShowCustomStreamState("Charging", null)) }

        // Test unknown
        val dataPointUnknown = DataPoint(
            dataTypeId = dataType.dataTypeId,
            values = mapOf(DataType.Field.SINGLE to 5.0)
        )
        dataType.handleStreamState(OnStreamState(StreamState.Streaming(dataPointUnknown)), emitter, context)
        verify { emitter.onNext(ShowCustomStreamState("---", null)) }
    }

    @Test
    fun `startView for charge state should register cancellable to disconnect`() {
        val dataType = ForumsladerDataType("karoo-forumslader", DataFieldId.CHARGE_STATE)
        
        // We use Any here to bypass capturing the internal listener class
        every {
            anyConstructed<KarooSystemService>().addConsumer(any())
        } returns "listener-id"

        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        dataType.startView(context, config, emitter)

        val cancelCallback = cancelSlot.captured
        cancelCallback()

        verify { anyConstructed<KarooSystemService>().removeConsumer("listener-id") }
        verify { anyConstructed<KarooSystemService>().disconnect() }
    }
}
