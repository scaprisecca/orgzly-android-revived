package com.orgzly.android.capture

import android.content.Context
import com.orgzly.R
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.prefs.AppPreferences
import com.orgzly.android.ui.note.NoteBuilder
import com.orgzly.android.ui.note.NotePayload
import com.orgzly.org.OrgProperties
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CaptureTemplate(
    val id: String,
    val labelRes: Int,
) {
    INBOX_TASK("inbox_task", R.string.capture_template_inbox_task),
    REPEATING_CHORE("repeating_chore", R.string.capture_template_repeating_chore),
    MEETING_NOTE("meeting_note", R.string.capture_template_meeting_note),
    LEARNING_NOTE("learning_note", R.string.capture_template_learning_note),
    BUSINESS_IDEA("business_idea", R.string.capture_template_business_idea);

    companion object {
        @JvmStatic
        fun fromId(id: String?): CaptureTemplate? {
            return values().firstOrNull { it.id == id }
        }
    }
}

data class CaptureInput @JvmOverloads constructor(
    val title: String? = null,
    val content: String? = null,
)

object CaptureTemplates {
    @JvmStatic
    fun enabledTemplates(context: Context): List<CaptureTemplate> {
        return CaptureTemplate.values().filter {
            AppPreferences.isCaptureTemplateEnabled(context, it.id)
        }
    }

    @JvmStatic
    fun shareEnabledTemplates(context: Context): List<CaptureTemplate> {
        return enabledTemplates(context).filter {
            AppPreferences.isCaptureTemplateShareEnabled(context, it.id)
        }
    }

    @JvmStatic
    fun buildPayload(
        context: Context,
        template: CaptureTemplate?,
        input: CaptureInput,
    ): NotePayload {
        return if (template == null) {
            NoteBuilder.newPayload(context, input.title.orEmpty(), input.content)
        } else {
            buildTemplatePayload(context, template, input)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun resolveTargetBook(
        dataRepository: DataRepository,
        context: Context,
        template: CaptureTemplate?,
        explicitBookId: Long?,
    ): BookView {
        explicitBookId?.let { bookId ->
            dataRepository.getBookView(bookId)?.let { return it }
        }

        template?.let {
            val notebookName = AppPreferences.captureTemplateNotebook(context, it.id)
            if (!notebookName.isNullOrBlank()) {
                dataRepository.getBookView(notebookName)?.let { book -> return book }
            }
        }

        return dataRepository.getTargetBook(context)
    }

    private fun buildTemplatePayload(
        context: Context,
        template: CaptureTemplate,
        input: CaptureInput,
    ): NotePayload {
        val title = buildTitle(context, template, input)
        val basePayload = NoteBuilder.newPayload(context, title, null)
        val tags = LinkedHashSet(basePayload.tags)
        val properties = OrgProperties().apply {
            basePayload.properties.all.forEach { put(it.name, it.value) }
        }

        when (template) {
            CaptureTemplate.INBOX_TASK -> Unit
            CaptureTemplate.REPEATING_CHORE -> tags.add("chore")
            CaptureTemplate.MEETING_NOTE -> tags.add("meeting")
            CaptureTemplate.LEARNING_NOTE -> tags.add("learning")
            CaptureTemplate.BUSINESS_IDEA -> {
                tags.add("business")
                tags.add("idea")
            }
        }

        val state = when (template) {
            CaptureTemplate.REPEATING_CHORE -> basePayload.state ?: "TODO"
            else -> basePayload.state
        }

        val scheduled = when (template) {
            CaptureTemplate.REPEATING_CHORE -> basePayload.scheduled ?: NoteBuilder.initialScheduledTimeForTemplate(context)
            else -> basePayload.scheduled
        }

        return basePayload.copy(
            title = title,
            content = buildContent(template, input.content),
            state = state,
            scheduled = scheduled,
            tags = tags.toList(),
            properties = properties,
        )
    }

    private fun buildTitle(context: Context, template: CaptureTemplate, input: CaptureInput): String {
        val suppliedTitle = input.title?.takeIf { it.isNotBlank() }
        if (suppliedTitle != null) {
            return suppliedTitle
        }

        return when (template) {
            CaptureTemplate.MEETING_NOTE -> context.getString(
                R.string.capture_template_meeting_title_pattern,
                dateString(),
            )
            else -> ""
        }
    }

    private fun buildContent(template: CaptureTemplate, incomingContent: String?): String? {
        val scaffold = when (template) {
            CaptureTemplate.INBOX_TASK -> null
            CaptureTemplate.REPEATING_CHORE -> "* Notes\n"
            CaptureTemplate.MEETING_NOTE -> """
                * Attendees
                * Agenda
                * Notes
                * Follow-ups
            """.trimIndent()
            CaptureTemplate.LEARNING_NOTE -> """
                * Source
                * Takeaway
                * Follow-up / Review
            """.trimIndent()
            CaptureTemplate.BUSINESS_IDEA -> """
                * Problem
                * Idea
                * Next step
            """.trimIndent()
        }

        val shared = incomingContent?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            scaffold.isNullOrBlank() -> shared
            shared == null -> scaffold
            else -> "$scaffold\n\n$shared"
        }
    }

    private fun dateString(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    }
}
