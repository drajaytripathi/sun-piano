package code.alimiracle.sunpiano

import android.app.Activity
import android.app.UiAutomation
import android.graphics.Point
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android 16+ ignores the landscape lock on tablets (TABLETS.md, improvement 1). A device held in
 * portrait shows two keyboards, the upper one continuing where the lower one ends. A landscape
 * display shows one, even in a tall window such as one half of split screen.
 */
@RunWith(AndroidJUnit4::class)
class PortraitLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val whites = KeyboardLayout().whiteNotes

    /** Where the screen is laid out: the whole window, or a box standing in for a split-screen window. */
    private enum class Area(val box: Modifier, val widthDp: Int?, val heightDp: Int?) {
        WHOLE(Modifier.fillMaxSize(), null, null),
        TALL(Modifier.width(300.dp).fillMaxHeight(), 300, null),
        WIDE(Modifier.fillMaxWidth().height(300.dp), null, 300),
    }

    private class Column(val hits: List<Int>, val lowerFirst: Int, val visibleKeys: Int)

    @After
    fun unfreeze() {
        instrumentation.uiAutomation.setRotation(UiAutomation.ROTATION_UNFREEZE)
    }

    @Suppress("DEPRECATION")
    private fun displayIsPortrait(activity: Activity): Boolean {
        val size = Point().also { activity.windowManager.defaultDisplay.getRealSize(it) }
        return size.y > size.x
    }

    /**
     * Turns the device so its display is portrait (or landscape) if the app lets it, lays the screen
     * out in [area], then taps from the bottom up through the middle of the left-most white key until
     * well past the last keyboard. Returns the notes hit, bottom first, each run of taps on one key
     * counted once, or null if the display couldn't be turned that way (the landscape lock on phones).
     */
    private fun tapUpTheLeftColumn(portraitDisplay: Boolean, area: Area): Column? {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var vm: PianoViewModel
            lateinit var view: View
            scenario.onActivity { activity ->
                vm = ViewModelProvider(activity)[PianoViewModel::class.java]
                activity.setContent { PanTheme { Box(area.box) { PianoScreen(vm) } } }
                view = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
            }
            var turned = false
            for (rotation in listOf(UiAutomation.ROTATION_FREEZE_0, UiAutomation.ROTATION_FREEZE_90)) {
                instrumentation.uiAutomation.setRotation(rotation)
                SystemClock.sleep(1500) // rotation, saved settings load, layout settles
                scenario.onActivity { turned = displayIsPortrait(it) == portraitDisplay }
                if (turned) break
            }
            if (!turned) return null
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
            val boxWidth = area.widthDp?.let { it * density } ?: view.width.toFloat()
            val boxBottom = loc[1] + (area.heightDp?.let { (it * density).toInt() } ?: view.height) - ins.bottom
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
    fun aPortraitDeviceShowsTwoKeyboardsTheUpperOneContinuingTheLower() {
        val c = tapUpTheLeftColumn(portraitDisplay = true, Area.WHOLE)
        assumeTrue("the display stays landscape here (the lock is honoured on phones)", c != null)
        assertEquals("notes up the left column", listOf(whites[c!!.lowerFirst], whites[c.lowerFirst + c.visibleKeys]), c.hits)
    }

    @Test
    fun aTallWindowOnALandscapeDisplayShowsOneKeyboard() { // e.g. split screen on a landscape tablet
        val c = tapUpTheLeftColumn(portraitDisplay = false, Area.TALL)!!
        assertEquals("notes up the left column", listOf(whites[c.lowerFirst]), c.hits)
    }

    @Test
    fun aLandscapeDeviceShowsOneKeyboard() {
        val c = tapUpTheLeftColumn(portraitDisplay = false, Area.WIDE)!!
        assertEquals("notes up the left column", listOf(whites[c.lowerFirst]), c.hits)
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
}
