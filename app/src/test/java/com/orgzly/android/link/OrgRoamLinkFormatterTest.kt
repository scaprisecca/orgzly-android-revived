package com.orgzly.android.link

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class OrgRoamLinkFormatterTest {
    @Test
    fun formatBuildsStandardIdLink() {
        assertThat(
            OrgRoamLinkFormatter.format("123", "Target"),
            equalTo("[[id:123][Target]]"),
        )
    }

    @Test
    fun normalizeDescriptionCollapsesUnsupportedCharacters() {
        assertThat(
            OrgRoamLinkFormatter.normalizeDescription("Target ]\n  Name"),
            equalTo("Target ) Name"),
        )
    }
}
