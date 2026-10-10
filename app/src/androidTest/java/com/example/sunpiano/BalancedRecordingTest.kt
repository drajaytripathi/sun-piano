package com.example.sunpiano

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sunpiano.data.MidiFileWriter
import com.example.sunpiano.data.RecordingStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Every note-on in a recording, and in its exported .mid, must have its own note-off (bug.md #21). */
@RunWith(AndroidJUnit4::class)
class BalancedRecordingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application

    @Test
    fun aNoteHeldByTwoFingersIsRecordedAsTwoCompleteNotes() {
        val store = RecordingStore(app)
        val before = runBlocking { store.list() }.map { it.id }.toSet()
        lateinit var vm: PianoViewModel
        instrumentation.runOnMainSync { vm = PianoViewModel(app) }
        Thread.sleep(300)
        instrumentation.runOnMainSync {
            vm.toggleRecording()
            vm.noteOn(60) // first finger
        }
        Thread.sleep(50)
        instrumentation.runOnMainSync { vm.noteOn(60) } // second finger on the same key
        Thread.sleep(50)
        instrumentation.runOnMainSync { vm.noteOff(60); vm.noteOff(60); vm.toggleRecording() }
        Thread.sleep(500) // saved in the background

        val rec = runBlocking { store.list() }.single { it.id !in before }
        try {
            val ons = rec.events.count { it.note == 60 && it.velocity > 0 }
            val offs = rec.events.count { it.note == 60 && it.velocity == 0 }
            assertEquals("note-ons vs note-offs in ${rec.events}", ons, offs)
            assertEquals(2, ons)

            // the exported file: count 0x90 (note on, velocity > 0) and 0x80 events for note 60
            val midi = MidiFileWriter.write(rec)
            var on = 0; var off = 0
            for (i in 0 until midi.size - 2) {
                val status = midi[i].toInt() and 0xFF
                if (midi[i + 1].toInt() != 60) continue
                if (status == 0x90 && midi[i + 2] > 0) on++
                if (status == 0x80) off++
            }
            assertEquals("unbalanced .mid", on, off)
        } finally {
            runBlocking { store.delete(rec.id) }
        }
    }
}
