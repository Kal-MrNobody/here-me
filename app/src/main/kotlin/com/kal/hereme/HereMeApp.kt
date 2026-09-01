package com.kal.hereme

import android.app.Application
import android.content.Context
import com.kal.hereme.data.BalanceRepository
import com.kal.hereme.data.HereMeDatabase
import com.kal.hereme.data.SettingsStore
import com.kal.hereme.service.CheckInScheduler
import com.kal.hereme.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One user, one device, no network. A plain service locator is the honest amount of
 * wiring for that, and it keeps the overlay service's startup path short.
 */
class HereMeApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: HereMeDatabase by lazy { HereMeDatabase.create(this) }

    val repository: BalanceRepository by lazy { BalanceRepository(database) }

    val settings: SettingsStore by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)

        applicationScope.launch {
            val hour = settings.settings.first().checkInHour
            CheckInScheduler.schedule(this@HereMeApp, hour)
        }
    }
}

val Context.hereMe: HereMeApp
    get() = applicationContext as HereMeApp
