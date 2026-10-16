package code.alimiracle.sunpiano

import code.alimiracle.sunpiano.data.GeneralMidi
import code.alimiracle.sunpiano.data.MidiFileReader
import code.alimiracle.sunpiano.data.MidiFileWriter
import code.alimiracle.sunpiano.data.NoteEvent
import code.alimiracle.sunpiano.data.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Importing MIDI files: the app's own exports and files from other programs. */
class MidiImportTest {
    /** A MIDI file with [division] ticks per quarter note and the given track bodies (without end-of-track). */
    private fun smf(format: Int, division: Int, vararg tracks: IntArray): ByteArray {
        val out = ByteArrayOutputStream()
        fun u32(v: Int) { out.write(v ushr 24); out.write(v ushr 16); out.write(v ushr 8); out.write(v) }
        out.write("MThd".toByteArray()); u32(6)
        out.write(byteArrayOf(0, format.toByte(), 0, tracks.size.toByte(), (division shr 8).toByte(), division.toByte()))
        for (t in tracks) {
            val body = t + intArrayOf(0x00, 0xFF, 0x2F, 0x00)
            out.write("MTrk".toByteArray()); u32(body.size)
            body.forEach { out.write(it) }
        }
        return out.toByteArray()
    }

    private fun notes(events: List<NoteEvent>) = events.map { Triple(it.timeMs, it.note, it.velocity) }

    @Test
    fun theAppsOwnExportComesBackWithItsInstruments() {
        val rec = Recording(
            "id", "Tune", 0, 1500,
            listOf(
                NoteEvent(0, 60, 100, "grand"), NoteEvent(400, 60, 0),
                NoteEvent(500, NoteEvent.PEDAL, 127),
                NoteEvent(600, 64, 90, "lead"), NoteEvent(900, 64, 0),
                NoteEvent(1000, 67, 80, "organ"), NoteEvent(1400, 67, 0),
                NoteEvent(1500, NoteEvent.PEDAL, 0),
            ),
        )
        val back = MidiFileReader.read(MidiFileWriter.write(rec))
        assertEquals(rec.events, back.events)
        assertEquals(1500, back.durationMs)
    }

    @Test
    fun everyInstrumentSurvivesTheRoundTrip() {
        for (id in listOf("grand", "electric", "honky", "organ", "strings", "harpsichord", "marimba", "lead")) {
            assertEquals(id, GeneralMidi.instrumentFor(GeneralMidi.programFor(id)))
        }
    }

    @Test
    fun runningStatusAndVelocityZeroNoteOffs() {
        // 96 ticks per quarter at the default 120 bpm: 96 ticks = 500 ms
        val track = intArrayOf(
            0x00, 0x90, 60, 100,   // note on
            0x60, 64, 90,          // (running status) another note on, 96 ticks later
            0x60, 60, 0,           // velocity 0 = note off
            0x00, 64, 0,
        )
        val r = MidiFileReader.read(smf(0, 96, track))
        assertEquals(listOf(Triple(0L, 60, 100), Triple(500L, 64, 90), Triple(1000L, 60, 0), Triple(1000L, 64, 0)), notes(r.events))
        assertNull("no program change: the app's instrument", r.events[0].instrumentId)
    }

    @Test
    fun tempoChangesAreFollowed() {
        val track = intArrayOf(
            0x00, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20, // 500 000 µs per quarter (120 bpm)
            0x00, 0x90, 60, 100,
            0x60, 0xFF, 0x51, 0x03, 0x0F, 0x42, 0x40, // after one quarter (500 ms): 1 000 000 µs (60 bpm)
            0x00, 0x80, 60, 0,
            0x00, 0x90, 62, 100,
            0x60, 0x80, 62, 0,                       // one quarter at 60 bpm: 1000 ms
        )
        val r = MidiFileReader.read(smf(0, 96, track))
        assertEquals(listOf(Triple(0L, 60, 100), Triple(500L, 60, 0), Triple(500L, 62, 100), Triple(1500L, 62, 0)), notes(r.events))
    }

    @Test
    fun format1TracksAreMergedAndProgramsPickInstruments() {
        val conductor = intArrayOf(0x00, 0xFF, 0x03, 0x04, 'S'.code, 'o'.code, 'n'.code, 'g'.code)
        val strings = intArrayOf(0x00, 0xC0, 48, 0x00, 0x90, 55, 70, 0x81, 0x40, 0x80, 55, 0) // 192 ticks
        val lead = intArrayOf(0x00, 0xC1, 81, 0x60, 0x91, 72, 100, 0x60, 0x81, 72, 0)
        val drums = intArrayOf(0x00, 0x99, 36, 120, 0x10, 0x89, 36, 0)
        val r = MidiFileReader.read(smf(1, 96, conductor, strings, lead, drums))
        assertEquals("Song", r.trackName)
        assertEquals(listOf(Triple(0L, 55, 70), Triple(500L, 72, 100), Triple(1000L, 55, 0), Triple(1000L, 72, 0)), notes(r.events))
        assertEquals(listOf("strings", "lead"), r.events.filter { it.velocity > 0 }.map { it.instrumentId })
    }

    @Test
    fun theSustainPedalIsKept() {
        val track = intArrayOf(0x00, 0xB0, 64, 127, 0x00, 0x90, 60, 100, 0x60, 0x80, 60, 0, 0x60, 0xB0, 64, 0)
        val r = MidiFileReader.read(smf(0, 96, track))
        assertEquals(listOf(Triple(0L, NoteEvent.PEDAL, 127), Triple(0L, 60, 100), Triple(500L, 60, 0), Triple(1000L, NoteEvent.PEDAL, 0)), notes(r.events))
    }

    @Test
    fun overlappingCopiesOfANoteEndWithTheLastOne() { // two channels holding the same key
        val track = intArrayOf(0x00, 0x90, 60, 100, 0x00, 0x91, 60, 100, 0x60, 0x80, 60, 0, 0x60, 0x81, 60, 0)
        val r = MidiFileReader.read(smf(0, 96, track))
        assertEquals(listOf(Triple(0L, 60, 100), Triple(0L, 60, 100), Triple(1000L, 60, 0)), notes(r.events))
    }

    @Test
    fun aNoteNeverReleasedEndsWithTheFile() {
        val track = intArrayOf(0x00, 0x90, 60, 100, 0x60, 0x90, 64, 100, 0x60, 0x80, 64, 0)
        val r = MidiFileReader.read(smf(0, 96, track))
        assertEquals(Triple(1000L, 60, 0), notes(r.events).last())
    }

    @Test
    fun filesThatAreNotMidiOrHaveNoNotesAreRefused() {
        assertThrows(IOException::class.java) { MidiFileReader.read("RIFF....WAVEfmt ".toByteArray()) }
        assertThrows(IOException::class.java) { MidiFileReader.read(ByteArray(0)) }
        assertThrows(IOException::class.java) { MidiFileReader.read(smf(0, 96, intArrayOf(0x00, 0xFF, 0x03, 0x01, 'x'.code))) }
        assertThrows(IOException::class.java) { MidiFileReader.read(smf(0, 96, intArrayOf(0x00, 0x99, 36, 100, 0x10, 0x89, 36, 0))) } // drums only
        val cut = smf(0, 96, intArrayOf(0x00, 0x90, 60, 100, 0x60, 0x80, 60, 0))
        assertThrows(IOException::class.java) { MidiFileReader.read(cut.copyOf(cut.size - 9)) } // ends inside a note
    }

    @Test
    fun aTrackLengthWithTheTopBitSetIsRefused() { // read as negative, it moved the reader backwards and crashed
        val note = intArrayOf(0x00, 0x90, 60, 100, 0x60, 0x80, 60, 0)
        for (length in listOf(0xFFFFFF9C.toInt(), Int.MIN_VALUE)) {
            val file = smf(1, 96, note, note)
            for (i in 0 until 4) file[18 + i] = (length ushr (24 - 8 * i)).toByte() // the first track's length
            assertThrows(IOException::class.java) { MidiFileReader.read(file) }
        }
    }
}
