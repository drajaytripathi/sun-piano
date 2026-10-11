package code.alimiracle.sunpiano.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import code.alimiracle.sunpiano.data.NoteEvent
import code.alimiracle.sunpiano.data.Recording
import code.alimiracle.sunpiano.data.Settings
import java.io.File
import java.io.IOException
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns a recording into sound: plays its notes through a [SynthEngine] of its own, faster than
 * real time and without an audio output, and encodes the result as AAC in an .m4a file. AAC is
 * Android's built-in encoder (there is none for MP3) and plays everywhere MP3 does.
 *
 * Each note sounds with the instrument it was recorded with, and the recording's saved volume,
 * reverb and tuning apply. An old recording without them uses [current] instead.
 */
object AudioExporter {
    private const val BIT_RATE = 192_000
    private const val INPUT_BUFFER_BYTES = 64 * 1024
    /** Longest time let ring after the last event (reverb, Strings' release). */
    private const val MAX_TAIL_MS = 4_000L
    /** The tail ends once it has been this quiet for this long. */
    private const val SILENCE = 1f / 32768
    private const val SILENT_MS = 300L
    private const val INSTRUMENT_WAIT_MS = 10_000L

    /** Writes [rec] to [file]; [onProgress] gets 0..1. Throws [IOException] if it can't. */
    fun exportM4a(rec: Recording, current: Settings, file: File, onProgress: (Float) -> Unit = {}) {
        val settings = rec.settings ?: current
        val engine = SynthEngine() // never started: only this thread renders it
        val fallback = Instruments.byId(settings.instrumentId) // for notes that don't say
        val used = (rec.instrumentIds.map(Instruments::byId) + fallback).distinct()
        engine.setInstrument(used.first()) // built first; the others are built in the background
        val deadline = SystemClock.uptimeMillis() + INSTRUMENT_WAIT_MS
        for (ins in used) {
            while (!engine.isReady(ins)) {
                if (SystemClock.uptimeMillis() > deadline) throw IOException("${ins.name} wasn't ready")
                Thread.sleep(5)
            }
        }
        engine.setInstrument(fallback)
        engine.volume = settings.volume
        engine.reverbLevel = settings.reverb
        engine.tuningA4 = settings.tuning.toDouble()

        val rate = engine.sampleRate
        val events = rec.events.sortedBy { it.timeMs }
        val endMs = maxOf(rec.durationMs, events.lastOrNull()?.timeMs ?: 0L)
        val lastEventFrame = endMs * rate / 1000
        val maxFrames = (endMs + MAX_TAIL_MS) * rate / 1000

        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 2).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            // Each buffer handed to the encoder costs a round trip to the media service (~5 ms);
            // its default 4 KB buffers hold only 21 ms of sound, so ask for bigger ones.
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, INPUT_BUFFER_BYTES)
        }
        val codec = try {
            MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        } catch (e: IllegalArgumentException) {
            throw IOException("no AAC encoder", e)
        }
        var muxer: MediaMuxer? = null
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            val info = MediaCodec.BufferInfo()
            val block = FloatArray(2 * SynthEngine.BLOCK)
            val blockBytes = block.size * 2
            var frame = 0L
            var next = 0
            var quietFrames = 0L
            var inputDone = false
            var lastPercent = -1
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        val buffer = codec.getInputBuffer(index)!!.order(ByteOrder.LITTLE_ENDIAN)
                        buffer.clear()
                        val startFrame = frame
                        var finished = false
                        while (buffer.remaining() >= blockBytes) {
                            // events due by this block's start (a block is about 5 ms)
                            while (next < events.size && events[next].timeMs * rate / 1000 <= frame) {
                                val e = events[next++]
                                when {
                                    e.note == NoteEvent.PEDAL -> engine.setSustain(e.velocity > 0)
                                    e.velocity > 0 -> engine.noteOn(e.note, e.velocity, Instruments.byId(rec.instrumentFor(e) ?: fallback.id))
                                    else -> engine.noteOff(e.note)
                                }
                            }
                            engine.renderBlock(block)
                            var peak = 0f
                            for (x in block) {
                                peak = maxOf(peak, abs(x))
                                buffer.putShort((x.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort())
                            }
                            frame += SynthEngine.BLOCK
                            quietFrames = if (peak < SILENCE) quietFrames + SynthEngine.BLOCK else 0
                            val pastEnd = frame >= lastEventFrame && next >= events.size
                            if (frame >= maxFrames || (pastEnd && quietFrames * 1000 >= SILENT_MS * rate)) {
                                finished = true
                                break
                            }
                        }
                        val pts = startFrame * 1_000_000 / rate
                        codec.queueInputBuffer(index, 0, buffer.position(), pts, if (finished) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                        if (finished) inputDone = true
                        val percent = (frame * 100 / maxOf(lastEventFrame, 1)).toInt().coerceAtMost(100)
                        if (percent != lastPercent) { // each report redraws the button: only when the percentage changes
                            lastPercent = percent
                            onProgress(percent / 100f)
                        }
                    }
                }
                // take out everything encoded so far: a full output queue stalls the encoder, which
                // then hands input buffers back slowly
                var draining = true
                while (draining && !outputDone) {
                    val out = codec.dequeueOutputBuffer(info, if (inputDone) 10_000 else 0)
                    when {
                        out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            track = muxer.addTrack(codec.outputFormat)
                            muxer.start()
                        }
                        out >= 0 -> {
                            val data = codec.getOutputBuffer(out)!!
                            val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                            if (!config && info.size > 0 && track >= 0) {
                                data.position(info.offset).limit(info.offset + info.size)
                                muxer.writeSampleData(track, data, info)
                            }
                            codec.releaseOutputBuffer(out, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                        else -> draining = false // nothing ready yet
                    }
                }
            }
            muxer.stop()
        } catch (e: IllegalStateException) { // MediaCodec.CodecException, or the muxer refusing
            file.delete()
            throw IOException("encoding failed", e)
        } catch (e: IOException) {
            file.delete()
            throw e
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { muxer?.release() }
        }
    }
}
