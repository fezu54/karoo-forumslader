package org.happycode.karoo.forumslader.model

sealed class ForumsladerVersion(
    val key: String,
    val frequencyScale: Float,
    val impulseScale: Double,
    val impulseIndex: Int
) {
    data object V5 : ForumsladerVersion(
        key = "V5",
        frequencyScale = 1.0f,
        impulseScale = 4096.0,
        impulseIndex = 13
    )

    data object V6 : ForumsladerVersion(
        key = "V6",
        frequencyScale = 0.1f,
        impulseScale = 1.0,
        impulseIndex = 12
    )

    data object Unknown : ForumsladerVersion(
        key = "UNKNOWN",
        frequencyScale = 0.1f, // Assume V6 as default for modern devices
        impulseScale = 1.0,
        impulseIndex = 12
    )

    companion object {
        fun fromKey(key: String?): ForumsladerVersion {
            return when (key) {
                V5.key -> V5
                V6.key -> V6
                else -> Unknown
            }
        }
    }
}
