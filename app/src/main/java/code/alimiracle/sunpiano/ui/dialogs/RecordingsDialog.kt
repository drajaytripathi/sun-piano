package code.alimiracle.sunpiano.ui.dialogs

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import code.alimiracle.sunpiano.PianoViewModel
import code.alimiracle.sunpiano.audio.Instruments
import code.alimiracle.sunpiano.data.Recording
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** What the file picker offers for Import .mid: file managers and browsers label .mid files differently. */
private val MIDI_TYPES = arrayOf("audio/midi", "audio/x-midi", "audio/mid", "application/x-midi", "audio/sp-midi", "application/octet-stream")

/** The Library: play, rename, share as .mid or audio, and delete recordings; import .mid files. */
@Composable
internal fun RecordingsDialog(vm: PianoViewModel, onClose: () -> Unit) {
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
