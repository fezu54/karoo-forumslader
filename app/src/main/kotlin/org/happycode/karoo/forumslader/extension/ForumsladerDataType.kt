package org.happycode.karoo.forumslader.extension

import android.content.Context
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
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

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (typeId != DataFieldId.CHARGE_STATE) {
            val formatId = formatDataTypeId ?: dataTypeId
            emitter.onNext(UpdateNumericConfig(formatId))
        }

        when (typeId) {
            DataFieldId.CHARGE_STATE -> setupStreamConsumer(context, emitter) { event ->
                handleStreamState(event, emitter, context)
            }
            DataFieldId.BATTERY_RANGE -> setupStreamConsumer(context, emitter) { event ->
                handleBatteryRangeStreamState(event, emitter, context)
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

    internal fun handleBatteryRangeStreamState(event: OnStreamState, emitter: ViewEmitter, context: Context) {
        emitter.handleCommonState(event, context) { streamingState ->
            val value = streamingState.dataPoint.singleValue
            val stateStr = when {
                value == null -> context.getString(R.string.battery_range_calculating)
                value.isInfinite() -> context.getString(R.string.charge_state_charging)
                else -> null
            }
            emitter.onNext(ShowCustomStreamState(stateStr, null))
        }
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
