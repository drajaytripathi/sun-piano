package code.alimiracle.sunpiano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import code.alimiracle.sunpiano.music.KeyboardLayout
import code.alimiracle.sunpiano.ui.theme.Amber

/** Thin mini-map of the whole 88-key range; drag or tap to move the visible window. */
@Composable
fun KeyboardOverview(
    layout: KeyboardLayout,
    pressed: Set<Int>,
    firstWhite: Float,
    visibleKeys: Int,
    onScroll: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll by rememberUpdatedState(onScroll)
    val vis by rememberUpdatedState(visibleKeys)
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(layout) {
                detectTapGestures { p -> scroll(p.x / size.width * layout.whiteCount - vis / 2f) }
            }
            .pointerInput(layout) {
                detectDragGestures { change, _ ->
                    change.consume()
                    scroll(change.position.x / size.width * layout.whiteCount - vis / 2f)
                }
            },
    ) {
        val w = size.width / layout.whiteCount
        for (i in 0 until layout.whiteCount) {
            val down = layout.whiteNotes[i] in pressed
            drawRect(
                if (down) Amber else Color(0xFFCFD3D8),
                Offset(i * w + 0.5f, 0f), Size(w - 1f, size.height),
            )
            layout.blackAfter(i)?.let { b ->
                drawRect(
                    if (b in pressed) Amber else Color(0xFF1A1E23),
                    Offset((i + 1) * w - w * 0.3f, 0f), Size(w * 0.6f, size.height * 0.6f),
                )
            }
        }
        // dim everything outside the window
        val left = firstWhite * w
        val right = (firstWhite + visibleKeys) * w
        val shade = Color(0xAA101418)
        drawRect(shade, Offset.Zero, Size(left, size.height))
        drawRect(shade, Offset(right, 0f), Size(size.width - right, size.height))
        drawRoundRect(
            Amber, Offset(left, 0f), Size(right - left, size.height),
            CornerRadius(4f), style = androidx.compose.ui.graphics.drawscope.Stroke(3f),
        )
    }
}
