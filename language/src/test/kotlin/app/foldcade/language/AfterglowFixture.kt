package app.foldcade.language

import java.io.File

internal fun afterglowZip(): File = File(
    checkNotNull(System.getProperty("foldcade.afterglowZip")) {
        "Run the language tests through Gradle to generate the Afterglow archive."
    },
)
