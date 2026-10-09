package com.example.sunpiano.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
)

private val Context.dataStore by preferencesDataStore("pan_settings")

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

    val flow: Flow<Settings> = context.dataStore.data.map { p -> p.toSettings() }

    suspend fun save(s: Settings) {
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
