package com.example.mitension

import com.example.mitension.model.*
import com.example.mitension.data.Excel
import org.junit.Assert.*
import org.junit.Test
import java.time.*
import java.util.zip.*
import java.io.*

/** Synthetic fixtures only. These tests do not touch phone files, permissions, or real alarms. */
class DomainTest {
    @Test fun watchImportUsesStableIdentityWithoutTouchingManualReadings() {
        assertTrue(isWatchDevice(1))
        assertFalse(isWatchDevice(2))
        assertFalse(isWatchDevice(null))
        val source = WatchPressure("source-42", 124, 78, 1_600_000_000_000L, "watch.provider")
        val first = source.toReading()
        assertTrue(first.valid())
        assertEquals(first.id, source.toReading().id)
        assertNotEquals(first.id, source.copy(sourceId = "source-43").toReading().id)
        assertNotEquals(first.id, source.copy(origin = "other.provider").toReading().id)
        assertNull(first.pulse)
    }
    @Test fun rollingPeriodMatchesIPhoneAtCutoffAndAcrossDaylightSaving() {
        val madrid = ZoneId.of("Europe/Madrid")
        val now = ZonedDateTime.of(2026, 10, 27, 10, 30, 0, 0, madrid).toInstant()
        val cutoff = now.atZone(madrid).minusDays(7).toInstant().toEpochMilli()
        val included = Reading(systolic = 120, diastolic = 80, measuredAt = cutoff)
        val excluded = included.copy(id = java.util.UUID.randomUUID().toString(), measuredAt = cutoff - 1)
        assertEquals(listOf(included), readingsInLastDays(listOf(included, excluded), 7, now, madrid))
        assertEquals(2, readingsInLastDays(listOf(included, excluded), null, now, madrid).size)
    }
    private val zone = ZoneId.of("Europe/Madrid")
    private fun reading(hour: Int, minute: Int = 0) = Reading(systolic = 120, diastolic = 80,
        measuredAt = LocalDate.of(2026,9,27).atTime(hour,minute).atZone(zone).toInstant().toEpochMilli())
    private val headers = listOf("Fecha y hora", "Momento", "Sistólica", "Diastólica", "Pulso", "Notas", "ID", "Medicamentos")

    @Test fun unicodeIntegersAndInvalidValues() {
        assertEquals(123, integer("١٢٣")); assertEquals(123, integer("۱۲۳")); assertEquals(123, integer("１２３"))
        listOf("", "1.5", "-120", "abc", "99999999999999999999").forEach { assertNull(integer(it)) }
        assertTrue(reading(8).valid()); assertFalse(reading(8).copy(diastolic = 140).valid())
        assertFalse(reading(8).copy(pulse = -1).valid()); assertFalse(reading(8).copy(medications = listOf(Medication(name = ""))).valid())
    }
    @Test fun localTimeClassifiesTheBoundaryAndLateEvening() {
        assertEquals("morning", reading(13,59).period(zone)); assertEquals("evening", reading(14).period(zone))
        assertEquals("evening", reading(20,55).period(zone))
        assertEquals("morning", reading(14).period(ZoneId.of("UTC")))
    }
    @Test fun everyDayGroupsEachReadingWithoutAveraging() {
        val rows = listOf(reading(8), reading(8,2), reading(20,55))
        val day = grouped(rows, zone).values.single()
        assertEquals(2, day["morning"]!!.size); assertEquals(1, day["evening"]!!.size)
        assertEquals(listOf("morning", "evening"), day.keys.toList())
        assertEquals(3, day.values.flatten().map { it.id }.distinct().size)
    }
    @Test fun excelRoundTripsOneTwoAndThreeReadingsInBothPeriods() {
        for(count in 1..3) {
            val rows = listOf(8,20).flatMap { hour -> (0 until count).map { reading(hour,it) } }
            val bytes = Excel.write(rows, headers, zone)
            val imported = Excel.read(bytes.inputStream(), zone)
            assertEquals(rows.sortedBy { it.measuredAt }, imported)
            assertEquals(count, imported.count { it.period(zone) == "morning" })
            assertEquals(count, imported.count { it.period(zone) == "evening" })
        }
    }
    @Test fun excelPreservesMedicationNotesAndExactMilliseconds() {
        val row = reading(8).copy(measuredAt = reading(8).measuredAt + 123,
            note = "<nota> & 中文", medications = listOf(Medication(name = "MEDICAMENTO SINTÉTICO", dose = "texto de prueba", note = "sin datos reales")))
        assertEquals(row, Excel.read(Excel.write(listOf(row), headers, zone).inputStream(), zone).single())
    }
    @Test fun uuidCaseDoesNotDuplicateAnIOSRoundTrip() {
        val row = reading(8)
        val imported = Excel.read(Excel.write(listOf(row, row.copy(id = row.id.uppercase())), headers, zone).inputStream(), zone)
        assertEquals(listOf(row), imported)
    }
    @Test fun excelRejectsFormulaAndConflictingDuplicateIDs() {
        val row = reading(8)
        val file = Excel.write(listOf(row), headers, zone)
        val mutated = mutateSheet(file) { it.replace("<c r=\"C2\"><v>", "<c r=\"C2\"><f>SUM(1,2)</f><v>") }
        assertThrows(Exception::class.java) { Excel.read(mutated.inputStream(), zone) }
        val conflict = Excel.write(listOf(row, row.copy(systolic = 130)), headers, zone)
        assertThrows(Exception::class.java) { Excel.read(conflict.inputStream(), zone) }
    }
    @Test fun malformedAndExternalEntityFilesAreRejected() {
        assertThrows(Exception::class.java) { Excel.read("not an XLSX".byteInputStream()) }
        val file = Excel.write(listOf(reading(8)), headers, zone)
        assertThrows(Exception::class.java) { Excel.read(file.copyOf(file.size-8).inputStream(), zone) }
        val entity = mutateSheet(file) { "<!DOCTYPE worksheet [<!ENTITY secret SYSTEM 'file:///etc/passwd'>]>" + it }
        assertThrows(Exception::class.java) { Excel.read(entity.inputStream(), zone) }
    }
    @Test fun followupIsThirtyMinutesAndSatisfiedOnlyByItsOwnPeriod() {
        val config = ReminderConfig(Schedule(enabled = true), Schedule(enabled = true, hour = 21))
        val now = LocalDate.of(2026,9,27).atTime(7,59).atZone(zone).toInstant()
        val plans = reminderPlans(config, emptyList(), now, zone)
        val morning = plans.first { it.key == "morning.primary" }
        val follow = plans.first { it.key == "morning.followup" }
        assertEquals(1800000L, follow.at - morning.at)
        assertFalse(follow.alarm)
        val satisfied = reminderPlans(config, listOf(reading(8)), now, zone)
        assertTrue(satisfied.none { it.key == "morning.followup" && it.day == LocalDate.of(2026,9,27) })
        assertTrue(satisfied.any { it.key == "evening.followup" && it.day == LocalDate.of(2026,9,27) })
    }
    @Test fun followupCrossesMidnightAndDisabledSchedulesDisappear() {
        val config = ReminderConfig(evening = Schedule(enabled = true, hour = 23, minute = 50))
        val now = LocalDate.of(2026,9,28).atTime(0,5).atZone(zone).toInstant()
        val follow = reminderPlans(config, emptyList(), now, zone).first { it.followup }
        assertEquals(LocalDate.of(2026,9,27), follow.day)
        assertEquals(20, Instant.ofEpochMilli(follow.at).atZone(zone).minute)
        assertTrue(reminderPlans(ReminderConfig(), emptyList(), now, zone).isEmpty())
    }
    private fun mutateSheet(bytes: ByteArray, mutate: (String) -> String): ByteArray {
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream()).use { zip -> while(true) {
            val entry = zip.nextEntry ?: break; files[entry.name] = zip.readBytes()
        } }
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip -> files.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(if(name.endsWith("sheet1.xml")) mutate(data.toString(Charsets.UTF_8)).toByteArray() else data); zip.closeEntry()
            } }; output.toByteArray()
        }
    }
}
