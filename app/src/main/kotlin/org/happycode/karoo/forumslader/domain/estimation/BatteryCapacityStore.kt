package org.happycode.karoo.forumslader.domain.estimation

fun interface BatteryCapacityStore {
    fun saveCapacityMah(capacityMah: Int)
}
