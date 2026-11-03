package app.foldcade.ui

/** The fading edge occupies one pitch; keep one more beyond it for incoming tiles. */
internal fun findResultInWindow(at: Float, first: Float, across: Int): Boolean =
    at - first >= -2f && at - first <= across + 1f
