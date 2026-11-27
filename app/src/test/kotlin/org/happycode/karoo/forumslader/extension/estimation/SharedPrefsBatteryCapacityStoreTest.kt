package org.happycode.karoo.forumslader.extension.estimation

import android.content.SharedPreferences
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class SharedPrefsBatteryCapacityStoreTest : ShouldSpec({

    val preferences = mockk<SharedPreferences>(relaxed = true)
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)

    beforeEach {
        every { preferences.edit() } returns editor
        every { editor.putInt(any(), any()) } returns editor
    }

    should("return null when no saved battery capacity exists") {
        // given
        every {
            preferences.getInt(SharedPrefsBatteryCapacityStore.KEY_LAST_FULL_CHARGE_CAPACITY_MAH, -1)
        } returns -1
        val store = SharedPrefsBatteryCapacityStore(preferences)

        // when
        val loaded = store.loadCapacityMah()

        // then
        loaded.shouldBeNull()
    }

    should("save and load credible fullChargeCapacityMah from preferences") {
        // given
        val store = SharedPrefsBatteryCapacityStore(preferences)

        // when
        store.saveCapacityMah(1140)

        // then
        verify { editor.putInt(SharedPrefsBatteryCapacityStore.KEY_LAST_FULL_CHARGE_CAPACITY_MAH, 1140) }

        // given
        every {
            preferences.getInt(SharedPrefsBatteryCapacityStore.KEY_LAST_FULL_CHARGE_CAPACITY_MAH, -1)
        } returns 1140

        // when / then
        store.loadCapacityMah() shouldBe 1140
    }

    should("return null when stored capacity is outside credible bounds") {
        // given
        val store = SharedPrefsBatteryCapacityStore(preferences)

        listOf(0, 449, 6001).forEach { invalid ->
            every {
                preferences.getInt(SharedPrefsBatteryCapacityStore.KEY_LAST_FULL_CHARGE_CAPACITY_MAH, -1)
            } returns invalid

            // when / then
            store.loadCapacityMah().shouldBeNull()
        }
    }
})
