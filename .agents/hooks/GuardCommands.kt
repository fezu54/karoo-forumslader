import java.io.File
import java.util.concurrent.TimeUnit

class JsonParser(private val source: String) {
    private var pos = 0

    fun parse(): Any? {
        skipWhitespace()
        if (pos >= source.length) throw IllegalArgumentException("Empty JSON input")
        val value = parseValue()
        skipWhitespace()
        if (pos < source.length) throw IllegalArgumentException("Trailing JSON content at $pos")
        return value
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        if (pos >= source.length) throw IllegalArgumentException("Unexpected end of JSON")
        return when (val ch = source[pos]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> parseString()
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> parseNumber()
            else -> throw IllegalArgumentException("Unexpected character '$ch' at $pos")
        }
    }

    private fun parseObject(): Map<String, Any?> {
        pos++
        val map = LinkedHashMap<String, Any?>()
        skipWhitespace()
        if (peek() == '}') return map.also { pos++ }

        while (true) {
            skipWhitespace()
            if (peek() != '"') throw IllegalArgumentException("Expected string key at $pos")
            val key = parseString()
            skipWhitespace()
            expect(':')
            map[key] = parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                '}' -> return map.also { pos++ }
                else -> throw IllegalArgumentException("Expected ',' or '}' at $pos")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        pos++
        val list = mutableListOf<Any?>()
        skipWhitespace()
        if (peek() == ']') return list.also { pos++ }

        while (true) {
            list += parseValue()
            skipWhitespace()
            when (peek()) {
                ',' -> pos++
                ']' -> return list.also { pos++ }
                else -> throw IllegalArgumentException("Expected ',' or ']' at $pos")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val out = StringBuilder()
        while (pos < source.length) {
            when (val ch = source[pos++]) {
                '"' -> return out.toString()
                '\\' -> out.append(parseEscape())
                else -> out.append(ch)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun parseEscape(): Char {
        if (pos >= source.length) throw IllegalArgumentException("Unterminated escape")
        return when (val esc = source[pos++]) {
            '"', '\\', '/' -> esc
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> parseUnicodeEscape()
            else -> throw IllegalArgumentException("Unknown escape '\\$esc'")
        }
    }

    private fun parseUnicodeEscape(): Char {
        if (pos + 4 > source.length) throw IllegalArgumentException("Invalid unicode escape")
        val hex = source.substring(pos, pos + 4)
        pos += 4
        return hex.toInt(16).toChar()
    }

    private fun parseNumber(): Double {
        val start = pos
        while (pos < source.length && source[pos] in "0123456789+-.eE") {
            pos++
        }
        return source.substring(start, pos).toDouble()
    }

    private fun parseBoolean(): Boolean = when {
        source.startsWith("true", pos) -> true.also { pos += 4 }
        source.startsWith("false", pos) -> false.also { pos += 5 }
        else -> throw IllegalArgumentException("Invalid boolean at $pos")
    }

    private fun parseNull(): Any? {
        if (!source.startsWith("null", pos)) throw IllegalArgumentException("Invalid null at $pos")
        pos += 4
        return null
    }

    private fun expect(expected: Char) {
        if (pos >= source.length || source[pos] != expected) {
            throw IllegalArgumentException("Expected '$expected' at $pos")
        }
        pos++
    }

    private fun peek(): Char? = source.getOrNull(pos)

    private fun skipWhitespace() {
        while (pos < source.length && source[pos].isWhitespace()) pos++
    }
}

object JsonFormatter {
    fun escape(value: String): String = buildString(value.length + 8) {
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> append(ch)
            }
        }
    }
}

data class ShellSegment(
    val executable: String,
    val args: List<String>,
    val raw: String
)

private class QuoteTracker {
    var inSingle = false
        private set
    var inDouble = false
        private set

    val isQuoted: Boolean get() = inSingle || inDouble

    fun consumeIfQuote(ch: Char): Boolean = when {
        ch == '\'' && !inDouble -> true.also { inSingle = !inSingle }
        ch == '"' && !inSingle -> true.also { inDouble = !inDouble }
        else -> false
    }
}

object ShellCommandParser {
    private val ENV_ASSIGNMENT = Regex("^[A-Za-z_][A-Za-z0-9_]*=.*$")
    private val SHELL_WRAPPERS = setOf("sh", "bash", "zsh")
    private val SINGLE_SEPARATORS = charArrayOf(';', '|', '\n')

    fun parseSegments(command: String): List<ShellSegment> =
        splitRawSegments(command).flatMap(::buildSegmentsFromRaw)

    private fun buildSegmentsFromRaw(raw: String): List<ShellSegment> {
        val stripped = stripEnvPrefixes(tokenize(raw))
        if (stripped.isEmpty()) return emptyList()

        val executable = stripped.first()
        val args = stripped.drop(1)
        val current = ShellSegment(executable, args, raw)

        val inlineScript = if (File(executable).name in SHELL_WRAPPERS) {
            extractInlineShellScript(args)
        } else {
            null
        }
        return inlineScript?.let { listOf(current) + parseSegments(it) } ?: listOf(current)
    }

    private fun extractInlineShellScript(args: List<String>): String? =
        args.indices.firstNotNullOfOrNull { idx ->
            args.getOrNull(idx + 1)?.takeIf { args[idx] in setOf("-c", "-lc", "-ec") }
        }

    private fun stripEnvPrefixes(tokens: List<String>): List<String> {
        val firstCommandIndex = tokens.indexOfFirst { token ->
            !ENV_ASSIGNMENT.matches(token) && token != "env"
        }
        return if (firstCommandIndex == -1) emptyList() else tokens.subList(firstCommandIndex, tokens.size)
    }

    private fun splitRawSegments(command: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        val quotes = QuoteTracker()
        var idx = 0

        while (idx < command.length) {
            idx += advanceSplitStep(command, idx, quotes, current, result)
        }
        flushSegment(current, result)
        return result
    }

    private fun advanceSplitStep(
        command: String,
        idx: Int,
        quotes: QuoteTracker,
        current: StringBuilder,
        result: MutableList<String>
    ): Int {
        val ch = command[idx]
        val next = command.getOrNull(idx + 1)
        return when {
            ch == '\\' && !quotes.inSingle && next != null -> 2.also { current.append(ch).append(next) }
            quotes.consumeIfQuote(ch) -> 1.also { current.append(ch) }
            !quotes.isQuoted && isDoubleOperator(ch, next) -> 2.also { flushSegment(current, result) }
            !quotes.isQuoted && ch in SINGLE_SEPARATORS -> 1.also { flushSegment(current, result) }
            else -> 1.also { current.append(ch) }
        }
    }

    private fun isDoubleOperator(first: Char, second: Char?): Boolean =
        (first == '&' && second == '&') || (first == '|' && second == '|')

    private fun flushSegment(buffer: StringBuilder, target: MutableList<String>) {
        val trimmed = buffer.toString().trim()
        if (trimmed.isNotEmpty()) target += trimmed
        buffer.setLength(0)
    }

    private fun tokenize(segment: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        val quotes = QuoteTracker()
        var hasToken = false
        var idx = 0

        while (idx < segment.length) {
            val ch = segment[idx]
            val next = segment.getOrNull(idx + 1)
            when {
                ch == '#' && !quotes.isQuoted && !hasToken -> return tokens
                ch == '\\' && !quotes.inSingle && next != null -> {
                    current.append(next)
                    hasToken = true
                    idx += 2
                }
                quotes.consumeIfQuote(ch) -> {
                    hasToken = true
                    idx++
                }
                ch.isWhitespace() && !quotes.isQuoted -> {
                    if (hasToken) {
                        tokens += current.toString()
                        current.setLength(0)
                        hasToken = false
                    }
                    idx++
                }
                else -> {
                    current.append(ch)
                    hasToken = true
                    idx++
                }
            }
        }
        if (hasToken) tokens += current.toString()
        return tokens
    }
}

interface GitWorkspaceInspector {
    fun hasRelevantChanges(directory: File): Boolean
}

class ProcessGitWorkspaceInspector : GitWorkspaceInspector {
    override fun hasRelevantChanges(directory: File): Boolean {
        if (!directory.isDirectory) return true

        val status = runGit(directory, "status", "--porcelain", "--", *WATCHED_PATHS)
        if (status.exitCode != 0 || status.output.isNotEmpty()) return true

        val branch = runGit(directory, "rev-parse", "--abbrev-ref", "HEAD")
        if (branch.exitCode != 0) return true
        if (branch.output == "main") return false

        val baseRef = resolveBaseRef(directory) ?: return true
        val diff = runGit(directory, "diff", "--quiet", "$baseRef...HEAD", "--", *WATCHED_PATHS)
        return diff.exitCode != 0
    }

    private fun resolveBaseRef(directory: File): String? = listOf("main", "origin/main").firstOrNull { ref ->
        runGit(directory, "rev-parse", "--verify", "--quiet", ref).exitCode == 0
    }

    private fun runGit(directory: File, vararg args: String): GitResult {
        val process = ProcessBuilder("git", "-C", directory.absolutePath, *args)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trim()
        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return GitResult(exitCode = -1, output = "")
        }
        return GitResult(exitCode = process.exitValue(), output = output)
    }

    private data class GitResult(val exitCode: Int, val output: String)

    companion object {
        private val WATCHED_PATHS = arrayOf("app", "build.gradle.kts", "settings.gradle.kts", "gradle")
    }
}

sealed interface GuardDecision {
    fun toJson(): String

    data object Allow : GuardDecision {
        override fun toJson(): String = """{"decision":"allow"}"""
    }

    data object Ask : GuardDecision {
        override fun toJson(): String = """{"decision":"ask"}"""
    }

    data class Deny(val reason: String) : GuardDecision {
        override fun toJson(): String =
            """{"decision":"deny","reason":"${JsonFormatter.escape(reason)}"}"""
    }

    data class ForceAsk(val reason: String) : GuardDecision {
        override fun toJson(): String =
            """{"decision":"force_ask","reason":"${JsonFormatter.escape(reason)}"}"""
    }
}

class CommandGuard(private val gitInspector: GitWorkspaceInspector) {
    fun evaluate(commandLine: String, workingDir: File): GuardDecision {
        val segments = ShellCommandParser.parseSegments(commandLine)
        var currentDir = workingDir

        for (segment in segments) {
            if (File(segment.executable).name == "cd") {
                resolveCdDirectory(currentDir, segment.args)?.let { currentDir = it }
                continue
            }
            if (isArchiveUnpack(segment)) return GuardDecision.Deny(ARCHIVE_DENY_REASON)
            if (isDisallowedPreEditGradle(segment, currentDir)) return GuardDecision.ForceAsk(GRADLE_ASK_REASON)
        }
        return GuardDecision.Allow
    }

    private fun resolveCdDirectory(baseDir: File, args: List<String>): File? {
        val target = args.firstOrNull { !it.startsWith("-") } ?: return null
        val candidate = File(target)
        return if (candidate.isAbsolute) candidate else File(baseDir, target).canonicalFile
    }

    private fun isArchiveUnpack(segment: ShellSegment): Boolean {
        val execName = File(segment.executable).name.lowercase()
        val joinedArgs = segment.args.joinToString(" ").lowercase()
        if (ARCHIVE_TARGETS.none { joinedArgs.contains(it) }) return false

        return when (execName) {
            in ARCHIVE_EXECUTABLES -> true
            in PYTHON_EXECUTABLES -> joinedArgs.contains("zipfile")
            else -> false
        }
    }

    private fun isDisallowedPreEditGradle(segment: ShellSegment, currentDir: File): Boolean {
        val execName = File(segment.executable).name.lowercase()
        if (execName !in GRADLE_EXECUTABLES) return false

        val hasBuildOrTestTask = extractGradleTasks(segment.args).any { task ->
            val simpleName = task.substringAfterLast(':').lowercase()
            simpleName !in HOUSEKEEPING_TASKS && BUILD_TASK_PREFIXES.any { simpleName.startsWith(it) }
        }
        if (!hasBuildOrTestTask) return false

        return !gitInspector.hasRelevantChanges(resolveGradleProjectDir(currentDir, segment.args))
    }

    private fun resolveGradleProjectDir(currentDir: File, args: List<String>): File {
        args.forEachIndexed { idx, arg ->
            val rawDir = when {
                (arg == "-p" || arg == "--project-dir") && idx + 1 < args.size -> args[idx + 1]
                arg.startsWith("--project-dir=") -> arg.removePrefix("--project-dir=")
                else -> null
            }
            if (rawDir != null) {
                val path = File(rawDir)
                return if (path.isAbsolute) path else File(currentDir, rawDir).canonicalFile
            }
        }
        return currentDir
    }

    private fun extractGradleTasks(args: List<String>): List<String> {
        val tasks = mutableListOf<String>()
        var idx = 0
        while (idx < args.size) {
            val arg = args[idx]
            when {
                arg in GRADLE_OPTIONS_WITH_VALUE -> idx += 2
                arg.startsWith("-") -> idx++
                else -> {
                    tasks += arg
                    idx++
                }
            }
        }
        return tasks
    }

    companion object {
        private val ARCHIVE_EXECUTABLES = setOf("unzip", "jar", "javap", "zipinfo", "bsdtar", "7z", "7za")
        private val PYTHON_EXECUTABLES = setOf("python", "python3")
        private val ARCHIVE_TARGETS = listOf(".aar", ".jar", ".gradle/caches", "karoo-ext", "kable")
        private val GRADLE_EXECUTABLES = setOf("gradlew", "gradle")
        private val BUILD_TASK_PREFIXES = listOf(
            "build", "assemble", "test", "check", "kover", "compile", "lint", "connected", "generatefieldscreenshots"
        )
        private val HOUSEKEEPING_TASKS = setOf(
            "installgithooks", "tasks", "projects", "dependencies", "wrapper", "help", "properties"
        )
        private val GRADLE_OPTIONS_WITH_VALUE = setOf(
            "-p", "--project-dir", "-b", "--build-file", "-c", "--settings-file",
            "-I", "--init-script", "-g", "--gradle-user-home", "-x", "--exclude-task"
        )

        private const val ARCHIVE_DENY_REASON =
            "Do not unpack or inspect .aar/.jar dependency archives (token-saving rule). " +
                "Read .agents/skills/dev-implement/references/karoo-ext-api.md and existing " +
                "usages in app/src/main/kotlin/.../extension/ instead."

        private const val GRADLE_ASK_REASON =
            "Skipping pre-edit Gradle build/test runs saves tokens. No uncommitted changes or " +
                "branch commits were detected under app/ or Gradle configuration. Approve only if " +
                "you explicitly asked to run Gradle on a clean workspace."
    }
}

internal fun evaluateHookPayload(stdinText: String, guard: CommandGuard): GuardDecision {
    val root = JsonParser(stdinText).parse() as? Map<*, *> ?: return GuardDecision.Allow
    val toolCall = root["toolCall"] as? Map<*, *> ?: return GuardDecision.Allow
    val args = toolCall["args"] as? Map<*, *> ?: return GuardDecision.Allow
    val commandLine = args["CommandLine"] as? String ?: return GuardDecision.Allow
    val bypassSandbox = args["BypassSandbox"] as? Boolean ?: false

    val cwdString = (root["cwd"] as? String)?.takeIf { it.isNotBlank() }
        ?: ((root["workspacePaths"] as? List<*>)?.firstOrNull() as? String)?.takeIf { it.isNotBlank() }
        ?: "."

    val decision = guard.evaluate(commandLine, File(cwdString))
    return if (decision is GuardDecision.Allow && bypassSandbox) GuardDecision.Ask else decision
}

fun main() {
    val decision = runCatching {
        val stdinText = System.`in`.bufferedReader().readText()
        evaluateHookPayload(stdinText, CommandGuard(ProcessGitWorkspaceInspector()))
    }.getOrElse { GuardDecision.Allow }

    println(decision.toJson())
}
