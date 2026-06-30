package com.orgzly.android.ui.capture

import com.orgzly.android.ui.util.CommaSeparatedAutocomplete

object CaptureTemplateTagInput {
    val tokenizer = CommaSeparatedAutocomplete.tokenizer

    fun normalizeTagsCsv(rawValue: String?): String? {
        val normalized = rawValue
            .orEmpty()
            .split(',')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()

        return normalized.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
}
