package org.happycode.karoo.forumslader.adapters.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class FirmwareDownloader(
    private val context: Context,
    private val firmwareUrl: String = "https://www.forumslader.de/fw-flv6-latest",
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    suspend fun downloadLatest(): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = openConnection(URL(firmwareUrl))
            try {
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("HTTP error $responseCode")
                }
                val firmwareDir = context.cacheDir.resolve("firmware").apply { mkdirs() }
                val targetFile = firmwareDir.resolve("fw-flv6-latest.zip")
                connection.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Uri.fromFile(targetFile)
            } finally {
                connection.disconnect()
            }
        }
    }

    private tailrec fun openConnection(url: URL, hops: Int = 0): HttpURLConnection {
        if (hops >= 5) throw IllegalStateException("Too many redirects")
        val connection = connectionFactory(url).apply {
            instanceFollowRedirects = false
            connectTimeout = 10_000
            readTimeout = 30_000
        }
        val location = when (connection.responseCode) {
            in 300..399 -> connection.getHeaderField("Location") ?: throw IllegalStateException("Missing Location header")
            else -> return connection
        }
        connection.disconnect()
        val nextUrl = URL(url, location).let { target ->
            if (target.protocol.equals("http", ignoreCase = true)) {
                URL("https", target.host, target.file)
            } else {
                target
            }
        }
        return openConnection(nextUrl, hops + 1)
    }
}
