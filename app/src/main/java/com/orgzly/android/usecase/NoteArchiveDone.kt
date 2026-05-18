package com.orgzly.android.usecase

import com.orgzly.android.App
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.NotePlace

class NoteArchiveDone(private val scope: DoneArchiveScope) : UseCase() {
    override fun run(dataRepository: com.orgzly.android.data.DataRepository): UseCaseResult {
        val context = App.getAppContext()
        val destinationBookId = AppPreferences.doneArchiveBookId(context)
            ?: throw DestinationNotConfigured()
        val destinationBook = dataRepository.getBook(destinationBookId)
            ?: throw DestinationMissing(destinationBookId)

        if (destinationBookId == scope.sourceBookId) {
            throw DestinationIsSameBook(destinationBook.name)
        }

        val doneKeywords = AppPreferences.doneKeywordsSet(context)
        val todoKeywords = AppPreferences.todoKeywordsSet(context)
        val notesInScope = when (scope) {
            is DoneArchiveScope.Book -> dataRepository.getTopLevelNotes(scope.bookId)
            is DoneArchiveScope.Heading -> dataRepository.getNoteChildren(scope.parentNoteId)
        }

        val selectedIds = linkedSetOf<Long>()
        var candidateCount = 0
        var skippedActiveDescendantCount = 0

        for (note in notesInScope) {
            if (note.state !in doneKeywords) {
                continue
            }

            candidateCount += 1

            val hasActiveDescendant = dataRepository.getNotesAndSubtrees(setOf(note.id)).any {
                it.id != note.id && it.state in todoKeywords
            }

            if (hasActiveDescendant) {
                skippedActiveDescendantCount += 1
            } else {
                selectedIds.add(note.id)
            }
        }

        if (selectedIds.isNotEmpty()) {
            dataRepository.refileNotes(selectedIds, NotePlace(destinationBookId))
        }

        return UseCaseResult(
            modifiesLocalData = selectedIds.isNotEmpty(),
            triggersSync = if (selectedIds.isNotEmpty()) SYNC_DATA_MODIFIED else SYNC_NOT_REQUIRED,
            userData = DoneArchiveResult(
                movedCount = selectedIds.size,
                skippedActiveDescendantCount = skippedActiveDescendantCount,
                candidateCount = candidateCount,
                destinationBookName = destinationBook.name,
                sourceScope = scope,
            ),
        )
    }

    class DestinationNotConfigured : Throwable()

    class DestinationMissing(val destinationBookId: Long) : Throwable()

    class DestinationIsSameBook(val destinationBookName: String) : Throwable()
}
