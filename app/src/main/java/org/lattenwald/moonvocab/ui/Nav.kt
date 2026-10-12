package org.lattenwald.moonvocab.ui

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object InboxKey : NavKey

@Serializable data object LibraryKey : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data class BookKey(val bookId: Long) : NavKey

@Serializable data class CardKey(val cardId: Long) : NavKey

@Serializable data class PreviewKey(val cardId: Long) : NavKey

enum class LanguageKind { TARGET, APP }

@Serializable data class LanguageKey(val kind: LanguageKind) : NavKey

@Serializable data object ServicesKey : NavKey

@Serializable data class ServiceKey(val id: String) : NavKey

val TabKeys: List<NavKey> = listOf(InboxKey, LibraryKey, SettingsKey)

fun NavBackStack<NavKey>.selectTab(tab: NavKey) {
    if (lastOrNull() == tab) return
    while (size > 1) removeLastOrNull()
    if (tab != InboxKey) add(tab)
}
