package org.happycode.karoo.forumslader.application

import java.nio.file.Path

object CsvLoggerProvider {
    @Volatile
    private var instance: CsvLogger? = null

    fun getInstance(directory: Path): CsvLogger {
        return instance ?: synchronized(this) {
            instance ?: CsvLogger(directory).also { instance = it }
        }
    }
}
