import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

data class CommandResult(
    val exitCode: Int,
    val output: String
)

interface CommandRunner {
    fun run(workingDir: File, vararg args: String): CommandResult
}

class ProcessCommandRunner : CommandRunner {
    override fun run(workingDir: File, vararg args: String): CommandResult {
        val process = ProcessBuilder(*args)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return CommandResult(exitCode = -1, output = "Command timed out: ${args.joinToString(" ")}")
        }
        return CommandResult(exitCode = process.exitValue(), output = output.trim())
    }
}

object WorktreeSlug {
    private val INVALID_CHARS = Regex("[^a-zA-Z0-9._-]+")
    private val REPEATED_DASHES = Regex("-{2,}")

    fun fromBranch(branchName: String): String {
        val trimmed = branchName.trim()
        require(trimmed.isNotEmpty()) { "Branch name must not be blank." }
        require(!trimmed.contains("..")) { "Branch name must not contain '..': $branchName" }

        val sanitized = INVALID_CHARS.replace(trimmed, "-")
            .let { REPEATED_DASHES.replace(it, "-") }
            .trim('-', '.')

        require(sanitized.isNotEmpty() && sanitized != "." && sanitized != "..") {
            "Branch name produces an invalid worktree slug: $branchName"
        }
        return sanitized
    }
}

class WorktreeSetup(
    private val repoRoot: File,
    private val runner: CommandRunner = ProcessCommandRunner()
) {
    fun create(branchName: String, baseRef: String = "main"): File {
        val canonicalRoot = repoRoot.canonicalFile
        validateRepositoryRoot(canonicalRoot)

        val slug = WorktreeSlug.fromBranch(branchName)
        val worktreeDir = File(canonicalRoot, ".worktrees/$slug")
        if (worktreeDir.exists()) {
            throw IllegalStateException("Worktree directory already exists: ${worktreeDir.absolutePath}")
        }

        worktreeDir.parentFile?.mkdirs()
        val addResult = runner.run(
            canonicalRoot,
            "git", "worktree", "add", "-b", branchName, worktreeDir.absolutePath, baseRef
        )
        if (addResult.exitCode != 0) {
            throw IllegalStateException("git worktree add failed: ${addResult.output}")
        }

        symlinkLocalProperties(canonicalRoot, worktreeDir)
        ensureGitHooksConfigured(canonicalRoot)
        return worktreeDir
    }

    private fun validateRepositoryRoot(root: File) {
        val gitMarker = File(root, ".git")
        when {
            gitMarker.isFile -> throw IllegalStateException(
                "Already inside a linked git worktree: ${root.absolutePath}"
            )
            !gitMarker.exists() -> throw IllegalStateException(
                "Not a git repository root: ${root.absolutePath}"
            )
        }
    }

    private fun symlinkLocalProperties(repoRoot: File, worktreeDir: File) {
        val sourceProps = File(repoRoot, "local.properties")
        if (!sourceProps.exists()) return

        val linkPath = worktreeDir.canonicalFile.toPath().resolve("local.properties")
        if (Files.exists(linkPath)) return

        val relativeTarget = worktreeDir.canonicalFile.toPath()
            .relativize(sourceProps.canonicalFile.toPath())
        Files.createSymbolicLink(linkPath, relativeTarget)
    }

    private fun ensureGitHooksConfigured(repoRoot: File) {
        if (!File(repoRoot, ".githooks").isDirectory) return
        val current = runner.run(repoRoot, "git", "config", "--get", "core.hooksPath")
        if (current.exitCode == 0 && current.output == ".githooks") return
        runner.run(repoRoot, "git", "config", "core.hooksPath", ".githooks")
    }
}

private data class CliOptions(
    val branchName: String,
    val baseRef: String,
    val repoRoot: File
)

private fun parseCliOptions(args: Array<String>): CliOptions {
    var repoRoot: File? = null
    val positional = mutableListOf<String>()
    var idx = 0
    while (idx < args.size) {
        when (args[idx]) {
            "--repo-root" -> {
                idx++
                require(idx < args.size) { "Missing path after --repo-root" }
                repoRoot = File(args[idx])
            }
            else -> positional += args[idx]
        }
        idx++
    }

    require(positional.size in 1..2) {
        "Usage: setup-worktree.sh <branch-name> [base-ref] [--repo-root <dir>]"
    }
    val resolvedRoot = repoRoot ?: findGitRoot(File(".").canonicalFile)
    return CliOptions(
        branchName = positional[0],
        baseRef = positional.getOrElse(1) { "main" },
        repoRoot = resolvedRoot
    )
}

private fun findGitRoot(startDir: File): File {
    var current: File? = startDir
    while (current != null) {
        if (File(current, ".git").exists()) return current
        current = current.parentFile
    }
    return startDir
}

fun main(args: Array<String>) {
    runCatching {
        val options = parseCliOptions(args)
        val created = WorktreeSetup(options.repoRoot).create(
            branchName = options.branchName,
            baseRef = options.baseRef
        )
        println(created.absolutePath)
    }.onFailure { error ->
        System.err.println(error.message ?: "Failed to create worktree.")
        exitProcess(1)
    }
}
