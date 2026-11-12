package app.foldcade

import androidx.compose.ui.graphics.Color
import app.foldcade.language.ThemeStyle
import app.foldcade.language.builtInTheme
import app.foldcade.language.parseThemeJson
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ApkPluginAppearanceTest {
    private val plugin = testApkPlugin()

    private fun packAppearance(path: String): BrokerReply {
        val manifest = listOf(File("../$path"), File(path)).first { it.isFile }.readBytes()
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("theme.json"))
            zip.write(manifest)
            zip.closeEntry()
        }
        return requireNotNull(readApkPluginAppearance(bytes.toByteArray().inputStream()))
    }

    private suspend fun call(
        caller: String? = plugin.packageName,
        plugins: List<ApkPlugin> = listOf(plugin),
        wait: suspend () -> Boolean = { true },
        appearance: () -> BrokerReply? = { apkPluginAppearance(builtInTheme()) },
        fallback: () -> BrokerReply? = { null },
    ): BrokerReply = callPluginBroker(
        caller, "appearance", null, null, null, "test.host", plugins,
        granted = { _, _ -> error("Appearance must not require a scope grant") },
        hosts = { error("Appearance must not read paired hosts") },
        request = { _, _, _ -> error("Appearance must not use the transport") },
        awaitReady = wait, appearance = appearance, fallbackAppearance = fallback,
    )

    @Test fun afterglowUsesThePackColorsRatherThanTheEngineDefaults() {
        assertEquals(mapOf(
            "ok" to true,
            "background" to 0xFF000000.toInt(), "surface" to 0xFF1C1C20.toInt(),
            "onBackground" to 0xFFF4F1EA.toInt(), "muted" to 0xFF9A958C.toInt(), "focus" to 0xFFC8FFE4.toInt(),
            "focusRingStart" to 0xFF52B788.toInt(), "focusRingEnd" to 0xFF3E8EC0.toInt(), "attention" to 0xFFFFC48A.toInt(),
            "round" to .5f, "cardCornerDp" to 28f,
        ), packAppearance("themes/afterglow/theme.json").values)
    }

    @Test fun daylightUsesItsOwnPaletteRingAndScaledCorners() {
        val values = packAppearance("samples/themes/daylight/theme.json").values
        assertEquals(0xFFF4F1EA.toInt(), values["background"])
        assertEquals(0xFFFFFFFF.toInt(), values["surface"])
        assertEquals(0xFF1E1B18.toInt(), values["onBackground"])
        assertEquals(0xFF6E675E.toInt(), values["muted"])
        assertEquals(0xFF2F6FDE.toInt(), values["focus"])
        assertEquals(0xFF2F6FDE.toInt(), values["focusRingStart"])
        assertEquals(0xFF7B4FE0.toInt(), values["focusRingEnd"])
        assertEquals(0xFFFFC48A.toInt(), values["attention"])
        assertEquals(.3f, values["round"])
        assertEquals(28f * .6f, values["cardCornerDp"])
    }

    @Test fun ringFallbackAttentionAlphaAndCornerBoundsFollowTheContract() {
        val file = requireNotNull(parseThemeJson("""{"name":"Square","style":{
            "focusRing":{"start":"#123456"},"colors":{"attention":"#ABCDEF"},
            "shape":{"round":0},"metrics":{"cardCornerDp":100}}}"""))
        val theme = builtInTheme().copy(focus = Color(0x80456789), style = ThemeStyle(file.style))
        val values = apkPluginAppearance(theme).values
        assertEquals(0xFF123456.toInt(), values["focusRingStart"])
        assertEquals(0x80456789.toInt(), values["focusRingEnd"])
        assertEquals(0x80456789.toInt(), values["focus"])
        assertEquals(0xFFABCDEF.toInt(), values["attention"])
        assertEquals(0f, values["round"])
        assertEquals(64f, values["cardCornerDp"])
    }

    @Test fun appearanceRequiresDiscoveryButNoConsentOrRequestedScopes() = runBlocking {
        val mustNotRead: () -> BrokerReply? = { error("Undiscovered caller read the theme") }
        for (caller in listOf(null, "other.plugin")) {
            assertEquals("not_granted", call(caller, appearance = mustNotRead).values["error"])
        }
        assertEquals("not_granted", call(plugins = emptyList(), appearance = mustNotRead).values["error"])
        assertEquals(true, call().values["ok"])
        assertEquals(true, call(plugins = listOf(plugin.copy(scopes = emptySet()))).values["ok"])
    }

    @Test fun eachCallReadsTheCurrentTheme() = runBlocking {
        val afterglow = packAppearance("themes/afterglow/theme.json")
        val daylight = packAppearance("samples/themes/daylight/theme.json")
        var selected = afterglow
        assertEquals(afterglow, call(appearance = { selected }))
        selected = daylight
        assertEquals(daylight, call(appearance = { selected }))
    }

    @Test fun coldStartWaitsBeforeReadingTheSelectedThemeAndFallsBackWhenItIsUnavailable() = runBlocking {
        val ready = CompletableDeferred<Unit>()
        val afterglow = packAppearance("themes/afterglow/theme.json")
        val daylight = packAppearance("samples/themes/daylight/theme.json")
        val selected = {
            check(ready.isCompleted)
            daylight
        }
        assertEquals(afterglow, call(wait = { awaitApkPluginBrokerReady(ready, 1) }, appearance = selected, fallback = { afterglow }))
        val waiting = async(start = CoroutineStart.UNDISPATCHED) {
            call(wait = { awaitApkPluginBrokerReady(ready) }, appearance = selected, fallback = { afterglow })
        }
        assertFalse(waiting.isCompleted)
        ready.complete(Unit)
        assertEquals(daylight, waiting.await())
        assertEquals(afterglow, call(appearance = { null }, fallback = { afterglow }))
    }

    @Test fun failuresNeverExposeThemeFilesOrThrow() = runBlocking {
        for (reply in listOf(
            call(appearance = { throw IllegalStateException("private-theme-file.zip") }),
            call(appearance = { null }, fallback = { throw IllegalStateException("private-theme-file.zip") }),
            call(wait = { throw IllegalStateException("private-theme-file.zip") }),
            call(appearance = { null }),
        )) {
            assertEquals(mapOf("ok" to false, "error" to "internal", "message" to "Theme unavailable"), reply.values)
        }
    }
}
