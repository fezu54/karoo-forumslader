package org.happycode.karoo.forumslader.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

sealed interface ForumsladerAlert {
    val id: String
    val detail: String
    val title: String get() = "Forumslader"

    data class BatteryLow(val percentage: Int) : ForumsladerAlert {
        override val id = "fl_bat_low"
        override val detail = "Battery Low ($percentage%)"
    }

    object ShortCircuit : ForumsladerAlert {
        override val id = "fl_short"
        override val detail = "Short Circuit!"
    }

    object SystemInterrupt : ForumsladerAlert {
        override val id = "fl_sys_int"
        override val detail = "System Interrupt!"
    }

    data class HighTemperature(val temperature: Float) : ForumsladerAlert {
        override val id = "fl_hi_temp"
        override val detail = "High Temperature (${temperature.toInt()}°C)"
    }
}

interface AlertRule {
    fun evaluate(metrics: ForumsladerMetrics, currentTime: Duration): ForumsladerAlert?
}

class BatteryLowRule(
    private val thresholdProvider: () -> Int,
    private val muteDuration: Duration = 1.minutes
) : AlertRule {
    private var batteryLowTriggered = false
    private var mutedUntil = Duration.ZERO

    companion object {
        private const val BATTERY_RE_ARM_OFFSET = 8
    }

    override fun evaluate(metrics: ForumsladerMetrics, currentTime: Duration): ForumsladerAlert? {
        val batteryPercentage = metrics.power.batteryLevelPercentage ?: return null
        val threshold = thresholdProvider()
        
        if (!batteryLowTriggered && batteryPercentage < threshold) {
            batteryLowTriggered = true
            
            if (currentTime >= mutedUntil) {
                mutedUntil = currentTime + muteDuration
                return ForumsladerAlert.BatteryLow(batteryPercentage)
            }
        } else if (batteryLowTriggered && batteryPercentage >= threshold + BATTERY_RE_ARM_OFFSET) {
            batteryLowTriggered = false
        }

        return null
    }
}

class HighTemperatureRule(
    private val thresholdProvider: () -> Float,
    private val muteDuration: Duration = 1.minutes
) : AlertRule {
    private var mutedUntil = Duration.ZERO

    override fun evaluate(metrics: ForumsladerMetrics, currentTime: Duration): ForumsladerAlert? {
        val temperature = metrics.environment.temperatureCelsius
        if (temperature >= thresholdProvider() && currentTime >= mutedUntil) {
            mutedUntil = currentTime + muteDuration
            return ForumsladerAlert.HighTemperature(temperature)
        }
        return null
    }
}

class StatusBitmaskRule(
    private val bitmask: Int,
    private val alertToTrigger: ForumsladerAlert,
    private val muteDuration: Duration = 1.minutes
) : AlertRule {
    private var mutedUntil = Duration.ZERO

    override fun evaluate(metrics: ForumsladerMetrics, currentTime: Duration): ForumsladerAlert? {
        if ((metrics.power.statusMask and bitmask) != 0 && currentTime >= mutedUntil) {
            mutedUntil = currentTime + muteDuration
            return alertToTrigger
        }
        return null
    }
}

class ForumsladerAlertManager(private val rules: List<AlertRule>) {
    fun evaluate(
        forumsladerMetrics: ForumsladerMetrics,
        currentTime: Duration = System.currentTimeMillis().milliseconds
    ): List<ForumsladerAlert> {
        return rules.mapNotNull { it.evaluate(forumsladerMetrics, currentTime) }
    }
}
