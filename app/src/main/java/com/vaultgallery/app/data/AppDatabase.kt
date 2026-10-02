package com.vaultgallery.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PhotoEntity::class, TagEntity::class, PhotoTagCrossRef::class, RecentPhotoEntity::class,
        WalletEntity::class, WalletTransactionEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photos(): PhotoDao
    abstract fun tags(): TagDao
    abstract fun recent(): RecentDao
    abstract fun wallet(): WalletDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        // Future schema changes must ship a Migration here. Never use destructive fallback.
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "vault_gallery.db")
                .build().also { instance = it }
        }
    }
}
