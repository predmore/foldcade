package app.foldcade.language

/**
 * Settings › Library › SteamGridDB, from a phone. The QR code beside the key field opens a
 * one-time page that Foldcade serves on this Wi-Fi while the form is open. That page links
 * to SteamGridDB's API page, where the key is copied, and the key pasted back there comes
 * straight to the form, which checks and saves it as if it had been typed.
 */
sealed interface KeyDrop {
    /**
     * No Wi-Fi address to serve from. The QR code is SteamGridDB's own page instead, so the
     * key can be read on a phone and typed here.
     */
    data object Offline : KeyDrop

    /** Serving at [url]. [phoneHere] once a phone has opened it. */
    data class Open(val url: String, val phoneHere: Boolean = false) : KeyDrop

    /** The phone sent a key, and the form is checking it. The page has closed; a refused key gets a new one. */
    data object Sent : KeyDrop
}

/** Where SteamGridDB shows a signed-in player's API key. */
const val STEAMGRIDDB_API_PAGE = "https://www.steamgriddb.com/profile/preferences/api"

/** The line under the QR code: what to do with it now. */
fun keyDropLine(drop: KeyDrop?): String = when (drop) {
    null -> KeyDropCopy.opening
    KeyDrop.Offline -> KeyDropCopy.scanOffline
    is KeyDrop.Open -> if (drop.phoneHere) KeyDropCopy.phoneHere else KeyDropCopy.scan
    KeyDrop.Sent -> KeyDropCopy.sent
}

/**
 * What the QR code holds: the drop's page, or SteamGridDB's without Wi-Fi. Null while the
 * page is opening or a sent key is being checked, when there is nothing to scan.
 */
fun keyDropQr(drop: KeyDrop?): String? = when (drop) {
    null, KeyDrop.Sent -> null
    KeyDrop.Offline -> STEAMGRIDDB_API_PAGE
    is KeyDrop.Open -> drop.url
}

object KeyDropCopy {
    const val opening = "Getting a code…"
    const val scan = "Scan with your phone to add a key"
    const val phoneHere = "Paste your key on your phone"
    const val sent = "Got it"
    const val scanOffline = "Scan for your key, then type it here"

    // The phone's page.
    const val pageTitle = "SteamGridDB key for Foldcade"
    const val pageHeading = "Send your SteamGridDB key"
    const val pageStepOne = "Get your key"
    const val pageStepOneHint = "Sign in, then copy the API key."
    const val pageStepTwo = "Paste it here"
    const val pageSend = "Send to Foldcade"
    const val pageSent = "Sent. Foldcade is checking it."
    const val pageEmpty = "Nothing came through. Scan the new code on Foldcade."
}

/**
 * The phone's page, in the theme's colours as `#rrggbb`. Self-contained: no scripts, no
 * fonts or images to fetch, and it sends no referrer to SteamGridDB.
 */
object KeyDropPage {
    data class Colors(val background: String, val surface: String, val text: String, val muted: String, val accent: String)

    fun form(action: String, colors: Colors): String = page(colors, """
        <h1>${KeyDropCopy.pageHeading}</h1>
        <ol>
          <li><a class="button" href="$STEAMGRIDDB_API_PAGE" target="_blank" rel="noopener noreferrer">${KeyDropCopy.pageStepOne}</a>
            <p>${KeyDropCopy.pageStepOneHint}</p></li>
          <li><form method="post" action="${escape(action)}">
            <label for="key">${KeyDropCopy.pageStepTwo}</label>
            <input id="key" name="key" required maxlength="256" autocomplete="off" autocapitalize="off" autocorrect="off" spellcheck="false">
            <button type="submit">${KeyDropCopy.pageSend}</button>
          </form></li>
        </ol>
    """)

    fun done(received: Boolean, colors: Colors): String =
        page(colors, "<h1>${if (received) KeyDropCopy.pageSent else KeyDropCopy.pageEmpty}</h1>")

    internal fun page(colors: Colors, body: String, title: String = KeyDropCopy.pageTitle): String = """
        <!doctype html>
        <html lang="en"><head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="referrer" content="no-referrer">
        <title>${escape(title)}</title>
        <style>
          :root { color-scheme: dark; }
          body { margin: 0; padding: 28px 22px; background: ${hex(colors.background)}; color: ${hex(colors.text)};
                 font: 18px/1.4 system-ui, -apple-system, sans-serif; }
          h1 { font-size: 24px; margin: 0 0 20px; }
          ol { padding-left: 22px; margin: 0; }
          li { margin-bottom: 26px; }
          li::marker { color: ${hex(colors.muted)}; font-weight: 700; }
          p { color: ${hex(colors.muted)}; margin: 8px 0 0; font-size: 15px; }
          label { display: block; font-weight: 700; margin-bottom: 10px; }
          .button, button { display: block; width: 100%; box-sizing: border-box; text-align: center; padding: 14px 18px;
                 border-radius: 14px; border: 0; font: inherit; font-weight: 700; text-decoration: none;
                 background: ${hex(colors.accent)}; color: ${hex(colors.background)}; }
          input { display: block; width: 100%; box-sizing: border-box; padding: 14px 16px; margin-bottom: 12px; border-radius: 14px;
                 border: 2px solid ${hex(colors.muted)}; background: ${hex(colors.surface)}; color: ${hex(colors.text)}; font: 16px ui-monospace, monospace; }
          input:focus { outline: none; border-color: ${hex(colors.accent)}; }
        </style>
        </head><body>
        $body
        </body></html>
    """.trimIndent()

    internal fun escape(text: String): String =
        text.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    /** Only a `#rrggbb`, so nothing else can reach the style sheet. */
    private fun hex(color: String): String = color.takeIf { HEX.matches(it) } ?: "#000000"

    private val HEX = Regex("#[0-9a-fA-F]{6}")
}
