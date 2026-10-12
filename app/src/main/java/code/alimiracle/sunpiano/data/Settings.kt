package code.alimiracle.sunpiano.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

data class Settings(
    val instrumentId: String = "grand",
    val volume: Float = 0.8f,
    val reverb: Float = 0.25f,
    val transpose: Int = 0,
    val tuning: Int = 440,
    val visibleKeys: Int = 14,
    val showLabels: Boolean = true,
    val solfege: Boolean = false,
    val velocityFromTouch: Boolean = true,
    val glissando: Boolean = true,
    val keepScreenOn: Boolean = true,
    val bpm: Int = 100,
    val beatsPerBar: Int = 4,
    val metronomeVolume: Float = 0.6f,
) {
    /** All settings, for saving with a recording ([fromJson] reads it back). */
    fun toJson(): JSONObject = JSONObject().apply {
        put("instrument", instrumentId)
        put("volume", exact(volume))
        put("reverb", exact(reverb))
        put("transpose", transpose)
        put("tuning", tuning)
        put("visibleKeys", visibleKeys)
        put("showLabels", showLabels)
        put("solfege", solfege)
        put("velocityFromTouch", velocityFromTouch)
        put("glissando", glissando)
        put("keepScreenOn", keepScreenOn)
        put("bpm", bpm)
        put("beatsPerBar", beatsPerBar)
        put("metronomeVolume", exact(metronomeVolume))
    }

    /** 0.8f as 0.8, not 0.800000011920929 (a float widened to a double). */
    private fun exact(f: Float): Double = f.toString().toDouble()

    companion object {
        /** Reads [toJson]'s output; anything missing (e.g. a setting added later) gets its default. */
        fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            return Settings(
                instrumentId = o.optString("instrument", d.instrumentId),
                volume = o.optDouble("volume", d.volume.toDouble()).toFloat(),
                reverb = o.optDouble("reverb", d.reverb.toDouble()).toFloat(),
                transpose = o.optInt("transpose", d.transpose),
                tuning = o.optInt("tuning", d.tuning),
                visibleKeys = o.optInt("visibleKeys", d.visibleKeys),
                showLabels = o.optBoolean("showLabels", d.showLabels),
                solfege = o.optBoolean("solfege", d.solfege),
                velocityFromTouch = o.optBoolean("velocityFromTouch", d.velocityFromTouch),
                glissando = o.optBoolean("glissando", d.glissando),
                keepScreenOn = o.optBoolean("keepScreenOn", d.keepScreenOn),
                bpm = o.optInt("bpm", d.bpm),
                beatsPerBar = o.optInt("beatsPerBar", d.beatsPerBar),
                metronomeVolume = o.optDouble("metronomeVolume", d.metronomeVolume.toDouble()).toFloat(),
            )
        }
    }
}

// DataStore reads the file before every write too, so without this a corrupt file would make every
// save fail: it's replaced by empty settings (the defaults) and saving works again.
private val Context.dataStore by preferencesDataStore(
    "pan_settings",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

class SettingsRepository(private val context: Context) {
    private object K {
        val instrument = stringPreferencesKey("instrument")
        val volume = floatPreferencesKey("volume")
        val reverb = floatPreferencesKey("reverb")
        val transpose = intPreferencesKey("transpose")
        val tuning = intPreferencesKey("tuning")
        val visibleKeys = intPreferencesKey("visible_keys")
        val showLabels = booleanPreferencesKey("show_labels")
        val solfege = booleanPreferencesKey("solfege")
        val velocity = booleanPreferencesKey("velocity_touch")
        val glissando = booleanPreferencesKey("glissando")
        val keepOn = booleanPreferencesKey("keep_screen_on")
        val bpm = intPreferencesKey("bpm")
        val beats = intPreferencesKey("beats")
        val metroVol = floatPreferencesKey("metro_volume")
    }

    // A file that can't be read falls back to the defaults instead of failing every launch (a
    // corrupt one is replaced by the corruptionHandler above); anything else is a bug and must surface.
    val flow: Flow<Settings> = context.dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e("SunPiano", "couldn't read settings, using defaults", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { p -> p.toSettings() }

    suspend fun save(s: Settings) {
        try {
            context.dataStore.edit {
                it[K.instrument] = s.instrumentId
                it[K.volume] = s.volume
                it[K.reverb] = s.reverb
                it[K.transpose] = s.transpose
                it[K.tuning] = s.tuning
                it[K.visibleKeys] = s.visibleKeys
                it[K.showLabels] = s.showLabels
                it[K.solfege] = s.solfege
                it[K.velocity] = s.velocityFromTouch
                it[K.glissando] = s.glissando
                it[K.keepOn] = s.keepScreenOn
                it[K.bpm] = s.bpm
                it[K.beats] = s.beatsPerBar
                it[K.metroVol] = s.metronomeVolume
            }
        } catch (e: IOException) {
            // a full disk and the like: the change is lost, but the app must live on
            Log.e("SunPiano", "couldn't save settings", e)
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            instrumentId = this[K.instrument] ?: d.instrumentId,
            volume = this[K.volume] ?: d.volume,
            reverb = this[K.reverb] ?: d.reverb,
            transpose = this[K.transpose] ?: d.transpose,
            tuning = this[K.tuning] ?: d.tuning,
            visibleKeys = this[K.visibleKeys] ?: d.visibleKeys,
            showLabels = this[K.showLabels] ?: d.showLabels,
            solfege = this[K.solfege] ?: d.solfege,
            velocityFromTouch = this[K.velocity] ?: d.velocityFromTouch,
            glissando = this[K.glissando] ?: d.glissando,
            keepScreenOn = this[K.keepOn] ?: d.keepScreenOn,
            bpm = this[K.bpm] ?: d.bpm,
            beatsPerBar = this[K.beats] ?: d.beatsPerBar,
            metronomeVolume = this[K.metroVol] ?: d.metronomeVolume,
        )
    }
}
