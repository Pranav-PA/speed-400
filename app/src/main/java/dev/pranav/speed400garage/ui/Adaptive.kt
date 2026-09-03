package dev.pranav.speed400garage.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the screen is too narrow for two panes side by side — a phone in portrait,
 * or a tablet in a split-screen half.
 *
 * Measured from the actual available width rather than the device class, because the
 * question that matters is "is there room", not "what kind of device is this". A tablet
 * sharing its screen with another app has a phone's width and wants a phone's layout.
 *
 * Set once at the top of the tree from the real constraints, so every screen agrees.
 */
val LocalIsCompact = staticCompositionLocalOf { false }

/** Below this, one pane at a time. Material's own compact/medium boundary. */
const val COMPACT_WIDTH_DP = 600
