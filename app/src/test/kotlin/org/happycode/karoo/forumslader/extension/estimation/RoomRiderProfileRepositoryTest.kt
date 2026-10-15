package org.happycode.karoo.forumslader.extension.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.happycode.karoo.forumslader.domain.estimation.GradientProfile
import org.happycode.karoo.forumslader.framework.persistence.RiderProfileDao
import org.happycode.karoo.forumslader.framework.persistence.RiderProfileEntity

class RoomRiderProfileRepositoryTest : ShouldSpec({

    val dao = mockk<RiderProfileDao>(relaxed = true)
    val repository = RoomRiderProfileRepository(dao)

    should("save and retrieve domain gradient profiles via dao") {
        // given
        val domainProfile = GradientProfile(
            gradientPct = 6,
            speedMetersPerSecond = 3.2f,
            frequencyHz = 23.0f,
            dynamoPowerWatts = 2.5f
        )
        val entity = RiderProfileEntity.fromDomain(domainProfile)
        coEvery { dao.findByGradient(6) } returns entity
        coEvery { dao.findAll() } returns listOf(entity)

        // when
        repository.save(domainProfile)
        val fetched = repository.findByGradient(6)
        val allProfiles = repository.findAll()

        // then
        val slot = slot<RiderProfileEntity>()
        coVerify { dao.upsert(capture(slot)) }
        slot.captured.gradientPct shouldBe 6
        slot.captured.speedMetersPerSecond shouldBe 3.2f
        slot.captured.frequencyHz shouldBe 23.0f
        slot.captured.dynamoPowerWatts shouldBe 2.5f

        fetched.shouldNotBeNull()
        fetched shouldBe domainProfile
        allProfiles[6] shouldBe domainProfile
    }
})
