package app.foldcade

import app.foldcade.api.plugin.SaveFolderHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveFolderFinderTest {
    private val directories = mutableSetOf<String>()
    private val names = mutableMapOf<String, List<String>>()
    private val playerGrants = mutableSetOf<String>()
    private val placesOnDisk = mutableSetOf<String>()

    private fun finder(vararg grants: SaveFolderGrant) = SaveFolderFinder(
        grants.toList(),
        { folderDocumentId(it) in directories },
        { names[folderDocumentId(it)] },
        { it in playerGrants },
        { it in placesOnDisk },
    )

    @Test fun equalTreeIsUsedAsGiven() {
        directories += "primary:RetroArch/saves"
        val tree = tree("primary:RetroArch/saves")
        assertEquals(tree, finder(grant(tree)).find(holder("RetroArch/saves")).folder)
    }

    @Test fun ancestorTreeUsesADocumentInsideTheClosestGrant() {
        directories += "primary:saves/nds"
        val result = finder(grant(tree("primary:")), grant(tree("primary:saves"))).find(holder("saves/nds"))
        assertEquals(folderDocumentUri(tree("primary:saves"), "primary:saves/nds"), result.folder)
    }

    @Test fun volumeRootCoversItsChildren() {
        directories += "primary:saves/nds"
        assertEquals(folderDocumentUri(tree("primary:"), "primary:saves/nds"), finder(grant(tree("primary:"))).find(holder("saves/nds")).folder)
    }

    @Test fun otherVolumesAndNamePrefixesDoNotCoverThePlace() {
        directories += "primary:saves/nds"
        assertNull(finder(grant(tree("1234:saves")), grant(tree("primary:save"))).find(holder("saves/nds")).folder)
    }

    @Test fun readOnlyAndWriteOnlyTreesAreNotReachable() {
        directories += "primary:saves/nds"
        for (g in listOf(SaveFolderGrant(tree("primary:saves"), true, false), SaveFolderGrant(tree("primary:saves"), false, true))) {
            assertNull(finder(g).find(holder("saves/nds")).folder)
        }
    }

    @Test fun aReadGrantCountsOnlyAfterItsUpgradeSucceeds() {
        assertTrue(upgradeTreeGrant {})
        assertFalse(upgradeTreeGrant { throw SecurityException("read only") })
    }

    @Test fun markersMustPassAndAnUnlistableFolderDoesNotCount() {
        val h = holder("Azahar", markers = setOf("sdmc"))
        directories += "primary:Azahar"
        val f = finder(grant(tree("primary:Azahar")))
        assertNull(f.find(h).folder)
        names["primary:Azahar"] = listOf("roms")
        assertNull(f.find(h).folder)
        names["primary:Azahar"] = listOf("SDMC")
        assertEquals(tree("primary:Azahar"), f.find(h).folder)
        directories.clear()
        assertNull(f.find(h).folder)
    }

    @Test fun aFolderPickedInThePlayerNeedsItsReadWriteGrant() {
        directories += "primary:saves/nds"
        val h = holder("saves/nds", picked = true)
        val f = finder(grant(tree("primary:saves/nds")))
        assertNull(f.find(h).folder)
        playerGrants += "primary:saves/nds"
        assertEquals(tree("primary:saves/nds"), f.find(h).folder)
    }

    @Test fun theThorStaleAzaharCopyNeverWinsOverThePlayersRealFolder() {
        directories += listOf("primary:saves/Azahar", "primary:Azahar")
        directories.forEach { names[it] = listOf("config", "nand", "sdmc") }
        playerGrants += "primary:saves/Azahar"
        val result = finder(grant(tree("primary:"))).find(holder("saves/Azahar", "Azahar", markers = setOf("sdmc"), picked = true))
        assertEquals(folderDocumentUri(tree("primary:"), "primary:saves/Azahar"), result.folder)
        assertEquals(primaryFolderDocument("saves/Azahar"), result.startingFolder)
    }

    @Test fun azaharWithGrantsOnBothCopiesRemainsAmbiguous() {
        directories += listOf("primary:saves/Azahar", "primary:Azahar")
        directories.forEach { names[it] = listOf("sdmc") }
        playerGrants += directories
        val h = holder("saves/Azahar", "Azahar", markers = setOf("sdmc"), picked = true)
        assertNull(finder(grant(tree("primary:"))).find(h).folder)
    }

    @Test fun aPlayerGivenBothCopiesStaysAmbiguousWhenFoldcadeReachesOnlyOne() {
        directories += listOf("primary:saves/Azahar", "primary:Azahar")
        directories.forEach { names[it] = listOf("sdmc") }
        playerGrants += directories
        val h = holder("saves/Azahar", "Azahar", markers = setOf("sdmc"), picked = true)
        val result = finder(grant(tree("primary:saves"))).find(h)
        assertNull(result.folder)
        assertEquals(primaryFolderDocument("saves/Azahar"), result.placeStart)
    }

    @Test fun aRootPickIsNarrowedToItsChildWithMarkers() {
        val root = tree("primary:")
        directories += listOf("primary:", "primary:Azahar")
        names["primary:"] = listOf("Azahar", "Music")
        names["primary:Azahar"] = listOf("sdmc")
        val h = holder(markers = setOf("sdmc"))
        assertEquals(folderDocumentUri(root, "primary:Azahar"), finder().narrow(h, root))
    }

    @Test fun placeStartIsOnlyAPlaceThatIsThere() {
        assertNull(finder().find(holder("one")).placeStart)
        placesOnDisk += "one"
        assertEquals(primaryFolderDocument("one"), finder().find(holder("one")).placeStart)
    }

    @Test fun twoPlausibleFoldersAreAmbiguousEvenWhenOrdered() {
        directories += listOf("primary:one", "primary:two")
        val f = finder(grant(tree("primary:")))
        val h = holder("one", "two")
        assertNull(f.find(h).folder)
        assertEquals(primaryFolderDocument("one"), f.find(h).startingFolder)
        playerGrants += "primary:two"
        assertEquals(folderDocumentUri(tree("primary:"), "primary:two"), f.find(h).folder)
        playerGrants += "primary:one"
        assertNull(f.find(h).folder)
    }

    @Test fun startingFolderPrefersCountingThenExistingThenSuggestionThenFirstPlace() {
        val h = holder("one", "two", suggested = "content://suggestion")
        assertEquals("content://suggestion", finder().find(h).startingFolder)
        assertEquals(primaryFolderDocument("one"), finder().find(holder("one", "two")).startingFolder)
        placesOnDisk += "two"
        assertEquals(primaryFolderDocument("two"), finder().find(h).startingFolder)
        directories += "primary:one"
        assertEquals(primaryFolderDocument("one"), finder(grant(tree("primary:"))).find(h).startingFolder)
        assertNull(finder().find(holder()).startingFolder)
    }

    @Test fun aBroadPickNarrowsToAContainedKnownSaveFolder() {
        directories += "primary:saves/nds"
        playerGrants += "primary:saves/nds"
        assertEquals(folderDocumentUri(tree("primary:saves"), "primary:saves/nds"), finder().narrow(holder("saves/nds", picked = true), tree("primary:saves")))
    }

    @Test fun aPickThatAlreadyHasThePlayersMarkersStaysChosen() {
        directories += "primary:Azahar/child"
        names["primary:Azahar"] = listOf("sdmc", "child")
        names["primary:Azahar/child"] = listOf("sdmc")
        assertEquals(tree("primary:Azahar"), finder().narrow(holder("Azahar/child", markers = setOf("sdmc")), tree("primary:Azahar")))
    }

    @Test fun aBroadPickNarrowsToOneChildWithMarkers() {
        names["primary:saves"] = listOf("custom", "notes")
        names["primary:saves/custom"] = listOf("sdmc")
        directories += listOf("primary:saves/custom", "primary:saves/notes")
        assertEquals(folderDocumentUri(tree("primary:saves"), "primary:saves/custom"), finder().narrow(holder(markers = setOf("sdmc")), tree("primary:saves")))
        names["primary:saves/notes"] = listOf("sdmc")
        assertNull(finder().narrow(holder(markers = setOf("sdmc")), tree("primary:saves")))
    }

    @Test fun aDocumentPickDoesNotNarrowOutsideItsSelectedFolder() {
        directories += "primary:saves/nds"
        val picked = folderDocumentUri(tree("primary:"), "primary:other")
        assertEquals(picked, finder().narrow(holder("saves/nds"), picked))
    }

    @Test fun aBroadPickDoesNotChooseBetweenKnownSaveFolders() {
        directories += listOf("primary:one", "primary:two")
        assertNull(finder().narrow(holder("one", "two"), tree("primary:")))
    }

    @Test fun anEmptyExplicitSaveFolderNeedsNoSaveFile() {
        assertEquals(tree("primary:empty"), finder().narrow(holder("saves/nds", picked = true), tree("primary:empty")))
    }

    @Test fun aPickedFolderStaysPickedThroughRestartAndDetection() {
        val prefs = MemoryPrefs()
        val store = SessionStore(prefs)
        store.setPlayerSaveFolder("azahar", tree("primary:found"), found = true)
        assertTrue(SessionStore(prefs).playerSaveFolderFound("azahar"))
        store.setPlayerSaveFolder("azahar", tree("primary:chosen"))
        val restarted = SessionStore(prefs)
        assertFalse(restarted.playerSaveFolderFound("azahar"))
        val saved = SavedSaveFolder(restarted.playerSaveFolder("azahar")!!, restarted.playerSaveFolderFound("azahar"))
        assertEquals(saved, detectedSaveFolder(saved, { error("must not replace a user pick") }))
    }

    @Test fun lostReachClearsAFoundFolderAndAllowsDetectionAgain() {
        val store = SessionStore(MemoryPrefs())
        val saved = SavedSaveFolder(tree("primary:old"), found = true)
        assertEquals(saved, detectedSaveFolder(saved, { saved.uri }))
        val lost = detectedSaveFolder(saved, { null })
        store.setPlayerSaveFolder("azahar", lost?.uri, lost?.found == true)
        assertNull(store.playerSaveFolder("azahar"))
        assertFalse(store.playerSaveFolderFound("azahar"))
        assertEquals(SavedSaveFolder(tree("primary:new"), true), detectedSaveFolder(lost, { tree("primary:new") }))
    }

    @Test fun realSafGrantsRankBeforeTheAllFilesTreeInEitherOrder() {
        val allFiles = SaveFolderGrant(SharedStorageProvider.tree("app.foldcade"), true, true, allFiles = true)
        directories += "primary:RetroArch/saves"
        for (saf in listOf(grant(tree("primary:")), grant(tree("primary:RetroArch/saves")))) {
            for (grants in listOf(listOf(allFiles, saf), listOf(saf, allFiles))) {
                val found = finder(*grants.toTypedArray()).find(holder("RetroArch/saves"))
                assertEquals(if (folderTreeId(saf.uri) == "primary:") folderDocumentUri(saf.uri, "primary:RetroArch/saves") else saf.uri, found.folder)
            }
        }
        assertEquals(folderDocumentUri(allFiles.uri, "primary:RetroArch/saves"), finder(allFiles).find(holder("RetroArch/saves")).folder)
    }

    @Test fun allFilesStillNeedsThePlayersGrantForMelonDs() {
        val allFiles = SaveFolderGrant(SharedStorageProvider.tree("app.foldcade"), true, true, allFiles = true)
        directories += "primary:saves/nds"
        val h = holder("saves/nds", picked = true)
        assertNull(finder(allFiles).find(h).folder)
        playerGrants += "primary:saves/nds"
        assertEquals(folderDocumentUri(allFiles.uri, "primary:saves/nds"), finder(allFiles).find(h).folder)
    }

    @Test fun allFilesMakesTheThorsTwoAzaharGrantsAmbiguousAndClearsAnEarlierFind() {
        val allFiles = SaveFolderGrant(SharedStorageProvider.tree("app.foldcade"), true, true, allFiles = true)
        val saf = grant(tree("primary:saves/Azahar"))
        directories += listOf("primary:saves/Azahar", "primary:Azahar")
        directories.forEach { names[it] = listOf("config", "nand", "sdmc") }
        playerGrants += directories
        val h = holder("saves/Azahar", "Azahar", markers = setOf("config", "nand", "sdmc"), picked = true)
        // Found earlier, when only the real copy had the player's grant.
        val saved = SavedSaveFolder(tree("primary:saves/Azahar"), found = true)
        assertNull(finder(saf).find(h).folder)
        val result = finder(saf, allFiles).find(h)
        assertNull(result.folder)
        assertEquals(2, result.candidates.size)
        assertEquals(primaryFolderDocument("saves/Azahar"), result.startingFolder)
        assertNull(detectedSaveFolder(saved, { result.folder }))
        val picked = saved.copy(found = false)
        assertEquals(picked, detectedSaveFolder(picked, { result.folder }))
    }

    @Test fun allFilesRevocationClearsItsFoundFolderOrUsesSafWithoutReplacingAPick() {
        val allFiles = SaveFolderGrant(SharedStorageProvider.tree("app.foldcade"), true, true, allFiles = true)
        val h = holder("RetroArch/saves")
        directories += "primary:RetroArch/saves"
        val saved = SavedSaveFolder(finder(allFiles).find(h).folder!!, found = true)
        val lost = detectedSaveFolder(saved, { finder().find(h).folder })
        val store = SessionStore(MemoryPrefs())
        store.setPlayerSaveFolder("retroarch", saved.uri, found = true)
        store.setPlayerSaveFolder("retroarch", lost?.uri, lost?.found == true)
        assertNull(store.playerSaveFolder("retroarch"))
        assertFalse(store.playerSaveFolderFound("retroarch"))
        val saf = grant(tree("primary:RetroArch/saves"))
        assertEquals(SavedSaveFolder(saf.uri, true), detectedSaveFolder(saved, { finder(saf).find(h).folder }))
        assertEquals(SavedSaveFolder(saf.uri, true), detectedSaveFolder(saved, { finder(allFiles, saf).find(h).folder }))
        val picked = SavedSaveFolder(tree("primary:chosen"), found = false)
        assertEquals(picked, detectedSaveFolder(picked, { finder(saf).find(h).folder }))
    }

    private fun holder(vararg places: String, markers: Set<String> = emptySet(), picked: Boolean = false, suggested: String? = null) = object : SaveFolderHolder {
        override val saveFolderPlaces = places.toList()
        override val saveFolderMarkers = markers
        override val saveFolderPickedInPlayer = picked
        override val suggestedSaveFolder = suggested
        override fun bindSaveFolder(contentUri: String) = Unit
        override fun hasSaveFolder() = false
    }

    private fun grant(uri: String) = SaveFolderGrant(uri, true, true)
    private fun tree(id: String): String = "content://com.android.externalstorage.documents/tree/" + java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")
}
