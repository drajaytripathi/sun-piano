package code.alimiracle.sunpiano

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import code.alimiracle.sunpiano.data.NoteEvent
import code.alimiracle.sunpiano.data.Recording
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Playback stops when headphones are unplugged or another app takes the sound (bug.md #26). */
@RunWith(AndroidJUnit4::class)
class PlaybackInterruptTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val audio = instrumentation.targetContext.getSystemService(AudioManager::class.java)
    private val long = Recording("interrupt-test", "interrupt test", 0, 10_000, listOf(NoteEvent(0, 60, 90), NoteEvent(10_000, 60, 0)))

    private fun withPlayback(block: (PianoViewModel) -> Unit) = ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        lateinit var vm: PianoViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
        instrumentation.runOnMainSync { vm.togglePlayback(long) }
        SystemClock.sleep(300)
        instrumentation.runOnMainSync { assertEquals("playback didn't start", long.id, vm.playingId.value) }
        block(vm)
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use { it.readBytes().decodeToString() }

    @Test
    fun anotherAppTakingTheSoundStopsPlayback() = withPlayback { vm ->
        val other = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setOnAudioFocusChangeListener {}.build()
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(other))
        try {
            SystemClock.sleep(500)
            instrumentation.runOnMainSync { assertNull("still playing after losing audio focus", vm.playingId.value) }
        } finally {
            audio.abandonAudioFocusRequest(other)
        }
    }

    @Test
    fun unpluggingHeadphonesStopsPlaybackAndTheMetronome() = withPlayback { vm ->
        instrumentation.runOnMainSync { vm.setMetronome(true) }
        // the real system broadcast; only the system (root here, on the emulator) may send it
        shell("su 0 am broadcast -a android.media.AUDIO_BECOMING_NOISY")
        SystemClock.sleep(800)
        instrumentation.runOnMainSync {
            assertNull("still playing after headphones were unplugged", vm.playingId.value)
            assertFalse("metronome still on after headphones were unplugged", vm.metronomeOn.value)
        }
    }

    @Test
    fun playingLiveDoesNotTakeTheSoundFromOtherApps() {
        val other = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setOnAudioFocusChangeListener { lost -> lostFocus = lost }.build()
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, audio.requestAudioFocus(other))
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var vm: PianoViewModel
                scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
                instrumentation.runOnMainSync { vm.noteOn(60); vm.setMetronome(true) }
                SystemClock.sleep(500)
                instrumentation.runOnMainSync { vm.noteOff(60); vm.setMetronome(false) }
            }
            assertTrue("live playing took audio focus ($lostFocus)", lostFocus == null)
        } finally {
            audio.abandonAudioFocusRequest(other)
        }
    }

    @Volatile private var lostFocus: Int? = null
}
