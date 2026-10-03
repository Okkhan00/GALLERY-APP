package com.vaultgallery.app

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.vaultgallery.app.data.AppDatabase
import com.vaultgallery.app.data.MediaCounts
import com.vaultgallery.app.data.SettingsKeys
import com.vaultgallery.app.data.TypeBytes
import com.vaultgallery.app.data.PhotoEntity
import com.vaultgallery.app.data.PhotoTagCrossRef
import com.vaultgallery.app.data.RecentPhotoEntity
import com.vaultgallery.app.data.SettingsRepository
import com.vaultgallery.app.data.TagCount
import com.vaultgallery.app.data.TagEntity
import com.vaultgallery.app.data.WalletEntity
import com.vaultgallery.app.domain.Currency
import com.vaultgallery.app.domain.UnlockResult
import com.vaultgallery.app.domain.WalletPricing
import com.vaultgallery.app.domain.WalletRepository
import com.vaultgallery.app.media.MediaScanner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.time.LocalDate

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDatabase.get(app)
    val settingsRepo = SettingsRepository(app)
    val walletRepo = WalletRepository(db)
    private val scanner = MediaScanner(app, db)
    private val scanMutex = Mutex()
    private val scanTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val query = MutableStateFlow("")
    val favoritesOnly = MutableStateFlow(false)
    val tagFilter = MutableStateFlow(-1L)
    val selection = MutableStateFlow<Set<Long>>(emptySet())
    val scanProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    private data class Params(val q: String, val fav: Boolean, val tag: Long, val sort: Int, val type: Int)

    /** null = still loading. The same list (photos AND videos) feeds the grid, photo viewer and video player, so indices always match. */
    val photos: StateFlow<List<PhotoEntity>?> = combine(
        query.debounce { if (it.isEmpty()) 0L else 200L },
        favoritesOnly, tagFilter,
        settingsRepo.flow.map { it.sort }.distinctUntilChanged(),
        settingsRepo.flow.map { it.mediaFilter }.distinctUntilChanged(),
    ) { q, f, t, s, type -> Params(q.trim(), f, t, s, type) }
        .flatMapLatest { p -> db.photos().observe(p.q, p.type, if (p.fav) 1 else 0, p.tag, p.sort) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val trash: StateFlow<List<PhotoEntity>> =
        db.photos().observeTrash().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags: StateFlow<List<TagCount>> =
        db.tags().observeCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recent: StateFlow<List<PhotoEntity>> =
        db.recent().observe(RECENT_LIMIT).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val wallet: StateFlow<WalletEntity?> =
        walletRepo.wallet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val totalBytes: StateFlow<Long> =
        db.photos().observeBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val counts: StateFlow<MediaCounts> =
        db.photos().observeCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaCounts(0, 0, 0))
    val typeBytes: StateFlow<TypeBytes> =
        db.photos().observeTypeBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TypeBytes(0, 0))

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scanTrigger.tryEmit(Unit) }
    }

    init {
        // Watch both collections so new recordings, downloads, WhatsApp videos and outside deletions are picked up.
        app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        app.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        viewModelScope.launch { walletRepo.ensure() }
        viewModelScope.launch { scanTrigger.debounce(1_500).collect { runScan() } }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    // ---- scanning ----
    fun scan() { viewModelScope.launch { runScan() } }

    private suspend fun runScan() {
        if (!scanMutex.tryLock()) return
        try {
            runCatching { scanner.sync { done, total -> scanProgress.value = done to total } }
        } finally {
            scanProgress.value = null
            scanMutex.unlock()
        }
    }

    // ---- selection ----
    fun toggleSelect(id: Long) { selection.value = selection.value.let { if (id in it) it - id else it + id } }
    fun clearSelection() { selection.value = emptySet() }
    fun selectAll(list: List<PhotoEntity>) { selection.value = list.map { it.id }.toSet() }

    // ---- photo actions (chunked: SQLite caps bound variables) ----
    private suspend fun chunked(ids: Collection<Long>, block: suspend (List<Long>) -> Unit) {
        db.withTransaction { ids.chunked(500).forEach { block(it) } }
    }

    fun toggleFavorite(photo: PhotoEntity) = setFavorite(listOf(photo.id), !photo.favorite)
    fun setFavorite(ids: Collection<Long>, fav: Boolean) = viewModelScope.launch {
        chunked(ids) { db.photos().setFavorite(it, fav, now()) }
    }

    fun trash(ids: Collection<Long>) = viewModelScope.launch {
        chunked(ids) { db.photos().setDeleted(it, true, now(), now()) }
        selection.value = selection.value - ids.toSet()
    }

    fun restore(ids: Collection<Long>) = viewModelScope.launch {
        chunked(ids) { db.photos().setDeleted(it, false, null, now()) }
    }

    /** Called after the system confirmed deletion of the real files. */
    fun purge(ids: Collection<Long>) = viewModelScope.launch {
        chunked(ids) { db.photos().deleteByIds(it) }
    }

    suspend fun urisFor(ids: Collection<Long>): List<Uri> =
        ids.chunked(500).flatMap { db.photos().uris(it) }.map { Uri.parse(it) }

    fun lockPhotos(ids: Collection<Long>) = viewModelScope.launch {
        db.withTransaction {
            ids.forEach { db.photos().setLocked(it, true, WalletPricing.stars(it), WalletPricing.coins(it), now()) }
        }
        selection.value = emptySet()
    }

    suspend fun unlock(photoId: Long, currency: Currency): UnlockResult = walletRepo.unlock(photoId, currency)

    fun debugGrant() = viewModelScope.launch { walletRepo.debugGrant(100, 500) }

    fun setMediaFilter(type: Int) { setSetting(SettingsKeys.MEDIA_FILTER, type) }

    /** Saved playback position; written at most every few seconds by the player and on exit. */
    fun savePosition(id: Long, positionMs: Long) {
        viewModelScope.launch { db.photos().setPosition(id, positionMs.coerceAtLeast(0)) }
    }

    fun touchRecent(id: Long) = viewModelScope.launch {
        db.recent().touch(RecentPhotoEntity(id, now()))
        db.recent().trim(RECENT_LIMIT)
    }

    suspend fun getPhoto(id: Long): PhotoEntity? = db.photos().get(id)
    fun markEdited(id: Long, params: String) = viewModelScope.launch { db.photos().markEdited(id, params, now()) }

    /** Deterministic per calendar day, always local. */
    suspend fun photoOfTheDay(): PhotoEntity? {
        val n = db.photos().visibleUnlockedCount()
        if (n == 0) return null
        return db.photos().unlockedAt((LocalDate.now().toEpochDay() % n).toInt())
    }

    // ---- tags ----
    private fun normalize(raw: String) = raw.trim().replace(Regex("\\s+"), " ")

    suspend fun createTag(raw: String): Boolean {
        val name = normalize(raw)
        if (name.isEmpty() || name.length > 40) return false
        return runCatching { db.tags().insert(TagEntity(name = name, createdAt = now())) }.isSuccess
    }

    suspend fun renameTag(id: Long, raw: String): Boolean {
        val name = normalize(raw)
        if (name.isEmpty() || name.length > 40) return false
        return runCatching { db.tags().rename(id, name) }.isSuccess
    }

    fun deleteTag(id: Long) = viewModelScope.launch {
        if (tagFilter.value == id) tagFilter.value = -1L
        db.tags().delete(id)
    }

    fun addTag(photoIds: Collection<Long>, tagId: Long) = viewModelScope.launch {
        db.tags().addRefs(photoIds.map { PhotoTagCrossRef(it, tagId) })
    }

    fun removeTag(photoIds: Collection<Long>, tagId: Long) = viewModelScope.launch {
        chunked(photoIds) { db.tags().removeRefs(it, tagId) }
    }

    fun tagNames(photoId: Long): Flow<List<String>> = db.tags().observeNamesFor(photoId)

    fun <T> setSetting(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        viewModelScope.launch { settingsRepo.set(key, value) }
    }

    private fun now() = System.currentTimeMillis()
    private companion object { const val RECENT_LIMIT = 20 }
}
