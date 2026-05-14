package com.orgzly.android.savedsearch

import com.orgzly.android.savedsearch.builder.AgendaViewBuilderState
import com.orgzly.android.savedsearch.builder.AgendaViewBuilderState.DateFilter
import com.orgzly.android.savedsearch.builder.AgendaViewBuilderState.SortPreference
import com.orgzly.android.query.AgendaDateSource

data class AgendaPresetSeed(
    val presetKey: String,
    val name: String,
    val state: AgendaViewBuilderState,
)

object AgendaPresetSeeds {
    val all = listOf(
        AgendaPresetSeed(
            presetKey = "today_overdue",
            name = "Today / overdue",
            state = AgendaViewBuilderState(
                name = "Today / overdue",
                excludeDone = true,
                dateFilter = DateFilter.TODAY_OVERDUE,
                dateSources = linkedSetOf(AgendaDateSource.SCHEDULED, AgendaDateSource.DEADLINE),
                sort = SortPreference.DATE,
            ),
        ),
        AgendaPresetSeed(
            presetKey = "next_7_days",
            name = "Next 7 days",
            state = AgendaViewBuilderState(
                name = "Next 7 days",
                excludeDone = true,
                dateFilter = DateFilter.NEXT_7,
                dateSources = linkedSetOf(AgendaDateSource.SCHEDULED, AgendaDateSource.DEADLINE),
                sort = SortPreference.DATE,
            ),
        ),
        AgendaPresetSeed(
            presetKey = "no_date",
            name = "No Date",
            state = AgendaViewBuilderState(
                name = "No Date",
                excludeDone = true,
                dateFilter = DateFilter.NO_SCHEDULED_OR_DEADLINE,
                sort = SortPreference.PRIORITY,
            ),
        ),
        AgendaPresetSeed(
            presetKey = "waiting",
            name = "Waiting",
            state = AgendaViewBuilderState(
                name = "Waiting",
                excludeDone = true,
                sort = SortPreference.NOTEBOOK,
                advancedQuery = "(i.WAITING or t.waiting)",
            ),
        ),
        AgendaPresetSeed(
            presetKey = "calendar",
            name = "Calendar",
            state = AgendaViewBuilderState(
                name = "Calendar",
                includeNotebooks = listOf("calendar.org"),
                excludeDone = true,
                dateFilter = DateFilter.NEXT_7,
                dateSources = linkedSetOf(AgendaDateSource.EVENT),
                sort = SortPreference.EVENT,
            ),
        ),
        AgendaPresetSeed(
            presetKey = "home",
            name = "Home",
            state = AgendaViewBuilderState(
                name = "Home",
                excludeDone = true,
                sort = SortPreference.PRIORITY,
                advancedQuery = "(t.home or b.home.org)",
            ),
        ),
        AgendaPresetSeed(
            presetKey = "business",
            name = "Business",
            state = AgendaViewBuilderState(
                name = "Business",
                excludeDone = true,
                sort = SortPreference.PRIORITY,
                advancedQuery = "(t.business or b.business.org)",
            ),
        ),
        AgendaPresetSeed(
            presetKey = "recurring_chores",
            name = "Recurring chores",
            state = AgendaViewBuilderState(
                name = "Recurring chores",
                includeNotebooks = listOf("routines.org"),
                excludeDone = true,
                sort = SortPreference.PRIORITY,
            ),
        ),
    )
}
