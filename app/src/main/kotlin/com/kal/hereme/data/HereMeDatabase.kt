package com.kal.hereme.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [LedgerEntryEntity::class, BalanceMetaEntity::class],
    version = 1,
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
            ).build()
    }
}
