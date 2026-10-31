package com.shiyi.memory

import com.shiyi.memory.data.*
import com.shiyi.memory.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class LogicTest {
    private val m =
        Memory(
            id = "m",
            title = "草莓蛋糕",
            description = "不喜欢太甜",
            date = "2020-10-31",
            excerpt = "买一点草莓吧",
            keywords = "甜品",
        )
    private val s =
        Snapshot(
            memories = listOf(m),
            entries = listOf(ProfileEntry(id = "p", title = "喜欢的食物", content = "草莓和奶油")),
            wishes = listOf(Wish(id = "w", title = "看电影", notes = "草莓主题")),
            tags = listOf(Tag("t", "小确幸")),
            tagRefs = listOf(MemoryTagCrossRef("m", "t")),
        )

    @Test
    fun chineseSubstringAcrossModules() {
        assertEquals(3, search(s, "草莓").size)
        assertEquals(1, search(s, "太甜").size)
        assertEquals(1, search(s, "小确").size)
    }

    @Test
    fun searchFilters() {
        assertEquals(1, search(s, "草莓", "回忆").size)
        assertEquals(0, search(s, "草莓", "回忆", from = "2021-01-01").size)
        assertEquals(1, search(s, "草莓", "回忆", to = "2020-12-31").size)
    }

    @Test
    fun onThisDayOnlyPreviousYears() {
        assertEquals(1, onThisDay(s, LocalDate.of(2026, 10, 31)).size)
        assertTrue(onThisDay(s, LocalDate.of(2020, 10, 31)).isEmpty())
        assertTrue(onThisDay(s, LocalDate.of(2026, 10, 30)).isEmpty())
    }

    @Test
    fun leapAnniversaryUsesFebruary28() {
        val a = Anniversary(title = "生日", date = "2000-02-29")
        assertEquals(LocalDate.of(2025, 2, 28), nextDate(a, LocalDate.of(2025, 1, 1)))
        assertEquals(LocalDate.of(2028, 2, 29), nextDate(a, LocalDate.of(2027, 3, 1)))
    }

    @Test
    fun nextAnnualDateRollsOverYear() {
        val a = Anniversary(title = "相识", date = "2020-01-01")
        assertEquals(LocalDate.of(2027, 1, 1), nextDate(a, LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun pastOneTimeEventIsNotUpcoming() {
        assertNull(
            nextDate(
                Anniversary(title = "一次", date = "2020-01-01", annual = false),
                LocalDate.of(2026, 1, 1),
            )
        )
    }

    @Test
    fun remindersCrossYearAndLeapDay() {
        assertTrue(
            dueReminder(
                Anniversary(title = "年初", date = "2020-01-01", advanceDays = 3),
                LocalDate.of(2026, 12, 29),
            )
        )
        assertTrue(
            dueReminder(
                Anniversary(title = "闰日", date = "2000-02-29", advanceDays = 1),
                LocalDate.of(2025, 2, 27),
            )
        )
    }

    @Test
    fun oneTimeReminderHasExactDay() {
        val a = Anniversary(title = "见面", date = "2026-11-03", annual = false, advanceDays = 3)
        assertTrue(dueReminder(a, LocalDate.of(2026, 10, 31)))
        assertFalse(dueReminder(a, LocalDate.of(2027, 10, 31)))
    }

    @Test
    fun togetherDaysIsCalendarBased() {
        assertEquals(2L, daysTogether("2026-10-30", LocalDate.of(2026, 10, 31)))
    }

    @Test
    fun statisticsUseCreationAndCompletionDate() {
        val time =
            LocalDate.of(2026, 10, 20)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        val data =
            s.copy(
                memories = listOf(m.copy(createdAt = time)),
                wishes =
                    listOf(
                        Wish(
                            title = "旅行",
                            status = 2,
                            createdAt = time - 40 * 86400000L,
                            completedAt = time,
                        )
                    ),
            )
        val stats = statistics(data, 2026, 10)
        assertEquals(1, stats.memories)
        assertEquals(1, stats.completed)
        assertEquals(0, stats.wishes)
        assertEquals(1, stats.monthly[9])
        assertEquals(0, statistics(data, 2026, 11).memories)
    }

    @Test
    fun validSnapshotAccepted() {
        validateSnapshot(s)
    }

    @Test(expected = IllegalArgumentException::class)
    fun orphanLinkRejected() {
        validateSnapshot(s.copy(profileRefs = listOf(ProfileMemoryCrossRef("missing", "m"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateIdsRejected() {
        validateSnapshot(s.copy(memories = listOf(m, m)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun pathTraversalRejected() {
        validateSnapshot(
            s.copy(media = listOf(MemoryMedia(memoryId = "m", path = "../secret", position = 0)))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCoverRejected() {
        validateSnapshot(s.copy(memories = listOf(m.copy(coverPath = "absent.img"))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidStatusRejected() {
        validateSnapshot(s.copy(wishes = listOf(Wish(title = "w", status = 7))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateImageOrderRejected() {
        validateSnapshot(
            s.copy(
                media =
                    listOf(
                        MemoryMedia(memoryId = "m", path = "one.img", position = 0),
                        MemoryMedia(memoryId = "m", path = "two.img", position = 0),
                    )
            )
        )
    }

    @Test
    fun randomMemoryIsAlwaysExisting() {
        repeat(100) { assertTrue(s.memories.random() in s.memories) }
        assertNull(emptyList<Memory>().randomOrNull())
    }

    @Test
    fun futureAnnualEventDoesNotRemindBeforeFirstOccurrence() {
        val a = Anniversary(title = "未来的约定", date = "2028-01-01", advanceDays = 1)
        assertEquals(LocalDate.of(2028, 1, 1), nextDate(a, LocalDate.of(2026, 1, 1)))
        assertFalse(dueReminder(a, LocalDate.of(2026, 12, 31)))
        assertTrue(dueReminder(a, LocalDate.of(2027, 12, 31)))
    }
}
