package com.example.sunpiano

import android.content.Context
import android.media.midi.MidiManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sunpiano.midi.MidiInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/** A MIDI device that finishes opening after input was stopped must not be kept (bug.md #24). */
@RunWith(AndroidJUnit4::class)
class MidiOpenAfterStopTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun aDeviceOpeningWhenTheAppIsLeftIsClosed() {
        val manager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
        assertTrue("the test MIDI keyboard isn't installed", manager.devices.any { it.outputPortCount > 0 })
        val reported = Collections.synchronizedList(ArrayList<String?>())
        lateinit var input: MidiInput
        instrumentation.runOnMainSync {
            input = MidiInput(context, { _, _ -> }, {}, {}, onDeviceChanged = { name, _ -> reported += name })
            input.start() // starts opening the device; the answer comes later on this thread
            input.stop()  // the app is left before it arrives
        }
        Thread.sleep(1500)
        instrumentation.runOnMainSync {}
        assertEquals("device attached after input was stopped: $reported", null, reported.lastOrNull())

        // input started normally still opens it
        instrumentation.runOnMainSync { input.start() }
        Thread.sleep(1500)
        assertTrue("device not opened: $reported", reported.lastOrNull() != null)
        instrumentation.runOnMainSync { input.stop() }
    }
}
