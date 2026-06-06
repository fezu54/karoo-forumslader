package org.happycode.karoo.forumslader.model

import android.util.Log
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ForumsladerParser {

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
    private var speedKmh: Float = 0f
    private var tripDistanceKm: Float = 0f
    private var totalDistanceKm: Float = 0f
    private var temperatureCelsius: Float = 0f
    private var altitudeMeters: Float = 0f

    // Configuration parameters
    private var wheelsize: Int = 2200 // default fallback in mm
    private var poles: Int = 14       // default fallback (pole pairs)
    private var isV6: Boolean = true   // default fallback

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
                batteryVoltage = batteryVoltage,
                batteryCurrent = batteryCurrent,
                consumerCurrent = consumerCurrent,
                batteryLevelPct = batteryLevelPct,
                speedKmh = speedKmh,
                tripDistanceKm = tripDistanceKm,
                totalDistanceKm = totalDistanceKm,
                temperatureCelsius = temperatureCelsius,
                altitudeMeters = altitudeMeters
            )
            totalMetricsEmitted++
            
            Log.i(TAG, "Metrics emitted (#$totalMetricsEmitted) from $parsedSentenceType | " +
                "V=${String.format("%.2f", batteryVoltage)}V I=${String.format("%.2f", batteryCurrent)}A " +
                "Speed=${String.format("%.1f", speedKmh)}km/h Trip=${String.format("%.2f", tripDistanceKm)}km " +
                "Batt=$batteryLevelPct% Temp=${String.format("%.1f", temperatureCelsius)}°C " +
                "[Config: WS=$wheelsize poles=$poles V6=$isV6]")
            
            metrics
        } else {
            null
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
            "ERROR"
        }
    }

    private fun isTelemetrySentence(payload: String): Boolean {
        // Extract header to determine if this is a primary telemetry sentence
        if (!payload.startsWith("$")) return false
        
        val starIndex = payload.indexOf('*')
        val semiIndex = payload.indexOf(';')
        
        val dataString: String = when {
            starIndex != -1 -> payload.substring(1, starIndex)
            semiIndex != -1 -> payload.substring(1, semiIndex)
            else -> payload.substring(1)
        }
        
        val header = dataString.split(",").getOrNull(0) ?: return false
        
        // Only FL5, FL6, and FLD contain actual telemetry (speed, power, distance)
        // FLP, FLC, FLB are configuration or environmental and should not trigger emissions
        return header in listOf("FL5", "FL6", "FLD")
    }

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
                    val prevIsV6 = isV6
                    isV6 = (header == "FL6")
                    if (prevIsV6 != isV6) {
                        Log.i(TAG, "Device version changed: V5/V6 mode is now $isV6")
                    }
                    
                    val frequency = tokens.getOrNull(3)?.toFloatOrNull() ?: 0f
                    val cell1 = tokens.getOrNull(4)?.toFloatOrNull() ?: 0f
                    val cell2 = tokens.getOrNull(5)?.toFloatOrNull() ?: 0f
                    val cell3 = tokens.getOrNull(6)?.toFloatOrNull() ?: 0f
                    batteryVoltage = (cell1 + cell2 + cell3) / 1000f
                    batteryCurrent = (tokens.getOrNull(7)?.toFloatOrNull() ?: 0f) / 1000f
                    consumerCurrent = (tokens.getOrNull(8)?.toFloatOrNull() ?: 0f) / 1000f

                    val impulseIdx = if (isV6) 12 else 13
                    val impulseCounter = tokens.getOrNull(impulseIdx)?.toFloatOrNull() ?: 0f

                    val freq2speed = wheelsize.toFloat() / poles.toFloat() * 0.0036f / (if (isV6) 10f else 1f)
                    speedKmh = frequency * freq2speed

                    val imp2odo = wheelsize.toDouble() / poles.toDouble() / 1000000.0 * (if (isV6) 1.0 else 4096.0)
                    tripDistanceKm = (impulseCounter * imp2odo).toFloat()
                    totalDistanceKm = tripDistanceKm
                    
                    if (DEBUG_SENTENCE_PARSING) {
                        Log.d(TAG, "$header: freq=$frequency impulse=$impulseCounter " +
                            "-> speed=${String.format("%.1f", speedKmh)}km/h trip=${String.format("%.2f", tripDistanceKm)}km")
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
                    
                    if (newWheelsize != wheelsize || newPoles != poles) {
                        Log.i(TAG, "Configuration updated: wheelsize $wheelsize -> $newWheelsize mm, " +
                            "poles $poles -> $newPoles (pole pairs)")
                    }
                    
                    wheelsize = newWheelsize
                    poles = newPoles
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

                    val freq2speed = wheelsize.toFloat() / poles.toFloat() * 0.0036f / (if (isV6) 10f else 1f)
                    speedKmh = frequency * freq2speed

                    val kmCounter = tokens.getOrNull(14)?.toFloatOrNull() ?: 0f
                    tripDistanceKm = kmCounter
                    totalDistanceKm = kmCounter
                    
                    if (DEBUG_SENTENCE_PARSING) {
                        Log.d(TAG, "FLD: freq=$frequency -> speed=${String.format("%.1f", speedKmh)}km/h " +
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
}
