package app.foldcade.romm

open class RommException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** The server's OpenAPI major is newer than [RommContract.TESTED_MAJOR]. Negotiate was not called. */
class RommProtocolMismatch(
    val serverVersion: String,
    val testedMajor: Int,
) : RommException(
    "This RomM server speaks OpenAPI $serverVersion. " +
        "Foldcade was tested with RomM $testedMajor and will not negotiate saves against a newer major.",
)

/** `/openapi.json` did not yield a major version, so negotiate was not called. */
class RommProtocolUnreadable(reason: String) : RommException(
    "Foldcade could not read this server's OpenAPI version ($reason), so it will not negotiate saves.",
)

class RommUnavailable(message: String, cause: Throwable? = null) : RommException(message, cause)

class RommHttpException(val status: Int, val detail: String) : RommException(
    if (detail.isBlank()) "RomM returned HTTP $status" else "RomM returned HTTP $status: $detail",
)

class RommUnauthenticated : RommException("RomM client token is missing")

/** `POST /api/saves` returned 409 with `overwrite=false`. The slot moved; do not overwrite it. */
class RommSlotMoved(detail: String) : RommException(
    detail.ifBlank { "RomM refused the save upload because the slot changed" },
)

/** `GET /api/roms/{id}/content/{file}` returned 202. This client does not send `format`. */
class RommConversionPending(val retryAfterSeconds: Long?) : RommException(
    "RomM is converting this ROM. Foldcade does not request format conversion.",
)

class RommUnsupportedServer(val serverVersion: String, message: String) : RommException(message)

class RommResponseException(message: String) : RommException(message)
