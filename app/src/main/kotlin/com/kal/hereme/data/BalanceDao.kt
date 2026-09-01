package com.kal.hereme.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceDao {

    @Query("SELECT COALESCE(SUM(deltaPaise), 0) FROM ledger_entries")
    fun observeTotalPaise(): Flow<Long>

    @Query("SELECT COALESCE(SUM(deltaPaise), 0) FROM ledger_entries")
    suspend fun totalPaise(): Long

    @Query("SELECT COUNT(*) FROM ledger_entries")
    suspend fun entryCount(): Int

    @Query("SELECT * FROM ledger_entries ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY createdAt DESC, id DESC LIMIT 1")
    fun observeLatest(): Flow<LedgerEntryEntity?>

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun entry(id: Long): LedgerEntryEntity?

    @Insert
    suspend fun insertEntry(entry: LedgerEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: LedgerEntryEntity)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAllEntries()

    @Query("SELECT * FROM balance_meta WHERE id = 1")
    fun observeMeta(): Flow<BalanceMetaEntity?>

    @Query("SELECT * FROM balance_meta WHERE id = 1")
    suspend fun meta(): BalanceMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: BalanceMetaEntity)

    @Query("UPDATE balance_meta SET lastReconciledAt = :at WHERE id = 1")
    suspend fun touchReconciledAt(at: Long)
}
