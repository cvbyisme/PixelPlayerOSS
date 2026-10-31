package com.lostf1sh.pixelplayeross.data.preferences

/**
 * App-wide custom background image configuration.
 *
 * [imagePath] points at a copy of the picked image inside the app's private storage, so the
 * background keeps working after a restart and after the media permission is revoked.
 */
data class AppBackgroundConfig(
    val enabled: Boolean = false,
    val imagePath: String? = null,
    val blurRadius: Int = DEFAULT_BLUR,
    val dimPercent: Int = DEFAULT_DIM
) {
    /** True only when the user turned the feature on and a usable image is stored. */
    val isActive: Boolean get() = enabled && !imagePath.isNullOrBlank()

    companion object {
        /** Private directory (under filesDir) holding the copied background image. */
        const val DIR_NAME = "app_background"
        const val DEFAULT_BLUR = 12
        const val DEFAULT_DIM = 40
        const val MAX_BLUR = 40
        const val MAX_DIM = 85

        fun clampBlur(value: Int): Int = value.coerceIn(0, MAX_BLUR)

        fun clampDim(value: Int): Int = value.coerceIn(0, MAX_DIM)
    }
}
