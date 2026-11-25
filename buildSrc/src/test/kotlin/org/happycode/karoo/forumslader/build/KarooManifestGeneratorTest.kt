package org.happycode.karoo.forumslader.build

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class KarooManifestGeneratorTest : ShouldSpec({

    fun createSpec(
        latestVersion: String = "v3.12.0",
        latestVersionCode: Int = 42,
        packageName: String = "org.happycode.karoo.forumslader",
        description: String? = "Forumslader telemetry, range estimation, and DFU updates for Karoo.",
        releaseNotes: String? = "* Improved battery range estimation",
        screenshotUrls: List<String> = listOf(
            "https://github.com/fezu54/karoo-forumslader/releases/latest/download/card-1.png",
            "https://github.com/fezu54/karoo-forumslader/releases/latest/download/card-2.png"
        ),
        tags: List<String> = emptyList()
    ) = KarooAppManifestSpec(
        label = "Karoo Forumslader",
        packageName = packageName,
        latestApkUrl = "https://github.com/fezu54/karoo-forumslader/releases/latest/download/app-release.apk",
        latestVersion = latestVersion,
        latestVersionCode = latestVersionCode,
        iconUrl = "https://github.com/fezu54/karoo-forumslader/releases/latest/download/karoo-forumslader.png",
        developer = "fezu54",
        description = description,
        releaseNotes = releaseNotes,
        screenshotUrls = screenshotUrls,
        tags = tags
    )

    should("serialize all configured KarooAppManifest fields when building store manifest") {
        // given
        val spec = createSpec()

        // when
        val json = KarooManifestJsonSerializer.toJson(spec)

        // then
        json shouldContain "\"label\": \"Karoo Forumslader\""
        json shouldContain "\"packageName\": \"org.happycode.karoo.forumslader\""
        json shouldContain "\"latestApkUrl\": \"https://github.com/fezu54/karoo-forumslader/releases/latest/download/app-release.apk\""
        json shouldContain "\"latestVersion\": \"3.12.0\""
        json shouldContain "\"latestVersionCode\": 42"
        json shouldNotContain "\"latestVersionCode\": \"42\""
        json shouldContain "\"iconUrl\": \"https://github.com/fezu54/karoo-forumslader/releases/latest/download/karoo-forumslader.png\""
        json shouldContain "\"developer\": \"fezu54\""
        json shouldContain "\"description\": \"Forumslader telemetry, range estimation, and DFU updates for Karoo.\""
        json shouldContain "\"releaseNotes\": \"* Improved battery range estimation\""
        json shouldContain "\"screenshotUrls\": ["
        json shouldContain "\"https://github.com/fezu54/karoo-forumslader/releases/latest/download/card-1.png\""
        json shouldContain "\"https://github.com/fezu54/karoo-forumslader/releases/latest/download/card-2.png\""
    }

    should("omit tags field when tags list is empty") {
        // given
        val spec = createSpec(tags = emptyList())

        // when
        val json = KarooManifestJsonSerializer.toJson(spec)

        // then
        json shouldNotContain "\"tags\""
    }

    should("include tags field when tags are provided") {
        // given
        val spec = createSpec(tags = listOf("performance"))

        // when
        val json = KarooManifestJsonSerializer.toJson(spec)

        // then
        json shouldContain "\"tags\": ["
        json shouldContain "\"performance\""
    }

    should("escape quotes backslashes and newlines when serializing json strings") {
        // given
        val spec = createSpec(
            description = "Line 1 \"quoted\" and \\path\nLine 2",
            releaseNotes = "* Fix \"parser\"\n* Keep \\escaped"
        )

        // when
        val json = KarooManifestJsonSerializer.toJson(spec)

        // then
        json shouldContain "\"description\": \"Line 1 \\\"quoted\\\" and \\\\path\\nLine 2\""
        json shouldContain "\"releaseNotes\": \"* Fix \\\"parser\\\"\\n* Keep \\\\escaped\""
    }

    should("strip leading v prefix when normalizing release version name") {
        // given
        val taggedVersion = "v3.12.0"
        val plainVersion = "3.12.0"

        // when
        val normalizedTagged = KarooManifestVersionNormalizer.normalize(taggedVersion)
        val normalizedPlain = KarooManifestVersionNormalizer.normalize(plainVersion)

        // then
        normalizedTagged shouldBe "3.12.0"
        normalizedPlain shouldBe "3.12.0"
    }

    should("sanitize markdown headers bold markers and blockquotes when formatting release notes") {
        // given
        val markdown = """
            ## What's new

            * **feat(ui): add range card**
              > Shows remaining km on route

            * **fix(ble): reconnect faster**
        """.trimIndent()

        // when
        val formatted = ReleaseNotesFormatter.format(markdown)

        // then
        formatted shouldBe """
            * feat(ui): add range card
              Shows remaining km on route
            * fix(ble): reconnect faster
        """.trimIndent()
    }

    should("return fallback release notes when markdown input is null or blank") {
        // given
        val blankMarkdown = "   \n\t  "

        // when
        val fromNull = ReleaseNotesFormatter.format(null)
        val fromBlank = ReleaseNotesFormatter.format(blankMarkdown)

        // then
        fromNull shouldBe "Bug fixes and improvements."
        fromBlank shouldBe "Bug fixes and improvements."
    }

    should("throw IllegalArgumentException when packageName is blank or versionCode is not positive") {
        // given
        val blankPackageSpec = createSpec(packageName = "  ")
        val invalidVersionSpec = createSpec(latestVersionCode = 0)

        // when / then
        shouldThrow<IllegalArgumentException> {
            KarooManifestJsonSerializer.toJson(blankPackageSpec)
        }
        shouldThrow<IllegalArgumentException> {
            KarooManifestJsonSerializer.toJson(invalidVersionSpec)
        }
    }
})
