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

    @Test
    fun compilesDateSourceOnlySearchAsDatePresenceFilter() {
        val state = AgendaViewBuilderState(
            name = "Scheduled only",
            excludeDone = false,
            dateFilter = AgendaViewBuilderState.DateFilter.NONE,
            dateSources = linkedSetOf(AgendaDateSource.SCHEDULED),
            sort = AgendaViewBuilderState.SortPreference.PRIORITY,
        )

        assertThat(
            compiler.compileToString(state),
            `is`("s.ne.none o.p o.b ads.s"),
        )
    }

    @Test
    fun compilesDateSourceOnlySearchWithMultipleSources() {
        val state = AgendaViewBuilderState(
            name = "All dated notes",
            excludeDone = false,
            dateFilter = AgendaViewBuilderState.DateFilter.NONE,
            dateSources = linkedSetOf(AgendaDateSource.SCHEDULED, AgendaDateSource.DEADLINE, AgendaDateSource.EVENT),
            sort = AgendaViewBuilderState.SortPreference.PRIORITY,
        )

        assertThat(
            compiler.compileToString(state),
            `is`("s.ne.none or d.ne.none or e.ne.none o.p o.b"),
        )
    }

    @Test
    fun compilesPropertySearchWithoutImplicitDatePresenceFilter() {
        val state = AgendaViewBuilderState(
            name = "Client",
            excludeDone = false,
            includeProperties = listOf(
                AgendaViewBuilderState.PropertyFilter(
                    name = "client",
                    operator = AgendaViewBuilderState.PropertyFilter.Operator.EQUALS,
                    value = "acme",
                ),
            ),
            dateFilter = AgendaViewBuilderState.DateFilter.NONE,
            dateSources = linkedSetOf(AgendaDateSource.SCHEDULED, AgendaDateSource.DEADLINE, AgendaDateSource.EVENT),
            sort = AgendaViewBuilderState.SortPreference.PRIORITY,
        )

        assertThat(
            compiler.compileToString(state),
            `is`("prop.client=acme o.p o.b"),
        )
    }
}
