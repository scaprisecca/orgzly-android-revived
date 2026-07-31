package com.orgzly.android.ui.tags

import org.junit.Assert.assertEquals
import org.junit.Test

class TagBrowserRowsTest {
    @Test
    fun inheritedTagContributesToChildTodoCount() {
        val rows = TagBrowserRows.fromNotes(listOf(
                TagBrowserRows.NoteInput(
                        directTags = emptyList(),
                        inheritedTags = listOf("parent"),
                        isActiveTodo = true)
        ))

        assertEquals(listOf(TagBrowserRow("parent", 1)), rows)
    }

    @Test
    fun directTagContributesToCount() {
        val rows = TagBrowserRows.fromNotes(listOf(
                TagBrowserRows.NoteInput(
                        directTags = listOf("work"),
                        inheritedTags = emptyList(),
                        isActiveTodo = true)
        ))

        assertEquals(listOf(TagBrowserRow("work", 1)), rows)
    }

    @Test
    fun doneAndPlainNotesAreExcluded() {
        val rows = TagBrowserRows.fromNotes(listOf(
                TagBrowserRows.NoteInput(
                        directTags = listOf("done-tag"),
                        inheritedTags = emptyList(),
                        isActiveTodo = false),
                TagBrowserRows.NoteInput(
                        directTags = listOf("plain-tag"),
                        inheritedTags = listOf("inherited"),
                        isActiveTodo = false)
        ))

        assertEquals(emptyList<TagBrowserRow>(), rows)
    }

    @Test
    fun duplicateTagAcrossDirectAndInheritedCountsOncePerNote() {
        val rows = TagBrowserRows.fromNotes(listOf(
                TagBrowserRows.NoteInput(
                        directTags = listOf("work"),
                        inheritedTags = listOf("work", "shared"),
                        isActiveTodo = true)
        ))

        assertEquals(
                listOf(
                        TagBrowserRow("shared", 1),
                        TagBrowserRow("work", 1)),
                rows)
    }

    @Test
    fun caseSensitiveTagNamesRemainSeparate() {
        val rows = TagBrowserRows.fromNotes(listOf(
                TagBrowserRows.NoteInput(
                        directTags = listOf("@HomeDepot"),
                        inheritedTags = emptyList(),
                        isActiveTodo = true),
                TagBrowserRows.NoteInput(
                        directTags = listOf("@homedepot"),
                        inheritedTags = emptyList(),
                        isActiveTodo = true)
        ))

        assertEquals(
                listOf(
                        TagBrowserRow("@HomeDepot", 1),
                        TagBrowserRow("@homedepot", 1)),
                rows)
    }

    @Test
    fun outputIsAlphabetical() {
        val rows = TagBrowserRows.fromNotes(listOf(
                TagBrowserRows.NoteInput(
                        directTags = listOf("zeta"),
                        inheritedTags = listOf("alpha"),
                        isActiveTodo = true),
                TagBrowserRows.NoteInput(
                        directTags = listOf("middle"),
                        inheritedTags = emptyList(),
                        isActiveTodo = true)
        ))

        assertEquals(
                listOf(
                        TagBrowserRow("alpha", 1),
                        TagBrowserRow("middle", 1),
                        TagBrowserRow("zeta", 1)),
                rows)
    }
}
