package com.shiyi.memory.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyi.memory.data.*
import com.shiyi.memory.domain.*
import java.time.*

@Composable
fun EditorScreen(vm: AppViewModel, kind: String, id: String, type: String, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val m = s.memories.find { it.id == id }
    val e = s.entries.find { it.id == id }
    val w = s.wishes.find { it.id == id }
    val a = s.anniversaries.find { it.id == id }
    val c = s.collections.find { it.id == id }
    val p = s.partner.firstOrNull() ?: PartnerProfile()
    val recordId = rememberSaveable { if (id == "new") newId() else id }
    val stateSaver =
        mapSaver<androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>>(
            save = { it.toMap() },
            restore = { it.entries.map { x -> x.key to (x.value as String) }.toMutableStateMap() },
        )
    val form =
        rememberSaveable(kind, id, saver = stateSaver) {
            mutableStateMapOf<String, String>().apply {
                when (kind) {
                    "memory" ->
                        putAll(
                            mapOf(
                                "title" to m?.title.orEmpty(),
                                "description" to m?.description.orEmpty(),
                                "type" to (m?.type ?: if (type == "chat") "聊天收藏" else "瞬间记录"),
                                "date" to (m?.date ?: today().toString()),
                                "time" to m?.time.orEmpty(),
                                "location" to m?.location.orEmpty(),
                                "mood" to m?.mood.orEmpty(),
                                "category" to m?.category.orEmpty(),
                                "keywords" to m?.keywords.orEmpty(),
                                "excerpt" to m?.excerpt.orEmpty(),
                                "context" to m?.context.orEmpty(),
                                "reason" to m?.reason.orEmpty(),
                                "feeling" to m?.feeling.orEmpty(),
                                "favorite" to (m?.favorite ?: false).toString(),
                                "pinned" to (m?.pinned ?: false).toString(),
                                "tags" to s.tagsFor(id).joinToString(","),
                                "images" to
                                    s.media
                                        .filter { it.memoryId == id }
                                        .sortedBy { it.position }
                                        .joinToString(",") { it.path },
                                "cover" to m?.coverPath.orEmpty(),
                                "profiles" to
                                    s.profileRefs
                                        .filter { it.memoryId == id }
                                        .joinToString(",") { it.profileId },
                                "related" to
                                    s.links
                                        .filter { it.fromId == id || it.toId == id }
                                        .joinToString(",") {
                                            if (it.fromId == id) it.toId else it.fromId
                                        },
                            )
                        )
                    "entry" ->
                        putAll(
                            mapOf(
                                "title" to e?.title.orEmpty(),
                                "content" to e?.content.orEmpty(),
                                "category" to (e?.category ?: profileCategories.first()),
                                "importance" to (e?.importance ?: 0).toString(),
                                "tags" to e?.tags.orEmpty(),
                                "pinned" to (e?.pinned ?: false).toString(),
                                "related" to
                                    s.profileRefs
                                        .filter { it.profileId == id }
                                        .joinToString(",") { it.memoryId },
                            )
                        )
                    "wish" ->
                        putAll(
                            mapOf(
                                "title" to w?.title.orEmpty(),
                                "description" to w?.description.orEmpty(),
                                "category" to (w?.category ?: wishCategories.first()),
                                "images" to w?.image.orEmpty(),
                                "plannedDate" to w?.plannedDate.orEmpty(),
                                "status" to (w?.status ?: 0).toString(),
                                "notes" to w?.notes.orEmpty(),
                                "memory" to w?.memoryId.orEmpty(),
                            )
                        )
                    "anniversary" ->
                        putAll(
                            mapOf(
                                "title" to a?.title.orEmpty(),
                                "date" to (a?.date ?: today().toString()),
                                "annual" to (a?.annual ?: true).toString(),
                                "advance" to (a?.advanceDays ?: 0).toString(),
                                "notes" to a?.notes.orEmpty(),
                                "memory" to a?.memoryId.orEmpty(),
                            )
                        )
                    "collection" ->
                        putAll(
                            mapOf(
                                "title" to c?.title.orEmpty(),
                                "description" to c?.description.orEmpty(),
                                "related" to
                                    s.collectionRefs
                                        .filter { it.collectionId == id }
                                        .joinToString(",") { it.memoryId },
                            )
                        )
                    "partner" ->
                        putAll(
                            mapOf(
                                "title" to p.nickname,
                                "myNickname" to p.myNickname,
                                "birthday" to p.birthday,
                                "metDate" to p.metDate,
                                "bio" to p.bio,
                                "images" to p.avatar,
                            )
                        )
                }
            }
        }
    fun value(key: String) = form[key].orEmpty()
    fun ids(key: String) = value(key).split(',').filter(String::isNotBlank).toSet()
    val allMemories = s.memories.filter { it.id != id }.map { it.id to it.title }
    @Composable
    fun f(label: String, key: String, multi: Boolean = false) =
        Field(label, value(key), { form[key] = it }, multi)
    @Composable
    fun date(label: String, key: String, optional: Boolean = false) =
        DateField(label, value(key), { form[key] = it }, optional)
    @Composable
    fun related() =
        MultiSelect("关联回忆", allMemories, ids("related")) { form["related"] = it.joinToString(",") }
    fun save() {
        vm.action(onSuccess = back) {
            require(value("title").trim().isNotBlank()) { "请填写标题或昵称" }
            when (kind) {
                "memory" -> {
                    LocalDate.parse(value("date"))
                    if (value("time").isNotBlank()) LocalTime.parse(value("time"))
                    vm.repo.saveMemory(
                        Memory(
                            recordId,
                            value("type"),
                            value("title").trim(),
                            value("description"),
                            value("date"),
                            value("time"),
                            value("location"),
                            value("mood"),
                            value("category"),
                            value("keywords"),
                            value("excerpt"),
                            value("context"),
                            value("reason"),
                            value("feeling"),
                            value("favorite").toBoolean(),
                            value("pinned").toBoolean(),
                            value("cover"),
                            m?.createdAt ?: System.currentTimeMillis(),
                        ),
                        value("images").split(',').filter(String::isNotBlank),
                        value("tags").split(',', '，', '、').filter(String::isNotBlank),
                        ids("profiles"),
                        ids("related"),
                    )
                }
                "entry" ->
                    vm.repo.saveEntry(
                        ProfileEntry(
                            recordId,
                            value("title").trim(),
                            value("content"),
                            value("category"),
                            value("importance").toInt(),
                            value("tags"),
                            value("pinned").toBoolean(),
                            e?.createdAt ?: System.currentTimeMillis(),
                        ),
                        ids("related"),
                    )
                "wish" -> {
                    if (value("plannedDate").isNotBlank()) LocalDate.parse(value("plannedDate"))
                    val status = value("status").toInt()
                    vm.repo.dao.put(
                        Wish(
                            recordId,
                            value("title").trim(),
                            value("description"),
                            value("category"),
                            value("images"),
                            value("plannedDate"),
                            status,
                            value("notes"),
                            value("memory").ifBlank { null },
                            w?.createdAt ?: System.currentTimeMillis(),
                            System.currentTimeMillis(),
                            if (status == 2) w?.completedAt ?: System.currentTimeMillis() else null,
                        )
                    )
                }
                "anniversary" -> {
                    LocalDate.parse(value("date"))
                    val days = value("advance").toIntOrNull()
                    require(days != null && days in 0..365) { "提前天数需为 0–365" }
                    vm.repo.dao.put(
                        Anniversary(
                            recordId,
                            value("title").trim(),
                            value("date"),
                            value("annual").toBoolean(),
                            days,
                            value("notes"),
                            value("memory").ifBlank { null },
                        )
                    )
                    if (vm.prefs.read().notifications)
                        com.shiyi.memory.platform.Reminders.schedule(vm.app)
                }
                "collection" ->
                    vm.repo.saveCollection(
                        MemoryCollection(recordId, value("title").trim(), value("description")),
                        ids("related"),
                    )
                "partner" -> {
                    listOf("birthday", "metDate").forEach {
                        if (value(it).isNotBlank()) LocalDate.parse(value(it))
                    }
                    require(
                        value("metDate").isBlank() || LocalDate.parse(value("metDate")) <= today()
                    ) {
                        "相识日期不能晚于今天"
                    }
                    vm.repo.dao.put(
                        p.copy(
                            nickname = value("title").trim(),
                            myNickname = value("myNickname").ifBlank { "我" },
                            birthday = value("birthday"),
                            metDate = value("metDate"),
                            bio = value("bio"),
                            avatar = value("images"),
                        )
                    )
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Header(
            if (kind == "partner") "恋人档案"
            else
                (if (id == "new") "新增" else "编辑") +
                    when (kind) {
                        "memory" -> "回忆"
                        "entry" -> "档案细节"
                        "wish" -> "心愿"
                        "anniversary" -> "纪念日"
                        else -> "合集"
                    },
            back = back,
            actions = { TextButton(onClick = ::save, enabled = !busy) { Text("保存") } },
        )
        Column(
            Modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            f(if (kind == "partner") "TA 的昵称" else "标题 *", "title")
            when (kind) {
                "memory" -> {
                    Choices("回忆类型", value("type"), memoryTypes) { form["type"] = it }
                    MediaEditor(
                        vm,
                        value("images").split(',').filter(String::isNotBlank),
                        value("cover"),
                        { form["images"] = it.joinToString(",") },
                        { form["cover"] = it },
                    )
                    f("回忆描述", "description", true)
                    date("发生日期", "date")
                    f("发生时间（可选，HH:mm）", "time")
                    f("地点（可选）", "location")
                    f("心情（可选）", "mood")
                    if (value("type") == "聊天收藏") {
                        Section("手动收藏聊天", "截图只作为原图保存，不读取图片文字。")
                        f("聊天摘录", "excerpt", true)
                        f("聊天背景", "context", true)
                        f("收藏理由", "reason", true)
                        f("个人感受", "feeling", true)
                    }
                    f("自定义分类", "category")
                    Choices(
                        "选择已有分类",
                        value("category"),
                        listOf("") +
                            s.categories.filter { it.scope == "回忆" }.map { it.name } +
                            s.memories.map { it.category }.filter(String::isNotBlank),
                    ) {
                        form["category"] = it
                    }
                    f("标签（用逗号分隔）", "tags")
                    f("自定义关键词", "keywords")
                    Toggle("收藏", value("favorite").toBoolean()) { form["favorite"] = it.toString() }
                    Toggle("置顶", value("pinned").toBoolean()) { form["pinned"] = it.toString() }
                    MultiSelect("关联 TA 的档案", s.entries.map { it.id to it.title }, ids("profiles")) {
                        form["profiles"] = it.joinToString(",")
                    }
                    related()
                }
                "entry" -> {
                    f("详细内容", "content", true)
                    Choices(
                        "分类",
                        value("category"),
                        profileCategories + s.categories.filter { it.scope == "档案" }.map { it.name },
                    ) {
                        form["category"] = it
                    }
                    Choices("重要程度", importanceNames[value("importance").toInt()], importanceNames) {
                        form["importance"] = importanceNames.indexOf(it).toString()
                    }
                    f("标签", "tags")
                    Toggle("置顶", value("pinned").toBoolean()) { form["pinned"] = it.toString() }
                    related()
                }
                "wish" -> {
                    f("详细描述", "description", true)
                    Choices("分类", value("category"), wishCategories) { form["category"] = it }
                    MediaEditor(
                        vm,
                        listOf(value("images")).filter(String::isNotBlank),
                        "",
                        { form["images"] = it.firstOrNull().orEmpty() },
                        {},
                        single = true,
                    )
                    date("计划日期", "plannedDate", true)
                    Choices("状态", wishStates[value("status").toInt()], wishStates) {
                        form["status"] = wishStates.indexOf(it).toString()
                    }
                    f("备注", "notes", true)
                    Choices(
                        "关联回忆",
                        s.memories.find { it.id == value("memory") }?.title.orEmpty(),
                        listOf("") + s.memories.map { it.title + " · " + it.id.take(8) },
                    ) { choice ->
                        form["memory"] =
                            s.memories
                                .find { it.title + " · " + it.id.take(8) == choice }
                                ?.id
                                .orEmpty()
                    }
                }
                "anniversary" -> {
                    Choices("纪念日名称预设", value("title"), listOf("相识纪念日", "第一次见面", "生日", "其他特殊日子")) {
                        form["title"] = it
                    }
                    date("纪念日期", "date")
                    Toggle("每年重复", value("annual").toBoolean()) { form["annual"] = it.toString() }
                    f("提前提醒天数（0–365）", "advance")
                    f("备注", "notes", true)
                    Text(
                        "提醒需在设置中主动开启。2 月 29 日在平年按 2 月 28 日计算。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Choices(
                        "关联回忆",
                        s.memories.find { it.id == value("memory") }?.title.orEmpty(),
                        listOf("") + s.memories.map { it.title + " · " + it.id.take(8) },
                    ) { choice ->
                        form["memory"] =
                            s.memories
                                .find { it.title + " · " + it.id.take(8) == choice }
                                ?.id
                                .orEmpty()
                    }
                }
                "collection" -> {
                    f("合集描述", "description", true)
                    related()
                    Text("合集引用已有回忆，不重复复制图片。", style = MaterialTheme.typography.bodySmall)
                }
                "partner" -> {
                    f("我的昵称", "myNickname")
                    MediaEditor(
                        vm,
                        listOf(value("images")).filter(String::isNotBlank),
                        "",
                        { form["images"] = it.firstOrNull().orEmpty() },
                        {},
                        true,
                    )
                    date("TA 的生日", "birthday", true)
                    date("相识日期", "metDate", true)
                    f("简短介绍", "bio", true)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = ::save, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text("保存到拾忆")
            }
        }
    }
}

@Composable
private fun MediaEditor(
    vm: AppViewModel,
    paths: List<String>,
    cover: String,
    change: (List<String>) -> Unit,
    setCover: (String) -> Unit,
    single: Boolean = false,
) {
    var preview by remember { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) {
            uris ->
            if (uris.isNotEmpty())
                vm.importImages(if (single) uris.take(1) else uris) { imported ->
                    change(if (single) imported else (paths + imported).distinct())
                }
        }
    Section(if (single) "图片（可选）" else "原始图片 · ${paths.size}", "复制原始文件，不默认压缩")
    OutlinedButton(
        onClick = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Outlined.AddPhotoAlternate, null)
        Text(if (single) "选择图片" else "多选图片")
    }
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        itemsIndexed(paths, key = { _, p -> p }) { i, path ->
            Column(Modifier.width(160.dp)) {
                Photo(path, vm, Modifier.size(160.dp).clickable { preview = path })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (!single) {
                        IconButton(
                            onClick = {
                                change(
                                    paths.toMutableList().apply {
                                        if (i > 0) {
                                            removeAt(i)
                                            add(i - 1, path)
                                        }
                                    }
                                )
                            },
                            enabled = i > 0,
                        ) {
                            Icon(Icons.Outlined.ArrowBack, "向前移动")
                        }
                        IconButton(
                            onClick = {
                                change(
                                    paths.toMutableList().apply {
                                        if (i < paths.lastIndex) {
                                            removeAt(i)
                                            add(i + 1, path)
                                        }
                                    }
                                )
                            },
                            enabled = i < paths.lastIndex,
                        ) {
                            Icon(Icons.Outlined.ArrowForward, "向后移动")
                        }
                    }
                    IconButton(onClick = { remove = path }) {
                        Icon(Icons.Outlined.DeleteOutline, "删除此图")
                    }
                }
                if (!single)
                    TextButton(onClick = { setCover(path) }) {
                        Text(if (cover == path || (cover.isBlank() && i == 0)) "✓ 当前封面" else "设为封面")
                    }
            }
        }
    }
    if (preview != null)
        Dialog(onDismissRequest = { preview = null }) {
            Column {
                Photo(preview!!, vm, Modifier.fillMaxWidth().height(500.dp), false)
                TextButton(onClick = { preview = null }) { Text("关闭预览") }
            }
        }
    if (remove != null)
        ConfirmDelete("移除此图片？", "保存后从此记录移除，其他记录不受影响。", { remove = null }) {
            val path = remove
            change(paths.filter { it != path })
            if (path == cover) setCover("")
            remove = null
        }
}
