package com.orgzly.android.link

import com.orgzly.android.ui.views.style.IdLinkSpan

object OrgRoamLinkFormatter {
    fun format(id: String, description: String): String {
        return "[[${IdLinkSpan.PREFIX}$id][${normalizeDescription(description)}]]"
    }

    fun normalizeDescription(description: String): String {
        return description
            .replace('\n', ' ')
            .replace(']', ')')
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
