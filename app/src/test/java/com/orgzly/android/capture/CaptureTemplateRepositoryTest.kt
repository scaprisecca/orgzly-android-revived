package com.orgzly.android.capture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.orgzly.android.LocalStorage
import com.orgzly.android.data.DataRepository
import com.orgzly.android.data.DbRepoBookRepository
import com.orgzly.android.db.OrgzlyDatabase
import com.orgzly.android.db.entity.CaptureTemplateEntity
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.repos.RepoFactory
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CaptureTemplateRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbNamesToDelete = mutableListOf<String>()
    private val openDatabases = mutableListOf<OrgzlyDatabase>()

    @After
    fun tearDown() {
        openDatabases.forEach { it.close() }
        dbNamesToDelete.forEach { context.deleteDatabase(it) }
        AppPreferences.setToDefaults(context)
    }

    @Test
    fun freshInstallSeedsBuiltInTemplates() {
        AppPreferences.setToDefaults(context)

        val dataRepository = dataRepositoryWithInMemoryDb()

        val templates = dataRepository.getCaptureTemplates()

        assertThat(templates.size, `is`(CaptureTemplateSeeder.presetSeeds.size))
        assertThat(templates.map { it.id }, `is`(CaptureTemplateSeeder.presetSeeds.map { it.id }))
    }

    @Test
    fun seededTemplatesPreserveLegacyPreferenceValuesWithoutDuplication() {
        AppPreferences.setToDefaults(context)
        AppPreferences.isCaptureTemplateEnabled(context, CaptureTemplate.MEETING_NOTE.id, false)
        AppPreferences.isCaptureTemplateShareEnabled(context, CaptureTemplate.BUSINESS_IDEA.id, false)
        AppPreferences.captureTemplateNotebook(context, CaptureTemplate.LEARNING_NOTE.id, "Learning")

        val dbName = "capture-template-repository-test.db"
        context.deleteDatabase(dbName)
        dbNamesToDelete += dbName

        val firstRepository = dataRepositoryWithFileDb(dbName)
        val firstTemplates = firstRepository.getCaptureTemplates()

        assertThat(firstTemplates.size, `is`(CaptureTemplateSeeder.presetSeeds.size))
        assertThat(firstRepository.getCaptureTemplate(CaptureTemplate.MEETING_NOTE.id)!!.enabled, `is`(false))
        assertThat(firstRepository.getCaptureTemplate(CaptureTemplate.BUSINESS_IDEA.id)!!.shareEnabled, `is`(false))
        assertThat(firstRepository.getCaptureTemplate(CaptureTemplate.LEARNING_NOTE.id)!!.targetNotebookName, `is`("Learning"))

        openDatabases.removeAt(openDatabases.lastIndex).close()

        val secondRepository = dataRepositoryWithFileDb(dbName)
        val secondTemplates = secondRepository.getCaptureTemplates()

        assertThat(secondTemplates.size, `is`(CaptureTemplateSeeder.presetSeeds.size))
        assertThat(secondRepository.getCaptureTemplate(CaptureTemplate.LEARNING_NOTE.id)!!.targetNotebookName, `is`("Learning"))
    }

    @Test
    fun softDeletedBuiltInTemplateStaysDeletedAfterReseed() {
        AppPreferences.setToDefaults(context)

        val dataRepository = dataRepositoryWithInMemoryDb()
        dataRepository.deleteCaptureTemplate(CaptureTemplate.INBOX_TASK.id)

        val db = openDatabases.last()
        CaptureTemplateSeeder.seedMissingTemplates(context, db.openHelper.writableDatabase)

        val deletedTemplate = dataRepository.getCaptureTemplate(CaptureTemplate.INBOX_TASK.id)

        assertNotNull(deletedTemplate)
        assertThat(deletedTemplate!!.deleted, `is`(true))
        assertFalse(dataRepository.getCaptureTemplates().any { it.id == CaptureTemplate.INBOX_TASK.id })
    }

    @Test
    fun createCaptureTemplateAssignsNextPosition() {
        AppPreferences.setToDefaults(context)

        val dataRepository = dataRepositoryWithInMemoryDb()

        dataRepository.createCaptureTemplate(
            CaptureTemplateEntity(
                id = "custom-template",
                name = "Custom template",
                sourceType = CaptureTemplateEntity.SOURCE_TYPE_CUSTOM,
                templateKind = CaptureTemplateEntity.TEMPLATE_KIND_NOTE,
                position = 0,
            )
        )

        val template = dataRepository.getCaptureTemplate("custom-template")

        assertNotNull(template)
        assertThat(template!!.position, `is`(CaptureTemplateSeeder.presetSeeds.size + 1))
    }

    private fun dataRepositoryWithInMemoryDb(): DataRepository {
        val database = OrgzlyDatabase.forMemory(context)
        openDatabases += database
        return dataRepository(database)
    }

    private fun dataRepositoryWithFileDb(name: String): DataRepository {
        val database = OrgzlyDatabase.forFile(context, name)
        openDatabases += database
        return dataRepository(database)
    }

    private fun dataRepository(database: OrgzlyDatabase): DataRepository {
        val dbRepoBookRepository = DbRepoBookRepository(database)
        val localStorage = LocalStorage(context)
        val repoFactory = RepoFactory(context, dbRepoBookRepository)

        return DataRepository(
            context, database, repoFactory, context.resources, localStorage,
        )
    }
}
