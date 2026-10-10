package code.alimiracle.sunpiano

import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import code.alimiracle.sunpiano.data.Recording
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A configuration change must not stop recording, playback or the metronome (bug.md #2). */
@RunWith(AndroidJUnit4::class)
class ConfigChangeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun manifestHandlesKeyboardDarkModeLanguageAndFontSize() {
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val needed = mapOf(
            "keyboard" to ActivityInfo.CONFIG_KEYBOARD,
            "navigation" to ActivityInfo.CONFIG_NAVIGATION,
            "uiMode" to ActivityInfo.CONFIG_UI_MODE,
            "locale" to ActivityInfo.CONFIG_LOCALE,
            "layoutDirection" to ActivityInfo.CONFIG_LAYOUT_DIRECTION,
            "fontScale" to ActivityInfo.CONFIG_FONT_SCALE,
            "density" to ActivityInfo.CONFIG_DENSITY,
        )
        for ((name, flag) in needed) assertTrue("configChanges lacks $name", info.configChanges and flag != 0)
    }

    @Test
    fun recreatingTheActivityKeepsRecordingAndMetronomeGoing() {
        val startedAt = System.currentTimeMillis()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[PianoViewModel::class.java]
                vm.setMetronome(true)
                vm.toggleRecording()
                vm.noteOn(60, 90)
            }

            scenario.recreate() // what Android does for a configuration change it must restart for

            scenario.onActivity { activity ->
                assertSame("view model replaced", vm, ViewModelProvider(activity)[PianoViewModel::class.java])
                assertTrue("recording stopped", vm.isRecording.value)
                assertTrue("metronome stopped", vm.metronomeOn.value)
                vm.noteOff(60)
                vm.toggleRecording()
                vm.setMetronome(false)
            }

            // the recording, spanning the recreation, is saved with its note
            var saved: Recording? = null
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (saved == null && SystemClock.elapsedRealtime() < deadline) {
                saved = vm.recordings.value.firstOrNull { r -> r.createdAt >= startedAt && r.events.any { it.note == 60 && it.velocity > 0 } }
                if (saved == null) Thread.sleep(50)
            }
            assertNotNull("recording not saved", saved)
            val noteOff = saved!!.events.firstOrNull { it.note == 60 && it.velocity == 0 }
            assertNotNull("note-off after the recreation not recorded", noteOff)
            scenario.onActivity { vm.deleteRecording(saved) } // don't leave test data in the library
        }
    }
}
