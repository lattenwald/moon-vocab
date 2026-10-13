package org.lattenwald.moonvocab.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import org.lattenwald.moonvocab.R

private fun variableFont(resId: Int, weight: Int, style: FontStyle = FontStyle.Normal) = Font(
    resId = resId,
    weight = FontWeight(weight),
    style = style,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val RobotoFlexFamily: FontFamily = FontFamily(
    variableFont(R.font.roboto_flex, 400),
    variableFont(R.font.roboto_flex, 500),
    variableFont(R.font.roboto_flex, 600),
)

val BookFontFamily: FontFamily = FontFamily(
    variableFont(R.font.literata, 400),
    variableFont(R.font.literata, 600),
    variableFont(R.font.literata_italic, 400, FontStyle.Italic),
    variableFont(R.font.literata_italic, 600, FontStyle.Italic),
)

// Literata and Roboto Flex have no IPA letters (ɪ, ʊ, ʃ).
val IpaFontFamily: FontFamily = FontFamily.SansSerif

val AppTypography: Typography = Typography(fontFamily = RobotoFlexFamily)
