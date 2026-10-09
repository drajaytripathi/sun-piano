package com.example.sunpiano

import com.example.sunpiano.midi.MidiParser
import org.junit.Assert.assertEquals
import org.junit.Test

class MidiParserTest {
    private val log = ArrayList<String>()
    private val parser = MidiParser(
        onNoteOn = { n, v -> log += "on $n $v" },
        onNoteOff = { n -> log += "off $n" },
        onSustain = { down -> log += if (down) "pedal down" else "pedal up" },
    )

    private fun bytes(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }

    @Test fun parsesNotesPedalAndRunningStatus() {
        // note on, running-status note on, velocity-0 note on (= off), note off, pedal, realtime clock byte
        parser.parse(bytes(0x90, 60, 100, 64, 90, 0x90, 64, 0, 0x80, 60, 0, 0xB0, 64, 127, 0xF8, 0xB0, 64, 0))
        assertEquals(listOf("on 60 100", "on 64 90", "off 64", "off 60", "pedal down", "pedal up"), log)
    }

    @Test fun releaseAllEndsWhatTheDeviceStillHolds() {
        // device unplugged while holding 60 and 67 (64 already released) with the pedal down
        parser.parse(bytes(0x90, 60, 100, 0x90, 64, 90, 0x90, 67, 80, 0x80, 64, 0, 0xB0, 64, 127))
        log.clear()
        parser.releaseAll()
        assertEquals(listOf("off 60", "off 67", "pedal up"), log)
        log.clear()
        parser.releaseAll() // nothing left to release
        assertEquals(emptyList<String>(), log)
    }

    @Test fun releaseAllDoesNothingWhenNothingIsHeld() {
        parser.parse(bytes(0x90, 60, 100, 0x80, 60, 0, 0xB0, 64, 127, 0xB0, 64, 0))
        log.clear()
        parser.releaseAll()
        assertEquals(emptyList<String>(), log)
    }
}
