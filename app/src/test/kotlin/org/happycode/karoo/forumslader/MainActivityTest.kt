package org.happycode.karoo.forumslader

import android.content.Intent
import android.net.Uri
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import io.kotest.matchers.shouldNotBe

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun `should create successfully with regular intent`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        controller.get() shouldNotBe null
    }

    @Test
    fun `should handle SEND intent with stream extra`() {
        val uri = Uri.parse("content://test.hex")
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, uri)
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).create().start().resume()
        controller.get() shouldNotBe null
    }
}
