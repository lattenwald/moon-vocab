package org.lattenwald.moonvocab.work

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.lattenwald.moonvocab.data.llm.LlmException
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.import.ImportSummary
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.StyleCombo

class ImportWorkTest {
    @Test fun requestSurvivesInputData() {
        val full = ImportRequest(
            File("/cache/backups/a.mrpro"), setOf(StyleCombo(MarkStyle.WAVY, -28160), StyleCombo(MarkStyle.HIGHLIGHT, 7)), "diaspora.epub", true,
        )
        val resume = ImportRequest(null, emptySet(), null, false)

        assertEquals(full, ImportWork.request(ImportWork.input(full)))
        assertEquals(resume, ImportWork.request(ImportWork.input(resume)))
        assertFalse(ImportWork.isRetry(ImportWork.input(full)))
        assertTrue(ImportWork.isRetry(ImportWork.retryInput()))
    }

    @Test fun summarySurvivesOutputData() {
        val full = ImportSummary(3, 1, 2, "truncated", LlmException.Stop.Kind.CREDITS, "AnkiDroid refused")
        val plain = ImportSummary(0, 0, 0, null, null, null)

        assertEquals(full, ImportWork.summary(ImportWork.output(full)))
        assertEquals(plain, ImportWork.summary(ImportWork.output(plain)))
    }
}
