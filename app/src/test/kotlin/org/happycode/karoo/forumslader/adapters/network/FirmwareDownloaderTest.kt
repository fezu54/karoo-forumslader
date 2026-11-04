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
})
