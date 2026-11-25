package app.foldcade.language

import org.junit.Assert.*
import org.junit.Test

class SaveHealthTest {
    @Test fun afterglowAttentionHasReadableContrastAndItsOwnRole() {
        val theme = builtInTheme()
        fun luminance(color: androidx.compose.ui.graphics.Color): Double {
            fun channel(value: Float): Double = if (value <= .04045f) value / 12.92 else Math.pow((value + .055) / 1.055, 2.4)
            return .2126 * channel(color.red) + .7152 * channel(color.green) + .0722 * channel(color.blue)
        }
        val ratio = (luminance(theme.style.colors.attention) + .05) / (luminance(theme.surface) + .05)
        assertTrue("Attention contrast: $ratio", ratio >= 4.5)
        assertEquals(TokenRole.Attention, theme.style.specs.getValue("colors.attention").role)
    }

}
