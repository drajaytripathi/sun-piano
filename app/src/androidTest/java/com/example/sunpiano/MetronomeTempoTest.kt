package com.example.sunpiano

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sunpiano.audio.SynthEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A tempo change applies to the beat in progress (bug.md #22). */
@RunWith(AndroidJUnit4::class)
class MetronomeTempoTest {
    private val engine = SynthEngine()
    private val block = FloatArray(SynthEngine.BLOCK * 2)
    private var beats = 0

    init {
        engine.onBeat = { beats++ }
        engine.metronomeOn = true
    }

    /** Renders until the next click; returns the seconds that took. */
    private fun secondsToNextBeat(): Double {
        val target = beats + 1
        var blocks = 0
        while (beats < target) {
            engine.renderBlock(block)
            check(++blocks < 10_000) { "no click" }
        }
        return blocks * SynthEngine.BLOCK.toDouble() / engine.sampleRate
    }

    private fun render(seconds: Double) = repeat((seconds * engine.sampleRate / SynthEngine.BLOCK).toInt()) { engine.renderBlock(block) }

    @Test
    fun speedingUpFromThirtyBpmStartsTheNewTempoRightAway() {
        engine.bpm = 30
        secondsToNextBeat() // first click
        render(0.2)
        engine.bpm = 240 // 0.25 s beats
        val wait = secondsToNextBeat()
        assertTrue("next click came after $wait s at 240 BPM", wait < 0.3)
        assertEquals(0.25, secondsToNextBeat(), 0.01)
    }

    @Test
    fun slowingDownStretchesTheBeatInProgress() {
        engine.bpm = 240
        secondsToNextBeat()
        render(0.125) // half of a 0.25 s beat
        engine.bpm = 60 // 1 s beats: the other half now lasts 0.5 s
        assertEquals(0.5, secondsToNextBeat(), 0.02)
    }
}
