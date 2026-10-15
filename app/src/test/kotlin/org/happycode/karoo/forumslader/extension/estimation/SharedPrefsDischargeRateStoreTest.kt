package org.happycode.karoo.forumslader.extension.estimation

import android.content.SharedPreferences
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.happycode.karoo.forumslader.domain.estimation.BatteryEstimator

class SharedPrefsDischargeRateStoreTest : ShouldSpec({

    val preferences = mockk<SharedPreferences>(relaxed = true)
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    beforeEach {
        every { preferences.edit() } returns editor
        every { editor.putFloat(any(), any()) } returns editor
    }

    should("return default discharge rate when preferences contain no saved rate") {
        // given
        every {
            preferences.getFloat(
                "last_discharge_rate_pct_per_km",
                BatteryEstimator.DEFAULT_DISCHARGE_RATE
            )
        } returns BatteryEstimator.DEFAULT_DISCHARGE_RATE
        val store = SharedPrefsDischargeRateStore(preferences)

        // when
        val rate = store.loadInitialRate()

        // then
        rate shouldBe BatteryEstimator.DEFAULT_DISCHARGE_RATE
    }

    should("save and load persisted discharge rate from preferences") {
        // given
        val store = SharedPrefsDischargeRateStore(preferences)
        val testRate = 2.5f

        // when
        store.save(testRate)

        // then
        verify { editor.putFloat("last_discharge_rate_pct_per_km", testRate) }

        // given
        every {
            preferences.getFloat(
                "last_discharge_rate_pct_per_km",
                BatteryEstimator.DEFAULT_DISCHARGE_RATE
            )
        } returns testRate

        // when
        val loadedRate = store.loadInitialRate()

        // then
        loadedRate shouldBe testRate
    }

    should("return default discharge rate when preferences contain rate below minimum credible threshold") {
        // given
        every {
            preferences.getFloat(
                "last_discharge_rate_pct_per_km",
                BatteryEstimator.DEFAULT_DISCHARGE_RATE
            )
        } returns 0.01f
        val store = SharedPrefsDischargeRateStore(preferences)

        // when
        val rate = store.loadInitialRate()

        // then
        rate shouldBe BatteryEstimator.DEFAULT_DISCHARGE_RATE
    }
})
