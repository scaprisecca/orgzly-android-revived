package com.orgzly.android.savedsearch.builder

import com.orgzly.android.query.Condition
import com.orgzly.android.query.AgendaDateSource
import com.orgzly.android.query.Query
import com.orgzly.android.query.QueryInterval
import com.orgzly.android.query.Relation
import com.orgzly.android.query.SortOrder
import com.orgzly.android.query.StateType
import com.orgzly.android.query.user.DottedQueryBuilder
import com.orgzly.android.query.user.InternalQueryParser

class AgendaViewQueryCompiler(
    private val dottedQueryBuilder: DottedQueryBuilder = DottedQueryBuilder(),
    private val parser: InternalQueryParser = InternalQueryParser(),
) {
    fun compile(state: AgendaViewBuilderState): Query {
        val conditionParts = mutableListOf<Condition>()

        addGroup(conditionParts, state.includeNotebooks) { Condition.InBook(it) }
        addGroup(conditionParts, state.excludeNotebooks) { Condition.InBook(it, not = true) }
        addGroup(conditionParts, state.includeTags) { Condition.HasTag(it) }
        addGroup(conditionParts, state.excludeTags) { Condition.HasTag(it, not = true) }
        addGroup(conditionParts, state.includeStates) { Condition.HasState(it) }
        addGroup(conditionParts, state.excludeStates) { Condition.HasState(it, not = true) }
        addPropertyGroup(conditionParts, state.includeProperties, false)
        addPropertyGroup(conditionParts, state.excludeProperties, true)

        if (state.excludeDone) {
            conditionParts.add(Condition.HasStateType(StateType.DONE, not = true))
        }

        compileDateFilter(state)?.let(conditionParts::add)

        val baseCondition = conditionParts.toCondition()
        val baseQuery = Query(
            condition = baseCondition,
            sortOrders = compileSortOrders(state.sort),
            options = compileOptions(state),
        )

        val advancedQuery = state.advancedQuery?.trim().orEmpty()
        if (advancedQuery.isBlank()) {
            return baseQuery
        }

        val parsedAdvanced = parser.parse(advancedQuery)
        val mergedCondition = listOfNotNull(baseQuery.condition, parsedAdvanced.condition).toCondition()

        return Query(
            condition = mergedCondition,
            sortOrders = if (parsedAdvanced.sortOrders.isNotEmpty()) parsedAdvanced.sortOrders else baseQuery.sortOrders,
            options = if (parsedAdvanced.options != com.orgzly.android.query.Options()) parsedAdvanced.options else baseQuery.options,
        )
    }

    fun compileToString(state: AgendaViewBuilderState): String {
        return dottedQueryBuilder.build(compile(state))
    }

    private fun compileOptions(state: AgendaViewBuilderState): com.orgzly.android.query.Options {
        val agendaDays = when (state.dateFilter) {
            AgendaViewBuilderState.DateFilter.TODAY_OVERDUE -> 1
            AgendaViewBuilderState.DateFilter.NEXT_3 -> 3
            AgendaViewBuilderState.DateFilter.NEXT_7 -> 7
            AgendaViewBuilderState.DateFilter.NEXT_14 -> 14
            AgendaViewBuilderState.DateFilter.NEXT_30 -> 30
            else -> 0
        }

        return com.orgzly.android.query.Options(
            agendaDays = agendaDays,
            agendaDateSources = state.dateSources,
        )
    }

    private fun compileSortOrders(sort: AgendaViewBuilderState.SortPreference): List<SortOrder> {
        return when (sort) {
            AgendaViewBuilderState.SortPreference.DATE -> listOf(SortOrder.Scheduled(), SortOrder.Deadline(), SortOrder.Priority())
            AgendaViewBuilderState.SortPreference.PRIORITY -> listOf(SortOrder.Priority(), SortOrder.Book())
            AgendaViewBuilderState.SortPreference.NOTEBOOK -> listOf(SortOrder.Book(), SortOrder.Priority())
            AgendaViewBuilderState.SortPreference.EVENT -> listOf(SortOrder.Event())
        }
    }

    private fun compileDateFilter(state: AgendaViewBuilderState): Condition? {
        return when (state.dateFilter) {
            AgendaViewBuilderState.DateFilter.NONE -> dateSourceExistenceFilter(state.dateSources)
            AgendaViewBuilderState.DateFilter.CUSTOM_ADVANCED -> null
            AgendaViewBuilderState.DateFilter.TODAY_OVERDUE -> dateSourceFilter("today", state.dateSources)
            AgendaViewBuilderState.DateFilter.NEXT_3 -> dateSourceFilter("3d", state.dateSources)
            AgendaViewBuilderState.DateFilter.NEXT_7 -> dateSourceFilter("7d", state.dateSources)
            AgendaViewBuilderState.DateFilter.NEXT_14 -> dateSourceFilter("14d", state.dateSources)
            AgendaViewBuilderState.DateFilter.NEXT_30 -> dateSourceFilter("30d", state.dateSources)
            AgendaViewBuilderState.DateFilter.NO_SCHEDULED_OR_DEADLINE -> Condition.And(
                listOf(
                    Condition.Scheduled(requireInterval("none"), Relation.LE),
                    Condition.Deadline(requireInterval("none"), Relation.LE),
                ),
            )
        }
    }

    private fun dateSourceFilter(intervalValue: String, dateSources: Set<AgendaDateSource>): Condition? {
        val interval = requireInterval(intervalValue)
        val conditions = buildList {
            if (dateSources.contains(AgendaDateSource.SCHEDULED)) add(Condition.Scheduled(interval, Relation.LE))
            if (dateSources.contains(AgendaDateSource.DEADLINE)) add(Condition.Deadline(interval, Relation.LE))
            if (dateSources.contains(AgendaDateSource.EVENT)) add(Condition.Event(interval, Relation.LE))
        }

        return when (conditions.size) {
            0 -> null
            1 -> conditions.first()
            else -> Condition.Or(conditions)
        }
    }

    private fun dateSourceExistenceFilter(dateSources: Set<AgendaDateSource>): Condition? {
        val none = requireInterval("none")
        val conditions = buildList {
            if (dateSources.contains(AgendaDateSource.SCHEDULED)) add(Condition.Scheduled(none, Relation.NE))
            if (dateSources.contains(AgendaDateSource.DEADLINE)) add(Condition.Deadline(none, Relation.NE))
            if (dateSources.contains(AgendaDateSource.EVENT)) add(Condition.Event(none, Relation.NE))
        }

        return when (conditions.size) {
            0 -> null
            1 -> conditions.first()
            else -> Condition.Or(conditions)
        }
    }

    private fun requireInterval(value: String): QueryInterval {
        return QueryInterval.parse(value) ?: error("Failed to build interval for $value")
    }

    private fun addPropertyGroup(
        conditions: MutableList<Condition>,
        properties: List<AgendaViewBuilderState.PropertyFilter>,
        not: Boolean,
    ) {
        addGroup(conditions, properties) { property ->
            when (property.operator) {
                AgendaViewBuilderState.PropertyFilter.Operator.EXISTS ->
                    Condition.HasProperty(property.name, not = not)
                AgendaViewBuilderState.PropertyFilter.Operator.EQUALS ->
                    Condition.HasProperty(property.name, property.value.orEmpty(), not = not)
            }
        }
    }

    private fun <T> addGroup(
        conditions: MutableList<Condition>,
        values: List<T>,
        mapper: (T) -> Condition,
    ) {
        if (values.isEmpty()) {
            return
        }

        val mapped = values.map(mapper)
        conditions.add(if (mapped.size == 1) mapped.first() else Condition.Or(mapped))
    }

    private fun List<Condition>.toCondition(): Condition? {
        return when (size) {
            0 -> null
            1 -> first()
            else -> Condition.And(this)
        }
    }
}
