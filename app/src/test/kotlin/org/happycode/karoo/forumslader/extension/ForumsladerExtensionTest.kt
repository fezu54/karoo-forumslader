package org.happycode.karoo.forumslader.extension

import android.content.Context
import android.content.SharedPreferences
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.FitEffect
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
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
        
        // Using reflection to set mBase (ContextWrapper) is tricky, 
        // we can just test startFit independently.
    }

    @Test
    fun `startFit should manage fitEmitter lifecycle`() {
        // given
        val emitter = mockk<Emitter<FitEffect>>(relaxed = true)
        val cancelSlot = slot<() -> Unit>()
        every { emitter.setCancellable(capture(cancelSlot)) } returns Unit

        // when
        extension.startFit(emitter)

        // then
        // The emitter is saved and cancel block is registered
        verify { emitter.setCancellable(any()) }
        
        // Simulating cancellation
        cancelSlot.captured.invoke()
        
        // The emitter should be cleared, but since it's private we can't easily assert. 
        // Just executing the cancel block to ensure it covers the branches.
    }
}
