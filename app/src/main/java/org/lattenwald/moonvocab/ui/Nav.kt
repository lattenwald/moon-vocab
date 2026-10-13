package org.lattenwald.moonvocab.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object InboxKey : NavKey

@Serializable data object LibraryKey : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data object SetupKey : NavKey

@Serializable data class BookKey(val bookId: Long) : NavKey

@Serializable data class CardKey(val cardId: Long) : NavKey

// A null card id previews the sample card.
@Serializable data class PreviewKey(val cardId: Long?) : NavKey

enum class LanguageKind { TARGET, APP }

@Serializable data class LanguageKey(val kind: LanguageKind) : NavKey

@Serializable data object ServicesKey : NavKey

@Serializable data class ServiceKey(val id: String) : NavKey

@Serializable data object CardsSettingsKey : NavKey

@Serializable data object DeveloperKey : NavKey

val TabKeys: List<NavKey> = listOf(InboxKey, LibraryKey, SettingsKey)

class Navigator(private val stack: MutableList<NavKey>) {
    val top: NavKey get() = stack.last()

    fun at(key: NavKey): Page = Page(key)

    fun popTop() {
        stack.removeLastOrNull()
    }

    fun selectTab(tab: NavKey) {
        if (stack.lastOrNull() == tab) return
        while (stack.size > 1) stack.removeLastOrNull()
        if (tab != InboxKey) stack.add(tab)
    }

    // A second tap during a transition must not push twice, and a late back must pop only its own page.
    inner class Page(private val key: NavKey) {
        private val onTop: Boolean get() = stack.lastOrNull() == key

        fun back() {
            if (onTop) stack.removeLastOrNull()
        }

        fun open(next: NavKey) {
            if (onTop) stack.add(next)
        }

        fun replace(next: NavKey) {
            if (!onTop) return
            stack.removeLastOrNull()
            stack.add(next)
        }

        fun openAbove(tab: NavKey, next: NavKey) {
            if (!onTop) return
            selectTab(tab)
            stack.add(next)
        }

        fun startOver(root: NavKey): Boolean {
            if (!onTop) return false
            stack.add(root)
            while (stack.size > 1) stack.removeAt(0)
            return true
        }
    }
}
