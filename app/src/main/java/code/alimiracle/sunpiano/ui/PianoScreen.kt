package code.alimiracle.sunpiano.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import code.alimiracle.sunpiano.BuildConfig
import code.alimiracle.sunpiano.PianoViewModel
import code.alimiracle.sunpiano.audio.Instruments
import code.alimiracle.sunpiano.data.Recording
import code.alimiracle.sunpiano.data.Settings
import code.alimiracle.sunpiano.midi.BluetoothMidiScanner
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class Panel { NONE, METRONOME, SETTINGS, RECORDINGS, ABOUT }

/** What the file picker offers for Import .mid: file managers and browsers label .mid files differently. */
private val MIDI_TYPES = arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "audio/sp-midi", "application/octet-stream")

@Composable
fun PianoScreen(vm: PianoViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pressed by vm.pressed.collectAsStateWithLifecycle()
    val sustain by vm.sustainLatched.collectAsStateWithLifecycle()
    val recording by vm.isRecording.collectAsStateWithLifecycle()
    val metronome by vm.metronomeOn.collectAsStateWithLifecycle()
    val beat by vm.beat.collectAsStateWithLifecycle()
    val firstWhite by vm.firstWhite.collectAsStateWithLifecycle()
    val midiName by vm.midiDevice.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf(Panel.NONE) }

    LocalView.current.keepScreenOn = settings.keepScreenOn

    // On a device held in portrait (Android 16+ ignores the landscape lock on tablets) one keyboard
    // would have very long, narrow keys, so show two: the upper one continues where the lower one ends.
    // Decided by the display, not the window, so split screen on a landscape tablet keeps one keyboard.
    val portrait = displayIsPortrait()
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        val rows = if (portrait) 2 else 1
        SideEffect { vm.setKeyboardRows(rows) }
        Column(Modifier.fillMaxSize()) {
            ControlBar(
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
                onPanel = { panel = it },
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

    when (panel) {
        Panel.METRONOME -> MetronomeDialog(settings, metronome, beat, vm::setMetronome, vm::updateSettings) { panel = Panel.NONE }
        Panel.SETTINGS -> SettingsDialog(settings, vm::updateSettings, { BluetoothMidiSection(vm) }) { panel = Panel.NONE }
        Panel.RECORDINGS -> RecordingsDialog(vm) { panel = Panel.NONE }
        Panel.ABOUT -> AboutDialog { panel = Panel.NONE }
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

@Composable
private fun ControlBar(
    wrap: Boolean,
    settings: Settings,
    sustain: Boolean,
    recording: Boolean,
    metronome: Boolean,
    beat: Int,
    midiName: String?,
    onInstrument: (String) -> Unit,
    onVolume: (Float) -> Unit,
    onSustain: () -> Unit,
    onRecord: () -> Unit,
    onOctave: (Int) -> Unit,
    onPanel: (Panel) -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        // too narrow for one line (portrait): wrap onto more lines instead of scrolling
        val content: @Composable () -> Unit = {
            InstrumentPicker(settings.instrumentId, onInstrument)

            Text("Vol", fontSize = 12.sp, color = Color.Gray)
            Slider(settings.volume, onVolume, Modifier.width(110.dp))

            ToggleButton("Sustain", sustain, onSustain)
            ToggleButton("◀", false) { onOctave(-1) }
            ToggleButton("▶", false) { onOctave(1) }

            ToggleButton(
                if (metronome && beat >= 0) "Metro ${beat + 1}" else "Metro",
                metronome,
            ) { onPanel(Panel.METRONOME) }

            Button(
                onClick = onRecord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White,
                ),
            ) { Text(if (recording) "■ Stop" else "● Rec") }

            OutlinedButton(onClick = { onPanel(Panel.RECORDINGS) }) { Text("Library") }
            OutlinedButton(onClick = { onPanel(Panel.SETTINGS) }) { Text("⚙ Settings") }
            OutlinedButton(onClick = { onPanel(Panel.ABOUT) }) { Text("ⓘ About") }

            if (midiName != null) {
                Text("MIDI: $midiName", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (wrap) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) { content() }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) { content() }
        }
    }
}

@Composable
private fun ToggleButton(label: String, on: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun InstrumentPicker(selectedId: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text(Instruments.byId(selectedId).name + " ▾", maxLines = 1) }
        DropdownMenu(open, { open = false }) {
            Instruments.ALL.forEach { ins ->
                DropdownMenuItem(
                    text = { Text(ins.name, fontWeight = if (ins.id == selectedId) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { onSelect(ins.id); open = false },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- metronome

@Composable
private fun MetronomeDialog(
    s: Settings,
    on: Boolean,
    beat: Int,
    setOn: (Boolean) -> Unit,
    update: ((Settings) -> Settings) -> Unit,
    onClose: () -> Unit,
) {
    var lastTap by remember { mutableLongStateOf(0L) }
    AlertDialog(
        onDismissRequest = onClose,
        // Start/Stop sits with Done in the button row, which never scrolls: inside the content it
        // was below the visible part of the dialog on phones, and people couldn't find it
        confirmButton = { Button({ setOn(!on) }) { Text(if (on) "Stop" else "Start") } },
        dismissButton = { TextButton(onClose) { Text("Done") } },
        title = { Text("Metronome") },
        text = {
            // scrolls on a short (phone landscape) screen
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ update { it.copy(bpm = (it.bpm - 1).coerceIn(30, 240)) } }) { Text("−") }
                    Text("${s.bpm} BPM", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    OutlinedButton({ update { it.copy(bpm = (it.bpm + 1).coerceIn(30, 240)) } }) { Text("+") }
                }
                Slider(s.bpm.toFloat(), { v -> update { it.copy(bpm = v.roundToInt()) } }, valueRange = 30f..240f)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Beats")
                    listOf(2, 3, 4, 6).forEach { n ->
                        FilterChip(s.beatsPerBar == n, { update { it.copy(beatsPerBar = n) } }, label = { Text("$n") })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    repeat(s.beatsPerBar) { i ->
                        Box(
                            Modifier
                                .size(if (i == 0) 18.dp else 14.dp)
                                .clip(CircleShape)
                                .background(if (on && beat == i) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                }
                Text("Click volume", fontSize = 12.sp)
                Slider(s.metronomeVolume, { v -> update { it.copy(metronomeVolume = v) } })
                OutlinedButton({
                    val now = System.currentTimeMillis()
                    val gap = now - lastTap
                    if (lastTap != 0L && gap in 250..2000) update { it.copy(bpm = (60000f / gap).roundToInt().coerceIn(30, 240)) }
                    lastTap = now
                }) { Text("Tap tempo") }
            }
        },
    )
}

// ---------------------------------------------------------------- settings

@Composable
private fun SettingsDialog(
    s: Settings,
    update: ((Settings) -> Settings) -> Unit,
    bluetoothMidi: @Composable () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)

                LabeledSlider("Reverb", "${(s.reverb * 100).roundToInt()}%", s.reverb, 0f..1f) { v -> update { it.copy(reverb = v) } }
                LabeledSlider("Transpose", "%+d semitones".format(s.transpose), s.transpose.toFloat(), -12f..12f, 23) { v ->
                    update { it.copy(transpose = v.roundToInt()) }
                }
                LabeledSlider("Tuning (A4)", "${s.tuning} Hz", s.tuning.toFloat(), 415f..466f) { v ->
                    update { it.copy(tuning = v.roundToInt()) }
                }
                LabeledSlider("Visible keys", "${s.visibleKeys} white keys", s.visibleKeys.toFloat(), 7f..28f, 20) { v ->
                    update { it.copy(visibleKeys = v.roundToInt()) }
                }
                HorizontalDivider()
                SwitchRow("Show note names", s.showLabels) { b -> update { it.copy(showLabels = b) } }
                SwitchRow("Do-Re-Mi names", s.solfege) { b -> update { it.copy(solfege = b) } }
                SwitchRow("Touch position sets loudness", s.velocityFromTouch) { b -> update { it.copy(velocityFromTouch = b) } }
                SwitchRow("Glissando (slide between keys)", s.glissando) { b -> update { it.copy(glissando = b) } }
                SwitchRow("Keep screen on", s.keepScreenOn) { b -> update { it.copy(keepScreenOn = b) } }
                bluetoothMidi()
                Text(
                    "Tip: a computer keyboard works too (Z–M row = C3, Q–U row = C4), as does a USB MIDI keyboard: just plug it in.",
                    fontSize = 12.sp, color = Color.Gray,
                )
                TextButton(onClose, Modifier.align(Alignment.End)) { Text("Close") }
            }
        }
    }
}

@Composable
private fun BluetoothMidiSection(vm: PianoViewModel) {
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

@Composable
private fun LabeledSlider(
    label: String, value: String, v: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, color = MaterialTheme.colorScheme.primary)
        }
        Slider(v, onChange, valueRange = range, steps = steps)
    }
}

private const val SOURCE_URL = "https://codeberg.org/alimiracle/sun-piano"
private const val ISSUES_URL = "$SOURCE_URL/issues"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** Version, copyright and the GPL's notices (no warranty, where the license is), with links. */
@Composable
private fun AboutDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var noBrowser by remember { mutableStateOf<String?>(null) }
    fun open(url: String) {
        // the phone's browser shows it: Sun Piano itself has no internet access
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            noBrowser = url
        }
    }
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = { TextButton(onClose) { Text("Close") } },
        title = { Text("Sun Piano ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("An electronic piano with a synthesizer of its own.")
                Text(
                    "Copyright (C) 2026 alimiracle\n" +
                        "Free software under the GNU General Public License, version 3 or later. " +
                        "It comes with ABSOLUTELY NO WARRANTY.",
                    fontSize = 13.sp,
                )
                Text("No ads, no tracking, no internet access.", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                // above the links, so it's seen without scrolling the dialog
                noBrowser?.let { Text("No browser found. The address is:\n$it", fontSize = 12.sp, color = Color.Gray) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton({ open(SOURCE_URL) }) { Text("Source code") }
                    TextButton({ open(ISSUES_URL) }) { Text("Report a problem") }
                    TextButton({ open(LICENSE_URL) }) { Text("License (GPL v3)") }
                }
            }
        },
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // the whole row toggles, label included (and is one "switch" for screen readers)
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null)
    }
}

// ---------------------------------------------------------------- recordings

@Composable
private fun RecordingsDialog(vm: PianoViewModel, onClose: () -> Unit) {
    val list by vm.recordings.collectAsStateWithLifecycle()
    val playingId by vm.playingId.collectAsStateWithLifecycle()
    val progress by vm.playbackProgress.collectAsStateWithLifecycle()
    val audioExport by vm.audioExport.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<Recording?>(null) }
    var deleting by remember { mutableStateOf<Recording?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickMidi = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importMidi)
    }
    // A list keeps its first visible item in place, so a recording added at the top (an import)
    // would appear just above the visible part: show it.
    val listState = rememberLazyListState()
    LaunchedEffect(list.firstOrNull()?.id) { listState.scrollToItem(0) }

    AlertDialog(
        onDismissRequest = { vm.stopPlayback(); onClose() },
        confirmButton = { TextButton({ vm.stopPlayback(); onClose() }) { Text("Close") } },
        dismissButton = { TextButton({ pickMidi.launch(MIDI_TYPES) }) { Text("Import .mid") } },
        title = { Text("Recordings") },
        text = {
            if (list.isEmpty()) {
                Text("Nothing here yet. Tap ● Rec, play something, then tap ■ Stop.")
            } else {
                LazyColumn(Modifier.heightIn(max = 340.dp), state = listState) {
                    items(list, key = { it.id }) { rec ->
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(rec.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // once per recording: these walk all the events, and every item
                            // recomposes on each playback-progress tick
                            val subtitle = remember(rec.id) {
                                val instruments = rec.instrumentIds.takeIf { it.isNotEmpty() }
                                    ?.joinToString(", ", prefix = " · ") { Instruments.byId(it).name }.orEmpty()
                                "%.1f s · %d notes".format(
                                    rec.durationMs / 1000f,
                                    rec.events.count { it.velocity > 0 && it.note >= 0 },
                                ) + instruments
                            }
                            Text(subtitle, fontSize = 12.sp, color = Color.Gray)
                            if (playingId == rec.id) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            // wraps onto a second line in a narrow (phone) dialog instead of squeezing a button
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton({ vm.togglePlayback(rec) }) { Text(if (playingId == rec.id) "■ Stop" else "▶ Play") }
                                TextButton({ renaming = rec }) { Text("Rename") }
                                TextButton({
                                    scope.launch {
                                        val file = vm.exportMidi(rec) ?: return@launch
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/midi"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Share MIDI"))
                                    }
                                }) { Text("Share .mid") }
                                val exportingThis = audioExport?.first == rec.id
                                TextButton({
                                    scope.launch {
                                        val file = vm.exportAudio(rec) ?: return@launch
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "audio/mp4"
                                            putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Share audio"))
                                    }
                                }, enabled = audioExport == null) {
                                    Text(if (exportingThis) "Exporting ${((audioExport?.second ?: 0f) * 100).roundToInt()}%" else "Share audio")
                                }
                                TextButton({ deleting = rec }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
    )

    renaming?.let { rec ->
        var text by remember(rec.id) { mutableStateOf(rec.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename") },
            text = { OutlinedTextField(text, { text = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.renameRecording(rec, text); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { rec ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete recording?") },
            text = { Text("\"${rec.name}\" will be removed permanently.") },
            confirmButton = { TextButton({ vm.deleteRecording(rec); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } },
        )
    }
}
