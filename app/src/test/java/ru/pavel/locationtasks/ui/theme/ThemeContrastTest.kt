package ru.pavel.locationtasks.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    @Test
    fun `light theme key text pairs meet WCAG AA contrast`() {
        assertAccessibleTextPairs(LightColorScheme)
    }

    @Test
    fun `dark theme key text pairs meet WCAG AA contrast`() {
        assertAccessibleTextPairs(DarkColorScheme)
    }

    private fun assertAccessibleTextPairs(colors: ColorScheme) {
        assertContrast("primary", colors.onPrimary, colors.primary)
        assertContrast("primary container", colors.onPrimaryContainer, colors.primaryContainer)
        assertContrast("background", colors.onBackground, colors.background)
        assertContrast("surface", colors.onSurface, colors.surface)
        assertContrast("surface variant", colors.onSurfaceVariant, colors.surfaceVariant)
        assertContrast("error", colors.onError, colors.error)
    }

    private fun assertContrast(name: String, foreground: Color, background: Color) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05) / (darker + 0.05)
        assertTrue("$name contrast was $ratio", ratio >= MIN_TEXT_CONTRAST)
    }

    companion object {
        private const val MIN_TEXT_CONTRAST = 4.5
    }
}
