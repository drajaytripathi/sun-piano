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

    // ---- bug.md #5: nothing after a SysEx / system message may be lost ----

    @Test fun notesAfterSysexInTheSameBufferArrive() {
        parser.parse(bytes(0x90, 60, 100, 0xF0, 0x7E, 0x7F, 0x06, 0x01, 0xF7, 0x80, 60, 0))
        assertEquals(listOf("on 60 100", "off 60"), log)
    }

    @Test fun notesAfterSystemCommonMessagesArrive() {
        // MIDI time code quarter frame (1 data byte), song position (2 data bytes), tune request (none)
        parser.parse(bytes(0xF1, 0x10, 0x90, 62, 90, 0xF2, 0x00, 0x10, 0xF6, 0x80, 62, 0))
        assertEquals(listOf("on 62 90", "off 62"), log)
    }

    @Test fun sysexSpanningBuffersIsSkippedAndParsingContinues() {
        parser.parse(bytes(0xF0, 0x43, 0x10))
        parser.parse(bytes(0x4C, 0x00, 0xF7, 0x90, 64, 80))
        assertEquals(listOf("on 64 80"), log)
    }

    @Test fun runningStatusContinuesInTheNextBuffer() {
        parser.parse(bytes(0x90, 60, 100))
        parser.parse(bytes(62, 90))
        assertEquals(listOf("on 60 100", "on 62 90"), log)
    }

    @Test fun messageSplitAcrossBuffersArrives() {
        parser.parse(bytes(0x90, 60))
        parser.parse(bytes(100))
        assertEquals(listOf("on 60 100"), log)
    }

    @Test fun realtimeBytesInsideMessagesAreIgnored() {
        parser.parse(bytes(0x90, 0xF8, 60, 0xFE, 100))
        assertEquals(listOf("on 60 100"), log)
    }

    @Test fun systemCommonCancelsRunningStatus() {
        // per the MIDI spec, data bytes after a system common message have no status
        parser.parse(bytes(0x90, 60, 100, 0xF1, 0x00, 62, 90))
        assertEquals(listOf("on 60 100"), log)
    }

    @Test fun releaseAllForgetsAHalfReceivedMessage() {
        parser.parse(bytes(0x90, 60)) // device unplugged mid-message
        parser.releaseAll()
        parser.parse(bytes(100)) // a stray data byte from the next device must not complete it
        assertEquals(emptyList<String>(), log)
    }
}
