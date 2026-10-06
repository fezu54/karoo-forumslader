package org.happycode.karoo.forumslader.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe

class DynamoPolePresetTest : ShouldSpec({

    should("include presets for popular hub dynamos") {
        // given
        val presets = DynamoPolePreset.ALL

        // then
        presets.any { (it.name.contains("SON 28")) && it.poles == 13 } shouldBe true
        presets.any { (it.name.contains("SON 29s")) && it.poles == 20 } shouldBe true
        presets.any { (it.name.contains("Shutter Precision")) && it.poles == 14 } shouldBe true
        presets.any { (it.name.contains("Shimano")) && it.poles == 14 } shouldBe true
        presets.any { (it.name.contains("Velological")) && it.poles == 12 } shouldBe true
    }

    should("format short label with name and pole pairs") {
        // given
        val preset = DynamoPolePreset("SON 28", 13)

        // then
        preset.label shouldBe "SON 28 (13 pairs)"
    }
},)
