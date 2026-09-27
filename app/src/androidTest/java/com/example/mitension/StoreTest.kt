package com.example.mitension

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.mitension.data.ReadingStore
import com.example.mitension.model.Reading
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Each fixture uses a unique test-only directory, never the app's real history file. */
@RunWith(AndroidJUnit4::class)
class StoreTest {
    private fun isolated(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "store-tests/${UUID.randomUUID()}").apply { mkdirs() }
        return object : ContextWrapper(base) { override fun getFilesDir() = directory }
    }
    @Test fun persistenceAndIndividualDeletion() {
        val context = isolated(); val row = Reading(systolic = 120, diastolic = 80)
        ReadingStore(context).add(listOf(row))
        val reopened = ReadingStore(context)
        assertEquals(listOf(row), reopened.readings.value)
        reopened.delete(row.id)
        assertTrue(ReadingStore(context).readings.value.isEmpty())
    }
    @Test fun importDeduplicatesAndInvalidBatchLeavesDiskUnchanged() {
        val context = isolated(); val store = ReadingStore(context)
        val rows = List(3) { Reading(systolic = 120+it, diastolic = 80) }
        assertEquals(3, store.importRows(rows)); assertEquals(0, store.importRows(rows))
        val before = File(context.filesDir, "readings.json").readBytes()
        assertThrows(Exception::class.java) { store.add(listOf(Reading(systolic = 130, diastolic = 85), Reading(systolic = 10, diastolic = 80))) }
        assertArrayEquals(before, File(context.filesDir, "readings.json").readBytes())
        assertEquals(rows.sortedByDescending { it.measuredAt }, ReadingStore(context).readings.value)
    }
    @Test fun corruptOriginalCannotBeOverwritten() {
        val context = isolated(); val file = File(context.filesDir, "readings.json")
        file.writeText("synthetic broken fixture")
        val store = ReadingStore(context)
        assertTrue(store.loadError)
        assertThrows(Exception::class.java) { store.add(listOf(Reading(systolic = 120, diastolic = 80))) }
        assertEquals("synthetic broken fixture", file.readText())
    }
}
