package com.orgzly.android.ui.util

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.MultiAutoCompleteTextView
import androidx.annotation.LayoutRes

class CommaSeparatedSuggestionAdapter(
    context: Context,
    @LayoutRes resource: Int,
) : ArrayAdapter<String>(context, resource, mutableListOf<String>()) {
    private val dictionary = mutableListOf<String>()
    private var filtered = emptyList<String>()

    fun updateDictionary(items: List<String>) {
        dictionary.clear()
        dictionary.addAll(items)
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
            val suggestions = CommaSeparatedAutocompleteMatcher.match(dictionary, query)

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

object CommaSeparatedAutocomplete {
    val tokenizer: MultiAutoCompleteTextView.Tokenizer = CommaTokenizer()

    fun currentToken(text: CharSequence, cursor: Int): String {
        val safeCursor = cursor.coerceIn(0, text.length)
        val start = tokenizer.findTokenStart(text, safeCursor)
        val end = tokenizer.findTokenEnd(text, safeCursor)
        return text.subSequence(start, end).toString().trim()
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

internal object CommaSeparatedAutocompleteMatcher {
    fun match(dictionary: List<String>, query: String): List<String> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return dictionary

        return dictionary
            .mapNotNull { item ->
                score(item, normalizedQuery)?.let { score -> item to score }
            }
            .sortedWith(compareBy<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
            .map { it.first }
    }

    private fun score(item: String, normalizedQuery: String): Int? {
        val normalizedItem = item.lowercase()

        return when {
            normalizedItem == normalizedQuery -> 0
            normalizedItem.startsWith(normalizedQuery) -> 1
            normalizedItem.contains(normalizedQuery) -> 2
            isSubsequence(normalizedQuery, normalizedItem) -> 3
            else -> null
        }
    }

    private fun isSubsequence(query: String, item: String): Boolean {
        var itemIndex = 0

        for (queryChar in query) {
            itemIndex = item.indexOf(queryChar, startIndex = itemIndex)
            if (itemIndex == -1) return false
            itemIndex++
        }

        return true
    }
}
