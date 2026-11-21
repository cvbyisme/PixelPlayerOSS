package com.lostf1sh.pixelplayeross.data.service

import com.lostf1sh.pixelplayeross.data.model.AudioOutputMode

/**
 * cvby: resolves the user preferences into the settings actually handed to the engine.
 *
 * High-quality mode does not overwrite the stored preferences. It *overrides* them for as
 * long as it is on, so toggling it off restores exactly what the user had picked before.
 *
 * What the override does, and why each part is a real improvement for a plain app:
 *
 *  - output mode PCM_FLOAT: 32-bit float keeps the mixer path from quantising to 16-bit.
 *    Falls back to the user choice when the device rejects PCM_FLOAT (the engine already
 *    detects that and logs it).
 *  - offload off: COMPRESS_OFFLOAD hands the compressed bitstream to a vendor tunnel whose
 *    internal bit depth and resampling cannot be inspected or changed from an app. Going
 *    through decoded PCM means the app controls the whole chain.
 *  - hi-res cap on: for >192 kHz sources this now filters before decimating (anti-aliased)
 *    instead of averaging, which used to fold high frequencies back into the audible band.
 *
 * It does NOT raise the DAC rate. The system mixer rate is a platform decision and a plain
 * app cannot change it; the player badge reports what actually happened.
 */
internal data class HighQualityAudioSettings(
    val outputMode: AudioOutputMode,
    val offload: Boolean,
    val hiResCap: Boolean,
    val surroundDownmix: Boolean,
    val highQuality: Boolean
) {
    val effectiveOutputMode: AudioOutputMode
        get() = if (highQuality) AudioOutputMode.PCM_FLOAT else outputMode

    val effectiveOffload: Boolean
        get() = if (highQuality) false else offload

    val effectiveHiResCap: Boolean
        get() = if (highQuality) true else hiResCap

    val effectiveSurroundDownmix: Boolean
        get() = surroundDownmix
}
