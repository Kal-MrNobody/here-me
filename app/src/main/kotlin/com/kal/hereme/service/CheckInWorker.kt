package com.kal.hereme.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kal.hereme.core.Staleness
import com.kal.hereme.hereMe
import kotlinx.coroutines.flow.first

/**
 * The honesty check.
 *
 * The tracked balance is a number the user typed, and it drifts on every cash spend, ATM
 * withdrawal and autopay this app cannot see. Left alone it quietly stops being true, and
 * a number you have learned to distrust creates no friction at all — so once a day it
 * asks to be confirmed.
 */
class CheckInWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext.hereMe
        val snapshot = app.repository.snapshot.first()

        if (!snapshot.isOnboarded) return Result.success()

        // Don't nag on a day it has already been confirmed.
        val days = Staleness.daysSince(snapshot.lastReconciledAt, System.currentTimeMillis())
        if (days < 1L) return Result.success()

        Notifications.postCheckIn(applicationContext, snapshot.balance)
        return Result.success()
    }
}
