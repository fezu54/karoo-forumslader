package org.happycode.karoo.forumslader.model

import android.content.Context
import android.content.SharedPreferences
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME

class ForumsladerConfigTest : ShouldSpec({
    val storage = mutableMapOf<String, Any?>()
    val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    val prefs = mockk<SharedPreferences>()
    val context = mockk<Context>()

    beforeEach {
        storage.clear()
        every { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) } returns prefs

        every { prefs.getInt(any(), any()) } answers { storage[firstArg()] as? Int ?: secondArg() }
        every { prefs.getBoolean(any(), any()) } answers { storage[firstArg()] as? Boolean ?: secondArg() }
        every { prefs.getString(any(), any()) } answers { storage[firstArg()] as? String ?: secondArg() }

        every { prefs.edit() } returns editor
        every { editor.putInt(any(), any()) } answers { storage[firstArg()] = secondArg(); editor }
        every { editor.putBoolean(any(), any()) } answers { storage[firstArg()] = secondArg(); editor }
        every { editor.putString(any(), any()) } answers { storage[firstArg()] = secondArg(); editor }
        every { editor.remove(any()) } answers { storage.remove(firstArg()); editor }
        every { editor.apply() } returns Unit
    }

    should("have default values when initialized") {
        // given
        val config = ForumsladerConfig(context)

        // then
        with(config) {
            wheelsize shouldBe 2200
            poles shouldBe 14
            version shouldBe ForumsladerVersion.Unknown
            lockedMacAddress shouldBe null
        }
    }

    should("persist wheelsize when updated") {
        // given
        val config = ForumsladerConfig(context)
        val newValue = 2100

        // when
        config.wheelsize = newValue

        // then
        ForumsladerConfig(context).wheelsize shouldBe newValue
    }

    should("persist poles when updated") {
        // given
        val config = ForumsladerConfig(context)
        val newValue = 28

        // when
        config.poles = newValue

        // then
        ForumsladerConfig(context).poles shouldBe newValue
    }

    should("persist version when updated") {
        // given
        val config = ForumsladerConfig(context)
        val newValue = ForumsladerVersion.V6

        // when
        config.version = newValue

        // then
        ForumsladerConfig(context).version shouldBe newValue
    }

    should("persist lockedMacAddress when updated") {
        // given
        val config = ForumsladerConfig(context)
        val newValue = "11:22:33:44:55:66"

        // when
        config.lockedMacAddress = newValue

        // then
        ForumsladerConfig(context).lockedMacAddress shouldBe newValue
    }
})
