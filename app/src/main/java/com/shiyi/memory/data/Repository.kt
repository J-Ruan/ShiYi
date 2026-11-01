package com.shiyi.memory.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MemoryRepository(
    val context: Context,
    val db: MemoryDatabase,
    val images: ImageStorage = ImageStorage(context),
) {
    val mutex = Mutex()
    val mediaDir = images.directory
    val dao = db.dao()
    val flow =
        db.invalidationTracker
            .createFlow(
                "PartnerProfile",
                "Memory",
                "MemoryMedia",
                "ProfileEntry",
                "Wish",
                "Anniversary",
                "MemoryCollection",
                "CollectionMemoryCrossRef",
                "Tag",
                "MemoryTagCrossRef",
                "ProfileMemoryCrossRef",
                "MemoryLink",
                "Category",
            )
            .conflate()
            .map { snapshot() }
            .distinctUntilChanged()

    suspend fun snapshot(): Snapshot =
        db.withTransaction {
            Snapshot(
                partner = dao.partners(),
                memories = dao.memories(),
                media = dao.media(),
                entries = dao.entries(),
                wishes = dao.wishes(),
                anniversaries = dao.anniversaries(),
                collections = dao.collections(),
                collectionRefs = dao.collectionRefs(),
                tags = dao.tags(),
                tagRefs = dao.tagRefs(),
                profileRefs = dao.profileRefs(),
                links = dao.links(),
                categories = dao.categories(),
            )
        }

    fun file(path: String): File = images.file(path)

    suspend fun importImage(uri: Uri): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val name = newId() + ".img"
                val mime = context.contentResolver.getType(uri)
                require(mime == null || mime.startsWith("image/")) { "请选择图片文件" }
                try {
                    requireNotNull(context.contentResolver.openInputStream(uri)) { "无法读取图片" }
                        .use { source ->
                            images.write(
                                name,
                                object : java.io.FilterInputStream(source) {
                                    var total = 0L

                                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                                        val n = super.read(b, off, len)
                                        if (n > 0) {
                                            total += n
                                            require(
                                                total <= 4L * 1024 * 1024 * 1024 &&
                                                    mediaDir.usableSpace > 16L * 1024 * 1024
                                            ) {
                                                "图片过大或空间不足"
                                            }
                                        }
                                        return n
                                    }
                                },
                            )
                        }
                    images.withOriginal(name) { f ->
                        val bounds =
                            android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                        android.graphics.BitmapFactory.decodeFile(f.path, bounds)
                        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "文件不是可读取的图片" }
                    }
                    name
                } catch (e: Exception) {
                    file(name).delete()
                    throw e
                }
            }
        }

    suspend fun updateMemory(value: Memory) = mutex.withLock { dao.put(value) }

    suspend fun updateProfile(value: PartnerProfile) = mutex.withLock { dao.put(value) }

    suspend fun updateWish(value: Wish) = mutex.withLock { dao.put(value) }

    suspend fun updateAnniversary(value: Anniversary) = mutex.withLock { dao.put(value) }

    suspend fun updateCategory(value: Category) = mutex.withLock { dao.put(value) }

    suspend fun restoreMemory(id: String) = mutex.withLock { dao.setDeleted(id, null) }

    suspend fun permanentDelete(id: String) =
        mutex.withLock {
            val row = dao.memories().find { it.id == id } ?: return@withLock
            require(row.deletedAt != null) { "请先将回忆移入回收站" }
            dao.deleteMemory(id)
            // Unused files remain recoverable until explicit reviewed maintenance.
        }

    suspend fun emptyTrash(expiredOnly: Boolean = false) {
        dao.memories()
            .filter {
                it.deletedAt != null &&
                    (!expiredOnly || it.deletedAt <= System.currentTimeMillis() - 30L * 86400000)
            }
            .forEach { permanentDelete(it.id) }
    }

    suspend fun saveMemory(
        memory: Memory,
        paths: List<String>,
        tags: List<String>,
        profileIds: Set<String>,
        related: Set<String>,
    ) =
        mutex.withLock {
            require(memory.title.isNotBlank()) { "请填写标题" }
            LocalDate.parse(memory.date)
            db.withTransaction {
                dao.put(
                    memory.copy(
                        updatedAt = System.currentTimeMillis(),
                        coverPath =
                            memory.coverPath.takeIf { it in paths } ?: paths.firstOrNull().orEmpty(),
                    )
                )
                dao.clearMedia(memory.id)
                paths.distinct().forEachIndexed { i, path ->
                    require(file(path).isFile) { "图片文件不存在，请重新选择" }
                    dao.put(
                        MemoryMedia(
                            memoryId = memory.id,
                            path = path,
                            position = i,
                            size = file(path).length(),
                        )
                    )
                }
                dao.clearTags(memory.id)
                val existingTags = dao.tags().associateBy { it.name }.toMutableMap()
                tags
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { name ->
                        val tag =
                            existingTags[name]
                                ?: Tag(name = name).also {
                                    dao.put(it)
                                    existingTags[name] = it
                                }
                        dao.put(MemoryTagCrossRef(memory.id, tag.id))
                    }
                dao.clearMemoryProfileLinks(memory.id)
                profileIds.forEach { dao.put(ProfileMemoryCrossRef(it, memory.id)) }
                dao.clearMemoryLinks(memory.id)
                related.filter { it != memory.id }.forEach { dao.put(MemoryLink(memory.id, it)) }
            }
        }

    suspend fun saveEntry(entry: ProfileEntry, memories: Set<String>) =
        mutex.withLock {
            require(entry.title.isNotBlank())
            db.withTransaction {
                dao.put(entry.copy(updatedAt = System.currentTimeMillis()))
                dao.clearProfileLinks(entry.id)
                memories.forEach { dao.put(ProfileMemoryCrossRef(entry.id, it)) }
            }
        }

    suspend fun saveCollection(collection: MemoryCollection, memories: Set<String>) =
        mutex.withLock {
            require(collection.title.isNotBlank())
            db.withTransaction {
                dao.put(collection)
                dao.clearCollectionLinks(collection.id)
                memories.forEach { dao.put(CollectionMemoryCrossRef(collection.id, it)) }
            }
        }

    suspend fun delete(kind: String, id: String) =
        mutex.withLock {
            val before = snapshot()
            val candidates =
                when (kind) {
                    "memory" -> before.media.filter { it.memoryId == id }.map { it.path }
                    "wish" -> before.wishes.filter { it.id == id }.map { it.image }
                    else -> emptyList()
                }.filter(String::isNotBlank)
            db.withTransaction {
                when (kind) {
                    "memory" -> dao.setDeleted(id, System.currentTimeMillis())
                    "entry" -> dao.deleteEntry(id)
                    "wish" -> dao.deleteWish(id)
                    "anniversary" -> dao.deleteAnniversary(id)
                    "collection" -> dao.deleteCollection(id)
                    "category" -> dao.deleteCategory(id)
                }
            }
            val stillUsed = snapshot().images()
            candidates.filter { it !in stillUsed }.forEach { file(it).delete() }
        }

    suspend fun replace(s: Snapshot) {
        db.withTransaction {
            clearTables()
            s.partner.forEach { dao.put(it) }
            s.memories.forEach { dao.put(it) }
            s.media.forEach { dao.put(it) }
            s.entries.forEach { dao.put(it) }
            s.wishes.forEach { dao.put(it) }
            s.anniversaries.forEach { dao.put(it) }
            s.collections.forEach { dao.put(it) }
            s.tags.forEach { dao.put(it) }
            s.categories.forEach { dao.put(it) }
            s.collectionRefs.forEach { dao.put(it) }
            s.tagRefs.forEach { dao.put(it) }
            s.profileRefs.forEach { dao.put(it) }
            s.links.forEach { dao.put(it) }
        }
    }

    private suspend fun clearTables() {
        dao.clearWishes()
        dao.clearAnniversaries()
        dao.clearMemories()
        dao.clearEntries()
        dao.clearCollections()
        dao.clearAllTags()
        dao.clearPartner()
        dao.clearCategories()
    }

    suspend fun eraseAll(settings: SettingsRepository) =
        withContext(Dispatchers.IO + NonCancellable) {
            mutex.withLock {
                db.withTransaction { clearTables() }
                settings.update { Settings() }
                mediaDir.listFiles()?.forEach { it.delete() }
                File(context.noBackupFilesDir, "share-drafts").deleteRecursively()
                context.cacheDir.deleteRecursively()
                context.cacheDir.mkdirs()
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use {
                    it.moveToFirst()
                }
                db.openHelper.writableDatabase.execSQL("VACUUM")
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { result
                    ->
                    require(result.moveToFirst() && result.getInt(0) == 0) { "日志清理未完成，请重试删除操作" }
                }
            }
        }

    // Only explicit maintenance removes unreferenced files. Import and form edits can coexist
    // safely.
    private fun retainedImages(s: Snapshot): Set<String> {
        val drafts =
            File(context.noBackupFilesDir, "share-drafts").listFiles().orEmpty().filter {
                it.isFile && !it.name.endsWith(".bak")
            }
        return s.images() +
            drafts.flatMap {
                kotlinx.serialization.json.Json.decodeFromString<List<String>>(it.readText())
            }
    }

    suspend fun storageReport(): StorageReport =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val s = snapshot()
                val all = mediaDir.listFiles().orEmpty().filter { it.isFile }
                val used = retainedImages(s)
                val candidates =
                    all.filter {
                        it.name !in used &&
                            it.lastModified() < System.currentTimeMillis() - 86400000
                    }
                fun bytes(paths: Set<String>) =
                    all.filter { it.name in paths }.sumOf { it.length() }
                StorageReport(
                    all.size,
                    all.sumOf { it.length() },
                    bytes(s.media.map { it.path }.toSet()),
                    bytes(s.partner.map { it.avatar }.toSet()),
                    bytes(s.wishes.map { it.image }.toSet()),
                    candidates.map { StorageFile(it.name, it.length(), it.lastModified()) },
                )
            }
        }

    suspend fun cleanReviewed(files: List<StorageFile>): Long =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val used = retainedImages(snapshot())
                var bytes = 0L
                files.forEach { reviewed ->
                    val f = File(mediaDir, reviewed.name)
                    require(f.parentFile == mediaDir && f.name !in used) { "清理清单已变化，请重新预览" }
                    if (
                        f.isFile &&
                            f.length() == reviewed.bytes &&
                            f.lastModified() == reviewed.modified &&
                            f.delete()
                    )
                        bytes += reviewed.bytes
                }
                bytes
            }
        }

    suspend fun cleanUnusedMedia(): Long =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val used = snapshot().images()
                var bytes = 0L
                mediaDir
                    .listFiles()
                    ?.filter {
                        it.name !in used &&
                            it.lastModified() < System.currentTimeMillis() - 86_400_000
                    }
                    ?.forEach {
                        bytes += it.length()
                        it.delete()
                    }
                bytes
            }
        }
}

data class StorageFile(val name: String, val bytes: Long, val modified: Long)

data class StorageReport(
    val count: Int,
    val total: Long,
    val memories: Long,
    val profile: Long,
    val wishes: Long,
    val unused: List<StorageFile>,
)
