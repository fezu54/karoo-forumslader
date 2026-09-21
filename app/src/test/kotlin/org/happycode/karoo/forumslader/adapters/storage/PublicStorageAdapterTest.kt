package org.happycode.karoo.forumslader.adapters.storage

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

class PublicStorageAdapterTest : ShouldSpec({

    beforeSpec {
        mockkStatic(Environment::class)
        mockkStatic(MediaScannerConnection::class)
        every { Environment.getExternalStoragePublicDirectory(any()) } returns File("/tmp")
        every { MediaScannerConnection.scanFile(any(), any(), any(), any()) } returns Unit
    }

    afterSpec {
        unmockkStatic(Environment::class)
        unmockkStatic(MediaScannerConnection::class)
    }

    should("copy file to destination directory when file exists") {
        //given
        val context = mockk<Context>()
        val sourceDir = createTempDirectory()
        val targetDir = createTempDirectory()
        val sourceFile = sourceDir.resolve("sample.txt").apply { writeText("Hello storage") }
        val adapter = PublicStorageAdapter(context = context, targetDirectory = targetDir)

        //when
        val result = adapter.exportToPublicStorage(sourcePath = sourceFile, destinationFileName = "exported.txt")

        //then
        result.isSuccess shouldBe true
        val exportedPath = result.getOrThrow()
        exportedPath.exists() shouldBe true
        exportedPath.readText() shouldBe "Hello storage"
        exportedPath.fileName.toString() shouldBe "exported.txt"
    }

    should("fail when source file does not exist") {
        //given
        val sourceDir = createTempDirectory()
        val targetDir = createTempDirectory()
        val nonExistentFile = sourceDir.resolve("missing.txt")
        val adapter = PublicStorageAdapter(context = null, targetDirectory = targetDir)

        //when
        val result = adapter.exportToPublicStorage(sourcePath = nonExistentFile, destinationFileName = "exported.txt")

        //then
        result.isFailure shouldBe true
    }

    should("instantiate with default parameters without errors") {
        //when
        val adapter = PublicStorageAdapter()

        //then
        adapter.shouldNotBeNull()
    }
})
