import java.io.File
import java.util.concurrent.TimeUnit

fun runCommand(vararg args: String): String {
    val process = ProcessBuilder(*args)
        .redirectErrorStream(true)
        .start()
    
    // Read output before waitFor to avoid pipe buffer deadlocks
    val output = process.inputStream.bufferedReader().readText()
    process.waitFor(30, TimeUnit.SECONDS)
    return output
}

val previousTag = System.getenv("prev_tag")?.takeIf { it.isNotBlank() } ?: "v0.0.0"

val gitCommandArgs = if (previousTag == "v0.0.0") {
    arrayOf("git", "log", "--pretty=format:|||COMMIT|||%s|||BODY|||%b")
} else {
    arrayOf("git", "log", "$previousTag..HEAD", "--pretty=format:|||COMMIT|||%s|||BODY|||%b")
}

val gitLogOutput = runCommand(*gitCommandArgs)
val parsedCommits = gitLogOutput.split("|||COMMIT|||").drop(1)

val releaseNotesContent = buildString {
    appendLine("## What's new")
    appendLine()
    
    parsedCommits.forEach { commitString ->
        if (!commitString.contains("|||BODY|||")) return@forEach
        
        val (subjectRaw, bodyRaw) = commitString.split("|||BODY|||", limit = 2).let { 
            it[0] to it.getOrElse(1) { "" }
        }
        val commitSubject = subjectRaw.trim()
        val commitBody = bodyRaw.trim()
        
        if (commitSubject.contains("skip ci", ignoreCase = true)) return@forEach
        
        appendLine("* **$commitSubject**")
        commitBody.takeIf { it.isNotEmpty() }?.lines()?.forEach { line ->
            appendLine("  > $line")
        }
        appendLine()
    }
}

File("release_notes.md").writeText(releaseNotesContent)
println("Successfully generated release_notes.md")
