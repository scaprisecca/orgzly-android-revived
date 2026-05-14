package com.orgzly.android.savedsearch.builder

import com.orgzly.android.query.AgendaDateSource

data class AgendaViewBuilderState(
    val name: String = "",
    val includeNotebooks: List<String> = emptyList(),
    val excludeNotebooks: List<String> = emptyList(),
    val includeTags: List<String> = emptyList(),
    val excludeTags: List<String> = emptyList(),
    val includeStates: List<String> = emptyList(),
    val excludeStates: List<String> = emptyList(),
    val includeProperties: List<PropertyFilter> = emptyList(),
    val excludeProperties: List<PropertyFilter> = emptyList(),
    val excludeDone: Boolean = true,
    val dateFilter: DateFilter = DateFilter.NONE,
    val dateSources: Set<AgendaDateSource> = AgendaDateSource.defaultSet(),
    val sort: SortPreference = SortPreference.PRIORITY,
    val advancedQuery: String? = null,
) {
    enum class DateFilter {
        NONE,
        TODAY_OVERDUE,
        NEXT_3,
        NEXT_7,
        NEXT_14,
        NEXT_30,
        NO_SCHEDULED_OR_DEADLINE,
        CUSTOM_ADVANCED,
    }

    enum class SortPreference {
        DATE,
        PRIORITY,
        NOTEBOOK,
        EVENT,
    }

    data class PropertyFilter(
        val name: String,
        val operator: Operator,
        val value: String? = null,
    ) {
        enum class Operator {
            EXISTS,
            EQUALS,
        }
    }
}
