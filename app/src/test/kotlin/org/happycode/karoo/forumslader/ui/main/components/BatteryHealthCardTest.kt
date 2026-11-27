package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSnapshot
import org.happycode.karoo.forumslader.domain.health.BatteryHealthStatus
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSummary
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BatteryHealthCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() {
        Locale.setDefault(Locale.US)
    }

    private fun summary(
        capacityMah: Int = 1140,
        capacityWh: Float = 12.654f,
        baselineMah: Int = 1200,
        sohPct: Float = 95f,
        cycleCount: Int = 127,
        status: BatteryHealthStatus = BatteryHealthStatus.GOOD,
        slope: Float? = -1.5f,
        cyclesRemaining: Int? = 240,
        historySize: Int = 3
    ) = BatteryHealthSummary(
        currentCapacityMah = capacityMah,
        currentCapacityWh = capacityWh,
        baselineCapacityMah = baselineMah,
        stateOfHealthPct = sohPct,
        cycleCount = cycleCount,
        status = status,
        slopeMahPerCycle = slope,
        estimatedCyclesRemaining = cyclesRemaining,
        history = (1..historySize).map { idx ->
            BatteryHealthSnapshot(
                timestampEpochMs = idx * 86_400_000L,
                cycleCount = 100 + idx * 10,
                fullChargeCapacityMah = baselineMah - idx * 20,
                socPct = 85
            )
        }
    )

    @Test
    fun `should display empty state when summary is null`() {
        // when
        composeTestRule.setContent {
            AppTheme {
                BatteryHealthCard(summary = null)
            }
        }

        // then
        composeTestRule.onNodeWithText("Battery Health").assertIsDisplayed()
        composeTestRule.onNodeWithText("No battery health data yet").assertIsDisplayed()
    }

    @Test
    fun `should display capacity, cycle count, status badge, and remaining cycles when good summary provided`() {
        // when
        composeTestRule.setContent {
            AppTheme {
                BatteryHealthCard(summary = summary())
            }
        }

        // then
        composeTestRule.onNodeWithText("Good").assertIsDisplayed()
        composeTestRule.onNodeWithText("1140 mAh / 12.7 Wh (95%)").assertIsDisplayed()
        composeTestRule.onNodeWithText("127 cycles").assertIsDisplayed()
        composeTestRule.onNodeWithText("~240 cycles remaining").assertIsDisplayed()
    }

    @Test
    fun `should display degrading badge when status is DEGRADING`() {
        // when
        composeTestRule.setContent {
            AppTheme {
                BatteryHealthCard(
                    summary = summary(
                        capacityMah = 840,
                        capacityWh = 9.324f,
                        sohPct = 70f,
                        status = BatteryHealthStatus.DEGRADING,
                        cyclesRemaining = 60
                    )
                )
            }
        }

        // then
        composeTestRule.onNodeWithText("Degrading").assertIsDisplayed()
    }

    @Test
    fun `should display replace soon badge and replacement recommendation when status is REPLACE_SOON`() {
        // when
        composeTestRule.setContent {
            AppTheme {
                BatteryHealthCard(
                    summary = summary(
                        capacityMah = 660,
                        capacityWh = 7.326f,
                        sohPct = 55f,
                        status = BatteryHealthStatus.REPLACE_SOON,
                        cyclesRemaining = 0
                    )
                )
            }
        }

        // then
        composeTestRule.onNodeWithText("Replace soon").assertIsDisplayed()
        composeTestRule.onNodeWithText("Replacement recommended").assertIsDisplayed()
    }

    @Test
    fun `should prompt for more rides when only a single snapshot exists`() {
        // when
        composeTestRule.setContent {
            AppTheme {
                BatteryHealthCard(
                    summary = summary(
                        capacityMah = 1200,
                        capacityWh = 13.32f,
                        sohPct = 100f,
                        cycleCount = 1,
                        slope = null,
                        cyclesRemaining = null,
                        historySize = 1
                    )
                )
            }
        }

        // then
        composeTestRule.onNodeWithText("More rides needed for trend").assertIsDisplayed()
    }

    @Test
    fun `should compute chart points and threshold line for multi-point history`() {
        // given
        val history = listOf(
            BatteryHealthSnapshot(1_000L, 10, 1200, 90),
            BatteryHealthSnapshot(2_000L, 50, 1050, 85),
            BatteryHealthSnapshot(3_000L, 100, 900, 80)
        )

        // when
        val layout = computeBatteryHealthChartLayout(
            history = history,
            baselineCapacityMah = 1200,
            widthPx = 200f,
            heightPx = 100f
        ).shouldNotBeNull()

        // then: points span left-to-right and descend as capacity drops; threshold sits below latest 75% point
        layout.points.size shouldBe 3
        layout.points[0].x shouldBe 0f
        layout.points[1].x shouldBe 100f
        layout.points[2].x shouldBe 200f
        layout.points[2].y shouldBeGreaterThan layout.points[0].y
        layout.thresholdY shouldBeGreaterThan layout.points[2].y
    }

    @Test
    fun `should return null chart layout when history has fewer than two points or invalid bounds`() {
        // given
        val single = listOf(BatteryHealthSnapshot(1_000L, 10, 1200, 90))
        val pair = single + BatteryHealthSnapshot(2_000L, 20, 1180, 85)

        // when / then
        computeBatteryHealthChartLayout(single, 1200, 200f, 100f).shouldBeNull()
        computeBatteryHealthChartLayout(pair, 0, 200f, 100f).shouldBeNull()
        computeBatteryHealthChartLayout(pair, 1200, 0f, 100f).shouldBeNull()
        computeBatteryHealthChartLayout(pair, 1200, 200f, 0f).shouldBeNull()
    }
}
