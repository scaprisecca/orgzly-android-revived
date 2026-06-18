package com.orgzly.android.ui.capture

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.MultiAutoCompleteTextView
import androidx.annotation.LayoutRes

class CaptureTemplateTagSuggestionAdapter(
    context: Context,
    @LayoutRes resource: Int,
) : ArrayAdapter<String>(context, resource, mutableListOf<String>()) {
    private val dictionary = mutableListOf<String>()
    private var filtered = emptyList<String>()

    fun updateDictionary(tags: List<String>) {
        dictionary.clear()
        dictionary.addAll(tags)
        filtered = emptyList()
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return SuggestionFilter()
    }

    override fun getCount(): Int {
        return filtered.size
    }

    override fun getItem(position: Int): String {
        return filtered[position]
    }

    override fun getItemId(position: Int): Long {
        return filtered[position].hashCode().toLong()
    }

    private inner class SuggestionFilter : Filter() {
        override fun performFiltering(content: CharSequence?): FilterResults {
            val query = content?.toString().orEmpty()
            val suggestions = CaptureTemplateTagMatcher.match(dictionary, query)

            return FilterResults().apply {
                values = suggestions
                count = suggestions.size
            }
        }

        override fun publishResults(content: CharSequence?, results: FilterResults) {
            @Suppress("UNCHECKED_CAST")
            filtered = results.values as? List<String> ?: emptyList()
            notifyDataSetChanged()
        }
    }
}

object CaptureTemplateTagInput {
    val tokenizer: MultiAutoCompleteTextView.Tokenizer = CommaTokenizer()

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

    private class CommaTokenizer : MultiAutoCompleteTextView.Tokenizer {
        override fun findTokenStart(text: CharSequence, cursor: Int): Int {
            var index = cursor

            while (index > 0 && text[index - 1] != ',') {
                index--
            }

            while (index < cursor && text[index] == ' ') {
                index++
            }

            return index
        }

        override fun findTokenEnd(text: CharSequence, cursor: Int): Int {
            var index = cursor

            while (index < text.length) {
                if (text[index] == ',') {
                    return index
                }
                index++
            }

            return text.length
        }

        override fun terminateToken(text: CharSequence): CharSequence {
            var index = text.length

            while (index > 0 && text[index - 1].isWhitespace()) {
                index--
            }

            val trimmed = text.subSequence(0, index)
            if (trimmed.isNotEmpty() && trimmed.last() == ',') {
                return trimmed
            }

            return if (text is Spanned) {
                SpannableString("$trimmed, ").also { spannable ->
                    TextUtils.copySpansFrom(text, 0, trimmed.length, Any::class.java, spannable, 0)
                }
            } else {
                "$trimmed, "
            }
        }
    }
}

internal object CaptureTemplateTagMatcher {
    fun match(dictionary: List<String>, query: String): List<String> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return dictionary

        return dictionary
            .mapNotNull { tag ->
                score(tag, normalizedQuery)?.let { score -> tag to score }
            }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
            .map { it.first }
    }

    private fun score(tag: String, normalizedQuery: String): Int? {
        val normalizedTag = tag.lowercase()

        return when {
            normalizedTag == normalizedQuery -> 0
            normalizedTag.startsWith(normalizedQuery) -> 1
            normalizedTag.contains(normalizedQuery) -> 2
            isSubsequence(normalizedQuery, normalizedTag) -> 3
            else -> null
        }
    }

    private fun isSubsequence(query: String, tag: String): Boolean {
        var tagIndex = 0

        for (queryChar in query) {
            tagIndex = tag.indexOf(queryChar, startIndex = tagIndex)
            if (tagIndex == -1) return false
            tagIndex++
        }

        return true
    }
}
