package com.lostf1sh.pixelplayeross.presentation.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.lostf1sh.pixelplayeross.data.service.player.ActiveDecoderInfo
import com.lostf1sh.pixelplayeross.data.service.player.DualPlayerEngine
import java.util.Locale

/**
 * cvby: live transport-quality badge for the now-playing screen.
 *
 * Everything shown here comes from public player APIs, so it works with ordinary app
 * permissions:
 *
 *  - codec + source sample rate + bit depth + bitrate + channel count, read from
 *    `Player.audioFormat` the moment `onAudioInputFormatChanged` fires;
 *  - the transport path (bitstream/offload vs PCM through the mixer);
 *  - the decoder actually in use and whether it is hardware accelerated.
 *
 * It intentionally does not show a "DAC output rate": reading the real HAL clock needs
 * DUMP/root, and inventing a number would be worse than showing nothing.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AudioQualityBadge(
    snapshot: DualPlayerEngine.AudioFormatSnapshot?,
    decoder: ActiveDecoderInfo?,
    isOffloadActive: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = snapshot != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val info = snapshot ?: return@AnimatedVisibility
        BadgeContent(
            info = info,
            decoder = decoder,
            isOffloadActive = isOffloadActive,
            textColor = textColor,
        )
    }
}

@Composable
private fun BadgeContent(
    info: DualPlayerEngine.AudioFormatSnapshot,
    decoder: ActiveDecoderInfo?,
    isOffloadActive: Boolean,
    textColor: Color,
) {
    val codecLabel = codecDisplayName(info.sampleMimeType)
    val rateLabel = formatSampleRate(info.sampleRate)
    val depthLabel = info.sourceBitDepth?.let { "$it-bit" }
    val bitrateLabel = info.bitrate.takeIf { it > 0 }?.let { formatBitrate(it) }
    val channelLabel = channelDisplayName(info.channelCount)
    val pathLabel = when {
        isOffloadActive || info.isBitstream -> "Direct"
        info.isResampled -> "Mixer"
        else -> "PCM"
    }
    // Measured detail, shown only once the probe has returned. "96 kHz native" means the
    // platform accepted an AudioTrack at the source rate; "48 kHz" means it resampled.
    val pathDetail = when {
        info.probedOutputRate <= 0 -> null
        info.probeAcceptedSourceRate -> "${formatSampleRate(info.probedOutputRate)} native"
        else -> "→ ${formatSampleRate(info.probedOutputRate)}"
    }
    // Highlight the path only when it is actually lossy in some way, so a clean
    // bitstream does not look like a warning.
    val pathColor = when {
        pathLabel == "Mixer" -> textColor.copy(alpha = 0.92f)
        pathLabel == "Direct" -> textColor
        else -> textColor.copy(alpha = 0.72f)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(
                color = textColor.copy(alpha = 0.10f),
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        BadgeDot(color = pathColor)
        Spacer(Modifier.width(1.dp))
        QualitySegment(codecLabel, textColor, emphasized = true)
        QualityDivider(textColor)
        QualitySegment(rateLabel, textColor)
        if (depthLabel != null) {
            QualityDivider(textColor)
            QualitySegment(depthLabel, textColor)
        }
        if (bitrateLabel != null) {
            QualityDivider(textColor)
            QualitySegment(bitrateLabel, textColor)
        }
        if (channelLabel != null) {
            QualityDivider(textColor)
            QualitySegment(channelLabel, textColor)
        }
        QualityDivider(textColor)
        QualitySegment(pathLabel, pathColor, emphasized = true)
        if (pathDetail != null) {
            Spacer(Modifier.width(3.dp))
            QualitySegment(pathDetail, textColor)
        }
        // Hardware vs software decoder: cheap to show and genuinely useful when judging
        // whether a heavy format is being decoded in the fast path.
        if (decoder != null) {
            QualityDivider(textColor)
            QualitySegment(if (decoder.isHardware) "HW" else "SW", textColor)
        }
    }
}

@Composable
private fun BadgeDot(color: Color) {
    val alpha by animateFloatAsState(targetValue = 1f, label = "badgeDot")
    Box(
        modifier = Modifier
            .size(5.dp)
            .background(color.copy(alpha = alpha * 0.95f), CircleShape),
    )
}

@Composable
private fun QualitySegment(
    text: String,
    color: Color,
    emphasized: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
        color = color.copy(alpha = if (emphasized) 0.95f else 0.78f),
        maxLines = 1,
    )
}

@Composable
private fun QualityDivider(color: Color) {
    Text(
        text = "·",
        style = MaterialTheme.typography.labelSmall,
        color = color.copy(alpha = 0.35f),
    )
}

/** Turns a Media3 MIME type into the short label people recognise. */
internal fun codecDisplayName(mimeType: String?): String = when (mimeType) {
    MimeTypes.AUDIO_FLAC -> "FLAC"
    MimeTypes.AUDIO_ALAC -> "ALAC"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_AC3 -> "AC3"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "EAC3"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_VORBIS -> "Vorbis"
    MimeTypes.AUDIO_AMR, MimeTypes.AUDIO_AMR_NB -> "AMR-NB"
    MimeTypes.AUDIO_AMR_WB -> "AMR-WB"
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD -> "DTS"
    MimeTypes.AUDIO_TRUEHD -> "TrueHD"
    MimeTypes.AUDIO_WAV -> "WAV"
    // Media3 exposes no AUDIO_AIFF constant; the extractor reports this literal string.
    "audio/x-aiff", "audio/aiff" -> "AIFF"
    MimeTypes.AUDIO_RAW -> "PCM"
    MimeTypes.AUDIO_MP4 -> "M4A"
    MimeTypes.AUDIO_AC4 -> "AC4"
    null -> "—"
    else -> mimeType.substringAfter('/').uppercase(Locale.US)
}

/** 44100 -> "44.1 kHz", 96000 -> "96 kHz", 8000 -> "8 kHz". */
internal fun formatSampleRate(hz: Int): String {
    if (hz <= 0) return "—"
    val khz = hz / 1000.0
    return if (hz % 1000 == 0) {
        "${hz / 1000} kHz"
    } else {
        String.format(Locale.US, "%.1f kHz", khz)
    }
}

/** 320000 -> "320 kbps", 5000000 -> "5.0 Mbps". */
internal fun formatBitrate(bitsPerSecond: Int): String {
    if (bitsPerSecond <= 0) return "—"
    return if (bitsPerSecond >= 1_000_000) {
        String.format(Locale.US, "%.1f Mbps", bitsPerSecond / 1_000_000.0)
    } else {
        "${bitsPerSecond / 1000} kbps"
    }
}

/** Only labels non-stereo setups; stereo is the unremarkable default. */
internal fun channelDisplayName(channels: Int): String? = when (channels) {
    1 -> "Mono"
    2 -> null
    in 3..8 -> "${channels}ch"
    else -> null
}
