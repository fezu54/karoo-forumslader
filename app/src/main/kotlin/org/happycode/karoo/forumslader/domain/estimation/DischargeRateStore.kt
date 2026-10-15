package org.happycode.karoo.forumslader.domain.estimation

fun interface DischargeRateStore {
    fun save(ratePctPerKm: Float)
}
