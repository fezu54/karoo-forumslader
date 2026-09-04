package org.happycode.karoo.forumslader.application

import org.happycode.karoo.forumslader.domain.LogSanitizer
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.writeLines

class LogcatDumper(
    directory: Path,
    private val logcatSource: (Int?) -> Sequence<String> = { defaultLogcatReader(it) },
) {
    private val activeLogcatPath = directory.resolve(FILE_NAME)

    init {
        directory.createDirectories()
    }

    fun dumpLogcat(targetPid: Int? = null): Path {
        val pidRegex = targetPid?.let { Regex("""\(\s*$it\)""") }

        val sanitizedLines = logcatSource(targetPid)
            .filter { line ->
                when {
                    (pidRegex != null) && pidRegex.containsMatchIn(line) -> true
                    line.contains("Forumslader") || line.contains("FL_") -> true
                    targetPid == null -> true
                    else -> false
                }
            }
            .map { LogSanitizer.sanitize(it) }
            .toList()

        activeLogcatPath.writeLines(sanitizedLines)
        return activeLogcatPath
    }

    fun getLogcatPath(): Path? = if (activeLogcatPath.exists()) activeLogcatPath else null

    fun clear() {
        activeLogcatPath.deleteIfExists()
    }

    companion object {
        const val FILE_NAME = "forumslader-logcat.txt"

        private fun defaultLogcatReader(targetPid: Int? = null): Sequence<String> = runCatching {
            val cmd = if (targetPid != null) {
                arrayOf("logcat", "-d", "-v", "time", "--pid=$targetPid")
            } else {
                arrayOf("logcat", "-d", "-v", "time")
            }
            val process = Runtime.getRuntime().exec(cmd)
            try {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.toList().asSequence()
                }
            } finally {
                process.destroy()
            }
        }.getOrDefault(emptySequence())
    }
}
