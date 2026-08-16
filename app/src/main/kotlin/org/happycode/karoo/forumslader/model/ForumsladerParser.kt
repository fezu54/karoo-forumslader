package org.happycode.karoo.forumslader.model

import android.util.Log
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ForumsladerParser(private val config: ForumsladerConfig? = null) {

    companion object {
        private const val TAG = "FL_Parser"
        private const val DEBUG_SENTENCE_PARSING = true // Set to false to reduce verbose logs
    }

    private val frameBuffer = StringBuilder()

    // Stateful metrics updated incrementally by various sentences
    private var batteryVoltage: Float = 0f
    private var batteryCurrent: Float = 0f
    private var consumerCurrent: Float = 0f
    private var batteryLevelPercentage: Int? = null
    private var currentFrequency: Float = 0f
    private var speedMetersPerSecond: Float = 0f
    private var tripDistanceMeters: Double = 0.0
    private var totalDistanceMeters: Double = 0.0
    private var temperatureCelsius: Float = 0f
    private var altitudeMeters: Float = 0f

    private var generatorGear: Int = 0
    private var chargeState: ChargeState = ChargeState.STANDBY
    private var tripEnergyWattHours: Double = 0.0
    private var tourEnergyWattHours: Double = 0.0
    private var dayPulseOffset: Double = 0.0
    private var tourPulseOffset: Double = 0.0
    private var currentImpulseCounter: Double = 0.0
    private var odometerMeters: Double = 0.0
    private var dayDistanceMeters: Double = 0.0
    private var tourDistanceMeters: Double = 0.0
    private var currentStatusMask: Int = 0

    // Configuration parameters
    private var wheelsize: Int = config?.wheelsize ?: 2200 // default fallback in mm
    private var poles: Int = config?.poles ?: 14       // default fallback (pole pairs)
    var version: ForumsladerVersion = config?.version ?: ForumsladerVersion.Unknown
        internal set
    
    private val _isConfigLoadedFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isConfigLoadedFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isConfigLoadedFlow

    // Track whether we've received a primary telemetry sentence (FL5/FL6/FLD)
    // Configuration sentences (FLP, FLC, FLB) should not trigger emissions
    private var hasReceivedTelemetry: Boolean = false

    // Statistics for debugging
    private var totalFramesParsed: Int = 0
    private var totalMetricsEmitted: Int = 0

    /**
     * Collects incoming byte arrays from the BLE onCharacteristicChanged callback,
     * extracts complete lines, parses them, and returns the updated metrics.
     * 
     * Only returns metrics after receiving a primary telemetry sentence (FL5, FL6, or FLD),
     * ensuring that configuration data has had time to be received first.
     */
    fun processIncomingBytes(data: ByteArray): ForumsladerMetrics? {
        val chunk = String(data, Charsets.US_ASCII)
        frameBuffer.append(chunk)

        var parsedTelemetry = false
        var parsedSentenceType: String? = null

        while (true) {
            val delimiterIndex = frameBuffer.indexOf("\n")
            if (delimiterIndex == -1) break

            val completeFrame = frameBuffer.substring(0, delimiterIndex).trim()
            frameBuffer.delete(0, delimiterIndex + 1)

            if (completeFrame.isEmpty()) continue

            val sentenceType = extractSentenceType(completeFrame)
            if (parseAsciiPayload(completeFrame)) {
                totalFramesParsed++
                
                // Track if this was a telemetry sentence
                if (isTelemetrySentence(completeFrame)) {
                    hasReceivedTelemetry = true
                    parsedTelemetry = true
                    parsedSentenceType = sentenceType
                    
                    if (DEBUG_SENTENCE_PARSING) {
                        Log.d(TAG, "Telemetry sentence parsed: $sentenceType (frame #$totalFramesParsed)")
                    }
                } else if (DEBUG_SENTENCE_PARSING) {
                    Log.d(TAG, "Configuration sentence parsed: $sentenceType (frame #$totalFramesParsed)")
                }
            } else if (DEBUG_SENTENCE_PARSING) {
                Log.d(TAG, "Failed to parse sentence: $sentenceType | Payload: ${completeFrame.take(60)}")
            }
        }

        // Only return metrics if we've received a telemetry sentence in this batch
        // (configuration-only frames like FLP, FLC, FLB) don't trigger emissions
        return if (parsedTelemetry && hasReceivedTelemetry) {
            val dynamoPowerWatts = batteryVoltage * kotlin.math.abs(batteryCurrent + consumerCurrent)
            val metrics = ForumsladerMetrics(
                power = ForumsladerMetrics.Power(
                    batteryVoltage = batteryVoltage,
                    batteryCurrent = batteryCurrent,
                    consumerCurrent = consumerCurrent,
                    batteryLevelPercentage = batteryLevelPercentage,
                    chargeState = chargeState,
                    dynamoPowerWatts = dynamoPowerWatts,
                    statusMask = currentStatusMask
                ),
                dynamics = ForumsladerMetrics.Dynamics(
                    frequency = currentFrequency,
                    speedMetersPerSecond = speedMetersPerSecond,
                    generatorGear = generatorGear
                ),
                environment = ForumsladerMetrics.Environment(
                    temperatureCelsius = temperatureCelsius,
                    altitudeMeters = altitudeMeters
                ),
                energy = ForumsladerMetrics.Energy(
                    tripWattHours = tripEnergyWattHours,
                    tourWattHours = tourEnergyWattHours
                ),
                distance = ForumsladerMetrics.Distance(
                    tripMeters = tripDistanceMeters,
                    dayMeters = dayDistanceMeters,
                    tourMeters = tourDistanceMeters,
                    odometerMeters = odometerMeters
                )
            )
            totalMetricsEmitted++
            
            Log.i(TAG, "Metrics emitted (#$totalMetricsEmitted) from $parsedSentenceType | " +
                "V=${String.format("%.2f", batteryVoltage)}V I=${String.format("%.2f", batteryCurrent)}A " +
                "Speed=${String.format("%.1f", speedMetersPerSecond * 3.6f)}km/h Trip=${String.format("%.2f", tripDistanceMeters / 1000.0)}km " +
                "Batt=$batteryLevelPercentage% Temp=${String.format("%.1f", temperatureCelsius)}°C " +
                "[Config: WS=$wheelsize poles=$poles version=${version.key}]")
            
            metrics
        } else {
            null
        }
    }

    private fun updateConfig(newWheelsize: Int, newPoles: Int) {
        if (newWheelsize != wheelsize || newPoles != poles) {
            Log.i(TAG, "Configuration updated: wheelsize $wheelsize -> $newWheelsize mm, " +
                "poles $poles -> $newPoles (pole pairs)")
            wheelsize = newWheelsize
            poles = newPoles
            config?.let {
                it.wheelsize = newWheelsize
                it.poles = newPoles
            }
        }
    }

    private fun updateVersion(newVersion: ForumsladerVersion) {
        if (version != newVersion) {
            Log.i(TAG, "Device version changed: ${version.key} -> ${newVersion.key}")
            version = newVersion
            config?.version = newVersion
        }
    }

    private fun extractSentenceType(payload: String): String {
        return try {
            if (!payload.startsWith("$")) return "UNKNOWN"
            
            val starIndex = payload.lastIndexOf('*')
            val semiIndex = payload.indexOf(';')
            
            val dataString: String = when {
                starIndex != -1 -> payload.substring(1, starIndex)
                semiIndex != -1 -> payload.substring(1, semiIndex)
                else -> payload.substring(1)
            }
            
            dataString.split(",").getOrNull(0) ?: "UNKNOWN"
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sentence: $payload", e)
            "ERROR"
        }
    }

    private fun isTelemetrySentence(payload: String): Boolean =
        extractSentenceType(payload) in listOf("FL5", "FL6", "FLD")

    private fun parseAsciiPayload(payload: String): Boolean {
        if (!payload.startsWith("$")) return false

        return try {
            val starIndex = payload.lastIndexOf('*')
            val semiIndex = payload.indexOf(';')

            val dataString: String
            val checksumString: String

            if (starIndex != -1) {
                dataString = payload.substring(1, starIndex)
                checksumString = payload.substring(starIndex + 1).trim()
            } else if (semiIndex != -1) {
                dataString = payload.substring(1, semiIndex)
                checksumString = ""
            } else {
                dataString = payload.substring(1)
                checksumString = ""
            }

            if (checksumString.isNotEmpty()) {
                var calculatedParity = 0
                for (char in dataString) {
                    calculatedParity = calculatedParity xor char.code
                }
                val expectedParity = checksumString.toIntOrNull(16)
                if (expectedParity != null && calculatedParity != expectedParity) {
                    Log.w(TAG, "Checksum mismatch for ${dataString.split(",").getOrNull(0)}: " +
                        "expected=$checksumString calculated=${calculatedParity.toString(16).uppercase()}")
                    return false
                }
            }

            val tokens = dataString.split(",")
            val header = tokens.getOrNull(0) ?: return false

            when (header) {
                "FL5", "FL6" -> {
                    updateVersion(if (header == "FL6") ForumsladerVersion.V6 else ForumsladerVersion.V5)
                    
                    val statusStr = tokens.getOrNull(1)
                    val statusInt = statusStr?.let {
                        if (it.startsWith("0x", ignoreCase = true)) it.substring(2).toIntOrNull(16)
                        else it.toIntOrNull(16)
                    } ?: 0
                    currentStatusMask = statusInt

                    chargeState = when {
                        (statusInt and 0x8000) != 0 -> ChargeState.FULL
                        (statusInt and 0x200) != 0 -> ChargeState.CHARGING
                        (statusInt and 0x100) != 0 -> ChargeState.DISCHARGING
                        else -> ChargeState.STANDBY
                    }
                    
                    generatorGear = tokens.getOrNull(2)?.toIntOrNull() ?: generatorGear
                    
                    val frequency = tokens.getOrNull(3)?.toFloatOrNull() ?: 0f
                    val cell1 = tokens.getOrNull(4)?.toFloatOrNull() ?: 0f
                    val cell2 = tokens.getOrNull(5)?.toFloatOrNull() ?: 0f
                    val cell3 = tokens.getOrNull(6)?.toFloatOrNull() ?: 0f
                    batteryVoltage = (cell1 + cell2 + cell3) / 1000f
                    batteryCurrent = (tokens.getOrNull(7)?.toFloatOrNull() ?: 0f) / 1000f
                    consumerCurrent = (tokens.getOrNull(8)?.toFloatOrNull() ?: 0f) / 1000f

                    val impulseCounter = tokens.getOrNull(version.impulseIndex)?.toDoubleOrNull() ?: 0.0
                    currentImpulseCounter = impulseCounter

                    val frequencyToSpeedFactor = wheelsize.toFloat() / poles.toFloat() / 1000f * version.frequencyScale
                    currentFrequency = frequency
                    val multiplier = config?.speedMultiplier ?: 1.0f
                    speedMetersPerSecond = frequency * frequencyToSpeedFactor * multiplier

                    val impulsesToOdometerFactor = wheelsize.toDouble() / poles.toDouble() / 1000.0 * version.impulseScale
                    tripDistanceMeters = impulseCounter * impulsesToOdometerFactor * multiplier.toDouble()
                    totalDistanceMeters = tripDistanceMeters
                    
                    odometerMeters = impulseCounter * impulsesToOdometerFactor * multiplier.toDouble()
                    dayDistanceMeters = (impulseCounter - dayPulseOffset) * impulsesToOdometerFactor * multiplier.toDouble()
                    tourDistanceMeters = (impulseCounter - tourPulseOffset) * impulsesToOdometerFactor * multiplier.toDouble()
                    
                    if (DEBUG_SENTENCE_PARSING) {
                        Log.d(TAG, "$header: freq=$frequency impulse=$impulseCounter " +
                            "-> speed=${String.format("%.1f", speedMetersPerSecond * 3.6f)}km/h trip=${String.format("%.2f", tripDistanceMeters / 1000.0)}km")
                    }
                    true
                }
                "FLB" -> {
                    temperatureCelsius = (tokens.getOrNull(1)?.toFloatOrNull() ?: 0f) / 10f
                    altitudeMeters = (tokens.getOrNull(3)?.toFloatOrNull() ?: 0f) / 10f
                    
                    if (DEBUG_SENTENCE_PARSING) {
                        Log.d(TAG, "FLB: temp=${String.format("%.1f", temperatureCelsius)}°C alt=${String.format("%.1f", altitudeMeters)}m")
                    }
                    true
                }
                "FLC" -> {
                    when (tokens.getOrNull(1)) {
                        "5" -> {
                            batteryLevelPercentage = tokens.getOrNull(3)?.toIntOrNull() ?: batteryLevelPercentage
                            if (DEBUG_SENTENCE_PARSING) {
                                Log.d(TAG, "FLC: battery level set to $batteryLevelPercentage%")
                            }
                        }
                        "3" -> {
                            tourEnergyWattHours = tokens.getOrNull(3)?.toDoubleOrNull() ?: tourEnergyWattHours
                            tripEnergyWattHours = tokens.getOrNull(4)?.toDoubleOrNull() ?: tripEnergyWattHours
                            if (DEBUG_SENTENCE_PARSING) {
                                Log.d(TAG, "FLC: tripEnergy=$tripEnergyWattHours Wh, tourEnergy=$tourEnergyWattHours Wh")
                            }
                        }
                    }
                    true
                }
                "FLP" -> {
                    val newWheelsize = tokens.getOrNull(1)?.toIntOrNull() ?: wheelsize
                    val newPoles = tokens.getOrNull(2)?.toIntOrNull() ?: poles
                    dayPulseOffset = tokens.getOrNull(4)?.toDoubleOrNull() ?: dayPulseOffset
                    tourPulseOffset = tokens.getOrNull(6)?.toDoubleOrNull() ?: tourPulseOffset
                    updateConfig(newWheelsize, newPoles)
                    _isConfigLoadedFlow.value = true
                    true
                }
                "FLD" -> {
                    val frequency = tokens.getOrNull(4)?.toFloatOrNull() ?: 0f
                    tokens.getOrNull(5)?.toFloatOrNull()?.let { batteryVoltage = it }
                    tokens.getOrNull(6)?.toFloatOrNull()?.let { batteryCurrent = it }
                    tokens.getOrNull(7)?.toFloatOrNull()?.let { consumerCurrent = it }
                    
                    tokens.getOrNull(8)?.trim()?.takeIf { it.isNotEmpty() }?.let { statusChar ->
                        when (statusChar) {
                            "+" -> chargeState = ChargeState.CHARGING
                            "-" -> chargeState = ChargeState.DISCHARGING
                            "V", "*" -> chargeState = ChargeState.FULL
                            // If it's something else, we can leave it alone or handle it if known
                        }
                    }

                    val p9 = tokens.getOrNull(9)?.toIntOrNull() ?: 0
                    batteryLevelPercentage = when (p9) {
                        0 -> 5
                        1 -> 10
                        2 -> 20
                        3 -> 35
                        4 -> 50
                        5 -> 65
                        6 -> 80
                        7 -> 95
                        else -> batteryLevelPercentage
                    }

                    val frequencyToSpeedFactor = wheelsize.toFloat() / poles.toFloat() / 1000f * version.frequencyScale
                    currentFrequency = frequency
                    val multiplier = config?.speedMultiplier ?: 1.0f
                    speedMetersPerSecond = frequency * frequencyToSpeedFactor * multiplier

                    val kilometersCounter = tokens.getOrNull(14)?.toDoubleOrNull() ?: 0.0
                    tripDistanceMeters = kilometersCounter * 1000.0 * multiplier.toDouble()
                    totalDistanceMeters = tripDistanceMeters
                    odometerMeters = totalDistanceMeters
                    dayDistanceMeters = 0.0
                    tourDistanceMeters = 0.0
                    
                    if (DEBUG_SENTENCE_PARSING) {
                        Log.d(TAG, "FLD: freq=$frequency -> speed=${String.format("%.1f", speedMetersPerSecond * 3.6f)}km/h " +
                            "distance=${String.format("%.2f", kilometersCounter)}km battery=$batteryLevelPercentage%")
                    }
                    true
                }
                else -> {
                    Log.w(TAG, "Unknown sentence type: $header")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse error in payload: ${payload.take(100)}", e)
            false
        }
    }

    fun resetConfigLoaded() {
        _isConfigLoadedFlow.value = false
    }
}
