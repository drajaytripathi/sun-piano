package code.alimiracle.sunpiano.ui.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import code.alimiracle.sunpiano.PianoViewModel

/** Which dialog is open (the top bar's buttons open them). */
enum class Panel { NONE, METRONOME, SETTINGS, RECORDINGS, ABOUT }

/** Shows the open [panel]'s dialog, and the view model's error message if there is one. */
@Composable
fun PianoDialogs(vm: PianoViewModel, panel: Panel, onClose: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val metronome by vm.metronomeOn.collectAsStateWithLifecycle()
    val beat by vm.beat.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()

    when (panel) {
        Panel.METRONOME -> MetronomeDialog(settings, metronome, beat, vm::setMetronome, vm::updateSettings, onClose)
        Panel.SETTINGS -> SettingsDialog(settings, vm::updateSettings, { BluetoothMidiSection(vm) }, onClose)
        Panel.RECORDINGS -> RecordingsDialog(vm, onClose)
        Panel.ABOUT -> AboutDialog(onClose)
        Panel.NONE -> Unit
    }

    message?.let { text ->
        AlertDialog(
            onDismissRequest = vm::messageShown,
            confirmButton = { TextButton(vm::messageShown) { Text("OK") } },
            title = { Text("Something went wrong") },
            text = { Text(text) },
        )
    }
}
