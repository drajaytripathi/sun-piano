package code.alimiracle.sunpiano.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import code.alimiracle.sunpiano.PianoViewModel
import code.alimiracle.sunpiano.midi.BluetoothMidiScanner

/** Finds Bluetooth MIDI keyboards (asking for Nearby devices first) and connects the one picked. */
@Composable
internal fun BluetoothMidiSection(vm: PianoViewModel) {
    if (!vm.bluetoothMidiSupported) return
    val scanning by vm.bluetoothScanning.collectAsStateWithLifecycle()
    val found by vm.bluetoothDevices.collectAsStateWithLifecycle()
    val connected by vm.bluetoothMidiDevice.collectAsStateWithLifecycle()
    var searched by remember { mutableStateOf(false) }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.searchBluetoothMidi() // reports a refused permission itself
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(Modifier.weight(1f)) {
            Text("Bluetooth MIDI keyboard")
            Text(connected?.let { "Connected: $it" } ?: "Not connected", fontSize = 12.sp, color = Color.Gray)
        }
        OutlinedButton(
            onClick = {
                searched = true
                if (vm.hasBluetoothPermissions()) vm.searchBluetoothMidi() else askPermission.launch(BluetoothMidiScanner.PERMISSIONS)
            },
            enabled = !scanning,
        ) { Text(if (scanning) "Searching…" else "Find keyboards") }
    }
    found.forEach { d ->
        TextButton({ searched = false; vm.connectBluetoothMidi(d) }, Modifier.fillMaxWidth()) {
            Text("Connect to ${d.name}", Modifier.weight(1f))
        }
    }
    if (searched && !scanning && found.isEmpty()) {
        Text(
            "No Bluetooth MIDI keyboards found. Switch yours on (or into Bluetooth mode) and search again.",
            fontSize = 12.sp, color = Color.Gray,
        )
    }
}
