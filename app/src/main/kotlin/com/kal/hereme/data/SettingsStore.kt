package com.kal.hereme.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kal.hereme.core.Paise
import com.kal.hereme.core.PaymentApps
import com.kal.hereme.core.Staleness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class AppSettings(
    val bubbleEnabled: Boolean = false,
    val bubbleX: Int = -1,
    val bubbleY: Int = -1,
    /** 0 = small, 1 = medium, 2 = large. */
    val bubbleSizeStep: Int = 1,
    val watchedPackages: Set<String> = PaymentApps.DEFAULT_WATCHED,
    val hapticsEnabled: Boolean = true,
    val lowBalanceThreshold: Paise = Paise.ofRupees(500),
    val checkInHour: Int = 21,
    val stalenessDays: Int = Staleness.DEFAULT_THRESHOLD_DAYS,
) {
    val bubbleScale: Float
        get() = when (bubbleSizeStep) {
            0 -> 0.85f
            2 -> 1.25f
            else -> 1.0f
        }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "here_me_settings")

class SettingsStore(context: Context) {

    private val store = context.applicationContext.dataStore

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            bubbleEnabled = prefs[BUBBLE_ENABLED] ?: false,
            bubbleX = prefs[BUBBLE_X] ?: -1,
            bubbleY = prefs[BUBBLE_Y] ?: -1,
            bubbleSizeStep = prefs[BUBBLE_SIZE] ?: 1,
            watchedPackages = prefs[WATCHED] ?: PaymentApps.DEFAULT_WATCHED,
            hapticsEnabled = prefs[HAPTICS] ?: true,
            lowBalanceThreshold = Paise(prefs[LOW_BALANCE] ?: Paise.ofRupees(500).raw),
            checkInHour = prefs[CHECK_IN_HOUR] ?: 21,
            stalenessDays = prefs[STALENESS_DAYS] ?: Staleness.DEFAULT_THRESHOLD_DAYS,
        )
    }

    suspend fun setBubbleEnabled(enabled: Boolean) = edit { it[BUBBLE_ENABLED] = enabled }

    suspend fun setBubblePosition(x: Int, y: Int) = edit {
        it[BUBBLE_X] = x
        it[BUBBLE_Y] = y
    }

    suspend fun setBubbleSizeStep(step: Int) = edit { it[BUBBLE_SIZE] = step.coerceIn(0, 2) }

    suspend fun setWatchedPackages(packages: Set<String>) = edit { it[WATCHED] = packages }

    suspend fun setHapticsEnabled(enabled: Boolean) = edit { it[HAPTICS] = enabled }

    suspend fun setLowBalanceThreshold(amount: Paise) = edit { it[LOW_BALANCE] = amount.raw }

    suspend fun setCheckInHour(hour: Int) = edit { it[CHECK_IN_HOUR] = hour.coerceIn(0, 23) }

    suspend fun setStalenessDays(days: Int) = edit { it[STALENESS_DAYS] = days.coerceIn(1, 30) }

    private suspend fun edit(block: suspend (MutablePreferences) -> Unit) {
        store.edit(block)
    }

    private companion object {
        val BUBBLE_ENABLED = booleanPreferencesKey("bubble_enabled")
        val BUBBLE_X = intPreferencesKey("bubble_x")
        val BUBBLE_Y = intPreferencesKey("bubble_y")
        val BUBBLE_SIZE = intPreferencesKey("bubble_size_step")
        val WATCHED = stringSetPreferencesKey("watched_packages")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val LOW_BALANCE = longPreferencesKey("low_balance_paise")
        val CHECK_IN_HOUR = intPreferencesKey("check_in_hour")
        val STALENESS_DAYS = intPreferencesKey("staleness_days")
    }
}
