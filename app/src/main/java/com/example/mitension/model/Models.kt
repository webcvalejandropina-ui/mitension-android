package com.example.mitension.model

import kotlinx.serialization.Serializable
import java.time.*
import java.util.UUID

/** A medication belongs to one immutable reading; its dose is user-authored text, not advice. */
@Serializable data class Medication(val id: String = UUID.randomUUID().toString(), val name: String,
    val dose: String = "", val note: String = "") {
    val description get() = name + (if (dose.isBlank()) "" else " · $dose") + (if (note.isBlank()) "" else " — $note")
}

/** Timestamp is an instant; day and period are always derived in the device's current time zone. */
@Serializable data class Reading(val id: String = UUID.randomUUID().toString(), val systolic: Int,
    val diastolic: Int, val pulse: Int? = null, val measuredAt: Long = System.currentTimeMillis(),
    val note: String = "", val medications: List<Medication> = emptyList()) {
    fun localTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime = Instant.ofEpochMilli(measuredAt).atZone(zone).toLocalDateTime()
    fun period(zone: ZoneId = ZoneId.systemDefault()) = if (localTime(zone).hour < 14) "morning" else "evening"
    fun valid() = uuid(id) && systolic in 40..300 && diastolic in 30..200 && diastolic < systolic &&
        (pulse == null || pulse in 20..250) && medications.all { uuid(it.id) && it.name.isNotBlank() } &&
        medications.map { it.id.lowercase(java.util.Locale.ROOT) }.distinct().size == medications.size && note.length <= 32767
    companion object {
        fun uuid(value: String) = runCatching { UUID.fromString(value).toString().equals(value, true) }.getOrDefault(false)
    }
}

/** UUID casing is representation, not identity; iOS emits uppercase and Android usually lowercase. */
fun Reading.canonical() = copy(id = id.lowercase(java.util.Locale.ROOT), medications = medications.map {
    it.copy(id = it.id.lowercase(java.util.Locale.ROOT))
})

/** Pure grouping shared by history and doctor screen. No averaging or editing of stored readings. */
fun grouped(readings: List<Reading>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Map<String, List<Reading>>> =
    readings.groupBy { it.localTime(zone).toLocalDate() }.toSortedMap(reverseOrder()).mapValues { (_, rows) ->
        val periods = rows.sortedByDescending { it.measuredAt }.groupBy { it.period(zone) }
        listOf("morning", "evening").mapNotNull { key -> periods[key]?.let { key to it } }.toMap()
    }

/** Accept decimal Unicode digits, but never signs, fractions or non-numeric medication text. */
fun integer(text: String): Int? {
    val clean = text.trim()
    if (clean.isEmpty() || !clean.all { Character.getType(it) == Character.DECIMAL_DIGIT_NUMBER.toInt() }) return null
    return clean.map { Character.digit(it, 10) }.joinToString("").toIntOrNull()
}

@Serializable data class Schedule(val enabled: Boolean = false, val hour: Int = 8, val minute: Int = 0,
    val weekdays: List<Int> = (1..7).toList(), val alarm: Boolean = false) {
    fun valid() = hour in 0..23 && minute in 0..59 && weekdays.all { it in 1..7 } && (!enabled || weekdays.isNotEmpty())
}
@Serializable data class ReminderConfig(val morning: Schedule = Schedule(), val evening: Schedule = Schedule(hour = 21))

data class ReminderPlan(val key: String, val at: Long, val period: String, val day: LocalDate,
    val followup: Boolean, val alarm: Boolean)

/** Plan only the nearest primary and follow-up per slot; each broadcast schedules the next occurrence. */
fun reminderPlans(config: ReminderConfig, readings: List<Reading>, now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()): List<ReminderPlan> {
    require(config.morning.valid() && config.evening.valid())
    val today = now.atZone(zone).toLocalDate()
    val savedSlots = readings.map { row ->
        val local = row.localTime(zone)
        local.toLocalDate() to if(local.hour < 14) "morning" else "evening"
    }.toSet()
    return listOf("morning" to config.morning, "evening" to config.evening).flatMap { (period, schedule) ->
        if (!schedule.enabled) emptyList() else {
            val candidates = (-1L..8L).flatMap { offset ->
                val day = today.plusDays(offset)
                val weekday = day.dayOfWeek.value % 7 + 1
                if (weekday !in schedule.weekdays) emptyList() else {
                    val original = day.atTime(schedule.hour, schedule.minute).atZone(zone).toInstant()
                    val satisfied = (day to period) in savedSlots
                    listOfNotNull(
                        if (original > now) ReminderPlan("$period.primary", original.toEpochMilli(), period, day, false, schedule.alarm) else null,
                        if (!satisfied && original.plusSeconds(1800) > now)
                            ReminderPlan("$period.followup", original.plusSeconds(1800).toEpochMilli(), period, day, true, false) else null)
                }
            }
            candidates.groupBy { it.key }.map { (_, plans) -> plans.minBy { it.at } }
        }
    }
}
