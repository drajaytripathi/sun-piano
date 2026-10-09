package com.example.sunpiano.music

import kotlin.math.pow

/** Note-number helpers. Notes use MIDI numbering (60 = middle C). */
object Notes {
    const val LOWEST = 21   // A0
    const val HIGHEST = 108 // C8
    const val MIDDLE_C = 60

    private val LETTERS = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
    private val SOLFEGE = arrayOf("Do", "Do♯", "Re", "Re♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")

    fun isBlack(note: Int): Boolean = when (((note % 12) + 12) % 12) {
        1, 3, 6, 8, 10 -> true
        else -> false
    }

    fun name(note: Int, solfege: Boolean = false, withOctave: Boolean = true): String {
        val pc = ((note % 12) + 12) % 12
        val base = if (solfege) SOLFEGE[pc] else LETTERS[pc]
        return if (withOctave) base + (Math.floorDiv(note, 12) - 1) else base
    }

    fun frequency(note: Int, a4: Double = 440.0): Double = a4 * 2.0.pow((note - 69) / 12.0)
}

/** Geometry of the keyboard in "white key" units, independent of pixel size. */
class KeyboardLayout(val low: Int = Notes.LOWEST, val high: Int = Notes.HIGHEST) {
    val whiteNotes: IntArray = (low..high).filter { !Notes.isBlack(it) }.toIntArray()
    val whiteCount: Int get() = whiteNotes.size

    fun whiteIndexOf(note: Int): Int {
        val i = whiteNotes.indexOfFirst { it >= note }
        return if (i < 0) whiteCount - 1 else i
    }

    /** Black note sitting on the right edge of white key [i], or null. */
    fun blackAfter(i: Int): Int? {
        if (i !in whiteNotes.indices) return null
        val n = whiteNotes[i] + 1
        return if (n <= high && Notes.isBlack(n)) n else null
    }

    /**
     * Returns the note under a point. [x] is in content coordinates (scroll already added),
     * [y] in 0..height. Black keys take priority in the upper part of the keyboard.
     */
    fun hitTest(x: Float, y: Float, whiteW: Float, height: Float): Hit? {
        if (x < 0f || y < 0f || y > height || whiteW <= 0f) return null
        val i = (x / whiteW).toInt()
        if (i !in whiteNotes.indices) return null
        val blackH = height * BLACK_HEIGHT
        if (y <= blackH) {
            val half = whiteW * BLACK_WIDTH / 2f
            blackAfter(i)?.let { n ->
                if (x >= (i + 1) * whiteW - half) return Hit(n, y / blackH)
            }
            blackAfter(i - 1)?.let { n ->
                if (x <= i * whiteW + half) return Hit(n, y / blackH)
            }
        }
        return Hit(whiteNotes[i], y / height)
    }

    /** [depth] is 0 at the top of the key and 1 at the bottom. */
    data class Hit(val note: Int, val depth: Float)

    companion object {
        const val BLACK_HEIGHT = 0.62f
        const val BLACK_WIDTH = 0.62f
    }
}
