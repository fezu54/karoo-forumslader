package org.happycode.karoo.forumslader.domain.estimation

/**
 * Computes the incremental discharge rate penalty (%/km) from excess headwind
 * across the non-climb fraction of the remaining route.
 */
class HeadwindCompensator(
    private val headwindPenaltyPctPerKmPerMs: Float
) {
    /**
     * Calculates the additional %/km drain from headwind above [baselineHeadwindMs] already
     * captured in the observed discharge window, weighted by [nonClimbRouteFraction].
     */
    fun calculateRatePenaltyPctPerKm(
        currentHeadwindMs: Float?,
        baselineHeadwindMs: Float?,
        nonClimbRouteFraction: Float
    ): Float {
        val activeHeadwind = currentHeadwindMs?.coerceAtLeast(0f) ?: return 0f
        val baseline = baselineHeadwindMs?.coerceAtLeast(0f) ?: 0f
        val excessHeadwindMs = (activeHeadwind - baseline).coerceAtLeast(0f)
        return excessHeadwindMs * headwindPenaltyPctPerKmPerMs * nonClimbRouteFraction.coerceIn(0f, 1f)
    }
}
