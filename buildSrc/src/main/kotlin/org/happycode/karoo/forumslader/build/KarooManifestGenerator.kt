package org.happycode.karoo.forumslader.build

import java.util.Locale

data class KarooAppManifestSpec(
    val label: String,
    val packageName: String,
    val latestApkUrl: String,
    val latestVersion: String,
    val latestVersionCode: Int,
    val iconUrl: String? = null,
    val developer: String? = null,
    val description: String? = null,
    val releaseNotes: String? = null,
    val screenshotUrls: List<String> = emptyList(),
    val tags: List<String> = emptyList()
)

object KarooManifestVersionNormalizer {
    fun normalize(versionName: String): String = versionName.trim()
        .let { trimmed -> if (trimmed.startsWith("v", ignoreCase = true)) trimmed.drop(1) else trimmed }
        .trim()
        .also { normalized -> require(normalized.isNotEmpty()) { "versionName must not be blank" } }
}

object ReleaseNotesFormatter {
    private val BLOCKQUOTE_PREFIX = Regex("""^(\s*)>\s?""")

    fun format(
        markdown: String?,
        fallback: String = "Bug fixes and improvements."
    ): String {
        if (markdown.isNullOrBlank()) return fallback

        return markdown.lines()
            .asSequence()
            .filterNot { line -> line.isBlank() || line.trimStart().startsWith("#") }
            .map { line ->
                line.replace(BLOCKQUOTE_PREFIX, "$1")
                    .replace("**", "")
                    .trimEnd()
            }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .takeIf { it.isNotBlank() }
            ?: fallback
    }
}

object KarooManifestJsonSerializer {
    fun toJson(spec: KarooAppManifestSpec): String {
        require(spec.label.isNotBlank()) { "label must not be blank" }
        require(spec.packageName.isNotBlank()) { "packageName must not be blank" }
        require(spec.latestApkUrl.isNotBlank()) { "latestApkUrl must not be blank" }
        require(spec.latestVersionCode > 0) { "latestVersionCode must be positive" }

        val normalizedVersion = KarooManifestVersionNormalizer.normalize(spec.latestVersion)
        val entries = buildList {
            add(stringEntry("label", spec.label))
            add(stringEntry("packageName", spec.packageName))
            add(stringEntry("latestApkUrl", spec.latestApkUrl))
            add(stringEntry("latestVersion", normalizedVersion))
            add("\"latestVersionCode\": ${spec.latestVersionCode}")
            spec.iconUrl?.takeIf { it.isNotBlank() }?.let { add(stringEntry("iconUrl", it)) }
            spec.developer?.takeIf { it.isNotBlank() }?.let { add(stringEntry("developer", it)) }
            spec.description?.takeIf { it.isNotBlank() }?.let { add(stringEntry("description", it)) }
            spec.releaseNotes?.takeIf { it.isNotBlank() }?.let { add(stringEntry("releaseNotes", it)) }
            spec.screenshotUrls.takeIf { it.isNotEmpty() }?.let { add(stringArrayEntry("screenshotUrls", it)) }
            spec.tags.takeIf { it.isNotEmpty() }?.let { add(stringArrayEntry("tags", it)) }
        }

        return entries.joinToString(separator = ",\n  ", prefix = "{\n  ", postfix = "\n}\n")
    }

    private fun stringEntry(key: String, value: String): String =
        "\"${escapeJson(key)}\": \"${escapeJson(value)}\""

    private fun stringArrayEntry(key: String, values: List<String>): String =
        values.joinToString(
            separator = ",\n    ",
            prefix = "\"${escapeJson(key)}\": [\n    ",
            postfix = "\n  ]"
        ) { item -> "\"${escapeJson(item)}\"" }

    private fun escapeJson(value: String): String = buildString(value.length) {
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (ch.code < 0x20) {
                    append("\\u%04x".format(Locale.US, ch.code))
                } else {
                    append(ch)
                }
            }
        }
    }
}
