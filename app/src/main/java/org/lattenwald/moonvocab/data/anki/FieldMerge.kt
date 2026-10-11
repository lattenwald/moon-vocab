package org.lattenwald.moonvocab.data.anki

fun mergeFields(fieldNames: List<String>, current: List<String>?, ours: Map<String, String>): Array<String> {
    val missing = ours.keys.filter { it !in fieldNames }
    if (missing.isNotEmpty()) throw NoteTypeChanged(missing)
    return Array(fieldNames.size) { i -> ours[fieldNames[i]] ?: current?.getOrNull(i) ?: "" }
}
