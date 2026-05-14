package com.orgzly.android.savedsearch.builder

import com.orgzly.android.query.AgendaDateSource
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class AgendaViewQueryCompilerTest {
    private val compiler = AgendaViewQueryCompiler()

    @Test
    fun compilesNoDatePresetShape() {
        val state = AgendaViewBuilderState(
            name = "No Date",
            excludeDone = true,
            dateFilter = AgendaViewBuilderState.DateFilter.NO_SCHEDULED_OR_DEADLINE,
            sort = AgendaViewBuilderState.SortPreference.PRIORITY,
        )

        assertThat(
            compiler.compileToString(state),
            `is`(".it.done s.none d.none o.p o.b"),
        )
    }

    @Test
    fun compilesTodayOverdueWithExplicitDateSources() {
        val state = AgendaViewBuilderState(
            name = "Today / overdue",
            excludeDone = true,
            dateFilter = AgendaViewBuilderState.DateFilter.TODAY_OVERDUE,
            dateSources = linkedSetOf(AgendaDateSource.SCHEDULED, AgendaDateSource.DEADLINE),
            sort = AgendaViewBuilderState.SortPreference.DATE,
        )

        assertThat(
            compiler.compileToString(state),
            `is`(".it.done (s.today or d.today) o.s o.d o.p ad.1 ads.sd"),
        )
    }
}
