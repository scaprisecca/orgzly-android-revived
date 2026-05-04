package com.orgzly.android.ui.note

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class EditorToolbarActionsTest {
    @Test
    fun boldInsertsPairAtCaret() {
        val result = EditorToolbarActions.bold("abc", EditorSelection(1, 1))

        assertThat(result.text, equalTo("a**bc"))
        assertThat(result.selection, equalTo(EditorSelection(2, 2)))
    }

    @Test
    fun italicWrapsSelection() {
        val result = EditorToolbarActions.italic("hello", EditorSelection(1, 4))

        assertThat(result.text, equalTo("h/ell/o"))
        assertThat(result.selection, equalTo(EditorSelection(2, 5)))
    }

    @Test
    fun linkTemplateSelectsTargetAtCaret() {
        val result = EditorToolbarActions.link("", EditorSelection(0, 0))

        assertThat(result.text, equalTo("[[link][description]]"))
        assertThat(result.selection, equalTo(EditorSelection(2, 6)))
    }

    @Test
    fun checkboxTransformsCurrentLine() {
        val result = EditorToolbarActions.checkboxList("task", EditorSelection(0, 0))

        assertThat(result.text, equalTo("- [ ] task"))
        assertThat(result.selection, equalTo(EditorSelection(6, 6)))
    }

    @Test
    fun bulletTransformsEverySelectedLine() {
        val result = EditorToolbarActions.bulletList("one\ntwo\nthree", EditorSelection(1, 7))

        assertThat(result.text, equalTo("- one\n- two\nthree"))
        assertThat(result.selection, equalTo(EditorSelection(3, 12)))
    }

    @Test
    fun numberedListNumbersEachSelectedLine() {
        val result = EditorToolbarActions.numberedList("one\ntwo", EditorSelection(0, 7))

        assertThat(result.text, equalTo("1. one\n2. two"))
        assertThat(result.selection, equalTo(EditorSelection(3, 13)))
    }

    @Test
    fun headingPrefixesCurrentLine() {
        val result = EditorToolbarActions.heading("Title", EditorSelection(2, 2))

        assertThat(result.text, equalTo("* Title"))
        assertThat(result.selection, equalTo(EditorSelection(4, 4)))
    }

    @Test
    fun propertyDrawerInsertsTemplateAndSelectsPropertyName() {
        val result = EditorToolbarActions.propertyDrawer("Body", EditorSelection(0, 0))

        assertThat(result.text, equalTo(":PROPERTIES:\n:PROPERTY: \n:END:\nBody"))
        assertThat(result.selection, equalTo(EditorSelection(13, 21)))
    }

    @Test
    fun scheduledTimestampInsertsAtLineStart() {
        val result = EditorToolbarActions.scheduledTimestamp("Task", EditorSelection(2, 2), "<2026-05-04 Mon>")

        assertThat(result.text, equalTo("SCHEDULED: <2026-05-04 Mon>\nTask"))
        assertThat(result.selection, equalTo(EditorSelection(27, 27)))
    }

    @Test
    fun inlineTimestampReplacesSelection() {
        val result = EditorToolbarActions.inlineTimestamp("abc", EditorSelection(1, 2), "<2026-05-04 Mon>")

        assertThat(result.text, equalTo("a<2026-05-04 Mon>c"))
        assertThat(result.selection, equalTo(EditorSelection(17, 17)))
    }
}
