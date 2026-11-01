package com.shiyi.memory

import android.content.Context
import androidx.room.Room
import com.shiyi.memory.data.*
import java.io.File

fun testDatabase(context: Context, name: String): MemoryDatabase =
    Room.databaseBuilder(context, MemoryDatabase::class.java, name)
        .addMigrations(MemoryDatabase.MIGRATION_1_2, MemoryDatabase.MIGRATION_2_3)
        .build()

fun testRepository(context: Context, db: MemoryDatabase): MemoryRepository =
    MemoryRepository(
        context,
        db,
        ImageStorage(
            context,
            File(context.filesDir, "jvm-fixture-${java.util.UUID.randomUUID()}"),
        ) {
            ByteArray(32) { 31 }
        },
    )
