import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

private class HookTestRunner {
    var passed = 0
        private set
    var failed = 0
        private set

    fun test(name: String, block: () -> Unit) {
        runCatching(block)
            .onSuccess {
                println("PASS: $name")
                passed++
            }
            .onFailure { error ->
                println("FAIL: $name -> ${error.message}")
                failed++
            }
    }
}

private fun assertTrue(condition: Boolean, message: String) {
    if (!condition) throw AssertionError(message)
}

private fun assertEquals(expected: Any?, actual: Any?, message: String = "Values differ") {
    if (expected != actual) throw AssertionError("$message: expected <$expected> but was <$actual>")
}

private fun runGit(dir: File, vararg args: String) {
    val process = ProcessBuilder("git", "-C", dir.absolutePath, *args)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor(10, TimeUnit.SECONDS)
    check(process.exitValue() == 0) { "git ${args.joinToString(" ")} failed: $output" }
}

private fun withTempGitRepo(block: (repoDir: File) -> Unit) {
    val root = Files.createTempDirectory("guard-test-").toFile()
    try {
        runGit(root, "init", "-b", "main")
        runGit(root, "config", "user.email", "test@example.com")
        runGit(root, "config", "user.name", "Test User")
        File(root, "app/src/main/kotlin").mkdirs()
        File(root, "app/src/main/kotlin/Sample.kt").writeText("class Sample\n")
        File(root, "build.gradle.kts").writeText("// root build\n")
        runGit(root, "add", ".")
        runGit(root, "commit", "-m", "chore: init")
        block(root)
    } finally {
        root.deleteRecursively()
    }
}

private fun hookJson(commandLine: String, cwd: String, bypassSandbox: Boolean = false): String =
    """{"toolCall":{"id":"t1","name":"run_command","args":{"CommandLine":"${JsonFormatter.escape(commandLine)}","BypassSandbox":$bypassSandbox}},"cwd":"${JsonFormatter.escape(cwd)}"}"""

private fun registerArchiveTests(suite: HookTestRunner, guard: CommandGuard) {
    suite.test("`should deny unzipping karoo-ext aar when targeting gradle cache`") {
        // given
        val cmd = "unzip -p ~/.gradle/caches/modules-2/files-2.1/io.hammerhead/karoo-ext/1.1.9/karoo-ext-1.1.9.aar classes.jar"

        // when
        val decision = guard.evaluate(cmd, File("."))

        // then
        assertTrue(decision is GuardDecision.Deny, "Expected Deny, got $decision")
    }

    suite.test("`should deny jar or javap inspection when targeting dependency jar`") {
        // given
        val jarCmd = "jar tf /tmp/karoo-ext-classes.jar"
        val javapCmd = "javap -cp /tmp/classes.jar io.hammerhead.karooext.KarooSystemService"

        // when / then
        assertTrue(guard.evaluate(jarCmd, File(".")) is GuardDecision.Deny, "Expected Deny for jar tf")
        assertTrue(guard.evaluate(javapCmd, File(".")) is GuardDecision.Deny, "Expected Deny for javap")
    }

    suite.test("`should deny python zipfile inspection when targeting karoo-ext archive`") {
        // given
        val cmd = """python3 -c "import zipfile; print(zipfile.ZipFile('karoo-ext-1.1.9.aar').namelist())""""

        // when
        val decision = guard.evaluate(cmd, File("."))

        // then
        assertTrue(decision is GuardDecision.Deny, "Expected Deny for python zipfile, got $decision")
    }

    suite.test("`should deny archive unpack when chained inside compound command`") {
        // given
        val cmd = "ls -la && unzip foo.aar -d /tmp/out"

        // when / then
        assertTrue(guard.evaluate(cmd, File(".")) is GuardDecision.Deny, "Expected Deny in compound command")
    }

    suite.test("`should allow non-unpack commands when mentioning archive keywords`") {
        // given
        val commands = listOf(
            """git commit -m "chore: stop unzipping karoo-ext.aar"""",
            """grep -rn "karoo-ext" app/""",
            """echo "unzip karoo-ext.aar""""
        )

        // when / then
        commands.forEach { cmd ->
            assertEquals(GuardDecision.Allow, guard.evaluate(cmd, File(".")), "False positive for: $cmd")
        }
    }
}

private fun registerGradleTests(suite: HookTestRunner, guard: CommandGuard) {
    suite.test("`should require confirmation for gradle builds when repo has no relevant edits on main`") {
        // given
        withTempGitRepo { repo ->
            val commands = listOf(
                "./gradlew build",
                "./gradlew testDebugUnitTest",
                "./gradlew :app:checkCoverageBaseline",
                "FOO=1 ./gradlew assembleDebug"
            )

            // when / then
            commands.forEach { cmd ->
                assertTrue(guard.evaluate(cmd, repo) is GuardDecision.ForceAsk, "Expected ForceAsk for clean repo: $cmd")
            }
        }
    }

    suite.test("`should require confirmation for gradle builds when on new branch with zero edits`") {
        // given
        withTempGitRepo { repo ->
            runGit(repo, "checkout", "-b", "feat/empty")

            // when
            val decision = guard.evaluate("./gradlew :app:testDebugUnitTest", repo)

            // then
            assertTrue(decision is GuardDecision.ForceAsk, "Expected ForceAsk on empty feature branch")
        }
    }

    suite.test("`should allow gradle test when app source has uncommitted modification`") {
        // given
        withTempGitRepo { repo ->
            File(repo, "app/src/main/kotlin/Sample.kt").writeText("class Sample(val x: Int)\n")

            // when
            val decision = guard.evaluate("./gradlew :app:testDebugUnitTest --tests SampleTest", repo)

            // then
            assertEquals(GuardDecision.Allow, decision)
        }
    }

    suite.test("`should allow gradle test when new untracked test file exists under app`") {
        // given
        withTempGitRepo { repo ->
            File(repo, "app/src/test/kotlin").mkdirs()
            File(repo, "app/src/test/kotlin/SampleTest.kt").writeText("class SampleTest\n")

            // when
            val decision = guard.evaluate("./gradlew :app:testDebugUnitTest --tests SampleTest", repo)

            // then
            assertEquals(GuardDecision.Allow, decision)
        }
    }

    suite.test("`should allow gradle check when branch has committed changes ahead of main`") {
        // given
        withTempGitRepo { repo ->
            runGit(repo, "checkout", "-b", "feat/committed")
            File(repo, "app/src/main/kotlin/Sample.kt").writeText("class SampleChanged\n")
            runGit(repo, "add", ".")
            runGit(repo, "commit", "-m", "feat: update sample")

            // when
            val decision = guard.evaluate("./gradlew :app:checkCoverageBaseline", repo)

            // then
            assertEquals(GuardDecision.Allow, decision)
        }
    }

    suite.test("`should track cd into worktree when evaluating gradle commands`") {
        // given
        withTempGitRepo { repo ->
            val worktree = File(repo, ".worktrees/feat-wt")
            File(repo, ".worktrees").mkdirs()
            runGit(repo, "worktree", "add", "-b", "feat/wt", worktree.absolutePath, "main")
            File(worktree, "app/src/main/kotlin/Sample.kt").writeText("class SampleEditedInWorktree\n")

            // when
            val decision = guard.evaluate(
                "cd .worktrees/feat-wt && ./gradlew :app:testDebugUnitTest --tests FooTest",
                repo
            )

            // then
            assertEquals(GuardDecision.Allow, decision)
        }
    }

    suite.test("`should allow gradle housekeeping commands when repo is clean`") {
        // given
        withTempGitRepo { repo ->
            val commands = listOf(
                "./gradlew installGitHooks",
                "./gradlew --version",
                "./gradlew --stop",
                "./gradlew tasks"
            )

            // when / then
            commands.forEach { cmd ->
                assertEquals(GuardDecision.Allow, guard.evaluate(cmd, repo), "Housekeeping failed: $cmd")
            }
        }
    }
}

private fun registerPayloadTests(suite: HookTestRunner, guard: CommandGuard) {
    suite.test("`should fail open to allow when hook json payload is malformed empty or missing fields`") {
        // given
        val invalidInputs = listOf(
            "",
            "{not valid json",
            """{"toolCall":{"name":"read_file","args":{"Path":"foo"}}}"""
        )

        // when / then
        invalidInputs.forEach { raw ->
            val result = runCatching { evaluateHookPayload(raw, guard) }.getOrElse { GuardDecision.Allow }
            assertEquals(GuardDecision.Allow, result)
        }
    }

    suite.test("`should parse escaped quotes inside json payload when running guard`") {
        // given
        val payload = hookJson("""sh -c "unzip karoo-ext.aar"""", ".")

        // when
        val decision = evaluateHookPayload(payload, guard)

        // then
        assertTrue(decision is GuardDecision.Deny, "Expected Deny for nested sh -c unzip, got $decision")
    }

    suite.test("`should return ask when bypass sandbox is true for unflagged command`") {
        // given
        val payload = hookJson("git status", ".", bypassSandbox = true)

        // when
        val decision = evaluateHookPayload(payload, guard)

        // then
        assertEquals(GuardDecision.Ask, decision)
    }

    suite.test("`should still deny archive unpack when bypass sandbox is true`") {
        // given
        val payload = hookJson("unzip karoo-ext.aar", ".", bypassSandbox = true)

        // when
        val decision = evaluateHookPayload(payload, guard)

        // then
        assertTrue(decision is GuardDecision.Deny, "Deny must take precedence over BypassSandbox, got $decision")
    }

    suite.test("`should serialize allow and ask decisions with required decision field`") {
        // when / then
        assertEquals("""{"decision":"allow"}""", GuardDecision.Allow.toJson())
        assertEquals("""{"decision":"ask"}""", GuardDecision.Ask.toJson())
    }
}

fun main() {
    val suite = HookTestRunner()
    val guard = CommandGuard(ProcessGitWorkspaceInspector())

    registerArchiveTests(suite, guard)
    registerGradleTests(suite, guard)
    registerPayloadTests(suite, guard)

    println("--------------------------------")
    println("Results: ${suite.passed} passed, ${suite.failed} failed.")
    if (suite.failed > 0) exitProcess(1)
}
