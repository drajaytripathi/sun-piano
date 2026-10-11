package code.alimiracle.sunpiano

import code.alimiracle.sunpiano.data.NoteEvent
import code.alimiracle.sunpiano.data.Recording
import code.alimiracle.sunpiano.data.Settings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A recording keeps every note's instrument and all settings; older recordings still load. */
class RecordingInstrumentTest {
    private val events = listOf(NoteEvent(0, 60, 100), NoteEvent(500, 60, 0))

    @Test
    fun everyNotesInstrumentAndAllSettingsAreSavedAndLoaded() {
        val settings = Settings(
            instrumentId = "organ", volume = 0.5f, reverb = 0.6f, transpose = -3, tuning = 432,
            visibleKeys = 21, showLabels = false, solfege = true, velocityFromTouch = false, glissando = false,
            keepScreenOn = false, bpm = 77, beatsPerBar = 3, metronomeVolume = 0.25f,
        )
        val switching = listOf(
            NoteEvent(0, 60, 100, "grand"), NoteEvent(400, 60, 0),
            NoteEvent(500, NoteEvent.PEDAL, 127),
            NoteEvent(600, 64, 90, "lead"), NoteEvent(900, 64, 0),
            NoteEvent(950, NoteEvent.PEDAL, 0),
        )
        val rec = Recording("id1", "Tune", 1L, 950L, switching, settings = settings)
        val loaded = Recording.fromJson(rec.toJson())
        assertEquals(rec, loaded)
        assertEquals(settings, loaded.settings)
        assertEquals(listOf("grand", "lead"), loaded.instrumentIds)
        assertEquals("lead", loaded.instrumentFor(switching[3]))
    }

    @Test
    fun settingsAreWrittenAsShown() { // 0.8, not 0.800000011920929
        val json = Settings(volume = 0.8f, reverb = 0.25f, metronomeVolume = 0.6f).toJson().toString()
        for (part in listOf("\"volume\":0.8", "\"reverb\":0.25", "\"metronomeVolume\":0.6")) {
            assertEquals("$part in $json", true, Regex(Regex.escape(part) + "[,}]").containsMatchIn(json)) // keys in any order
        }
    }

    @Test
    fun settingsAddedLaterGetTheirDefaults() {
        val partial = JSONObject().put("instrument", "strings").put("tuning", 415)
        assertEquals(Settings(instrumentId = "strings", tuning = 415), Settings.fromJson(partial))
    }

    @Test
    fun aRecordingFromBeforeInstrumentsWereStoredStillLoads() {
        val old = """{"id":"id2","name":"Old","createdAt":1,"durationMs":500,"events":[[0,60,100],[500,60,0]]}"""
        val rec = Recording.fromJson(old)
        assertNull(rec.settings)
        assertEquals(emptyList<String>(), rec.instrumentIds)
        assertNull(rec.instrumentFor(rec.events[0]))
        assertEquals(events, rec.events)
    }

    @Test
    fun aRecordingWithOneInstrumentForAllNotesStillLoads() { // the format of 766cdfc
        val old = """{"id":"id3","name":"Lead","createdAt":1,"durationMs":500,"instrument":"lead","events":[[0,60,100],[500,60,0]]}"""
        val rec = Recording.fromJson(old)
        assertEquals("lead", rec.instrumentFor(rec.events[0]))
        assertEquals(listOf("lead"), rec.instrumentIds)
    }
}
