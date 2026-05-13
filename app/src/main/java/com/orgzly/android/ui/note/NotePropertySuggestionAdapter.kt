package com.orgzly.android.ui.note

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.annotation.LayoutRes

class NotePropertySuggestionAdapter(
    context: Context,
    @LayoutRes resource: Int,
): ArrayAdapter<String>(context, resource, mutableListOf<String>()) {

    private val dictionary = mutableListOf<String>()
    private var filtered = FilteredSuggestions.EMPTY

    fun updateDictionary(items: List<String>) {
        dictionary.clear()
        dictionary.addAll(items)
        filtered = FilteredSuggestions.EMPTY
        notifyDataSetChanged()
    }

    override fun getFilter(): Filter {
        return NotePropertySuggestionAdapterFilter()
    }

    override fun getCount(): Int {
        return filtered.suggestions.size
    }

    override fun getItemId(position: Int): Long {
        return filtered.suggestions[position].hashCode().toLong()
    }

    override fun getItem(position: Int): String {
        return (if (filtered.hasLeadingPlus) "+" else "") +
                filtered.suggestions[position]
    }

    inner class NotePropertySuggestionAdapterFilter: Filter() {
        override fun performFiltering(content: CharSequence?): FilterResults {
            if (content == null) return FilterResults().apply {
                count = 0
                values = FilteredSuggestions.EMPTY
            }

            val altered = content.trim('+').toString()
            val filtered = NotePropertyNameMatcher.match(dictionary, altered)

            val results = FilterResults()
            results.values = FilteredSuggestions(
                filtered,
                content.startsWith('+')
            )
            results.count = filtered.size
            return results
        }

        override fun publishResults(
            content: CharSequence?,
            filterResults: FilterResults
        ) {
            filtered = filterResults.values as FilteredSuggestions
            notifyDataSetChanged()
        }
    }

    private data class FilteredSuggestions(
        val suggestions: List<String>,
        val hasLeadingPlus: Boolean
    ) {

        companion object {
            val EMPTY = FilteredSuggestions(
                emptyList(),
                false
            )
        }

    }
}

internal object NotePropertyNameMatcher {
    fun match(dictionary: List<String>, query: String): List<String> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return dictionary

        return dictionary
            .mapNotNull { item ->
                score(item, normalizedQuery)?.let { score -> item to score }
            }
            .sortedWith(
                compareBy<Pair<String, Int>> { it.second }
                    .thenBy { it.first.lowercase() }
            )
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
