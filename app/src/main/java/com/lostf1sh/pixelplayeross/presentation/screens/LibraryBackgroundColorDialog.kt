package com.lostf1sh.pixelplayeross.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lostf1sh.pixelplayeross.R
import kotlin.math.roundToInt

/** Hand-picked backdrops that read well under both the light and dark palettes. */
private val LibraryBackgroundPresets = listOf(
    0xFF121212,
    0xFF1B2430,
    0xFF0F2027,
    0xFF1E2A38,
    0xFF241E38,
    0xFF2B1B1B,
    0xFF14261F,
    0xFF2A2418,
    0xFFF5F5F7,
    0xFFE8EEF5
)

/**
 * Picks the solid colour painted behind the library screen. The dynamic theme colour keeps
 * following the wallpaper until the user chooses something here.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LibraryBackgroundColorDialog(
    initialColor: Int?,
    onDismiss: () -> Unit,
    onColorSelected: (Int?) -> Unit
) {
    val startColor = Color(initialColor ?: 0xFF121212.toInt())
    val startHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(startColor.toArgb(), hsv)
        hsv
    }
    var hue by remember(initialColor) { mutableFloatStateOf(startHsv[0]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(startHsv[1]) }
    var brightness by remember(initialColor) { mutableFloatStateOf(startHsv[2]) }
    val preview = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.setcat_library_background_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(preview)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    )
                    Text(
                        text = String.format("#%06X", 0xFFFFFF and preview.toArgb()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LibraryBackgroundPresets.forEach { argb ->
                        val swatch = Color(argb.toInt())
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable {
                                    val hsv = FloatArray(3)
                                    android.graphics.Color.colorToHSV(argb.toInt(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    brightness = hsv[2]
                                }
                        )
                    }
                }
                ColorSlider(
                    label = stringResource(R.string.setcat_library_background_hue),
                    value = hue,
                    range = 0f..360f,
                    onValueChange = { hue = it }
                )
                ColorSlider(
                    label = stringResource(R.string.setcat_library_background_saturation),
                    value = saturation,
                    range = 0f..1f,
                    onValueChange = { saturation = it }
                )
                ColorSlider(
                    label = stringResource(R.string.setcat_library_background_brightness),
                    value = brightness,
                    range = 0f..1f,
                    onValueChange = { brightness = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onColorSelected(preview.toArgb()) }) {
                Text(text = stringResource(R.string.setcat_library_background_apply))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onColorSelected(null) }) {
                    Text(text = stringResource(R.string.setcat_library_background_use_theme))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        }
    )
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                text = if (range.endInclusive > 1.5f) value.roundToInt().toString()
                else "${(value * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
