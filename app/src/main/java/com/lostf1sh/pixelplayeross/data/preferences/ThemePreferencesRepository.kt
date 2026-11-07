package com.lostf1sh.pixelplayeross.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.lostf1sh.pixelplayeross.utils.AppIconStyle
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
        val ALBUM_ART_PALETTE_STYLE = stringPreferencesKey("album_art_palette_style_v1")
        val ALBUM_ART_COLOR_ACCURACY = intPreferencesKey("album_art_color_accuracy_v1")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val GLOBAL_NOW_PLAYING_THEME_ENABLED = booleanPreferencesKey("global_now_playing_theme_enabled_v1")
        val THEME_PRESET = stringPreferencesKey("theme_preset")
        val APP_BACKGROUND_ENABLED = booleanPreferencesKey("app_background_enabled_v1")
        val APP_BACKGROUND_PATH = stringPreferencesKey("app_background_path_v1")
        val APP_BACKGROUND_BLUR = intPreferencesKey("app_background_blur_v1")
        val APP_BACKGROUND_DIM = intPreferencesKey("app_background_dim_v1")
        val APP_BACKGROUND_OPACITY = intPreferencesKey("app_background_opacity_v1")
        val APP_CONTAINER_OPACITY = intPreferencesKey("app_container_opacity_v1")
        val PLAYER_COVER_TAP_LYRICS = booleanPreferencesKey("player_cover_tap_lyrics_v1")
        val PLAYER_BLURRED_COVER_BG = booleanPreferencesKey("player_blurred_cover_bg_v1")
        val PLAYER_BG_SCRIM = intPreferencesKey("player_bg_scrim_v1")
        val PLAYER_CONTROLS_STYLE = stringPreferencesKey("player_controls_style_v1")
        val PLAYER_CONTROLS_SHAPE = stringPreferencesKey("player_controls_shape_v1")
        val ANIMATED_SPLASH_ENABLED = booleanPreferencesKey("animated_splash_enabled_v1")
        val LIBRARY_BACKGROUND_COLOR = intPreferencesKey("library_background_color_v1")

        /** Launcher icon flavour: upstream look or the anime one bundled with this fork. */
        val APP_ICON_STYLE = stringPreferencesKey("app_icon_style_v1")
    }

    /** Launcher icon flavour; sanitised so an unknown value never breaks the picker. */
    val appIconStyleFlow: Flow<String> = dataStore.data.map { preferences ->
        AppIconStyle.sanitize(preferences[Keys.APP_ICON_STYLE])
    }

    /** Animated launch animation; users who prefer an instant start can turn it off. */
    val animatedSplashEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.ANIMATED_SPLASH_ENABLED] ?: true
    }

    /** Optional solid backdrop for the library screen; null keeps the dynamic theme colour. */
    val libraryBackgroundColorFlow: Flow<Int?> = dataStore.data.map { preferences ->
        preferences[Keys.LIBRARY_BACKGROUND_COLOR]
    }

    /** Custom app-wide background image, plus its blur and dim tuning. */
    val appBackgroundFlow: Flow<AppBackgroundConfig> = dataStore.data.map { preferences ->
        AppBackgroundConfig(
            enabled = preferences[Keys.APP_BACKGROUND_ENABLED] ?: false,
            imagePath = preferences[Keys.APP_BACKGROUND_PATH],
            blurRadius = AppBackgroundConfig.clampBlur(
                preferences[Keys.APP_BACKGROUND_BLUR] ?: AppBackgroundConfig.DEFAULT_BLUR
            ),
            dimPercent = AppBackgroundConfig.clampDim(
                preferences[Keys.APP_BACKGROUND_DIM] ?: AppBackgroundConfig.DEFAULT_DIM
            ),
            backgroundOpacity = AppBackgroundConfig.clampOpacity(
                preferences[Keys.APP_BACKGROUND_OPACITY] ?: AppBackgroundConfig.DEFAULT_BACKGROUND_OPACITY
            ),
            containerOpacity = AppBackgroundConfig.clampOpacity(
                preferences[Keys.APP_CONTAINER_OPACITY] ?: AppBackgroundConfig.DEFAULT_CONTAINER_OPACITY
            )
        )
    }

    /** Now-playing screen look: cover tap action, background treatment and button palette. */
    val playerUiConfigFlow: Flow<PlayerUiConfig> = dataStore.data.map { preferences ->
        PlayerUiConfig(
            coverTapOpensLyrics = preferences[Keys.PLAYER_COVER_TAP_LYRICS]
                ?: PlayerUiConfig().coverTapOpensLyrics,
            blurredCoverBackground = preferences[Keys.PLAYER_BLURRED_COVER_BG] ?: false,
            backgroundScrimPercent = (preferences[Keys.PLAYER_BG_SCRIM]
                ?: PlayerUiConfig().backgroundScrimPercent).coerceIn(0, PlayerUiConfig.MAX_SCRIM),
            controlsStyle = preferences[Keys.PLAYER_CONTROLS_STYLE]
                ?.takeIf { it in PlayerControlsStyle.values }
                ?: PlayerControlsStyle.DEFAULT,
            controlsShape = preferences[Keys.PLAYER_CONTROLS_SHAPE]
                ?.takeIf { it in PlayerControlsShape.values }
                ?: PlayerControlsShape.DEFAULT
        )
    }

    val appThemeModeFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.APP_THEME_MODE] ?: AppThemeMode.FOLLOW_SYSTEM
    }

    val playerThemePreferenceFlow: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.PLAYER_THEME_PREFERENCE] ?: ThemePreference.ALBUM_ART
    }

    val globalNowPlayingThemeEnabledFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[Keys.GLOBAL_NOW_PLAYING_THEME_ENABLED] ?: false
    }

    val themePresetFlow: Flow<String> = dataStore.data.map { preferences ->
        ThemePreset.sanitize(preferences[Keys.THEME_PRESET])
    }

    val albumArtPaletteStyleFlow: Flow<AlbumArtPaletteStyle> = dataStore.data.map { preferences ->
        AlbumArtPaletteStyle.fromStorageKey(preferences[Keys.ALBUM_ART_PALETTE_STYLE])
    }

    val albumArtColorAccuracyFlow: Flow<Int> = dataStore.data.map { preferences ->
        AlbumArtColorAccuracy.clamp(preferences[Keys.ALBUM_ART_COLOR_ACCURACY] ?: AlbumArtColorAccuracy.DEFAULT)
    }

    suspend fun setPlayerThemePreference(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_THEME_PREFERENCE] = themeMode
        }

    suspend fun setAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_THEME_MODE] = themeMode
        }

    suspend fun setGlobalNowPlayingThemeEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.GLOBAL_NOW_PLAYING_THEME_ENABLED] = enabled
        }
    suspend fun setThemePreset(preset: String) =
        dataStore.edit { preferences ->
            preferences[Keys.THEME_PRESET] = ThemePreset.sanitize(preset)
        }

    suspend fun setAppBackgroundEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_BACKGROUND_ENABLED] = enabled
        }

    suspend fun setAppBackgroundPath(path: String?) =
        dataStore.edit { preferences ->
            if (path.isNullOrBlank()) {
                preferences.remove(Keys.APP_BACKGROUND_PATH)
            } else {
                preferences[Keys.APP_BACKGROUND_PATH] = path
            }
        }

    suspend fun setAppBackgroundBlur(radius: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_BACKGROUND_BLUR] = AppBackgroundConfig.clampBlur(radius)
        }

    suspend fun setAppBackgroundDim(percent: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_BACKGROUND_DIM] = AppBackgroundConfig.clampDim(percent)
        }

    suspend fun setAppBackgroundOpacity(percent: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_BACKGROUND_OPACITY] = AppBackgroundConfig.clampOpacity(percent)
        }

    suspend fun setAppContainerOpacity(percent: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_CONTAINER_OPACITY] = AppBackgroundConfig.clampOpacity(percent)
        }

    suspend fun setPlayerCoverTapOpensLyrics(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_COVER_TAP_LYRICS] = enabled
        }

    suspend fun setPlayerBlurredCoverBackground(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_BLURRED_COVER_BG] = enabled
        }

    suspend fun setPlayerBackgroundScrim(percent: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_BG_SCRIM] = percent.coerceIn(0, PlayerUiConfig.MAX_SCRIM)
        }

    suspend fun setPlayerControlsStyle(style: String) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_CONTROLS_STYLE] =
                if (style in PlayerControlsStyle.values) style else PlayerControlsStyle.DEFAULT
        }

    suspend fun setPlayerControlsShape(shape: String) =
        dataStore.edit { preferences ->
            preferences[Keys.PLAYER_CONTROLS_SHAPE] =
                if (shape in PlayerControlsShape.values) shape else PlayerControlsShape.DEFAULT
        }

    suspend fun setAnimatedSplashEnabled(enabled: Boolean) =
        dataStore.edit { preferences ->
            preferences[Keys.ANIMATED_SPLASH_ENABLED] = enabled
        }
    suspend fun setAppIconStyle(style: String) =
        dataStore.edit { preferences ->
            preferences[Keys.APP_ICON_STYLE] = AppIconStyle.sanitize(style)
        }

    suspend fun setLibraryBackgroundColor(argb: Int?) =
        dataStore.edit { preferences ->
            if (argb == null) {
                preferences.remove(Keys.LIBRARY_BACKGROUND_COLOR)
            } else {
                preferences[Keys.LIBRARY_BACKGROUND_COLOR] = argb
            }
        }


    suspend fun initializeAppThemeMode(themeMode: String) =
        dataStore.edit { preferences ->
            if (preferences[Keys.APP_THEME_MODE] == null) {
                preferences[Keys.APP_THEME_MODE] = themeMode
            }
        }

    suspend fun setAlbumArtPaletteStyle(style: AlbumArtPaletteStyle) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        }

    suspend fun setAlbumArtColorAccuracy(level: Int) =
        dataStore.edit { preferences ->
            preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(level)
        }

    suspend fun setAlbumArtPaletteSettings(
        style: AlbumArtPaletteStyle,
        accuracyLevel: Int
    ) = dataStore.edit { preferences ->
        preferences[Keys.ALBUM_ART_PALETTE_STYLE] = style.storageKey
        preferences[Keys.ALBUM_ART_COLOR_ACCURACY] = AlbumArtColorAccuracy.clamp(accuracyLevel)
    }
}

/** App-wide surface palettes. Only [DEFAULT] and [AMOLED] exist; unknown stored values fall back to [DEFAULT]. */
object ThemePreset {
    const val DEFAULT = "default"
    const val AMOLED = "amoled"

    fun sanitize(value: String?): String = if (value == AMOLED) AMOLED else DEFAULT
}
