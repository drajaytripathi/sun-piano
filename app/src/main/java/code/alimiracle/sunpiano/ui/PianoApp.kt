package code.alimiracle.sunpiano.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import code.alimiracle.sunpiano.PianoViewModel
import code.alimiracle.sunpiano.ui.components.KeyboardOverview
import code.alimiracle.sunpiano.ui.components.PianoKeyboard
import code.alimiracle.sunpiano.ui.components.PianoTopBar
import code.alimiracle.sunpiano.ui.dialogs.Panel
import code.alimiracle.sunpiano.ui.dialogs.PianoDialogs
import code.alimiracle.sunpiano.ui.theme.SunPianoTheme

/** The whole app: the top bar, the mini-map, one or two keyboards, and the dialogs. */
@Composable
fun PianoApp(vm: PianoViewModel) = SunPianoTheme {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    val sustain by vm.sustainLatched.collectAsStateWithLifecycle()
    val recording by vm.isRecording.collectAsStateWithLifecycle()
    val metronome by vm.metronomeOn.collectAsStateWithLifecycle()
    val beat by vm.beat.collectAsStateWithLifecycle()
    val firstWhite by vm.firstWhite.collectAsStateWithLifecycle()
    val midiName by vm.midiDevice.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf(Panel.NONE) }

    LocalView.current.keepScreenOn = settings.keepScreenOn

    // On a device held in portrait (Android 16+ ignores the landscape lock on tablets) one keyboard
    // would have very long, narrow keys, so show two: the upper one continues where the lower one ends.
    // Decided by the display, not the window, so split screen on a landscape tablet keeps one keyboard.
    val portrait = displayIsPortrait()
    // A tablet in landscape has room for two rows too (unless turned off in Settings). This one is
    // decided by the window, so a short split-screen window still gets one keyboard with usable keys.
    val tallLandscape = !portrait && settings.twoRowsInLandscape &&
        LocalConfiguration.current.screenHeightDp >= TWO_ROW_MIN_HEIGHT_DP
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val rows = if (portrait || tallLandscape) 2 else 1
        SideEffect { vm.setKeyboardRows(rows) }
        Column(Modifier.fillMaxSize()) {
            PianoTopBar(
                wrap = portrait,
                settings = settings,
                sustain = sustain,
                recording = recording,
                metronome = metronome,
                beat = beat,
                midiName = midiName,
                onInstrument = { id -> vm.updateSettings { it.copy(instrumentId = id) } },
                onVolume = { v -> vm.updateSettings { it.copy(volume = v) } },
                onSustain = vm::toggleSustainButton,
                onRecord = vm::toggleRecording,
                onOctave = vm::shiftOctave,
                onMetronome = { panel = Panel.METRONOME },
                onLibrary = { panel = Panel.RECORDINGS },
                onSettings = { panel = Panel.SETTINGS },
                onAbout = { panel = Panel.ABOUT },
            )
            KeyboardOverview(
                layout = vm.layout,
                pressed = pressed,
                firstWhite = firstWhite,
                visibleKeys = settings.visibleKeys * rows,
                onScroll = vm::setFirstWhite,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).clip(RoundedCornerShape(4.dp)),
            )
            for (row in rows - 1 downTo 0) key(row) { // the bottom keyboard keeps its touches when rotating
                if (row < rows - 1) Box(Modifier.fillMaxWidth().height(10.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    PianoKeyboard(
                        layout = vm.layout,
                        pressed = pressed,
                        firstWhite = firstWhite + row * settings.visibleKeys,
                        visibleKeys = settings.visibleKeys,
                        showLabels = settings.showLabels,
                        solfege = settings.solfege,
                        glissando = settings.glissando,
                        velocityFromTouch = settings.velocityFromTouch,
                        onNoteOn = vm::noteOn,
                        onNoteOff = vm::noteOff,
                    )
                }
            }
        }
    }

    PianoDialogs(vm, panel) { panel = Panel.NONE }
}

/** Window height (dp) from which a landscape window shows two keyboards: tablets, not phones. */
private const val TWO_ROW_MIN_HEIGHT_DP = 600

/** Whether the whole display (not just this app's window) is taller than wide. */
@Composable
private fun displayIsPortrait(): Boolean {
    val view = LocalView.current
    val configuration = LocalConfiguration.current // changes on rotation, so this is re-read then
    return remember(configuration, view) {
        val activity = view.context as? Activity
            ?: return@remember configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        val bounds = activity.windowManager.maximumWindowMetrics.bounds
        bounds.height() > bounds.width()
    }
}
