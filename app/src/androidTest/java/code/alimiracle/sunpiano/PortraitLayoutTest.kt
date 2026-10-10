package code.alimiracle.sunpiano

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import code.alimiracle.sunpiano.music.KeyboardLayout
import code.alimiracle.sunpiano.ui.PanTheme
import code.alimiracle.sunpiano.ui.PianoScreen
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android 16+ ignores the landscape lock on tablets (TABLETS.md, improvement 1). A tall window shows
 * two keyboards, the upper one continuing where the lower one ends; a wide one shows one.
 */
@RunWith(AndroidJUnit4::class)
class PortraitLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class Column(val hits: List<Int>, val lowerFirst: Int, val visibleKeys: Int)

    /**
     * Lays the screen out [narrowDp] wide (portrait) or 300 dp tall (landscape), then taps from the
     * bottom up through the middle of the left-most white key until well past the last keyboard.
     * Returns the notes hit, bottom first, each run of taps on one key counted once.
     */
    private fun tapUpTheLeftColumn(portrait: Boolean): Column {
        val narrowDp = 300
        val box = if (portrait) Modifier.width(narrowDp.dp).fillMaxHeight() else Modifier.fillMaxWidth().height(300.dp)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            lateinit var view: View
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[PianoViewModel::class.java]
                activity.setContent { PanTheme { Box(box) { PianoScreen(vm) } } }
                view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            }
            SystemClock.sleep(800) // saved settings load, layout settles
            var first = 0
            var keys = 0
            scenario.onActivity {
                vm.setFirstWhite(vm.layout.whiteIndexOf(36).toFloat()) // C2: room for two rows above it
                first = vm.firstWhite.value.toInt()
                keys = vm.settings.value.visibleKeys
            }
            SystemClock.sleep(300) // the keyboards redraw at the new position
            instrumentation.waitForIdleSync()

            val density = view.resources.displayMetrics.density
            val loc = IntArray(2).also { view.getLocationOnScreen(it) }
            // the screen keeps clear of the system bars and camera cutout on every side (safeDrawing)
            val ins = WindowInsetsCompat.toWindowInsetsCompat(view.rootWindowInsets)
                .getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val boxWidth = if (portrait) narrowDp * density else view.width.toFloat()
            val boxBottom = loc[1] + (if (portrait) view.height else (300 * density).toInt()) - ins.bottom
            val x = loc[0] + ins.left + (boxWidth - ins.left - ins.right) / keys / 2f
            val step = 4 * density
            val stopAfterMisses = 24 * density // more than the gap between keyboards, less than reaching the buttons

            val hits = ArrayList<Int>()
            var y = boxBottom - step
            var missed = 0f
            while (y > loc[1] && missed < stopAfterMisses) {
                val t = SystemClock.uptimeMillis()
                instrumentation.sendPointerSync(MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0))
                var down: Set<Int> = emptySet()
                scenario.onActivity { down = vm.pressed.value }
                instrumentation.sendPointerSync(MotionEvent.obtain(t, t + 10, MotionEvent.ACTION_UP, x, y, 0))
                val note = down.singleOrNull()
                if (note == null) {
                    if (hits.isNotEmpty()) missed += step
                } else {
                    missed = 0f
                    if (hits.lastOrNull() != note) hits += note
                }
                y -= step
            }
            return Column(hits, first, keys)
        }
    }

    @Test
    fun aTallWindowShowsTwoKeyboardsTheUpperOneContinuingTheLower() {
        val c = tapUpTheLeftColumn(portrait = true)
        val whites = KeyboardLayout().whiteNotes
        assertEquals("notes up the left column", listOf(whites[c.lowerFirst], whites[c.lowerFirst + c.visibleKeys]), c.hits)
    }

    @Test
    fun withTwoKeyboardsScrollingStopsWhereTheUpperOneReachesTheTop() {
        instrumentation.runOnMainSync {
            val vm = PianoViewModel(instrumentation.targetContext.applicationContext as android.app.Application)
            val keys = vm.settings.value.visibleKeys
            vm.setFirstWhite(1000f)
            assertEquals(vm.layout.whiteCount - keys.toFloat(), vm.firstWhite.value)
            vm.setKeyboardRows(2)
            assertEquals("upper keyboard runs past the top note", vm.layout.whiteCount - 2f * keys, vm.firstWhite.value)
            vm.setKeyboardRows(1)
            vm.setFirstWhite(1000f)
            assertEquals(vm.layout.whiteCount - keys.toFloat(), vm.firstWhite.value)
        }
    }

    @Test
    fun aWideWindowShowsOneKeyboard() {
        val c = tapUpTheLeftColumn(portrait = false)
        val whites = KeyboardLayout().whiteNotes
        assertEquals("notes up the left column", listOf(whites[c.lowerFirst]), c.hits)
    }
}
