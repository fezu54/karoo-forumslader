package org.happycode.karoo.forumslader.framework.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.happycode.karoo.forumslader.domain.estimation.GradientProfile

@Entity(tableName = "rider_gradient_profile")
data class RiderProfileEntity(
    @PrimaryKey val gradientPct: Int,
    val speedMetersPerSecond: Float,
    val frequencyHz: Float,
    val dynamoPowerWatts: Float
) {
    fun toDomain(): GradientProfile =
        GradientProfile(gradientPct, speedMetersPerSecond, frequencyHz, dynamoPowerWatts)

    companion object {
        fun fromDomain(profile: GradientProfile): RiderProfileEntity = RiderProfileEntity(
            gradientPct = profile.gradientPct,
            speedMetersPerSecond = profile.speedMetersPerSecond,
            frequencyHz = profile.frequencyHz,
            dynamoPowerWatts = profile.dynamoPowerWatts
        )
    }
}
