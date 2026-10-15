package code.alimiracle.sunpiano.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import code.alimiracle.sunpiano.data.Settings
import kotlin.math.roundToInt

/** Sound and keyboard settings, and the Bluetooth MIDI keyboard section. */
@Composable
internal fun SettingsDialog(
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
