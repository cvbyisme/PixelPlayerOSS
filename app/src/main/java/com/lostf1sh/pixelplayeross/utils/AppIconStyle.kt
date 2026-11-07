package com.lostf1sh.pixelplayeross.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber

/**
 * Launcher icon flavour for this fork.
 *
 * Android has no "change my icon" API, so the manifest declares two <activity-alias> entries
 * pointing at [com.lostf1sh.pixelplayeross.MainActivity]: [DEFAULT] carries the upstream icon,
 * [ANIME] carries the anime one. Exactly one of them may be enabled at a time, otherwise the
 * launcher shows two icons for the same app.
 */
object AppIconStyle {
    /** Upstream PixelPlayerOSS icon. */
    const val DEFAULT = "default"

    /** Anime-style icon bundled with the personal fork. */
    const val ANIME = "anime"

    val values = setOf(DEFAULT, ANIME)

    fun sanitize(style: String?): String = if (style != null && style in values) style else DEFAULT

    /**
     * Enables the alias for [style] and disables the other one.
     *
     * DONT_KILL_APP keeps playback alive; some launchers only repaint their home screen the next
     * time they are resumed, which is why the settings UI asks the user to be patient.
     */
    fun apply(context: Context, style: String) {
        val manager = context.packageManager
        val packageName = context.packageName
        val wanted = sanitize(style)
        val defaultAlias = ComponentName(packageName, "$packageName.LauncherDefault")
        val animeAlias = ComponentName(packageName, "$packageName.LauncherAnime")
        val enable = if (wanted == ANIME) animeAlias else defaultAlias
        val disable = if (wanted == ANIME) defaultAlias else animeAlias

        runCatching {
            // Enable first: a launcher with no enabled entry would drop the icon entirely.
            if (manager.getComponentEnabledSetting(enable) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                manager.setComponentEnabledSetting(
                    enable,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            if (manager.getComponentEnabledSetting(disable) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                manager.setComponentEnabledSetting(
                    disable,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }.onFailure { Timber.w(it, "Unable to switch the launcher icon to %s", wanted) }
    }
}
