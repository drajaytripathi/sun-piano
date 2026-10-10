package code.alimiracle.sunpiano

import android.view.KeyEvent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Releasing a key while Ctrl, Alt or Meta is held must still end its note or sustain (bug.md #18). */
@RunWith(AndroidJUnit4::class)
class ModifierReleaseTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun key(code: Int, scan: Int, action: Int, meta: Int = 0) = KeyEvent(0, 0, action, code, 0, meta, -1, scan)

    private fun check(meta: Int, name: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
            scenario.onActivity {
                it.dispatchKeyEvent(key(KeyEvent.KEYCODE_Z, 44, KeyEvent.ACTION_DOWN))
                it.dispatchKeyEvent(key(KeyEvent.KEYCODE_SPACE, 57, KeyEvent.ACTION_DOWN))
            }
            instrumentation.runOnMainSync { assertTrue(48 in vm.pressed.value && vm.sustain.value) }
            scenario.onActivity {
                it.dispatchKeyEvent(key(KeyEvent.KEYCODE_Z, 44, KeyEvent.ACTION_UP, meta))
                it.dispatchKeyEvent(key(KeyEvent.KEYCODE_SPACE, 57, KeyEvent.ACTION_UP, meta))
            }
            instrumentation.runOnMainSync {
                assertFalse("C3 stuck after releasing Z with $name held", 48 in vm.pressed.value)
                assertFalse("sustain stuck after releasing Space with $name held", vm.sustain.value)
            }
        }
    }

    @Test fun ctrl() = check(KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON, "Ctrl")
    @Test fun alt() = check(KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON, "Alt")
    @Test fun meta() = check(KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON, "Meta")

    @Test
    fun ctrlShortcutsStillReachAndroid() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
            val ctrl = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
            scenario.onActivity { it.dispatchKeyEvent(key(KeyEvent.KEYCODE_Z, 44, KeyEvent.ACTION_DOWN, ctrl)) }
            instrumentation.runOnMainSync { assertFalse("Ctrl+Z played a note", 48 in vm.pressed.value) }
            scenario.onActivity { it.dispatchKeyEvent(key(KeyEvent.KEYCODE_Z, 44, KeyEvent.ACTION_UP, ctrl)) }
        }
    }
}
