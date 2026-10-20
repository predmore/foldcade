package app.foldcade.language

import androidx.compose.ui.text.font.FontFamily
import org.junit.Assert.assertSame
import org.junit.Test

class ThemeFontFallbackTest {
    @Test
    fun aFontThatFailsToLoadWearsTheInheritedFace() {
        val inherited = FontFamily.Serif
        val loaded = FontFamily.Monospace
        assertSame(inherited, fontOrInherited(null, inherited))
        assertSame(loaded, fontOrInherited(loaded, inherited))
        assertSame(FontFamily.SansSerif, fontOrInherited(null, null))
    }
}
