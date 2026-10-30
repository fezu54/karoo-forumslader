package org.happycode.karoo.forumslader.extension

import android.content.Context
import androidx.core.content.ContextCompat
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

    internal var viewEmitter: ViewEmitter? = null
        private set

    internal var viewContext: Context? = null
        private set

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        viewEmitter = emitter
        viewContext = context

        if (typeId != DataFieldId.CHARGE_STATE) {
            val formatId = formatDataTypeId ?: dataTypeId
            emitter.onNext(UpdateNumericConfig(formatId))
        }

        when (typeId) {
            DataFieldId.CHARGE_STATE -> setupStreamConsumer(context, emitter) { event ->
                handleStreamState(event, emitter, context)
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
                val (stateStr, colorRes) = when (value.toInt()) {
                    0 -> Pair(context.getString(R.string.charge_state_standby), null)
                    1 -> Pair(context.getString(R.string.charge_state_charging), R.color.karoo_battery_good)
                    2 -> Pair(context.getString(R.string.charge_state_discharging), R.color.karoo_battery_medium)
                    3 -> Pair(context.getString(R.string.charge_state_full), R.color.karoo_battery_good)
                    else -> Pair("---", null)
                }
                val color = colorRes?.let { ContextCompat.getColor(context, it) }
                emitter.onNext(ShowCustomStreamState(stateStr, color = color))
            }
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
            is StreamState.NotAvailable -> onNext(ShowCustomStreamState(context.getString(R.string.status_not_available), color = null))
            is StreamState.Searching -> onNext(ShowCustomStreamState(context.getString(R.string.status_searching), color = null))
            is StreamState.Idle -> Unit
        }
    }
}
