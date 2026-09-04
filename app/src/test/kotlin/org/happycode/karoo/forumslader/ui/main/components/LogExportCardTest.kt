package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.happycode.karoo.forumslader.theme.AppTheme
import org.junit.Assert.assertTrue
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
        //given
        composeTestRule.setContent {
            AppTheme {
                LogExportCard(
                    csvRowCount = 42,
                    csvFileSize = 2048L,
                    isServerRunning = false,
                    serverUrl = null,
                    onSaveToUsb = {},
                    onToggleServer = {},
                    onClearLogs = {}
                )
            }
        }

        //then
        composeTestRule.onNodeWithText("Diagnostics & Logs").assertIsDisplayed()
        composeTestRule.onNodeWithText("42 rows (2 KB)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save to USB Storage").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share to Phone (Wi-Fi)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear Logs").assertIsDisplayed()
    }

    @Test
    fun `should trigger onSaveToUsb when save button clicked`() {
        //given
        var clicked = false
        composeTestRule.setContent {
            AppTheme {
                LogExportCard(
                    csvRowCount = 10,
                    csvFileSize = 500L,
                    isServerRunning = false,
                    serverUrl = null,
                    onSaveToUsb = { clicked = true },
                    onToggleServer = {},
                    onClearLogs = {}
                )
            }
        }

        //when
        composeTestRule.onNodeWithText("Save to USB Storage").performClick()

        //then
        assertTrue(clicked)
    }

    @Test
    fun `should trigger onToggleServer when share wifi button clicked`() {
        //given
        var clicked = false
        composeTestRule.setContent {
            AppTheme {
                LogExportCard(
                    csvRowCount = 10,
                    csvFileSize = 500L,
                    isServerRunning = false,
                    serverUrl = null,
                    onSaveToUsb = {},
                    onToggleServer = { clicked = true },
                    onClearLogs = {}
                )
            }
        }

        //when
        composeTestRule.onNodeWithText("Share to Phone (Wi-Fi)").performClick()

        //then
        assertTrue(clicked)
    }

    @Test
    fun `should trigger onClearLogs when clear logs clicked`() {
        //given
        var clicked = false
        composeTestRule.setContent {
            AppTheme {
                LogExportCard(
                    csvRowCount = 10,
                    csvFileSize = 500L,
                    isServerRunning = false,
                    serverUrl = null,
                    onSaveToUsb = {},
                    onToggleServer = {},
                    onClearLogs = { clicked = true }
                )
            }
        }

        //when
        composeTestRule.onNodeWithText("Clear Logs").performClick()

        //then
        assertTrue(clicked)
    }

    @Test
    fun `should display qr code and stop server button when server is active`() {
        //given
        var stopClicked = false
        val testUrl = "http://192.168.1.100:8080"
        composeTestRule.setContent {
            AppTheme {
                LogExportCard(
                    csvRowCount = 15,
                    csvFileSize = 1024L * 1024L * 3L, // 3.0 MB
                    isServerRunning = true,
                    serverUrl = testUrl,
                    statusMessage = "Server active",
                    onSaveToUsb = {},
                    onToggleServer = { stopClicked = true },
                    onClearLogs = {}
                )
            }
        }

        //then
        composeTestRule.onNodeWithText("15 rows (3.0 MB)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan with phone camera to download logs:").assertIsDisplayed()
        composeTestRule.onNodeWithText(testUrl).assertIsDisplayed()
        composeTestRule.onNodeWithText("Stop Server").assertIsDisplayed()
        composeTestRule.onNodeWithText("Server active").assertIsDisplayed()

        //when
        composeTestRule.onNodeWithText("Stop Server").performClick()

        //then
        assertTrue(stopClicked)
    }

    @Test
    fun `should display bytes format when file size is under 1 KB`() {
        //given
        composeTestRule.setContent {
            AppTheme {
                LogExportCard(
                    csvRowCount = 2,
                    csvFileSize = 512L,
                    isServerRunning = false,
                    serverUrl = null,
                    onSaveToUsb = {},
                    onToggleServer = {},
                    onClearLogs = {}
                )
            }
        }

        //then
        composeTestRule.onNodeWithText("2 rows (512 B)").assertIsDisplayed()
    }
}
