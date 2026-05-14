package com.orgzly.android.savedsearch.builder

import com.google.gson.Gson
import com.orgzly.android.db.entity.SavedSearch

object AgendaViewMetadataJson {
    const val VERSION = 1

    private val gson = Gson()

    fun serialize(state: AgendaViewBuilderState): String {
        return gson.toJson(state)
    }

    fun deserialize(savedSearch: SavedSearch?): AgendaViewBuilderState? {
        if (savedSearch?.builderMetadataVersion != VERSION) {
            return null
        }

        return deserialize(savedSearch.builderMetadata)
    }

    fun deserialize(json: String?): AgendaViewBuilderState? {
        if (json.isNullOrBlank()) {
            return null
        }

        return runCatching {
            validate(gson.fromJson(json, AgendaViewBuilderState::class.java))
        }.getOrNull()
    }

    private fun validate(state: AgendaViewBuilderState): AgendaViewBuilderState {
        requirePresent(state.name)
        requireStringList(state.includeNotebooks)
        requireStringList(state.excludeNotebooks)
        requireStringList(state.includeTags)
        requireStringList(state.excludeTags)
        requireStringList(state.includeStates)
        requireStringList(state.excludeStates)
        requirePropertyList(state.includeProperties)
        requirePropertyList(state.excludeProperties)
        requirePresent(state.dateFilter)
        requirePresent(state.dateSources).forEach { requirePresent(it) }
        requirePresent(state.sort)
        state.advancedQuery?.let { requirePresent(it) }

        return state
    }

    private fun requireStringList(values: List<String>) {
        requirePresent(values).forEach { requirePresent(it) }
    }

    private fun requirePropertyList(values: List<AgendaViewBuilderState.PropertyFilter>) {
        requirePresent(values).forEach { property ->
            requirePresent(property)
            requirePresent(property.name)
            requirePresent(property.operator)
            property.value?.let { requirePresent(it) }
        }
    }

    private fun <T : Any> requirePresent(value: T): T = value
}
