package org.lattenwald.moonvocab.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MoonFb2Test {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun untitledSectionJoinsTheChapterBeforeIt() {
        val file = BookFixtures.fb2(tmp.root, sections = listOf(
            listOf("<title><p>One</p></title>", "First."),
            listOf("Second."),
            listOf("<title><p>Two</p></title>", "Third."),
        ))
        val book = MoonFb2.read(file)!!
        assertEquals(2, book.size)
        val one = book.parts(0).single()
        assertTrue("First." in one && "Second." in one)
        val two = book.parts(1).single()
        assertTrue("<h2>Two</h2>" in two && "Third." in two)
        assertFalse("Second." in two)
    }

    @Test fun encodingComesFromTheXmlDeclaration() {
        val file = BookFixtures.fb2(tmp.root, encoding = "windows-1251", sections = listOf(listOf("<title><p>Глава</p></title>", "Привет, мир.")))
        assertTrue("Привет, мир." in MoonFb2.read(file)!!.parts(0).single())
    }
}
