package app.foldcade.language

object RommPhoneCopy {
    const val scan = "Scan on this Wi-Fi to send your address"
    const val phoneHere = "Paste your RomM address on your phone"
    const val opening = "Getting your phone ready…"
    const val offline = "Join Wi-Fi to use your phone, or type here"
    const val sent = "Finding your RomM…"
    const val searching = "Looking for RomM nearby…"
    const val notFound = "No RomM found nearby. Your phone knows the way."
    const val useServer = "Use this server"
    const val title = "RomM for Foldcade"
    const val heading = "Where’s your RomM?"
    const val address = "Server address"
    const val hint = "Paste the address from your bookmark or RomM app. Sign in on RomM next."
    const val send = "Continue to RomM"
    const val approve = "Approve Foldcade on RomM"
    const val changeServerToken = "To change servers, enter its token."
    const val failed = "Check Foldcade for the next step. If there’s a new QR code, scan it to try again."
    const val invalid = "Use an http:// or https:// server address, without a password or token. Scan the new code on Foldcade to try again."
}

/** A known address goes straight to its approval code; only a failed address needs another handoff. */
fun rommNeedsAddress(model: PickerModel): Boolean =
    model.connectOrigin.isBlank() || model.connectHint == Copy.rommCodeUnavailable

/** Address only. The continuation holds RomM's public user code, never its secret device code or token. */
object RommPhonePage {
    fun form(action: String, colors: KeyDropPage.Colors): String = KeyDropPage.page(colors, """
        <h1>${RommPhoneCopy.heading}</h1>
        <p>${RommPhoneCopy.hint}</p>
        <form method="post" action="${KeyDropPage.escape(action)}">
          <label for="key">${RommPhoneCopy.address}</label>
          <input id="key" name="key" type="url" placeholder="https://romm.example.com" required maxlength="256"
                 autocomplete="url" autocapitalize="off" autocorrect="off" spellcheck="false">
          <button type="submit">${RommPhoneCopy.send}</button>
        </form>
    """, RommPhoneCopy.title)

    fun done(url: String?, invalid: Boolean, colors: KeyDropPage.Colors): String = KeyDropPage.page(colors,
        if (url == null) "<h1>${RommPhoneCopy.title}</h1><p>${if (invalid) RommPhoneCopy.invalid else RommPhoneCopy.failed}</p>"
        else "<h1>${RommPhoneCopy.title}</h1><a class=\"button\" href=\"${KeyDropPage.escape(url)}\" rel=\"noreferrer\">${RommPhoneCopy.approve}</a>",
        RommPhoneCopy.title,
    )
}
