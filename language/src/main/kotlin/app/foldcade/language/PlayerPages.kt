package app.foldcade.language

/**
 * Where a missing player can be got, by its name, so its dialog offers Get rather than a dead
 * end. Each is the page docs/integrations names for that player: its own site, or its GitHub
 * releases for those that publish there. A player with no page here keeps OK alone.
 */
object PlayerPages {
    private val pages = mapOf(
        // https://github.com/azahar-emu/azahar
        "Azahar" to "https://github.com/azahar-emu/azahar/releases/latest",
        // https://github.com/rafaelvcaetano/melonDS-android
        "melonDS" to "https://github.com/rafaelvcaetano/melonDS-android/releases/latest",
        // DuckStation's Android build is on its beta page only, and may not be mirrored.
        "DuckStation" to "https://downloads.duckstation.org/android-testing/",
        // https://github.com/ARMSX2/ARMSX2
        "ARMSX2" to "https://github.com/ARMSX2/ARMSX2/releases/latest",
        // https://github.com/aenu1/aps3e
        "aPS3e" to "https://github.com/aenu1/aps3e/releases/latest",
        "PPSSPP" to "https://www.ppsspp.org/download/",
        "Dolphin" to "https://dolphin-emu.org/download/",
        // Not Google Play's build, which cannot open games from other apps.
        "RetroArch" to "https://www.retroarch.com/?page=platforms",
        "Moonlight" to "https://moonlight-stream.org",
        "GameNative" to "https://github.com/utkarshdalal/GameNative/releases/latest",
        "DroidDeck" to "https://github.com/Droid-Deck/DroidDeck/releases/latest",
    )

    fun of(playerName: String): String? = pages[playerName]
}
