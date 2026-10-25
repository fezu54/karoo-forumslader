package org.happycode.karoo.forumslader.extension

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import io.hammerhead.karooext.models.ViewConfig
import org.happycode.karoo.forumslader.PreferencesConstants.DEFAULT_BATTERY_LOW_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_BATTERY_LOW_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.ChargeState
import kotlin.math.roundToInt

class ForumsladerGraphicalDataType(
    extension: String,
    typeId: String = DataFieldId.BATTERY_LEVEL,
    private val timeProvider: () -> Long = System::currentTimeMillis,
    private val karooSystemFactory: (Context) -> KarooSystemService = { KarooSystemService(it) }
) : DataTypeImpl(extension, typeId) {

    companion object {
        private const val KM_TO_MILES = 0.621371
        private const val THROTTLE_WINDOW_MS = 950L
    }

    internal data class BatteryViewState(
        val levelText: String,
        val levelUnitText: String,
        val levelColorResId: Int,
        val rangeText: String,
        val rangeUnitText: String,
        val isRangeNumeric: Boolean
    )

    private var lastEmitTimeMs = 0L
    private var pendingViews: RemoteViews? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var emitRunnable: Runnable? = null

    internal var isImperial: Boolean = false
        private set
    internal var lastStreamState: StreamState = StreamState.Searching
        private set
    internal var lowThreshold: Int = DEFAULT_BATTERY_LOW_THRESHOLD
        private set

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        lowThreshold = prefs.getInt(KEY_BATTERY_LOW_THRESHOLD, DEFAULT_BATTERY_LOW_THRESHOLD)

        if (config.preview) {
            val previewState = BatteryViewState(
                levelText = "85",
                levelUnitText = "%",
                levelColorResId = R.color.karoo_battery_good,
                rangeText = "120",
                rangeUnitText = context.getString(R.string.unit_km),
                isRangeNumeric = true
            )
            val views = createRemoteViews(context, config, previewState)
            emitter.updateView(views)
            return
        }

        isImperial = false
        lastStreamState = StreamState.Searching

        val karooSystem = karooSystemFactory(context)
        karooSystem.connect { connected ->
            if (connected) {
                val streamListenerId = karooSystem.addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                    handleStreamState(event, context, config, emitter)
                }

                val userProfileListenerId = karooSystem.addConsumer { profile: UserProfile ->
                    handleUserProfile(profile, context, config, emitter)
                }

                emitter.setCancellable {
                    emitRunnable?.let { mainHandler.removeCallbacks(it) }
                    pendingViews = null
                    karooSystem.removeConsumer(streamListenerId)
                    karooSystem.removeConsumer(userProfileListenerId)
                    karooSystem.disconnect()
                }
            }
        }
    }

    internal fun handleStreamState(
        event: OnStreamState,
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter
    ) {
        lastStreamState = event.state
        val state = createViewState(context, lastStreamState, isImperial, lowThreshold)
        val views = createRemoteViews(context, config, state)
        postUpdateView(emitter, views)
    }

    internal fun handleUserProfile(
        profile: UserProfile,
        context: Context,
        config: ViewConfig,
        emitter: ViewEmitter
    ) {
        val imperial = profile.preferredUnit.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL
        if (isImperial != imperial) {
            isImperial = imperial
            val state = createViewState(context, lastStreamState, isImperial, lowThreshold)
            val views = createRemoteViews(context, config, state)
            postUpdateView(emitter, views)
        }
    }

    internal fun createViewState(
        context: Context,
        streamState: StreamState,
        isImperial: Boolean,
        lowThreshold: Int
    ): BatteryViewState = when (streamState) {
        is StreamState.Streaming -> {
            val dataPoint = streamState.dataPoint
            val batteryLevel = dataPoint.values[DataFieldId.FIELD_BATTERY_LEVEL]
                ?: dataPoint.values[DataType.Field.SINGLE]

            val chargeStateOrdinal = dataPoint.values[DataFieldId.FIELD_CHARGE_STATE]?.toInt()
            val chargeState = chargeStateOrdinal?.let { ChargeState.entries.getOrNull(it) } ?: ChargeState.DISCHARGING

            val levelText = batteryLevel?.toInt()?.toString() ?: "---"
            val levelUnitText = if (chargeState == ChargeState.CHARGING) "⚡%" else "%"
            val levelColorResId = batteryLevel?.toInt()?.let { resolveBatteryColor(it, lowThreshold) }
                ?: R.color.karoo_text_primary

            val rangeKm = dataPoint.values[DataFieldId.FIELD_RANGE_KM]
            val routeSufficient = dataPoint.values[DataFieldId.FIELD_ROUTE_SUFFICIENT]
            val distanceUnit = if (isImperial) context.getString(R.string.unit_mi) else context.getString(R.string.unit_km)
            val suffix = when (routeSufficient) {
                1.0 -> " ✓"
                0.0 -> " !"
                else -> ""
            }

            val (rangeText, rangeUnitText, isNumeric) = when {
                chargeState == ChargeState.CHARGING -> Triple(context.getString(R.string.charge_state_charging), "", false)
                chargeState == ChargeState.FULL -> Triple(context.getString(R.string.charge_state_full), "", false)
                rangeKm != null -> {
                    val displayRange = if (isImperial) rangeKm * KM_TO_MILES else rangeKm
                    Triple(displayRange.roundToInt().toString(), "$distanceUnit$suffix", true)
                }
                else -> Triple(context.getString(R.string.battery_range_calc_short), "", false)
            }

            BatteryViewState(
                levelText = levelText,
                levelUnitText = levelUnitText,
                levelColorResId = levelColorResId,
                rangeText = rangeText,
                rangeUnitText = rangeUnitText,
                isRangeNumeric = isNumeric
            )
        }
        is StreamState.Searching -> BatteryViewState(
            levelText = "---",
            levelUnitText = "%",
            levelColorResId = R.color.karoo_text_primary,
            rangeText = context.getString(R.string.status_searching),
            rangeUnitText = "",
            isRangeNumeric = false
        )
        is StreamState.NotAvailable -> BatteryViewState(
            levelText = "---",
            levelUnitText = "%",
            levelColorResId = R.color.karoo_text_primary,
            rangeText = context.getString(R.string.status_not_available),
            rangeUnitText = "",
            isRangeNumeric = false
        )
        is StreamState.Idle -> BatteryViewState(
            levelText = "---",
            levelUnitText = "%",
            levelColorResId = R.color.karoo_text_primary,
            rangeText = "---",
            rangeUnitText = "",
            isRangeNumeric = false
        )
    }

    internal fun resolveBatteryColor(level: Int, lowThreshold: Int): Int = when {
        level <= lowThreshold -> R.color.karoo_battery_low
        level <= 50 -> R.color.karoo_battery_medium
        else -> R.color.karoo_battery_good
    }

    internal fun createRemoteViews(
        context: Context,
        config: ViewConfig,
        state: BatteryViewState
    ): RemoteViews = RemoteViews(context.packageName, R.layout.custom_data_field_battery).apply {
        val isCompact = config.gridSize.first < 50
        val digitScale = if (isCompact) 0.7f else 0.9f
        val textScale = if (isCompact) 0.5f else 0.65f
        val unitScale = if (isCompact) 0.35f else 0.45f

        val digitSizeSp = (config.textSize * digitScale).coerceAtLeast(14f)
        val textSizeSp = (config.textSize * textScale).coerceAtLeast(11f)
        val unitSizeSp = (config.textSize * unitScale).coerceAtLeast(10f)

        setTextViewText(R.id.battery_level_value, state.levelText)
        setTextViewTextSize(R.id.battery_level_value, TypedValue.COMPLEX_UNIT_SP, digitSizeSp)
        setTextColor(R.id.battery_level_value, ContextCompat.getColor(context, state.levelColorResId))

        setTextViewText(R.id.battery_level_unit, state.levelUnitText)
        setTextViewTextSize(R.id.battery_level_unit, TypedValue.COMPLEX_UNIT_SP, unitSizeSp)

        setTextViewText(R.id.battery_range_value, state.rangeText)
        setTextViewTextSize(
            R.id.battery_range_value,
            TypedValue.COMPLEX_UNIT_SP,
            if (state.isRangeNumeric) digitSizeSp else textSizeSp
        )

        setTextViewText(R.id.battery_range_unit, state.rangeUnitText)
        setTextViewTextSize(R.id.battery_range_unit, TypedValue.COMPLEX_UNIT_SP, unitSizeSp)
    }

    private fun postUpdateView(emitter: ViewEmitter, views: RemoteViews) {
        val now = timeProvider()
        val elapsed = now - lastEmitTimeMs
        if (elapsed >= THROTTLE_WINDOW_MS) {
            emitRunnable?.let { mainHandler.removeCallbacks(it) }
            emitter.updateView(views)
            lastEmitTimeMs = now
            pendingViews = null
        } else {
            pendingViews = views
            emitRunnable?.let { mainHandler.removeCallbacks(it) }
            val runnable = Runnable {
                pendingViews?.let { v ->
                    emitter.updateView(v)
                    lastEmitTimeMs = timeProvider()
                    pendingViews = null
                }
            }
            emitRunnable = runnable
            mainHandler.postDelayed(runnable, THROTTLE_WINDOW_MS - elapsed)
        }
    }
}
