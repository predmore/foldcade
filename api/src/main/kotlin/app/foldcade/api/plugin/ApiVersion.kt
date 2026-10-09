package app.foldcade.api.plugin

/**
 * Major version of this plugin contract.
 *
 * Public value types in this module are frozen data classes, or sealed
 * interfaces whose leaves are frozen data classes. Every property is a `val`.
 * There are no builders and no setters. Adding, removing, reordering, or
 * changing the type of a constructor property bumps this major. Adding an
 * interface member with a default body does not. This module compiles with
 * `-Xjvm-default=all`, so that member is a JVM default method and a plugin
 * compiled against an older minor of the same major still loads.
 *
 * [PluginException] is the error hierarchy. It is not a data class.
 *
 * This is the first versioned contract.
 */
const val PLUGIN_API_VERSION = 1
