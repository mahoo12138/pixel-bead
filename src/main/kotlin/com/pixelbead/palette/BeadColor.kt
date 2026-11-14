package com.pixelbead.palette

import kotlinx.serialization.Serializable

/**
 * One MARD bead color. Canonical schema is generated at build time from
 * color-data/mard-291.csv (MIT, MakeBead) into assets/pixelbead/palette/mard291.json.
 */
@Serializable
data class BeadColor(
    val id: String,
    val code: String,
    val series: String,
    val hex: String,
    val rgb: List<Int>,
    val name: String,
    val discontinued: Boolean,
) {
    /** Opaque ARGB used for block/item tinting and UI swatches. */
    val argb: Int
        get() = (0xFF shl 24) or (rgb[0] shl 16) or (rgb[1] shl 8) or rgb[2]
}