package com.kal.hereme.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.kal.hereme.core.EntryType
import com.kal.hereme.core.Paise

/**
 * Every change to the balance is a row here, and the balance itself is nothing more than
 * the sum of these rows. Keeping a separate cached total is what would let the number on
 * screen drift away from its own history, so there isn't one.
 */
@Entity(tableName = "ledger_entries")
data class LedgerEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Negative for money leaving, positive for money arriving. */
    val deltaPaise: Long,
    val type: EntryType,
    val counterparty: String? = null,
    val sourcePackage: String? = null,
    /** The notification text this was read from, kept so a wrong parse can be understood. */
    val rawText: String? = null,
    val createdAt: Long,
) {
    val delta: Paise get() = Paise(deltaPaise)
}

@Entity(tableName = "balance_meta")
data class BalanceMetaEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    /** When the user last told us the number was actually right. */
    val lastReconciledAt: Long,
    val onboardedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 1
    }
}

class EntryTypeConverter {

    @TypeConverter
    fun toName(type: EntryType): String = type.name

    @TypeConverter
    fun fromName(name: String): EntryType =
        runCatching { EntryType.valueOf(name) }.getOrDefault(EntryType.MANUAL)
}
