package com.shiyi.memory.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyi.memory.domain.*
import java.time.*

@Composable
fun WishesScreen(vm: AppViewModel, go: (String) -> Unit, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("全部") }
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Header(
                "心愿清单",
                "让期待，有个安放的地方",
                back,
                actions = {
                    IconButton(onClick = { go("edit/wish/new") }) {
                        Icon(Icons.Outlined.Add, "添加心愿")
                    }
                },
            )
            Chips(listOf("全部") + wishStates, filter) { filter = it }
        }
        val records = s.wishes.filter { filter == "全部" || wishStates[it.status] == filter }
        if (records.isEmpty()) item { Empty("还有什么想一起做的？", "记下想吃、想去、想看的小小愿望。") }
        items(records, key = { it.id }) { w ->
            RecordCard(
                w.title,
                "${w.category}\n${w.description}",
                { go("detail/wish/${w.id}") },
                wishStates[w.status],
            )
        }
    }
}

@Composable
fun AnniversariesScreen(vm: AppViewModel, go: (String) -> Unit, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Header(
                "纪念日",
                "重要的日子，都记在这里",
                back,
                actions = {
                    IconButton(onClick = { go("edit/anniversary/new") }) {
                        Icon(Icons.Outlined.Add, "添加纪念日")
                    }
                },
            )
            Text(
                "提醒需在设置中主动开启，使用系统非精确闹钟。荣耀设备可在系统电池设置中允许拾忆后台运行，提醒时间可能受省电影响。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (s.anniversaries.isEmpty()) item { Empty("值得纪念的第一天", "生日、相识，或任何属于你们的特别日子。") }
        items(s.anniversaries.sortedBy { nextDate(it) ?: LocalDate.MAX }, key = { it.id }) { a ->
            RecordCard(
                a.title,
                "${a.date} · ${if (a.annual) "每年" else "一次"}\n下次：${nextDate(a) ?: "已过去"}",
                { go("detail/anniversary/${a.id}") },
            )
        }
    }
}

@Composable
fun CollectionsScreen(vm: AppViewModel, go: (String) -> Unit, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Header(
                "回忆合集",
                "把相关的时光，收进同一本册子",
                back,
                actions = {
                    IconButton(onClick = { go("edit/collection/new") }) {
                        Icon(Icons.Outlined.Add, "创建合集")
                    }
                },
            )
        }
        if (s.collections.isEmpty()) item { Empty("创建第一本时光合集", "可以收藏旅行、第一次，或有趣的聊天。") }
        items(s.collections, key = { it.id }) { c ->
            RecordCard(
                c.title,
                c.description,
                { go("detail/collection/${c.id}") },
                "${s.collectionRefs.count { it.collectionId == c.id }} 段",
            )
        }
    }
}

@Composable
fun SearchScreen(vm: AppViewModel, go: (String) -> Unit, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var kind by rememberSaveable { mutableStateOf("全部") }
    var category by rememberSaveable { mutableStateOf("") }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    val valid =
        (from.isBlank() || runCatching { LocalDate.parse(from) }.isSuccess) &&
            (to.isBlank() || runCatching { LocalDate.parse(to) }.isSuccess) &&
            (from.isBlank() || to.isBlank() || from <= to)
    val records =
        remember(s, query, kind, category, from, to) { search(s, query, kind, category, from, to) }
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Header("搜索收藏", "只搜索手动录入的文字，不读取图片内容", back)
            Field("搜索中文关键词", query, { query = it })
            Chips(listOf("全部", "回忆", "档案", "心愿"), kind) { kind = it }
            Field("分类包含（可选）", category, { category = it })
            DateField("开始日期", from, { from = it }, true)
            DateField("结束日期", to, { to = it }, true)
            if (!valid) Text("请填写有效日期范围", color = MaterialTheme.colorScheme.error)
            else Section("${records.size} 条结果")
        }
        if (valid) {
            if (records.isEmpty()) item { Empty("没有找到相关内容", "试试更短的中文关键词。") }
            items(records, key = { it.kind + it.id }) { r ->
                RecordCard(
                    r.title,
                    "${r.kind} · ${r.date}\n${r.description}",
                    {
                        go(
                            "detail/${when (r.kind) { "回忆" -> "memory"
 "档案" -> "entry"
 else -> "wish" }}/${r.id}"
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun CalendarScreen(vm: AppViewModel, go: (String) -> Unit, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today().toString()) }
    val month = YearMonth.parse(monthText)
    val selected = LocalDate.parse(selectedText)
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Header("记忆日历", "● 回忆    ◇ 纪念日", back)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusYears(1).toString() }) {
                    Text("«", style = MaterialTheme.typography.headlineSmall)
                }
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                    Icon(Icons.Outlined.ChevronLeft, "上个月")
                }
                Text("${month.year} 年 ${month.monthValue} 月", Modifier.weight(1f))
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) {
                    Icon(Icons.Outlined.ChevronRight, "下个月")
                }
                IconButton(onClick = { monthText = month.plusYears(1).toString() }) {
                    Text("»", style = MaterialTheme.typography.headlineSmall)
                }
            }
            TextButton(
                onClick = {
                    monthText = YearMonth.now().toString()
                    selectedText = today().toString()
                }
            ) {
                Text("回到今天")
            }
            Row {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth()
            repeat((cells + 6) / 7) { row ->
                Row {
                    repeat(7) { col ->
                        val day = row * 7 + col - offset + 1
                        Box(
                            Modifier.weight(1f).height(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day in 1..month.lengthOfMonth()) {
                                val date = month.atDay(day)
                                val hasMemory = s.memories.any { it.date == date.toString() }
                                val anniversary =
                                    s.anniversaries.any {
                                        if (it.annual)
                                            anniversaryInYear(
                                                LocalDate.parse(it.date),
                                                date.year,
                                            ) == date
                                        else it.date == date.toString()
                                    }
                                Surface(
                                    onClick = { selectedText = date.toString() },
                                    shape = MaterialTheme.shapes.medium,
                                    color =
                                        if (selected == date)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.background,
                                ) {
                                    Column(
                                        Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Text(day.toString())
                                        Text(
                                            (if (hasMemory) "●" else "") +
                                                if (anniversary) "◇" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Section(selectedText)
        }
        val events =
            s.anniversaries.filter {
                if (it.annual)
                    selected.year >= LocalDate.parse(it.date).year &&
                        anniversaryInYear(LocalDate.parse(it.date), selected.year) == selected
                else it.date == selectedText
            }
        items(events, key = { "anniversary" + it.id }) {
            RecordCard(it.title, "纪念日", { go("detail/anniversary/${it.id}") })
        }
        val records = s.memories.filter { it.date == selectedText }
        if (records.isEmpty()) item { Empty("这一天，还没有记录", "平凡的一天，也能成为珍贵的回忆。") }
        items(records, key = { it.id }) {
            MemoryCard(it, s, vm, { go("detail/memory/${it.id}") })
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun StatsScreen(vm: AppViewModel, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf("全部") }
    var year by rememberSaveable { mutableIntStateOf(today().year) }
    var month by rememberSaveable { mutableIntStateOf(today().monthValue) }
    val stats = statistics(s, year.takeIf { period != "全部" }, month.takeIf { period == "月度" })
    LazyColumn(contentPadding = PaddingValues(20.dp)) {
        item {
            Header("时光足迹", "每一条记录，都曾认真生活", back)
            Chips(listOf("全部", "月度", "年度"), period) { period = it }
            if (period != "全部") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { year-- }) { Text("上一年") }
                    Text("$year 年", Modifier.weight(1f))
                    TextButton(onClick = { year++ }) { Text("下一年") }
                }
            }
            if (period == "月度")
                Choices("月份", month.toString(), (1..12).map(Int::toString)) { month = it.toInt() }
            Section("${stats.memories} 段回忆", "按添加时间统计；已实现心愿按实现时间计算。")
        }
        items(
            listOf(
                "照片总数" to stats.photos,
                "聊天收藏" to stats.chats,
                "事件记录" to stats.events,
                "文字记录" to stats.texts,
                "档案信息" to stats.entries,
                "心愿总数" to stats.wishes,
                "已实现心愿" to stats.completed,
                "合集总数（全部）" to stats.collections,
            )
        ) { (label, count) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                Text(label, Modifier.weight(1f))
                Text(count.toString(), color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            Section("回忆分类分布")
            if (stats.distribution.isEmpty()) Text("暂无记录")
            stats.distribution.forEach { (category, count) ->
                Text("$category  ·  $count", Modifier.padding(vertical = 5.dp))
            }
            Section("每月记录数量", if (period == "全部") "所有年份合计" else "$year 年")
            val max = stats.monthly.maxOrNull()?.coerceAtLeast(1) ?: 1
            stats.monthly.forEachIndexed { i, count ->
                Row(
                    Modifier.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}月", Modifier.width(36.dp))
                    LinearProgressIndicator(
                        progress = { count.toFloat() / max },
                        modifier = Modifier.weight(1f).height(8.dp),
                    )
                    Text(count.toString(), Modifier.padding(start = 12.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
