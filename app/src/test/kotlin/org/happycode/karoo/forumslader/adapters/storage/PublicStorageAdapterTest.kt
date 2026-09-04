package org.happycode.karoo.forumslader.adapters.storage

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PublicStorageAdapterTest {

    @Test
    fun `should copy file to destination directory when file exists`() {
        //given
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sourceDir = createTempDirectory()
        val targetDir = createTempDirectory()
        val sourceFile = sourceDir.resolve("sample.txt").apply { writeText("Hello storage") }
        val adapter = PublicStorageAdapter(context = context, targetDirectory = targetDir)

        //when
        val result = adapter.exportToPublicStorage(sourceFile, "exported.txt")

        //then
        assertTrue(result.isSuccess)
        val exportedPath = result.getOrThrow()
        assertTrue(exportedPath.exists())
        assertEquals("Hello storage", exportedPath.readText())
        assertEquals("exported.txt", exportedPath.fileName.toString())
    }

    @Test
    fun `should fail when source file does not exist`() {
        //given
        val sourceDir = createTempDirectory()
        val targetDir = createTempDirectory()
        val nonExistentFile = sourceDir.resolve("missing.txt")
        val adapter = PublicStorageAdapter(context = null, targetDirectory = targetDir)

        //when
        val result = adapter.exportToPublicStorage(nonExistentFile, "exported.txt")

        //then
        assertTrue(result.isFailure)
    }

    @Test
    fun `should instantiate with default parameters without errors`() {
        //when
        val adapter = PublicStorageAdapter()

        //then
        org.junit.Assert.assertNotNull(adapter)
    }
}
