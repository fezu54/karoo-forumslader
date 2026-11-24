import java.io.File
import java.nio.file.Files
import kotlin.system.exitProcess

private class TestSuiteRunner {
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

private fun withTempGitRepo(
    withLocalProperties: Boolean = true,
    withGithooks: Boolean = true,
    block: (repoDir: File, runner: ProcessCommandRunner) -> Unit
) {
    val root = Files.createTempDirectory("worktree-test-").toFile()
    val runner = ProcessCommandRunner()
    try {
        check(runner.run(root, "git", "init", "-b", "main").exitCode == 0) { "git init failed" }
        check(runner.run(root, "git", "config", "user.email", "test@example.com").exitCode == 0)
        check(runner.run(root, "git", "config", "user.name", "Test User").exitCode == 0)
        File(root, "README.md").writeText("init\n")
        if (withLocalProperties) {
            File(root, "local.properties").writeText("sdk.dir=/tmp/android-sdk\n")
        }
        if (withGithooks) {
            File(root, ".githooks").mkdirs()
            File(root, ".githooks/commit-msg").writeText("#!/bin/sh\nexit 0\n")
        }
        check(runner.run(root, "git", "add", "README.md").exitCode == 0)
        check(runner.run(root, "git", "commit", "-m", "chore: init").exitCode == 0)
        block(root, runner)
    } finally {
        root.deleteRecursively()
    }
}

private fun registerSlugTests(suite: TestSuiteRunner) {
    suite.test("`should sanitize branch name into filesystem slug when creating worktree`") {
        // given
        val branch = "feat/96-usb_auto/toggle"

        // when
        val slug = WorktreeSlug.fromBranch(branch)

        // then
        assertEquals("feat-96-usb_auto-toggle", slug)
    }
}

private fun registerCreationTests(suite: TestSuiteRunner) {
    suite.test("`should create worktree and symlink local properties when main repo is clean`") {
        // given
        withTempGitRepo(withLocalProperties = true, withGithooks = true) { repo, runner ->
            val setup = WorktreeSetup(repo, runner)

            // when
            val worktree = setup.create("feat/123-demo-feature")

            // then
            val linkPath = worktree.toPath().resolve("local.properties")
            assertTrue(worktree.isDirectory, "Worktree directory must exist")
            assertTrue(File(worktree, ".git").isFile, "Linked worktree must have .git file")
            assertTrue(Files.isSymbolicLink(linkPath), "local.properties must be symlinked")
            assertTrue(
                Files.readString(linkPath).contains("sdk.dir=/tmp/android-sdk"),
                "Symlinked local.properties must resolve to original content"
            )
            assertEquals("feat/123-demo-feature", runner.run(worktree, "git", "branch", "--show-current").output)
        }
    }

    suite.test("`should configure git hooks path when githooks directory exists`") {
        // given
        withTempGitRepo(withLocalProperties = false, withGithooks = true) { repo, runner ->
            val setup = WorktreeSetup(repo, runner)

            // when
            setup.create("fix/hook-check")

            // then
            val hooksPath = runner.run(repo, "git", "config", "--get", "core.hooksPath").output
            assertEquals(".githooks", hooksPath)
        }
    }

    suite.test("`should succeed without symlink when local properties does not exist`") {
        // given
        withTempGitRepo(withLocalProperties = false, withGithooks = false) { repo, runner ->
            val setup = WorktreeSetup(repo, runner)

            // when
            val worktree = setup.create("fix/no-local-props")

            // then
            assertTrue(worktree.isDirectory, "Worktree should be created even without local.properties")
            assertTrue(!File(worktree, "local.properties").exists(), "local.properties must not be fabricated")
        }
    }
}

private fun registerFailureTests(suite: TestSuiteRunner) {
    suite.test("`should fail when invoked inside an existing linked worktree`") {
        // given
        withTempGitRepo { repo, runner ->
            val existing = WorktreeSetup(repo, runner).create("feat/outer")
            val nestedSetup = WorktreeSetup(existing, runner)

            // when
            val outcome = runCatching { nestedSetup.create("feat/inner") }

            // then
            assertTrue(outcome.isFailure, "Must refuse nested worktree creation")
        }
    }

    suite.test("`should fail when target worktree directory already exists`") {
        // given
        withTempGitRepo { repo, runner ->
            val setup = WorktreeSetup(repo, runner)
            File(repo, ".worktrees/feat-existing").mkdirs()

            // when
            val outcome = runCatching { setup.create("feat/existing") }

            // then
            assertTrue(outcome.isFailure, "Must fail when worktree path already exists")
        }
    }
}

fun main() {
    val suite = TestSuiteRunner()

    registerSlugTests(suite)
    registerCreationTests(suite)
    registerFailureTests(suite)

    println("--------------------------------")
    println("Results: ${suite.passed} passed, ${suite.failed} failed.")
    if (suite.failed > 0) exitProcess(1)
}
