package org.happycode.karoo.forumslader.framework.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface RiderProfileDao {
    @Query("SELECT * FROM rider_gradient_profile WHERE gradientPct = :gradientPct")
    suspend fun findByGradient(gradientPct: Int): RiderProfileEntity?

    @Query("SELECT * FROM rider_gradient_profile")
    suspend fun findAll(): List<RiderProfileEntity>

    @Upsert
    suspend fun upsert(entity: RiderProfileEntity)
}
