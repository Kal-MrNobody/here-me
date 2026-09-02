package com.kal.hereme.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kal.hereme.core.EntryType
import com.kal.hereme.core.Money
import com.kal.hereme.core.Paise
import com.kal.hereme.data.AppSettings
import com.kal.hereme.data.BalanceRepository
import com.kal.hereme.data.BalanceSnapshot
import com.kal.hereme.data.LedgerEntryEntity
import com.kal.hereme.data.NotificationLogEntity
import com.kal.hereme.data.SettingsStore
import com.kal.hereme.hereMe
import com.kal.hereme.overlay.OverlayService
import com.kal.hereme.service.CheckInScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val repository: BalanceRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    val snapshot: StateFlow<BalanceSnapshot?> = repository.snapshot
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val entries: StateFlow<List<LedgerEntryEntity>> = repository.recentEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val notificationLog: StateFlow<List<NotificationLogEntity>> = repository.notificationLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Typed rupees to paise, or null if what they typed isn't a number. */
    fun parseTyped(input: String): Paise? = Money.parseAmount(input.trim().removePrefix("₹").trim())

    fun setOpeningBalance(amount: Paise) = viewModelScope.launch {
        repository.setOpeningBalance(amount)
    }

    fun reconcileTo(amount: Paise) = viewModelScope.launch {
        repository.reconcileTo(amount)
    }

    fun confirmStillCorrect() = viewModelScope.launch {
        repository.markConfirmed()
    }

    fun addManualEntry(amount: Paise, isSpend: Boolean, note: String?, cash: Boolean) =
        viewModelScope.launch {
            repository.record(
                delta = if (isSpend) -amount else amount,
                type = if (cash) EntryType.CASH else EntryType.MANUAL,
                counterparty = note?.takeIf { it.isNotBlank() },
            )
        }

    fun deleteEntry(id: Long) = viewModelScope.launch { repository.deleteEntry(id) }

    fun setBubbleEnabled(context: Context, enabled: Boolean) = viewModelScope.launch {
        settingsStore.setBubbleEnabled(enabled)
        if (enabled) OverlayService.start(context) else OverlayService.stop(context)
    }

    fun setBubbleSizeStep(step: Int) = viewModelScope.launch {
        settingsStore.setBubbleSizeStep(step)
    }

    fun setHaptics(enabled: Boolean) = viewModelScope.launch {
        settingsStore.setHapticsEnabled(enabled)
    }

    fun setLowBalanceThreshold(amount: Paise) = viewModelScope.launch {
        settingsStore.setLowBalanceThreshold(amount)
    }

    fun setStalenessDays(days: Int) = viewModelScope.launch {
        settingsStore.setStalenessDays(days)
    }

    fun setCheckInHour(context: Context, hour: Int) = viewModelScope.launch {
        settingsStore.setCheckInHour(hour)
        CheckInScheduler.schedule(context, hour)
    }

    fun setWatchedPackages(packages: Set<String>) = viewModelScope.launch {
        settingsStore.setWatchedPackages(packages)
    }

    /** From the debug screen: start watching whatever app posted a payment notification. */
    fun watchPackage(packageName: String) = viewModelScope.launch {
        settingsStore.setWatchedPackages(settings.value.watchedPackages + packageName)
    }

    fun clearNotificationLog() = viewModelScope.launch { repository.clearNotificationLog() }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.hereMe
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MainViewModel(app.repository, app.settings) as T
            }
        }
    }
}
