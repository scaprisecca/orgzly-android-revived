package com.orgzly.android.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.orgzly.android.db.entity.CaptureTemplateEntity

@Dao
interface CaptureTemplateDao : BaseDao<CaptureTemplateEntity> {
    @Query("SELECT * FROM capture_templates WHERE deleted = 0 ORDER BY position, id")
    fun getAll(): List<CaptureTemplateEntity>

    @Query("SELECT * FROM capture_templates WHERE deleted = 0 AND enabled = 1 ORDER BY position, id")
    fun getEnabled(): List<CaptureTemplateEntity>

    @Query("SELECT * FROM capture_templates WHERE deleted = 0 AND enabled = 1 AND share_enabled = 1 ORDER BY position, id")
    fun getShareEnabled(): List<CaptureTemplateEntity>

    @Query("SELECT * FROM capture_templates WHERE id = :id")
    fun get(id: String): CaptureTemplateEntity?

    @Query("SELECT MAX(position) + 1 FROM capture_templates")
    fun getNextAvailablePosition(): Int?

    @Query("UPDATE capture_templates SET deleted = 1 WHERE id = :id")
    fun softDelete(id: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsert(entity: CaptureTemplateEntity)
}
