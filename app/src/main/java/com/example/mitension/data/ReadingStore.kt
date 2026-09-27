package com.example.mitension.data

import android.content.Context
import android.util.AtomicFile
import com.example.mitension.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Internal JSON is implementation detail: public imports/exports are XLSX. No network or login. */
class ReadingStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "readings.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow<List<Reading>>(emptyList())
    val readings = state.asStateFlow()
    var loadError: Boolean = false; private set

    init {
        if (file.baseFile.exists()) runCatching {
            val decoded = json.decodeFromString<List<Reading>>(file.openRead().bufferedReader().use { it.readText() }).map { it.canonical() }
            require(decoded.all { it.valid() } && decoded.map { it.id }.distinct().size == decoded.size)
            state.value = decoded.sortedByDescending { it.measuredAt }
        }.onFailure { loadError = true } // Never overwrite a corrupt original with an empty history.
    }

    @Synchronized fun add(items: List<Reading>) {
        val normalized = items.map { it.canonical() }
        require(normalized.isNotEmpty() && normalized.all { it.valid() })
        require(normalized.map { it.id }.distinct().size == normalized.size)
        require(normalized.none { item -> state.value.any { it.id == item.id } })
        persist((state.value + normalized).sortedByDescending { it.measuredAt })
    }

    @Synchronized fun importRows(items: List<Reading>): Int {
        require(items.all { it.valid() })
        val grouped = items.map { it.canonical() }.groupBy { it.id }
        require(grouped.values.all { rows -> rows.distinct().size == 1 })
        val known = state.value.map { it.id }.toSet()
        val added = grouped.values.map { it.first() }.filter { it.id !in known }
        if (added.isNotEmpty()) add(added)
        return added.size
    }

    @Synchronized fun delete(id: String) { persist(state.value.filterNot { it.id.equals(id, true) }) }

    /** Commit disk first, publish second; failed writes leave both previous disk and UI state intact. */
    private fun persist(items: List<Reading>) {
        check(!loadError)
        val output = file.startWrite()
        try {
            output.write(json.encodeToString(items).toByteArray())
            file.finishWrite(output)
            state.value = items
        } catch (error: Exception) { file.failWrite(output); throw error }
    }
}
