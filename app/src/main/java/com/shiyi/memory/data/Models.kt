package com.shiyi.memory.data

import androidx.room.*
import java.util.UUID
import kotlinx.serialization.Serializable

fun newId(): String = UUID.randomUUID().toString()

@Serializable
@Entity
data class PartnerProfile(
    @PrimaryKey val id: Int = 1,
    val nickname: String = "TA",
    val myNickname: String = "我",
    val birthday: String = "",
    val metDate: String = "",
    val bio: String = "",
    val avatar: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(indices = [Index("date"), Index("createdAt")])
data class Memory(
    @PrimaryKey val id: String = newId(),
    val type: String = "瞬间记录",
    val title: String,
    val description: String = "",
    val date: String,
    val time: String = "",
    val location: String = "",
    val mood: String = "",
    val category: String = "",
    val keywords: String = "",
    val excerpt: String = "",
    val context: String = "",
    val reason: String = "",
    val feeling: String = "",
    val favorite: Boolean = false,
    val pinned: Boolean = false,
    val coverPath: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Memory::class,
                parentColumns = ["id"],
                childColumns = ["memoryId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("memoryId"), Index(value = ["memoryId", "position"], unique = true)],
)
data class MemoryMedia(
    @PrimaryKey val id: String = newId(),
    val memoryId: String,
    val path: String,
    val position: Int,
    val mime: String = "image/*",
    val size: Long = 0,
)

@Serializable
@Entity
data class ProfileEntry(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val content: String = "",
    val category: String = "喜欢的东西",
    val importance: Int = 0,
    val tags: String = "",
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Memory::class,
                parentColumns = ["id"],
                childColumns = ["memoryId"],
                onDelete = ForeignKey.SET_NULL,
            )
        ],
    indices = [Index("memoryId")],
)
data class Wish(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val description: String = "",
    val category: String = "想做的事情",
    val image: String = "",
    val plannedDate: String = "",
    val status: Int = 0,
    val notes: String = "",
    val memoryId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Serializable
@Entity(
    foreignKeys =
        [
            ForeignKey(
                entity = Memory::class,
                parentColumns = ["id"],
                childColumns = ["memoryId"],
                onDelete = ForeignKey.SET_NULL,
            )
        ],
    indices = [Index("memoryId")],
)
data class Anniversary(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val date: String,
    val annual: Boolean = true,
    val advanceDays: Int = 0,
    val notes: String = "",
    val memoryId: String? = null,
)

@Serializable
@Entity
data class MemoryCollection(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val description: String = "",
)

@Serializable
@Entity(
    primaryKeys = ["collectionId", "memoryId"],
    foreignKeys =
        [
            ForeignKey(
                entity = MemoryCollection::class,
                parentColumns = ["id"],
                childColumns = ["collectionId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Memory::class,
                parentColumns = ["id"],
                childColumns = ["memoryId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("memoryId")],
)
data class CollectionMemoryCrossRef(val collectionId: String, val memoryId: String)

@Serializable
@Entity(indices = [Index(value = ["name"], unique = true)])
data class Tag(@PrimaryKey val id: String = newId(), val name: String)

@Serializable
@Entity(
    primaryKeys = ["memoryId", "tagId"],
    foreignKeys =
        [
            ForeignKey(
                entity = Memory::class,
                parentColumns = ["id"],
                childColumns = ["memoryId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Tag::class,
                parentColumns = ["id"],
                childColumns = ["tagId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("tagId")],
)
data class MemoryTagCrossRef(val memoryId: String, val tagId: String)

@Serializable
@Entity(
    primaryKeys = ["profileId", "memoryId"],
    foreignKeys =
        [
            ForeignKey(
                entity = ProfileEntry::class,
                parentColumns = ["id"],
                childColumns = ["profileId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Memory::class,
                parentColumns = ["id"],
                childColumns = ["memoryId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("memoryId")],
)
data class ProfileMemoryCrossRef(val profileId: String, val memoryId: String)

@Serializable
@Entity(
    primaryKeys = ["fromId", "toId"],
    foreignKeys =
        [
            ForeignKey(
                entity = Memory::class,
                parentColumns = ["id"],
                childColumns = ["fromId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = Memory::class,
                parentColumns = ["id"],
                childColumns = ["toId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("toId")],
)
data class MemoryLink(val fromId: String, val toId: String)

@Serializable
@Entity(indices = [Index(value = ["scope", "name"], unique = true)])
data class Category(@PrimaryKey val id: String = newId(), val scope: String, val name: String)

@Serializable
data class Snapshot(
    val version: Int = 1,
    val partner: List<PartnerProfile> = emptyList(),
    val memories: List<Memory> = emptyList(),
    val media: List<MemoryMedia> = emptyList(),
    val entries: List<ProfileEntry> = emptyList(),
    val wishes: List<Wish> = emptyList(),
    val anniversaries: List<Anniversary> = emptyList(),
    val collections: List<MemoryCollection> = emptyList(),
    val collectionRefs: List<CollectionMemoryCrossRef> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val tagRefs: List<MemoryTagCrossRef> = emptyList(),
    val profileRefs: List<ProfileMemoryCrossRef> = emptyList(),
    val links: List<MemoryLink> = emptyList(),
    val categories: List<Category> = emptyList(),
) {
    fun images(): Set<String> =
        (media.map { it.path } + partner.map { it.avatar } + wishes.map { it.image })
            .filter { it.isNotEmpty() }
            .toSet()

    fun tagsFor(id: String) =
        tagRefs
            .filter { it.memoryId == id }
            .mapNotNull { r -> tags.find { it.id == r.tagId }?.name }
}
