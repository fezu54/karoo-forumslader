package org.happycode.karoo.forumslader.extension

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.FitEffect
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ForumsladerExtensionTest {

    private lateinit var context: Context
    private lateinit var extension: ForumsladerExtension

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        val mockPrefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns mockPrefs

        extension = ForumsladerExtension()

        // Mock Application context which is used internally by KarooExtension
        val applicationContext = mockk<Context>(relaxed = true)
        every { context.applicationContext } returns applicationContext
    }

    @Test
    fun `should manage fit emitter lifecycle when start fit is called`() {
        // given
        val emitter = mockk<Emitter<FitEffect>>(relaxed = true)
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        extension.startFit(emitter)

        // then
        verify { emitter.setCancellable(any()) }

        // Simulating cancellation to verify the execution path
        cancelSlot.captured.invoke()
    }

    @Test
    fun `should provide all supported data types`() {
        // when
        val types = extension.types

        // then
        assertEquals(17, types.size, "Should provide exactly 17 data types")
        assertTrue(types.any { it.typeId == DataFieldId.BATTERY_LEVEL }, "Should include battery level type")
        assertTrue(types.any { it.typeId == DataFieldId.BATTERY_RANGE }, "Should include battery range type")
        assertTrue(types.any { it.typeId == DataFieldId.SPEED }, "Should include speed type")
    }

    @Test
    fun `should handle missing permission when start scan is called`() {
        // given
        val spyExtension = spyk(extension)
        every { spyExtension.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
        val emitter = mockk<Emitter<Device>>(relaxed = true)

        // when
        spyExtension.startScan(emitter)

        // then
        verify { emitter.setCancellable(any()) }
    }
}
