package com.orgzly.android.query

enum class AgendaDateSource {
    SCHEDULED,
    DEADLINE,
    EVENT;

    companion object {
        fun defaultSet(): Set<AgendaDateSource> = linkedSetOf(SCHEDULED, DEADLINE, EVENT)
    }
}
