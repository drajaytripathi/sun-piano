package com.example.sunpiano

import com.example.sunpiano.music.NoteHolders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A note shared by several holders stops only when the last one lets go (bug.md #8). */
class NoteHoldersTest {
    private val h = NoteHolders()

    @Test fun twoFingersOnOneKey() {
        h.press(60, 60)
        h.press(60, 60)
        assertNull("first finger lifted: still held by the second", h.release(60))
        assertEquals(setOf(60), h.keys)
        assertEquals(60, h.release(60))
        assertEquals(emptySet<Int>(), h.keys)
    }

    @Test fun twoKeysSoundingTheSameNoteAfterATransposeChange() {
        h.press(60, 62) // C4 pressed with transpose +2
        h.press(62, 62) // D4 pressed with transpose 0
        assertNull(h.release(60))
        assertEquals(62, h.release(62))
    }

    @Test fun playbackAndLiveShareANote() {
        h.press(60, 60)
        h.playbackPress(60)
        assertNull("playback ended but you still hold it", h.playbackRelease(60))
        assertEquals(60, h.release(60))
    }

    @Test fun liveReleaseDoesNotCutPlayback() {
        h.playbackPress(64)
        h.press(64, 64)
        assertNull(h.release(64))
        assertEquals(64, h.playbackRelease(64))
    }

    @Test fun playbackIsOneHolderHoweverOftenItReStrikes() {
        h.playbackPress(60)
        h.playbackPress(60)
        assertEquals(60, h.playbackRelease(60))
        assertNull(h.playbackRelease(60))
    }

    @Test fun releasingAKeyThatIsNotHeldDoesNothing() {
        h.press(60, 60)
        assertNull(h.release(61))
        assertEquals(60, h.release(60))
        assertNull(h.release(60))
    }

    @Test fun liveSoundingListsWhatLiveInputHolds() {
        h.press(60, 61)
        h.press(64, 64)
        h.playbackPress(67)
        assertEquals(setOf(61, 64), h.liveSounding())
        assertEquals(setOf(67), h.playbackNotes)
    }

    @Test fun isHeldWhileAnyHolderHoldsTheNote() {
        assertEquals(false, h.isHeld(60))
        h.press(60, 60)
        h.playbackPress(60)
        assertEquals(true, h.isHeld(60))
        h.release(60)
        assertEquals(true, h.isHeld(60))
        h.playbackRelease(60)
        assertEquals(false, h.isHeld(60))
    }
}
