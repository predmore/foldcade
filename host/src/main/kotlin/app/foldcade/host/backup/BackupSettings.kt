package app.foldcade.host.backup

import java.net.URI

enum class BackupDisposition { BackedUp, DeviceOnly }
enum class PreferenceType(val wire: String) {
    StringValue("string"), BooleanValue("bool"), IntValue("int"), LongValue("long"), FloatValue("float"), StringSet("stringSet");
    fun accepts(value: Any): Boolean = when (this) {
        StringValue -> value is String
        BooleanValue -> value is Boolean
        IntValue -> value is Int
        LongValue -> value is Long
        FloatValue -> value is Float && value.isFinite()
        StringSet -> value is Set<*> && value.all { it is String }
    }
}

data class BackupKey(val name: String, val disposition: BackupDisposition, val type: PreferenceType? = null) {
    fun matches(key: String): Boolean = if (name.endsWith(':')) key.startsWith(name) else key == name
}

/** The only preference transfer policy. Unknown keys never travel, even from a newer writer. */
object BackupSettings {
    private val backedStrings = listOf("music_track", "background_motion", "motion_speed", "theme", "icon_theme_id",
        "home_board", "same_games", "romm_origin", "button_prompt_confirm:", "screen_layout", "play_on",
        "system_player:", "game_player:",
        // Found ROM folders the player removed stay removed after a restore.
        "removed_roots")
    private val backedBooleans = listOf("primary_top", "music_enabled", "music_default_night_route", "artwork", "button_prompt_dismissed:")
    val keys: List<BackupKey> = backedStrings.map { BackupKey(it, BackupDisposition.BackedUp, PreferenceType.StringValue) } +
        backedBooleans.map { BackupKey(it, BackupDisposition.BackedUp, PreferenceType.BooleanValue) } +
        BackupKey("music_volume", BackupDisposition.BackedUp, PreferenceType.FloatValue) +
        listOf(
            "folder_tree", "folder_explained", "home_prompted",
            // Found again on each device, whose cards have their own ids.
            "found_roots", "announced_roots", "find_games_offered", "add_games_asked", "usage_prompted", "button_prompt_seen",
            "player_save_folder:", "player_save_found:", "player_firmware_folder:", "player_save_mark:", "pending_save_return:", "last_player_start:",
            "players_holding_games", "save_awaits_load:", "retroarch_cores_folder:", "retroarch_cores_offered",
            "eden_folder:", "eden_restart_package", "eden_restart_boot", "refused_eden_firmware",
            "moonlight_pins", "moonlight_hosts", "moonlight_listed_apps", "moonlight_paired_hosts", "moonlight_addresses", "gamestream_client_id",
            "droiddeck_pins", "home_return_game", "home_board_before_collections", "restore_holds", "restore_folders", "restore_romm_sign_in", "restore_committed",
            // Retired keys are still classified: startup migrations must never become a transfer path.
            "game_screens", "platform_screens", "android_shelf_games", "android_shelf_apps", "android_shelf_hidden",
            "android_shelf_favorites", "android_shelf_shown", "moonlight_source", "moonlight_import",
            "moonlight_import_confirmed", "moonlight_import_settled", "moonlight_placements",
            "player_save_folder_skipped:", "player_hosted_saves_skipped:",
        ).map { BackupKey(it, BackupDisposition.DeviceOnly) }

    fun classify(key: String): BackupKey? = keys.singleOrNull { it.matches(key) }

    fun portable(values: Map<String, *>): Map<String, Any> = values.mapNotNull { (key, value) ->
        val rule = classify(key)?.takeIf { it.disposition == BackupDisposition.BackedUp } ?: return@mapNotNull null
        if (value == null || rule.type?.accepts(value) != true) return@mapNotNull null
        if (key == "romm_origin" && !safeOrigin(value as String)) return@mapNotNull null
        key to value
    }.toMap()

    /** A replacement, including absent keys, while every device-owned value stays as it is. */
    fun plan(current: Map<String, *>, incoming: Map<String, *>): PreferencePlan {
        val values = portable(incoming)
        return PreferencePlan(current.keys.filter { classify(it)?.disposition == BackupDisposition.BackedUp &&
            (it != "romm_origin" || values.containsKey(it)) }.toSet(), values)
    }

    private fun safeOrigin(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme in setOf("http", "https") && uri.host != null && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null
    } catch (_: Exception) { false }
}

data class PreferencePlan(val remove: Set<String>, val put: Map<String, Any>) {
    fun appliedTo(current: Map<String, *>): Map<String, Any?> = (current - remove) + put
}
