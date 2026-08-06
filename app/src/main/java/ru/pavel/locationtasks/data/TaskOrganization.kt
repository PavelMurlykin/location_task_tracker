package ru.pavel.locationtasks.data

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import java.util.UUID

enum class TaskRecurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY;

    companion object {
        fun fromStorage(value: String): TaskRecurrence =
            entries.firstOrNull { it.name == value } ?: NONE
    }
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
): Long? {
    if (recurrence == TaskRecurrence.NONE) return null
    var candidate = Instant.ofEpochMilli(currentDueAt ?: nowMillis).atZone(zoneId)
    do {
        candidate = when (recurrence) {
            TaskRecurrence.NONE -> return null
            TaskRecurrence.DAILY -> candidate.plusDays(1)
            TaskRecurrence.WEEKLY -> candidate.plusWeeks(1)
            TaskRecurrence.MONTHLY -> candidate.plusMonths(1)
        }
    } while (!candidate.toInstant().isAfter(Instant.ofEpochMilli(nowMillis)))
    return candidate.toInstant().toEpochMilli()
}

private const val TAG_SEPARATOR = "\u001F"
private const val MAX_TAGS = 20
