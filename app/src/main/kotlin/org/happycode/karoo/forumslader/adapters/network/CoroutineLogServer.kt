package org.happycode.karoo.forumslader.adapters.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.happycode.karoo.forumslader.application.LogServerGateway
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.time.Duration.Companion.milliseconds

class CoroutineLogServer(
    private val scope: CoroutineScope,
    private val getLogcatPath: () -> Path?,
    private val getCsvPath: () -> Path?,
    private val ipAddressProvider: () -> String? = { defaultIpAddressProvider() },
    private val autoStopDurationMs: Long = DEFAULT_TIMEOUT_MS,
) : LogServerGateway {

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _serverUrl = MutableStateFlow<String?>(null)
    override val serverUrl: StateFlow<String?> = _serverUrl.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var autoStopJob: Job? = null

    @Synchronized
    override fun start(port: Int): Result<String> = runCatching {
        if (_isRunning.value) {
            return@runCatching _serverUrl.value ?: ""
        }

        val ip = ipAddressProvider() ?: error("No active network connection")
        val socket = ServerSocket(port).also { serverSocket = it }
        val url = "http://$ip:${socket.localPort}"

        _isRunning.value = true
        _serverUrl.value = url

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && !socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch(Dispatchers.IO) { handleClient(client) }
                } catch (_: Exception) {
                    break
                }
            }
            stop()
        }

        autoStopJob = scope.launch {
            delay(autoStopDurationMs.milliseconds)
            stop()
        }

        url
    }

    @Synchronized
    override fun stop() {
        if (!_isRunning.value && serverSocket == null) return

        autoStopJob?.cancel()
        autoStopJob = null

        serverJob?.cancel()
        serverJob = null

        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null

        _isRunning.value = false
        _serverUrl.value = null
    }

    private suspend fun handleClient(client: Socket) = withContext(Dispatchers.IO) {
        runCatching {
            client.use {
                val reader = client.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return@runCatching
                val output = client.getOutputStream()

                when {
                    requestLine.startsWith("GET / ") || requestLine.startsWith("GET /index.html") -> {
                        serveHtml(output)
                    }

                    requestLine.startsWith("GET /logcat") -> {
                        serveFile(output, getLogcatPath(), "text/plain", "forumslader-logcat.txt")
                    }

                    requestLine.startsWith("GET /telemetry.csv") -> {
                        serveFile(output, getCsvPath(), "text/csv", "telemetry.csv")
                    }

                    else -> {
                        serveNotFound(output)
                    }
                }
            }
        }
    }

    private fun serveHtml(output: OutputStream) {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Forumslader Diagnostics</title>
            </head>
            <body style="font-family:sans-serif;padding:24px;line-height:1.6;">
                <h2>Forumslader Diagnostics</h2>
                <p><a href="/logcat" download="forumslader-logcat.txt" style="display:inline-block;padding:12px 20px;background:#0066cc;color:white;text-decoration:none;border-radius:6px;">📥 Download LogCat (.txt)</a></p>
                <p><a href="/telemetry.csv" download="telemetry.csv" style="display:inline-block;padding:12px 20px;background:#008844;color:white;text-decoration:none;border-radius:6px;">📥 Download Telemetry (.csv)</a></p>
            </body>
            </html>
        """.trimIndent()
        val header =
            "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${html.toByteArray().size}\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray())
        output.write(html.toByteArray())
        output.flush()
    }

    private fun serveFile(
        output: OutputStream,
        path: Path?,
        contentType: String,
        downloadFilename: String,
    ) {
        if ((path == null) || (!path.exists())) {
            serveNotFound(output)
            return
        }

        val length = path.fileSize()
        val header =
            "HTTP/1.1 200 OK\r\nContent-Type: $contentType\r\nContent-Length: $length\r\nContent-Disposition: attachment; filename=\"$downloadFilename\"\r\nConnection: close\r\n\r\n"
        output.write(header.toByteArray())
        path.inputStream().use { input: InputStream ->
            input.copyTo(output)
        }
        output.flush()
    }

    private fun serveNotFound(output: OutputStream) {
        val response = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
        output.write(response.toByteArray())
        output.flush()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5 * 60 * 1000L

        private fun defaultIpAddressProvider(): String? = runCatching {
            var fallbackIp: String? = null
            val interfaces = NetworkInterface.getNetworkInterfaces()?.asSequence() ?: return@runCatching null

            for (netIf in interfaces) {
                if (!netIf.isUp || netIf.isLoopback) continue
                val isPreferred = netIf.name.contains("wlan", ignoreCase = true) ||
                    netIf.name.contains("ap", ignoreCase = true)

                for (address in netIf.inetAddresses.asSequence()) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        val host = address.hostAddress ?: continue
                        if (isPreferred) return@runCatching host
                        if (fallbackIp == null) fallbackIp = host
                    }
                }
            }
            fallbackIp
        }.getOrNull()
    }
}
