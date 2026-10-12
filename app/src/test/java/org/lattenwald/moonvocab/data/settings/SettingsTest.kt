package org.lattenwald.moonvocab.data.settings

import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.StyleCombo
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsTest {
    @get:Rule val folder = TemporaryFolder()

    private val openRouter = LlmService("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "", "anthropic/claude-haiku-5.5")

    private lateinit var dataStore: DataStore<Preferences>

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") }.also { dataStore = it }

    private suspend fun rawPreferences() = dataStore.data.first()

    @Test fun emptyStoreGivesDefaults() = runTest {
        val read = Settings(store()).current()
        assertEquals(
            SettingsSnapshot(
                backupTree = null, booksTree = null, services = listOf(openRouter), activeServiceId = "openrouter",
                targetLanguage = "ru", maxExcerptWords = 15, deckPattern = "Moon+::{language}", tagPattern = "book::{title}",
                reviewFirst = true, styles = emptySet(), noteTypeId = null, noteTypeVersion = 0, deckOverride = "Moon+::Test",
                lastCheckAt = 0, theme = ThemeMode.SYSTEM, setupDone = false, ankiAsked = false,
            ),
            read,
        )
        assertEquals(openRouter, read.activeService)
    }

    @Test fun anInstallWithABackupFolderCountsAsSetUp() = runTest {
        val store = store()
        assertFalse(Settings(store).current().setupDone)
        store.edit { it[SettingsKeys.backupTree] = "content://tree/backups" }
        assertTrue(Settings(store).current().setupDone)
    }

    @Test fun choosingABackupFolderDuringSetupKeepsSetupUnfinished() = runTest {
        val settings = Settings(store())
        settings.update { it.copy(backupTree = Uri.parse("content://tree/backups")) }
        assertFalse(settings.current().setupDone)
        settings.update { it.copy(setupDone = true) }
        assertTrue(settings.current().setupDone)
        assertEquals(setOf("backupTree"), rawPreferences().asMap().keys.map { it.name }.toSet())
    }

    @Test fun themeRoundTrips() = runTest {
        val settings = Settings(store())
        settings.update { it.copy(theme = ThemeMode.DARK) }
        assertEquals(ThemeMode.DARK, settings.current().theme)
    }

    @Test fun writingSystemThemeRemovesTheKey() = runTest {
        val settings = Settings(store())
        settings.update { it.copy(theme = ThemeMode.LIGHT) }; settings.update { it.copy(theme = ThemeMode.SYSTEM) }
        assertFalse("theme" in rawPreferences().asMap().keys.map { it.name })
    }

    @Test fun oldSettingsReadUnchanged() = runTest {
        val store = store()
        store.edit {
            it[SettingsKeys.services] = """[{"id":"a","name":"A","baseUrl":"https://example.com/v1","apiKey":"k","model":"m"}]"""
            it[SettingsKeys.activeServiceId] = "a"
            it[SettingsKeys.targetLanguage] = "de"
            it[SettingsKeys.backupTree] = "content://tree/backups"
        }
        val read = Settings(store).current()
        assertEquals(listOf(LlmService("a", "A", "https://example.com/v1", "k", "m")), read.services)
        assertEquals("a", read.activeServiceId)
        assertEquals("de", read.targetLanguage)
        assertEquals(Uri.parse("content://tree/backups"), read.backupTree)
    }

    @Test fun roundTripsTwoServicesAndSwitchedActive() = runTest {
        val settings = Settings(store())
        val cheap = openRouter.copy(id = "a", name = "Cheap", apiKey = "k1")
        val strong = openRouter.copy(id = "b", name = "Strong", apiKey = "k2", model = "anthropic/claude-sonnet")
        val changed = settings.current().copy(
            backupTree = Uri.parse("content://tree/backups"), services = listOf(cheap, strong), activeServiceId = "b",
            styles = setOf(StyleCombo(MarkStyle.WAVY, -28160), StyleCombo(MarkStyle.HIGHLIGHT, 1996532479)),
            noteTypeId = 1_700_000_000_123, deckOverride = null, reviewFirst = false, lastCheckAt = 42, ankiAsked = true,
        )
        settings.update { changed }
        val read = settings.current()
        assertEquals(changed, read)
        assertEquals(strong, read.activeService)
    }

    @Test fun unparsableServicesFallBackToDefault() = runTest {
        val store = store()
        store.edit { it[SettingsKeys.services] = "{not json"; it[SettingsKeys.activeServiceId] = "gone" }
        val read = Settings(store).current()
        assertEquals(listOf(openRouter), read.services)
        assertEquals(openRouter, read.activeService)
    }

    @Test fun serviceWithMissingFieldsDecodes() = runTest {
        val store = store()
        store.edit { it[SettingsKeys.services] = """[{"id":"a","name":"A","baseUrl":"https://example.com/v1","future":1}]""" }
        assertEquals(listOf(LlmService("a", "A", "https://example.com/v1", "", "")), Settings(store).current().services)
    }

    @Test fun updateLeavesUnparsableServicesUntouched() = runTest {
        val store = store()
        store.edit { it[SettingsKeys.services] = "{not json" }
        Settings(store).update { it.copy(targetLanguage = "de") }
        assertEquals("{not json", store.data.first()[SettingsKeys.services])
        assertEquals("de", Settings(store).current().targetLanguage)
    }

    @Test fun updateTrimsServiceFields() = runTest {
        val settings = Settings(store())
        settings.update {
            it.copy(services = listOf(openRouter.copy(baseUrl = " https://example.com/v1\n", apiKey = "  sk-1 ", model = "\tm ")))
        }
        assertEquals(
            listOf(openRouter.copy(baseUrl = "https://example.com/v1", apiKey = "sk-1", model = "m")),
            settings.current().services,
        )
    }

    @Test fun unchangedDefaultsAreNotStored() = runTest {
        val settings = Settings(store())
        settings.update { it.copy(targetLanguage = "de") }
        val keys = rawPreferences().asMap().keys.map { it.name }.toSet()
        assertEquals(setOf("targetLanguage"), keys)
    }

    @Test fun valueSetBackToDefaultIsRemoved() = runTest {
        val settings = Settings(store())
        settings.update { it.copy(deckOverride = null) }; settings.update { it.copy(deckOverride = "Moon+::Test") }
        assertFalse("deckOverride" in rawPreferences().asMap().keys.map { it.name })
    }
}
