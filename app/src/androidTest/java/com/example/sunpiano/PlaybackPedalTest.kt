package com.example.sunpiano

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sunpiano.audio.SynthEngine
import com.example.sunpiano.data.NoteEvent
import com.example.sunpiano.data.Recording
import com.example.sunpiano.music.SustainSources
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A playing recording's pedal must not override the live pedal (bug.md #19). */
@RunWith(AndroidJUnit4::class)
class PlaybackPedalTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** The pedal state the view model last sent to its engine. */
    private fun enginePedal(vm: PianoViewModel): Boolean {
        val engine = PianoViewModel::class.java.getDeclaredField("engine").apply { isAccessible = true }.get(vm)
        return SynthEngine::class.java.getDeclaredField("sustainRequested").apply { isAccessible = true }.get(engine) as Boolean
    }

    private fun recording(vararg events: NoteEvent) = Recording("pedal-test", "pedal test", 0, events.maxOf { it.timeMs }, events.toList())

    private fun withVm(block: (PianoViewModel) -> Unit) = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        lateinit var vm: PianoViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
        block(vm)
    }

    @Test
    fun aRecordingLiftingThePedalDoesNotLiftTheLatchedButton() = withVm { vm ->
        instrumentation.runOnMainSync {
            vm.setSustain(SustainSources.Source.BUTTON, true)
            vm.togglePlayback(recording(NoteEvent(0, NoteEvent.PEDAL, 127), NoteEvent(100, 72, 90), NoteEvent(200, 72, 0), NoteEvent(300, NoteEvent.PEDAL, 0)))
        }
        SystemClock.sleep(500) // after the recording's pedal-up
        instrumentation.runOnMainSync { assertTrue("the recording lifted the latched pedal", enginePedal(vm)) }
        SystemClock.sleep(600) // playback finished
        instrumentation.runOnMainSync { assertTrue(enginePedal(vm)) }
    }

    @Test
    fun theRecordingsPedalWorksAndIsLiftedWhenPlaybackStops() = withVm { vm ->
        instrumentation.runOnMainSync {
            vm.togglePlayback(recording(NoteEvent(0, NoteEvent.PEDAL, 127), NoteEvent(100, 72, 90), NoteEvent(5000, 72, 0)))
        }
        SystemClock.sleep(300)
        instrumentation.runOnMainSync { assertTrue("the recording's pedal didn't go down", enginePedal(vm)) }
        instrumentation.runOnMainSync { vm.stopPlayback() }
        SystemClock.sleep(200)
        instrumentation.runOnMainSync {
            assertFalse("pedal left down after stopping playback", enginePedal(vm))
            assertFalse(vm.sustainLatched.value)
        }
    }
}
