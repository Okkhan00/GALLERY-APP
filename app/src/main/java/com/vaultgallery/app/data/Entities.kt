package com.vaultgallery.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "photos",
    indices = [Index(value = ["mediaStoreId"], unique = true), Index("deleted"), Index("favorite"), Index("dateTaken")]
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
)

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
