package code.alimiracle.sunpiano.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import code.alimiracle.sunpiano.data.Settings
import kotlin.math.roundToInt

/** Tempo, beats per bar, the beat lights, click volume and tap tempo; Start/Stop beside Done. */
@Composable
internal fun MetronomeDialog(
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
