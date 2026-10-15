package code.alimiracle.sunpiano.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import code.alimiracle.sunpiano.audio.Instruments
import code.alimiracle.sunpiano.data.Settings

/** Windows narrower than this (phones in landscape) get the compact top bar. */
private const val COMPACT_BAR_BELOW_DP = 1000

/** Instrument, volume, sustain, octave, metronome, recording, and the Library, Settings and About buttons. */
@Composable
fun PianoTopBar(
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
    onMetronome: () -> Unit,
    onLibrary: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    // a phone in landscape: the full bar doesn't fit on one line, so Settings and About were
    // off-screen (an upright tablet wraps the bar instead, with room for the labels)
    val compact = !wrap && LocalConfiguration.current.screenWidthDp < COMPACT_BAR_BELOW_DP
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        // compact: narrower buttons, and Settings and About as icons (TalkBack still reads their names)
        val pad = if (compact) PaddingValues(horizontal = 12.dp, vertical = 8.dp) else ButtonDefaults.ContentPadding
        val content: @Composable () -> Unit = {
            InstrumentPicker(settings.instrumentId, onInstrument, pad)

            Text("Vol", fontSize = 12.sp, color = Color.Gray)
            Slider(settings.volume, onVolume, Modifier.width(if (compact) 90.dp else 110.dp))

            ToggleButton("Sustain", sustain, pad, onSustain)
            ToggleButton("◀", false, pad) { onOctave(-1) }
            ToggleButton("▶", false, pad) { onOctave(1) }

            ToggleButton(
                if (metronome && beat >= 0) "Metro ${beat + 1}" else "Metro",
                metronome,
                pad,
                onMetronome,
            )

            Button(
                onClick = onRecord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = Color.White,
                ),
                contentPadding = pad,
            ) { Text(if (recording) "■ Stop" else "● Rec") }

            OutlinedButton(onClick = onLibrary, contentPadding = pad) { Text("Library") }
            if (compact) {
                OutlinedButton(
                    onClick = onSettings,
                    Modifier.semantics { contentDescription = "Settings" },
                    contentPadding = pad,
                ) { Text("⚙", fontSize = 18.sp) }
                OutlinedButton(
                    onClick = onAbout,
                    Modifier.semantics { contentDescription = "About" },
                    contentPadding = pad,
                ) { Text("ⓘ", fontSize = 18.sp) }
            } else {
                OutlinedButton(onClick = onSettings) { Text("⚙ Settings") }
                OutlinedButton(onClick = onAbout) { Text("ⓘ About") }
            }

            if (midiName != null) {
                Text("MIDI: $midiName", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // too narrow for one line (portrait): wrap onto more lines instead of scrolling
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
                horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp),
            ) { content() }
        }
    }
}

@Composable
private fun ToggleButton(label: String, on: Boolean, padding: PaddingValues, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = padding,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(label, maxLines = 1) }
}

@Composable
private fun InstrumentPicker(selectedId: String, onSelect: (String) -> Unit, padding: PaddingValues) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, contentPadding = padding) { Text(Instruments.byId(selectedId).name + " ▾", maxLines = 1) }
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
