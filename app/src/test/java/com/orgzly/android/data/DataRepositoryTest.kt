package com.orgzly.android.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.LocalStorage
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.link.OrgRoamLinkTarget
import com.orgzly.android.repos.RepoFactory
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.note.NotePayload
import com.orgzly.android.ui.views.style.IdLinkSpan
import com.orgzly.org.OrgProperties
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DataRepositoryTest {

    private lateinit var context: Context
    private lateinit var dataRepository: DataRepository
    private lateinit var database: OrgzlyDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Set up in-memory database for tests
        database = OrgzlyDatabase.forMemory(context)

        val dbRepoBookRepository = DbRepoBookRepository(database)
        val localStorage = LocalStorage(context)
        val repoFactory = RepoFactory(context, dbRepoBookRepository)

        dataRepository = DataRepository(
            context, database, repoFactory, context.resources, localStorage)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ===== Tests for createBook() path traversal validation =====

    @Test
    fun testCreateBookWithPathTraversalAtStartThrowsException() {
        val exception = assertThrows(IOException::class.java) {
            dataRepository.createBook("../malicious-name")
        }
        assertThat(exception.message, containsString("Book names cannot contain '../'"))
    }

    @Test
    fun testCreateBookWithPathTraversalInMiddleThrowsException() {
        val exception = assertThrows(IOException::class.java) {
            dataRepository.createBook("prefix/../malicious")
        }
        assertThat(exception.message, containsString("Book names cannot contain '../'"))
    }

    @Test
    fun testCreateBookWithPathTraversalAtEndThrowsException() {
        val exception = assertThrows(IOException::class.java) {
            dataRepository.createBook("malicious../")
        }
        assertThat(exception.message, containsString("Book names cannot contain '../'"))
    }

    @Test
    fun testCreateBookWithMultiplePathTraversalsThrowsException() {
        val exception = assertThrows(IOException::class.java) {
            dataRepository.createBook("../../very-malicious")
        }
        assertThat(exception.message, containsString("Book names cannot contain '../'"))
    }

    @Test
    fun testCreateBookWithValidNameSucceeds() {
        // Should not throw
        val book = dataRepository.createBook("valid-book-name")
        assertThat(book.book.name, `is`("valid-book-name"))
    }

    @Test
    fun testCreateBookWithDotsInNameSucceeds() {
        // Single dots and multiple consecutive dots (not "../") should be allowed
        val book = dataRepository.createBook("file.with.dots")
        assertThat(book.book.name, `is`("file.with.dots"))
    }

    @Test
    fun testCreateBookWithConsecutiveDotsSucceeds() {
        // ".." without "/" should be allowed (edge case)
        val book = dataRepository.createBook("file..name")
        assertThat(book.book.name, `is`("file..name"))
    }

    // ===== Tests for renameBook() path traversal validation =====

    @Test
    fun testRenameBookWithPathTraversalGivesError() {
        val book = dataRepository.createBook("valid-book")
        dataRepository.renameBook(book, "../malicious-name")
        assertThat(
            dataRepository.getBook(book.book.id)!!.lastAction!!.message,
            containsString("Book names cannot contain '../'")
        )
    }

    @Test
    fun testRenameBookWithPathTraversalInMiddleGivesError() {
        val book = dataRepository.createBook("valid-book")

        dataRepository.renameBook(book, "prefix/../malicious")
        assertThat(
            dataRepository.getBook(book.book.id)!!.lastAction!!.message,
            containsString("Book names cannot contain '../'")
        )
    }

    @Test
    fun testRenameBookWithPathTraversalAtEndGivesError() {
        val book = dataRepository.createBook("valid-book")

        dataRepository.renameBook(book, "malicious../")
        assertThat(
            dataRepository.getBook(book.book.id)!!.lastAction!!.message,
            containsString("Book names cannot contain '../'")
        )
    }

    @Test
    fun testRenameBookWithMultiplePathTraversalsGivesError() {
        val book = dataRepository.createBook("valid-book")

        dataRepository.renameBook(book, "../../../very-malicious")
        assertThat(
            dataRepository.getBook(book.book.id)!!.lastAction!!.message,
            containsString("Book names cannot contain '../'")
        )
    }

    @Test
    fun testRenameBookWithValidNameSucceeds() {
        val book = dataRepository.createBook("original-name")

        // Should not throw
        dataRepository.renameBook(book, "new-valid-name")
        assertEquals("new-valid-name", dataRepository.getBook(book.book.id)!!.name)
    }

    @Test
    fun testRenameBookWithDotsInNameSucceeds() {
        val book = dataRepository.createBook("original-name")

        dataRepository.renameBook(book, "renamed.with.dots")
        assertEquals("renamed.with.dots", dataRepository.getBook(book.book.id)!!.name)
    }

    @Test
    fun testRenameBookWithConsecutiveDotsSucceeds() {
        val book = dataRepository.createBook("original-name")

        // ".." without "/" should be allowed
        dataRepository.renameBook(book, "renamed..name")
        assertEquals("renamed..name", dataRepository.getBook(book.book.id)!!.name)
    }

    @Test
    fun searchOrgRoamLinkTargetsExcludesNotesWithoutIdsByDefault() {
        val book = dataRepository.createBook("search-book")
        createNote(book.book.id, "Alpha node", mapOf("ID" to "alpha-id"))
        createNote(book.book.id, "Beta node")

        val results = dataRepository.searchOrgRoamLinkTargets("node", includeWithoutIds = false)

        assertEquals(1, results.size)
        assertEquals("Alpha node", results.first().title)
        assertFalse(results.first().requiresIdCreation)
    }

    @Test
    fun searchOrgRoamLinkTargetsIncludesNotesWithoutIdsWhenRequested() {
        val book = dataRepository.createBook("search-book")
        createNote(book.book.id, "Alpha node")

        val results = dataRepository.searchOrgRoamLinkTargets("alpha", includeWithoutIds = true)

        assertEquals(1, results.size)
        assertTrue(results.first().requiresIdCreation)
        assertEquals(OrgRoamLinkTarget.Type.NOTE, results.first().type)
    }

    @Test
    fun searchOrgRoamLinkTargetsMatchesCustomIdAliasesAndBookId() {
        val book = dataRepository.createBook("book-name")
        database.bookProperty().upsert(book.book.id, "ID", "book-id")
        createNote(
            book.book.id,
            "Alpha node",
            mapOf(
                "ID" to "alpha-id",
                "CUSTOM_ID" to "custom-anchor",
                "ROAM_ALIASES" to "Project Alias",
            ),
        )

        assertEquals("Alpha node", dataRepository.searchOrgRoamLinkTargets("custom-anchor", false).first().title)
        assertEquals("Alpha node", dataRepository.searchOrgRoamLinkTargets("project alias", false).first().title)
        assertEquals(OrgRoamLinkTarget.Type.BOOK, dataRepository.searchOrgRoamLinkTargets("book-id", false).first().type)
    }

    @Test
    fun searchOrgRoamLinkTargetsMarksDuplicateIds() {
        val firstBook = dataRepository.createBook("book-one")
        val secondBook = dataRepository.createBook("book-two")
        createNote(firstBook.book.id, "Alpha node", mapOf("ID" to "shared-id"))
        createNote(secondBook.book.id, "Beta node", mapOf("ID" to "shared-id"))

        val results = dataRepository.searchOrgRoamLinkTargets("shared-id", includeWithoutIds = false)

        assertEquals(2, results.size)
        assertTrue(results.all { it.hasDuplicateId })
        assertEquals(2, results.first().duplicateIdCount)
    }

    @Test
    fun ensureNoteIdAddsIdAndMarksBookModified() {
        val book = dataRepository.createBook("book")
        val note = createNote(book.book.id, "Alpha node")

        val id = dataRepository.ensureNoteId(note.id)

        assertNotNull(id)
        val payload = dataRepository.getNotePayload(note.id)
        assertEquals(id, payload!!.properties[IdLinkSpan.PROPERTY])
        assertTrue(dataRepository.getBook(book.book.id)!!.isModified)
    }

    @Test
    fun ensureNoteIdPreservesExistingId() {
        val book = dataRepository.createBook("book")
        val note = createNote(book.book.id, "Alpha node", mapOf("ID" to "existing-id"))

        val id = dataRepository.ensureNoteId(note.id)

        assertEquals("existing-id", id)
        assertEquals(1, database.noteProperty().get(note.id, IdLinkSpan.PROPERTY).size)
    }

    @Test
    fun createLinkedNoteTargetAlwaysGeneratesId() {
        val book = dataRepository.createBook("book")

        val target = dataRepository.createLinkedNoteTarget(book.book.id, "Linked note")

        assertEquals(OrgRoamLinkTarget.Type.NOTE, target.type)
        assertNotNull(target.id)
        assertEquals("Linked note", target.title)
        val payload = dataRepository.getNotePayload(target.noteId!!)
        assertEquals(target.id, payload!!.properties[IdLinkSpan.PROPERTY])
    }

    private fun createNote(bookId: Long, title: String, properties: Map<String, String> = emptyMap()): com.orgzly.android.db.entity.Note {
        val orgProperties = OrgProperties()
        properties.forEach { (name, value) -> orgProperties.put(name, value) }
        return dataRepository.createNote(
            NotePayload(title = title, properties = orgProperties),
            NotePlace(bookId),
        )
    }
}
