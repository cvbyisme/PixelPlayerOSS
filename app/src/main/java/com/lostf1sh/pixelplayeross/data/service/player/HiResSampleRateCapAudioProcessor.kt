package com.lostf1sh.pixelplayeross.data.service.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil

/**
 * Caps ultra-high-rate PCM to a rate Android devices handle more reliably.
 *
 * Media3/AudioTrack can end up in unstable device-specific paths for sample rates above 192 kHz.
 * Reducing 352.8/384 kHz streams before they reach the sink avoids the "loading audio" hang while
 * preserving normal playback for standard and regular hi-res files.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class HiResSampleRateCapAudioProcessor(
    private val maxOutputSampleRateHz: Int = 192_000
) : AudioProcessor {

    /**
     * cvby: decimation needs a low-pass first.
     *
     * The previous implementation averaged every [downsampleFactor] input frames (a box filter).
     * That is a poor anti-alias filter: content above the new Nyquist folds back into the
     * audible band as aliasing, which sounds like gritty/harsh treble -- exactly the opposite of
     * what a hi-res downsampler should do. Every sample is now run through a one-pole high-cut
     * IIR whose corner sits below the output Nyquist before being decimated, so out-of-band
     * energy is attenuated instead of reflected.
     */
    private companion object {
        private val NATIVE_ORDER = ByteOrder.nativeOrder()
        private val NATIVE_ORDER_IS_BIG_ENDIAN = NATIVE_ORDER == ByteOrder.BIG_ENDIAN

        /** Corner frequency as a fraction of the *output* Nyquist (must stay below 1.0). */
        private const val LOWPASS_PASSBAND_RATIO = 0.45
    }

    private var inputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var downsampleFactor: Int = 1
    // One-pole low-pass state, one value per channel. Applied before decimation so energy
    // above the output Nyquist is attenuated rather than aliased back into the audible band.
    private var lowpassCoefficient: Float = 0f
    private var lowpassState = FloatArray(0)
    // Retain storage when getOutput() clears the pending output reference.
    private var reusableBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var pendingBytes = ByteArray(0)
    private var pendingSize = 0
    private var inputEnded = false

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        val shouldDownsample = (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT || inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) &&
            inputAudioFormat.channelCount > 0 &&
            inputAudioFormat.sampleRate > maxOutputSampleRateHz

        if (!shouldDownsample) {
            inputFormat = AudioFormat.NOT_SET
            outputFormat = AudioFormat.NOT_SET
            downsampleFactor = 1
            pendingSize = 0
            return inputAudioFormat
        }

        downsampleFactor = ceil(
            inputAudioFormat.sampleRate.toDouble() / maxOutputSampleRateHz.toDouble()
        ).toInt().coerceAtLeast(2)

        inputFormat = inputAudioFormat
        outputFormat = AudioFormat(
            inputAudioFormat.sampleRate / downsampleFactor,
            inputAudioFormat.channelCount,
            inputAudioFormat.encoding
        )
        // Corner = LOWPASS_PASSBAND_RATIO * (output rate / 2), expressed relative to the input
        // rate because the filter runs at the input rate.
        //   fc/fs_in = LOWPASS_PASSBAND_RATIO * outputRate / (2 * inputRate)
        // One-pole: y[n] = y[n-1] + a * (x[n] - y[n-1]), a = 1 - exp(-2*pi*fc/fs).
        val inputRate = inputAudioFormat.sampleRate.toDouble()
        val normalizedCorner = LOWPASS_PASSBAND_RATIO.toDouble() *
            (outputFormat.sampleRate / 2.0) / inputRate
        lowpassCoefficient = if (normalizedCorner > 0.0 && normalizedCorner < 0.5) {
            (1.0 - kotlin.math.exp(-2.0 * Math.PI * normalizedCorner)).toFloat()
        } else {
            1f
        }
        lowpassState = FloatArray(inputAudioFormat.channelCount)
        pendingSize = 0
        ensurePendingCapacity(maxPendingBytes())
        return outputFormat
    }

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!isActive()) return

        if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
            processFloat(inputBuffer)
        } else {
            process16Bit(inputBuffer)
        }
        inputBuffer.position(inputBuffer.limit())
    }

    private fun process16Bit(inputBuffer: ByteBuffer) {
        val bytesPerFrame = inputFormat.channelCount * Short.SIZE_BYTES
        val processableFrameCount = processableFrameCount(bytesPerFrame, inputBuffer.remaining())
        if (processableFrameCount == 0) {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            stashPendingBytes(inputBuffer)
            return
        }

        outputBuffer = ensureOutputBuffer(processableFrameCount * bytesPerFrame)
        val shortInput = inputBuffer.duplicate().order(NATIVE_ORDER)
        var pendingReadOffset = 0

        repeat(processableFrameCount) {
            // Same anti-alias approach as the float path: filter every input frame, then emit the
            // last filtered value of each decimation group.
            repeat(downsampleFactor) {
                for (ch in 0 until inputFormat.channelCount) {
                    val x = if (pendingReadOffset < pendingSize) {
                        val v = readShortFromPending(pendingReadOffset).toFloat()
                        pendingReadOffset += Short.SIZE_BYTES
                        v
                    } else {
                        shortInput.short.toFloat()
                    }
                    val previous = lowpassState[ch]
                    lowpassState[ch] = previous + lowpassCoefficient * (x - previous)
                }
            }
            for (ch in 0 until inputFormat.channelCount) {
                // Back to 16-bit; clamp instead of wrapping on overshoot.
                val sample = lowpassState[ch].coerceIn(
                    Short.MIN_VALUE.toFloat(),
                    Short.MAX_VALUE.toFloat()
                )
                outputBuffer.putShort(sample.toInt().toShort())
            }
        }
        storePendingRemainder(pendingReadOffset, shortInput)
        outputBuffer.flip()
    }

    private fun processFloat(inputBuffer: ByteBuffer) {
        val bytesPerSample = Float.SIZE_BYTES
        val bytesPerFrame = inputFormat.channelCount * bytesPerSample
        val processableFrameCount = processableFrameCount(bytesPerFrame, inputBuffer.remaining())
        if (processableFrameCount == 0) {
            outputBuffer = AudioProcessor.EMPTY_BUFFER
            stashPendingBytes(inputBuffer)
            return
        }

        outputBuffer = ensureOutputBuffer(processableFrameCount * bytesPerFrame)
        val floatInput = inputBuffer.duplicate().order(NATIVE_ORDER)
        var pendingReadOffset = 0

        repeat(processableFrameCount) {
            // Run every input frame through the low-pass, then emit the final filtered value of
            // each decimation group. Filtering before dropping samples is what prevents aliasing.
            repeat(downsampleFactor) {
                for (ch in 0 until inputFormat.channelCount) {
                    val x = if (pendingReadOffset < pendingSize) {
                        val v = readFloatFromPending(pendingReadOffset)
                        pendingReadOffset += Float.SIZE_BYTES
                        v
                    } else {
                        floatInput.float
                    }
                    val previous = lowpassState[ch]
                    lowpassState[ch] = previous + lowpassCoefficient * (x - previous)
                }
            }
            for (ch in 0 until inputFormat.channelCount) {
                outputBuffer.putFloat(lowpassState[ch].coerceIn(-1f, 1f))
            }
        }
        storePendingRemainder(pendingReadOffset, floatInput)
        outputBuffer.flip()
    }

    private fun processableFrameCount(bytesPerFrame: Int, incomingBytes: Int): Int {
        val totalFrames = (pendingSize + incomingBytes) / bytesPerFrame
        return totalFrames / downsampleFactor
    }

    private fun maxPendingBytes(): Int {
        val sampleBytes = if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) Float.SIZE_BYTES else Short.SIZE_BYTES
        return inputFormat.channelCount * sampleBytes * downsampleFactor
    }

    private fun ensurePendingCapacity(requiredCapacity: Int) {
        if (pendingBytes.size >= requiredCapacity) return
        pendingBytes = pendingBytes.copyOf(requiredCapacity.coerceAtLeast(pendingBytes.size * 2).coerceAtLeast(1))
    }

    private fun ensureOutputBuffer(requiredCapacity: Int): ByteBuffer {
        if (reusableBuffer.capacity() < requiredCapacity) {
            reusableBuffer = ByteBuffer.allocateDirect(requiredCapacity).order(NATIVE_ORDER)
        } else {
            reusableBuffer.clear()
        }
        return reusableBuffer
    }

    private fun stashPendingBytes(inputBuffer: ByteBuffer) {
        ensurePendingCapacity(pendingSize + inputBuffer.remaining())
        val source = inputBuffer.duplicate()
        source.get(pendingBytes, pendingSize, source.remaining())
        pendingSize += inputBuffer.remaining()
    }

    private fun storePendingRemainder(pendingReadOffset: Int, inputBuffer: ByteBuffer) {
        val unreadPendingBytes = pendingSize - pendingReadOffset
        val remainingInputBytes = inputBuffer.remaining()
        ensurePendingCapacity(unreadPendingBytes + remainingInputBytes)
        if (unreadPendingBytes > 0 && pendingReadOffset > 0) {
            pendingBytes.copyInto(
                destination = pendingBytes,
                destinationOffset = 0,
                startIndex = pendingReadOffset,
                endIndex = pendingSize
            )
        }
        if (remainingInputBytes > 0) {
            inputBuffer.get(pendingBytes, unreadPendingBytes, remainingInputBytes)
        }
        pendingSize = unreadPendingBytes + remainingInputBytes
    }

    private fun readShortFromPending(offset: Int): Short {
        val byte0 = pendingBytes[offset].toInt() and 0xFF
        val byte1 = pendingBytes[offset + 1].toInt() and 0xFF
        val bits = if (NATIVE_ORDER_IS_BIG_ENDIAN) {
            (byte0 shl 8) or byte1
        } else {
            (byte1 shl 8) or byte0
        }
        return bits.toShort()
    }

    private fun readFloatFromPending(offset: Int): Float {
        val intBits = readIntFromPending(offset)
        return Float.fromBits(intBits)
    }

    private fun readIntFromPending(offset: Int): Int {
        val byte0 = pendingBytes[offset].toInt() and 0xFF
        val byte1 = pendingBytes[offset + 1].toInt() and 0xFF
        val byte2 = pendingBytes[offset + 2].toInt() and 0xFF
        val byte3 = pendingBytes[offset + 3].toInt() and 0xFF
        return if (NATIVE_ORDER_IS_BIG_ENDIAN) {
            (byte0 shl 24) or (byte1 shl 16) or (byte2 shl 8) or byte3
        } else {
            (byte3 shl 24) or (byte2 shl 16) or (byte1 shl 8) or byte0
        }
    }

    override fun getOutput(): ByteBuffer {
        val pendingOutput = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return pendingOutput
    }

    override fun isEnded(): Boolean = inputEnded && pendingSize == 0 && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun queueEndOfStream() {
        // Emit whatever tail is still buffered instead of dropping it. A leftover group is the
        // last few milliseconds of the track; discarding it clipped the end of every song that
        // happened to end mid-group. Any trailing bytes that do not form a whole frame are
        // still dropped, since they cannot be decoded as audio.
        emitPendingTail()
        pendingSize = 0
        inputEnded = true
    }

    /** Runs the remaining buffered frames through the filter and emits one output frame each. */
    private fun emitPendingTail() {
        if (!isActive() || pendingSize <= 0) return
        val sampleBytes = if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) Float.SIZE_BYTES else Short.SIZE_BYTES
        val bytesPerFrame = inputFormat.channelCount * sampleBytes
        val wholeFrames = pendingSize / bytesPerFrame
        if (wholeFrames == 0) return

        outputBuffer = ensureOutputBuffer(wholeFrames * bytesPerFrame)
        var offset = 0
        repeat(wholeFrames) {
            for (ch in 0 until inputFormat.channelCount) {
                val x = if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    val v = readFloatFromPending(offset)
                    offset += Float.SIZE_BYTES
                    v
                } else {
                    val v = readShortFromPending(offset).toFloat()
                    offset += Short.SIZE_BYTES
                    v
                }
                val previous = lowpassState[ch]
                lowpassState[ch] = previous + lowpassCoefficient * (x - previous)
            }
            for (ch in 0 until inputFormat.channelCount) {
                if (inputFormat.encoding == C.ENCODING_PCM_FLOAT) {
                    outputBuffer.putFloat(lowpassState[ch].coerceIn(-1f, 1f))
                } else {
                    val sample = lowpassState[ch].coerceIn(
                        Short.MIN_VALUE.toFloat(),
                        Short.MAX_VALUE.toFloat()
                    )
                    outputBuffer.putShort(sample.toInt().toShort())
                }
            }
        }
        outputBuffer.flip()
    }

    @Deprecated("Media3 AudioProcessor now prefers flush(StreamMetadata); kept for interface compatibility")
    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pendingSize = 0
        inputEnded = false
        // Drop filter memory so a seek does not bleed the previous position's tail into the
        // new one.
        java.util.Arrays.fill(lowpassState, 0f)
    }

    override fun reset() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        reusableBuffer = AudioProcessor.EMPTY_BUFFER
        pendingSize = 0
        inputEnded = false
        inputFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        downsampleFactor = 1
        lowpassCoefficient = 0f
        lowpassState = FloatArray(0)
    }
}
