package org.lattenwald.moonvocab.ui

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigatorTest {
    private fun stackOf(vararg keys: NavKey) = mutableListOf(*keys)

    @Test fun aPageThatIsNoLongerOnTopMovesNothing() {
        val stack = stackOf(InboxKey, CardKey(1))
        val navigator = Navigator(stack)
        val inbox = navigator.at(InboxKey)

        inbox.open(CardKey(2))
        inbox.back()
        navigator.at(CardKey(1)).replace(CardKey(3))
        assertEquals(listOf(InboxKey, CardKey(3)), stack)

        navigator.at(CardKey(1)).back()
        navigator.at(CardKey(3)).back()
        navigator.at(CardKey(3)).back()
        assertEquals(listOf(InboxKey), stack)
    }

    @Test fun aTabSitsAloneOnTheInbox() {
        val stack = stackOf(InboxKey, CardKey(1))
        val navigator = Navigator(stack)

        navigator.selectTab(LibraryKey)
        assertEquals(listOf(InboxKey, LibraryKey), stack)
        navigator.selectTab(LibraryKey)
        assertEquals(listOf(InboxKey, LibraryKey), stack)
        navigator.selectTab(InboxKey)
        assertEquals(listOf(InboxKey), stack)
    }

    @Test fun servicesOpenAboveTheSettingsTabOnlyFromTheInbox() {
        val stack = stackOf(InboxKey, CardKey(1))
        val navigator = Navigator(stack)

        navigator.openFromTab(InboxKey, SettingsKey, ServicesKey)
        assertEquals(listOf(InboxKey, CardKey(1)), stack)

        navigator.back()
        navigator.openFromTab(InboxKey, SettingsKey, ServicesKey)
        assertEquals(listOf(InboxKey, SettingsKey, ServicesKey), stack)
    }

    @Test fun finishingSetupLeavesOnlyTheInbox() {
        val stack = stackOf(SetupKey, LanguageKey(LanguageKind.TARGET))
        val navigator = Navigator(stack)

        assertFalse(navigator.finishSetup())
        navigator.back()
        assertTrue(navigator.finishSetup())
        assertEquals(listOf<NavKey>(InboxKey), stack)
    }
}
