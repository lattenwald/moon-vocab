package org.lattenwald.moonvocab.domain.inbox

data class Drag(val snapshot: Set<Long>, val order: List<Long>, val anchor: Int, val current: Int, val adding: Boolean)

data class Selection(val ids: Set<Long> = emptySet(), val active: Boolean = false, val drag: Drag? = null)

fun Selection.startDrag(order: List<Long>, id: Long): Selection {
    val index = order.indexOf(id)
    if (index < 0) return this
    val adding = id !in ids
    return Selection(
        ids = if (adding) ids + id else ids - id,
        active = true,
        drag = Drag(snapshot = ids, order = order, anchor = index, current = index, adding = adding),
    )
}

fun Selection.dragOver(id: Long): Selection {
    val d = drag ?: return this
    val index = d.order.indexOf(id)
    if (index < 0) return this
    val range = d.order.subList(minOf(d.anchor, index), maxOf(d.anchor, index) + 1)
    return copy(
        ids = if (d.adding) d.snapshot + range else d.snapshot - range.toSet(),
        drag = d.copy(current = index),
    )
}

// No-op without a drag, so a stray call can't close an emptied Select-all.
fun Selection.endDrag(): Selection = when {
    drag == null -> this
    ids.isEmpty() -> Selection()
    else -> copy(drag = null)
}

fun Selection.toggle(id: Long): Selection = withIds(if (id in ids) ids - id else ids + id)

fun Selection.toggleGroup(ids: List<Long>): Selection = withIds(flipAll(ids))

fun Selection.toggleAll(ids: List<Long>): Selection = copy(ids = flipAll(ids), active = true)

fun Selection.retain(existing: Set<Long>): Selection {
    val kept = ids intersect existing
    if (drag == null && ids.isNotEmpty() && kept.isEmpty()) return Selection()
    return copy(ids = kept, drag = drag?.let { it.copy(snapshot = it.snapshot intersect existing) })
}

private fun Selection.flipAll(group: List<Long>): Set<Long> = if (ids.containsAll(group)) ids - group.toSet() else ids + group

private fun Selection.withIds(next: Set<Long>): Selection = if (next.isEmpty()) Selection() else copy(ids = next, active = true)

data class RowSpan(val id: Long?, val top: Int, val bottom: Int) // id null for headers and other non-card items

fun rowAt(y: Int, rows: List<RowSpan>): Long? = rows.firstOrNull { y >= it.top && y < it.bottom }?.id

fun autoScrollStep(y: Float, top: Float, bottom: Float, zone: Float, maxStep: Float): Float = when {
    y < top + zone -> -minOf(maxStep, (top + zone - y) / 5)
    y > bottom - zone -> minOf(maxStep, (y - (bottom - zone)) / 5)
    else -> 0f
}
