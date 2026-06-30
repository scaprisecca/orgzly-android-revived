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
}
