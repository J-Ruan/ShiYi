package com.shiyi.memory.data

import android.content.Context
import com.shiyi.memory.domain.Crypto
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

/**
 * Copy, validate, commit, then clean. Old files remain the recovery source until every check
 * passes.
 */
object LegacyMigration {
    @Synchronized
    fun prepare(context: Context) {
        val old = context.getDatabasePath("memory.db")
        if (!old.exists()) return
        val secure = context.getDatabasePath("memory-secure.db")
        val stage = context.getDatabasePath("memory-secure.next")
        val media = ImageStorage(context)
        val oldMedia = File(context.filesDir, "media")
        val key = LocalKeys.database(context)
        try {
            CipherDatabase.openDatabase(old.path, "", null, CipherDatabase.OPEN_READWRITE, null)
                .use { source ->
                    source.rawQuery("PRAGMA wal_checkpoint(FULL)", emptyArray<String>()).use {
                        check(it.moveToFirst() && it.getInt(0) == 0)
                    }
                    source
                        .rawQuery(
                            "SELECT path FROM MemoryMedia UNION SELECT avatar FROM PartnerProfile UNION SELECT image FROM Wish",
                            emptyArray<String>(),
                        )
                        .use { rows ->
                            while (rows.moveToNext()) {
                                val path = rows.getString(0)
                                if (path.isNotBlank())
                                    require(
                                        path.matches(Regex("[A-Za-z0-9_-]+\\.img")) &&
                                            File(oldMedia, path).isFile
                                    ) {
                                        "旧图片缺失，迁移已停止"
                                    }
                            }
                        }
                    if (!secure.exists()) {
                        // A killed pre-commit attempt is disposable; the complete legacy source
                        // remains.
                        CipherDatabase.deleteDatabase(stage)
                        media.directory.listFiles().orEmpty().forEach { check(it.delete()) }
                        oldMedia
                            .listFiles()
                            .orEmpty()
                            .filter { it.isFile && it.extension == "img" }
                            .forEach { original ->
                                original.inputStream().use { media.write(original.name, it) }
                                media.withOriginal(original.name) {
                                    check(Crypto.sha256(it) == Crypto.sha256(original))
                                }
                            }
                        val escaped = stage.path.replace("'", "''")
                        val password = key.toString(Charsets.US_ASCII)
                        source.execSQL("ATTACH DATABASE '$escaped' AS encrypted KEY '$password'")
                        try {
                            source
                                .rawQuery(
                                    "SELECT sqlcipher_export('encrypted')",
                                    emptyArray<String>(),
                                )
                                .use { while (it.moveToNext()) {} }
                            source.execSQL("PRAGMA encrypted.user_version=${source.version}")
                        } finally {
                            source.execSQL("DETACH DATABASE encrypted")
                        }
                        verify(source, stage, key, oldMedia, media)
                        check(stage.renameTo(secure)) { "无法提交加密迁移，旧数据已保留" }
                    }
                    verify(source, secure, key, oldMedia, media)
                }
            // Cleanup is deliberately deferred until Room has validated schema and all
            // relationships.
        } catch (e: Exception) {
            throw java.io.IOException("加密迁移未完成。旧数据已保留，请检查存储空间后重试。", e)
        } finally {
            key.fill(0)
        }
    }

    private fun verify(
        source: CipherDatabase,
        target: File,
        key: ByteArray,
        oldMedia: File,
        media: ImageStorage,
    ) {
        CipherDatabase.openDatabase(target.path, key, null, CipherDatabase.OPEN_READWRITE, null)
            .use { db ->
                db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use {
                    check(it.moveToFirst() && it.getString(0) == "ok")
                }
                db.rawQuery("PRAGMA foreign_key_check", emptyArray<String>()).use {
                    check(!it.moveToFirst())
                }
                source
                    .rawQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'",
                        emptyArray<String>(),
                    )
                    .use { tables ->
                        while (tables.moveToNext()) {
                            val table = tables.getString(0).replace("\"", "\"\"")
                            fun count(database: CipherDatabase): Long =
                                database
                                    .rawQuery(
                                        "SELECT count(*) FROM \"$table\"",
                                        emptyArray<String>(),
                                    )
                                    .use {
                                        it.moveToFirst()
                                        it.getLong(0)
                                    }
                            check(count(source) == count(db)) { "迁移记录数量不一致" }
                        }
                    }
            }
        oldMedia
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension == "img" }
            .forEach { old ->
                media.withOriginal(old.name) {
                    check(Crypto.sha256(old) == Crypto.sha256(it)) { "迁移图片校验失败" }
                }
            }
    }

    fun finish(context: Context) {
        // Durable cleanup intent survives death between deleting the database and old media.
        val old = context.getDatabasePath("memory.db")
        val markerFile = File(context.noBackupFilesDir, "legacy-cleanup-v1")
        val marker = android.util.AtomicFile(markerFile)
        val marked = markerFile.exists() || File(markerFile.path + ".bak").exists()
        if (!old.exists() && !marked) return
        check(context.getDatabasePath("memory-secure.db").isFile)
        if (!marked) {
            val out = marker.startWrite()
            try {
                out.write(byteArrayOf(1))
                marker.finishWrite(out)
            } catch (e: Exception) {
                marker.failWrite(out)
                throw e
            }
        }
        if (old.exists()) check(CipherDatabase.deleteDatabase(old))
        check(File(context.filesDir, "media").let { !it.exists() || it.deleteRecursively() }) {
            "加密迁移已完成，请重试清理旧副本"
        }
        marker.delete()
    }
}
