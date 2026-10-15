package org.happycode.karoo.forumslader.domain.estimation

class DynamoPowerCurve {
    fun predictPowerWatts(profile: GradientProfile, baseProfile: GradientProfile? = null): Float =
        baseProfile?.takeIf { it.frequencyHz > 0f && it.dynamoPowerWatts > 0f }
            ?.let { learned ->
                val ratio = profile.frequencyHz / learned.frequencyHz
                (learned.dynamoPowerWatts * ratio).coerceIn(0f, MAX_POWER_WATTS)
            }
            ?: predictFromFrequency(profile.frequencyHz)

    fun predictFromFrequency(frequencyHz: Float): Float = when {
        frequencyHz < CUT_IN_HZ -> 0.0f
        frequencyHz < STAGE_TWO_HZ -> interpolate(frequencyHz, CUT_IN_HZ, STAGE_TWO_HZ, 0.5f, 3.2f)
        frequencyHz < STAGE_THREE_HZ -> interpolate(frequencyHz, STAGE_TWO_HZ, STAGE_THREE_HZ, 3.2f, 7.0f)
        frequencyHz < SATURATION_HZ -> interpolate(frequencyHz, STAGE_THREE_HZ, SATURATION_HZ, 7.0f, 10.2f)
        else -> MAX_POWER_WATTS
    }

    private fun interpolate(
        value: Float,
        minInput: Float,
        maxInput: Float,
        minOutput: Float,
        maxOutput: Float
    ): Float = minOutput + ((value - minInput) / (maxInput - minInput)) * (maxOutput - minOutput)

    companion object {
        private const val CUT_IN_HZ = 12.0f
        private const val STAGE_TWO_HZ = 25.0f
        private const val STAGE_THREE_HZ = 40.0f
        private const val SATURATION_HZ = 60.0f
        private const val MAX_POWER_WATTS = 11.0f
    }
}
