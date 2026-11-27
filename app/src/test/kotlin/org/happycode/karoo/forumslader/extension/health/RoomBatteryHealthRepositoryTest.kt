package org.happycode.karoo.forumslader.extension.health

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSnapshot
import org.happycode.karoo.forumslader.framework.persistence.BatteryHealthDao
import org.happycode.karoo.forumslader.framework.persistence.BatteryHealthEntity

class RoomBatteryHealthRepositoryTest : ShouldSpec({

    val dao = mockk<BatteryHealthDao>(relaxed = true)
    val repository = RoomBatteryHealthRepository(dao)

    should("save, retrieve, and prune domain battery health snapshots via dao") {
        // given
        val epochDay = 20_000L
        val snapshot = BatteryHealthSnapshot(
            timestampEpochMs = epochDay * 86_400_000L + 1_000L,
            cycleCount = 127,
            fullChargeCapacityMah = 1140,
            socPct = 85
        )
        val entity = BatteryHealthEntity.fromDomain(epochDay, snapshot)
        coEvery { dao.findAll() } returns listOf(entity)

        // when
        repository.upsertForDay(epochDay, snapshot)
        val all = repository.findAll()
        repository.deleteOlderThan(5_000L)

        // then
        val slot = slot<BatteryHealthEntity>()
        coVerify { dao.upsert(capture(slot)) }
        slot.captured.epochDay shouldBe epochDay
        slot.captured.cycleCount shouldBe 127
        slot.captured.fullChargeCapacityMah shouldBe 1140
        slot.captured.socPct shouldBe 85

        all shouldBe listOf(snapshot)
        coVerify { dao.deleteOlderThan(5_000L) }
    }
})
