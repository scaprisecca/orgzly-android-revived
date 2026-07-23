package com.orgzly.android.capture

import android.content.Context
import com.orgzly.R
import com.orgzly.android.data.DataRepository
import com.orgzly.android.db.entity.BookView
import com.orgzly.android.db.entity.CaptureTemplateEntity
import com.orgzly.android.ui.NotePlace
import com.orgzly.android.ui.Place
import com.orgzly.android.ui.note.NoteBuilder
import com.orgzly.android.ui.note.NotePayload
import com.orgzly.org.OrgProperties
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun captureTemplateDateString(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
}

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

        @JvmStatic
        fun fromPresetKey(presetKey: String?): CaptureTemplate? {
            return fromId(presetKey)
        }
    }

    fun defaultTitle(context: Context, configuredTitle: String?): String {
        if (!configuredTitle.isNullOrBlank()) {
            return configuredTitle
        }

        return when (this) {
            MEETING_NOTE -> context.getString(
                R.string.capture_template_meeting_title_pattern,
                captureTemplateDateString(),
            )
            else -> ""
        }
    }

    fun resolveState(baseState: String?, configuredState: String?): String? {
        return when (this) {
            INBOX_TASK, REPEATING_CHORE -> configuredState ?: baseState ?: "TODO"
            else -> configuredState ?: baseState
        }
    }

    fun resolveScheduled(context: Context, baseScheduled: String?): String? {
        return when (this) {
            REPEATING_CHORE -> baseScheduled ?: NoteBuilder.initialScheduledTimeForTemplate(context)
            else -> baseScheduled
        }
    }
}

data class CaptureInput @JvmOverloads constructor(
    val title: String? = null,
    val content: String? = null,
)

data class CaptureTargetResolution(
    val place: NotePlace,
    val resolvedBook: BookView,
    val missingHeadingPath: String? = null,
)

object CaptureTemplates {
    @JvmStatic
    fun enabledTemplates(dataRepository: DataRepository): List<CaptureTemplateEntity> {
        return dataRepository.getEnabledCaptureTemplates()
    }

    @JvmStatic
    fun shareEnabledTemplates(dataRepository: DataRepository): List<CaptureTemplateEntity> {
        return dataRepository.getShareEnabledCaptureTemplates()
    }

    @JvmStatic
    fun fromId(dataRepository: DataRepository, id: String?): CaptureTemplateEntity? {
        if (id == null) {
            return null
        }

        return dataRepository.getCaptureTemplate(id)?.takeUnless { it.deleted }
    }

    @JvmStatic
    fun normalizeHeadingPath(raw: String?): String? {
        return raw
            ?.split("/")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString("/")
            ?.takeIf { it.isNotEmpty() }
    }

    @JvmStatic
    fun buildPayload(
        context: Context,
        template: CaptureTemplateEntity?,
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
        template: CaptureTemplateEntity?,
        explicitBookId: Long?,
    ): BookView {
        template?.let {
            val notebookName = it.targetNotebookName
            if (!notebookName.isNullOrBlank()) {
                dataRepository.getBookView(notebookName)?.let { book -> return book }
            }
        }

        explicitBookId?.let { bookId ->
            dataRepository.getBookView(bookId)?.let { return it }
        }

        return dataRepository.getTargetBook(context)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun resolveTarget(
        dataRepository: DataRepository,
        context: Context,
        template: CaptureTemplateEntity?,
        explicitBookId: Long?,
    ): CaptureTargetResolution {
        val targetBook = resolveTargetBook(dataRepository, context, template, explicitBookId)
        val headingPath = normalizeHeadingPath(template?.targetHeadingPath)

        if (headingPath == null) {
            return CaptureTargetResolution(
                place = NotePlace(targetBook.book.id),
                resolvedBook = targetBook,
            )
        }

        val targetHeading = dataRepository.getNoteAtPath("${targetBook.book.name}/$headingPath")

        return if (targetHeading != null) {
            CaptureTargetResolution(
                place = NotePlace(targetBook.book.id, targetHeading.note.id, Place.UNDER),
                resolvedBook = targetBook,
            )
        } else {
            CaptureTargetResolution(
                place = NotePlace(targetBook.book.id),
                resolvedBook = targetBook,
                missingHeadingPath = headingPath,
            )
        }
    }

    private fun buildTemplatePayload(
        context: Context,
        template: CaptureTemplateEntity,
        input: CaptureInput,
    ): NotePayload {
        val preset = CaptureTemplate.fromPresetKey(template.presetKey)
        val title = buildTitle(context, template, input, preset)
        val basePayload = NoteBuilder.newPayload(context, title, null)
        val tags = LinkedHashSet(basePayload.tags)
        val properties = OrgProperties().apply {
            basePayload.properties.all.forEach { put(it.name, it.value) }
        }

        template.tagsCsv
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.forEach(tags::add)

        val state = when {
            preset != null -> preset.resolveState(basePayload.state, template.defaultState)
            template.templateKind == CaptureTemplateEntity.TEMPLATE_KIND_TASK -> {
                template.defaultState ?: basePayload.state ?: "TODO"
            }
            else -> template.defaultState ?: basePayload.state
        }
        val scheduled = preset?.resolveScheduled(context, basePayload.scheduled) ?: basePayload.scheduled

        return basePayload.copy(
            title = title,
            content = buildContent(template, input.content),
            state = state,
            scheduled = scheduled,
            tags = tags.toList(),
            properties = properties,
        )
    }

    private fun buildTitle(
        context: Context,
        template: CaptureTemplateEntity,
        input: CaptureInput,
        preset: CaptureTemplate?,
    ): String {
        val suppliedTitle = input.title?.takeIf { it.isNotBlank() }
        if (suppliedTitle != null) {
            return suppliedTitle
        }

        return preset?.defaultTitle(context, template.titleTemplate) ?: template.titleTemplate.orEmpty()
    }

    private fun buildContent(template: CaptureTemplateEntity, incomingContent: String?): String? {
        val scaffold = template.bodyTemplate
        val shared = incomingContent?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            scaffold.isNullOrBlank() -> shared
            shared == null -> scaffold
            else -> "$scaffold\n\n$shared"
        }
    }
}
