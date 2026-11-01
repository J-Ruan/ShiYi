package com.shiyi.memory.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.staggeredgrid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyi.memory.data.*
import com.shiyi.memory.domain.*
import java.time.*

@Composable
fun HomeScreen(vm: AppViewModel, go: (String) -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    var randomId by rememberSaveable { mutableStateOf("") }
    val p = s.partner.firstOrNull() ?: PartnerProfile()
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 90.dp)) {
        item {
            Header(
                "拾忆",
                "MEMORY / 把时光轻轻收藏",
                actions = {
                    IconButton(onClick = { go("search") }) { Icon(Icons.Outlined.Search, "全局搜索") }
                },
            )
        }
        item {
            Row(
                Modifier.padding(20.dp).fillMaxWidth().clickable { go("edit/partner/1") },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Photo(p.avatar, vm, Modifier.size(72.dp))
                Column(Modifier.padding(start = 18.dp)) {
                    Text(
                        "${p.myNickname} 与 ${p.nickname}",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        if (p.metDate.isBlank()) "编辑档案"
                        else "相识 ${daysTogether(p.metDate)} 天 · ${p.metDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Section(
                    "最近回忆",
                    "每个瞬间，都有自己的光",
                    action = { TextButton(onClick = { go("memories") }) { Text("查看全部") } },
                )
            }
        }
        item {
            if (s.activeMemories.isEmpty()) Empty()
            else
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(
                        s.activeMemories.sortedByDescending { it.createdAt }.take(12),
                        key = { it.id },
                    ) { m ->
                        MemoryCard(
                            m,
                            s,
                            vm,
                            { go("detail/memory/${m.id}") },
                            Modifier.width(260.dp),
                        )
                    }
                }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Section("今日回忆", "往年今天，曾经发生的美好")
                val records = onThisDay(s, today())
                if (records.isEmpty()) Empty("今天，还没有旧日回声", "新的一天，也值得被记住。")
                else
                    records.forEach { m ->
                        RecordCard(m.title, m.date, { go("detail/memory/${m.id}") })
                    }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Section(
                    "偶然想起",
                    action = {
                        TextButton(
                            onClick = {
                                randomId =
                                    s.activeMemories
                                        .filter { it.id != randomId }
                                        .ifEmpty { s.activeMemories }
                                        .randomOrNull()
                                        ?.id
                                        .orEmpty()
                            },
                            enabled = s.activeMemories.isNotEmpty(),
                        ) {
                            Text("随机一段")
                        }
                    },
                )
                val m = s.activeMemories.find { it.id == randomId }
                if (m != null) MemoryCard(m, s, vm, { go("detail/memory/${m.id}") })
                else
                    Text(
                        if (s.activeMemories.isEmpty()) "先保存一段回忆，让时光有迹可循。" else "轻触「随机一段」，重逢一段旧时光。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp)) {
                Section("TA 的最近动态")
                if (s.entries.isEmpty()) Empty("慢慢了解，慢慢记录", "手动记下 TA 的喜好与相处细节。")
                else
                    s.entries
                        .sortedByDescending { it.createdAt }
                        .take(3)
                        .forEach {
                            RecordCard(it.title, it.category, { go("detail/entry/${it.id}") })
                        }
                Section("即将到来的纪念日")
                val events =
                    s.anniversaries
                        .mapNotNull { a -> nextDate(a)?.let { a to it } }
                        .filter { it.second <= today().plusDays(90) }
                        .sortedBy { it.second }
                if (events.isEmpty())
                    Text("未来 90 天暂无纪念日", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else
                    events.take(5).forEach { (a, d) ->
                        RecordCard(a.title, d.toString(), { go("detail/anniversary/${a.id}") })
                    }
                Section("快捷记录")
                listOf(
                        "添加回忆" to "edit/memory/new",
                        "收藏聊天" to "edit/memory/new?type=chat",
                        "记录 TA 的细节" to "edit/entry/new",
                        "添加心愿" to "edit/wish/new",
                    )
                    .forEach { (label, path) ->
                        OutlinedButton(onClick = { go(path) }, Modifier.fillMaxWidth()) {
                            Text(label)
                        }
                    }
            }
        }
    }
}

@Composable
fun MemoriesScreen(vm: AppViewModel, go: (String) -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf("瀑布流") }
    var type by rememberSaveable { mutableStateOf("全部") }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var tag by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("发生日期") }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    var special by rememberSaveable { mutableStateOf("全部") }
    val records =
        s.activeMemories
            .filter {
                (from.isBlank() || it.date >= from) &&
                    (to.isBlank() || it.date <= to) &&
                    (type == "全部" || it.type == type) &&
                    (category.isBlank() || it.category == category) &&
                    (tag.isBlank() || tag in s.tagsFor(it.id)) &&
                    (special != "收藏" || it.favorite) &&
                    (special != "置顶" || it.pinned) &&
                    (query.isBlank() || (it.title + it.description).contains(query, true))
            }
            .let {
                if (sort == "最近添加") it.sortedByDescending { it.createdAt }
                else if (sort == "最早添加") it.sortedBy { it.createdAt }
                else
                    it.sortedWith(
                        compareByDescending<Memory> { it.date }.thenByDescending { it.time }
                    )
            }
            .sortedByDescending { it.pinned }
    Column(Modifier.fillMaxSize()) {
        Header(
            "回忆",
            "${s.activeMemories.size} 段时光",
            actions = {
                IconButton(onClick = { go("collections") }) {
                    Icon(Icons.Outlined.CollectionsBookmark, "回忆合集")
                }
                IconButton(onClick = { go("calendar") }) {
                    Icon(Icons.Outlined.CalendarMonth, "日历")
                }
            },
        )
        Column(Modifier.padding(horizontal = 20.dp)) {
            Field("搜索标题和描述", query, { query = it })
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起筛选" else "筛选与视图")
            }
            if (expanded) {
                Chips(listOf("瀑布流", "网格", "时间轴"), mode) { mode = it }
                Chips(listOf("全部") + memoryTypes, type) { type = it }
                Row {
                    Box(Modifier.weight(1f)) {
                        Choices(
                            "分类",
                            category,
                            listOf("") +
                                s.activeMemories.map { it.category }.filter(String::isNotBlank),
                            { category = it },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        Choices("标签", tag, listOf("") + s.tags.map { it.name }, { tag = it })
                    }
                }
                Row {
                    Box(Modifier.weight(1f)) {
                        Choices("排序", sort, listOf("发生日期", "最近添加", "最早添加"), { sort = it })
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        Choices("筛选", special, listOf("全部", "收藏", "置顶"), { special = it })
                    }
                }
            }
            if (expanded) {
                DateField("开始日期", from, { from = it }, true)
                DateField("结束日期", to, { to = it }, true)
            }
        }
        if (records.isEmpty()) Empty("还没有符合条件的回忆", "试试其他筛选，或添加一段新回忆。")
        else
            when (mode) {
                "瀑布流" ->
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Adaptive(170.dp),
                        contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 90.dp),
                        verticalItemSpacing = 12.dp,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(records, key = { it.id }) {
                            MemoryCard(it, s, vm, { go("detail/memory/${it.id}") })
                        }
                    }
                "网格" ->
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(170.dp),
                        contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 90.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(records, key = { it.id }) {
                            MemoryCard(
                                it,
                                s,
                                vm,
                                { go("detail/memory/${it.id}") },
                                Modifier.height(310.dp),
                            )
                        }
                    }
                else ->
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp, 10.dp, 20.dp, 90.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        records
                            .groupBy { it.date.take(7) }
                            .forEach { (month, list) ->
                                item {
                                    Text(
                                        "${month.take(4)} 年 ${month.takeLast(2)} 月",
                                        Modifier.padding(vertical = 10.dp),
                                        style = MaterialTheme.typography.titleLarge,
                                    )
                                }
                                items(list, key = { it.id }) { m ->
                                    Row {
                                        Text(
                                            m.date.takeLast(2),
                                            Modifier.padding(end = 16.dp, top = 16.dp),
                                            style = MaterialTheme.typography.headlineSmall,
                                        )
                                        Box(Modifier.weight(1f)) {
                                            MemoryCard(m, s, vm, { go("detail/memory/${m.id}") })
                                        }
                                    }
                                }
                            }
                    }
            }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel, go: (String) -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var importance by rememberSaveable { mutableStateOf("全部") }
    val p = s.partner.firstOrNull() ?: PartnerProfile()
    LazyColumn(contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 90.dp)) {
        item {
            Header(
                "TA 的档案",
                "关于 TA，那些值得记住的小事",
                actions = {
                    IconButton(onClick = { go("edit/partner/1") }) {
                        Icon(Icons.Outlined.Edit, "编辑恋人档案")
                    }
                },
            )
        }
        item {
            Row(
                Modifier.padding(vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Photo(p.avatar, vm, Modifier.size(88.dp))
                Column(Modifier.padding(start = 16.dp)) {
                    Text(p.nickname, style = MaterialTheme.typography.headlineMedium)
                    if (p.bio.isNotBlank()) Text(p.bio)
                    if (p.birthday.isNotBlank())
                        Text("生日 ${p.birthday}", style = MaterialTheme.typography.bodySmall)
                    if (s.partner.isNotEmpty())
                        Text(
                            "建立于 ${Instant.ofEpochMilli(p.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()} · ${s.entries.size} 条记录",
                            style = MaterialTheme.typography.labelSmall,
                        )
                }
            }
        }
        item {
            RecordCard(
                "心愿清单",
                "",
                { go("wishes") },
                "${s.wishes.count { it.status == 2 }}/${s.wishes.size}",
            )
            RecordCard("纪念日", "", { go("anniversaries") })
            Field("搜索档案与标签", query, { query = it })
            Choices(
                "分类",
                category,
                listOf("全部") +
                    profileCategories +
                    s.categories.filter { it.scope == "档案" }.map { it.name },
                { category = it },
            )
            Choices("重要程度", importance, listOf("全部") + importanceNames, { importance = it })
            Section(
                "细节收藏",
                action = { TextButton(onClick = { go("edit/entry/new") }) { Text("新增") } },
            )
        }
        val entries =
            s.entries.filter {
                (category == "全部" || it.category == category) &&
                    (importance == "全部" || importanceNames[it.importance] == importance) &&
                    (query.isBlank() || (it.title + it.content + it.tags).contains(query, true))
            }
        if (entries.isEmpty()) item { Empty("用心记下 TA 的每一面", "六大分类，也允许创建自己的分类。") }
        items(entries, key = { it.id }) {
            RecordCard(
                (if (it.pinned) "置顶 · " else "") + it.title,
                it.category,
                { go("detail/entry/${it.id}") },
            )
        }
    }
}

@Composable
fun MeScreen(vm: AppViewModel, go: (String) -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 90.dp)) {
        item {
            Header("我的", "自己的收藏馆，自己的节奏")
            Section(
                "收藏了 ${s.activeMemories.size} 段时光",
                "${s.media.size} 张原图 · ${s.entries.size} 条 TA 的细节",
            )
            Spacer(Modifier.height(18.dp))
        }
        items(
            listOf(
                Triple("回收站", "", "trash"),
                Triple("存储空间", "", "storage"),
                Triple("数据统计与回顾", "月度与年度的记录足迹", "stats"),
                Triple("PDF 回忆册", "在本地排版，保存或分享", "pdf"),
                Triple("备份与恢复", "密码加密，包含全部原始图片", "backup"),
                Triple("分类管理", "为回忆与档案创建自定义分类", "categories"),
                Triple("设置与隐私", "主题、应用锁、提醒与数据管理", "settings"),
            )
        ) { (title, description, path) ->
            RecordCard(title, "", { go(path) })
        }
        item {
            Section("拾忆 · Memory", "1.1.0")
            Text(
                "完全离线 · 本地加密",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
