package com.lostf1sh.pixelplayeross.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lostf1sh.pixelplayeross.data.preferences.AppBackgroundConfig
import com.lostf1sh.pixelplayeross.data.preferences.PlayerUiConfig
import java.io.File

/** Current custom background configuration, exposed so screens can opt into a transparent root. */
val LocalAppBackground = staticCompositionLocalOf { AppBackgroundConfig() }

/** Now-playing screen appearance preferences, exposed so the player sheet can honour them. */
val LocalPlayerUiConfig = staticCompositionLocalOf { PlayerUiConfig() }

/**
 * Draws the user's custom background image behind the app content: a cover-cropped image with an
 * optional blur, then a dimming scrim so foreground text keeps its contrast.
 *
 * Renders nothing when the feature is off, so callers can place it unconditionally at the bottom
 * of the screen's layout.
 */
@Composable
fun AppBackgroundLayer(
    config: AppBackgroundConfig,
    modifier: Modifier = Modifier
) {
    if (!config.isActive) return
    val path = config.imagePath ?: return
    val context = LocalContext.current
    Box(modifier = modifier) {
        val imageModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Modifier
                .fillMaxSize()
                .blur(AppBackgroundConfig.clampBlur(config.blurRadius).dp)
        } else {
            // RenderEffect-backed blur needs API 31; older devices get the unblurred image.
            Modifier.fillMaxSize()
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(File(path))
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = imageModifier
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = AppBackgroundConfig.clampDim(config.dimPercent) / 100f
                    )
                )
        )
    }
}
