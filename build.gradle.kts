// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ksp) apply false
}

val installGitHooks = tasks.register("installGitHooks") {
    description = "Configures git to use .githooks for commit hooks"
    group = "git"
    onlyIf { File(rootDir, ".git").exists() }
    doLast {
        val process = ProcessBuilder("git", "config", "core.hooksPath", ".githooks")
            .directory(rootDir)
            .start()
        val exitCode = process.waitFor()
        if (exitCode == 0) {
            logger.lifecycle("Configured git core.hooksPath to .githooks")
        } else {
            logger.warn("Failed to set git core.hooksPath (exit code $exitCode)")
        }
    }
}

gradle.projectsEvaluated {
    allprojects {
        tasks.matching { it.name in listOf("prepareKotlinIdeaImport", "checkCoverageBaseline", "preBuild") }.configureEach {
            dependsOn(installGitHooks)
        }
    }
}
