package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.domain.WheelSizePreset
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConfigCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should include wheel size presets from domain model`() {
        // given / when
        val presets = WheelSizePreset.ALL

        // then
        presets.any { it.designation.contains("700x48c") } shouldBe true
        presets.any { it.designation.contains("700x50c") } shouldBe true
    }

    @Test
    fun `should update wheelsize when typing a valid value and hitting done`() {
        var updatedWs: Int? = null
        renderCard(
            onConfigUpdate = { ws, _ -> updatedWs = ws },
        )

        composeTestRule.onNodeWithText("Override Wheel Size (mm)").performClick()
        // Replace instead of typing to avoid text insertion confusion
        composeTestRule.onAllNodesWithText("2100").onFirst().performTextReplacement("2250")
        composeTestRule.onNodeWithText("2250").performImeAction()

        updatedWs shouldBe 2250
    }

    @Test
    fun `should update poles when typing a valid value and hitting done`() {
        var updatedPoles: Int? = null
        renderCard(
            onConfigUpdate = { _, p -> updatedPoles = p },
        )

        composeTestRule.onNodeWithText("Override Poles").performClick()
        composeTestRule.onAllNodesWithText("28").onFirst().performTextReplacement("14")
        composeTestRule.onAllNodesWithText("14").onFirst().performImeAction()

        updatedPoles shouldBe 14
    }

    @Test
    fun `should update wheelsize when selecting a preset from dropdown`() {
        var updatedWs: Int? = null
        renderCard(
            onConfigUpdate = { ws, _ -> updatedWs = ws },
        )

        composeTestRule.onNodeWithText("Override Wheel Size (mm)").performClick()
        composeTestRule.onNodeWithText("47-406", substring = true).performClick()

        // Verify the value was updated via the callback
        updatedWs shouldBe 1515

        // Let Compose process the state update
        composeTestRule.waitForIdle()

        // Verify the text field now displays the selected value
        composeTestRule.onAllNodesWithText("1515").onFirst().assertExists()
    }

    @Test
    fun `should update poles when selecting a preset from dropdown`() {
        var updatedPoles: Int? = null
        renderCard(
            onConfigUpdate = { _, p -> updatedPoles = p },
        )

        composeTestRule.onNodeWithText("Override Poles").performClick()
        composeTestRule.onAllNodesWithText("SON 28", substring = true).onFirst().performClick()

        updatedPoles shouldBe 13
    }

    private fun renderCard(
        wheelsize: Int = 2100,
        poles: Int = 28,
        versionKey: String = "v6",
        lockedMacAddress: String? = null,
        onConfigUpdate: (Int, Int) -> Unit = { _, _ -> },
        onForgetDevice: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                ConfigCard(
                    wheelsize = wheelsize,
                    poles = poles,
                    versionKey = versionKey,
                    lockedMacAddress = lockedMacAddress,
                    onConfigUpdate = onConfigUpdate,
                    onForgetDevice = onForgetDevice,
                )
            }
        }
    }
}
