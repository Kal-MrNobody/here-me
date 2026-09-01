package com.kal.hereme.data

import androidx.room.withTransaction
import com.kal.hereme.core.EntryType
import com.kal.hereme.core.Ledger
import com.kal.hereme.core.Paise
import com.kal.hereme.core.Staleness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** What a recorded change did, which is what the drain animation needs to play it. */
data class Recorded(
    val entryId: Long,
    val delta: Paise,
    val balanceBefore: Paise,
    val balanceAfter: Paise,
)

data class BalanceSnapshot(
    val balance: Paise,
    val lastReconciledAt: Long,
    val isOnboarded: Boolean,
) {
    fun isStale(now: Long, thresholdDays: Int = Staleness.DEFAULT_THRESHOLD_DAYS): Boolean =
        isOnboarded && Staleness.isStale(lastReconciledAt, now, thresholdDays)
}

class BalanceRepository(
    private val database: HereMeDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) {

    private val dao = database.balanceDao()

    val balance: Flow<Paise> = dao.observeTotalPaise().map(::Paise)

    val latestEntry: Flow<LedgerEntryEntity?> = dao.observeLatest()

    val snapshot: Flow<BalanceSnapshot> =
        combine(dao.observeTotalPaise(), dao.observeMeta()) { total, meta ->
            BalanceSnapshot(
                balance = Paise(total),
                lastReconciledAt = meta?.lastReconciledAt ?: 0L,
                isOnboarded = meta != null,
            )
        }

    fun recentEntries(limit: Int = 300): Flow<List<LedgerEntryEntity>> = dao.observeRecent(limit)

    suspend fun currentBalance(): Paise = Paise(dao.totalPaise())

    suspend fun isOnboarded(): Boolean = dao.meta() != null

    /**
     * Onboarding, and the "start over" button. Deliberately destructive: an opening
     * balance only means anything if it is the first thing in the ledger.
     */
    suspend fun setOpeningBalance(amount: Paise) {
        val timestamp = now()
        database.withTransaction {
            dao.deleteAllEntries()
            dao.insertEntry(
                LedgerEntryEntity(
                    deltaPaise = amount.raw,
                    type = EntryType.OPENING,
                    createdAt = timestamp,
                ),
            )
            dao.upsertMeta(
                BalanceMetaEntity(
                    lastReconciledAt = timestamp,
                    onboardedAt = dao.meta()?.onboardedAt ?: timestamp,
                ),
            )
        }
    }

    suspend fun record(
        delta: Paise,
        type: EntryType,
        counterparty: String? = null,
        sourcePackage: String? = null,
        rawText: String? = null,
    ): Recorded = database.withTransaction {
        val before = Paise(dao.totalPaise())
        val id = dao.insertEntry(
            LedgerEntryEntity(
                deltaPaise = delta.raw,
                type = type,
                counterparty = counterparty,
                sourcePackage = sourcePackage,
                rawText = rawText,
                createdAt = now(),
            ),
        )
        Recorded(entryId = id, delta = delta, balanceBefore = before, balanceAfter = before + delta)
    }

    /** Undo is just removal — the balance is the sum of what is left. */
    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)

    suspend fun entry(id: Long): LedgerEntryEntity? = dao.entry(id)

    suspend fun updateEntryAmount(id: Long, delta: Paise) {
        val existing = dao.entry(id) ?: return
        dao.updateEntry(existing.copy(deltaPaise = delta.raw))
    }

    /**
     * The user says what they really have. The gap is written into the ledger as its own
     * entry rather than being quietly absorbed, so the drift stays visible in history.
     */
    suspend fun reconcileTo(actual: Paise): Paise = database.withTransaction {
        val current = Paise(dao.totalPaise())
        val delta = Ledger.reconciliationDelta(current, actual)

        if (!delta.isZero) {
            dao.insertEntry(
                LedgerEntryEntity(
                    deltaPaise = delta.raw,
                    type = EntryType.RECONCILE,
                    createdAt = now(),
                ),
            )
        }
        markConfirmed()
        delta
    }

    /** "Yes, that number is right" — resets staleness without changing the balance. */
    suspend fun markConfirmed() {
        val timestamp = now()
        val existing = dao.meta()
        if (existing == null) {
            dao.upsertMeta(BalanceMetaEntity(lastReconciledAt = timestamp, onboardedAt = timestamp))
        } else {
            dao.touchReconciledAt(timestamp)
        }
    }
}
