package org.happycode.karoo.forumslader.adapters.network

import android.content.Context
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Files

class FirmwareDownloaderTest : ShouldSpec({

    lateinit var tempDir: File
    lateinit var context: Context
    lateinit var mockConnection: HttpURLConnection

    beforeTest {
        tempDir = Files.createTempDirectory("cache_test").toFile()
        context = mockk {
            every { cacheDir } returns tempDir
        }
        mockConnection = mockk(relaxed = true)
    }

    afterTest {
        tempDir.deleteRecursively()
    }

    should("return downloaded file uri when download succeeds") {
        // given
        val zipBytes = "PK_DUMMY_ZIP_DATA".toByteArray()
        every { mockConnection.responseCode } returns 200
        every { mockConnection.inputStream } returns ByteArrayInputStream(zipBytes)

        val downloader = FirmwareDownloader(
            context = context,
            connectionFactory = { mockConnection }
        )

        // when
        val result = downloader.downloadLatest()

        // then
        result.shouldBeSuccess()
        val targetFile = tempDir.resolve("firmware/fw-flv6-latest.zip")
        targetFile.exists() shouldBe true
        targetFile.readBytes() shouldBe zipBytes
    }

    should("return failure when server returns non-200 HTTP response") {
        // given
        every { mockConnection.responseCode } returns 404

        val downloader = FirmwareDownloader(
            context = context,
            connectionFactory = { mockConnection }
        )

        // when
        val result = downloader.downloadLatest()

        // then
        result.shouldBeFailure()
    }

    should("return failure when connection throws exception") {
        // given
        val downloader = FirmwareDownloader(
            context = context,
            connectionFactory = { throw IOException("Network unreachable") }
        )

        // when
        val result = downloader.downloadLatest()

        // then
        result.shouldBeFailure()
    }

    should("follow 303 redirect and download file") {
        // given
        val zipBytes = "PK_DUMMY_ZIP_DATA".toByteArray()
        val redirectConnection: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 303
            every { getHeaderField("Location") } returns "http://www.forumslader.de/fileadmin/fw-flv6/flv6.zip"
        }
        val targetConnection: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 200
            every { inputStream } returns ByteArrayInputStream(zipBytes)
        }

        val downloader = FirmwareDownloader(
            context = context,
            connectionFactory = { url ->
                if (url.toString() == "https://www.forumslader.de/fw-flv6-latest") redirectConnection else targetConnection
            }
        )

        // when
        val result = downloader.downloadLatest()

        // then
        result.shouldBeSuccess()
        val targetFile = tempDir.resolve("firmware/fw-flv6-latest.zip")
        targetFile.exists() shouldBe true
        targetFile.readBytes() shouldBe zipBytes
    }

    should("upgrade http redirects to https to prevent cleartext traffic") {
        // given
        val zipBytes = "PK_DUMMY_ZIP_DATA".toByteArray()
        val requestedUrls = mutableListOf<String>()
        val redirectConnection: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 303
            every { getHeaderField("Location") } returns "http://www.forumslader.de/fileadmin/fw-flv6/flv6.zip"
        }
        val targetConnection: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 200
            every { inputStream } returns ByteArrayInputStream(zipBytes)
        }

        val downloader = FirmwareDownloader(
            context = context,
            connectionFactory = { url ->
                requestedUrls.add(url.toString())
                if (url.toString() == "https://www.forumslader.de/fw-flv6-latest") redirectConnection else targetConnection
            }
        )

        // when
        val result = downloader.downloadLatest()

        // then
        result.shouldBeSuccess()
        requestedUrls shouldBe listOf(
            "https://www.forumslader.de/fw-flv6-latest",
            "https://www.forumslader.de/fileadmin/fw-flv6/flv6.zip"
        )
    }

    should("follow multiple redirects") {
        // given
        val zipBytes = "PK_DUMMY_ZIP_DATA".toByteArray()
        val firstRedirect: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 303
            every { getHeaderField("Location") } returns "https://www.forumslader.de/intermediate"
        }
        val secondRedirect: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 301
            every { getHeaderField("Location") } returns "https://www.forumslader.de/fileadmin/fw-flv6/flv6.zip"
        }
        val targetConnection: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 200
            every { inputStream } returns ByteArrayInputStream(zipBytes)
        }

        val downloader = FirmwareDownloader(
            context = context,
            connectionFactory = { url ->
                when (url.toString()) {
                    "https://www.forumslader.de/fw-flv6-latest" -> firstRedirect
                    "https://www.forumslader.de/intermediate" -> secondRedirect
                    else -> targetConnection
                }
            }
        )

        // when
        val result = downloader.downloadLatest()

        // then
        result.shouldBeSuccess()
        val targetFile = tempDir.resolve("firmware/fw-flv6-latest.zip")
        targetFile.exists() shouldBe true
        targetFile.readBytes() shouldBe zipBytes
    }

    should("return failure when redirect is missing Location header") {
        // given
        val redirectConnection: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 303
            every { getHeaderField("Location") } returns null
        }

        val downloader = FirmwareDownloader(
            context = context,
            connectionFactory = { redirectConnection }
        )

        // when
        val result = downloader.downloadLatest()

        // then
        result.shouldBeFailure()
    }

    should("return failure when redirect exceeds maximum limit") {
        // given
        val redirectConnection: HttpURLConnection = mockk(relaxed = true) {
            every { responseCode } returns 303
            every { getHeaderField("Location") } returns "https://www.forumslader.de/loop"
        }

        val downloader = FirmwareDownloader(
            context = context,
            connectionFactory = { redirectConnection }
        )

        // when
        val result = downloader.downloadLatest()

        // then
        result.shouldBeFailure()
    }
})
