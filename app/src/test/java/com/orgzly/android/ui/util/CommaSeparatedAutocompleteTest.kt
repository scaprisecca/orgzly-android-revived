package com.orgzly.android.ui.util

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class CommaSeparatedAutocompleteTest {
    @Test
    fun tokenizerFindsCurrentTokenAfterCommaAndSpace() {
        val text = "alpha, beta,  gam"

        assertThat(CommaSeparatedAutocomplete.tokenizer.findTokenStart(text, text.length), `is`(14))
        assertThat(CommaSeparatedAutocomplete.tokenizer.findTokenEnd(text, 14), `is`(17))
    }

    @Test
    fun tokenizerTerminatesSelectedValueAsCommaSpace() {
        assertThat(CommaSeparatedAutocomplete.tokenizer.terminateToken("meeting"), `is`("meeting, "))
    }

    @Test
    fun matcherRanksExactThenPrefixThenContainsThenSubsequence() {
        val dictionary = listOf("meeting", "meetup", "team-meeting", "marketing")

        assertThat(
            CommaSeparatedAutocompleteMatcher.match(dictionary, "meeting"),
            `is`(listOf("meeting", "team-meeting")),
        )
        assertThat(
            CommaSeparatedAutocompleteMatcher.match(dictionary, "mee"),
            `is`(listOf("meeting", "meetup", "team-meeting")),
        )
        assertThat(
            CommaSeparatedAutocompleteMatcher.match(dictionary, "mkt"),
            `is`(listOf("marketing")),
        )
    }

    @Test
    fun emptyQueryReturnsDictionaryOrder() {
        val dictionary = listOf("zeta", "alpha", "beta")

        assertThat(
            CommaSeparatedAutocompleteMatcher.match(dictionary, ""),
            `is`(dictionary),
        )
    }
}
