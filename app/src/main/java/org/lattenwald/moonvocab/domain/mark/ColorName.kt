package org.lattenwald.moonvocab.domain.mark

enum class ColorName { RED, ORANGE, YELLOW, GREEN, CYAN, BLUE, PURPLE, PINK, GRAY, BLACK, WHITE }

enum class Shade { DARK, LIGHT }

data class ComboName(val color: ColorName, val shade: Shade? = null, val hex: String? = null)

fun comboNames(combos: List<StyleCombo>): List<ComboName> {
    fun distinguish(names: List<ComboName>, extra: (Int, ComboName) -> ComboName) = names.mapIndexed { i, name ->
        val clash = names.indices.any { j -> j != i && combos[j].style == combos[i].style && names[j] == name }
        if (clash) extra(combos[i].color, name) else name
    }
    val plain = combos.map { ComboName(colorName(it.color)) }
    val shaded = distinguish(plain) { argb, name ->
        if (name.color == ColorName.BLACK || name.color == ColorName.WHITE) name else name.copy(shade = shadeOf(argb))
    }
    return distinguish(shaded) { argb, name -> name.copy(hex = hexOf(argb)) }
}

private fun shadeOf(argb: Int): Shade {
    val channels = listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)
    return if (channels.max() + channels.min() < 255) Shade.DARK else Shade.LIGHT
}

private fun hexOf(argb: Int): String =
    if (argb ushr 24 == 0xFF) "#%06X".format(argb and 0xFFFFFF) else "#%08X".format(argb)

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
