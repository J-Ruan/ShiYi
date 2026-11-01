package com.shiyi.memory.domain

import com.shiyi.memory.data.*
import java.time.*
import java.time.temporal.ChronoUnit

val memoryTypes = listOf("瞬间记录", "聊天收藏", "事件记录", "文字记录")
val profileCategories = listOf("喜欢的东西", "不喜欢的东西", "性格与印象", "相处方式", "重要注意事项", "其他小细节")
val wishCategories = listOf("想吃的东西", "想去的地方", "想看的电影", "想收到的礼物", "想做的事情", "其他愿望")
val wishStates = listOf("待实现", "计划中", "已实现")
val importanceNames = listOf("普通", "重要", "特别重要")

fun today(): LocalDate = LocalDate.now()

fun anniversaryInYear(date: LocalDate, year: Int): LocalDate = MonthDay.from(date).atYear(year)

fun nextDate(a: Anniversary, from: LocalDate = today()): LocalDate? {
    val base = LocalDate.parse(a.date)
    if (!a.annual) return base.takeUnless { it < from }
    val year = maxOf(base.year, from.year)
    return anniversaryInYear(base, year).let {
        if (it < from) anniversaryInYear(base, year + 1) else it
    }
}

fun dueReminder(a: Anniversary, day: LocalDate): Boolean {
    // Search in reminder space: a January anniversary may remind in December.
    val d = LocalDate.parse(a.date)
    return if (!a.annual) d.minusDays(a.advanceDays.toLong()) == day
    else
        (day.year..day.year + 1).any {
            it >= d.year && anniversaryInYear(d, it).minusDays(a.advanceDays.toLong()) == day
        }
}

fun onThisDay(s: Snapshot, date: LocalDate) =
    s.activeMemories.filter {
        val d = LocalDate.parse(it.date)
        d.year < date.year && MonthDay.from(d) == MonthDay.from(date)
    }

fun daysTogether(date: String, day: LocalDate = today()) =
    ChronoUnit.DAYS.between(LocalDate.parse(date), day).coerceAtLeast(0) + 1

data class SearchResult(
    val kind: String,
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val category: String,
)

fun search(
    s: Snapshot,
    query: String,
    kind: String = "全部",
    category: String = "",
    from: String = "",
    to: String = "",
): List<SearchResult> {
    val records =
        s.activeMemories.map {
            SearchResult(
                "回忆",
                it.id,
                it.title,
                listOf(
                        it.description,
                        it.excerpt,
                        it.context,
                        it.reason,
                        it.feeling,
                        it.keywords,
                        s.tagsFor(it.id).joinToString(" "),
                    )
                    .joinToString("\n"),
                it.date,
                it.category,
            )
        } +
            s.entries.map {
                SearchResult(
                    "档案",
                    it.id,
                    it.title,
                    it.content + "\n" + it.tags,
                    Instant.ofEpochMilli(it.createdAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString(),
                    it.category,
                )
            } +
            s.wishes.map {
                SearchResult(
                    "心愿",
                    it.id,
                    it.title,
                    it.description + "\n" + it.notes,
                    Instant.ofEpochMilli(it.createdAt)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                        .toString(),
                    it.category,
                )
            }
    return records.filter {
        (kind == "全部" || it.kind == kind) &&
            (category.isBlank() || it.category.contains(category, true)) &&
            (from.isBlank() || it.date >= from) &&
            (to.isBlank() || it.date <= to) &&
            (query.isBlank() || (it.title + "\n" + it.description).contains(query.trim(), true))
    }
}

data class Stats(
    val memories: Int,
    val photos: Int,
    val chats: Int,
    val events: Int,
    val texts: Int,
    val entries: Int,
    val wishes: Int,
    val completed: Int,
    val collections: Int,
    val monthly: List<Int>,
    val distribution: Map<String, Int>,
)

fun statistics(s: Snapshot, year: Int? = null, month: Int? = null): Stats {
    fun accepts(time: Long): Boolean {
        val d = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate()
        return (year == null || d.year == year) && (month == null || d.monthValue == month)
    }
    val memories = s.activeMemories.filter { accepts(it.createdAt) }
    val ids = memories.map { it.id }.toSet()
    return Stats(
        memories.size,
        s.media.count { it.memoryId in ids },
        memories.count { it.type == "聊天收藏" },
        memories.count { it.type == "事件记录" },
        memories.count { it.type == "文字记录" },
        s.entries.count { accepts(it.createdAt) },
        s.wishes.count { accepts(it.createdAt) },
        s.wishes.count { it.status == 2 && it.completedAt?.let(::accepts) == true },
        s.collections.size,
        (1..12).map { m ->
            s.activeMemories.count {
                val d = Instant.ofEpochMilli(it.createdAt).atZone(ZoneId.systemDefault())
                (year == null || d.year == year) && d.monthValue == m
            }
        },
        memories.groupingBy { it.category.ifBlank { it.type } }.eachCount(),
    )
}

fun validateSnapshot(s: Snapshot) {
    require(s.version == 1) { "不支持的备份数据版本" }
    require(s.partner.size <= 1 && s.partner.all { it.id == 1 }) { "恋人档案无效" }
    val memoryIds = s.memories.map { it.id }.toSet()
    val entryIds = s.entries.map { it.id }.toSet()
    val tagIds = s.tags.map { it.id }.toSet()
    val collectionIds = s.collections.map { it.id }.toSet()
    listOf(
            s.memories.map { it.id },
            s.media.map { it.id },
            s.entries.map { it.id },
            s.wishes.map { it.id },
            s.anniversaries.map { it.id },
            s.collections.map { it.id },
            s.tags.map { it.id },
            s.categories.map { it.id },
        )
        .forEach {
            require(it.size == it.toSet().size && it.none(String::isBlank)) { "备份包含重复或空 ID" }
        }
    require(s.images().all { it.matches(Regex("[A-Za-z0-9_-]+\\.img")) }) { "备份图片路径无效" }
    s.memories.forEach {
        LocalDate.parse(it.date)
        require(it.title.isNotBlank())
        require(
            it.coverPath.isBlank() ||
                s.media.any { m -> m.memoryId == it.id && m.path == it.coverPath }
        )
    }
    s.anniversaries.forEach {
        LocalDate.parse(it.date)
        require(it.advanceDays in 0..365)
        require(it.memoryId == null || it.memoryId in memoryIds)
    }
    s.wishes.forEach {
        require(it.status in 0..2)
        if (it.plannedDate.isNotBlank()) LocalDate.parse(it.plannedDate)
        require(it.memoryId == null || it.memoryId in memoryIds)
    }
    s.partner.forEach {
        if (it.birthday.isNotBlank()) LocalDate.parse(it.birthday)
        if (it.metDate.isNotBlank()) LocalDate.parse(it.metDate)
    }
    require(s.entries.all { it.importance in 0..2 })
    require(s.media.all { it.memoryId in memoryIds && it.position >= 0 })
    require(s.media.map { it.memoryId to it.position }.distinct().size == s.media.size)
    require(s.tags.map { it.name }.distinct().size == s.tags.size)
    require(
        s.collectionRefs.all { it.memoryId in memoryIds && it.collectionId in collectionIds } &&
            s.collectionRefs.distinct().size == s.collectionRefs.size
    )
    require(
        s.profileRefs.all { it.memoryId in memoryIds && it.profileId in entryIds } &&
            s.profileRefs.distinct().size == s.profileRefs.size
    )
    require(
        s.tagRefs.all { it.memoryId in memoryIds && it.tagId in tagIds } &&
            s.tagRefs.distinct().size == s.tagRefs.size
    )
    require(
        s.links.all { it.fromId in memoryIds && it.toId in memoryIds && it.fromId != it.toId } &&
            s.links.distinct().size == s.links.size
    )
}
