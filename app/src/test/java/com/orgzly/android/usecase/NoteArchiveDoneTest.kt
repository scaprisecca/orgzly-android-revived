package com.orgzly.android.usecase

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.BookFormat
import com.orgzly.android.LocalStorage
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.repos.RepoFactory
import com.orgzly.android.util.MiscUtils
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoteArchiveDoneTest {
    private lateinit var context: Context
    private lateinit var dataRepository: DataRepository
    private lateinit var database: OrgzlyDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = OrgzlyDatabase.forMemory(context)

        val dbRepoBookRepository = DbRepoBookRepository(database)
        val localStorage = LocalStorage(context)
        val repoFactory = RepoFactory(context, dbRepoBookRepository)

        dataRepository = DataRepository(
            context, database, repoFactory, context.resources, localStorage)

        AppPreferences.states(context, "TODO NEXT | DONE")
        AppPreferences.doneArchiveBookId(context, null)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun movesImmediateDoneTopLevelNotesAndSkipsDoneParentsWithActiveDescendants() {
        val source = setupBook(
            "work",
            """* DONE Move me
               |** DONE Moved child
               |* DONE Skip me
               |** TODO Active child
               |* TODO Active parent
               |** DONE Nested done stays put
               |* NOTE Plain note
               |""".trimMargin()
        )
        val destination = dataRepository.createBook("done")
        AppPreferences.doneArchiveBookId(context, destination.book.id)

        val result = NoteArchiveDone(DoneArchiveScope.Book(source.book.id)).run(dataRepository)
            .userData as DoneArchiveResult

        assertEquals(2, result.candidateCount)
        assertEquals(1, result.movedCount)
        assertEquals(1, result.skippedActiveDescendantCount)

        val sourceContent = bookContent("work")
        val destinationContent = bookContent("done")

        assertThat(sourceContent, not(containsString("* DONE Move me")))
        assertThat(sourceContent, containsString("* DONE Skip me"))
        assertThat(sourceContent, containsString("** DONE Nested done stays put"))
        assertThat(destinationContent, containsString("* DONE Move me"))
        assertThat(destinationContent, containsString("** DONE Moved child"))
    }

    @Test
    fun archivesOnlyImmediateDoneChildrenForHeadingScope() {
        val source = setupBook(
            "work",
            """* Parent
               |** DONE Move child
               |*** DONE Grandchild moves with parent
               |** DONE Skip child
               |*** NEXT Active grandchild
               |** TODO Active sibling
               |""".trimMargin()
        )
        val destination = dataRepository.createBook("done")
        AppPreferences.doneArchiveBookId(context, destination.book.id)

        val parentId = dataRepository.getNotesByTitle("Parent").single().id

        val result = NoteArchiveDone(
            DoneArchiveScope.Heading(source.book.id, parentId)
        ).run(dataRepository).userData as DoneArchiveResult

        assertEquals(2, result.candidateCount)
        assertEquals(1, result.movedCount)
        assertEquals(1, result.skippedActiveDescendantCount)

        val sourceContent = bookContent("work")
        val destinationContent = bookContent("done")

        assertThat(sourceContent, containsString("* Parent"))
        assertThat(sourceContent, not(containsString("** DONE Move child")))
        assertThat(sourceContent, containsString("** DONE Skip child"))
        assertThat(destinationContent, containsString("* DONE Move child"))
        assertThat(destinationContent, containsString("** DONE Grandchild moves with parent"))
    }

    @Test
    fun usesConfiguredDoneWorkflowInsteadOfHardCodedDoneKeyword() {
        AppPreferences.states(context, "TODO NEXT | FINISHED")
        val source = setupBook(
            "work",
            """* FINISHED Move me
               |* DONE Leave me
               |""".trimMargin()
        )
        val destination = dataRepository.createBook("archive")
        AppPreferences.doneArchiveBookId(context, destination.book.id)

        val result = NoteArchiveDone(DoneArchiveScope.Book(source.book.id)).run(dataRepository)
            .userData as DoneArchiveResult

        assertEquals(1, result.candidateCount)
        assertEquals(1, result.movedCount)
        assertEquals(0, result.skippedActiveDescendantCount)

        val sourceContent = bookContent("work")
        val destinationContent = bookContent("archive")

        assertThat(sourceContent, containsString("* DONE Leave me"))
        assertThat(destinationContent, containsString("* FINISHED Move me"))
        assertThat(destinationContent, not(containsString("* DONE Leave me")))
    }

    @Test
    fun blocksArchiveWhenDestinationIsMissing() {
        val source = setupBook("work", "* DONE Move me")
        AppPreferences.doneArchiveBookId(context, 9999L)

        val error = assertThrows(NoteArchiveDone.DestinationMissing::class.java) {
            NoteArchiveDone(DoneArchiveScope.Book(source.book.id)).run(dataRepository)
        }

        assertEquals(9999L, error.destinationBookId)
    }

    @Test
    fun blocksArchiveWhenDestinationIsSameBook() {
        val source = setupBook("work", "* DONE Move me")
        AppPreferences.doneArchiveBookId(context, source.book.id)

        val error = assertThrows(NoteArchiveDone.DestinationIsSameBook::class.java) {
            NoteArchiveDone(DoneArchiveScope.Book(source.book.id)).run(dataRepository)
        }

        assertEquals("work", error.destinationBookName)
    }

    @Test
    fun blocksArchiveWhenDestinationIsNotConfigured() {
        val source = setupBook("work", "* DONE Move me")

        assertThrows(NoteArchiveDone.DestinationNotConfigured::class.java) {
            NoteArchiveDone(DoneArchiveScope.Book(source.book.id)).run(dataRepository)
        }
    }

    private fun setupBook(name: String, content: String): BookView {
        val tmpFile = dataRepository.getTempBookFile()
        try {
            MiscUtils.writeStringToFile(content, tmpFile)
            return dataRepository.loadBookFromFile(name, BookFormat.ORG, tmpFile, null)!!
        } catch (e: IOException) {
            throw RuntimeException("Failed to setup book: $name", e)
        } finally {
            tmpFile.delete()
        }
    }

    private fun bookContent(name: String): String {
        return dataRepository.getBookContent(name, BookFormat.ORG).orEmpty()
    }
}
