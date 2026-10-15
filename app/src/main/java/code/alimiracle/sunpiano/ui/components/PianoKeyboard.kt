package code.alimiracle.sunpiano.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import code.alimiracle.sunpiano.music.KeyboardLayout
import code.alimiracle.sunpiano.music.Notes
import code.alimiracle.sunpiano.ui.theme.Amber
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Multitouch piano keyboard. Each finger plays the key under it; with [glissando] on,
 * sliding a finger across keys plays each one in turn.
 */
@Composable
fun PianoKeyboard(
    layout: KeyboardLayout,
    pressed: Set<Int>,
    firstWhite: Float,
    visibleKeys: Int,
    showLabels: Boolean,
    solfege: Boolean,
    glissando: Boolean,
    velocityFromTouch: Boolean,
    onNoteOn: (note: Int, velocity: Int) -> Unit,
    onNoteOff: (note: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val first by rememberUpdatedState(firstWhite)
    val visible by rememberUpdatedState(visibleKeys)
    val gliss by rememberUpdatedState(glissando)
    val touchVel by rememberUpdatedState(velocityFromTouch)
    val noteOn by rememberUpdatedState(onNoteOn)
    val noteOff by rememberUpdatedState(onNoteOff)
    val active = remember { HashMap<PointerId, Int>() }
    val measurer = rememberTextMeasurer()

    // Never leave a note hanging if the keyboard leaves composition.
    DisposableEffect(Unit) {
        onDispose {
            active.values.forEach { noteOff(it) }
            active.clear()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(layout) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val w = size.width / visible.toFloat()
                        val h = size.height.toFloat()
                        for (c in event.changes) {
                            val prev = active[c.id]
                            if (c.pressed) {
                                val hit = layout.hitTest(c.position.x + first * w, c.position.y, w, h)
                                val vel = if (hit != null && touchVel) (45 + 82 * hit.depth).toInt().coerceIn(1, 127) else 100
                                if (prev == null) {
                                    if (hit != null) {
                                        active[c.id] = hit.note
                                        noteOn(hit.note, vel)
                                    }
                                } else if (hit != null && hit.note != prev && gliss) {
                                    noteOff(prev)
                                    active[c.id] = hit.note
                                    noteOn(hit.note, vel)
                                }
                                c.consume()
                            } else if (prev != null) {
                                noteOff(prev)
                                active.remove(c.id)
                                c.consume()
                            }
                        }
                    }
                }
            },
    ) {
        val w = size.width / visibleKeys
        val h = size.height
        val scroll = firstWhite * w
        val from = floor(firstWhite).toInt().coerceAtLeast(0)
        val to = ceil(firstWhite + visibleKeys).toInt().coerceAtMost(layout.whiteCount - 1)
        val labelStyle = TextStyle(fontSize = (w / 4.2f).coerceIn(9f, 16f).toSp())

        // white keys
        for (i in from..to) {
            val note = layout.whiteNotes[i]
            val x = i * w - scroll
            val down = note in pressed
            val brush = if (down) {
                Brush.verticalGradient(listOf(Color(0xFFFFE0B2), Amber), 0f, h)
            } else {
                Brush.verticalGradient(listOf(Color(0xFFFDFDFD), Color(0xFFDDE0E3)), 0f, h)
            }
            drawRoundRect(brush, Offset(x + 1f, -12f), Size(w - 2f, h + 12f - 2f), CornerRadius(8f))
            if (showLabels && (visibleKeys <= 16 || note % 12 == 0)) {
                val text = Notes.name(note, solfege, withOctave = note % 12 == 0)
                val layoutResult = measurer.measure(text, labelStyle)
                drawText(
                    layoutResult,
                    color = if (note % 12 == 0) Color(0xFF8A5A00) else Color(0xFF7A828A),
                    topLeft = Offset(x + (w - layoutResult.size.width) / 2f, h - layoutResult.size.height - 10f),
                )
            }
        }
        // black keys on top
        val bw = w * KeyboardLayout.BLACK_WIDTH
        val bh = h * KeyboardLayout.BLACK_HEIGHT
        for (i in (from - 1).coerceAtLeast(0)..to) {
            val note = layout.blackAfter(i) ?: continue
            val x = (i + 1) * w - scroll - bw / 2f
            val down = note in pressed
            val brush = if (down) {
                Brush.verticalGradient(listOf(Amber, Color(0xFFB36B00)), 0f, bh)
            } else {
                Brush.verticalGradient(listOf(Color(0xFF3A4048), Color(0xFF0B0D10)), 0f, bh)
            }
            drawRoundRect(brush, Offset(x, -12f), Size(bw, bh + 12f), CornerRadius(6f))
        }
    }
}
