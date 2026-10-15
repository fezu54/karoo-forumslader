package org.happycode.karoo.forumslader.extension.estimation

import org.happycode.karoo.forumslader.domain.estimation.GradientProfile
import org.happycode.karoo.forumslader.domain.estimation.RiderProfileRepository
import org.happycode.karoo.forumslader.framework.persistence.RiderProfileDao
import org.happycode.karoo.forumslader.framework.persistence.RiderProfileEntity

class RoomRiderProfileRepository(
    private val dao: RiderProfileDao
) : RiderProfileRepository {

    override suspend fun findByGradient(gradientPct: Int): GradientProfile? =
        dao.findByGradient(gradientPct)?.toDomain()

    override suspend fun findAll(): Map<Int, GradientProfile> =
        dao.findAll().associate { entity -> entity.gradientPct to entity.toDomain() }

    override suspend fun save(profile: GradientProfile) {
        dao.upsert(RiderProfileEntity.fromDomain(profile))
    }
}
