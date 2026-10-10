package com.example.sunpiano

import com.example.sunpiano.music.SustainSources
import com.example.sunpiano.music.SustainSources.Source
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pedal stays down while any source holds it (bug.md #9). */
class SustainSourcesTest {
    private val s = SustainSources()

    @Test fun releasingSpaceKeepsTheLatchedButton() {
        assertTrue(s.set(Source.BUTTON, true))
        assertTrue(s.set(Source.SPACE, true))
        assertTrue("Space released: the button still holds the pedal", s.set(Source.SPACE, false))
        assertTrue(s.isHeld(Source.BUTTON))
    }

    @Test fun unlatchingTheButtonKeepsAHeldMidiPedal() {
        s.set(Source.MIDI, true)
        s.set(Source.BUTTON, true)
        assertTrue(s.set(Source.BUTTON, false))
        assertFalse(s.set(Source.MIDI, false))
    }

    @Test fun releasingAnUnheldSourceChangesNothing() {
        assertFalse(s.set(Source.SPACE, false))
        s.set(Source.BUTTON, true)
        assertTrue(s.set(Source.MIDI, false))
    }
}
