package app.foldcade.api.plugin

/**
 * A failure from a plugin call that the shell can show.
 *
 * Implementations rethrow [kotlin.coroutines.cancellation.CancellationException].
 * They must not catch it, and they must not wrap it in [PluginException].
 * Cancellation is not one of these failures.
 */
sealed class PluginException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class NotAuthenticated(
        message: String,
        cause: Throwable? = null,
    ) : PluginException(message, cause)

    /** The plugin cannot reach its source. Offline is this failure. */
    class Unavailable(
        message: String,
        cause: Throwable? = null,
    ) : PluginException(message, cause)

    /**
     * The server speaks a protocol this plugin does not.
     * [serverVersion] is the version the server reported.
     */
    class ProtocolMismatch(
        val serverVersion: String,
        message: String,
        cause: Throwable? = null,
    ) : PluginException(message, cause)

    class NotFound(
        message: String,
        cause: Throwable? = null,
    ) : PluginException(message, cause)
}
