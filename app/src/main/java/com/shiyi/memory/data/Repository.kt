package com.shiyi.memory.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MemoryRepository(val context: Context, val db: MemoryDatabase) {
    val mutex = Mutex()
    val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
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
            .map { snapshot() }

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

    fun file(path: String): File {
        require(path.matches(Regex("[A-Za-z0-9._-]+")) && path != "." && path != "..") { "无效图片路径" }
        return File(mediaDir, path)
    }

    suspend fun importImage(uri: Uri): String =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val name = newId() + ".img"
                val target = file(name)
                try {
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "无法读取图片" }
                        target.outputStream().use { out -> input.copyTo(out) }
                    }
                    require(target.length() > 0) { "图片文件为空" }
                    val options =
                        android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    android.graphics.BitmapFactory.decodeFile(target.path, options)
                    require(options.outWidth > 0 && options.outHeight > 0) { "文件不是可读取的图片" }
                    name
                } catch (e: Exception) {
                    target.delete()
                    throw e
                }
            }
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
                tags
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .forEach { name ->
                        val tag =
                            dao.tags().find { it.name == name }
                                ?: Tag(name = name).also { dao.put(it) }
                        dao.put(MemoryTagCrossRef(memory.id, tag.id))
                    }
                // Replace both directions while retaining every original record.
                dao.profileRefs()
                    .filter { it.memoryId == memory.id }
                    .forEach { old ->
                        val others =
                            dao.profileRefs().filter {
                                it.profileId == old.profileId && it.memoryId != memory.id
                            }
                        dao.clearProfileLinks(old.profileId)
                        others.forEach { dao.put(it) }
                    }
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
                    "memory" -> dao.deleteMemory(id)
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
