package org.happycode.karoo.forumslader.extension

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.HideCustomStreamState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.ShowCustomStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId

class ForumsladerDataType(
    extension: String,
    typeId: String,
    private val formatDataTypeId: String? = null
) : DataTypeImpl(extension, typeId) {

    internal var activeCustomState: String? = null
        private set

    internal var viewEmitter: ViewEmitter? = null
        private set

    internal var streamEmitter: Emitter<StreamState>? = null
        private set

    internal var viewContext: Context? = null
        private set

    override fun startStream(emitter: Emitter<StreamState>) {
        streamEmitter = emitter
        emitter.setCancellable {
            if (streamEmitter == emitter) {
                streamEmitter = null
            }
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        viewEmitter = emitter
        viewContext = context

        if (typeId != DataFieldId.CHARGE_STATE) {
            val formatId = formatDataTypeId ?: dataTypeId
            emitter.onNext(UpdateNumericConfig(formatId))
        }

        if (typeId == DataFieldId.BATTERY_RANGE) {
            val initialState = activeCustomState?.let { ShowCustomStreamState(it, null) } ?: HideCustomStreamState
            emitter.onNext(initialState)
        }

        when (typeId) {
            DataFieldId.CHARGE_STATE -> setupStreamConsumer(context, emitter) { event ->
                handleStreamState(event, emitter, context)
            }
            DataFieldId.BATTERY_RANGE -> setupStreamConsumer(context, emitter) { event ->
                handleBatteryRangeStreamState(event, emitter, context)
            }
            else -> emitter.setCancellable {
                if (viewEmitter == emitter) {
                    viewEmitter = null
                    viewContext = null
                }
            }
        }
    }

    internal fun handleStreamState(event: OnStreamState, emitter: ViewEmitter, context: Context) {
        emitter.handleCommonState(event, context) { streamingState ->
            streamingState.dataPoint.singleValue?.let { value ->
                val stateStr = when (value.toInt()) {
                    0 -> context.getString(R.string.charge_state_standby)
                    1 -> context.getString(R.string.charge_state_charging)
                    2 -> context.getString(R.string.charge_state_discharging)
                    3 -> context.getString(R.string.charge_state_full)
                    else -> "---"
                }
                emitter.onNext(ShowCustomStreamState(stateStr, null))
            }
        }
    }

    internal fun handleBatteryRangeStreamState(
        event: OnStreamState,
        emitter: ViewEmitter? = viewEmitter,
        context: Context? = viewContext
    ) {
        val targetEmitter = emitter ?: viewEmitter
        val ctx = context ?: viewContext
        when (val state = event.state) {
            is StreamState.Streaming -> handleBatteryRangeValue(state.dataPoint.singleValue, targetEmitter, ctx)
            is StreamState.NotAvailable -> handleStatusState(
                ctx?.getString(R.string.status_not_available) ?: "N/A",
                StreamState.NotAvailable,
                targetEmitter
            )
            is StreamState.Searching -> handleStatusState(
                ctx?.getString(R.string.status_searching) ?: "Searching",
                StreamState.Searching,
                targetEmitter
            )
            is StreamState.Idle -> Unit
        }
    }

    private fun handleStatusState(
        stateStr: String,
        streamState: StreamState,
        emitter: ViewEmitter?
    ) {
        activeCustomState = stateStr
        emitter?.onNext(ShowCustomStreamState(stateStr, null))
        streamEmitter?.onNext(streamState)
    }

    private fun handleBatteryRangeValue(
        value: Double?,
        emitter: ViewEmitter?,
        context: Context?
    ) {
        val stateStr = resolveRangeStateString(value, context)
        activeCustomState = stateStr
        emitter?.onNext(stateStr?.let { ShowCustomStreamState(it, null) } ?: HideCustomStreamState)

        when {
            isSentinelRangeValue(value) -> streamEmitter?.onNext(StreamState.Searching)
            value != null && value in 0.0..<DataFieldId.BATTERY_RANGE_CALCULATING -> streamEmitter?.onNext(
                StreamState.Streaming(
                    DataPoint(
                        dataTypeId,
                        mapOf(DataType.Field.SINGLE to value)
                    )
                )
            )
            else -> streamEmitter?.onNext(StreamState.NotAvailable)
        }
    }

    private fun isSentinelRangeValue(value: Double?): Boolean =
        value == null || value >= DataFieldId.BATTERY_RANGE_CALCULATING || value.isInfinite()

    private fun resolveRangeStateString(value: Double?, context: Context?): String? = when {
        value == null || value == DataFieldId.BATTERY_RANGE_CALCULATING ->
            context?.getString(R.string.battery_range_calculating) ?: "Calculating…"
        value == DataFieldId.BATTERY_RANGE_CHARGING || value.isInfinite() ->
            context?.getString(R.string.charge_state_charging) ?: "Charging"
        value in 0.0..<DataFieldId.BATTERY_RANGE_CALCULATING -> null
        else -> context?.getString(R.string.status_not_available) ?: "N/A"
    }

    private fun setupStreamConsumer(
        context: Context,
        emitter: ViewEmitter,
        consumer: (OnStreamState) -> Unit
    ) {
        val karooSystem = KarooSystemService(context)
        karooSystem.connect { connected ->
            if (connected) {
                val listenerId = karooSystem.addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                    consumer(event)
                }
                emitter.setCancellable {
                    if (viewEmitter == emitter) {
                        viewEmitter = null
                        viewContext = null
                    }
                    karooSystem.removeConsumer(listenerId)
                    karooSystem.disconnect()
                }
            }
        }
    }

    private inline fun ViewEmitter.handleCommonState(
        event: OnStreamState,
        context: Context,
        onStreaming: (StreamState.Streaming) -> Unit
    ) {
        when (val state = event.state) {
            is StreamState.Streaming -> onStreaming(state)
            is StreamState.NotAvailable -> onNext(ShowCustomStreamState(context.getString(R.string.status_not_available), null))
            is StreamState.Searching -> onNext(ShowCustomStreamState(context.getString(R.string.status_searching), null))
            is StreamState.Idle -> Unit
        }
    }
}
