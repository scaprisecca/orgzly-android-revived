package com.orgzly.android.savedsearch

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.orgzly.android.savedsearch.builder.AgendaViewMetadataJson
import com.orgzly.android.savedsearch.builder.AgendaViewQueryCompiler

object AgendaPresetSeeder {
    private val compiler = AgendaViewQueryCompiler()

    fun seedMissingPresets(db: SupportSQLiteDatabase) {
        AgendaPresetSeeds.all.forEachIndexed { index, seed ->
            if (hasPresetKey(db, seed.presetKey)) {
                return@forEachIndexed
            }

            val metadata = AgendaViewMetadataJson.serialize(seed.state)
            val query = compiler.compileToString(seed.state)
            val exactLegacyMatch = findByExactNameAndQuery(db, seed.name, query)

            if (exactLegacyMatch != null) {
                updateMetadata(db, exactLegacyMatch, metadata, seed.presetKey)
                return@forEachIndexed
            }

            val resolvedName = if (hasExactName(db, seed.name)) "${seed.name} (preset)" else seed.name

            db.insert("searches", SQLiteDatabase.CONFLICT_ABORT, ContentValues().apply {
                put("name", resolvedName)
                put("query", query)
                put("position", nextPosition(db, index))
                put("builderMetadata", metadata)
                put("builderMetadataVersion", AgendaViewMetadataJson.VERSION)
                put("presetKey", seed.presetKey)
            })
        }
    }

    private fun hasPresetKey(db: SupportSQLiteDatabase, presetKey: String): Boolean {
        db.query("SELECT id FROM searches WHERE presetKey = ?", arrayOf<Any>(presetKey)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun hasExactName(db: SupportSQLiteDatabase, name: String): Boolean {
        db.query("SELECT id FROM searches WHERE name = ?", arrayOf<Any>(name)).use { cursor ->
            return cursor.moveToFirst()
        }
    }

    private fun findByExactNameAndQuery(db: SupportSQLiteDatabase, name: String, query: String): Long? {
        db.query("SELECT id FROM searches WHERE name = ? AND query = ? ORDER BY position, id LIMIT 1", arrayOf(name, query)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    private fun updateMetadata(
        db: SupportSQLiteDatabase,
        id: Long,
        metadata: String,
        presetKey: String,
    ) {
        db.execSQL(
            "UPDATE searches SET builderMetadata = ?, builderMetadataVersion = ?, presetKey = ? WHERE id = ?",
            arrayOf(metadata, AgendaViewMetadataJson.VERSION, presetKey, id),
        )
    }

    private fun nextPosition(db: SupportSQLiteDatabase, index: Int): Int {
        db.query("SELECT COALESCE(MAX(position), 0) + 1 FROM searches").use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else index + 1
        }
    }
}
