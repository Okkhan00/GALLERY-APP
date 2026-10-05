package com.vaultgallery.app.data

/** Plain SQL for schema migrations, kept free of Room/Android types so it can be unit-tested on the JVM. */
object MigrationSql {
    /** v1 -> v2 (video support). Additive only: nothing is dropped except the old narrower unique index. */
    val V1_TO_V2 = listOf(
        "ALTER TABLE photos ADD COLUMN mediaType INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE photos ADD COLUMN durationMs INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE photos ADD COLUMN bucket TEXT NOT NULL DEFAULT ''",
        "ALTER TABLE photos ADD COLUMN orientation INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE photos ADD COLUMN lastPositionMs INTEGER NOT NULL DEFAULT 0",
        "DROP INDEX IF EXISTS index_photos_mediaStoreId",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_photos_mediaStoreId_mediaType ON photos (mediaStoreId, mediaType)",
        "CREATE INDEX IF NOT EXISTS index_photos_mediaType ON photos (mediaType)",
    )

    /** v2 -> v3 (private vault). Adds one new table; every existing table is untouched. */
    val V2_TO_V3 = listOf(
        "CREATE TABLE IF NOT EXISTS `vault_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`fileName` TEXT NOT NULL, `thumbName` TEXT NOT NULL, `wrappedKey` TEXT NOT NULL, `displayName` TEXT NOT NULL, " +
            "`mimeType` TEXT NOT NULL, `mediaType` INTEGER NOT NULL, `size` INTEGER NOT NULL, `width` INTEGER NOT NULL, " +
            "`height` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `dateTaken` INTEGER NOT NULL, `hiddenAt` INTEGER NOT NULL, " +
            "`favorite` INTEGER NOT NULL, `sha256` TEXT NOT NULL)",
    )
}
