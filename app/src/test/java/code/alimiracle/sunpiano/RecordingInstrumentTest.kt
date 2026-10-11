package code.alimiracle.sunpiano

import code.alimiracle.sunpiano.data.NoteEvent
import code.alimiracle.sunpiano.data.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A recording keeps the instrument it was played with; older recordings have none. */
class RecordingInstrumentTest {
    private val events = listOf(NoteEvent(0, 60, 100), NoteEvent(500, 60, 0))

    @Test
    fun theInstrumentIsSavedAndLoaded() {
        val rec = Recording("id1", "Tune", 1L, 500L, events, instrumentId = "lead")
        assertEquals(rec, Recording.fromJson(rec.toJson()))
    }

    @Test
    fun aRecordingFromBeforeInstrumentsWereStoredStillLoads() {
        val old = """{"id":"id2","name":"Old","createdAt":1,"durationMs":500,"events":[[0,60,100],[500,60,0]]}"""
        val rec = Recording.fromJson(old)
        assertNull(rec.instrumentId)
        assertEquals(events, rec.events)
    }
}
