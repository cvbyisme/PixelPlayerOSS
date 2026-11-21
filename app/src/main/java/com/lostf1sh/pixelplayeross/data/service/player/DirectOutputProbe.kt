package com.lostf1sh.pixelplayeross.data.service.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log
import androidx.media3.common.util.UnstableApi

/**
 * cvby: runtime probe for the *real* output path.
 *
 * Tells us whether the platform actually granted a direct/unmixed AudioTrack at the
 * source sample rate, instead of silently inserting a resampler and pinning everything
 * to the mixer rate (48 kHz on essentially every Android device).
 *
 * Side-effect free: every AudioTrack is created MODE_STATIC and released immediately.
 */
@UnstableApi
object DirectOutputProbe {

    private const val TAG = "DirectOutputProbe"

    private val PREFERRED_ENCODINGS = intArrayOf(
        AudioFormat.ENCODING_PCM_32BIT,
        AudioFormat.ENCODING_PCM_24BIT_PACKED,
        AudioFormat.ENCODING_PCM_16BIT
    )

    data class Result(
        val directGranted: Boolean,
        /**
         * The rate the platform actually plays this track at.
         *
         * Only meaningful when [directGranted] is true, i.e. the platform echoed back exactly the
         * rate that was requested. When it did not, this holds the *rejected* request's echo,
         * which describes a static AudioTrack slot and NOT what the mixer is running at -- some
         * devices answer a 96 kHz request with 384 kHz (their top PCM slot), and reporting that
         * number as an output rate is what made the badge claim "96 kHz source, 384 kHz output".
         * Read [reportedSampleRate] instead of this field for display purposes.
         */
        val actualSampleRate: Int,
        val actualEncoding: Int,
        val fellBackToMixer: Boolean,
        val deviceName: String?,
        val rejectionReason: String?
    ) {
        /**
         * Rate worth showing to a user, or 0 when the probe result must not be trusted.
         *
         * A rejected request tells us the track is going through the mixer, but it tells us
         * nothing about the mixer's rate (a plain app cannot read the HAL clock). Returning 0
         * keeps the badge honest instead of printing the platform's echo of a rejected value.
         */
        val reportedSampleRate: Int
            get() = if (directGranted) actualSampleRate else 0

        val encodingLabel: String
            get() = when (actualEncoding) {
                AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit packed"
                AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
                AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
                else -> "unknown"
            }

        /** Log-friendly one-liner; never prints a rejected request's echo as an output rate. */
        val summary: String
            get() {
                val rate = reportedSampleRate
                val rateLabel = if (rate > 0) "${rate / 1000f} kHz / " else ""
                return "$rateLabel$encodingLabel  " + if (directGranted) "[DIRECT]" else "[MIXER]"
            }
    }


    // Keyed by rate *and* encoding: a 96 kHz result for 24-bit packed must not be reused for
    // a float request, since the platform can accept one and refuse the other.
    private val cache = HashMap<Long, Result>()

    private fun cacheKey(sampleRate: Int, encoding: Int): Long =
        (sampleRate.toLong() shl 32) or (encoding.toLong() and 0xffffffffL)

    @Synchronized
    fun probe(
        context: Context,
        sampleRate: Int,
        encoding: Int = AudioFormat.ENCODING_PCM_24BIT_PACKED
    ): Result {
        val key = cacheKey(sampleRate, encoding)
        cache[key]?.let { return it }
        return runProbe(context, sampleRate, encoding).also { cache[key] = it }
    }

    @Synchronized
    fun invalidate() {
        cache.clear()
    }

    fun currentOutputDevice(context: Context): AudioDeviceInfo? {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return null
        val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS) ?: return null
        return devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        } ?: devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    /** True when a USB audio device is currently attached. */
    fun hasUsbAudio(context: Context): Boolean {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET || it.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
    }

    private fun runProbe(context: Context, sampleRate: Int, encoding: Int): Result {
        val device = currentOutputDevice(context)
        val deviceName = device?.productName?.toString()?.takeIf { it.isNotBlank() }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        var lastReason: String? = null

        val ordered = PREFERRED_ENCODINGS.filter { it == encoding } +
            PREFERRED_ENCODINGS.filter { it != encoding }

        for (enc in ordered) {
            val minBytes = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                enc
            )
            if (minBytes <= 0) {
                lastReason = "getMinBufferSize rejected enc=$enc @ ${sampleRate}Hz (code=$minBytes)"
                continue
            }
            val track = try {
                AudioTrack.Builder()
                    .setAudioAttributes(attrs)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(enc)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                    .build()
            } catch (e: Exception) {
                lastReason = "AudioTrack.Builder threw enc=$enc @ ${sampleRate}Hz: ${e.message}"
                Log.w(TAG, "probe build failed enc=$enc rate=$sampleRate", e)
                continue
            }

            try {
                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    lastReason = "AudioTrack not INITIALIZED enc=$enc @ ${sampleRate}Hz"
                    continue
                }
                val actualRate = track.sampleRate
                val actualEnc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    track.format?.encoding ?: enc
                } else enc
                val downgraded = actualRate != sampleRate
                val result = Result(
                    directGranted = !downgraded,
                    actualSampleRate = actualRate,
                    actualEncoding = actualEnc,
                    fellBackToMixer = downgraded,
                    deviceName = deviceName,
                    rejectionReason = if (downgraded) {
                        "requested ${sampleRate}Hz, platform returned ${actualRate}Hz"
                    } else null
                )
                Log.i(TAG, "probe ok $result")
                return result
            } finally {
                try {
                    track.release()
                } catch (_: Exception) {
                }
            }
        }

        return Result(
            directGranted = false,
            actualSampleRate = 48_000,
            actualEncoding = AudioFormat.ENCODING_PCM_16BIT,
            fellBackToMixer = true,
            deviceName = deviceName,
            rejectionReason = lastReason ?: "no encoding accepted at ${sampleRate}Hz"
        )
    }
}
