package com.orgzly.android.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.LocalStorage
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.repos.RepoFactory
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureTemplatesTest {
    private lateinit var context: Context
    private lateinit var database: OrgzlyDatabase
    private lateinit var dataRepository: DataRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        AppPreferences.setToDefaults(context)

        database = OrgzlyDatabase.forMemory(context)
        val dbRepoBookRepository = DbRepoBookRepository(database)
        val localStorage = LocalStorage(context)
        val repoFactory = RepoFactory(context, dbRepoBookRepository)

        dataRepository = DataRepository(
            context, database, repoFactory, context.resources, localStorage,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun meetingTemplatePreservesSharedTitleAndAppendsBody() {
        val payload = CaptureTemplates.buildPayload(
            context,
            CaptureTemplate.MEETING_NOTE,
            CaptureInput(title = "Design sync", content = "Discuss capture flows"),
        )

        assertThat(payload.title, `is`("Design sync"))
        assertThat(payload.tags, `is`(listOf("meeting")))
        assertThat(payload.content!!, containsString("* Agenda"))
        assertThat(payload.content!!, containsString("Discuss capture flows"))
    }

    @Test
    fun repeatingChoreTemplateSchedulesByDefault() {
        val payload = CaptureTemplates.buildPayload(
            context,
            CaptureTemplate.REPEATING_CHORE,
            CaptureInput(title = "Take out trash"),
        )

        assertThat(payload.tags, `is`(listOf("chore")))
        assertThat(payload.scheduled.isNullOrBlank(), `is`(false))
    }

    @Test
    fun inboxTaskDefaultsToTodoWhenGlobalDefaultStateIsBlank() {
        AppPreferences.newNoteState(context, "")

        val payload = CaptureTemplates.buildPayload(
            context,
            CaptureTemplate.INBOX_TASK,
            CaptureInput(title = "Buy nails"),
        )

        assertThat(payload.state, `is`("TODO"))
    }

    @Test
    fun templateNotebookWinsOverExplicitBookOverrideDuringRouting() {
        val inboxBook = dataRepository.createBook("Inbox")
        val choresBook = dataRepository.createBook("Chores")
        AppPreferences.captureTemplateNotebook(context, CaptureTemplate.REPEATING_CHORE.id, "Chores")

        val resolved = CaptureTemplates.resolveTargetBook(
            dataRepository,
            context,
            CaptureTemplate.REPEATING_CHORE,
            inboxBook.book.id,
        )

        assertThat(resolved.book.id, `is`(choresBook.book.id))
    }

    @Test
    fun explicitBookOverrideIsUsedWhenTemplateHasNoConfiguredNotebook() {
        val inboxBook = dataRepository.createBook("Inbox")
        val errandsBook = dataRepository.createBook("Errands")

        val resolved = CaptureTemplates.resolveTargetBook(
            dataRepository,
            context,
            CaptureTemplate.INBOX_TASK,
            errandsBook.book.id,
        )

        assertThat(resolved.book.id, `is`(errandsBook.book.id))
        assertThat(inboxBook.book.name, `is`("Inbox"))
    }

    @Test
    fun templateNotebookFallbackIsUsedWhenPresent() {
        dataRepository.createBook("Inbox")
        val learningBook = dataRepository.createBook("Learning")
        AppPreferences.captureTemplateNotebook(context, CaptureTemplate.LEARNING_NOTE.id, "Learning")

        val resolved = CaptureTemplates.resolveTargetBook(
            dataRepository,
            context,
            CaptureTemplate.LEARNING_NOTE,
            null,
        )

        assertThat(resolved.book.id, `is`(learningBook.book.id))
    }
}
