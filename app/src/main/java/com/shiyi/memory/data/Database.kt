package com.shiyi.memory.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Dao
interface MemoryDao {
    @Query("SELECT * FROM PartnerProfile") suspend fun partners(): List<PartnerProfile>

    @Query("SELECT * FROM Memory ORDER BY pinned DESC, date DESC, createdAt DESC")
    suspend fun memories(): List<Memory>

    @Query("SELECT * FROM MemoryMedia ORDER BY position") suspend fun media(): List<MemoryMedia>

    @Query("SELECT * FROM ProfileEntry ORDER BY pinned DESC, importance DESC, updatedAt DESC")
    suspend fun entries(): List<ProfileEntry>

    @Query("SELECT * FROM Wish ORDER BY createdAt DESC") suspend fun wishes(): List<Wish>

    @Query("SELECT * FROM Anniversary") suspend fun anniversaries(): List<Anniversary>

    @Query("SELECT * FROM MemoryCollection") suspend fun collections(): List<MemoryCollection>

    @Query("SELECT * FROM CollectionMemoryCrossRef")
    suspend fun collectionRefs(): List<CollectionMemoryCrossRef>

    @Query("SELECT * FROM Tag") suspend fun tags(): List<Tag>

    @Query("SELECT * FROM MemoryTagCrossRef") suspend fun tagRefs(): List<MemoryTagCrossRef>

    @Query("SELECT * FROM ProfileMemoryCrossRef")
    suspend fun profileRefs(): List<ProfileMemoryCrossRef>

    @Query("SELECT * FROM MemoryLink") suspend fun links(): List<MemoryLink>

    @Query("SELECT * FROM Category") suspend fun categories(): List<Category>

    @Upsert suspend fun put(value: PartnerProfile)

    @Upsert suspend fun put(value: Memory)

    @Upsert suspend fun put(value: MemoryMedia)

    @Upsert suspend fun put(value: ProfileEntry)

    @Upsert suspend fun put(value: Wish)

    @Upsert suspend fun put(value: Anniversary)

    @Upsert suspend fun put(value: MemoryCollection)

    @Upsert suspend fun put(value: Category)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(value: Tag)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(value: CollectionMemoryCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(value: MemoryTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(value: ProfileMemoryCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun put(value: MemoryLink)

    @Query("UPDATE Memory SET deletedAt=:time WHERE id=:id")
    suspend fun setDeleted(id: String, time: Long?)

    @Query(
        "SELECT * FROM Memory WHERE deletedAt IS NULL AND (:from='' OR date>=:from) AND (:to='' OR date<=:to) ORDER BY date DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun memoryPage(
        from: String = "",
        to: String = "",
        limit: Int = 50,
        offset: Int = 0,
    ): List<Memory>

    @Query("DELETE FROM Memory WHERE id=:id") suspend fun deleteMemory(id: String)

    @Query("DELETE FROM ProfileEntry WHERE id=:id") suspend fun deleteEntry(id: String)

    @Query("DELETE FROM Wish WHERE id=:id") suspend fun deleteWish(id: String)

    @Query("DELETE FROM Anniversary WHERE id=:id") suspend fun deleteAnniversary(id: String)

    @Query("DELETE FROM MemoryCollection WHERE id=:id") suspend fun deleteCollection(id: String)

    @Query("DELETE FROM Category WHERE id=:id") suspend fun deleteCategory(id: String)

    @Query("DELETE FROM MemoryMedia WHERE memoryId=:id") suspend fun clearMedia(id: String)

    @Query("DELETE FROM MemoryTagCrossRef WHERE memoryId=:id") suspend fun clearTags(id: String)

    @Query("DELETE FROM ProfileMemoryCrossRef WHERE memoryId=:id")
    suspend fun clearMemoryProfileLinks(id: String)

    @Query("DELETE FROM ProfileMemoryCrossRef WHERE profileId=:id")
    suspend fun clearProfileLinks(id: String)

    @Query("DELETE FROM MemoryLink WHERE fromId=:id OR toId=:id")
    suspend fun clearMemoryLinks(id: String)

    @Query("DELETE FROM CollectionMemoryCrossRef WHERE collectionId=:id")
    suspend fun clearCollectionLinks(id: String)

    @Query("DELETE FROM PartnerProfile") suspend fun clearPartner()

    @Query("DELETE FROM Memory") suspend fun clearMemories()

    @Query("DELETE FROM ProfileEntry") suspend fun clearEntries()

    @Query("DELETE FROM Wish") suspend fun clearWishes()

    @Query("DELETE FROM Anniversary") suspend fun clearAnniversaries()

    @Query("DELETE FROM MemoryCollection") suspend fun clearCollections()

    @Query("DELETE FROM Tag") suspend fun clearAllTags()

    @Query("DELETE FROM Category") suspend fun clearCategories()
}

@Database(
    entities =
        [
            PartnerProfile::class,
            Memory::class,
            MemoryMedia::class,
            ProfileEntry::class,
            Wish::class,
            Anniversary::class,
            MemoryCollection::class,
            CollectionMemoryCrossRef::class,
            Tag::class,
            MemoryTagCrossRef::class,
            ProfileMemoryCrossRef::class,
            MemoryLink::class,
            Category::class,
        ],
    version = 3,
    exportSchema = true,
)
abstract class MemoryDatabase : RoomDatabase() {
    abstract fun dao(): MemoryDao

    companion object {
        // v1 -> v2 adds a field without deleting any existing records.
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE Wish ADD COLUMN completedAt INTEGER")
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE Memory ADD COLUMN deletedAt INTEGER")
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_Memory_deletedAt ON Memory(deletedAt)"
                    )
                }
            }

        fun open(context: Context, name: String = "memory.db"): MemoryDatabase {
            System.loadLibrary("sqlcipher")
            if (name == "memory.db") LegacyMigration.prepare(context)
            val key = LocalKeys.database(context)
            // Factory owns its copy for reopen; clear our temporary key immediately.
            return try {
                Room.databaseBuilder(
                        context,
                        MemoryDatabase::class.java,
                        if (name == "memory.db") "memory-secure.db" else name,
                    )
                    .openHelperFactory(
                        net.zetetic.database.sqlcipher.SupportOpenHelperFactory(key.copyOf())
                    )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
            } finally {
                key.fill(0)
            }
        }
    }
}
