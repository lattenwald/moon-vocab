package org.lattenwald.moonvocab.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import java.util.Locale
import org.lattenwald.moonvocab.R

@Composable
fun languageName(code: String?): String {
    if (code == null) return stringResource(R.string.inbox_language_other)
    return languageName(code, LocalConfiguration.current.locales[0])
}

fun languageName(code: String, inLocale: Locale): String =
    Locale.forLanguageTag(code).getDisplayLanguage(inLocale).ifEmpty { code }.replaceFirstChar { it.titlecase(inLocale) }
