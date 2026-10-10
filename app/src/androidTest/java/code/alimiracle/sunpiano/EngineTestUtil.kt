package code.alimiracle.sunpiano

import android.os.SystemClock
import code.alimiracle.sunpiano.audio.Instrument
import code.alimiracle.sunpiano.audio.SynthEngine

/** Sets [instrument] and waits until its wavetables are built, so the next note uses it. */
fun SynthEngine.useInstrument(instrument: Instrument) {
    setInstrument(instrument)
    val deadline = SystemClock.uptimeMillis() + 3000
    while (currentInstrument !== instrument) {
        check(SystemClock.uptimeMillis() < deadline) { "${instrument.name} never became current" }
        SystemClock.sleep(2)
    }
}
