package com.orgzly.android.ui.note

import org.junit.Assert.assertEquals
import org.junit.Test

class NotePropertyNameMatcherTest {
    @Test
    fun emptyQueryReturnsAllPropertiesInDictionaryOrder() {
        val dictionary = listOf("CREATED", "Effort", "CUSTOM_ID")

        assertEquals(dictionary, NotePropertyNameMatcher.match(dictionary, ""))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        val dictionary = listOf("CREATED", "Effort", "CUSTOM_ID")

        assertEquals(listOf("Effort"), NotePropertyNameMatcher.match(dictionary, "eff"))
    }

    @Test
    fun matchesPropertyNamesContainingQuery() {
        val dictionary = listOf("CREATED", "Effort", "CUSTOM_ID", "projectArea")

        assertEquals(listOf("CUSTOM_ID"), NotePropertyNameMatcher.match(dictionary, "id"))
    }

    @Test
    fun matchesPropertyNamesAsSubsequenceForFuzzyTyping() {
        val dictionary = listOf("CREATED", "CUSTOM_ID", "AssignedTo")

        assertEquals(listOf("CUSTOM_ID"), NotePropertyNameMatcher.match(dictionary, "ctm"))
    }

    @Test
    fun ranksExactThenPrefixThenContainsThenSubsequence() {
        val dictionary = listOf("foo_bar", "bar", "barrel", "bright_area")

        assertEquals(
            listOf("bar", "barrel", "foo_bar", "bright_area"),
            NotePropertyNameMatcher.match(dictionary, "bar")
        )
    }
}
