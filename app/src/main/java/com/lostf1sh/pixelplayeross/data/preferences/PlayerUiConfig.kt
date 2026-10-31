package com.lostf1sh.pixelplayeross.data.preferences

/**
 * Appearance preferences for the now-playing (full player) screen.
 *
 * Kept separate from [AppBackgroundConfig] because these only affect the player sheet, while the
 * background config drives the whole app.
 */
data class PlayerUiConfig(
    /** Tapping the album cover opens the lyrics panel instead of the album detail page. */
    val coverTapOpensLyrics: Boolean = false,
    /** Paint the current cover, blurred, as the player background instead of the themed colour. */
    val blurredCoverBackground: Boolean = false,
    /** Extra dark scrim over the player background, so text stays readable. */
    val backgroundScrimPercent: Int = 0,
    /** Which palette the transport buttons (play/pause/skip) use. */
    val controlsStyle: String = PlayerControlsStyle.DEFAULT
) {
    val clampedScrim: Int get() = backgroundScrimPercent.coerceIn(0, MAX_SCRIM)

    companion object {
        const val MAX_SCRIM = 85
    }
}

/** Colour treatments for the player transport buttons. */
object PlayerControlsStyle {
    const val DEFAULT = "default"
    const val ACCENT = "accent"
    const val MONO = "mono"

    val values = listOf(DEFAULT, ACCENT, MONO)
}
