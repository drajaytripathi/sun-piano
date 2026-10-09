package com.example.sunpiano.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.max

/** Where [SynthEngine] plays its interleaved stereo float blocks: an [AudioTrack] in the app, a fake in tests. */
interface AudioOutput {
    fun play()

    /** Blocks until written; returns the number of floats written or a negative `AudioTrack` error code. */
    fun write(data: FloatArray): Int

    fun stop()

    fun release()
}

/** The real output: a low-latency float stereo [AudioTrack]. */
class AudioTrackOutput(sampleRate: Int) : AudioOutput {
    private val track: AudioTrack = run {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        val bytes = max(minBytes, SynthEngine.BLOCK * 2 * 4 * 2)
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .setBufferSizeInBytes(bytes)
            .build()
    }

    override fun play() = track.play()

    override fun write(data: FloatArray): Int = track.write(data, 0, data.size, AudioTrack.WRITE_BLOCKING)

    override fun stop() = track.stop()

    override fun release() = track.release()
}
