package com.orgzly.android.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.orgzly.android.db.entity.SavedSearch
import androidx.lifecycle.LiveData

@Dao
interface SavedSearchDao : BaseDao<SavedSearch> {
    @Query("SELECT * FROM searches WHERE id = :id")
    fun get(id: Long): SavedSearch?

    @Query("SELECT * FROM searches ORDER BY position, id")
    fun getLiveData(): LiveData<List<SavedSearch>>

    @Query("SELECT * FROM searches ORDER BY position, id")
    fun getAll(): List<SavedSearch>

    @Query("SELECT * FROM searches WHERE name LIKE :name ORDER BY position, id")
    fun getAllByNameIgnoreCase(name: String): List<SavedSearch>

    @Query("SELECT * FROM searches WHERE presetKey = :presetKey LIMIT 1")
    fun getByPresetKey(presetKey: String): SavedSearch?

    @Query("SELECT * FROM searches WHERE name = :name ORDER BY position, id")
    fun getAllByExactName(name: String): List<SavedSearch>

    @Query("SELECT * FROM searches WHERE name = :name AND query = :query ORDER BY position, id")
    fun getAllByExactNameAndQuery(name: String, query: String): List<SavedSearch>

    @Query("SELECT * FROM searches WHERE position > :position ORDER BY position LIMIT 1")
    fun getFirstBelow(position: Int): SavedSearch?

    @Query("SELECT * FROM searches WHERE position < :position ORDER BY position DESC LIMIT 1")
    fun getFirstAbove(position: Int): SavedSearch?

    @Query("SELECT MAX(position) + 1 FROM searches")
    fun getNextAvailablePosition(): Int

    @Query("DELETE FROM searches")
    fun deleteAll()

    @Query("DELETE FROM searches WHERE id IN (:ids)")
    fun delete(ids: Set<Long>)
}
