package org.lattenwald.moonvocab.domain.inbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {
    private val order = listOf(1L, 2, 3, 4, 5, 6)

    @Test fun dragForwardSelectsRange() = assertEquals(setOf(2L, 3, 4), Selection().startDrag(order, 2).dragOver(4).ids)

    @Test fun dragBackShrinksToSnapshot() {
        val s = Selection(setOf(6L), active = true).startDrag(order, 2).dragOver(5).dragOver(3)
        assertEquals(setOf(2L, 3, 6), s.ids)
    }

    @Test fun dragFromSelectedRowDeselects() {
        val s = Selection(setOf(2L, 3, 4), active = true).startDrag(order, 3).dragOver(4)
        assertEquals(setOf(2L), s.ids)
    }

    @Test fun dragUpward() = assertEquals(setOf(3L, 4, 5), Selection().startDrag(order, 5).dragOver(3).ids)

    @Test fun unknownRowIsIgnored() {
        val s = Selection().startDrag(order, 2)
        assertEquals(s, s.dragOver(99))
    }

    @Test fun endingADragThatSelectedNothingLeavesSelection() =
        assertEquals(Selection(), Selection(setOf(2L), active = true).startDrag(order, 2).endDrag())

    @Test fun togglingTheLastOffLeavesSelection() = assertEquals(Selection(), Selection(setOf(2L), active = true).toggle(2))

    @Test fun groupToggleAddsThenRemoves() {
        val s = Selection(setOf(1L), active = true).toggleGroup(listOf(1, 2))
        assertEquals(setOf(1L, 2), s.ids)
        assertEquals(Selection(), s.toggleGroup(listOf(1, 2)))
    }

    @Test fun selectAllStaysInSelectionWhenCleared() {
        val all = Selection().toggleAll(order)
        assertTrue(all.active)
        assertEquals(order.toSet(), all.ids)
        assertEquals(Selection(active = true), all.toggleAll(order))
    }

    @Test fun retainDropsVanishedCards() {
        assertEquals(setOf(2L), Selection(setOf(2L, 3), active = true).retain(setOf(1, 2)).ids)
        assertEquals(Selection(), Selection(setOf(3L), active = true).retain(setOf(1, 2)))
    }

    @Test fun rowAtSkipsHeadersAndGaps() {
        val rows = listOf(RowSpan(null, 0, 40), RowSpan(1, 40, 100), RowSpan(2, 102, 160))
        assertNull(rowAt(10, rows))
        assertEquals(1L, rowAt(40, rows))
        assertNull(rowAt(101, rows))
        assertEquals(2L, rowAt(159, rows))
    }

    @Test fun autoScrollOnlyNearTheEdges() {
        assertEquals(0f, autoScrollStep(500f, 0f, 1000f, 72f, 18f))
        assertEquals(-4f, autoScrollStep(52f, 0f, 1000f, 72f, 18f))
        assertEquals(14.4f, autoScrollStep(1000f, 0f, 1000f, 72f, 18f), 0.001f)
        assertEquals(18f, autoScrollStep(1050f, 0f, 1000f, 72f, 18f))
    }
}
