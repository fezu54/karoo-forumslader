package org.happycode.karoo.forumslader.framework

import android.content.Context
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

class DfuUpdateServiceProviderTest : ShouldSpec({

    lateinit var applicationContext: Context
    lateinit var activityContext: Context

    beforeTest {
        DfuUpdateServiceProvider.reset()
        applicationContext = mockk<Context>(relaxed = true)
        activityContext = mockk<Context>(relaxed = true) {
            every { this@mockk.applicationContext } returns applicationContext
        }
    }

    should("return same instance when getInstance is called multiple times") {
        // when
        val first = DfuUpdateServiceProvider.getInstance(activityContext)
        val second = DfuUpdateServiceProvider.getInstance(activityContext)

        // then
        first shouldBeSameInstanceAs second
    }

    should("return same instance when getInstance is called concurrently") {
        // when
        val services = (1..100).map {
            async(Dispatchers.IO) { DfuUpdateServiceProvider.getInstance(activityContext) }
        }.awaitAll()

        // then
        services.forEach { it shouldBeSameInstanceAs services.first() }
    }

    should("use application context instead of given context when creating service") {
        // when
        DfuUpdateServiceProvider.getInstance(activityContext)

        // then
        verify(exactly = 1) { activityContext.applicationContext }
    }
})
