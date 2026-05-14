package com.orgzly.android.query

data class Options(
    val agendaDays: Int = 0,
    val agendaDateSources: Set<AgendaDateSource> = AgendaDateSource.defaultSet(),
)
