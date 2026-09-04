package org.happycode.karoo.forumslader.adapters.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import org.happycode.karoo.forumslader.application.PublicStorageGateway
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

class PublicStorageAdapter(
    private val context: Context? = null,
    private val targetDirectory: Path = defaultDownloadDirectory(),
) : PublicStorageGateway {

    override fun exportToPublicStorage(sourcePath: Path, destinationFileName: String): Result<Path> = runCatching {
        if (!sourcePath.exists()) {
            error("Source file does not exist: $sourcePath")
        }

        targetDirectory.createDirectories()
        val destinationPath = targetDirectory.resolve(destinationFileName)
        sourcePath.copyTo(destinationPath, StandardCopyOption.REPLACE_EXISTING)

        context?.let { ctx ->
            runCatching {
                MediaScannerConnection.scanFile(
                    ctx,
                    arrayOf(destinationPath.toString()),
                    null,
                    null
                )
            }
        }

        destinationPath
    }

    companion object {
        private fun defaultDownloadDirectory(): Path =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                .toPath()
                .resolve("forumslader")
    }
}
