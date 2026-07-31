package com.orgzly.android.ui.tags

object TagBrowserRows {
    data class NoteInput(
            val directTags: List<String>,
            val inheritedTags: List<String>,
            val isActiveTodo: Boolean
    )

    fun fromNotes(notes: Iterable<NoteInput>): List<TagBrowserRow> {
        val counts = linkedMapOf<String, Int>()

        notes.forEach { note ->
            if (!note.isActiveTodo) {
                return@forEach
            }

            (note.directTags + note.inheritedTags)
                    .toSet()
                    .forEach { tag ->
                        counts[tag] = (counts[tag] ?: 0) + 1
                    }
        }

        return counts.entries
                .asSequence()
                .filter { it.value > 0 }
                .map { TagBrowserRow(it.key, it.value) }
                .sortedBy { it.tag }
                .toList()
    }
}
