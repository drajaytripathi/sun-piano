package com.example.sunpiano

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sunpiano.audio.AudioOutput
import com.example.sunpiano.audio.Instruments
import com.example.sunpiano.audio.SynthEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** What was sounding when the engine stopped must not come back as an echo on restart (bug.md #20). */
@RunWith(AndroidJUnit4::class)
class ReverbClearTest {
    private class Sink : AudioOutput {
        @Volatile var peak = 0f
        override fun play() {}
        override fun write(data: FloatArray): Int {
            for (x in data) peak = maxOf(peak, abs(x))
            SystemClock.sleep(5) // roughly real time
            return data.size
        }
        override fun stop() {}
        override fun release() {}
    }

    @Test
    fun restartingAfterAChordIsSilent() {
        val sink = Sink()
        val engine = SynthEngine { sink }
        engine.reverbLevel = 0.5f
        engine.useInstrument(Instruments.ORGAN)
        engine.metronomeOn = true
        engine.start()
        engine.noteOn(60, 120); engine.noteOn(64, 120); engine.noteOn(67, 120)
        SystemClock.sleep(600)
        assertTrue("the chord didn't play", sink.peak > 0.1f)
        engine.metronomeOn = false
        engine.stop() // leaving the app with the chord held

        val block = FloatArray(SynthEngine.BLOCK * 2)
        var peak = 0f
        repeat(40) { engine.renderBlock(block); for (x in block) peak = maxOf(peak, abs(x)) }
        assertEquals("echo of the old chord after restarting", 0f, peak, 0.001f)
    }
}
