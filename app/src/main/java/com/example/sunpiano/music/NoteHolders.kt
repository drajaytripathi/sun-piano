package com.example.sunpiano.music

/**
 * Who is holding which note. A note can be held by several things at once: two fingers on one
 * key, two computer keys for the same note, or a recording playing back a note you also play
 * live. Its sound must stop only when the last of them lets go.
 */
class NoteHolders {
    /** Keyboard key -> sounding notes of its current presses (one per finger, key or device). */
    private val presses = HashMap<Int, ArrayDeque<Int>>()
    private val playback = HashSet<Int>()
    private val holders = HashMap<Int, Int>()

    /** Keys currently pressed by live input. */
    val keys: Set<Int> get() = presses.keys

    /** Notes currently held by playback. */
    val playbackNotes: Set<Int> get() = playback

    /** Sounding notes currently held by live input. */
    fun liveSounding(): Set<Int> = presses.values.flatMapTo(HashSet()) { it }

    /** [key] was pressed and sounds [sounding] (the key plus the transpose at press time). */
    fun press(key: Int, sounding: Int) {
        presses.getOrPut(key) { ArrayDeque() }.addLast(sounding)
        hold(sounding)
    }

    /**
     * [key] was released. Returns the sounding note to stop, or null when something else still
     * holds it or the key wasn't pressed.
     */
    fun release(key: Int): Int? {
        val queue = presses[key] ?: return null
        val sounding = queue.removeFirst()
        if (queue.isEmpty()) presses.remove(key)
        return letGo(sounding)
    }

    /** Playback started [note]. Playback counts as one holder however often it re-strikes. */
    fun playbackPress(note: Int) {
        if (playback.add(note)) hold(note)
    }

    /** Playback ended [note]. Returns the note to stop, or null when something else still holds it. */
    fun playbackRelease(note: Int): Int? = if (playback.remove(note)) letGo(note) else null

    private fun hold(note: Int) {
        holders[note] = (holders[note] ?: 0) + 1
    }

    private fun letGo(note: Int): Int? {
        val n = holders[note] ?: return null
        if (n > 1) {
            holders[note] = n - 1
            return null
        }
        holders.remove(note)
        return note
    }
}
