package ru.pavel.locationtasks.ui

internal fun extractSharedTaskTitle(
    subject: String?,
    sharedText: String?,
): String? = (
    subject?.takeIf(String::isNotBlank)
        ?: sharedText
            ?.lineSequence()
            ?.firstOrNull(String::isNotBlank)
    )
    ?.trim()
    ?.take(MAX_SHARED_TITLE_LENGTH)
    ?.takeIf(String::isNotEmpty)

private const val MAX_SHARED_TITLE_LENGTH = 300
