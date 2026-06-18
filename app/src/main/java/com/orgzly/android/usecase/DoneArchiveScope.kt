package com.orgzly.android.usecase

sealed class DoneArchiveScope(val sourceBookId: Long) {
    data class Book(val bookId: Long) : DoneArchiveScope(bookId)

    data class Heading(val bookId: Long, val parentNoteId: Long) : DoneArchiveScope(bookId)
}
