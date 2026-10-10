package code.alimiracle.sunpiano.audio

/**
 * A synth patch. Time values are exponential time constants in seconds.
 *
 * @param harmonics amplitudes of partials 1..n for the wavetable oscillator
 * @param sustain level the decay stage settles to (0 = the note dies away even while held)
 * @param brightStart/brightEnd one-pole low-pass openness (1 = fully open) at note start/after [brightTime]
 * @param fmRatio/fmIndex/fmDecay optional phase-modulation (electric piano, mallets)
 * @param detuneCents a second, detuned oscillator for chorus/richness
 * @param vibratoHz/vibratoCents pitch modulation
 */
class Instrument(
    val id: String,
    val name: String,
    val harmonics: FloatArray,
    val attack: Float,
    val decay: Float,
    val sustain: Float,
    val release: Float,
    val brightStart: Float = 1f,
    val brightEnd: Float = 1f,
    val brightTime: Float = 1f,
    val fmRatio: Float = 0f,
    val fmIndex: Float = 0f,
    val fmDecay: Float = 0.5f,
    val detuneCents: Float = 0f,
    val vibratoHz: Float = 0f,
    val vibratoCents: Float = 0f,
)

object Instruments {
    private fun saw(n: Int) = FloatArray(n) { 1f / (it + 1) }

    private val pianoHarmonics = floatArrayOf(1f, .6f, .35f, .25f, .12f, .08f, .05f, .03f, .02f, .01f)

    val GRAND = Instrument(
        "grand", "Piano", pianoHarmonics,
        attack = .003f, decay = 2.2f, sustain = 0f, release = .12f,
        brightStart = 1f, brightEnd = .16f, brightTime = 1.6f, detuneCents = 2.5f,
    )
    val ELECTRIC = Instrument(
        "electric", "Electric Piano", floatArrayOf(1f, .08f, .03f),
        attack = .004f, decay = 2.0f, sustain = 0f, release = .2f,
        brightStart = 1f, brightEnd = .5f, brightTime = 2f,
        fmRatio = 1f, fmIndex = 2.4f, fmDecay = .55f, detuneCents = 1.5f,
    )
    val HONKY = Instrument(
        "honky", "Honky-Tonk", pianoHarmonics,
        attack = .003f, decay = 1.6f, sustain = 0f, release = .1f,
        brightStart = 1f, brightEnd = .3f, brightTime = 1.2f, detuneCents = 14f,
    )
    val ORGAN = Instrument(
        "organ", "Organ", floatArrayOf(1f, .75f, .55f, .0f, .4f, 0f, 0f, .25f),
        attack = .008f, decay = .1f, sustain = 1f, release = .04f,
        detuneCents = 1f, vibratoHz = 5.8f, vibratoCents = 4f,
    )
    val STRINGS = Instrument(
        "strings", "Strings", saw(14),
        attack = .35f, decay = .3f, sustain = 1f, release = .35f,
        brightStart = .25f, brightEnd = .8f, brightTime = .7f,
        detuneCents = 9f, vibratoHz = 5f, vibratoCents = 6f,
    )
    val HARPSICHORD = Instrument(
        "harpsichord", "Harpsichord", floatArrayOf(1f, .9f, .8f, .7f, .6f, .5f, .4f, .3f, .25f, .2f),
        attack = .001f, decay = .7f, sustain = 0f, release = .05f,
        brightStart = 1f, brightEnd = .3f, brightTime = .8f, detuneCents = 4f,
    )
    val MARIMBA = Instrument(
        "marimba", "Marimba", floatArrayOf(1f),
        attack = .001f, decay = .45f, sustain = 0f, release = .1f,
        fmRatio = 4f, fmIndex = 1.6f, fmDecay = .06f,
    )
    val LEAD = Instrument(
        "lead", "Synth Lead", saw(20),
        attack = .01f, decay = .3f, sustain = .8f, release = .12f,
        brightStart = 1f, brightEnd = .45f, brightTime = .5f,
        detuneCents = 7f, vibratoHz = 5.5f, vibratoCents = 5f,
    )

    val ALL = listOf(GRAND, ELECTRIC, HONKY, ORGAN, STRINGS, HARPSICHORD, MARIMBA, LEAD)

    fun byId(id: String): Instrument = ALL.firstOrNull { it.id == id } ?: GRAND
}
