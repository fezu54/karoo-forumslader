package org.happycode.karoo.forumslader.extension

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.ChargeState
import java.util.concurrent.atomic.AtomicBoolean

class ChargeStateGraphicalDataType(
    extension: String,
    typeId: String = DataFieldId.CHARGE_STATE,
    private val timeProvider: () -> Long = SystemClock::elapsedRealtime,
    private val karooSystemFactory: (Context) -> KarooSystemService = { KarooSystemService(it) },
    private val handlerFactory: () -> Handler = { Handler(Looper.getMainLooper()) },
    private val streamConsumerRegistrar: (KarooSystemService, String, (OnStreamState) -> Unit) -> String =
        { karooSystem, id, onEvent ->
            karooSystem.addConsumer(OnStreamState.StartStreaming(id)) { event: OnStreamState -> onEvent(event) }
        }
) : DataTypeImpl(extension, typeId) {

    companion object {
        private const val THROTTLE_WINDOW_MS = 1050L
    }

    internal data class ChargeStateViewState(
        val label: String,
        val colorResId: Int,
        val isStatus: Boolean = false
    )

    internal inner class ViewSession(
        private val context: Context,
        private val config: ViewConfig,
        private val emitter: ViewEmitter,
        private val handler: Handler,
        private val isCancelled: () -> Boolean = { false }
    ) {
        private var lastViewState: ChargeStateViewState? = null
        private var lastEmitTimeMs: Long? = null
        private var pendingViews: RemoteViews? = null
        private var emitRunnable: Runnable? = null

        fun onStreamState(event: OnStreamState) {
            if (isCancelled()) return
            val state = createViewState(context, event.state)
            if (state == lastViewState) return
            lastViewState = state
            postUpdateView(createRemoteViews(context, config, state))
        }

        private fun postUpdateView(views: RemoteViews) {
            val now = timeProvider()
            val elapsed = lastEmitTimeMs?.let { now - it }
            if (elapsed == null || elapsed < 0L || elapsed >= THROTTLE_WINDOW_MS) {
                emitRunnable?.let { handler.removeCallbacks(it) }
                emitRunnable = null
                pendingViews = null
                emitter.updateView(views)
                lastEmitTimeMs = now
            } else {
                pendingViews = views
                emitRunnable?.let { handler.removeCallbacks(it) }
                val runnable = Runnable {
                    if (!isCancelled()) {
                        pendingViews?.let { pending ->
                            emitter.updateView(pending)
                            lastEmitTimeMs = timeProvider()
                            pendingViews = null
                        }
                    }
                    emitRunnable = null
                }
                emitRunnable = runnable
                handler.postDelayed(runnable, THROTTLE_WINDOW_MS - elapsed)
            }
        }

        fun cancel() {
            emitRunnable?.let { handler.removeCallbacks(it) }
            emitRunnable = null
            pendingViews = null
        }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val previewState = ChargeStateViewState(
                label = context.getString(R.string.charge_state_charging),
                colorResId = R.color.karoo_battery_good
            )
            emitter.updateView(createRemoteViews(context, config, previewState))
            return
        }

        val karooSystem = karooSystemFactory(context)
        val handler = handlerFactory()
        val cancelled = AtomicBoolean(false)
        val lock = Any()
        val session = ViewSession(context, config, emitter, handler, isCancelled = cancelled::get)
        var listenerId: String? = null

        emitter.setCancellable {
            cancelled.set(true)
            val idToRemove = synchronized(lock) {
                listenerId.also { listenerId = null }
            }
            idToRemove?.let { karooSystem.removeConsumer(it) }
            karooSystem.disconnect()
            handler.post { session.cancel() }
        }

        karooSystem.connect { connected ->
            if (!connected || cancelled.get()) {
                karooSystem.disconnect()
                return@connect
            }
            val registeredId = streamConsumerRegistrar(karooSystem, dataTypeId) { event: OnStreamState ->
                if (!cancelled.get()) {
                    handler.post { session.onStreamState(event) }
                }
            }
            val shouldRemove = synchronized(lock) {
                if (cancelled.get()) {
                    true
                } else {
                    listenerId = registeredId
                    false
                }
            }
            if (shouldRemove) {
                karooSystem.removeConsumer(registeredId)
                karooSystem.disconnect()
            }
        }
    }

    internal fun createViewState(
        context: Context,
        streamState: StreamState
    ): ChargeStateViewState = when (streamState) {
        is StreamState.Streaming -> when (streamState.dataPoint.singleValue?.toInt()?.let { ChargeState.entries.getOrNull(it) }) {
            ChargeState.STANDBY -> ChargeStateViewState(context.getString(R.string.charge_state_standby), R.color.karoo_text_primary)
            ChargeState.CHARGING -> ChargeStateViewState(context.getString(R.string.charge_state_charging), R.color.karoo_battery_good)
            ChargeState.DISCHARGING -> ChargeStateViewState(
                label = context.getString(R.string.charge_state_discharging),
                colorResId = R.color.karoo_battery_medium
            )
            ChargeState.FULL -> ChargeStateViewState(context.getString(R.string.charge_state_full), R.color.karoo_battery_good)
            null -> ChargeStateViewState("---", R.color.karoo_text_primary)
        }
        is StreamState.Searching -> ChargeStateViewState(
            label = context.getString(R.string.status_searching),
            colorResId = R.color.karoo_text_primary,
            isStatus = true
        )
        is StreamState.NotAvailable -> ChargeStateViewState(
            label = context.getString(R.string.status_not_available),
            colorResId = R.color.karoo_text_primary,
            isStatus = true
        )
        is StreamState.Idle -> ChargeStateViewState(
            label = "---",
            colorResId = R.color.karoo_text_primary
        )
    }

    internal fun createRemoteViews(
        context: Context,
        config: ViewConfig,
        state: ChargeStateViewState
    ): RemoteViews = RemoteViews(context.packageName, R.layout.custom_data_field_charge_state).apply {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1.0f)
        val viewWidthDp = config.viewSize.first / density
        val viewHeightDp = config.viewSize.second / density
        val availableWidthDp = (viewWidthDp - 8f).coerceAtLeast(20f)

        val isCompact = config.gridSize.first < 50
        val textScale = when {
            state.isStatus -> if (isCompact) 0.40f else 0.50f
            isCompact -> 0.55f
            else -> 0.70f
        }
        val maxHeightSp = viewHeightDp * if (state.isStatus) 0.38f else 0.52f
        val minSizeSp = if (state.isStatus) 10f else 12f
        val maxSizeSp = if (state.isStatus) 22f else 32f
        val textLen = state.label.length.coerceAtLeast(1)
        val maxWidthSp = availableWidthDp / (textLen * 0.58f)
        val textSizeSp = minOf(config.textSize * textScale, maxHeightSp, maxWidthSp)
            .coerceIn(minSizeSp, maxSizeSp)

        setTextViewText(R.id.charge_state_value, state.label)
        setTextViewTextSize(R.id.charge_state_value, TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        setTextColor(R.id.charge_state_value, ContextCompat.getColor(context, state.colorResId))
    }
}
