package com.example.sunpiano

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sunpiano.data.Settings
import com.example.sunpiano.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/** Fixes checked through the real view model: bug.md #8 (shared notes) and #17 (settings writes). */
@RunWith(AndroidJUnit4::class)
class ViewModelFixesTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val repo = SettingsRepository(app)
    private lateinit var vm: PianoViewModel
    private lateinit var original: Settings

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)

    @Before
    fun setUp() {
        original = runBlocking { repo.flow.first() }
        onMain { vm = PianoViewModel(app) }
        Thread.sleep(500) // let the view model load the saved settings
    }

    @After
    fun tearDown() {
        Thread.sleep(700) // let any pending debounced write land first
        runBlocking { repo.save(original) }
    }

    @Test
    fun sharedNoteStaysLitUntilItsLastHolderReleasesIt() {
        onMain { vm.noteOn(60); vm.noteOn(60) } // two fingers on middle C
        onMain { vm.noteOff(60) }
        assertTrue("released by the first finger although the second still holds it", 60 in vm.pressed.value)
        onMain { vm.noteOff(60) }
        assertFalse(60 in vm.pressed.value)
    }

    @Test
    fun draggingASliderWritesTheSettingsOnce() = runBlocking {
        val writes = Collections.synchronizedList(ArrayList<Float>())
        val watcher = launch(Dispatchers.IO) { repo.flow.drop(1).collect { writes += it.volume } }
        delay(300)
        repeat(30) { i ->
            onMain { vm.updateSettings { it.copy(volume = 0.30f + i * 0.01f) } }
            Thread.sleep(16) // ~60 slider updates a second
        }
        delay(1500)
        watcher.cancel()
        assertEquals("number of settings writes for one drag: $writes", 1, writes.size)
        assertEquals(0.59f, writes.single(), 0.001f)
    }

    @Test
    fun aChangeJustBeforeLeavingTheAppIsSavedRightAway() = runBlocking {
        onMain {
            vm.updateSettings { it.copy(volume = 0.42f) }
            vm.onBackground()
        }
        delay(200) // well under the 500 ms debounce
        assertEquals(0.42f, repo.flow.first().volume, 0.0001f)
    }
}
