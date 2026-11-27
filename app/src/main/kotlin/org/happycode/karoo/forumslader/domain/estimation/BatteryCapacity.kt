package org.happycode.karoo.forumslader.domain.estimation

/**
 * Credibility bounds and nominal Watt-hour conversions for 3S Forumslader Li-Ion packs.
 */
object BatteryCapacity {
    const val MIN_CREDIBLE_MAH = 450
    const val MAX_CREDIBLE_MAH = 6000
    const val NOMINAL_PACK_VOLTAGE_V = 11.1f
    private const val MILLIAMPS_PER_AMP = 1000f

    fun isCredibleMah(capacityMah: Int?): Boolean =
        capacityMah != null && capacityMah in MIN_CREDIBLE_MAH..MAX_CREDIBLE_MAH

    fun toWh(capacityMah: Int): Float =
        (capacityMah / MILLIAMPS_PER_AMP) * NOMINAL_PACK_VOLTAGE_V

    fun toCredibleWh(capacityMah: Int?): Float? =
        capacityMah?.takeIf(::isCredibleMah)?.let(::toWh)
}
