package org.lattenwald.moonvocab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MarkLight = Color(0xFFD9480F)
private val MarkDark = Color(0xFFFF8A50)

val LocalMarkColor: ProvidableCompositionLocal<Color> = staticCompositionLocalOf { MarkLight }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MoonVocabTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    CompositionLocalProvider(LocalMarkColor provides if (dark) MarkDark else MarkLight) {
        MaterialExpressiveTheme(
            colorScheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
            motionScheme = MotionScheme.expressive(),
            typography = AppTypography,
            content = content,
        )
    }
}
