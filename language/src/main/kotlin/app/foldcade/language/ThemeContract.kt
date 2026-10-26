package app.foldcade.language

/**
 * What a pack can rely on from one Foldcade to the next, besides the style tokens [ThemeStyle]
 * reads. `themes/contract.json` is written from this and from Afterglow, and a test fails when it
 * is stale or when a token or slot leaves it without a line here. Never delete a line from these
 * maps and sets: a pack in the wild may still use the name.
 */
object ThemeContract {
    /** Old token names a pack may still write, and the token each became. The old name keeps working. */
    val renamedTokens: Map<String, String> = emptyMap()

    /** Tokens the engine no longer reads. A pack that sets one is told so. Their names are never reused. */
    val retiredTokens: Set<String> = emptySet()

    /** Old icon slot names a pack may still fill, and the slot each became. */
    val renamedSlots: Map<String, String> = emptyMap()

    /** Icon slots the host no longer draws. Their names are never reused. */
    val retiredSlots: Set<String> = emptySet()

    /**
     * Families whose names the host defines. A pack's slot in one of them that Afterglow does not
     * fill is a misspelling, and is reported.
     */
    val closedSlotFamilies: Set<String> = setOf("button", "status", "badge", "control")

    /** Families a pack may extend with names of its own: a platform's mark, an installed app. */
    val openSlotFamilies: Set<String> = setOf("mark", "symbol", "app")

    /**
     * The sounds a pack can ship as `sounds/<cue>.ogg`, each with the cue it falls back to when
     * the pack has no clip for it. Null falls back to silence. The pack's own clips are tried
     * before Afterglow's, so a pack that ships `activate` hears it wherever `open` would play.
     */
    val soundCues: Map<String, String?> = linkedMapOf(
        "move" to null,
        "activate" to null,
        "back" to null,
        "notify" to null,
        "open" to "activate",
        "close" to "back",
        "launch" to "activate",
        "toggle" to "activate",
        "type" to "activate",
        "erase" to "back",
        "refuse" to null,
        "edge" to null,
    )

    /** [cue], then what it falls back to, in order. */
    fun soundChain(cue: String): List<String> =
        generateSequence(cue) { soundCues[it] }.take(soundCues.size + 1).toList()

    /** The inputs a scene's expressions can read, with what each one is. */
    val sceneInputs: Map<String, String> = linkedMapOf(
        "time" to "Design seconds since the scene started, scaled by the Motion setting",
        "width" to "Render target width, px",
        "height" to "Render target height, px",
        "panel" to "0 on the top screen, 1 on the bottom",
        "seed" to "A random 0–1 each panel picks when the scene starts",
        "moving" to "1 while the scene is animating, 0 when motion is off",
        "clock" to "How fast design time runs against real time",
        "index" to "The repeat's index, inside a repeated layer",
        "u" to "0–1 along a sampled path",
        "accentR" to "Red of the focused item's accent, 0–1, eased as focus moves",
        "accentG" to "Green of the focused item's accent, 0–1",
        "accentB" to "Blue of the focused item's accent, 0–1",
        "hour" to "Local time of day, 0 to 24, with minutes as the fraction",
        "battery" to "Battery level, 0–1",
        "charging" to "1 while charging, else 0",
        "sinceMove" to "Real seconds since the cursor last moved, up to 3600",
        "scroll" to "How far the panel's grid has slid sideways, render px, following its glide",
    )

    /** Manifest fields, with their JSON kind. `style` and `icons` have contracts of their own. */
    val manifestFields: Map<String, String> = linkedMapOf(
        "version" to "integer",
        "id" to "string",
        "name" to "string",
        "release" to "string",
        "host" to "integer",
        "source" to "object",
        "extends" to "string",
        "author" to "string",
        "description" to "string",
        "license" to "string",
        "preview" to "string",
        "background" to "color",
        "surface" to "color",
        "onBackground" to "color",
        "muted" to "color",
        "focus" to "color",
        "iconRadius" to "number",
        "artScale" to "number",
        "backgroundMotion" to "string",
        "icons" to "object",
        "scenes" to "array",
        "fonts" to "object",
        "style" to "object",
        "backgroundMusic" to "string",
    )

    /** The faces `fonts` can name. */
    val fontFaces: Map<String, String> = linkedMapOf(
        "regular" to "Running text, at type.bodyWeight",
        "bold" to "Strong text, at type.strongWeight; without it the regular face is made bolder",
        "display" to "The hero's title, dialog titles, the clock and the save-conflict game",
    )
}
