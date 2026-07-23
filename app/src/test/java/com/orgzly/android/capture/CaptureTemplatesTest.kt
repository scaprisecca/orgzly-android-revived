package com.orgzly.android.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.R
import com.orgzly.android.LocalStorage
import com.orgzly.android.TestUtils
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.CaptureTemplateEntity
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.repos.RepoFactory
import com.orgzly.android.ui.Place
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureTemplatesTest {
    private lateinit var context: Context
    private lateinit var database: OrgzlyDatabase
    private lateinit var dataRepository: DataRepository
    private lateinit var testUtils: TestUtils

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
        testUtils = TestUtils(dataRepository, dbRepoBookRepository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun meetingTemplatePreservesSharedTitleAndAppendsBody() {
        val payload = CaptureTemplates.buildPayload(
            context,
            template(CaptureTemplate.MEETING_NOTE.id),
            CaptureInput(title = "Design sync", content = "Discuss capture flows"),
        )

        assertThat(payload.title, `is`("Design sync"))
        assertThat(payload.tags, `is`(listOf("meeting")))
        assertThat(payload.content!!, containsString("* Agenda"))
        assertThat(payload.content!!, containsString("Discuss capture flows"))
    }

    @Test
    fun meetingTemplateUsesDatedPresetTitleWhenBlank() {
        val payload = CaptureTemplates.buildPayload(
            context,
            template(CaptureTemplate.MEETING_NOTE.id),
            CaptureInput(content = "Discuss capture flows"),
        )

        val expectedTitle = context.getString(
            R.string.capture_template_meeting_title_pattern,
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
        )

        assertThat(payload.title, `is`(expectedTitle))
    }

    @Test
    fun repeatingChoreTemplateSchedulesByDefault() {
        val payload = CaptureTemplates.buildPayload(
            context,
            template(CaptureTemplate.REPEATING_CHORE.id),
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
            template(CaptureTemplate.INBOX_TASK.id),
            CaptureInput(title = "Buy nails"),
        )

        assertThat(payload.state, `is`("TODO"))
    }

    @Test
    fun templateNotebookWinsOverExplicitBookOverrideDuringRouting() {
        val inboxBook = dataRepository.createBook("Inbox")
        val choresBook = dataRepository.createBook("Chores")
        dataRepository.updateCaptureTemplate(template(CaptureTemplate.REPEATING_CHORE.id).copy(targetNotebookName = "Chores"))

        val resolved = CaptureTemplates.resolveTargetBook(
            dataRepository,
            context,
            template(CaptureTemplate.REPEATING_CHORE.id),
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
            template(CaptureTemplate.INBOX_TASK.id),
            errandsBook.book.id,
        )

        assertThat(resolved.book.id, `is`(errandsBook.book.id))
        assertThat(inboxBook.book.name, `is`("Inbox"))
    }

    @Test
    fun templateNotebookFallbackIsUsedWhenPresent() {
        dataRepository.createBook("Inbox")
        val learningBook = dataRepository.createBook("Learning")
        dataRepository.updateCaptureTemplate(template(CaptureTemplate.LEARNING_NOTE.id).copy(targetNotebookName = "Learning"))

        val resolved = CaptureTemplates.resolveTargetBook(
            dataRepository,
            context,
            template(CaptureTemplate.LEARNING_NOTE.id),
            null,
        )

        assertThat(resolved.book.id, `is`(learningBook.book.id))
    }

    @Test
    fun targetHeadingPathBlankUsesNotebookRoot() {
        val errandsBook = dataRepository.createBook("Errands")
        dataRepository.updateCaptureTemplate(
            template(CaptureTemplate.INBOX_TASK.id).copy(
                targetNotebookName = "Errands",
                targetHeadingPath = "   ",
            ),
        )

        val resolution = CaptureTemplates.resolveTarget(
            dataRepository,
            context,
            template(CaptureTemplate.INBOX_TASK.id),
            null,
        )

        assertThat(resolution.place.bookId, `is`(errandsBook.book.id))
        assertThat(resolution.place.noteId, `is`(0L))
        assertThat(resolution.place.place, `is`(Place.UNSPECIFIED))
        assertThat(resolution.missingHeadingPath, `is`(nullValue()))
    }

    @Test
    fun targetHeadingPathRoutesUnderMatchingTopLevelHeading() {
        val errandsBook = testUtils.setupBook("Errands", "* Home Depot\n")
        val homeDepot = requireNotNull(dataRepository.getNoteAtPath("Errands/Home Depot"))
        dataRepository.updateCaptureTemplate(
            template(CaptureTemplate.INBOX_TASK.id).copy(
                targetNotebookName = "Errands",
                targetHeadingPath = "Home Depot",
            ),
        )

        val resolution = CaptureTemplates.resolveTarget(
            dataRepository,
            context,
            template(CaptureTemplate.INBOX_TASK.id),
            null,
        )

        assertThat(resolution.place.bookId, `is`(errandsBook.book.id))
        assertThat(resolution.place.noteId, `is`(homeDepot.note.id))
        assertThat(resolution.place.place, `is`(Place.UNDER))
        assertThat(resolution.missingHeadingPath, `is`(nullValue()))
    }

    @Test
    fun nestedTargetHeadingPathRoutesUnderMatchingDescendant() {
        val errandsBook = testUtils.setupBook("Errands", "* Shopping\n** Home Depot\n")
        val homeDepot = requireNotNull(dataRepository.getNoteAtPath("Errands/Shopping/Home Depot"))
        dataRepository.updateCaptureTemplate(
            template(CaptureTemplate.INBOX_TASK.id).copy(
                targetNotebookName = "Errands",
                targetHeadingPath = "Shopping/Home Depot",
            ),
        )

        val resolution = CaptureTemplates.resolveTarget(
            dataRepository,
            context,
            template(CaptureTemplate.INBOX_TASK.id),
            null,
        )

        assertThat(resolution.place.bookId, `is`(errandsBook.book.id))
        assertThat(resolution.place.noteId, `is`(homeDepot.note.id))
        assertThat(resolution.place.place, `is`(Place.UNDER))
        assertThat(resolution.missingHeadingPath, `is`(nullValue()))
    }

    @Test
    fun missingTargetHeadingFallsBackToNotebookRootAndReportsMissingPath() {
        val errandsBook = dataRepository.createBook("Errands")
        dataRepository.updateCaptureTemplate(
            template(CaptureTemplate.INBOX_TASK.id).copy(
                targetNotebookName = "Errands",
                targetHeadingPath = "Missing Heading",
            ),
        )

        val resolution = CaptureTemplates.resolveTarget(
            dataRepository,
            context,
            template(CaptureTemplate.INBOX_TASK.id),
            null,
        )

        assertThat(resolution.place.bookId, `is`(errandsBook.book.id))
        assertThat(resolution.place.noteId, `is`(0L))
        assertThat(resolution.place.place, `is`(Place.UNSPECIFIED))
        assertThat(resolution.missingHeadingPath, `is`("Missing Heading"))
    }

    @Test
    fun missingTargetNotebookFallsBackToDefaultNotebookBeforeResolvingHeading() {
        val inboxBook = testUtils.setupBook("Inbox", "* Home Depot\n")
        val homeDepot = requireNotNull(dataRepository.getNoteAtPath("Inbox/Home Depot"))
        dataRepository.updateCaptureTemplate(
            template(CaptureTemplate.INBOX_TASK.id).copy(
                targetNotebookName = "Missing",
                targetHeadingPath = "Home Depot",
            ),
        )

        val resolution = CaptureTemplates.resolveTarget(
            dataRepository,
            context,
            template(CaptureTemplate.INBOX_TASK.id),
            null,
        )

        assertThat(resolution.place.bookId, `is`(inboxBook.book.id))
        assertThat(resolution.place.noteId, `is`(homeDepot.note.id))
        assertThat(resolution.place.place, `is`(Place.UNDER))
        assertThat(resolution.missingHeadingPath, `is`(nullValue()))
    }

    @Test
    fun headingPathNormalizationTrimsEmptySegments() {
        assertThat(
            CaptureTemplates.normalizeHeadingPath("  Shopping // Home Depot / "),
            `is`("Shopping/Home Depot"),
        )
        assertThat(CaptureTemplates.normalizeHeadingPath(" / / "), `is`(nullValue()))
    }

    @Test
    fun fromIdUsesPersistedTemplates() {
        val template = CaptureTemplates.fromId(dataRepository, CaptureTemplate.MEETING_NOTE.id)

        assertThat(template?.id, `is`(CaptureTemplate.MEETING_NOTE.id))
        assertThat(template?.name, `is`(context.getString(CaptureTemplate.MEETING_NOTE.labelRes)))
    }

    @Test
    fun enabledAndShareEnabledTemplatesUsePersistedCatalogFilters() {
        dataRepository.updateCaptureTemplate(template(CaptureTemplate.BUSINESS_IDEA.id).copy(enabled = false))
        dataRepository.updateCaptureTemplate(template(CaptureTemplate.LEARNING_NOTE.id).copy(shareEnabled = false))

        val enabled = CaptureTemplates.enabledTemplates(dataRepository)
        val shareEnabled = CaptureTemplates.shareEnabledTemplates(dataRepository)

        assertThat(enabled.any { it.id == CaptureTemplate.BUSINESS_IDEA.id }, `is`(false))
        assertThat(enabled.any { it.id == CaptureTemplate.LEARNING_NOTE.id }, `is`(true))
        assertThat(shareEnabled.any { it.id == CaptureTemplate.LEARNING_NOTE.id }, `is`(false))
    }

    @Test
    fun customTemplateFieldsMapCleanlyIntoPayload() {
        dataRepository.createCaptureTemplate(
            CaptureTemplateEntity(
                id = "custom-template",
                name = "Custom template",
                sourceType = CaptureTemplateEntity.SOURCE_TYPE_CUSTOM,
                titleTemplate = "Scratchpad",
                bodyTemplate = "* Prompt",
                defaultState = "WAITING",
                tagsCsv = "alpha, beta",
                templateKind = CaptureTemplateEntity.TEMPLATE_KIND_NOTE,
                position = 0,
            ),
        )

        val payload = CaptureTemplates.buildPayload(
            context,
            template("custom-template"),
            CaptureInput(content = "Shared snippet"),
        )

        assertThat(payload.title, `is`("Scratchpad"))
        assertThat(payload.state, `is`("WAITING"))
        assertThat(payload.tags, `is`(listOf("alpha", "beta")))
        assertThat(payload.content, `is`("* Prompt\n\nShared snippet"))
        assertThat(payload.scheduled, `is`(nullValue()))
    }

    @Test
    fun deletedTemplateIdFallsBackSafely() {
        dataRepository.deleteCaptureTemplate(CaptureTemplate.BUSINESS_IDEA.id)

        val deletedTemplate = CaptureTemplates.fromId(dataRepository, CaptureTemplate.BUSINESS_IDEA.id)
        val payload = CaptureTemplates.buildPayload(
            context,
            deletedTemplate,
            CaptureInput(title = "Recovered title", content = "Recovered content"),
        )

        assertThat(deletedTemplate, `is`(nullValue()))
        assertThat(payload.title, `is`("Recovered title"))
        assertThat(payload.content, `is`("Recovered content"))
    }

    private fun template(id: String): CaptureTemplateEntity {
        return requireNotNull(dataRepository.getCaptureTemplate(id))
    }
}
