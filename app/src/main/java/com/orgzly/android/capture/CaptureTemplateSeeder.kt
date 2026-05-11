package com.orgzly.android.capture

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.orgzly.android.db.entity.CaptureTemplateEntity
import com.orgzly.android.prefs.AppPreferences

data class CaptureTemplateSeed(
    val id: String,
    val labelRes: Int,
    val templateKind: String,
    val defaultState: String? = null,
    val tagsCsv: String? = null,
    val bodyTemplate: String? = null,
)

object CaptureTemplateSeeder {
    val presetSeeds = listOf(
        CaptureTemplateSeed(
            id = CaptureTemplate.INBOX_TASK.id,
            labelRes = CaptureTemplate.INBOX_TASK.labelRes,
            templateKind = CaptureTemplateEntity.TEMPLATE_KIND_TASK,
            defaultState = "TODO",
        ),
        CaptureTemplateSeed(
            id = CaptureTemplate.REPEATING_CHORE.id,
            labelRes = CaptureTemplate.REPEATING_CHORE.labelRes,
            templateKind = CaptureTemplateEntity.TEMPLATE_KIND_TASK,
            defaultState = "TODO",
            tagsCsv = "chore",
            bodyTemplate = "* Notes\n",
        ),
        CaptureTemplateSeed(
            id = CaptureTemplate.MEETING_NOTE.id,
            labelRes = CaptureTemplate.MEETING_NOTE.labelRes,
            templateKind = CaptureTemplateEntity.TEMPLATE_KIND_NOTE,
            tagsCsv = "meeting",
            bodyTemplate = """
                * Attendees
                * Agenda
                * Notes
                * Follow-ups
            """.trimIndent(),
        ),
        CaptureTemplateSeed(
            id = CaptureTemplate.LEARNING_NOTE.id,
            labelRes = CaptureTemplate.LEARNING_NOTE.labelRes,
            templateKind = CaptureTemplateEntity.TEMPLATE_KIND_NOTE,
            tagsCsv = "learning",
            bodyTemplate = """
                * Source
                * Takeaway
                * Follow-up / Review
            """.trimIndent(),
        ),
        CaptureTemplateSeed(
            id = CaptureTemplate.BUSINESS_IDEA.id,
            labelRes = CaptureTemplate.BUSINESS_IDEA.labelRes,
            templateKind = CaptureTemplateEntity.TEMPLATE_KIND_NOTE,
            tagsCsv = "business,idea",
            bodyTemplate = """
                * Problem
                * Idea
                * Next step
            """.trimIndent(),
        ),
    )

    fun seedMissingTemplates(context: Context, db: SupportSQLiteDatabase) {
        presetSeeds.forEachIndexed { index, seed ->
            if (hasTemplate(db, seed.id)) {
                return@forEachIndexed
            }

            val values = ContentValues().apply {
                put("id", seed.id)
                put("name", context.getString(seed.labelRes))
                put("source_type", CaptureTemplateEntity.SOURCE_TYPE_BUILT_IN)
                put("preset_key", seed.id)
                put("enabled", AppPreferences.isCaptureTemplateEnabled(context, seed.id))
                put("share_enabled", AppPreferences.isCaptureTemplateShareEnabled(context, seed.id))
                put("target_notebook_name", AppPreferences.captureTemplateNotebook(context, seed.id))
                putNull("title_template")
                put("body_template", seed.bodyTemplate)
                put("default_state", seed.defaultState)
                put("tags_csv", seed.tagsCsv)
                put("template_kind", seed.templateKind)
                put("position", index + 1)
                put("deleted", false)
            }

            db.insert("capture_templates", SQLiteDatabase.CONFLICT_ABORT, values)
        }
    }

    private fun hasTemplate(db: SupportSQLiteDatabase, id: String): Boolean {
        db.query("SELECT id FROM capture_templates WHERE id = ?", arrayOf<Any>(id)).use { cursor ->
            return cursor.moveToFirst()
        }
    }
}
