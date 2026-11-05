package org.happycode.karoo.forumslader.ui.main.components

import android.net.Uri
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.happycode.karoo.forumslader.domain.DfuState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirmwareUpdateCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should show Download Latest button and Browse button when idle`() {
        // given / when
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Idle,
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }

        // then
        composeTestRule.onNodeWithText("Download Latest (Requires Internet)").assertExists()
        composeTestRule.onNodeWithText("Browse").assertExists()
    }

    @Test
    fun `should show Start Update when a file is selected`() {
        // given
        val uri = mockk<Uri>(relaxed = true)

        // when
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Idle,
                sharedUri = uri,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }

        // then
        composeTestRule.onNodeWithText("Start Update").assertExists()
    }

    @Test
    fun `should show progress when uploading`() {
        // given / when
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Uploading(50),
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }

        // then
        composeTestRule.onNodeWithText("Uploading: 50%").assertExists()
    }

    @Test
    fun `should not offer starting another update when update is running`() {
        // given
        val uri = mockk<Uri>(relaxed = true)

        // when
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Uploading(50),
                sharedUri = uri,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }

        // then
        composeTestRule.onNodeWithText("Start Update").assertDoesNotExist()
        composeTestRule.onNodeWithText("Browse").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Download Latest (Requires Internet)").assertIsNotEnabled()
    }

    @Test
    fun `should show connecting state`() {
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Connecting,
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }
        composeTestRule.onNodeWithText("Connecting to Forumslader…").assertExists()
    }

    @Test
    fun `should show validating state`() {
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Validating,
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }
        composeTestRule.onNodeWithText("Validating Firmware…").assertExists()
    }

    @Test
    fun `should show verifying state`() {
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Verifying,
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }
        composeTestRule.onNodeWithText("Verifying Update…").assertExists()
    }

    @Test
    fun `should show success state`() {
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Success,
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }
        composeTestRule.onNodeWithText("Update Successful!").assertExists()
    }

    @Test
    fun `should show error state`() {
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Error("Test Error"),
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }
        composeTestRule.onNodeWithText("Error: Test Error").assertExists()
    }

    @Test
    fun `should start update again when retry clicked after error`() {
        // given
        val uri = mockk<Uri>(relaxed = true)
        val startedUris = mutableListOf<Uri>()
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Error("Test Error"),
                sharedUri = uri,
                onStartUpdate = { startedUris += it },
                onDownloadLatest = {}
            )
        }

        // when
        composeTestRule.onNodeWithText("Start Update").performClick()

        // then
        startedUris shouldBe listOf(uri)
    }

    @Test
    fun `should show cancel button and trigger onCancelUpdate when update is running`() {
        // given
        var cancelClicked = false
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Uploading(30),
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {},
                onCancelUpdate = { cancelClicked = true }
            )
        }

        // when
        composeTestRule.onNodeWithText("Cancel").performClick()

        // then
        cancelClicked shouldBe true
    }

    @Test
    fun `should show done button and trigger onReset when update succeeds`() {
        // given
        var resetClicked = false
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Success,
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            ) { resetClicked = true }
        }

        // when
        composeTestRule.onNodeWithText("Done").performClick()

        // then
        resetClicked shouldBe true
    }

    @Test
    @Config(qualifiers = "de")
    fun `should display German UI text when locale is German`() {
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Idle,
                sharedUri = null,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }

        composeTestRule.onNodeWithText("Firmware-Update (V6)").assertExists()
        composeTestRule.onNodeWithText("Neueste herunterladen (Internet erforderlich)").assertExists()
        composeTestRule.onNodeWithText("Durchsuchen").assertExists()
    }

    @Test
    fun `should show progress indicator and disable button when downloading latest firmware`() {
        // given / when
        composeTestRule.setContent {
            FirmwareUpdateCard(
                dfuState = DfuState.Idle,
                sharedUri = null,
                isDownloadingLatest = true,
                onStartUpdate = {},
                onDownloadLatest = {}
            )
        }

        // then
        composeTestRule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertExists()
        composeTestRule.onNodeWithText("Download Latest (Requires Internet)").assertIsNotEnabled()
    }
}
