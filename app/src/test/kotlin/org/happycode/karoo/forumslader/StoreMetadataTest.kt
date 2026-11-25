package org.happycode.karoo.forumslader

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class StoreMetadataTest : ShouldSpec({

    val projectRoot = File(requireNotNull(System.getProperty("project.rootDir")) {
        "System property project.rootDir must be configured in testOptions"
    })

    fun parseXml(relativePath: String) = DocumentBuilderFactory.newInstance()
        .apply { isNamespaceAware = true }
        .newDocumentBuilder()
        .parse(File(projectRoot, relativePath))
        .documentElement

    fun assertIsValidPng(file: File) {
        file.exists().shouldBeTrue()
        val header = file.inputStream().use { stream ->
            ByteArray(8).also { buffer ->
                val bytesRead = stream.read(buffer)
                bytesRead shouldBe 8
            }
        }
        val pngSignature = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A
        )
        header.contentEquals(pngSignature).shouldBeTrue()
    }

    should("declare karoo manifest url metadata in AndroidManifest") {
        // given
        val manifestRoot = parseXml("app/src/main/AndroidManifest.xml")
        val applicationElement = manifestRoot.getElementsByTagName("application").item(0) as Element
        val metaDataNodes = applicationElement.getElementsByTagName("meta-data")

        // when
        val manifestMetadata = (0 until metaDataNodes.length)
            .map { metaDataNodes.item(it) as Element }
            .firstOrNull {
                it.getAttributeNS("http://schemas.android.com/apk/res/android", "name") ==
                    "io.hammerhead.karooext.MANIFEST_URL"
            }

        // then
        manifestMetadata.shouldNotBeNull()
        manifestMetadata.getAttributeNS("http://schemas.android.com/apk/res/android", "value") shouldBe
            "https://github.com/fezu54/karoo-forumslader/releases/latest/download/manifest.json"
    }

    should("reference custom extension icon drawable in extension_info xml") {
        // given
        val extensionInfo = parseXml("app/src/main/res/xml/extension_info.xml")
        val drawableIcon = File(projectRoot, "app/src/main/res/drawable/ic_extension.png")
        val legacyVectorIcon = File(projectRoot, "app/src/main/res/drawable-v24/ic_launcher_foreground.xml")

        // when
        val iconReference = extensionInfo.getAttribute("icon")

        // then
        iconReference shouldBe "@drawable/ic_extension"
        assertIsValidPng(drawableIcon)
        legacyVectorIcon.exists().shouldBeFalse()
    }

    should("include store icon and all expected karoo screenshots in docs images") {
        // given
        val storeIcon = File(projectRoot, "docs/images/karoo-forumslader.png")
        val expectedScreenshots = listOf(
            "card-1.png",
            "card-2.png",
            "card-3.png",
            "card-4.png",
            "card-6.png",
            "card-7.png",
            "card-8.png"
        ).map { File(projectRoot, "docs/images/screenshots/$it") }

        // when / then
        assertIsValidPng(storeIcon)
        expectedScreenshots.forEach { screenshot ->
            assertIsValidPng(screenshot)
        }
    }

    should("configure ci workflow to generate validate and publish manifest and store assets") {
        // given
        val workflowText = File(projectRoot, ".github/workflows/ci.yml").readText()
        val workflowLines = workflowText.lines().map { it.trim() }

        // when / then
        workflowText shouldContain ":app:generateManifest"
        workflowText shouldContain "make_latest: true"
        workflowLines shouldContain "app/build/outputs/apk/release/app-release.apk"
        workflowLines shouldContain "app/build/outputs/manifest/manifest.json"
        workflowLines shouldContain "docs/images/karoo-forumslader.png"
        workflowLines shouldContain "docs/images/screenshots/card-*.png"
    }
})
