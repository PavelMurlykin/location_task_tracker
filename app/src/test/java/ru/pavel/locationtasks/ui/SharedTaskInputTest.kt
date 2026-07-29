package ru.pavel.locationtasks.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedTaskInputTest {
    @Test
    fun `share subject becomes task title`() {
        assertEquals(
            "Статья о путешествиях",
            extractSharedTaskTitle(
                subject = "  Статья о путешествиях  ",
                sharedText = "https://example.com/article",
            ),
        )
    }

    @Test
    fun `first non blank shared line becomes title without subject`() {
        assertEquals(
            "Купить билеты",
            extractSharedTaskTitle(subject = null, sharedText = "\n  Купить билеты\nдо пятницы"),
        )
    }

    @Test
    fun `empty share does not create draft`() {
        assertNull(extractSharedTaskTitle(subject = " ", sharedText = "\n"))
    }
}
