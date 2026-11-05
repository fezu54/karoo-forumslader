package org.happycode.karoo.forumslader.ui.main

import android.net.Uri
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.happycode.karoo.forumslader.domain.DfuState
import org.happycode.karoo.forumslader.domain.DfuUpdateService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.URI

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DfuViewModelTest {

    private val serviceState = MutableStateFlow<DfuState>(DfuState.Idle)
    private val updateService = mockk<DfuUpdateService>(relaxed = true) {
        every { state } returns serviceState
    }
    private val viewModel = DfuViewModel(updateService)

    @Test
    fun `should expose update service state when service state changes`() {
        // when
        serviceState.value = DfuState.Uploading(progressPercent = 42)

        // then
        viewModel.state.value shouldBe DfuState.Uploading(progressPercent = 42)
    }

    @Test
    fun `should start update with converted uri when firmware uri given`() {
        // given
        val firmwareUri = Uri.parse("content://firmware/firmware.zip")

        // when
        viewModel.startUpdate(firmwareUri)

        // then
        verify { updateService.startUpdate(URI("content://firmware/firmware.zip")) }
    }

    @Test
    fun `should handle invalid uri without crashing when firmware uri has invalid syntax`() {
        // given
        val invalidUri = Uri.parse("content://invalid path with spaces")

        // when
        viewModel.startUpdate(invalidUri)

        // then
        verify(exactly = 0) { updateService.startUpdate(any()) }
    }

    @Test
    fun `should delegate cancelUpdate to updateService when cancelUpdate called`() {
        // when
        viewModel.cancelUpdate()

        // then
        verify { updateService.cancelUpdate() }
    }

    @Test
    fun `should delegate reset to updateService when reset called`() {
        // when
        viewModel.reset()

        // then
        verify { updateService.reset() }
    }
}
