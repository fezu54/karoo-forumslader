package org.happycode.karoo.forumslader.domain.estimation

interface RiderProfileRepository {
    suspend fun findByGradient(gradientPct: Int): GradientProfile?
    suspend fun findAll(): Map<Int, GradientProfile>
    suspend fun save(profile: GradientProfile)
}
