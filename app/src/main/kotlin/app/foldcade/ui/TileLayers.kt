package app.foldcade.ui

import androidx.compose.runtime.Composable

/** One cell prepares its motion once; both passes use those same animation states. */
internal enum class TileLayer { Face, Status }

internal typealias TileContent = @Composable (TileLayer) -> Unit
