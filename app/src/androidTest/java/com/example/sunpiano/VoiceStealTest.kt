package com.example.sunpiano

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sunpiano.audio.AudioOutput
import com.example.sunpiano.audio.Instruments
import com.example.sunpiano.audio.SynthEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Playing more than [SynthEngine.MAX_VOICES] notes must not click (bug.md #14). */
@RunWith(AndroidJUnit4::class)
class VoiceStealTest {
    /** Keeps the left channel of everything written, at roughly real-time pace. */
    private class CapturingOutput(capacity: Int) : AudioOutput {
        val left = FloatArray(capacity)
        @Volatile var frames = 0
        override fun play() {}
        override fun write(data: FloatArray): Int {
            Thread.sleep(5)
            var n = frames
            var i = 0
            while (i < data.size && n < left.size) { left[n++] = data[i]; i += 2 }
            frames = n
            return data.size
        }
        override fun stop() {}
        override fun release() {}
    }

    /** Largest |x[n+1] - 2x[n] + x[n-1]|: tiny for smooth tones, large at a click. */
    private fun maxSecondDifference(x: FloatArray, from: Int, to: Int): Float {
        var m = 0f
        for (n in maxOf(from, 1) until minOf(to, x.size - 1)) m = maxOf(m, abs(x[n + 1] - 2 * x[n] + x[n - 1]))
        return m
    }

    @Test
    fun stealingAVoiceDoesNotClick() {
        val output = CapturingOutput(48000 * 4)
        val engine = SynthEngine { output }
        engine.setInstrument(Instruments.ORGAN) // steady tone: an abrupt stop would stand out
        engine.reverbLevel = 0f
        engine.volume = 1f
        engine.start()
        try {
            for (note in 36 until 36 + SynthEngine.MAX_VOICES) engine.noteOn(note, 20) // soft, low: no clipping
            Thread.sleep(400)
            val stealAt = output.frames
            // Six notes too many, one after another: each steals the oldest voice. A hard cut only
            // clicks if the stolen note isn't near a zero crossing, so several steals make sure.
            for (note in 60 until 66) {
                engine.noteOn(note, 20)
                Thread.sleep(80)
            }
            val stealEnd = output.frames
            Thread.sleep(500) // let it settle on the new set of notes
            val end = output.frames
            val window = (0.3 * engine.sampleRate).toInt()
            // Higher notes naturally change faster from sample to sample, so compare the stealing
            // with the steady sound both before it and after it (which contains the new notes).
            val steady = maxOf(
                maxSecondDifference(output.left, stealAt - window, stealAt),
                maxSecondDifference(output.left, end - window, end),
            )
            val stealing = maxSecondDifference(output.left, stealAt, stealEnd + window / 3)
            Log.i("VoiceStealTest", "second difference: steady $steady, while stealing $stealing")
            assertTrue("steady part too short to measure", steady > 0f)
            assertTrue("click when a voice was stolen: $stealing vs steady $steady", stealing < steady * 2)
        } finally {
            engine.stop()
        }
    }
}
