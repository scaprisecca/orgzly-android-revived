package com.orgzly.android.data

import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.orgzly.R
import com.orgzly.android.OrgzlyTest
import com.orgzly.android.db.entity.CaptureTemplateEntity
import com.orgzly.android.db.entity.SavedSearch
import com.orgzly.android.prefs.AppPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRepositoryTest : OrgzlyTest() {

    /**
     * If the user attempts to export app settings to a note with a non-unique "ID" value, then
     * - an exception should be thrown
     * - no export should happen
     */
    @Test(expected = RuntimeException::class)
    fun testExportSettingsToNonUniqueNoteId() {
        // Given
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: not-unique-value
                :END:

                content

                * Note 2
                :PROPERTIES:
                :ID: not-unique-value
                :END:

                content

           """.trimIndent()
        )
        assertEquals(2, dataRepository.getNotes("book1").size)
        AppPreferences.settingsExportAndImportNoteId(context, "not-unique-value")
        val targetNote = dataRepository.getNotes("book1")[0].note
        
        // Expect
        try {
            dataRepository.exportSettingsAndSearchesToNote(targetNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("Found multiple"))
            throw e
        } finally {
            assertEquals("content", dataRepository.getNotes("book1")[0].note.content)
            assertEquals("content", dataRepository.getNotes("book1")[1].note.content)
        }
    }

    /**
     * Unknown keys in the JSON blob must be silently ignored during import
     * without causing issues.
     */
    @Test
    fun testImportSettingsWithInvalidEntries() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{"pref_key_states":"NEXT | DONE","invalid_key":"invalid_value"},"saved_searches":{}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        // Check that a setting has its default value
        assertEquals("TODO NEXT | DONE", AppPreferences.states(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // When
        dataRepository.importSettingsAndSearchesFromNote(sourceNote)

        // Expect the ssetting to have changed
        assertEquals("NEXT | DONE", AppPreferences.states(context))
        // Expect searches not to have changed
        assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
    }

    /**
     * An attempt to import completely invalid data must fail gracefully, with no changes.
     */
    @Test(expected = RuntimeException::class)
    fun testImportInvalidSettingsData() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                Sorry, I'm just a little note. I may even look a little bit like
                JSON. {"something":"nothing"}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // Expect
        try {
            dataRepository.importSettingsAndSearchesFromNote(sourceNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("valid JSON"))
            throw e
        } finally {
            // Searches have not changed
            assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
            // Settings have not changed
            assertEquals(settingsBeforeImport, Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context)))
        }
    }

    /**
     * If the "settings" key is missing, no import should happen.
     */
    @Test(expected = RuntimeException::class)
    fun testImportSettingsNoSettingsKey() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"saved_searches":{"Agenda":".it.done ad.7"}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // Expect
        try {
            dataRepository.importSettingsAndSearchesFromNote(sourceNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("missing mandatory fields"))
            throw e
        } finally {
            // Searches have not changed
            assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
            // Settings have not changed
            assertEquals(settingsBeforeImport, Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context)))
        }
    }

    /**
     * If the "searches" key is missing, no import should happen.
     */
    @Test(expected = RuntimeException::class)
    fun testImportSettingsNoSearchesKey() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1",
            """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{"pref_key_states":"NEXT | DONE"}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // Expect
        try {
            dataRepository.importSettingsAndSearchesFromNote(sourceNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("missing mandatory fields"))
            throw e
        } finally {
            // Searches have not changed
            assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
            // Settings have not changed
            assertEquals(settingsBeforeImport, Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context)))
        }
    }

    /**
     * The "settings" and "saved_searches" keys must be present, but they may be empty.
     */

    @Test
    fun testImportSettingsWithSettingsDataWithoutSearchesData() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1", """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{"pref_key_states":"NEXT | DONE"},"saved_searches":{}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        // Check that the setting has the default value
        assertEquals("TODO NEXT | DONE", AppPreferences.states(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // When
        dataRepository.importSettingsAndSearchesFromNote(sourceNote)

        // Expect searches not to have changed
        assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
        // Expect settings to have changed
        assertEquals("NEXT | DONE", AppPreferences.states(context))
    }

    @Test
    fun testImportSettingsWithSearchesDataWithoutSettingsData() {
        // Given
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1", """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{},"saved_searches":{"Agenda":".it.done ad.7"}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        // Assert default number of searches
        assertEquals(4, searchesBeforeImport.size)
        // Store current settings
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // When
        dataRepository.importSettingsAndSearchesFromNote(sourceNote)

        // Then
        // Searches have changed
        assertEquals(1, dataRepository.getSavedSearches().size)
        // Settings have not changed
        assertEquals(settingsBeforeImport, Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context)))
    }

    @Test(expected = RuntimeException::class)
    fun testImportSettingsValidJsonButNoData() {
        val noteId = "my-export-note"
        testUtils.setupBook(
            "book1", """
                * Note 1
                :PROPERTIES:
                :ID: $noteId
                :END:

                {"settings":{},"saved_searches":{}}

           """.trimIndent()
        )
        val searchesBeforeImport = dataRepository.getSavedSearches()
        val settingsBeforeImport = Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
        val sourceNote = dataRepository.getNotes("book1")[0].note

        // Expect
        try {
            dataRepository.importSettingsAndSearchesFromNote(sourceNote)
        } catch (e: java.lang.RuntimeException) {
            assertTrue(e.message!!.contains("Found no settings or saved searches to import"))
            throw e
        } finally {
            // Searches have not changed
            assertEquals(searchesBeforeImport, dataRepository.getSavedSearches())
            // Settings have not changed
            assertEquals(
                settingsBeforeImport,
                Gson().toJson(AppPreferences.getDefaultPrefsAsJsonObject(context))
            )
        }
    }

    @Test
    fun testExportImportSettingsPreservesNewFeatureConfiguration() {
        val doneBook = testUtils.setupBook("done", "")
        AppPreferences.doneArchiveBookId(context, doneBook.book.id)

        val savedSearch = SavedSearch(
            0,
            "Agenda Builder",
            "todo state TODO",
            1,
            "{\"dateSource\":\"scheduled\",\"filters\":[\"home\"]}",
            1,
            "agenda-builder"
        )
        dataRepository.replaceSavedSearches(listOf(savedSearch))
        val savedSearchId = dataRepository.getSavedSearches().single().id
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(R.string.pref_key_calendar_sync_search), savedSearchId.toString())
            .commit()

        val captureTemplate = CaptureTemplateEntity(
            id = "CUSTOM_EXPORT_TEST",
            name = "Inbox task",
            sourceType = CaptureTemplateEntity.SOURCE_TYPE_CUSTOM,
            presetKey = null,
            enabled = true,
            shareEnabled = false,
            targetNotebookName = "inbox",
            titleTemplate = "TODO %title",
            bodyTemplate = "%body\n%url",
            defaultState = "TODO",
            tagsCsv = "home,errand",
            templateKind = CaptureTemplateEntity.TEMPLATE_KIND_TASK,
            position = 7,
            deleted = false
        )
        dataRepository.updateCaptureTemplate(captureTemplate)

        val exportNote = exportSettingsNote()
        dataRepository.exportSettingsAndSearchesToNote(exportNote)
        val exportedJson = dataRepository.getNotes("settings-export").single().note.content!!
        val exported = Gson().fromJson(exportedJson, JsonObject::class.java)
        assertEquals(2, exported.get("version").asInt)
        assertTrue(exported.getAsJsonArray("saved_searches").size() > 0)
        assertTrue(exported.getAsJsonArray("capture_templates").size() > 0)
        assertEquals("done", exported.getAsJsonObject("portable_settings").get("done_archive_notebook_name").asString)
        assertEquals("Agenda Builder", exported.getAsJsonObject("portable_settings").get("calendar_sync_search_name").asString)

        dataRepository.clearDatabase()
        val importedDoneBook = testUtils.setupBook("done", "")
        assertNotEquals(doneBook.book.id, importedDoneBook.book.id)
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putString(context.getString(R.string.pref_key_calendar_sync_search), "-1")
            .commit()
        AppPreferences.doneArchiveBookId(context, null)

        val importNote = importSettingsNote(exportedJson)
        dataRepository.importSettingsAndSearchesFromNote(importNote)

        val importedSearch = dataRepository.getSavedSearches().single { it.name == "Agenda Builder" }
        assertEquals("todo state TODO", importedSearch.query)
        assertEquals("{\"dateSource\":\"scheduled\",\"filters\":[\"home\"]}", importedSearch.builderMetadata)
        assertEquals(1, importedSearch.builderMetadataVersion)
        assertEquals("agenda-builder", importedSearch.presetKey)
        assertEquals(importedSearch.id, AppPreferences.calendarSyncSearchId(context))

        val importedTemplate = dataRepository.getCaptureTemplate("CUSTOM_EXPORT_TEST")!!
        assertEquals(captureTemplate.name, importedTemplate.name)
        assertEquals(captureTemplate.enabled, importedTemplate.enabled)
        assertEquals(captureTemplate.shareEnabled, importedTemplate.shareEnabled)
        assertEquals(captureTemplate.targetNotebookName, importedTemplate.targetNotebookName)
        assertEquals(captureTemplate.titleTemplate, importedTemplate.titleTemplate)
        assertEquals(captureTemplate.bodyTemplate, importedTemplate.bodyTemplate)
        assertEquals(captureTemplate.defaultState, importedTemplate.defaultState)
        assertEquals(captureTemplate.tagsCsv, importedTemplate.tagsCsv)
        assertEquals(captureTemplate.templateKind, importedTemplate.templateKind)
        assertEquals(captureTemplate.position, importedTemplate.position)
        assertEquals(captureTemplate.deleted, importedTemplate.deleted)

        assertEquals(importedDoneBook.book.id, AppPreferences.doneArchiveBookId(context))
    }

    private fun exportSettingsNote() = noteInBook(
        "settings-export",
        "my-export-note",
        "old content"
    )

    private fun importSettingsNote(content: String) = noteInBook(
        "settings-import",
        "my-import-note",
        content
    )

    private fun noteInBook(bookName: String, noteId: String, content: String) = testUtils.setupBook(
        bookName,
        """
            * Note 1
            :PROPERTIES:
            :ID: $noteId
            :END:

            $content

       """.trimIndent()
    ).let { dataRepository.getNotes(bookName)[0].note }
}
