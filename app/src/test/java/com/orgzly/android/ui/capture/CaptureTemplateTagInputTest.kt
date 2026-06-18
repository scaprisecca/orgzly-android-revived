package com.orgzly.android.ui.capture

import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class CaptureTemplateTagInputTest {
    @Test
    fun tokenizerFindsCurrentTokenAfterCommaAndSpace() {
        val text = "alpha, beta,  gam"

        assertThat(CaptureTemplateTagInput.tokenizer.findTokenStart(text, text.length), `is`(14))
        assertThat(CaptureTemplateTagInput.tokenizer.findTokenEnd(text, 14), `is`(17))
    }

    @Test
    fun tokenizerTerminatesSelectedTagAsCommaSpace() {
        assertThat(CaptureTemplateTagInput.tokenizer.terminateToken("meeting"), `is`("meeting, "))
    }

    @Test
    fun normalizationTrimsBlanksAndDeduplicatesPreservingOrder() {
        assertThat(
            CaptureTemplateTagInput.normalizeTagsCsv(" alpha, beta,, alpha "),
            `is`("alpha, beta"),
        )
    }

    @Test
    fun normalizationReturnsNullWhenNoTagsRemain() {
        assertThat(CaptureTemplateTagInput.normalizeTagsCsv(" ,  , "), `is`(null as String?))
    }

    @Test
    fun matcherRanksExactThenPrefixThenContainsThenSubsequence() {
        val dictionary = listOf("meeting", "meetup", "team-meeting", "marketing")

        assertThat(
            CaptureTemplateTagMatcher.match(dictionary, "meeting"),
            `is`(listOf("meeting", "team-meeting")),
        )
        assertThat(
            CaptureTemplateTagMatcher.match(dictionary, "mee"),
            `is`(listOf("meeting", "meetup", "team-meeting")),
        )
        assertThat(
            CaptureTemplateTagMatcher.match(dictionary, "mkt"),
            `is`(listOf("marketing")),
        )
    }
}
