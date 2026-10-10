package code.alimiracle.sunpiano.music

/**
 * Who is holding the sustain pedal. The on-screen Sustain button latches it, while Space, a
 * MIDI pedal and a playing recording hold it only while pressed. The pedal is down while any of them holds it, so letting
 * go of one never lifts a pedal another one still holds.
 */
class SustainSources {
    enum class Source { BUTTON, SPACE, MIDI, PLAYBACK }

    private val held = HashSet<Source>()

    /** True while any source holds the pedal. */
    val isDown: Boolean get() = held.isNotEmpty()

    fun isHeld(source: Source): Boolean = source in held

    /** Sets [source] and returns whether the pedal is down afterwards. */
    fun set(source: Source, down: Boolean): Boolean {
        if (down) held += source else held -= source
        return isDown
    }
}
