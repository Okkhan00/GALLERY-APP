package com.vaultgallery.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    // Search covers file name, caption, folder and tag names; media type, tag and favorites filters are optional.
    // :type = -1 all, 0 photos, 1 videos. sort: 0 newest, 1 oldest, 2 name A-Z, 3 largest, 4 name Z-A, 5 longest.
    @Query(
        """
        SELECT * FROM photos p
        WHERE p.deleted = 0 AND p.missing = 0
          AND (:type < 0 OR p.mediaType = :type)
          AND (:fav = 0 OR p.favorite = 1)
          AND (:tagId < 0 OR EXISTS (SELECT 1 FROM photo_tags x WHERE x.photoId = p.id AND x.tagId = :tagId))
          AND (:q = '' OR p.displayName LIKE '%' || :q || '%' OR p.caption LIKE '%' || :q || '%'
               OR p.bucket LIKE '%' || :q || '%'
               OR EXISTS (SELECT 1 FROM photo_tags x JOIN tags t ON t.id = x.tagId
                          WHERE x.photoId = p.id AND t.name LIKE '%' || :q || '%'))
        ORDER BY CASE WHEN :sort = 1 THEN p.dateTaken END ASC,
                 CASE WHEN :sort = 2 THEN p.displayName END COLLATE NOCASE ASC,
                 CASE WHEN :sort = 3 THEN p.size END DESC,
                 CASE WHEN :sort = 4 THEN p.displayName END COLLATE NOCASE DESC,
                 CASE WHEN :sort = 5 THEN p.durationMs END DESC,
                 p.dateTaken DESC
        """
    )
    fun observe(q: String, type: Int, fav: Int, tagId: Long, sort: Int): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE deleted = 1 AND missing = 0 ORDER BY trashedAt DESC")
    fun observeTrash(): Flow<List<PhotoEntity>>

    @Query("SELECT COALESCE(SUM(size), 0) FROM photos WHERE deleted = 0 AND missing = 0")
    fun observeBytes(): Flow<Long>

    // Counts always come from real rows, never hard-coded.
    @Query(
        """
        SELECT COUNT(*) AS total,
               COALESCE(SUM(CASE WHEN mediaType = 0 THEN 1 ELSE 0 END), 0) AS photos,
               COALESCE(SUM(CASE WHEN mediaType = 1 THEN 1 ELSE 0 END), 0) AS videos
        FROM photos WHERE deleted = 0 AND missing = 0
        """
    )
    fun observeCounts(): Flow<MediaCounts>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN mediaType = 0 THEN size ELSE 0 END), 0) AS photoBytes,
               COALESCE(SUM(CASE WHEN mediaType = 1 THEN size ELSE 0 END), 0) AS videoBytes
        FROM photos WHERE deleted = 0 AND missing = 0
        """
    )
    fun observeTypeBytes(): Flow<TypeBytes>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun get(id: Long): PhotoEntity?

    @Query("SELECT COUNT(*) FROM photos WHERE deleted = 0 AND missing = 0 AND locked = 0 AND mediaType = 0")
    suspend fun visibleUnlockedCount(): Int

    @Query("SELECT * FROM photos WHERE deleted = 0 AND missing = 0 AND locked = 0 AND mediaType = 0 ORDER BY id LIMIT 1 OFFSET :offset")
    suspend fun unlockedAt(offset: Int): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PhotoEntity>): List<Long>

    @Query(
        "UPDATE photos SET displayName = :name, size = :size, width = :w, height = :h, " +
            "dateModified = :modified, durationMs = :duration, bucket = :bucket, orientation = :orientation, " +
            "missing = 0, lastSeen = :stamp WHERE mediaStoreId = :mediaId AND mediaType = :type"
    )
    suspend fun updateMeta(
        mediaId: Long, type: Int, name: String, size: Long, w: Int, h: Int, modified: Long,
        duration: Long, bucket: String, orientation: Int, stamp: Long,
    )

    // Per type, so a failed/partial video pass can never flag every video as missing (or vice versa).
    @Query("UPDATE photos SET missing = 1 WHERE mediaType = :type AND lastSeen < :stamp")
    suspend fun markMissing(type: Int, stamp: Long)

    @Query("UPDATE photos SET lastPositionMs = :positionMs WHERE id = :id")
    suspend fun setPosition(id: Long, positionMs: Long)

    @Query("UPDATE photos SET favorite = :fav, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setFavorite(ids: List<Long>, fav: Boolean, now: Long)

    @Query("UPDATE photos SET deleted = :deleted, trashedAt = :trashedAt, updatedAt = :now WHERE id IN (:ids)")
    suspend fun setDeleted(ids: List<Long>, deleted: Boolean, trashedAt: Long?, now: Long)

    @Query("UPDATE photos SET locked = :locked, unlockCostStars = :stars, unlockCostCoins = :coins, updatedAt = :now WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean, stars: Int, coins: Int, now: Long)

    @Query("UPDATE photos SET edited = 1, editVersion = editVersion + 1, editParams = :params, updatedAt = :now WHERE id = :id")
    suspend fun markEdited(id: Long, params: String, now: Long)

    @Query("SELECT contentUri FROM photos WHERE id IN (:ids)")
    suspend fun uris(ids: List<Long>): List<String>

    @Query("DELETE FROM photos WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)
}

@Dao
interface TagDao {
    @Query(
        """
        SELECT t.id AS id, t.name AS name, COUNT(p.id) AS photoCount
        FROM tags t
        LEFT JOIN photo_tags pt ON pt.tagId = t.id
        LEFT JOIN photos p ON p.id = pt.photoId AND p.deleted = 0 AND p.missing = 0
        GROUP BY t.id
        ORDER BY t.name COLLATE NOCASE
        """
    )
    fun observeCounts(): Flow<List<TagCount>>

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("UPDATE tags SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addRefs(refs: List<PhotoTagCrossRef>)

    @Query("DELETE FROM photo_tags WHERE tagId = :tagId AND photoId IN (:photoIds)")
    suspend fun removeRefs(photoIds: List<Long>, tagId: Long)

    @Query("SELECT t.name FROM tags t JOIN photo_tags pt ON pt.tagId = t.id WHERE pt.photoId = :photoId ORDER BY t.name COLLATE NOCASE")
    fun observeNamesFor(photoId: Long): Flow<List<String>>
}

@Dao
interface RecentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun touch(item: RecentPhotoEntity)

    @Query("DELETE FROM recent WHERE photoId NOT IN (SELECT photoId FROM recent ORDER BY viewedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)

    @Query(
        """
        SELECT p.* FROM photos p JOIN recent r ON r.photoId = p.id
        WHERE p.deleted = 0 AND p.missing = 0
        ORDER BY r.viewedAt DESC LIMIT :limit
        """
    )
    fun observe(limit: Int): Flow<List<PhotoEntity>>
}

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun ensure(wallet: WalletEntity)

    @Query("SELECT * FROM wallet WHERE id = 1")
    fun observe(): Flow<WalletEntity?>

    // The WHERE clause makes overdraft impossible even under concurrent calls.
    @Query("UPDATE wallet SET stars = stars - :amount, updatedAt = :now WHERE id = 1 AND :amount > 0 AND stars >= :amount")
    suspend fun spendStars(amount: Int, now: Long): Int

    @Query("UPDATE wallet SET coins = coins - :amount, updatedAt = :now WHERE id = 1 AND :amount > 0 AND coins >= :amount")
    suspend fun spendCoins(amount: Int, now: Long): Int

    @Query("UPDATE wallet SET stars = stars + :stars, coins = coins + :coins, updatedAt = :now WHERE id = 1")
    suspend fun add(stars: Int, coins: Int, now: Long)

    @Insert
    suspend fun insertTx(tx: WalletTransactionEntity)
}
