package code.alimiracle.sunpiano

import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import code.alimiracle.sunpiano.music.SustainSources
import code.alimiracle.sunpiano.ui.PanTheme
import code.alimiracle.sunpiano.ui.PianoScreen

class MainActivity : ComponentActivity() {
    private val vm: PianoViewModel by viewModels()

    /** Physical key -> piano key, so releasing the same key always ends the right note. */
    private val typedKeys = HashMap<Int, Int>()
    private var spaceHeld = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { PanTheme { PianoScreen(vm) } }
        holdKeyFocus()
    }

    /**
     * Keeps focus on the content view, even in touch mode. When nothing has focus, the first Space
     * press after a screen touch is used up by Android to leave touch mode (it gives a view focus
     * instead of delivering the key), so sustain wouldn't engage.
     */
    private fun holdKeyFocus() {
        val content = findViewById<View>(android.R.id.content)
        content.isFocusableInTouchMode = true
        if (currentFocus == null) content.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        // Key-ups for keys held now would reach the next activity, which doesn't know them.
        typedKeys.values.forEach { vm.noteOff(it) }
        typedKeys.clear()
        spaceHeld = false
        vm.setSustain(SustainSources.Source.SPACE, false)
        // Being recreated for a configuration change the manifest doesn't handle: keep playing,
        // recording and the metronome going; the view model outlives this activity.
        if (!isChangingConfigurations) vm.onBackground()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            holdKeyFocus()
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /**
     * Handled in dispatchKeyEvent so a focused Compose view or button can't swallow the keys.
     * Keys are matched by physical position, so the map works with any keyboard layout.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val physical = if (event.scanCode > 0) event.scanCode else -event.keyCode
        // A release always ends what its press started, even if Ctrl, Alt or Meta went down in
        // between; otherwise the note (or sustain) would stay stuck.
        if (event.action == KeyEvent.ACTION_UP) {
            if (event.keyCode == KeyEvent.KEYCODE_SPACE && spaceHeld) {
                spaceHeld = false
                vm.setSustain(SustainSources.Source.SPACE, false)
                return true
            }
            typedKeys.remove(physical)?.let {
                vm.noteOff(it)
                return true
            }
        }
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_SPACE) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                spaceHeld = true
                vm.setSustain(SustainSources.Source.SPACE, true)
            }
            return true
        }
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                val key = PianoViewModel.keyForPhysicalKey(event.scanCode, event.keyCode)
                    ?: return super.dispatchKeyEvent(event)
                if (event.repeatCount == 0) {
                    typedKeys[physical] = key
                    vm.noteOn(key)
                }
                return true
            }
            KeyEvent.ACTION_UP -> {
                // a piano key released without a press we saw (e.g. pressed with Ctrl held)
                return if (PianoViewModel.keyForPhysicalKey(event.scanCode, event.keyCode) != null) true else super.dispatchKeyEvent(event)
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
