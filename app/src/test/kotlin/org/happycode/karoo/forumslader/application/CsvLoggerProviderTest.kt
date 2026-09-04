package org.happycode.karoo.forumslader.application

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.io.path.createTempDirectory

class CsvLoggerProviderTest {

    @Test
    fun `should return same instance when getInstance called multiple times`() {
        //given
        val tempDir = createTempDirectory()

        //when
        val logger1 = CsvLoggerProvider.getInstance(tempDir)
        val logger2 = CsvLoggerProvider.getInstance(tempDir)

        //then
        assertNotNull(logger1)
        assertSame(logger1, logger2)
    }
}
