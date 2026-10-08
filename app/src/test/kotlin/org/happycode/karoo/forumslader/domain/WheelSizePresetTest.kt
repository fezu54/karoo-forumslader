package org.happycode.karoo.forumslader.domain

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test

class WheelSizePresetTest {

    @Test
    fun `should include 700x48c Oracle Ridge and 50-622 presets`() {
        // given / when
        val presets = WheelSizePreset.ALL

        // then
        val oracleRidge = presets.find { it.designation.contains("700x48c") }
        oracleRidge shouldNotBe null
        oracleRidge?.circumferenceMm shouldBe 2268
        oracleRidge?.label shouldBe "700x48c / 48-622 (2268mm)"
        oracleRidge?.category shouldBe WheelSizePreset.Category.SIZE_700C

        val size50x622 = presets.find { it.designation.contains("50-622") }
        size50x622 shouldNotBe null
        size50x622?.circumferenceMm shouldBe 2280
        size50x622?.label shouldBe "700x50c / 50-622 (2280mm)"
        size50x622?.category shouldBe WheelSizePreset.Category.SIZE_700C
    }

    @Test
    fun `should contain valid wheel circumferences for all presets`() {
        // given / when / then
        WheelSizePreset.ALL.forEach { preset ->
            (preset.circumferenceMm in 1000..2500) shouldBe true
        }
    }

    @Test
    fun `should find preset by circumference when matched`() {
        // given / when
        val found = WheelSizePreset.findByCircumference(2268)

        // then
        found?.designation shouldBe "700x48c / 48-622"
    }

    @Test
    fun `should return null when finding unknown circumference`() {
        // given / when
        val found = WheelSizePreset.findByCircumference(9999)

        // then
        found shouldBe null
    }
}
