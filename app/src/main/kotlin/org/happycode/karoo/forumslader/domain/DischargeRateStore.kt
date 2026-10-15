package org.happycode.karoo.forumslader.domain

fun interface DischargeRateStore {
    fun save(ratePctPerKm: Float)
}
