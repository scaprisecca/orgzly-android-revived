package com.orgzly.android.query.user

import com.orgzly.android.query.*

open class BasicQueryParser : QueryParser() {
    override val groupOpen   = "("
    override val groupClose  = ")"

    override val logicalAnd = listOf("and", "AND", "&")
    override val logicalOr = listOf("or", "OR", "|")

    override val conditions = listOf(
            ConditionMatch("""^(-)?book:(.+)""") { match ->
                Condition.InBook(unQuote(match.groupValues[2]), match.groupValues[1].isNotEmpty())
            },

            ConditionMatch("""^(-)?state:(.+)""") { match ->
                Condition.HasState(unQuote(match.groupValues[2]), match.groupValues[1].isNotEmpty())
            },

            ConditionMatch("""^(-)?state-type:(todo|done|none)""") { match ->
                val stateType = StateType.valueOf(match.groupValues[2].uppercase())
                Condition.HasStateType(stateType, match.groupValues[1].isNotEmpty())
            },

            ConditionMatch("""^(-)?priority:([a-zA-Z])""") { match ->
                Condition.HasPriority(match.groupValues[2], match.groupValues[1].isNotEmpty())
            },

            ConditionMatch("""^(-)?set-priority:([a-zA-Z])""") { match ->
                Condition.HasSetPriority(match.groupValues[2], match.groupValues[1].isNotEmpty())
            },

            ConditionMatch("""^(-)?tag:(.+)""") { match ->
                Condition.HasTag(unQuote(match.groupValues[2]), match.groupValues[1].isNotEmpty())
            },

            ConditionMatch("""^(-)?own-tag:(.+)""") { match ->
                Condition.HasOwnTag(unQuote(match.groupValues[2]), match.groupValues[1].isNotEmpty())
            },

            ConditionMatch("""^(-)?property:(.+)""") { match ->
                parsePropertyCondition(match.groupValues[2], match.groupValues[1].isNotEmpty())
            },

            ConditionMatch("""^(scheduled|deadline|closed|created):(?:(!=|<|<=|>|>=))?(.+)""") { match ->
                val timeTypeMatch = match.groupValues[1]
                val relationMatch = match.groupValues[2]
                val intervalMatch = match.groupValues[3]

                val relation = when (relationMatch) {
                    "!=" -> Relation.NE
                    "<"  -> Relation.LT
                    "<=" -> Relation.LE
                    ">"  -> Relation.GT
                    ">=" -> Relation.GE
                    else -> Relation.EQ // Default if there is no relation
                }

                val interval = QueryInterval.parse(unQuote(intervalMatch))

                if (interval != null) {
                    when (timeTypeMatch) {
                        "deadline" -> Condition.Deadline(interval, relation)
                        "closed"   -> Condition.Closed(interval, relation)
                        "created"  -> Condition.Created(interval, relation)
                        else       -> Condition.Scheduled(interval, relation)
                    }
                } else {
                    null // Ignore this match
                }
            }
    )


    override val sortOrders = listOf(
            SortOrderMatch("""^(-)?sort-order:(?:notebook|book)$""") { match ->
                SortOrder.Book(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:title)$""") { match ->
                SortOrder.Title(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:scheduled|sched)$""") { match ->
                SortOrder.Scheduled(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:deadline|dead)$""") { match ->
                SortOrder.Deadline(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:event)$""") { match ->
                SortOrder.Event(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:closed|close)$""") { match ->
                SortOrder.Closed(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:created)$""") { match ->
                SortOrder.Created(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:priority|prio)$""") { match ->
                SortOrder.Priority(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:state|st)$""") { match ->
                SortOrder.State(match.groupValues[1].isNotEmpty())
            },
            SortOrderMatch("""^(-)?sort-order:(?:position|pos)$""") { match ->
                SortOrder.Position(match.groupValues[1].isNotEmpty())
            }
    )

    override val supportedOptions = listOf(
            OptionMatch("""^agenda-days:(\d+)$""") { match, options ->
                val days = match.groupValues[1].toInt()
                if (days > 0) options.copy(agendaDays = days) else null
            },
            OptionMatch("""^agenda-date-sources:(.+)$""") { match, options ->
                val sources = parseAgendaDateSources(match.groupValues[1])
                if (sources.isNotEmpty()) options.copy(agendaDateSources = sources) else null
            }
    )

    private fun parsePropertyCondition(raw: String, not: Boolean): Condition.HasProperty {
        val parts = raw.split("=", limit = 2)
        val name = unQuote(parts[0])
        val value = parts.getOrNull(1)?.let(::unQuote)
        return Condition.HasProperty(name, value, not)
    }

    private fun parseAgendaDateSources(raw: String): Set<AgendaDateSource> {
        val result = linkedSetOf<AgendaDateSource>()
        raw.split(",").map { it.trim().lowercase() }.forEach { token ->
            when (token) {
                "scheduled" -> result.add(AgendaDateSource.SCHEDULED)
                "deadline" -> result.add(AgendaDateSource.DEADLINE)
                "event" -> result.add(AgendaDateSource.EVENT)
            }
        }
        return result
    }
}
