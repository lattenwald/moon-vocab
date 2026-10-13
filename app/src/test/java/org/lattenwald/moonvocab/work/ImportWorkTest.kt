package org.lattenwald.moonvocab.work

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.StyleCombo

class ImportWorkTest {
    @Test fun jobSurvivesInputData() {
        val full = ImportJob.Import(
            ImportRequest(File("/cache/backups/a.mrpro"), setOf(StyleCombo(MarkStyle.WAVY, -28160), StyleCombo(MarkStyle.HIGHLIGHT, 7)), "diaspora.epub", true),
        )
        val resume = ImportJob.Import(ImportRequest(null, emptySet(), null, false))

        assertEquals(full, ImportWork.job(ImportWork.input(full)))
        assertEquals(resume, ImportWork.job(ImportWork.input(resume)))
        assertEquals(ImportJob.RetryFailed, ImportWork.job(ImportWork.input(ImportJob.RetryFailed)))
    }
}
