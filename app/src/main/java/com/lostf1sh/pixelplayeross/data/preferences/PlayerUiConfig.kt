package com.lostf1sh.pixelplayeross.data.preferences

/**
 * Appearance preferences for the now-playing (full player) screen.
 *
 * Kept separate from [AppBackgroundConfig] because these only affect the player sheet, while the
 * background config drives the whole app.
 */
data class PlayerUiConfig(
    /** Tapping the album cover opens the lyrics panel instead of the album detail page. */
    val coverTapOpensLyrics: Boolean = true,
    /** Paint the current cover, blurred, as the player background instead of the themed colour. */
    val blurredCoverBackground: Boolean = false,
    /** Extra dark scrim over the player background, so text stays readable. */
    val backgroundScrimPercent: Int = 0,
    /** Which palette the transport buttons (play/pause/skip) use. */
    val controlsStyle: String = PlayerControlsStyle.MONO,
    /** Which silhouette the transport buttons are clipped to. */
    val controlsShape: String = PlayerControlsShape.DEFAULT
) {
    val clampedScrim: Int get() = backgroundScrimPercent.coerceIn(0, MAX_SCRIM)

    companion object {
        const val MAX_SCRIM = 85
    }
}

/** Colour treatments for the player transport buttons. */
object PlayerControlsStyle {
    /** Material 3 expressive fixed tones. */
    const val DEFAULT = "default"
    /** Album accent colour. */
    const val ACCENT = "accent"
    /** Muted monochrome (default). */
    const val MONO = "mono"
    /** Transparent fill with a hairline outline. */
    const val OUTLINE = "outline"
    /** Frosted, barely-there fill that lets the backdrop through. */
    const val GLASS = "glass"
    /** Inverted: solid onSurface fill with background-coloured icons. */
    const val CONTRAST = "contrast"
    val values = listOf(DEFAULT, ACCENT, MONO, OUTLINE, GLASS, CONTRAST)
}

/** Silhouettes for the player transport buttons. */
object PlayerControlsShape {
    /** Soft squircle that morphs between play and pause (default). */
    const val DEFAULT = "smooth"
    /** Fully round capsules. */
    const val ROUND = "round"
    /** Wide pill / stadium silhouette. */
    const val PILL = "pill"
    /** Rounded square with a tight corner. */
    const val SQUARE = "square"
    val values = listOf(DEFAULT, ROUND, PILL, SQUARE)
}