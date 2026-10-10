package com.example.sunpiano

import android.accessibilityservice.AccessibilityServiceInfo
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Space must not turn off a latched Sustain button (bug.md #9). */
@RunWith(AndroidJUnit4::class)
class SustainSpaceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    /** Taps the on-screen Sustain button the way a finger would, through the accessibility tree. */
    // connected before the activity starts, so Compose builds its accessibility tree
    private val automation = instrumentation.uiAutomation.apply {
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    private fun tapSustainButton() {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (true) {
            val target = automation.windows.asSequence()
                .mapNotNull { it.root }
                .flatMap { it.descendants() }
                .firstOrNull { it.text?.toString() == "Sustain" }
                ?.let { node -> generateSequence(node) { it.parent }.firstOrNull { it.isClickable } }
            if (target != null) {
                target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                instrumentation.waitForIdleSync()
                return
            }
            check(SystemClock.uptimeMillis() < deadline) {
                "Sustain button not found in " + automation.windows.map { "${it.title}: ${it.root?.childCount}" }
            }
            Thread.sleep(100)
        }
    }

    private fun AccessibilityNodeInfo.descendants(): Sequence<AccessibilityNodeInfo> = sequence {
        yield(this@descendants)
        for (i in 0 until childCount) getChild(i)?.let { yieldAll(it.descendants()) }
    }

    private fun space(action: Int) = KeyEvent(0, 0, action, KeyEvent.KEYCODE_SPACE, 0, 0, -1, 57)

    @Test
    fun spaceKeepsTheLatchedButtonOn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
            tapSustainButton()
            instrumentation.runOnMainSync { assertTrue("button didn't latch sustain", vm.sustain.value) }

            scenario.onActivity { it.dispatchKeyEvent(space(KeyEvent.ACTION_DOWN)) }
            scenario.onActivity { it.dispatchKeyEvent(space(KeyEvent.ACTION_UP)) }
            instrumentation.runOnMainSync { assertTrue("releasing Space turned off the latched Sustain button", vm.sustain.value) }

            tapSustainButton()
            instrumentation.runOnMainSync { assertFalse("button didn't unlatch sustain", vm.sustain.value) }
        }
    }

    @Test
    fun spaceStillHoldsThePedalWhileTheButtonIsOff() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            scenario.onActivity { vm = ViewModelProvider(it)[PianoViewModel::class.java] }
            scenario.onActivity { it.dispatchKeyEvent(space(KeyEvent.ACTION_DOWN)) }
            instrumentation.runOnMainSync { assertTrue("Space didn't hold the pedal", vm.sustain.value) }
            scenario.onActivity { it.dispatchKeyEvent(space(KeyEvent.ACTION_UP)) }
            instrumentation.runOnMainSync { assertFalse("pedal stuck after Space", vm.sustain.value) }
        }
    }
}
