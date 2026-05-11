package com.orgzly.android.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
        tableName = "capture_templates",
        indices = [
            Index("preset_key", unique = true),
            Index("position"),
            Index("enabled"),
            Index("share_enabled"),
            Index("deleted")
        ]
)
data class CaptureTemplateEntity(
        @PrimaryKey
        val id: String,

        val name: String,

        @ColumnInfo(name = "source_type")
        val sourceType: String,

        @ColumnInfo(name = "preset_key")
        val presetKey: String? = null,

        val enabled: Boolean = true,

        @ColumnInfo(name = "share_enabled")
        val shareEnabled: Boolean = true,

        @ColumnInfo(name = "target_notebook_name")
        val targetNotebookName: String? = null,

        @ColumnInfo(name = "title_template")
        val titleTemplate: String? = null,

        @ColumnInfo(name = "body_template")
        val bodyTemplate: String? = null,

        @ColumnInfo(name = "default_state")
        val defaultState: String? = null,

        @ColumnInfo(name = "tags_csv")
        val tagsCsv: String? = null,

        @ColumnInfo(name = "template_kind")
        val templateKind: String,

        val position: Int,

        val deleted: Boolean = false
) {
    companion object {
        const val SOURCE_TYPE_BUILT_IN = "BUILT_IN"
        const val SOURCE_TYPE_CUSTOM = "CUSTOM"

        const val TEMPLATE_KIND_TASK = "TASK"
        const val TEMPLATE_KIND_NOTE = "NOTE"
    }
}
