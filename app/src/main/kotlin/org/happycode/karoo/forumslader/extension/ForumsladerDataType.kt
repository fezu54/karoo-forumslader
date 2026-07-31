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
        if (typeId == DataFieldId.CHARGE_STATE) {
            val karooSystem = KarooSystemService(context)
            karooSystem.connect { connected ->
                if (connected) {
                    val listenerId = karooSystem.addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                        handleStreamState(event, emitter, context)
                    }
                    emitter.setCancellable {
                        karooSystem.removeConsumer(listenerId)
                        karooSystem.disconnect()
                    }
                }
            }
        } else {
            val formatId = formatDataTypeId ?: dataTypeId
            emitter.onNext(UpdateNumericConfig(formatId))
        }
    }

    internal fun handleStreamState(event: OnStreamState, emitter: ViewEmitter, context: Context) {
        val state = event.state
        if (state is StreamState.Streaming) {
            val value = state.dataPoint.singleValue
            if (value != null) {
                val stateStr = when(value.toInt()) {
                    0 -> context.getString(R.string.charge_state_standby)
                    1 -> context.getString(R.string.charge_state_charging)
                    2 -> context.getString(R.string.charge_state_discharging)
                    3 -> context.getString(R.string.charge_state_full)
                    else -> "---"
                }
                emitter.onNext(ShowCustomStreamState(stateStr, null))
            }
        } else if (state is StreamState.NotAvailable) {
            emitter.onNext(ShowCustomStreamState(context.getString(R.string.status_not_available), null))
        } else if (state is StreamState.Searching) {
            emitter.onNext(ShowCustomStreamState(context.getString(R.string.status_searching), null))
        }
    }
}
