package com.vaultgallery.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PhotoEntity::class, TagEntity::class, PhotoTagCrossRef::class, RecentPhotoEntity::class,
        WalletEntity::class, WalletTransactionEntity::class, VaultItemEntity::class,
    ],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photos(): PhotoDao
    abstract fun tags(): TagDao
    abstract fun recent(): RecentDao
    abstract fun wallet(): WalletDao
    abstract fun vault(): VaultDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v1 -> v2: video support. Purely additive: new columns get defaults (every existing row is an IMAGE),
         * and the unique index widens from (mediaStoreId) to (mediaStoreId, mediaType). No row is touched or dropped,
         * so favorites, tags, trash, locks, recents and wallet all survive.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V1_TO_V2.forEach { db.execSQL(it) }
            }
        }

        /** v2 -> v3: private vault table only. No existing row is touched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MigrationSql.V2_TO_V3.forEach { db.execSQL(it) }
            }
        }

        // Never use destructive fallback: every schema change must ship a Migration.
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "vault_gallery.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build().also { instance = it }
        }
    }
}
