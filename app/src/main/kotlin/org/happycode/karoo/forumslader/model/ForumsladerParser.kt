package org.happycode.karoo.forumslader.model

import android.util.Log
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
    private var batteryLevelPct: Int = 0
    private var currentFrequency: Float = 0f
    private var speedMs: Float = 0f
    private var tripDistanceMeters: Double = 0.0
    private var totalDistanceMeters: Double = 0.0
    private var temperatureCelsius: Float = 0f
    private var altitudeMeters: Float = 0f

    // Configuration parameters
    private var wheelsize: Int = config?.wheelsize ?: 2200 // default fallback in mm
    private var poles: Int = config?.poles ?: 14       // default fallback (pole pairs)
    var version: ForumsladerVersion = config?.version ?: ForumsladerVersion.Unknown
        internal set
    var isConfigLoaded: Boolean = false
        private set

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
        // (configuration-only frames like FLP, FLC, FLB don't trigger emissions)
        return if (parsedTelemetry && hasReceivedTelemetry) {
            val metrics = ForumsladerMetrics(
                batteryVoltage,
                batteryCurrent,
                consumerCurrent,
                batteryLevelPct,
                currentFrequency,
                speedMs,
                tripDistanceMeters,
                totalDistanceMeters,
                temperatureCelsius,
                altitudeMeters
            )
            totalMetricsEmitted++
            
            Log.i(TAG, "Metrics emitted (#$totalMetricsEmitted) from $parsedSentenceType | " +
                "V=${String.format("%.2f", batteryVoltage)}V I=${String.format("%.2f", batteryCurrent)}A " +
                "Speed=${String.format("%.1f", speedMs * 3.6f)}km/h Trip=${String.format("%.2f", tripDistanceMeters / 1000.0)}km " +
                "Batt=$batteryLevelPct% Temp=${String.format("%.1f", temperatureCelsius)}°C " +
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
            
            val starIndex = payload.indexOf('*')
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
            val starIndex = payload.indexOf('*')
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
                    
                    val frequency = tokens.getOrNull(3)?.toFloatOrNull() ?: 0f
                    val cell1 = tokens.getOrNull(4)?.toFloatOrNull() ?: 0f
                    val cell2 = tokens.getOrNull(5)?.toFloatOrNull() ?: 0f
                    val cell3 = tokens.getOrNull(6)?.toFloatOrNull() ?: 0f
                    batteryVoltage = (cell1 + cell2 + cell3) / 1000f
                    batteryCurrent = (tokens.getOrNull(7)?.toFloatOrNull() ?: 0f) / 1000f
                    consumerCurrent = (tokens.getOrNull(8)?.toFloatOrNull() ?: 0f) / 1000f

                    val impulseCounter = tokens.getOrNull(version.impulseIndex)?.toDoubleOrNull() ?: 0.0

                    val freq2speed = wheelsize.toFloat() / poles.toFloat() / 1000f * version.frequencyScale
                    currentFrequency = frequency
                    val multiplier = config?.speedMultiplier ?: 1.0f
                    speedMs = frequency * freq2speed * multiplier

                    val imp2odo = wheelsize.toDouble() / poles.toDouble() / 1000.0 * version.impulseScale
                    tripDistanceMeters = impulseCounter * imp2odo
                    totalDistanceMeters = tripDistanceMeters
                    
                    if (DEBUG_SENTENCE_PARSING) {
                        Log.d(TAG, "$header: freq=$frequency impulse=$impulseCounter " +
                            "-> speed=${String.format("%.1f", speedMs * 3.6f)}km/h trip=${String.format("%.2f", tripDistanceMeters / 1000.0)}km")
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
                    val setnr = tokens.getOrNull(1)
                    if (setnr == "5") {
                        batteryLevelPct = tokens.getOrNull(3)?.toIntOrNull() ?: batteryLevelPct
                        if (DEBUG_SENTENCE_PARSING) {
                            Log.d(TAG, "FLC: battery level set to $batteryLevelPct%")
                        }
                    }
                    true
                }
                "FLP" -> {
                    val newWheelsize = tokens.getOrNull(1)?.toIntOrNull() ?: wheelsize
                    val newPoles = tokens.getOrNull(2)?.toIntOrNull() ?: poles
                    updateConfig(newWheelsize, newPoles)
                    isConfigLoaded = true
                    true
                }
                "FLD" -> {
                    val frequency = tokens.getOrNull(4)?.toFloatOrNull() ?: 0f
                    batteryVoltage = tokens.getOrNull(5)?.toFloatOrNull() ?: 0f
                    batteryCurrent = tokens.getOrNull(6)?.toFloatOrNull() ?: 0f
                    consumerCurrent = tokens.getOrNull(7)?.toFloatOrNull() ?: 0f

                    val p9 = tokens.getOrNull(9)?.toIntOrNull() ?: 0
                    batteryLevelPct = when (p9) {
                        0 -> 5
                        1 -> 10
                        2 -> 20
                        3 -> 35
                        4 -> 50
                        5 -> 65
                        6 -> 80
                        7 -> 95
                        else -> batteryLevelPct
                    }

                    val freq2speed = wheelsize.toFloat() / poles.toFloat() / 1000f * version.frequencyScale
                    currentFrequency = frequency
                    val multiplier = config?.speedMultiplier ?: 1.0f
                    speedMs = frequency * freq2speed * multiplier

                    val kmCounter = tokens.getOrNull(14)?.toDoubleOrNull() ?: 0.0
                    tripDistanceMeters = kmCounter * 1000.0
                    totalDistanceMeters = tripDistanceMeters
                    
                    if (DEBUG_SENTENCE_PARSING) {
                        Log.d(TAG, "FLD: freq=$frequency -> speed=${String.format("%.1f", speedMs * 3.6f)}km/h " +
                            "distance=${String.format("%.2f", kmCounter)}km battery=$batteryLevelPct%")
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
        isConfigLoaded = false
    }
}
