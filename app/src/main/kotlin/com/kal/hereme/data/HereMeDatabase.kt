package com.kal.hereme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LedgerEntryEntity::class, BalanceMetaEntity::class, NotificationLogEntity::class],
    version = 2,
    exportSchema = true,
)
@TypeConverters(EntryTypeConverter::class)
abstract class HereMeDatabase : RoomDatabase() {

    abstract fun balanceDao(): BalanceDao

    companion object {
        fun create(context: Context): HereMeDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                HereMeDatabase::class.java,
                "here-me.db",
            )
                // The only schema change so far just adds the debug notification_log table.
                // The ledger is disposable test data at this stage, so rather than risk a
                // hand-written migration that must match Room's expected schema exactly, we
                // let a version bump recreate the database. Cost: the opening balance is
                // re-entered once. Revisit before this app holds anything worth keeping.
                .fallbackToDestructiveMigration()
                .build()
    }
}
