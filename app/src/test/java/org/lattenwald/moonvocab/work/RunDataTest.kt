package org.lattenwald.moonvocab.work

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.domain.import.ImportRequest
import org.lattenwald.moonvocab.domain.mark.MarkStyle
import org.lattenwald.moonvocab.domain.mark.StyleCombo

class RunDataTest {
    @Test fun aRunKindSurvivesInputData() {
        val full = RunKind.Import(
            ImportRequest(File("/cache/backups/a.mrpro"), setOf(StyleCombo(MarkStyle.WAVY, -28160), StyleCombo(MarkStyle.HIGHLIGHT, 7)), "diaspora.epub", true),
        )
        val resume = RunKind.Import(ImportRequest(null, emptySet(), null, false))

        assertEquals(full, RunData.kind(RunData.input(full)))
        assertEquals(resume, RunData.kind(RunData.input(resume)))
        assertEquals(RunKind.RetryFailed, RunData.kind(RunData.input(RunKind.RetryFailed)))
    }
}
