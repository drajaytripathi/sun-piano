package code.alimiracle.sunpiano.ui.dialogs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import code.alimiracle.sunpiano.BuildConfig

private const val SOURCE_URL = "https://github.com/drajaytripathi/sun-piano-duo"
private const val ORIGINAL_URL = "https://codeberg.org/alimiracle/sun-piano"
private const val ISSUES_URL = "$SOURCE_URL/issues"
private const val LICENSE_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

/** Version, copyright and the GPL's notices (no warranty, where the license is), with links. */
@Composable
internal fun AboutDialog(onClose: () -> Unit) {
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
        title = { Text("Sun Piano Duo ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("An electronic piano with a synthesizer of its own. A fork of Sun Piano by alimiracle.")
                Text(
                    "Copyright (C) 2026 alimiracle\n" +
                        "Fork changes Copyright (C) 2026 drajaytripathi\n" +
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
                    TextButton({ open(ORIGINAL_URL) }) { Text("Original Sun Piano") }
                    TextButton({ open(LICENSE_URL) }) { Text("License (GPL v3)") }
                }
            }
        },
    )
}
