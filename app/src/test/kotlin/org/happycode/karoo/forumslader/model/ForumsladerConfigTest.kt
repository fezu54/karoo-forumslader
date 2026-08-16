package org.happycode.karoo.forumslader.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun `should have default values when initialized`() {
        // then
        with(config) {
            assertEquals(2200, wheelsize)
            assertEquals(14, poles)
            assertEquals(ForumsladerVersion.Unknown, version)
            assertNull(lockedMacAddress)
        }
    }

    @Test
    fun `should persist wheelsize when updated`() {
        // given
        val newValue = 2100

        // when
        config.wheelsize = newValue

        // then
        assertEquals(newValue, reloadConfig().wheelsize)
    }

    @Test
    fun `should persist poles when updated`() {
        // given
        val newValue = 28

        // when
        config.poles = newValue

        // then
        assertEquals(newValue, reloadConfig().poles)
    }

    @Test
    fun `should persist version when updated`() {
        // given
        val newValue = ForumsladerVersion.V6

        // when
        config.version = newValue

        // then
        assertEquals(newValue, reloadConfig().version)
    }

    @Test
    fun `should persist speedMultiplier when updated`() {
        // given
        val newValue = 1.05f

        // when
        config.speedMultiplier = newValue

        // then
        assertEquals(newValue, reloadConfig().speedMultiplier, 0.001f)
    }

    private fun reloadConfig() = ForumsladerConfig(context)
}
