package app.foldcade.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePluginsTest {
    private val thor = PluginRegistry.thor.packageName
    private val plugin = ApkPluginSetting(thor, "", "Foldcade Thor", "1.0 (1)", "6d:35:70:11", true, setOf(DEVICE_SCOPE), setOf(DEVICE_SCOPE), false,
        signers = PluginRegistry.thor.signers, device = DEVICE_THOR)

    private val reply = mapOf<String, Any?>(
        "ok" to true, "protocol" to 1, "device" to DEVICE_THOR, "root" to "granted",
        "capabilities" to arrayOf("fan", "lights", "brightness", "screens", "gameFan"),
        "fan" to "balanced", "fanBaseline" to "balanced", "lights" to "breathe",
        "brightnessTop" to 0.5f, "brightnessBottom" to 0.25f, "screens" to "dual",
    )
    private val full = parseDeviceState(thor, reply)!!

    private fun model(device: DeviceState? = full, granted: Boolean = true, extra: Map<String, Any?> = emptyMap()) = PickerModel(
        count = 4, rowsPerPage = 1,
        apkPlugins = listOf(plugin.copy(granted = if (granted) setOf(DEVICE_SCOPE) else emptySet())),
        pluginCatalogs = mapOf(thor to PluginCatalogSetting(PluginCatalogStatus.Current, installed = true)),
        device = device?.let { if (extra.isEmpty()) it else parseDeviceState(thor, reply + extra) },
    )

    @Test fun theStateBundleParsesIntoTheModel() {
        assertEquals(DeviceState(thor, DEVICE_THOR, DeviceRoot.Granted, DeviceCapability.entries.toSet(), FanTier.Balanced, FanTier.Balanced, null,
            StickLights.Breathe, 0.5f, 0.25f, DeviceScreens.Dual), full)
        // A game's override names the tier in force and the one game.end returns to.
        val game = parseDeviceState(thor, reply + mapOf("fan" to "performance", "fanGame" to "performance", "fanBaseline" to "default"))!!
        assertEquals(FanTier.Performance, game.fan)
        assertEquals(FanTier.Stock, game.fanBaseline)
        assertEquals(FanTier.Performance, game.fanGame)
        // A list works as an array does, and a missing baseline is the tier in force.
        val listed = parseDeviceState(thor, reply + mapOf("capabilities" to listOf("fan"), "fanBaseline" to null))!!
        assertEquals(setOf(DeviceCapability.Fan), listed.capabilities)
        assertEquals(FanTier.Balanced, listed.fanBaseline)
        assertEquals(false, parseDeviceReply(thor, mapOf("ok" to false, "error" to "no_root")).ok)
        assertEquals("no_root", parseDeviceReply(thor, mapOf("ok" to false, "error" to "no_root")).error)
        assertNull(parseDeviceReply(thor, mapOf("ok" to true, "level" to 0.4f)).state)
    }

    @Test fun nothingIsTrustedThatCantSayWhereItIs() {
        // Without root, nothing is listed, whatever the reply says.
        val denied = parseDeviceState(thor, reply + ("root" to "denied"))!!
        assertEquals(DeviceRoot.Denied, denied.root)
        assertTrue(denied.capabilities.isEmpty())
        assertNull(denied.fan)
        // An unknown value drops its own capability only. The game's tier needs the fan's.
        val odd = parseDeviceState(thor, reply + mapOf("fan" to "turbo", "brightnessBottom" to "high", "screens" to "left"))!!
        assertEquals(setOf(DeviceCapability.Lights), odd.capabilities)
        assertNull(odd.brightnessTop)
        assertNull(parseDeviceState(thor, reply - "root"))
        assertNull(parseDeviceState(thor, reply - "device"))
    }

    @Test fun withoutAGrantedPluginNoDeviceControlShowsAnywhere() {
        // No plugin, a plugin without consent, and one that reports nothing all look as Foldcade does today.
        val none = PickerModel(count = 4, rowsPerPage = 1)
        val ungranted = model(device = null, granted = false)
        val empty = model(parseDeviceState(thor, reply + ("capabilities" to emptyArray<String>())))
        for (m in listOf(none, ungranted, empty)) {
            assertFalse(SettingsCategory.Device in settingsCategories(m))
            assertEquals(rightRows(m.notices), rightRows(m.notices, m.device))
            assertEquals(listOf(Row.QuickTile(QuickSetting.Wifi), Row.QuickTile(QuickSetting.Bluetooth), Row.QuickTile(QuickSetting.Battery),
                Row.QuickTile(QuickSetting.Settings), Row.Brightness), rightRows(m.notices, m.device))
            assertEquals(BrightnessCopy.row, rowText(Row.Brightness, m).label)
            assertEquals(ScreenLayout.entries - ScreenLayout.BottomOnly, offeredLayouts(m))
            for (layout in ScreenLayout.entries - ScreenLayout.BottomOnly) {
                for (playOn in HostScreen.entries) assertEquals(playOn, m.copy(layout = layout, playOn = playOn).playScreen())
            }
            val tile = TileActions(game = "rom", openWith = PlayerChoiceRow(emptyList(), PlayerChoice("a", label = "A")))
            assertFalse(Row.GameFan in actionRows(tile))
        }
        assertFalse(settingsInnerRows(SettingsInner.ApkPlugin(thor), ungranted).any { it is Row.DeviceRoot })
    }

    @Test fun settingsThorListsOnlyWhatThePluginCanDo() {
        val all = model()
        val categories = settingsCategories(all)
        assertTrue(SettingsCategory.Device in categories)
        // After System, before Plugins.
        assertEquals(categories.indexOf(SettingsCategory.Screens) + 1, categories.indexOf(SettingsCategory.Device))
        assertEquals("Thor", settingsTitle(SettingsCategory.Device))
        assertEquals(listOf(Row.Fan, Row.StickLights), settingsRows(SettingsCategory.Device, all))
        val fanOnly = model(extra = mapOf("capabilities" to arrayOf("fan")))
        assertEquals(listOf(Row.Fan), settingsRows(SettingsCategory.Device, fanOnly))
        // Brightness or screens alone have no rows here: the category stays away.
        val screens = model(extra = mapOf("capabilities" to arrayOf("screens", "brightness")))
        assertFalse(SettingsCategory.Device in settingsCategories(screens))
    }

    @Test fun fanShowsTheChosenTierAndStepsThroughThePlugin() {
        val stock = model(extra = mapOf("fan" to "default", "fanBaseline" to "default")).onSetting(Row.Fan)
        assertEquals(RowText("Fan", "Stock"), rowText(Row.Fan, stock))
        assertEquals(RowControl.Choice("Stock"), rowControl(Row.Fan, stock))
        val (right, effect) = reduce(stock, Meaning.MoveRight)
        assertEquals(Effect.SetFan(FanTier.Quiet), effect)
        assertEquals("Quiet", rowText(Row.Fan, right).value)
        assertEquals(Effect.SetFan(FanTier.Performance), reduce(stock, Meaning.MoveLeft).second)
        // A steps forward, and the ends wrap.
        val performance = reduce(reduce(right, Meaning.MoveRight).first, Meaning.Activate)
        assertEquals(Effect.SetFan(FanTier.Performance), performance.second)
        assertEquals(Effect.SetFan(FanTier.Quiet), reduce(performance.first, Meaning.MoveRight).second)
        // During a game's override Settings shows the player's tier; choosing one ends the override.
        val game = model(extra = mapOf("fan" to "performance", "fanGame" to "performance", "fanBaseline" to "quiet")).onSetting(Row.Fan)
        assertEquals("Quiet", rowText(Row.Fan, game).value)
        val chosen = reduce(game, Meaning.MoveRight).first
        assertEquals(FanTier.Balanced, chosen.device?.fan)
        assertNull(chosen.device?.fanGame)
        assertEquals(SettingsCopy.change, settingsHints(game).first().label)
    }

    @Test fun stickLightsStepThroughTheirModes() {
        val lights = model().onSetting(Row.StickLights)
        assertEquals(RowText("Stick lights", "Breathe"), rowText(Row.StickLights, lights))
        val (usb, effect) = reduce(lights, Meaning.MoveRight)
        assertEquals(Effect.SetStickLights(StickLights.Usb), effect)
        assertEquals("Plugged in", rowText(Row.StickLights, usb).value)
        assertEquals(Effect.SetStickLights(StickLights.Off), reduce(usb, Meaning.MoveRight).second)
        assertEquals(Effect.SetStickLights(StickLights.Charging), reduce(reduce(usb, Meaning.MoveRight).first, Meaning.MoveRight).second)
    }

    @Test fun r1SplitsBrightnessAndShowsTheFanInForce() {
        val m = model(extra = mapOf("fan" to "performance", "fanGame" to "performance", "fanBaseline" to "quiet"))
        val rows = rightRows(m.notices, m.device)
        assertEquals(listOf(Row.Brightness, Row.BottomBrightness, Row.FanNow), rows.drop(quickSettings().size))
        // R1 still opens on the top screen's slider.
        assertEquals(Row.Brightness, rows[rightStart()])
        assertEquals(DeviceCopy.top, rowText(Row.Brightness, m).label)
        assertEquals(RowText(DeviceCopy.bottom, "25%"), rowText(Row.BottomBrightness, m))
        assertEquals(RowText("Fan", "Performance"), rowText(Row.FanNow, m))
        val open = reduce(m, Meaning.RightPanel).first
        assertEquals(rightStart(), open.panel?.index)
        // Down to the bottom slider: no Android permission to ask, and left and right move it.
        val bottom = reduce(open, Meaning.MoveDown).first
        assertEquals(listOf(PromptKey.DpadSides), panelHints(bottom)?.take(1)?.map { it.key })
        val (up, effect) = reduce(bottom, Meaning.MoveRight)
        assertEquals(Effect.SetBottomBrightness(30), effect)
        assertEquals("30%", rowText(Row.BottomBrightness, up).value)
        assertEquals(0.3f, up.device!!.brightnessBottom!!, 0.0001f)
        assertEquals(Effect.SetBottomBrightness(20), reduce(bottom, Meaning.MoveLeft).second)
        assertNull(reduce(bottom, Meaning.Activate).second)
        // The fan row steps from the tier in force, and it ends the override.
        val fan = reduce(up, Meaning.MoveDown).first
        assertEquals(Effect.SetFan(FanTier.Quiet), reduce(fan, Meaning.MoveRight).second)
        // Only fan: no second slider, and the top one keeps its old name.
        val fanOnly = model(extra = mapOf("capabilities" to arrayOf("fan")))
        assertEquals(listOf(Row.Brightness, Row.FanNow), rightRows(fanOnly.notices, fanOnly.device).drop(quickSettings().size))
        assertEquals(BrightnessCopy.row, rowText(Row.Brightness, fanOnly).label)
    }

    @Test fun aGamesFanStepsFromDefaultAndPersistsThroughItsEffect() {
        val tile = TileActions(game = "rom", gameFan = GameFanSetting(), openWith = PlayerChoiceRow(emptyList(), PlayerChoice("a", label = "A")))
        assertEquals(listOf(Row.OpenWith, Row.GameFan), actionRows(tile).filter { it == Row.OpenWith || it == Row.GameFan })
        val m = model().copy(tileActions = tile)
        val menu = m.copy(panel = tileMenu(m.focus, m.layout).copy(index = actionRows(tile).indexOf(Row.GameFan)))
        assertEquals(RowText("Fan", "Default"), rowText(Row.GameFan, menu))
        val (quiet, effect) = reduce(menu, Meaning.MoveRight)
        assertEquals(Effect.SetGameFan("rom", FanTier.Quiet), effect)
        assertEquals("Quiet", rowText(Row.GameFan, quiet).value)
        assertEquals(Effect.SetGameFan("rom", FanTier.Performance), reduce(menu, Meaning.MoveLeft).second)
        assertEquals(Effect.SetGameFan("rom", null), reduce(menu.copy(tileActions = tile.copy(gameFan = GameFanSetting(FanTier.Performance))), Meaning.MoveRight).second)
        // The menu stays open on the row.
        assertEquals(menu.panel?.index, quiet.panel?.index)
    }

    @Test fun aOneScreenLayoutLightsAPanelBeforeAGameTakesItAndDarkensItAfter() {
        // Two screens: both lit, whatever runs.
        for (layout in listOf(ScreenLayout.ShowcaseTop, ScreenLayout.ShowcaseBottom)) {
            for (held in listOf(emptySet(), setOf(HostScreen.Top), HostScreen.entries.toSet())) assertEquals(DeviceScreens.Dual, wantedScreens(layout, held))
        }
        // Top only: the bottom is off while nothing takes it.
        assertEquals(DeviceScreens.Top, wantedScreens(ScreenLayout.TopOnly, emptySet()))
        assertEquals(DeviceScreens.Top, wantedScreens(ScreenLayout.TopOnly, setOf(HostScreen.Top)))
        // A both-screen game, or one opened on the dark screen with X, lights it first.
        assertEquals(DeviceScreens.Dual, wantedScreens(ScreenLayout.TopOnly, HostScreen.entries.toSet()))
        assertEquals(DeviceScreens.Dual, wantedScreens(ScreenLayout.TopOnly, setOf(HostScreen.Bottom)))
        // Bottom only is its mirror.
        assertEquals(DeviceScreens.Bottom, wantedScreens(ScreenLayout.BottomOnly, emptySet()))
        assertEquals(DeviceScreens.Bottom, wantedScreens(ScreenLayout.BottomOnly, setOf(HostScreen.Bottom)))
        assertEquals(DeviceScreens.Dual, wantedScreens(ScreenLayout.BottomOnly, setOf(HostScreen.Top)))
    }

    @Test fun withPoweredScreensBottomOnlyIsOfferedAndGamesOpenOnTheLitScreen() {
        val m = model()
        assertEquals(ScreenLayout.entries, offeredLayouts(m))
        val layout = m.onSetting(Row.ScreenLayout).copy(layout = ScreenLayout.TopOnly)
        val bottomOnly = reduce(layout, Meaning.MoveRight).first
        assertEquals(ScreenLayout.BottomOnly, bottomOnly.layout)
        assertEquals(ScreenCopy.bottomOnly, rowText(Row.ScreenLayout, bottomOnly).value)
        assertEquals(ScreenLayout.ShowcaseTop, reduce(bottomOnly, Meaning.MoveRight).first.layout)
        // A one-screen game opens on the lit screen whatever Play on says; two screens follow Play on.
        assertEquals(HostScreen.Top, m.copy(layout = ScreenLayout.TopOnly, playOn = HostScreen.Bottom).playScreen())
        assertEquals(HostScreen.Bottom, m.copy(layout = ScreenLayout.BottomOnly, playOn = HostScreen.Top).playScreen())
        assertEquals(HostScreen.Bottom, m.copy(layout = ScreenLayout.ShowcaseTop, playOn = HostScreen.Bottom).playScreen())
        // Chosen, and then the plugin leaves: it stays where it is until stepped away from.
        val gone = bottomOnly.copy(device = null, apkPlugins = emptyList())
        assertEquals(ScreenLayout.entries, offeredLayouts(gone))
        assertEquals(ScreenLayout.ShowcaseTop, reduce(gone, Meaning.MoveRight).first.layout)
        assertFalse(ScreenLayout.BottomOnly in offeredLayouts(reduce(gone, Meaning.MoveRight).first))
    }

    @Test fun bottomOnlyKeepsEveryPageOnTheBottomScreen() {
        val m = model().copy(layout = ScreenLayout.BottomOnly)
        for (from in HostScreen.entries) {
            assertEquals(HostScreen.Bottom, reduce(m, Meaning.LeftPanel, from).first.panel?.screen)
            assertEquals(HostScreen.Bottom, reduce(m, Meaning.RightPanel, from).first.panel?.screen)
            assertEquals(HostScreen.Bottom, reduce(m, Meaning.Menu, from).first.settings?.screen)
        }
        // A dialog or page meant for the dark top lands on the bottom.
        val stray = m.copy(dialog = homePrompt(HostScreen.Top), settings = SettingsPage(HostScreen.Top))
        val lit = stray.offTheDarkScreen()
        assertEquals(HostScreen.Bottom, lit.dialog?.screen)
        assertEquals(HostScreen.Bottom, lit.settings?.screen)
    }

    @Test fun theHighestTierOnTheScreensHoldsAndTheOverrideEndsWithTheLast() {
        // The override compared is the one the plugin reports, so the decision is the same whoever began it.
        assertNull(gameFanCall(null, emptyList()))
        assertNull(gameFanCall(null, listOf(null)))
        assertEquals(GameFanCall.Begin(FanTier.Quiet), gameFanCall(null, listOf(FanTier.Quiet)))
        assertNull(gameFanCall(FanTier.Quiet, listOf(FanTier.Quiet)))
        // A second game with a higher tier takes over, whichever started first; a lower one or Default doesn't.
        assertEquals(GameFanCall.Begin(FanTier.Performance), gameFanCall(FanTier.Quiet, listOf(FanTier.Performance, FanTier.Quiet)))
        assertNull(gameFanCall(FanTier.Performance, listOf(FanTier.Performance, FanTier.Balanced)))
        assertNull(gameFanCall(FanTier.Quiet, listOf(FanTier.Quiet, null)))
        // The higher one leaves: the other's tier again. The last one leaves: the override ends.
        assertEquals(GameFanCall.Begin(FanTier.Quiet), gameFanCall(FanTier.Performance, listOf(FanTier.Quiet)))
        assertEquals(GameFanCall.End, gameFanCall(FanTier.Quiet, emptyList()))
        // An override a crash left, with no game with a tier on screen: it ends.
        assertEquals(GameFanCall.End, gameFanCall(FanTier.Performance, listOf(null)))
        assertEquals(FanTier.Performance, highestFan(listOf(FanTier.Balanced, null, FanTier.Performance, FanTier.Quiet)))
    }

    @Test fun poweredPanelsSayWhetherAStartMustWait() {
        assertTrue(DeviceScreens.Dual.lights(HostScreen.entries.toSet()))
        assertTrue(DeviceScreens.Top.lights(setOf(HostScreen.Top)))
        assertFalse(DeviceScreens.Top.lights(setOf(HostScreen.Bottom)))
        assertFalse(DeviceScreens.Top.lights(HostScreen.entries.toSet()))
        assertTrue(DeviceScreens.Bottom.lights(setOf(HostScreen.Bottom)))
        assertFalse(DeviceScreens.Bottom.lights(setOf(HostScreen.Top)))
        assertTrue(DeviceScreens.Top.lights(emptySet()))
    }

    @Test fun lightsWithoutAColorSayWhereToSetOneAndAGoesThere() {
        val refused = model().let { it.copy(device = it.device!!.copy(lightsNeedColor = true)) }.onSetting(Row.StickLights)
        assertEquals(DeviceCopy.lightsNeedColor, settingsRowCaption(Row.StickLights, refused))
        assertNull(settingsRowCaption(Row.StickLights, model()))
        // Only the modes that need no color are offered, both ways, so Right can't stall on Charging.
        val off = refused.copy(device = refused.device!!.copy(lights = StickLights.Off))
        val (usb, toUsb) = reduce(off, Meaning.MoveRight)
        assertEquals(Effect.SetStickLights(StickLights.Usb), toUsb)
        assertEquals(Effect.SetStickLights(StickLights.Off), reduce(usb, Meaning.MoveRight).second)
        assertEquals(Effect.SetStickLights(StickLights.Usb), reduce(off, Meaning.MoveLeft).second)
        // A mode the device is in but no longer offered steps into the offered ones. Without AYN's screen, A still steps.
        assertEquals(Effect.SetStickLights(StickLights.Off), reduce(refused, Meaning.Activate).second)
        // With it, A opens it, and left and right still step.
        val openable = refused.copy(device = refused.device!!.copy(colorSettings = true))
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.StickColor), reduce(openable, Meaning.Activate).second)
        assertEquals(SettingsCopy.open, settingsHints(openable).first().label)
        assertEquals(Effect.SetStickLights(StickLights.Off), reduce(openable, Meaning.MoveRight).second)
        // Colours may have changed: every mode again.
        assertEquals(StickLights.entries, offeredLights(model().device))
    }

    @Test fun aQuickSecondStepStartsFromTheFirst() {
        // The model shows each step at once, and the next one steps from it, not from the device.
        val quiet = model(extra = mapOf("fan" to "quiet", "fanBaseline" to "quiet")).onSetting(Row.Fan)
        val (balanced, first) = reduce(quiet, Meaning.MoveRight)
        assertEquals(Effect.SetFan(FanTier.Balanced), first)
        assertEquals(Effect.SetFan(FanTier.Performance), reduce(balanced, Meaning.MoveRight).second)
    }

    @Test fun anOpenMenuDropsItsFanRowWhenThePluginGoes() {
        val tile = TileActions(game = "rom", gameFan = GameFanSetting(FanTier.Performance), playTime = 5L)
        val m = model().copy(tileActions = tile)
        val rows = actionRows(tile)
        val open = retainMenuRows(m, m.copy(panel = tileMenu(m.focus, m.layout).copy(index = rows.indexOf(Row.GameFan))))
        assertTrue(Row.GameFan in panelRows(open.panel!!, open))
        val gone = retainMenuRows(open, open.copy(device = null, tileActions = tile.copy(gameFan = null)))
        val left = panelRows(gone.panel!!, gone)
        assertFalse(Row.GameFan in left)
        assertTrue(gone.panel!!.index in left.indices)
    }

    @Test fun thePluginPageAsksForConsentThenSaysWhatRootNeeds() {
        val page = SettingsInner.ApkPlugin(thor)
        val off = model(device = null, granted = false)
        val grant = Row.PluginGrant(thor, DEVICE_SCOPE)
        assertEquals(listOf(Row.PluginInstall(thor), grant), settingsInnerRows(page, off))
        assertEquals(RowText(DeviceCopy.controlDevice), rowText(grant, off))
        assertEquals(DeviceCopy.consent, settingsRowCaption(grant, off))
        assertEquals(ApkPluginCopy.off, pluginListing(page, off)?.value)
        // Granted, but Magisk hasn't let it in.
        val denied = model(parseDeviceState(thor, reply + ("root" to "denied")))
        val root = Row.DeviceRoot(thor)
        assertEquals(listOf(Row.PluginInstall(thor), grant, root), settingsInnerRows(page, denied))
        assertEquals(RowText(DeviceCopy.rootAccess, DeviceCopy.rootDenied), rowText(root, denied))
        assertEquals(RowControl.Value(DeviceCopy.rootDenied, attention = true), rowControl(root, denied))
        assertEquals("Magisk won’t ask. In Magisk › Superuser, tap + and add Foldcade Thor, then check again.", settingsRowCaption(root, denied))
        assertEquals(PluginListing("Foldcade Thor", "1.0", DeviceCopy.needsRoot, attention = true, verified = true), pluginListing(page, denied))
        val onRoot = denied.copy(settings = SettingsPage(HostScreen.Top, settingsCategories(denied).indexOf(SettingsCategory.Plugins), 2, onRows = true, inner = page))
        assertEquals(DeviceCopy.checkAgain, settingsHints(onRoot).first().label)
        assertEquals(Effect.CheckDevice(thor), reduce(onRoot, Meaning.Activate).second)
        // Where Magisk opens, A goes there, and the caption leaves out Check again.
        val magisk = onRoot.copy(device = onRoot.device!!.copy(rootSettings = true))
        assertEquals(DeviceCopy.openMagisk, settingsHints(magisk).first().label)
        assertEquals(Effect.OpenAndroidSetting(AndroidSetting.RootManager), reduce(magisk, Meaning.Activate).second)
        assertEquals("Magisk won’t ask. In Magisk › Superuser, tap + and add Foldcade Thor.", settingsRowCaption(root, magisk))
        val absent = model(parseDeviceState(thor, reply + ("root" to "absent")))
        assertEquals(DeviceCopy.rootMissing, settingsRowCaption(root, absent))
        // Root granted: the row leaves.
        assertFalse(settingsInnerRows(page, model()).any { it is Row.DeviceRoot })
        // A plugin for a device protocol 1 doesn't know says so.
        val other = off.copy(apkPlugins = listOf(plugin.copy(device = "other", supported = false)))
        assertEquals(DeviceCopy.unsupported, settingsInnerDetail(page, other))
    }

    @Test fun playOnLeavesSystemWhereItCanDoNothing() {
        // A one-screen layout with its dark panel powered off opens games on the lit screen.
        for (layout in listOf(ScreenLayout.TopOnly, ScreenLayout.BottomOnly)) {
            assertFalse(Row.PlayOn in settingsRows(SettingsCategory.Screens, model().copy(layout = layout)))
            // Without a plugin that powers panels, Play on still chooses, as today.
            assertTrue(Row.PlayOn in settingsRows(SettingsCategory.Screens, PickerModel(count = 1, rowsPerPage = 1, layout = layout)))
        }
        assertTrue(Row.PlayOn in settingsRows(SettingsCategory.Screens, model().copy(layout = ScreenLayout.ShowcaseTop)))
    }

    @Test fun theRegistryOffersFoldcadeThor() {
        val source = PluginRegistry.thor
        assertEquals("app.foldcade.thor", source.packageName)
        assertEquals("predmore/foldcade-thor", source.repository)
        assertEquals("foldcade-thor.apk", source.asset)
        assertEquals("Thor", settingsTileLabel(SettingsInner.PluginCatalog(source.packageName), PickerModel(count = 1, rowsPerPage = 1)))
        assertTrue(model().apkPlugins.single().verified)
    }
}
