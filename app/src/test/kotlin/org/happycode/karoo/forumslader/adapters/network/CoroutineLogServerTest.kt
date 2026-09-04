package org.happycode.karoo.forumslader.adapters.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.time.Duration.Companion.milliseconds

class CoroutineLogServerTest {

    private lateinit var scope: CoroutineScope
    private lateinit var tempDir: Path
    private lateinit var logcatFile: Path
    private lateinit var csvFile: Path
    private lateinit var server: CoroutineLogServer

    @BeforeEach
    fun setUp() {
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

    @AfterEach
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    @Test
    fun `should start server and expose correct url with injected ip`() {
        //when
        val result = server.start(port = 0)

        //then
        assertTrue(result.isSuccess)
        assertTrue(server.isRunning.value)
        assertTrue(server.serverUrl.value!!.startsWith("http://127.0.0.1:"))
    }

    @Test
    fun `should serve index html on get root`() {
        //given
        val url = server.start(port = 0).getOrThrow()

        //when
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        val responseCode = connection.responseCode
        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }

        //then
        assertEquals(200, responseCode)
        assertTrue(responseBody.contains("Forumslader Diagnostics"))
        assertTrue(responseBody.contains("/logcat"))
        assertTrue(responseBody.contains("/telemetry.csv"))
    }

    @Test
    fun `should serve logcat file on get logcat`() {
        //given
        val url = server.start(port = 0).getOrThrow()

        //when
        val connection = URI("$url/logcat").toURL().openConnection() as HttpURLConnection
        val responseCode = connection.responseCode
        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }

        //then
        assertEquals(200, responseCode)
        assertEquals("Log line 1\nLog line 2", responseBody)
    }

    @Test
    fun `should serve telemetry csv file on get telemetry csv`() {
        //given
        val url = server.start(port = 0).getOrThrow()

        //when
        val connection = URI("$url/telemetry.csv").toURL().openConnection() as HttpURLConnection
        val responseCode = connection.responseCode
        val responseBody = connection.inputStream.bufferedReader().use { it.readText() }

        //then
        assertEquals(200, responseCode)
        assertEquals("col1,col2\nval1,val2", responseBody)
    }

    @Test
    fun `should return 404 for unknown path`() {
        //given
        val url = server.start(port = 0).getOrThrow()

        //when
        val connection = URI("$url/unknown").toURL().openConnection() as HttpURLConnection
        val responseCode = connection.responseCode

        //then
        assertEquals(404, responseCode)
    }

    @Test
    fun `should stop server and update isRunning state when stop called`() {
        //given
        server.start(port = 0)
        assertTrue(server.isRunning.value)

        //when
        server.stop()

        //then
        assertFalse(server.isRunning.value)
        assertNull(server.serverUrl.value)
    }

    @Test
    fun `should return existing url when start called while already running`() {
        //given
        val firstUrl = server.start(port = 0).getOrThrow()

        //when
        val secondUrl = server.start(port = 0).getOrThrow()

        //then
        assertEquals(firstUrl, secondUrl)
    }

    @Test
    fun `should return 404 when requested log file is missing`() {
        //given
        val serverWithMissingFiles = CoroutineLogServer(
            scope = scope,
            getLogcatPath = { null },
            getCsvPath = { null },
            ipAddressProvider = { "127.0.0.1" }
        )
        val url = serverWithMissingFiles.start(port = 0).getOrThrow()

        //when
        val connection = URI("$url/logcat").toURL().openConnection() as HttpURLConnection
        val responseCode = connection.responseCode
        serverWithMissingFiles.stop()

        //then
        assertEquals(404, responseCode)
    }

    @Test
    fun `should auto stop when timeout duration expires`() = runBlocking {
        //given
        val shortTimeoutServer = CoroutineLogServer(
            scope = scope,
            getLogcatPath = { logcatFile },
            getCsvPath = { csvFile },
            ipAddressProvider = { "127.0.0.1" },
            autoStopDurationMs = 50L
        )
        shortTimeoutServer.start(port = 0)
        assertTrue(shortTimeoutServer.isRunning.value)

        //when
        delay(100L.milliseconds)

        //then
        assertFalse(shortTimeoutServer.isRunning.value)
    }

    @Test
    fun `should use default ip address provider when none supplied`() {
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
        assertTrue(result.isSuccess)
    }
}
