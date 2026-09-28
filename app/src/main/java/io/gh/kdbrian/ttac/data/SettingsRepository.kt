package io.gh.kdbrian.ttac.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.gh.kdbrian.ttac.game.Difficulty
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { SYSTEM("Auto"), DARK("Dark"), LIGHT("Light") }

enum class MarkStyle(val label: String) { NEON("Neon"), SOLID("Solid"), SKETCH("Sketch") }

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val markStyle: MarkStyle = MarkStyle.NEON,
    val xColor: Long = 0xFFFF3D7F,
    val oColor: Long = 0xFF33E1FF,
    /** Stroke width as a fraction of the cell size. */
    val strokeWidth: Float = 0.10f,
    /** Animation speed multiplier (higher is faster). */
    val animSpeed: Float = 1f,
    val boardSize: Int = 3,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val threatHeat: Boolean = true,
    val heatmapOverlay: Boolean = false,
    val soloProfileId: String? = null,
    val lanProfileId: String? = null,
    val lastXProfileId: String? = null,
    val lastOProfileId: String? = null,
    /** This device's persistent LAN alias — how other players see and remember you. */
    val lanAlias: String? = null,
)

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {

    private val store = context.applicationContext.settingsStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val markStyle = stringPreferencesKey("mark_style")
        val xColor = longPreferencesKey("x_color")
        val oColor = longPreferencesKey("o_color")
        val strokeWidth = floatPreferencesKey("stroke_width")
        val animSpeed = floatPreferencesKey("anim_speed")
        val boardSize = intPreferencesKey("board_size")
        val difficulty = stringPreferencesKey("difficulty")
        val sound = booleanPreferencesKey("sound")
        val haptics = booleanPreferencesKey("haptics")
        val threatHeat = booleanPreferencesKey("threat_heat")
        val heatmapOverlay = booleanPreferencesKey("heatmap_overlay")
        val soloProfile = stringPreferencesKey("solo_profile")
        val lanProfile = stringPreferencesKey("lan_profile")
        val lastX = stringPreferencesKey("last_x")
        val lastO = stringPreferencesKey("last_o")
        val lanAlias = stringPreferencesKey("lan_alias")
    }

    val settings: Flow<Settings> = store.data.map { it.toSettings() }

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val s = transform(p.toSettings())
            p[Keys.theme] = s.theme.name
            p[Keys.markStyle] = s.markStyle.name
            p[Keys.xColor] = s.xColor
            p[Keys.oColor] = s.oColor
            p[Keys.strokeWidth] = s.strokeWidth
            p[Keys.animSpeed] = s.animSpeed
            p[Keys.boardSize] = s.boardSize
            p[Keys.difficulty] = s.difficulty.name
            p[Keys.sound] = s.sound
            p[Keys.haptics] = s.haptics
            p[Keys.threatHeat] = s.threatHeat
            p[Keys.heatmapOverlay] = s.heatmapOverlay
            s.soloProfileId?.let { p[Keys.soloProfile] = it } ?: p.remove(Keys.soloProfile)
            s.lanProfileId?.let { p[Keys.lanProfile] = it } ?: p.remove(Keys.lanProfile)
            s.lastXProfileId?.let { p[Keys.lastX] = it } ?: p.remove(Keys.lastX)
            s.lastOProfileId?.let { p[Keys.lastO] = it } ?: p.remove(Keys.lastO)
            s.lanAlias?.let { p[Keys.lanAlias] = it } ?: p.remove(Keys.lanAlias)
        }
    }

    private fun Preferences.toSettings(): Settings {
        val d = Settings()
        return Settings(
            theme = enumOr(this[Keys.theme], d.theme),
            markStyle = enumOr(this[Keys.markStyle], d.markStyle),
            xColor = this[Keys.xColor] ?: d.xColor,
            oColor = this[Keys.oColor] ?: d.oColor,
            strokeWidth = this[Keys.strokeWidth] ?: d.strokeWidth,
            animSpeed = this[Keys.animSpeed] ?: d.animSpeed,
            boardSize = this[Keys.boardSize] ?: d.boardSize,
            difficulty = enumOr(this[Keys.difficulty], d.difficulty),
            sound = this[Keys.sound] ?: d.sound,
            haptics = this[Keys.haptics] ?: d.haptics,
            threatHeat = this[Keys.threatHeat] ?: d.threatHeat,
            heatmapOverlay = this[Keys.heatmapOverlay] ?: d.heatmapOverlay,
            soloProfileId = this[Keys.soloProfile],
            lanProfileId = this[Keys.lanProfile],
            lastXProfileId = this[Keys.lastX],
            lastOProfileId = this[Keys.lastO],
            lanAlias = this[Keys.lanAlias],
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
