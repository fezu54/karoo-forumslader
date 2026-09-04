package org.happycode.karoo.forumslader.domain

object LogSanitizer {
    private val MAC_ADDRESS_REGEX = Regex(
        """(?i)(?<![:\-0-9a-f])([0-9a-f]{2})([:\-])([0-9a-f]{2}\2){4}([0-9a-f]{2})(?![:\-0-9a-f])"""
    )

    fun sanitize(text: String): String =
        MAC_ADDRESS_REGEX.replace(text) { matchResult ->
            val delimiter = matchResult.groupValues[2]
            val prefix = matchResult.value.substring(0, 8)
            "$prefix$delimiter**$delimiter**$delimiter**"
        }
}
