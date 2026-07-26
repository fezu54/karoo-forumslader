package org.happycode.karoo.forumslader.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForumsladerConfigTest {

    private lateinit var context: Context
    private lateinit var config: ForumsladerConfig

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        config = ForumsladerConfig(context)
    }

    @Test
    fun `should have default values`() {
        assertEquals(2200, config.wheelsize)
        assertEquals(14, config.poles)
        assertEquals(ForumsladerVersion.Unknown, config.version)
        assertEquals(null, config.lockedMacAddress)
    }

    @Test
    fun `should persist wheelsize`() {
        config.wheelsize = 2100
        val newConfig = ForumsladerConfig(context)
        assertEquals(2100, newConfig.wheelsize)
    }

    @Test
    fun `should persist poles`() {
        config.poles = 28
        val newConfig = ForumsladerConfig(context)
        assertEquals(28, newConfig.poles)
    }

    @Test
    fun `should persist version`() {
        config.version = ForumsladerVersion.V6
        val newConfig = ForumsladerConfig(context)
        assertEquals(ForumsladerVersion.V6, newConfig.version)
        
        config.version = ForumsladerVersion.V5
        assertEquals(ForumsladerVersion.V5, ForumsladerConfig(context).version)
    }

    @Test
    fun `should persist lockedMacAddress`() {
        config.lockedMacAddress = "00:11:22:33:44:55"
        val newConfig = ForumsladerConfig(context)
        assertEquals("00:11:22:33:44:55", newConfig.lockedMacAddress)
        
        config.lockedMacAddress = null
        assertEquals(null, ForumsladerConfig(context).lockedMacAddress)
    }
}
