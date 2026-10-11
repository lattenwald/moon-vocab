package org.lattenwald.moonvocab.domain.mark

enum class ColorName { RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PURPLE, PINK, GRAY, BLACK, WHITE }

fun colorName(argb: Int): ColorName {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val saturation = if (max == 0f) 0f else delta / max
    if (saturation < 0.15f) {
        return when {
            max < 0.2f -> ColorName.BLACK
            max > 0.85f -> ColorName.WHITE
            else -> ColorName.GRAY
        }
    }
    val hue = when (max) {
        r -> 60 * ((g - b) / delta)
        g -> 60 * ((b - r) / delta + 2)
        else -> 60 * ((r - g) / delta + 4)
    }.let { if (it < 0) it + 360 else it }
    return when {
        hue >= 345 || hue < 15 -> ColorName.RED
        hue < 45 -> ColorName.ORANGE
        hue < 70 -> ColorName.YELLOW
        hue < 165 -> ColorName.GREEN
        hue < 195 -> ColorName.CYAN
        hue < 255 -> ColorName.BLUE
        hue < 290 -> ColorName.PURPLE
        else -> ColorName.PINK
    }
}
