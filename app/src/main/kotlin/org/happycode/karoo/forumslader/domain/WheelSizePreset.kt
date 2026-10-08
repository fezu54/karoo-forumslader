package org.happycode.karoo.forumslader.domain

data class WheelSizePreset(
    val designation: String,
    val circumferenceMm: Int,
    val category: Category
) {
    val label: String get() = "$designation (${circumferenceMm}mm)"

    enum class Category {
        SIZE_20,
        SIZE_26,
        SIZE_650B,
        SIZE_700C,
        SIZE_29
    }

    companion object {
        val ALL: List<WheelSizePreset> = listOf(
            // 20"
            WheelSizePreset("20x1.75 / 47-406", 1515, Category.SIZE_20),
            // 26"
            WheelSizePreset("26x1.50 / 40-559", 2030, Category.SIZE_26),
            WheelSizePreset("26x1.75 / 47-559", 2070, Category.SIZE_26),
            WheelSizePreset("26x2.00 / 50-559", 2089, Category.SIZE_26),
            WheelSizePreset("26x2.10 / 54-559", 2114, Category.SIZE_26),
            WheelSizePreset("26x2.25 / 57-559", 2115, Category.SIZE_26),
            // 650B / 27.5"
            WheelSizePreset("650Bx38 / 38-584", 2125, Category.SIZE_650B),
            WheelSizePreset("650Bx42 / 42-584", 2145, Category.SIZE_650B),
            WheelSizePreset("650Bx47 / 47-584", 2168, Category.SIZE_650B),
            WheelSizePreset("27.5x2.10 / 54-584", 2200, Category.SIZE_650B),
            WheelSizePreset("27.5x2.25 / 57-584", 2215, Category.SIZE_650B),
            // 700C / 28" (622 ISO)
            WheelSizePreset("700x23c / 23-622", 2096, Category.SIZE_700C),
            WheelSizePreset("700x25c / 25-622", 2105, Category.SIZE_700C),
            WheelSizePreset("700x28c / 28-622", 2136, Category.SIZE_700C),
            WheelSizePreset("700x30c / 30-622", 2146, Category.SIZE_700C),
            WheelSizePreset("700x32c / 32-622", 2155, Category.SIZE_700C),
            WheelSizePreset("700x35c / 35-622", 2168, Category.SIZE_700C),
            WheelSizePreset("700x38c / 38-622", 2180, Category.SIZE_700C),
            WheelSizePreset("700x40c / 40-622", 2200, Category.SIZE_700C),
            WheelSizePreset("700x42c / 42-622", 2230, Category.SIZE_700C),
            WheelSizePreset("700x45c / 45-622", 2250, Category.SIZE_700C),
            WheelSizePreset("700x47c / 47-622", 2260, Category.SIZE_700C),
            WheelSizePreset("700x48c / 48-622", 2268, Category.SIZE_700C),
            WheelSizePreset("700x50c / 50-622", 2280, Category.SIZE_700C),
            // 29"
            WheelSizePreset("29x2.10 / 54-622", 2288, Category.SIZE_29),
            WheelSizePreset("29x2.25 / 57-622", 2315, Category.SIZE_29),
            WheelSizePreset("29x2.35 / 60-622", 2326, Category.SIZE_29),
            WheelSizePreset("29x2.40 / 62-622", 2350, Category.SIZE_29)
        )

        fun findByCircumference(mm: Int): WheelSizePreset? =
            ALL.firstOrNull { it.circumferenceMm == mm }
    }
}
