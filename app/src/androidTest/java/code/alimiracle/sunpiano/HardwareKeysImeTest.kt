package code.alimiracle.sunpiano

import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * An input method such as SwiftKey gets a hardware keyboard's keys before the app and turned letters
 * and Space into typed text, so a Bluetooth keyboard played nothing on an Honor tablet. The piano's
 * window keeps the input method out, so the keys come straight to it.
 */
@RunWith(AndroidJUnit4::class)
class HardwareKeysImeTest {
    @Test
    fun thePianoWindowDoesNotUseTheInputMethod() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertTrue("the input method would get the keys first", flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM != 0)
                assertTrue(
                    "window can't take keyboard focus",
                    !WindowManager.LayoutParams.mayUseInputMethod(flags) && flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0,
                )
            }
        }
    }
}
