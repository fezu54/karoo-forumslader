package org.happycode.karoo.forumslader.application

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.types.shouldBeSameInstanceAs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class CsvLoggerProviderTest : ShouldSpec({

    lateinit var tempDir: Path

    beforeTest {
        tempDir = createTempDirectory()
    }

    afterTest {
        tempDir.toFile().deleteRecursively()
    }

    should("return same instance when getInstance is called multiple times") {
        // given / when
        val logger1 = CsvLoggerProvider.getInstance(tempDir)
        val logger2 = CsvLoggerProvider.getInstance(tempDir)

        // then
        logger1 shouldBeSameInstanceAs logger2
    }

    should("return same instance when getInstance is called concurrently") {
        // when
        val loggers = (1..100).map {
            async(Dispatchers.IO) {
                CsvLoggerProvider.getInstance(tempDir)
            }
        }.awaitAll()

        // then
        val first = loggers.first()
        loggers.forEach { logger ->
            logger shouldBeSameInstanceAs first
        }
    }
})
