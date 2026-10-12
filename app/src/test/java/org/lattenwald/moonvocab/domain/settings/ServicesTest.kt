package org.lattenwald.moonvocab.domain.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import org.lattenwald.moonvocab.data.settings.LlmService

class ServicesTest {
    private val a = LlmService("a", "A", "https://a.example/v1", "ka", "ma")
    private val b = LlmService("b", "B", "https://b.example/v1", "kb", "mb")
    private val c = LlmService("c", "C", "https://c.example/v1", "kc", "mc")
    private val abc = ServiceList(listOf(a, b, c), activeId = "a")

    @Test fun addActivatesTheNewServiceWithTheTemplateUrl() {
        val groq = SERVICE_TEMPLATES.single { it.key == "groq" }
        val added = abc.add(groq, name = "My Groq", newId = "n")
        assertEquals(ServiceList(listOf(a, b, c, LlmService("n", "My Groq", "https://api.groq.com/openai/v1", "", "")), "n"), added)
    }

    @Test fun duplicateKeepsKeyAndModelAndSitsAfterTheOriginal() {
        val copied = abc.duplicate("b", name = "B copy", newId = "n")
        assertEquals(ServiceList(listOf(a, b, b.copy(id = "n", name = "B copy"), c), "n"), copied)
    }

    @Test fun deletingTheActiveServiceActivatesItsNeighbour() {
        assertEquals(ServiceList(listOf(a, c), "c"), abc.copy(activeId = "b").delete("b"))
        assertEquals(ServiceList(listOf(a, b), "b"), abc.copy(activeId = "c").delete("c"))
    }

    @Test fun deletingAnInactiveServiceKeepsTheActiveOne() {
        assertEquals(ServiceList(listOf(b, c), "c"), abc.copy(activeId = "c").delete("a"))
    }

    @Test fun theLastServiceCannotBeDeleted() {
        val one = ServiceList(listOf(a), "a")
        assertEquals(one, one.delete("a"))
    }

    @Test fun selectIgnoresAnUnknownId() {
        assertEquals(abc.copy(activeId = "c"), abc.select("c"))
        assertEquals(abc, abc.select("gone"))
    }
}
