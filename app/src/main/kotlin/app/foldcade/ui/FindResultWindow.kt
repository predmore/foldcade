package app.foldcade.ui

/** Keep the animated and imminent windows, with one extra pitch beyond each fading edge. */
internal fun findResultInWindow(
    at: Float,
    first: Float,
    across: Int,
    targetAt: Float = at,
    targetFirst: Float = first,
): Boolean = nearResultWindow(at - first, across) || nearResultWindow(targetAt - targetFirst, across)

private fun nearResultWindow(along: Float, across: Int): Boolean = along >= -2f && along <= across + 1f
