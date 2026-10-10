package code.alimiracle.sunpiano

import androidx.test.ext.junit.runners.AndroidJUnit4
import code.alimiracle.sunpiano.audio.SynthEngine
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Changing beats-per-bar while the metronome runs must keep the count in range (bug.md #10). */
@RunWith(AndroidJUnit4::class)
class MetronomeBarChangeTest {
    private val engine = SynthEngine().apply {
        bpm = 300
        metronomeOn = true
    }
    private val beats = ArrayList<Int>()
    private val block = FloatArray(SynthEngine.BLOCK * 2)

    init {
        engine.onBeat = { beats += it }
    }

    /** Renders on this thread until [count] more beats have clicked. */
    private fun renderBeats(count: Int) {
        val target = beats.size + count
        var guard = 0
        while (beats.size < target) {
            engine.renderBlock(block)
            check(++guard < 100_000) { "metronome stopped clicking" }
        }
    }

    @Test
    fun shrinkingTheBarStartsANewBarInsteadOfAnOutOfRangeBeat() {
        engine.beatsPerBar = 4
        renderBeats(3)                      // beats 1, 2, 3 of 4/4
        engine.beatsPerBar = 3              // switch while beat 3 sounds
        renderBeats(4)
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 0), beats)
    }

    @Test
    fun growingTheBarContinuesTheCount() {
        engine.beatsPerBar = 3
        renderBeats(3)                      // a full bar of 3/4
        renderBeats(2)                      // beats 1, 2 of the next bar
        engine.beatsPerBar = 4
        renderBeats(4)
        assertEquals(listOf(0, 1, 2, 0, 1, 2, 3, 0, 1), beats)
    }
}
