package org.happycode.karoo.forumslader.domain

data class DynamoPolePreset(
    val name: String,
    val poles: Int,
) {
    val label: String get() = "$name ($poles pairs)"

    companion object {
        val ALL: List<DynamoPolePreset> = listOf(
            DynamoPolePreset("SON 28 / SON deluxe", 13),
            DynamoPolePreset("SON 29s", 20),
            DynamoPolePreset("Shutter Precision (SP)", 14),
            DynamoPolePreset("Shimano / SRAM / Kasai", 14),
            DynamoPolePreset("Velological Rim Dynamo", 12),
        )
    }
}
