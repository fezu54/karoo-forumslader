package org.happycode.karoo.forumslader.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

enum class LightLoadState {
    IDLE,
    SUSPECTED,
    DETECTED
}

class LightLoadDetector(
    private val thresholdAmperes: Float = DEFAULT_THRESHOLD_AMPERES,
    private val clearThresholdAmperes: Float = DEFAULT_CLEAR_THRESHOLD_AMPERES,
    private val debounceDuration: Duration = DEFAULT_DEBOUNCE_DURATION,
    private val usbGracePeriodDuration: Duration = DEFAULT_USB_GRACE_PERIOD_DURATION
) {
    var state: LightLoadState = LightLoadState.IDLE
        private set

    private var suspectedStartTime: Duration? = null
    private var previousConsumerCurrent: Float? = null
    private var suppressedUntil: Duration = Duration.ZERO

    fun calculateUnaccountedLoad(metrics: ForumsladerMetrics): Float {
        val voltage = metrics.power.batteryVoltage
        if (voltage <= 0f) return 0f

        val usbBattCurrent = (USB_VOLTAGE * metrics.power.consumerCurrent) / (voltage * BUCK_EFFICIENCY)
        val dynamoCurrent = metrics.power.dynamoPowerWatts / voltage
        return (-metrics.power.batteryCurrent - usbBattCurrent + dynamoCurrent).coerceAtLeast(0f)
    }

    fun onMetrics(
        metrics: ForumsladerMetrics,
        currentTime: Duration = System.currentTimeMillis().milliseconds
    ): LightLoadState {
        val consumerCurrent = metrics.power.consumerCurrent
        previousConsumerCurrent?.let { previous ->
            val stepChange = consumerCurrent - previous
            if (stepChange >= USB_STEP_CHANGE_THRESHOLD_AMPERES) {
                suppressedUntil = currentTime + usbGracePeriodDuration
            }
        }
        previousConsumerCurrent = consumerCurrent

        val isSuppressed = currentTime < suppressedUntil
        val load = calculateUnaccountedLoad(metrics)
        val isOverThreshold = load >= thresholdAmperes
        val isUnderClearThreshold = load < clearThresholdAmperes

        state = when (state) {
            LightLoadState.IDLE -> when {
                !isSuppressed && isOverThreshold -> {
                    suspectedStartTime = currentTime
                    if (debounceDuration == Duration.ZERO) LightLoadState.DETECTED else LightLoadState.SUSPECTED
                }
                else -> LightLoadState.IDLE
            }
            LightLoadState.SUSPECTED -> when {
                isSuppressed || isUnderClearThreshold -> {
                    suspectedStartTime = null
                    LightLoadState.IDLE
                }
                currentTime - (suspectedStartTime ?: currentTime) >= debounceDuration -> LightLoadState.DETECTED
                else -> LightLoadState.SUSPECTED
            }
            LightLoadState.DETECTED -> when {
                isUnderClearThreshold -> {
                    suspectedStartTime = null
                    LightLoadState.IDLE
                }
                else -> LightLoadState.DETECTED
            }
        }

        return state
    }

    fun reset() {
        state = LightLoadState.IDLE
        suspectedStartTime = null
        previousConsumerCurrent = null
        suppressedUntil = Duration.ZERO
    }

    companion object {
        const val DEFAULT_THRESHOLD_AMPERES = 0.35f
        const val DEFAULT_CLEAR_THRESHOLD_AMPERES = 0.20f
        const val USB_STEP_CHANGE_THRESHOLD_AMPERES = 0.15f
        const val USB_VOLTAGE = 5.0f
        const val BUCK_EFFICIENCY = 0.90f
        val DEFAULT_DEBOUNCE_DURATION = 3.seconds
        val DEFAULT_USB_GRACE_PERIOD_DURATION = 5.seconds
    }
}
