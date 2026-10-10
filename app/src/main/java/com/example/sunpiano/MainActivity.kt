package com.example.sunpiano

import android.media.AudioManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.sunpiano.music.SustainSources
import com.example.sunpiano.ui.PanTheme
import com.example.sunpiano.ui.PianoScreen

class MainActivity : ComponentActivity() {
    private val vm: PianoViewModel by viewModels()

    /** Physical key -> piano key, so releasing the same key always ends the right note. */
    private val typedKeys = HashMap<Int, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { PanTheme { PianoScreen(vm) } }
    }

    override fun onStart() {
        super.onStart()
        vm.onForeground()
    }

    override fun onStop() {
        // Key-ups for keys held now would reach the next activity, which doesn't know them.
        typedKeys.values.forEach { vm.noteOff(it) }
        typedKeys.clear()
        vm.setSustain(SustainSources.Source.SPACE, false)
        // Being recreated for a configuration change the manifest doesn't handle: keep playing,
        // recording and the metronome going; the view model outlives this activity.
        if (!isChangingConfigurations) vm.onBackground()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
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
        if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) return super.dispatchKeyEvent(event)
        if (event.keyCode == KeyEvent.KEYCODE_SPACE) {
            if (event.repeatCount == 0) vm.setSustain(SustainSources.Source.SPACE, event.action == KeyEvent.ACTION_DOWN)
            return true
        }
        val physical = if (event.scanCode > 0) event.scanCode else -event.keyCode
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
                val key = typedKeys.remove(physical)
                    ?: return if (PianoViewModel.keyForPhysicalKey(event.scanCode, event.keyCode) != null) true else super.dispatchKeyEvent(event)
                vm.noteOff(key)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}
