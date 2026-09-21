package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LogExportCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `should display telemetry stats and action buttons when server is idle`() {
        //when
        renderCard(
            csvRowCount = 42,
            csvFileSize = 2048L,
            isServerRunning = false,
            serverUrl = null,
        )

        //then
        composeTestRule.onNodeWithText("Diagnostics & Logs").assertIsDisplayed()
        composeTestRule.onNodeWithText("42 rows (2 KB)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save to USB Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share to Phone (Wi-Fi)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear Logs").assertIsDisplayed()
    }

    @Test
    fun `should trigger onSaveToUsb when save button is clicked`() {
        //given
        var clicked = false
        renderCard(onSaveToUsb = { clicked = true })

        //when
        composeTestRule.onNodeWithText("Save to USB Storage").performClick()

        //then
        clicked shouldBe true
    }

    @Test
    fun `should trigger onToggleServer when share wifi button is clicked`() {
        //given
        var clicked = false
        renderCard(onToggleServer = { clicked = true })

        //when
        composeTestRule.onNodeWithText("Share to Phone (Wi-Fi)").performClick()

        //then
        clicked shouldBe true
    }

    @Test
    fun `should trigger onClearLogs when clear logs button is clicked`() {
        //given
        var clicked = false
        renderCard(onClearLogs = { clicked = true })

        //when
        composeTestRule.onNodeWithText("Clear Logs").performClick()

        //then
        clicked shouldBe true
    }

    @Test
    fun `should display qr code and server info when server is active`() {
        //given
        val testUrl = "http://192.168.1.100:8080"

        //when
        renderCard(
            csvRowCount = 15,
            csvFileSize = 3L * 1024L * 1024L,
            isServerRunning = true,
            serverUrl = testUrl,
            statusMessage = "Server active",
        )

        //then
        composeTestRule.onNodeWithText("15 rows (3.0 MB)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan with phone camera to download logs:").assertIsDisplayed()
        composeTestRule.onNodeWithText(testUrl).assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server active").assertIsDisplayed()
    }

    @Test
    fun `should trigger onToggleServer when stop server button is clicked`() {
        //given
        var stopClicked = false
        renderCard(
            isServerRunning = true,
            serverUrl = "http://192.168.1.100:8080",
            onToggleServer = { stopClicked = true },
        )

        //when
        composeTestRule.onNodeWithText("Stop Server").performClick()

        //then
        stopClicked shouldBe true
    }

    @Test
    fun `should format file size in bytes when file size is under 1 KB`() {
        //when
        renderCard(
            csvRowCount = 2,
            csvFileSize = 512L,
        )

        //then
        composeTestRule.onNodeWithText("2 rows (512 B)").assertIsDisplayed()
    }

    private fun renderCard(
        csvRowCount: Int = 10,
        csvFileSize: Long = 500L,
        isServerRunning: Boolean = false,
        serverUrl: String? = null,
        statusMessage: String? = null,
        onSaveToUsb: () -> Unit = {},
        onToggleServer: () -> Unit = {},
        onClearLogs: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                LogExportCard(
                    csvRowCount = csvRowCount,
                    csvFileSize = csvFileSize,
                    isServerRunning = isServerRunning,
                    serverUrl = serverUrl,
                    statusMessage = statusMessage,
                    onSaveToUsb = onSaveToUsb,
                    onToggleServer = onToggleServer,
                    onClearLogs = onClearLogs,
                )
            }
        }
    }
}
