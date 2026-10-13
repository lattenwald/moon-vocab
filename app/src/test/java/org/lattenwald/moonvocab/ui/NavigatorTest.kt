package org.lattenwald.moonvocab.ui

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    @Test fun aPageMovesOnlyWhileOnTop() {
        val stack = mutableListOf<NavKey>(InboxKey, CardKey(1))
        val navigator = Navigator(stack)
        val inbox = navigator.at(InboxKey)

        inbox.open(CardKey(2))
        inbox.back()
        inbox.openAbove(SettingsKey, ServicesKey)
        assertEquals(listOf(InboxKey, CardKey(1)), stack)

        navigator.at(CardKey(1)).replace(CardKey(3))
        navigator.at(CardKey(1)).back()
        assertEquals(listOf(InboxKey, CardKey(3)), stack)

        navigator.at(CardKey(3)).back()
        navigator.at(CardKey(3)).back()
        assertEquals(listOf<NavKey>(InboxKey), stack)
    }

    @Test fun aTabSitsAloneOnTheInbox() {
        val stack = mutableListOf<NavKey>(InboxKey, CardKey(1))
        val navigator = Navigator(stack)

        navigator.selectTab(LibraryKey)
        assertEquals(listOf(InboxKey, LibraryKey), stack)
        navigator.selectTab(LibraryKey)
        assertEquals(listOf(InboxKey, LibraryKey), stack)
        navigator.selectTab(InboxKey)
        navigator.selectTab(InboxKey)
        assertEquals(listOf<NavKey>(InboxKey), stack)
    }

    @Test fun servicesOpenAboveTheSettingsTab() {
        val stack = mutableListOf<NavKey>(InboxKey)
        Navigator(stack).at(InboxKey).openAbove(SettingsKey, ServicesKey)
        assertEquals(listOf(InboxKey, SettingsKey, ServicesKey), stack)
    }

    @Test fun finishingSetupLeavesOnlyTheInbox() {
        val stack = mutableListOf<NavKey>(SetupKey, LanguageKey(LanguageKind.TARGET))
        val navigator = Navigator(stack)

        assertFalse(navigator.at(SetupKey).startOver(InboxKey))
        navigator.popTop()
        assertTrue(navigator.at(SetupKey).startOver(InboxKey))
        assertEquals(listOf<NavKey>(InboxKey), stack)
    }
}
