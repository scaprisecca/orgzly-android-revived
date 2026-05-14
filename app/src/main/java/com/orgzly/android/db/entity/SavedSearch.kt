package com.orgzly.android.db.entity

import androidx.room.*

@Entity(
        tableName = "searches",
        indices = [Index(value = ["presetKey"], unique = true)]
)
data class SavedSearch @JvmOverloads constructor(
        @PrimaryKey(autoGenerate = true)
        val id: Long,

        val name: String,

        val query: String,

        val position: Int,

        val builderMetadata: String? = null,

        val builderMetadataVersion: Int? = null,

        val presetKey: String? = null
) {
    fun areContentsTheSame(that: SavedSearch): Boolean {
        return name == that.name &&
            query == that.query &&
            position == that.position &&
            builderMetadata == that.builderMetadata &&
            builderMetadataVersion == that.builderMetadataVersion &&
            presetKey == that.presetKey
    }
}
