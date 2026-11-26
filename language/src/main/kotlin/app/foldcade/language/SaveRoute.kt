package app.foldcade.language

/**
 * What Y › Saves needs to say how a game's saves can leave this device (design §7.1). Every
 * field is a fact the host already has; nothing here is a guess about a partner's cloud.
 */
data class SaveRouteFacts(
    val status: SaveStatus,
    /** The game has a RomM copy, so RomM can keep its saves once the emulator syncs. */
    val onRomm: Boolean,
    /** A RomM server is signed in on this device. */
    val rommConnected: Boolean,
    /** The emulator that plays the game now, by name. Null with none installed. */
    val player: String?,
    /** That emulator is one Foldcade syncs for this game. */
    val playerSyncs: Boolean,
    /** An installed emulator Foldcade syncs for this game, when [player] isn't one. */
    val supported: PlayerChoice? = null,
    /** An emulator Foldcade syncs for this system that isn't installed, when none installed is. */
    val installable: String? = null,
    /** The local copy's file name, exactly as RomM must have it. */
    val fileName: String? = null,
    /** The system as Foldcade names it, such as `Nintendo DS`. */
    val system: String? = null,
    /** The playing emulator's id, for sentences that differ by emulator. */
    val playerId: String? = null,
    /** The game is already set to [supported], but [player] still has it open: the switch waits for it to close. */
    val switchPending: Boolean = false,
)

/** A checklist in the Fix page's marks: ✓ done, ▸ now, ○ after that. */
enum class SaveRouteMark { Done, Now, Next }

sealed interface SaveRouteAction {
    /** Play this game with [choice], as Y › Open with does. Asked first (§7.1). */
    data class UsePlayer(val choice: PlayerChoice) : SaveRouteAction
    /** The emulator's save setup, as the Saves chip's Set up does. */
    data object SetUp : SaveRouteAction
}

data class SaveRouteStep(val title: String, val detail: String?, val mark: SaveRouteMark, val action: SaveRouteAction? = null)

data class SaveRoute(
    /** One sentence: where the saves are, and why. */
    val summary: String,
    /** The way to RomM, in order. Empty when Foldcade has no way to offer. */
    val steps: List<SaveRouteStep>,
    /** What happens to saves already here, said once. */
    val note: String? = null,
    /** The line over a RomM game's list when its emulator can't sync: the way on, in one sentence. */
    val banner: String? = null,
) {
    /** What A does on the page: the step being done now, when Foldcade can do it. */
    val action: SaveRouteAction? get() = steps.firstOrNull { it.mark == SaveRouteMark.Now }?.action
}

/**
 * The route for a game whose saves can't reach RomM from here, or null when the ordinary Saves
 * page already tells the whole story: a RomM game played with an emulator Foldcade syncs.
 *
 * A problem (an incident) keeps its own Fix route and is never covered here. Partners, streams
 * and a missing emulator are explained without steps, since Foldcade can't change where those
 * saves go. Every other step is something the user can do today.
 */
fun saveRoute(facts: SaveRouteFacts): SaveRoute? {
    return facts.route()
}

private fun SaveRouteFacts.route(): SaveRoute? {
    if (status is SaveStatus.Problem) return null
    when (status) {
        is SaveStatus.Unconfirmed -> when (status.why) {
            UnconfirmedWhy.PartnerCloud -> return SaveRoute(SaveRouteCopy.partner(status.keeper as? SaveKeeper.Partner), emptyList())
            UnconfirmedWhy.HostComputer -> return SaveRoute(SaveRouteCopy.hostComputer, emptyList())
            UnconfirmedWhy.AppKeepsSaves -> return SaveRoute(SaveRouteCopy.appKeepsSaves, emptyList())
            else -> Unit
        }
        else -> Unit
    }
    val launchOnly = player != null && !playerSyncs
    if (onRomm && !launchOnly) return null
    val steps = buildList {
        // 1. An emulator Foldcade syncs.
        when {
            player != null && playerSyncs -> add(SaveRouteStep(SaveRouteCopy.stepEmulator, SaveRouteCopy.syncs(player), SaveRouteMark.Done))
            // Chosen already, while the old emulator still holds the game: nothing to press again.
            supported != null && switchPending && player != null ->
                add(SaveRouteStep(SaveRouteCopy.playWith(supported.label), SaveRouteCopy.afterClose(supported.label, player), SaveRouteMark.Now))
            supported != null -> add(SaveRouteStep(SaveRouteCopy.playWith(supported.label), SaveRouteCopy.syncs(supported.label), SaveRouteMark.Now,
                SaveRouteAction.UsePlayer(supported)))
            installable != null -> add(SaveRouteStep(SaveRouteCopy.install(installable), SaveRouteCopy.installDetail(installable, system), SaveRouteMark.Now))
            // Nothing Foldcade syncs plays this system, so no later step could finish.
            else -> return SaveRoute(if (player != null) SaveRouteCopy.cantSyncYet(player) else SaveRouteCopy.noPlayer, emptyList(),
                if (onRomm) null else SaveRouteCopy.notOnRommAlso, player?.let(SaveRouteCopy::cantSyncYet))
        }
        // 2. Its setup, when that is what stands in the way.
        if (player != null && playerSyncs && status is SaveStatus.NotSyncing) add(setupStep(status, player, playerId == "azahar"))
        // 3. A RomM copy with the same file name (§7.1, the same-name route).
        if (!onRomm) add(SaveRouteStep(
            if (rommConnected) SaveRouteCopy.stepRomm else SaveRouteCopy.stepSignInRomm,
            SaveRouteCopy.rommDetail(fileName, system, rommConnected),
            if (none { it.mark == SaveRouteMark.Now }) SaveRouteMark.Now else SaveRouteMark.Next,
        ))
    }
    val summary = when {
        launchOnly -> SaveRouteCopy.cantSync(player!!)
        player == null -> SaveRouteCopy.noPlayer
        else -> SaveRouteCopy.notOnRomm
    }
    // Saves a launch-only emulator made stay with it, so that is the one thing to say about switching.
    val note = if (launchOnly) SaveRouteCopy.staysIn(player!!) else SaveRouteCopy.nextTime
    val banner = if (!launchOnly) null else when {
        supported != null && switchPending -> SaveRouteCopy.afterCloseOnRomm(supported.label, player!!)
        supported != null -> SaveRouteCopy.banner(player!!, supported.label)
        else -> SaveRouteCopy.bannerInstall(player!!, installable!!)
    }
    return SaveRoute(summary, steps, note, banner)
}

private fun setupStep(status: SaveStatus.NotSyncing, player: String, azahar: Boolean): SaveRouteStep = when {
    // A game file with no save place arrives as NoSlot too, and setup can't fix it: its reason comes first.
    status.spot != null -> SaveRouteStep(SaveRouteCopy.stepSpot, status.detail ?: SaveProtectionCopy.spot(status.spot, azahar), SaveRouteMark.Now)
    status.issue != null -> SaveRouteStep(SaveRouteCopy.setUp(player), status.detail, SaveRouteMark.Now, SaveRouteAction.SetUp)
    else -> SaveRouteStep(saveStatusLine(status), null, SaveRouteMark.Now)
}

/**
 * Where a game's saves can go, from structure alone, as Settings counts it (§7.1): an emulator that
 * can't sync, or a game RomM doesn't have. Bytes still queued from an earlier emulator don't change
 * where the next save goes, so a waiting upload never hides it.
 */
fun savesOnlyHere(emulator: Boolean, syncs: Boolean, onRomm: Boolean): Boolean = emulator && (!syncs || !onRomm)

/** Y › Saves' words for saves that stay on this device. The voice is [SaveSetupCopy]'s. */
object SaveRouteCopy {
    const val notOnRomm = "RomM doesn’t have this game, so its saves stay on this device."
    fun cantSync(player: String) = "Foldcade can’t sync $player, so its saves stay on this device."
    fun cantSyncYet(player: String) = "Foldcade can’t sync $player yet, so its saves stay on this device."
    const val noPlayer = "No emulator Foldcade syncs is installed for this game."
    const val notOnRommAlso = "RomM doesn’t have this game either."

    const val stepEmulator = "Play with an emulator Foldcade syncs"
    fun syncs(player: String) = "Foldcade syncs $player’s saves."
    fun playWith(player: String) = "Play with $player"
    fun use(player: String) = "Use $player"
    fun install(player: String) = "Install $player"
    fun installDetail(player: String, system: String?) =
        if (system != null) "Foldcade syncs $player’s saves for $system." else syncs(player)
    fun setUp(player: String) = "Set up $player’s saves"
    const val stepSpot = "Use a game file that syncs"
    const val stepRomm = "Add this game to RomM"
    const val stepSignInRomm = "Sign in to RomM, then add this game"
    /** The same-name route: RomM must list the same file for the same system (`SameGames.kt`). */
    fun rommDetail(fileName: String?, system: String?, connected: Boolean): String {
        val upload = when {
            fileName != null && system != null -> "Upload $fileName for $system, with the same file name."
            fileName != null -> "Upload $fileName, with the same file name."
            else -> "Upload it with the same file name and system."
        }
        return if (connected) upload else "Settings › Library › Add › RomM server. Then ${upload.replaceFirstChar(Char::lowercase)}"
    }
    /** What finishing the steps does, as `romm.md` "Save sync" promises for a folded game. */
    const val nextTime = "The next time you play, Foldcade puts this save on RomM. If RomM already has a different one, Foldcade keeps both and asks which to play first."
    /** Names such as "My Boy!" end in their own mark, so a name never ends a sentence here. */
    fun staysIn(player: String) = "$player keeps the saves made in it. Foldcade can’t move them."

    /** Partners: only what Foldcade knows. No partner gives Foldcade a receipt yet (§8.3). */
    fun partner(keeper: SaveKeeper.Partner?): String = when {
        keeper?.cloud != null -> "${keeper.cloud} keeps these saves when it’s on for the game. Foldcade can’t check it yet."
        keeper != null -> "${keeper.name} keeps these saves. Foldcade can’t check its cloud yet."
        else -> "The player keeps these saves. Foldcade can’t check them yet."
    }
    const val hostComputer = "These saves stay on your computer, or with the game’s own cloud."
    const val appKeepsSaves = "This app keeps its own saves. Foldcade can’t see them."

    // The confirmation before switching emulators.
    fun switchTitle(player: String) = "Play with $player?"
    fun switchBody(to: String, from: String?, playedHere: Boolean): String = listOfNotNull(
        syncs(to),
        from?.takeIf { playedHere }?.let(::staysIn),
        "Change it any time in Y › Open with.",
    ).joinToString(" ")
    fun switched(player: String) = "$player plays this game now."
    /** A switch made while the old emulator still holds the game. Names never end these sentences. */
    fun afterClose(next: String, running: String) = "Once $running closes, $next plays this game."
    fun afterCloseOnRomm(next: String, running: String) = "Once $running closes, $next plays this game and keeps its saves on RomM."
    /** A on a RomM save while a launch-only emulator plays the game. */
    fun cantLoad(player: String, supported: String?) =
        if (supported != null) "$player can’t load RomM saves. Play with $supported first." else "$player can’t load RomM saves through Foldcade."
    /** The line over the list when the game is on RomM but its emulator can't sync. */
    fun banner(player: String, supported: String?) =
        if (supported != null) "Foldcade can’t sync $player, so play with $supported to keep these saves on RomM." else cantSyncYet(player)
    fun bannerInstall(player: String, installable: String) = "Foldcade can’t sync $player, so install $installable to keep these saves on RomM."
}
