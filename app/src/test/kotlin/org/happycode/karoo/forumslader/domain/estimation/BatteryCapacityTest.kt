package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class BatteryCapacityTest : ShouldSpec({

    should("convert credible fullChargeCapacityMah values including boundaries to Wh at 11.1V nominal") {
        // given / when / then
        BatteryCapacity.isCredibleMah(450) shouldBe true
        BatteryCapacity.isCredibleMah(1200) shouldBe true
        BatteryCapacity.isCredibleMah(6000) shouldBe true

        BatteryCapacity.toCredibleWh(450).shouldNotBeNull() shouldBe (4.995f plusOrMinus 0.001f)
        BatteryCapacity.toCredibleWh(1200).shouldNotBeNull() shouldBe (13.32f plusOrMinus 0.001f)
        BatteryCapacity.toCredibleWh(6000).shouldNotBeNull() shouldBe (66.6f plusOrMinus 0.001f)
        BatteryCapacity.toWh(900) shouldBe (9.99f plusOrMinus 0.001f)
    }

    should("reject null and out-of-range fullChargeCapacityMah values") {
        // given / when / then
        listOf(null, 0, -100, 449, 6001).forEach { invalidMah ->
            BatteryCapacity.isCredibleMah(invalidMah) shouldBe false
            BatteryCapacity.toCredibleWh(invalidMah).shouldBeNull()
        }
    }
})
