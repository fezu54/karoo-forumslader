package org.happycode.karoo.forumslader.adapters.network

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

class CoroutineLogServerTest : ShouldSpec({

    lateinit var scope: CoroutineScope
    lateinit var tempDir: Path
    lateinit var logcatFile: Path
    lateinit var csvFile: Path
    lateinit var server: CoroutineLogServer

    beforeEach {
        scope = CoroutineScope(Dispatchers.IO)
        tempDir = createTempDirectory()
        logcatFile = tempDir.resolve("forumslader-logcat.txt").apply {
            writeText("Log line 1\nLog line 2")
        }
        csvFile = tempDir.resolve("telemetry.csv").apply {
            writeText("col1,col2\nval1,val2")
        }
        server = CoroutineLogServer(
            scope = scope,
            getLogcatPath = { logcatFile },
            getCsvPath = { csvFile },
            ipAddressProvider = { "127.0.0.1" }
        )
    }

    afterEach {
        server.stop()
        scope.cancel()
        tempDir.toFile().deleteRecursively()
    }

    should("start server and expose correct url when started with valid ip") {
        //when
        val result = server.start(port = 0)

        //then
        result.isSuccess shouldBe true
        server.isRunning.value shouldBe true
        server.serverUrl.value.shouldNotBeNull().shouldStartWith("http://127.0.0.1:")
    }

    should("serve index html when root url requested") {
        //given
        val url = server.start(port = 0).getOrThrow()

        //when
        val response = httpGet(url)

        //then
        response.code shouldBe HttpURLConnection.HTTP_OK
        response.body shouldContain "Forumslader Diagnostics"
        response.body shouldContain "/logcat"
        response.body shouldContain "/telemetry.csv"
    }

    should("serve logcat file content when logcat endpoint requested") {
        //given
        val url = server.start(port = 0).getOrThrow()

        //when
        val response = httpGet("$url/logcat")

        //then
        response.code shouldBe HttpURLConnection.HTTP_OK
        response.body shouldBe "Log line 1\nLog line 2"
    }

    should("serve telemetry csv file content when telemetry endpoint requested") {
        //given
        val url = server.start(port = 0).getOrThrow()

        //when
        val response = httpGet("$url/telemetry.csv")

        //then
        response.code shouldBe HttpURLConnection.HTTP_OK
        response.body shouldBe "col1,col2\nval1,val2"
    }

    should("return 404 response code when unknown path requested") {
        //given
        val url = server.start(port = 0).getOrThrow()

        //when
        val response = httpGet("$url/unknown")

        //then
        response.code shouldBe HttpURLConnection.HTTP_NOT_FOUND
    }

    should("stop server and update isRunning state when stop is called") {
        //given
        server.start(port = 0)
        server.isRunning.value shouldBe true

        //when
        server.stop()

        //then
        server.isRunning.value shouldBe false
        server.serverUrl.value.shouldBeNull()
    }

    should("return existing url when start is called while already running") {
        //given
        val firstUrl = server.start(port = 0).getOrThrow()

        //when
        val secondUrl = server.start(port = 0).getOrThrow()

        //then
        firstUrl shouldBe secondUrl
    }

    should("return 404 response code when requested log file path is null") {
        //given
        val serverWithMissingFiles = CoroutineLogServer(
            scope = scope,
            getLogcatPath = { null },
            getCsvPath = { null },
            ipAddressProvider = { "127.0.0.1" }
        )
        val url = serverWithMissingFiles.start(port = 0).getOrThrow()

        //when
        val response = httpGet("$url/logcat")
        serverWithMissingFiles.stop()

        //then
        response.code shouldBe HttpURLConnection.HTTP_NOT_FOUND
    }

    should("auto stop server when auto stop duration expires") {
        //given
        val shortTimeoutServer = CoroutineLogServer(
            scope = scope,
            getLogcatPath = { logcatFile },
            getCsvPath = { csvFile },
            ipAddressProvider = { "127.0.0.1" },
            autoStopDurationMs = 50L
        )
        shortTimeoutServer.start(port = 0)
        shortTimeoutServer.isRunning.value shouldBe true

        //when & then
        eventually(500.milliseconds) {
            shortTimeoutServer.isRunning.value shouldBe false
        }
    }

    should("succeed when started with default ip address provider") {
        //given
        val defaultServer = CoroutineLogServer(
            scope = scope,
            getLogcatPath = { logcatFile },
            getCsvPath = { csvFile }
        )

        //when
        val result = defaultServer.start(port = 0)
        defaultServer.stop()

        //then
        result.isSuccess shouldBe true
    }
})

private data class HttpResponse(val code: Int, val body: String)

private fun httpGet(url: String): HttpResponse {
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    return try {
        val code = connection.responseCode
        val body = if (code == HttpURLConnection.HTTP_OK) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            ""
        }
        HttpResponse(code, body)
    } finally {
        connection.disconnect()
    }
}
