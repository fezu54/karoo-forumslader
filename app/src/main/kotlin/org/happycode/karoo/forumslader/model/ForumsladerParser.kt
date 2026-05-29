package org.happycode.karoo.forumslader.model

class ForumsladerParser {

    private val frameBuffer = StringBuilder()

    /**
     * Collects incoming byte arrays from the BLE onCharacteristicChanged callback
     * and triggers parsing as soon as a termination character is detected.
     */
    fun processIncomingBytes(data: ByteArray): ForumsladerData? {
        val chunk = String(data, Charsets.US_ASCII)
        frameBuffer.append(chunk)

        // Forumslader ASCII frames are typically terminated by \n or \r\n        
        val delimiterIndex = frameBuffer.indexOf("\n")
        return when {
            delimiterIndex != -1 -> {
                val completeFrame = frameBuffer.substring(0, delimiterIndex).trim()
                frameBuffer.delete(0, delimiterIndex + 1)
        
                parseAsciiPayload(completeFrame)
            }
            else -> null
        }
        // frame still incomplete
    }

    private fun parseAsciiPayload(payload: String): ForumsladerData? {
        return try {
            val tokens = payload.split(",")

            ForumsladerData(
                batteryVoltage = tokens.getOrNull(0)?.toFloatOrNull() ?: 0f,
                batteryCurrent = tokens.getOrNull(1)?.toFloatOrNull() ?: 0f,
                consumerCurrent = tokens.getOrNull(2)?.toFloatOrNull() ?: 0f,
                batteryLevelPct = tokens.getOrNull(3)?.toIntOrNull() ?: 0,
                speedKmh = tokens.getOrNull(4)?.toFloatOrNull() ?: 0f,
                tripDistanceKm = tokens.getOrNull(5)?.toFloatOrNull() ?: 0f,
                totalDistanceKm = tokens.getOrNull(6)?.toFloatOrNull() ?: 0f,
                temperatureCelsius = tokens.getOrNull(7)?.toFloatOrNull() ?: 0f,
                altitudeMeters = tokens.getOrNull(8)?.toFloatOrNull() ?: 0f
            )
        } catch (_: Exception) {
            null // Parsing failed (e.g. boot sequence or inconsistent frame)
        }
    }
}