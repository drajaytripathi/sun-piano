package com.example.sunpiano

import com.example.sunpiano.data.MidiFileWriter
import com.example.sunpiano.data.NoteEvent
import com.example.sunpiano.data.Recording
import com.example.sunpiano.music.KeyboardLayout
import com.example.sunpiano.music.Notes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import android.view.KeyEvent
import java.io.ByteArrayOutputStream

class PianoTest {
    @Test fun frequencies() {
        assertEquals(440.0, Notes.frequency(69), 1e-9)
        assertEquals(261.6256, Notes.frequency(60), 1e-3)
    }

    @Test fun names() {
        assertEquals("C4", Notes.name(60))
        assertEquals("A0", Notes.name(21))
        assertEquals("F♯3", Notes.name(54))
        assertEquals("Sol", Notes.name(67, solfege = true, withOctave = false))
        assertTrue(Notes.isBlack(61))
        assertFalse(Notes.isBlack(60))
    }

    @Test fun layoutHas52WhiteKeys() {
        val l = KeyboardLayout()
        assertEquals(52, l.whiteCount)
        assertEquals(21, l.whiteNotes.first())
        assertEquals(108, l.whiteNotes.last())
    }

    @Test fun hitTest() {
        val l = KeyboardLayout()
        val w = 100f
        val h = 400f
        val c4 = l.whiteIndexOf(60)
        // lower half of C4 -> white key
        assertEquals(60, l.hitTest(c4 * w + 20f, 350f, w, h)!!.note)
        // top of boundary between C4 and D4 -> C#4
        assertEquals(61, l.hitTest((c4 + 1) * w, 50f, w, h)!!.note)
        // same x but low on the key -> white
        assertEquals(60, l.hitTest((c4 + 1) * w - 5f, 350f, w, h)!!.note)
        // between E and F there is no black key
        val e4 = l.whiteIndexOf(64)
        assertEquals(64, l.hitTest((e4 + 1) * w - 2f, 50f, w, h)!!.note)
        assertNull(l.hitTest(-1f, 10f, w, h))
        assertNull(l.hitTest(1e6f, 10f, w, h))
    }

    @Test fun recordingJsonRoundTrip() {
        val r = Recording("id1", "Test", 123L, 900L, listOf(NoteEvent(0, 60, 90), NoteEvent(400, 60, 0), NoteEvent(500, NoteEvent.PEDAL, 127)))
        assertEquals(r, Recording.fromJson(r.toJson()))
    }

    @Test fun vlq() {
        fun enc(v: Long) = ByteArrayOutputStream().also { MidiFileWriter.writeVlq(it, v) }.toByteArray()
        assertArrayEquals(byteArrayOf(0), enc(0))
        assertArrayEquals(byteArrayOf(0x7F), enc(127))
        assertArrayEquals(byteArrayOf(0x81.toByte(), 0x00), enc(128))
        assertArrayEquals(byteArrayOf(0xC0.toByte(), 0x00), enc(8192))
    }

    @Test fun midiFileStructure() {
        val r = Recording("i", "n", 0, 500, listOf(NoteEvent(0, 60, 100), NoteEvent(500, 60, 0)))
        val b = MidiFileWriter.write(r)
        assertEquals("MThd", String(b, 0, 4, Charsets.US_ASCII))
        assertEquals("MTrk", String(b, 14, 4, Charsets.US_ASCII))
        val len = ((b[18].toInt() and 0xFF) shl 24) or ((b[19].toInt() and 0xFF) shl 16) or ((b[20].toInt() and 0xFF) shl 8) or (b[21].toInt() and 0xFF)
        assertEquals(b.size - 22, len)
        // ends with end-of-track meta
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0x2F, 0), b.copyOfRange(b.size - 3, b.size))
    }

    @Test fun computerKeyboardMappingUsesScanCodes() {
        // scan codes from Android's Generic.kl; the layout's character map is irrelevant
        assertEquals(48, PianoViewModel.keyForPhysicalKey(44, 0)) // Z -> C3
        assertEquals(64, PianoViewModel.keyForPhysicalKey(53, 0)) // / -> E4
        assertEquals(60, PianoViewModel.keyForPhysicalKey(16, 0)) // Q -> C4
        assertEquals(61, PianoViewModel.keyForPhysicalKey(3, 0))  // 2 -> C#4
        assertEquals(79, PianoViewModel.keyForPhysicalKey(27, 0)) // ] -> G5
        assertNull(PianoViewModel.keyForPhysicalKey(30, 0))       // A is unmapped
        // a scan code wins over a (possibly remapped) key code
        assertEquals(48, PianoViewModel.keyForPhysicalKey(44, KeyEvent.KEYCODE_W))
    }

    @Test fun computerKeyboardFallsBackToKeyCodes() {
        assertEquals(48, PianoViewModel.keyForPhysicalKey(0, KeyEvent.KEYCODE_Z))
        assertEquals(79, PianoViewModel.keyForPhysicalKey(0, KeyEvent.KEYCODE_RIGHT_BRACKET))
        assertNull(PianoViewModel.keyForPhysicalKey(0, KeyEvent.KEYCODE_A))
    }

    @Test fun everyMappedKeyPlaysADistinctNoteWithinARow() {
        val lower = listOf(44, 31, 45, 32, 46, 47, 34, 48, 35, 49, 36, 50, 51, 38, 52, 39, 53)
        assertEquals((48..64).toList(), lower.map { PianoViewModel.keyForPhysicalKey(it, 0) })
        val upper = listOf(16, 3, 17, 4, 18, 19, 6, 20, 7, 21, 8, 22, 23, 10, 24, 11, 25, 26, 13, 27)
        assertEquals((60..79).toList(), upper.map { PianoViewModel.keyForPhysicalKey(it, 0) })
    }
}
