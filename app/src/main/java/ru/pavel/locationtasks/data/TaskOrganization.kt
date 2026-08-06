package ru.pavel.locationtasks.data

import java.nio.charset.StandardCharsets
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Base64
import java.util.UUID

enum class TaskRecurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY;

    companion object {
        fun fromStorage(value: String): TaskRecurrence =
            entries.firstOrNull { it.name == value } ?: NONE
    }
}

data class TaskRecurrenceRule(
    val recurrence: TaskRecurrence,
    val interval: Int = 1,
    val daysOfWeekMask: Int = 0,
    val dayOfMonth: Int? = null,
    val anchorAt: Long? = null,
    val endAt: Long? = null,
) {
    val normalizedInterval: Int
        get() = interval.coerceIn(MIN_RECURRENCE_INTERVAL, MAX_RECURRENCE_INTERVAL)

    fun effectiveDaysOfWeekMask(zoneId: ZoneId = ZoneId.systemDefault()): Int {
        if (daysOfWeekMask != 0) return daysOfWeekMask and ALL_WEEK_DAYS_MASK
        val anchorDay = anchorAt?.let {
            Instant.ofEpochMilli(it).atZone(zoneId).dayOfWeek
        } ?: DayOfWeek.MONDAY
        return recurrenceDayBit(anchorDay)
    }

    fun effectiveDayOfMonth(zoneId: ZoneId = ZoneId.systemDefault()): Int =
        dayOfMonth?.coerceIn(1, 31)
            ?: anchorAt?.let { Instant.ofEpochMilli(it).atZone(zoneId).dayOfMonth }
            ?: 1
}

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isCompleted: Boolean = false,
)

object ChecklistCodec {
    fun encode(items: List<ChecklistItem>): String = items.joinToString("\n") { item ->
        listOf(
            if (item.isCompleted) "1" else "0",
            encodePart(item.id),
            encodePart(item.title),
        ).joinToString("\t")
    }

    fun decode(value: String): List<ChecklistItem> {
        if (value.isBlank()) return emptyList()
        return value.lineSequence().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size != 3) return@mapNotNull null
            runCatching {
                ChecklistItem(
                    id = decodePart(parts[1]),
                    title = decodePart(parts[2]),
                    isCompleted = parts[0] == "1",
                )
            }.getOrNull()
        }.filter { it.title.isNotBlank() }.toList()
    }

    private fun encodePart(value: String): String = Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodePart(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        StandardCharsets.UTF_8,
    )
}

fun parseTags(input: String): List<String> = input
    .split(',', ';', '\n')
    .map { it.trim().replace(TAG_SEPARATOR, "") }
    .filter(String::isNotEmpty)
    .distinctBy(String::lowercase)
    .take(MAX_TAGS)

fun encodeTags(tags: List<String>): String =
    tags.map { it.trim().replace(TAG_SEPARATOR, "") }
        .filter(String::isNotEmpty)
        .joinToString(TAG_SEPARATOR)

fun decodeTags(value: String): List<String> =
    value.split(TAG_SEPARATOR).map(String::trim).filter(String::isNotEmpty)

fun nextOccurrenceAt(
    recurrence: TaskRecurrence,
    currentDueAt: Long?,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long? = nextOccurrenceAt(
    rule = TaskRecurrenceRule(
        recurrence = recurrence,
        anchorAt = currentDueAt,
    ),
    currentDueAt = currentDueAt,
    nowMillis = nowMillis,
    zoneId = zoneId,
)

fun nextOccurrenceAt(
    rule: TaskRecurrenceRule,
    currentDueAt: Long?,
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long? {
    if (rule.recurrence == TaskRecurrence.NONE) return null
    val anchorMillis = rule.anchorAt ?: currentDueAt ?: nowMillis
    val anchor = Instant.ofEpochMilli(anchorMillis).atZone(zoneId)
    val cutoffMillis = maxOf(currentDueAt ?: Long.MIN_VALUE, nowMillis)
    val cutoff = Instant.ofEpochMilli(cutoffMillis).atZone(zoneId)
    val candidate = when (rule.recurrence) {
        TaskRecurrence.NONE -> null
        TaskRecurrence.DAILY -> nextDailyOccurrence(anchor, cutoff, rule.normalizedInterval)
        TaskRecurrence.WEEKLY -> nextWeeklyOccurrence(
            anchor = anchor,
            cutoff = cutoff,
            interval = rule.normalizedInterval,
            daysMask = rule.effectiveDaysOfWeekMask(zoneId),
        )
        TaskRecurrence.MONTHLY -> nextMonthlyOccurrence(
            anchor = anchor,
            cutoff = cutoff,
            interval = rule.normalizedInterval,
            dayOfMonth = rule.effectiveDayOfMonth(zoneId),
        )
        TaskRecurrence.YEARLY -> nextYearlyOccurrence(
            anchor = anchor,
            cutoff = cutoff,
            interval = rule.normalizedInterval,
        )
    } ?: return null
    val result = candidate.toInstant().toEpochMilli()
    return result.takeIf { rule.endAt == null || it <= rule.endAt }
}

fun recurrenceDayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)

private fun nextDailyOccurrence(
    anchor: ZonedDateTime,
    cutoff: ZonedDateTime,
    interval: Int,
): ZonedDateTime {
    val daysSinceAnchor = ChronoUnit.DAYS.between(anchor.toLocalDate(), cutoff.toLocalDate())
        .coerceAtLeast(0)
    var step = daysSinceAnchor / interval
    var candidate = atAnchorTime(anchor.toLocalDate().plusDays(step * interval), anchor)
    if (!candidate.toInstant().isAfter(cutoff.toInstant())) {
        step += 1
        candidate = atAnchorTime(anchor.toLocalDate().plusDays(step * interval), anchor)
    }
    return candidate
}

private fun nextWeeklyOccurrence(
    anchor: ZonedDateTime,
    cutoff: ZonedDateTime,
    interval: Int,
    daysMask: Int,
): ZonedDateTime {
    val anchorDate = anchor.toLocalDate()
    val anchorWeek = anchorDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    var date = maxOf(anchorDate, cutoff.toLocalDate())
    repeat(interval * DAYS_IN_WEEK + DAYS_IN_WEEK + 1) {
        val candidate = atAnchorTime(date, anchor)
        val candidateWeek = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weeksFromAnchor = ChronoUnit.WEEKS.between(anchorWeek, candidateWeek)
        val activeWeek = weeksFromAnchor >= 0 && weeksFromAnchor % interval == 0L
        if (activeWeek &&
            daysMask and recurrenceDayBit(date.dayOfWeek) != 0 &&
            candidate.toInstant().isAfter(cutoff.toInstant())
        ) {
            return candidate
        }
        date = date.plusDays(1)
    }
    error("Unable to calculate weekly recurrence")
}

private fun nextMonthlyOccurrence(
    anchor: ZonedDateTime,
    cutoff: ZonedDateTime,
    interval: Int,
    dayOfMonth: Int,
): ZonedDateTime? {
    val anchorMonth = YearMonth.from(anchor)
    var month = maxOf(anchorMonth, YearMonth.from(cutoff))
    repeat(interval * MONTHS_IN_YEAR + MONTHS_IN_YEAR) {
        val monthsFromAnchor = ChronoUnit.MONTHS.between(anchorMonth, month)
        if (monthsFromAnchor >= 0 &&
            monthsFromAnchor % interval == 0L &&
            dayOfMonth <= month.lengthOfMonth()
        ) {
            val candidate = atAnchorTime(month.atDay(dayOfMonth), anchor)
            if (candidate.toInstant().isAfter(cutoff.toInstant())) return candidate
        }
        month = month.plusMonths(1)
    }
    return null
}

private fun nextYearlyOccurrence(
    anchor: ZonedDateTime,
    cutoff: ZonedDateTime,
    interval: Int,
): ZonedDateTime? {
    var year = maxOf(anchor.year, cutoff.year)
    repeat(interval * LEAP_YEAR_CYCLE + LEAP_YEAR_CYCLE) {
        val yearsFromAnchor = year - anchor.year
        if (yearsFromAnchor >= 0 && yearsFromAnchor % interval == 0) {
            val date = runCatching {
                LocalDate.of(year, anchor.month, anchor.dayOfMonth)
            }.getOrNull()
            if (date != null) {
                val candidate = atAnchorTime(date, anchor)
                if (candidate.toInstant().isAfter(cutoff.toInstant())) return candidate
            }
        }
        year += 1
    }
    return null
}

private fun atAnchorTime(date: LocalDate, anchor: ZonedDateTime): ZonedDateTime =
    ZonedDateTime.of(date, LocalTime.from(anchor), anchor.zone)

private const val TAG_SEPARATOR = "\u001F"
private const val MAX_TAGS = 20
const val MIN_RECURRENCE_INTERVAL = 1
const val MAX_RECURRENCE_INTERVAL = 99
const val ALL_WEEK_DAYS_MASK = 127
private const val DAYS_IN_WEEK = 7
private const val MONTHS_IN_YEAR = 12
private const val LEAP_YEAR_CYCLE = 4
