package app.foldcade.api.plugin

/**
 * Major version of this plugin contract.
 *
 * Public value types in this module are frozen data classes, or sealed
 * interfaces whose leaves are frozen data classes. Every property is a `val`.
 * There are no builders and no setters. Adding, removing, reordering, or
 * changing the type of a constructor property bumps this major.
 *
 * [PLUGIN_API_MINOR] is the additive revision of this major. Adding an
 * interface member with a default body bumps the minor and does not bump
 * this major. This module sets `jvmDefault` to `no-compatibility`, so that
 * member is a JVM default method. An older plugin minor of the same major
 * is safe to load. A newer plugin minor is not.
 *
 * These enums and sealed types are open. A plugin that branches on one
 * must use an `else` branch. Adding an enum value or a sealed subclass of
 * an open type bumps [PLUGIN_API_MINOR] and does not bump this major:
 * [Availability], [ArtworkRole], [StartDisplay], [LaunchFlag], [SyncOutcome],
 * [LaunchTarget], [PlayerExtra], [PluginException], and [Credential].
 * Any other enum or sealed type in this module is closed. Adding a value
 * or a subclass there bumps this major.
 *
 * [PluginException] is the error hierarchy. It is not a data class.
 *
 * This is the first versioned contract.
 */
const val PLUGIN_API_VERSION = 1

/**
 * Additive revision of [PLUGIN_API_VERSION].
 * The host loads an older or equal plugin minor and rejects a newer one.
 */
const val PLUGIN_API_MINOR = 2
