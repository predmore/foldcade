package app.foldcade.language

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** The theme format this Foldcade reads. A pack's `host` names the level it needs. */
const val THEME_HOST_LEVEL: Int = 1

/** A pack's own version: `1`, `1.2` or `1.2.3`. A missing part is zero, so `1.2` is `1.2.0`. */
data class ThemeRelease(val major: Int, val minor: Int = 0, val patch: Int = 0) : Comparable<ThemeRelease> {
    override fun compareTo(other: ThemeRelease): Int =
        compareValuesBy(this, other, ThemeRelease::major, ThemeRelease::minor, ThemeRelease::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val FORM = Regex("""\d{1,6}(\.\d{1,6}){0,2}""")

        fun parse(text: String): ThemeRelease? {
            if (!text.matches(FORM)) return null
            val parts = text.split('.').map(String::toInt)
            return ThemeRelease(parts[0], parts.getOrElse(1) { 0 }, parts.getOrElse(2) { 0 })
        }
    }
}

/** Where a pack's updates come from. The first install locks it: an update from anywhere else is refused. */
sealed interface ThemeSource {
    val wire: String

    /** The releases of github.com/[owner]/[repo]. GitHub names ignore case, so both are lower case. */
    data class GitHub(val owner: String, val repo: String) : ThemeSource {
        override val wire: String get() = "github:$owner/$repo"
    }

    companion object {
        private val OWNER = Regex("[a-z0-9](?:[a-z0-9-]{0,37}[a-z0-9])?")
        private val REPO = Regex("[a-z0-9._-]{1,100}")

        /** The manifest's `{"github": "owner/repo"}`. Anything else is null. */
        fun parse(element: JsonElement?): ThemeSource? {
            val root = element as? JsonObject ?: return null
            if (root.keys != setOf("github")) return null
            val path = (root["github"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: return null
            return github(path)
        }

        fun fromWire(text: String): ThemeSource? = text.removePrefix("github:").takeIf { it != text }?.let(::github)

        private fun github(path: String): ThemeSource? {
            val parts = path.lowercase().split('/')
            if (parts.size != 2) return null
            val (owner, repo) = parts
            if (!owner.matches(OWNER) || !repo.matches(REPO) || repo == "." || repo == "..") return null
            return GitHub(owner, repo)
        }
    }
}

/** The manifest's `host`: 1 when left out, null when it is not a whole number from 1. */
internal fun themeHostField(element: JsonElement?): Int? {
    if (element == null) return 1
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    return primitive.intOrNull?.takeIf { it >= 1 && primitive.content == it.toString() }
}

/** What a manifest's `release`, `host` and `source` got wrong. They never stop the pack loading. */
internal fun themeReleaseDiagnostics(file: ThemeFile): List<ThemeDiagnostic> = buildList {
    if ("release" in file.root && file.release == null) add(ThemeDiagnostic("theme.json", "release must be a version such as 1.2.0"))
    when (val host = file.host) {
        null -> add(ThemeDiagnostic("theme.json", "host must be a whole number from 1"))
        else -> if (host > THEME_HOST_LEVEL) add(ThemeDiagnostic("theme.json", "host $host needs a newer Foldcade; this one reads $THEME_HOST_LEVEL"))
    }
    if ("source" in file.root && file.source == null) add(ThemeDiagnostic("theme.json", "source must be {\"github\": \"owner/repo\"}"))
}
