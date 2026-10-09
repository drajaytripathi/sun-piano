package com.example.sunpiano

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sunpiano.audio.Instruments
import com.example.sunpiano.audio.SynthEngine
import com.example.sunpiano.music.Notes
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The FM oscillator must not crash or alias on the highest notes (bug.md #13). */
@RunWith(AndroidJUnit4::class)
class FmModulatorTest {
    /** Renders [seconds] of [note] on Marimba (FM, modulator at 4x the note) through the real engine; returns the left channel. */
    private fun renderMarimba(note: Int, seconds: Double): Pair<FloatArray, Int> {
        val engine = SynthEngine()
        engine.setInstrument(Instruments.MARIMBA)
        engine.reverbLevel = 0f
        engine.noteOn(note, 100)
        val blocks = (seconds * engine.sampleRate / SynthEngine.BLOCK).toInt()
        val left = FloatArray(blocks * SynthEngine.BLOCK)
        val block = FloatArray(SynthEngine.BLOCK * 2)
        for (b in 0 until blocks) {
            engine.renderBlock(block) // straight on this thread: an exception fails the test
            for (i in 0 until SynthEngine.BLOCK) left[b * SynthEngine.BLOCK + i] = block[2 * i]
        }
        return left to engine.sampleRate
    }

    /** Share of the signal's energy NOT explained by a sine at [freq] (least-squares fit). */
    private fun nonSineShare(x: FloatArray, from: Int, n: Int, freq: Double, rate: Int): Double {
        var s2 = 0.0; var c2 = 0.0; var sc = 0.0; var xs = 0.0; var xc = 0.0; var xx = 0.0
        for (k in from until from + n) {
            val w = 2 * PI * freq * k / rate
            val s = sin(w); val c = cos(w); val v = x[k].toDouble()
            s2 += s * s; c2 += c * c; sc += s * c; xs += v * s; xc += v * c; xx += v * v
        }
        val det = s2 * c2 - sc * sc
        val a = (xs * c2 - xc * sc) / det
        val b = (xc * s2 - xs * sc) / det
        var r = 0.0
        for (k in from until from + n) {
            val w = 2 * PI * freq * k / rate
            val e = x[k] - a * sin(w) - b * cos(w)
            r += e * e
        }
        return r / xx
    }

    @Test
    fun theTopNoteDoesNotCrashTheAudioThread() {
        val (left, _) = renderMarimba(127, 0.5) // modulator above the sample rate at 48 kHz
        assertTrue(left.all { it.isFinite() })
    }

    @Test
    fun theHighestKeyboardNoteHasNoAliasedTone() {
        val note = 120 // top key (C8) transposed up an octave
        val (left, rate) = renderMarimba(note, 0.1)
        val share = nonSineShare(left, rate / 50, 1024, Notes.frequency(note), rate)
        Log.i("FmModulatorTest", "note $note: ${"%.1f".format(share * 100)}% of the energy is not the note's own tone")
        assertTrue("aliased tone: ${share * 100}% of the energy is off-pitch", share < 0.02)
    }

    @Test
    fun fmStillShapesNormalNotes() {
        val note = 72
        val (left, rate) = renderMarimba(note, 0.1)
        val share = nonSineShare(left, rate / 100, 1024, Notes.frequency(note), rate)
        Log.i("FmModulatorTest", "note $note: ${"%.1f".format(share * 100)}% of the energy is FM overtones")
        assertTrue("FM overtones missing on a normal note", share > 0.05)
    }
}
