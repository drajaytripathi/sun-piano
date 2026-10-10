package com.example.sunpiano

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first Space press after touching the screen must engage sustain (bug.md #6). The events are
 * injected into the system input pipeline, as a real screen and keyboard would send them, so they
 * pass through the window's touch-mode handling before reaching the activity.
 */
@RunWith(AndroidJUnit4::class)
class SpaceAfterTouchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    // connected before the activity starts, so Compose builds its accessibility tree
    private val automation = instrumentation.uiAutomation.apply {
        serviceInfo = serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    private fun AccessibilityNodeInfo.descendants(): Sequence<AccessibilityNodeInfo> = sequence {
        yield(this@descendants)
        for (i in 0 until childCount) getChild(i)?.let { yieldAll(it.descendants()) }
    }

    /** Screen bounds of the on-screen text [label]. */
    private fun boundsOf(label: String): Rect {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (true) {
            val node = automation.windows.asSequence()
                .mapNotNull { it.root }
                .flatMap { it.descendants() }
                .firstOrNull { it.text?.toString() == label }
            if (node != null) return Rect().also { node.getBoundsInScreen(it) }
            check(SystemClock.uptimeMillis() < deadline) { "\"$label\" not found" }
            Thread.sleep(100)
        }
    }

    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        for ((action, t) in listOf(MotionEvent.ACTION_DOWN to down, MotionEvent.ACTION_UP to down + 50)) {
            val e = MotionEvent.obtain(down, t, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            check(automation.injectInputEvent(e, true)) { "tap not injected" }
            e.recycle()
        }
        instrumentation.waitForIdleSync()
    }

    private fun key(code: Int, scanCode: Int, action: Int) {
        val now = SystemClock.uptimeMillis()
        val e = KeyEvent(now, now, action, code, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, scanCode, 0, InputDevice.SOURCE_KEYBOARD)
        check(automation.injectInputEvent(e, true)) { "key not injected" }
        instrumentation.waitForIdleSync()
    }

    @Test
    fun firstSpaceAfterTouchingTheScreenEngagesSustain() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            var w = 0; var h = 0
            scenario.onActivity {
                vm = ViewModelProvider(it)[PianoViewModel::class.java]
                w = it.window.decorView.width; h = it.window.decorView.height
            }
            val touches = linkedMapOf<String, () -> Unit>(
                "a piano key" to { tap(w / 2f, h * 0.8f) },
                "the ▶ button" to { boundsOf("▶").let { tap(it.exactCenterX(), it.exactCenterY()) } },
                "the volume slider" to { boundsOf("Vol").let { tap(it.right + 60 * instrumentation.targetContext.resources.displayMetrics.density, it.exactCenterY()) } },
                "opening and closing the metronome dialog" to {
                    boundsOf("Metro").let { tap(it.exactCenterX(), it.exactCenterY()) }
                    boundsOf("Beats") // dialog is up
                    key(KeyEvent.KEYCODE_BACK, 158, KeyEvent.ACTION_DOWN)
                    key(KeyEvent.KEYCODE_BACK, 158, KeyEvent.ACTION_UP)
                    Thread.sleep(500)
                    tap(w / 2f, h * 0.8f)
                },
            )
            for ((what, touch) in touches) {
                touch()
                key(KeyEvent.KEYCODE_SPACE, 57, KeyEvent.ACTION_DOWN)
                var held = false
                instrumentation.runOnMainSync { held = vm.sustain.value }
                key(KeyEvent.KEYCODE_SPACE, 57, KeyEvent.ACTION_UP)
                assertTrue("first Space press after touching $what didn't engage sustain", held)
                instrumentation.runOnMainSync { assertFalse("pedal stuck after Space", vm.sustain.value) }
            }
        }
    }
}
