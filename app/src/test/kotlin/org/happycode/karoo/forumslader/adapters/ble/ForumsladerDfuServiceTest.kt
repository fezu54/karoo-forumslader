package org.happycode.karoo.forumslader.adapters.ble

import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.MainActivity
import org.junit.Test

class ForumsladerDfuServiceTest {

    private class TestableDfuService : ForumsladerDfuService() {
        public override fun getNotificationTarget() = super.getNotificationTarget()
        public override fun isDebug() = super.isDebug()
    }

    @Test
    fun `should target MainActivity for notifications`() {
        val service = TestableDfuService()
        service.getNotificationTarget() shouldBe MainActivity::class.java
    }

    @Test
    fun `should have debug disabled`() {
        val service = TestableDfuService()
        service.isDebug() shouldBe false
    }
}
