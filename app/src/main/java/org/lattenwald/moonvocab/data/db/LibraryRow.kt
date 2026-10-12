package org.lattenwald.moonvocab.data.db

data class LibraryRow(
    val bookId: Long,
    val title: String,
    val author: String?,
    val language: String?,
    val inAnki: Int,
    val toReview: Int,
    val newestMark: Long?,
)
