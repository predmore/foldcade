package app.foldcade.plugins.romm

import app.foldcade.api.plugin.PluginException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RommTokenTest {
    @Test
    fun clientTokenDropsThePasswordAndDoesNotKeepIt() {
        val password = "hunter2".toCharArray()
        val token = RommSignIn.clientToken("  Bearer rmm_handheld  ", password)
        assertEquals("rmm_handheld", token)
        assertTrue(password.all { it == '\u0000' })
        assertNull(RommTokenSource { null }.accessToken())
    }

    @Test
    fun blankTokenStillWipesThePassword() {
        val password = "hunter2".toCharArray()
        val failure = runCatching { RommSignIn.clientToken("   ", password) }.exceptionOrNull()
        assertTrue(failure is PluginException.NotAuthenticated)
        assertTrue(password.all { it == '\u0000' })
    }
}
