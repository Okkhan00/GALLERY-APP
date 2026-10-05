package com.vaultgallery.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [Index(value = ["mediaStoreId", "mediaType"], unique = true), Index("deleted"), Index("favorite"), Index("dateTaken"), Index("mediaType")]
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mediaStoreId: Long,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val dateTaken: Long,
    val favorite: Boolean = false,
    val caption: String = "",
    val edited: Boolean = false,
    val editVersion: Int = 0,
    val editParams: String? = null,
    val hash: String? = null,
    val perceptualHash: Long? = null,
    val deleted: Boolean = false,
    val trashedAt: Long? = null,
    val locked: Boolean = false,
    val unlockCostStars: Int = 0,
    val unlockCostCoins: Int = 0,
    val missing: Boolean = false,
    val lastSeen: Long = 0,
    val createdAt: Long,
    val updatedAt: Long,
    // Added in DB v2 (video support). Defaults must match MIGRATION_1_2 exactly.
    @ColumnInfo(defaultValue = "0") val mediaType: Int = MediaType.IMAGE,
    @ColumnInfo(defaultValue = "0") val durationMs: Long = 0,
    @ColumnInfo(defaultValue = "''") val bucket: String = "",
    @ColumnInfo(defaultValue = "0") val orientation: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastPositionMs: Long = 0,
) {
    @get:Ignore val isVideo: Boolean get() = mediaType == MediaType.VIDEO
}

/** Stored as Int so queries stay trivial and the column default is 0 (= IMAGE) for every pre-video row. */
object MediaType {
    const val IMAGE = 0
    const val VIDEO = 1
}

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "photo_tags",
    primaryKeys = ["photoId", "tagId"],
    foreignKeys = [
        ForeignKey(entity = PhotoEntity::class, parentColumns = ["id"], childColumns = ["photoId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")]
)
data class PhotoTagCrossRef(val photoId: Long, val tagId: Long)

@Entity(tableName = "recent")
data class RecentPhotoEntity(@PrimaryKey val photoId: Long, val viewedAt: Long)

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = 1,
    val stars: Int = 0,
    val coins: Int = 0,
    val updatedAt: Long = 0,
)

@Entity(tableName = "wallet_tx", indices = [Index("timestamp")])
data class WalletTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val currency: String,
    val amount: Int,
    val reason: String,
    val photoId: Long?,
    val timestamp: Long,
)

data class TagCount(val id: Long, val name: String, val photoCount: Int)

data class MediaCounts(val total: Int, val photos: Int, val videos: Int)

/** One smart album = one MediaStore bucket (folder). Built from real bucket names, nothing is hard-coded. */
data class AlbumInfo(val bucket: String, val total: Int, val photos: Int, val videos: Int, val coverUri: String?)
data class TypeBytes(val photoBytes: Long, val videoBytes: Long)

/**
 * A photo/video that was moved into the private vault. The bytes live ONLY in app-private storage, encrypted
 * (see VaultCrypto); [wrappedKey] is the per-file data key sealed by the Android Keystore.
 * Added in DB v3; no defaults on purpose so the SQL in MigrationSql matches Room's expected schema exactly.
 */
@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val fileName: String,
    val thumbName: String,
    val wrappedKey: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: Int,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val dateTaken: Long,
    val hiddenAt: Long,
    val favorite: Boolean,
    val sha256: String,
) {
    @get:Ignore val isVideo: Boolean get() = mediaType == MediaType.VIDEO
}

data class VaultNames(val fileName: String, val thumbName: String)
