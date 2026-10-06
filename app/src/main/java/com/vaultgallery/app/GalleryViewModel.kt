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
import com.vaultgallery.app.data.AlbumInfo
import com.vaultgallery.app.data.AppDatabase
import com.vaultgallery.app.data.MediaType
import com.vaultgallery.app.data.VaultItemEntity
import com.vaultgallery.app.security.VaultStore
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
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
    @Volatile private var scanDebounceMs = 1_500L
    val vaultStore = VaultStore(app)
    private val scanTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val query = MutableStateFlow("")
    val favoritesOnly = MutableStateFlow(false)
    val tagFilter = MutableStateFlow(-1L)
    val selection = MutableStateFlow<Set<Long>>(emptySet())
    val scanProgress = MutableStateFlow<Pair<Int, Int>?>(null)

    // Extra filters (all optional). Set by the Albums screen, the filter sheet and the "N new items" indicator.
    val albumFilter = MutableStateFlow<String?>(null)
    val minSize = MutableStateFlow(0L)
    val dateSince = MutableStateFlow(0L)

    private data class Basic(val q: String, val fav: Boolean, val tag: Long)
    private data class Extra(val bucket: String?, val minSize: Long, val since: Long)
    private data class ViewOpts(val sort: Int, val type: Int)

    /** null = still loading. The same list (photos AND videos) feeds the grid, photo viewer and video player, so indices always match. */
    val photos: StateFlow<List<PhotoEntity>?> = combine(
        combine(query.debounce { if (it.isEmpty()) 0L else 200L }, favoritesOnly, tagFilter) { q, f, t -> Basic(q.trim(), f, t) },
        combine(albumFilter, minSize, dateSince) { b, m, d -> Extra(b, m, d) },
        combine(settingsRepo.flow.map { it.sort }.distinctUntilChanged(), settingsRepo.flow.map { it.mediaFilter }.distinctUntilChanged()) { so, ty -> ViewOpts(so, ty) },
    ) { a, b, v -> Triple(a, b, v) }
        .flatMapLatest { (a, b, v) ->
            db.photos().observe(a.q, v.type, if (a.fav) 1 else 0, a.tag, v.sort, if (b.bucket != null) 1 else 0, b.bucket ?: "", b.minSize, b.since)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private var appliedScope: String? = null

    /**
     * Called by each gallery-style screen. Switching to a different scope (Home, Favorites, an album) resets the filters
     * that belonged to the previous one; coming back from the viewer (same scope) keeps everything as it was.
     */
    fun applyScope(key: String, favorites: Boolean, album: String?) {
        if (appliedScope == key) return
        appliedScope = key
        query.value = ""; tagFilter.value = -1L; minSize.value = 0L; dateSince.value = 0L
        favoritesOnly.value = favorites
        albumFilter.value = album
    }

    /** Resets search and filters. The Favorites tab and an open album keep their own scope. */
    fun clearFilters(keepFavorites: Boolean = false, keepAlbum: Boolean = false) {
        query.value = ""; tagFilter.value = -1L; minSize.value = 0L; dateSince.value = 0L
        if (!keepFavorites) favoritesOnly.value = false
        if (!keepAlbum) albumFilter.value = null
    }

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

    val albums: StateFlow<List<AlbumInfo>> =
        db.photos().observeAlbums().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val newCount: StateFlow<Int> = settingsRepo.flow.map { it.newSince }.distinctUntilChanged()
        .flatMapLatest { since -> if (since <= 0) flowOf(0) else db.photos().observeNewCount(since) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    // ---- storage analyzer (all numbers come from Room aggregates, nothing re-scans the device) ----
    val trashBytes: StateFlow<Long> = db.photos().observeTrashBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val vaultBytes: StateFlow<Long> = db.vault().observeBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    val vaultCount: StateFlow<Int> = db.vault().observeCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val largestVideos: StateFlow<List<PhotoEntity>> = db.photos().observeLargest(MediaType.VIDEO, 30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val largestPhotos: StateFlow<List<PhotoEntity>> = db.photos().observeLargest(MediaType.IMAGE, 30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recentLarge: StateFlow<List<PhotoEntity>> =
        db.photos().observeRecentLarge(System.currentTimeMillis() - 30L * 24 * 3600 * 1000, 30).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { scanTrigger.tryEmit(Unit) }
    }

    init {
        // Watch both collections so new recordings, downloads, WhatsApp videos and outside deletions are picked up.
        app.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        app.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        viewModelScope.launch { walletRepo.ensure() }
        // Performance / battery-saver modes scan less often. Reads the latest setting each time.
        viewModelScope.launch {
            settingsRepo.flow.map { it.perfMode }.distinctUntilChanged().collect { scanDebounceMs = when (it) { 0 -> 1_500L; 1 -> 3_000L; else -> 15_000L } }
        }
        viewModelScope.launch { scanTrigger.debounce { scanDebounceMs }.collect { runScan() } }
        // First launch after the upgrade: nothing counts as "new" yet.
        viewModelScope.launch { if (settingsRepo.flow.first().newSince == 0L) settingsRepo.set(SettingsKeys.NEW_SINCE, now()) }
        // Remove vault files that no row refers to (left behind by a crash mid-hide). Recent files are kept.
        viewModelScope.launch(Dispatchers.IO) {
            val known = db.vault().allNames().flatMap { listOf(it.fileName, it.thumbName) }.toSet()
            vaultStore.sweepOrphans(known)
        }
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

    suspend fun shareUris(ids: Collection<Long>): List<Uri> =
        ids.chunked(500).flatMap { db.photos().unlockedUris(it) }.map { Uri.parse(it) }

    // ================= New-media indicator =================
    fun showNewItems() = viewModelScope.launch {
        val since = settingsRepo.flow.first().newSince
        if (since > 0) dateSince.value = since
        settingsRepo.set(SettingsKeys.NEW_SINCE, now())
    }
    fun markNewSeen() = viewModelScope.launch { settingsRepo.set(SettingsKeys.NEW_SINCE, now()) }

    // ================= Private vault =================
    data class VaultProgress(val label: String, val done: Int, val total: Int)
    /** Everything the system must confirm before the originals can be removed. */
    data class HideRequest(val uris: List<Uri>, val photoIds: List<Long>, val vaultIds: List<Long>, val id: Long = System.nanoTime())

    val vaultProgress = MutableStateFlow<VaultProgress?>(null)
    val hideRequest = MutableStateFlow<HideRequest?>(null)
    /** One-shot user messages (shown as toasts by the shell). Never contains paths, URIs or error details. */
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val vaultQuery = MutableStateFlow("")
    val vaultItems: StateFlow<List<VaultItemEntity>?> = vaultQuery.debounce { if (it.isEmpty()) 0L else 200L }
        .flatMapLatest { db.vault().observe(it.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var vaultJob: Job? = null

    fun cancelVaultWork() { vaultJob?.cancel() }

    /**
     * Hide = copy into encrypted private storage, verify, THEN ask the system to delete the originals.
     * The caller must have authenticated first. If the user declines the system dialog the vault copies are rolled back,
     * so nothing is ever removed without a verified encrypted copy, and nothing is duplicated silently.
     */
    fun hide(ids: Collection<Long>) {
        if (vaultJob?.isActive == true) return
        vaultJob = viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { ids.chunked(500).flatMap { db.photos().getAll(it) } }
            val eligible = items.filter { !it.locked && !it.deleted }
            if (eligible.isEmpty()) { messages.tryEmit("Nothing to hide. Items locked with Stars/Coins can't be moved to the Vault."); return@launch }
            val vaultIds = ArrayList<Long>()
            val photoIds = ArrayList<Long>()
            val uris = ArrayList<Uri>()
            var failed = 0
            try {
                eligible.forEachIndexed { i, p ->
                    vaultProgress.value = VaultProgress("Hiding ${i + 1} of ${eligible.size}", i, eligible.size)
                    try {
                        val staged = withContext(Dispatchers.IO) {
                            val job = coroutineContext.job
                            vaultStore.stage(Uri.parse(p.contentUri), p.isVideo) { job.ensureActive() }
                        }
                        val row = VaultItemEntity(
                            id = 0, fileName = vaultStore.mediaName(staged.base),
                            thumbName = if (staged.hasThumb) vaultStore.thumbName(staged.base) else "",
                            wrappedKey = staged.wrappedKey, displayName = p.displayName, mimeType = p.mimeType, mediaType = p.mediaType,
                            size = staged.size, width = p.width, height = p.height, durationMs = p.durationMs, dateTaken = p.dateTaken,
                            hiddenAt = now(), favorite = p.favorite, sha256 = staged.sha256,
                        )
                        val vid = try { db.vault().insert(row) } catch (e: Exception) {
                            vaultStore.delete(row.fileName, row.thumbName); throw e
                        }
                        vaultIds += vid; photoIds += p.id; uris += Uri.parse(p.contentUri)
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { failed++ }
                }
            } catch (e: CancellationException) {
                withContext(NonCancellable) { rollbackVault(vaultIds) }
                vaultProgress.value = null
                throw e
            }
            vaultProgress.value = null
            if (uris.isEmpty()) { messages.tryEmit("Couldn't hide the selected items."); return@launch }
            if (failed > 0) messages.tryEmit("$failed item(s) couldn't be copied and were left where they are.")
            selection.value = emptySet()
            hideRequest.value = HideRequest(uris, photoIds, vaultIds)   // the shell shows the system delete confirmation
        }
    }

    /** Result of the system delete dialog for the originals. */
    fun finishHide(confirmed: Boolean) {
        val req = hideRequest.value ?: return
        hideRequest.value = null
        viewModelScope.launch {
            if (confirmed) {
                chunked(req.photoIds) { db.photos().deleteByIds(it) }
                messages.tryEmit("Moved ${req.vaultIds.size} item(s) to the Vault")
            } else {
                withContext(NonCancellable) { rollbackVault(req.vaultIds) }
                messages.tryEmit("Cancelled. Nothing was hidden.")
            }
        }
    }

    private suspend fun rollbackVault(vaultIds: List<Long>) {
        if (vaultIds.isEmpty()) return
        withContext(Dispatchers.IO) {
            val rows = vaultIds.chunked(500).flatMap { db.vault().getAll(it) }
            vaultIds.chunked(500).forEach { db.vault().deleteByIds(it) }
            rows.forEach { vaultStore.delete(it.fileName, it.thumbName) }
        }
    }

    fun unhide(ids: Collection<Long>) {
        if (vaultJob?.isActive == true) return
        vaultJob = viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { ids.chunked(500).flatMap { db.vault().getAll(it) } }
            var done = 0
            var failed = 0
            try {
                items.forEachIndexed { i, item ->
                    vaultProgress.value = VaultProgress("Restoring ${i + 1} of ${items.size}", i, items.size)
                    try {
                        withContext(Dispatchers.IO) {
                            val job = coroutineContext.job
                            vaultStore.export(item) { job.ensureActive() }
                        }
                        db.vault().deleteByIds(listOf(item.id))
                        withContext(Dispatchers.IO) { vaultStore.delete(item.fileName, item.thumbName) }
                        done++
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { failed++ }
                }
            } finally {
                vaultProgress.value = null
            }
            messages.tryEmit(
                if (failed == 0) "Restored $done item(s) to Pictures/Movies › VaultGallery"
                else "Restored $done item(s). $failed couldn't be restored and stay in the Vault."
            )
            runScan()
        }
    }

    fun deleteFromVault(ids: Collection<Long>) = viewModelScope.launch {
        withContext(NonCancellable) { rollbackVault(ids.toList()) }
        messages.tryEmit("Deleted ${ids.size} item(s) from the Vault")
    }

    fun setVaultFavorite(ids: Collection<Long>, fav: Boolean) = viewModelScope.launch { db.vault().setFavorite(ids.toList(), fav) }

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
