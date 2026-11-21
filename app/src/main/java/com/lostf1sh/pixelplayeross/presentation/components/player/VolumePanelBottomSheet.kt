package com.lostf1sh.pixelplayeross.presentation.components.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.lostf1sh.pixelplayeross.R
import com.lostf1sh.pixelplayeross.ui.theme.RoundedSans
import kotlin.math.roundToInt

/**
 * cvby: fine-grained media volume panel.
 *
 * The now-playing bar only shows a percentage, which hides how many hardware steps exist.
 * This panel drives `STREAM_MUSIC` directly in *steps*, so the user can move one step at a
 * time instead of jumping 7% per key press, and it labels the level as "step / total" so the
 * real resolution is visible.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VolumePanelBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val steps = remember(audioManager) {
        audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 15
    }
    var volume by remember(audioManager) {
        mutableIntStateOf(
            audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC)?.coerceIn(0, steps) ?: 0
        )
    }

    // Keep the slider honest if the level changes from anywhere else (hardware keys, the
    // system panel, another app casting). Without this the slider drifts out of sync.
    DisposableEffect(audioManager, context) {
        if (audioManager == null) return@DisposableEffect onDispose { }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                volume = audioManager
                    .getStreamVolume(AudioManager.STREAM_MUSIC)
                    .coerceIn(0, steps)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun applyStep(value: Int) {
        val clamped = value.coerceIn(0, steps)
        // Absolute step, no FLAG_SHOW_UI: the panel is the UI, and the system overlay would
        // cover the very slider being dragged.
        runCatching { audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0) }
        volume = clamped
    }

    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = colors.onSurfaceVariant.copy(alpha = 0.4f))
        },
        containerColor = colors.surfaceContainerHigh,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.player_volume_panel_title),
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = RoundedSans,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() }
            )

            // Big percentage plus the exact step, so the resolution is explicit.
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = ((volume * 100f) / steps).roundToInt().toString(),
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = RoundedSans,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface
                )
                Text(
                    text = "%",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.player_volume_panel_steps, volume, steps),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Step-based slider: continuous drag is snapped to whole steps, so a slow drag
            // always lands on an exact hardware level instead of a rounded percentage.
            Slider(
                value = volume.toFloat(),
                onValueChange = { applyStep(it.roundToInt()) },
                valueRange = 0f..steps.toFloat(),
                steps = (steps - 1).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { applyStep(0) }) {
                    Icon(
                        imageVector = Icons.Rounded.VolumeOff,
                        contentDescription = stringResource(R.string.player_volume_panel_mute)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepButton(
                        icon = Icons.Rounded.VolumeDown,
                        description = stringResource(R.string.player_volume_panel_step_down),
                        enabled = volume > 0,
                        onClick = { applyStep(volume - 1) }
                    )
                    StepButton(
                        icon = Icons.Rounded.VolumeUp,
                        description = stringResource(R.string.player_volume_panel_step_up),
                        enabled = volume < steps,
                        onClick = { applyStep(volume + 1) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.player_volume_panel_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (enabled) colors.secondaryContainer else colors.surfaceContainer
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) colors.onSecondaryContainer else colors.onSurfaceVariant
        )
    }
}