package com.example.sunpiano

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sunpiano.audio.Instrument
import com.example.sunpiano.audio.Instruments
import com.example.sunpiano.audio.SynthEngine
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Picking an instrument must not block the main thread while its wavetables are built (bug.md #23). */
@RunWith(AndroidJUnit4::class)
class InstrumentSwitchTest {
    /** The instrument new notes use (read through reflection, so this also runs on older code). */
    private fun current(engine: SynthEngine): Instrument {
        val bank = SynthEngine::class.java.getDeclaredField("bank").apply { isAccessible = true }.get(engine)
        return bank.javaClass.getDeclaredField("instrument").apply { isAccessible = true }.get(bank) as Instrument
    }

    private fun waitForInstrument(engine: SynthEngine, ins: Instrument) {
        val deadline = SystemClock.uptimeMillis() + 3000
        while (current(engine) !== ins) {
            check(SystemClock.uptimeMillis() < deadline) { "${ins.name} never took over (still ${current(engine).name})" }
            SystemClock.sleep(5)
        }
    }

    @Test
    fun pickingAnyInstrumentOnAFreshEngineIsInstant() {
        for (ins in Instruments.ALL.reversed()) { // the slowest (Synth Lead, Strings) first
            val engine = SynthEngine()
            val start = System.nanoTime()
            engine.setInstrument(ins)
            val ms = (System.nanoTime() - start) / 1e6
            Log.i("InstrumentSwitchTest", "${ins.name}: setInstrument took ${"%.2f".format(ms)} ms")
            assertTrue("choosing ${ins.name} blocked for $ms ms", ms < 4.0)
            waitForInstrument(engine, ins)
        }
    }

    @Test
    fun aQuickSecondChoiceWins() {
        val engine = SynthEngine()
        engine.setInstrument(Instruments.LEAD)    // slow to build
        engine.setInstrument(Instruments.MARIMBA) // picked before Lead is ready
        waitForInstrument(engine, Instruments.MARIMBA)
        SystemClock.sleep(300) // Lead's build finishes meanwhile
        assertSame("a late build undid the newer choice", Instruments.MARIMBA, current(engine))
    }
}
