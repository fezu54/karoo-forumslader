package org.happycode.karoo.forumslader.extension

import fi.nikosavola.karooext.testing.ViewRecorder
import fi.nikosavola.karooext.testing.ViewUpdate
import fi.nikosavola.karooext.testing.robolectric.FakeKarooRule
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The real DataTypeImpl views run against the fake system, so the stream consumer each view
// registers and the custom states it emits are exercised end to end.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerDataTypeViewFakeTest {
    @get:Rule val karoo = FakeKarooRule()

    private val chargeStateView get() = DataType.dataTypeId(EXTENSION_ID, DataFieldId.CHARGE_STATE)

    @Test
    fun `should show the charging label when the charge state stream reports charging`() {
        val view = karoo.host<ForumsladerExtension>().startView(
            DataFieldId.CHARGE_STATE,
            viewConfig(),
        )
        karoo.awaitStreamConsumer(chargeStateView)

        karoo.system.setDataPoint(chargeStateView, CHARGE_STATE_CHARGING)

        assertEquals(
            karoo.app.getString(R.string.charge_state_charging),
            view.awaitCustomState(),
        )
    }

    @Test
    fun `should show the searching label when the charge state stream is searching`() {
        val view = karoo.host<ForumsladerExtension>().startView(
            DataFieldId.CHARGE_STATE,
            viewConfig(),
        )
        karoo.awaitStreamConsumer(chargeStateView)

        karoo.system.setStreamState(chargeStateView, StreamState.Searching)

        assertEquals(karoo.app.getString(R.string.status_searching), view.awaitCustomState())
    }

    @Test
    fun `should show the not available label when the charge state stream is not available`() {
        val view = karoo.host<ForumsladerExtension>().startView(
            DataFieldId.CHARGE_STATE,
            viewConfig(),
        )
        karoo.awaitStreamConsumer(chargeStateView)

        karoo.system.setStreamState(chargeStateView, StreamState.NotAvailable)

        assertEquals(karoo.app.getString(R.string.status_not_available), view.awaitCustomState())
    }

    @Test
    fun `should request numeric formatting when a numeric field view starts`() {
        val view = karoo.host<ForumsladerExtension>().startView(
            DataFieldId.BATTERY_VOLTAGE,
            viewConfig(),
        )

        val config = view.await(5_000) { it is ViewUpdate.Event && it.event is UpdateNumericConfig }
            .let { (it as ViewUpdate.Event).event as UpdateNumericConfig }

        assertEquals(DataType.dataTypeId(EXTENSION_ID, DataFieldId.BATTERY_VOLTAGE), config.formatDataTypeId)
    }

    @Test
    fun `should release the view consumer when the view session stops`() {
        val host = karoo.host<ForumsladerExtension>()
        val before = karoo.system.consumerCount
        val view = host.startView(DataFieldId.CHARGE_STATE, viewConfig())
        karoo.awaitStreamConsumer(chargeStateView)
        assertEquals(before + 1, karoo.system.consumerCount)

        host.stopView(view)

        assertEquals(before, karoo.system.consumerCount)
    }

    private fun viewConfig() = ViewConfig(
        gridSize = 30 to 15,
        viewSize = 240 to 90,
        textSize = 16,
    )

    private fun ViewRecorder.awaitCustomState(): String? =
        await(5_000) { it is ViewUpdate.Event && it.event is ShowCustomStreamState }
            .let { (it as ViewUpdate.Event).event as ShowCustomStreamState }
            .message

    private companion object {
        const val EXTENSION_ID = "karoo-forumslader"
        const val CHARGE_STATE_CHARGING = 1.0
    }
}
