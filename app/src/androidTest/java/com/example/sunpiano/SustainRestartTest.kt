package com.example.sunpiano

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sunpiano.audio.AudioOutput
import com.example.sunpiano.audio.Instruments
import com.example.sunpiano.audio.SynthEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** The sustain pedal must survive the engine being stopped and started, as when leaving and returning to the app (bug.md #3). */
@RunWith(AndroidJUnit4::class)
class SustainRestartTest {
    /** Accepts blocks at roughly real-time pace and remembers how loud the last one was. */
    private class LevelOutput : AudioOutput {
        @Volatile var lastPeak = 0f
        override fun play() {}
        override fun write(data: FloatArray): Int {
            Thread.sleep(5)
            lastPeak = data.maxOf { abs(it) }
            return data.size
        }
        override fun stop() {}
        override fun release() {}
    }

    /** Level 0.8 s after releasing a note played once the engine was stopped and restarted. */
    private fun levelAfterReleaseAcrossRestart(sustain: Boolean): Float {
        val output = LevelOutput()
        // Organ: holds at full level while sustained, silent ~0.4 s after release otherwise
        val engine = SynthEngine { output }
        engine.useInstrument(Instruments.ORGAN)
        engine.reverbLevel = 0f
        engine.setSustain(sustain)
        engine.start()
        Thread.sleep(100)
        engine.stop() // leaving the app
        engine.start() // coming back
        try {
            engine.noteOn(60, 100)
            Thread.sleep(300)
            engine.noteOff(60)
            Thread.sleep(800)
            return output.lastPeak
        } finally {
            engine.stop()
        }
    }

    @Test
    fun sustainStillHoldsNotesAfterRestart() {
        val level = levelAfterReleaseAcrossRestart(sustain = true)
        assertTrue("released note died although sustain is on (level $level)", level > 0.01f)
    }

    @Test
    fun withoutSustainTheReleasedNoteStops() {
        assertEquals(0f, levelAfterReleaseAcrossRestart(sustain = false), 0f)
    }
}
