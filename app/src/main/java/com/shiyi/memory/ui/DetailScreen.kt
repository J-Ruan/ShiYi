package com.shiyi.memory.ui

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyi.memory.data.*
import com.shiyi.memory.domain.*
import java.time.*

@Composable
fun DetailScreen(
    vm: AppViewModel,
    kind: String,
    id: String,
    go: (String) -> Unit,
    back: () -> Unit,
) {
    val s by vm.data.collectAsStateWithLifecycle()
    var delete by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val m = s.activeMemories.find { it.id == id }
    val e = s.entries.find { it.id == id }
    val w = s.wishes.find { it.id == id }
    val a = s.anniversaries.find { it.id == id }
    val c = s.collections.find { it.id == id }
    val title =
        when (kind) {
            "memory" -> m?.title
            "entry" -> e?.title
            "wish" -> w?.title
            "anniversary" -> a?.title
            else -> c?.title
        }
    Column(Modifier.fillMaxSize()) {
        Header(
            "珍藏详情",
            back = back,
            actions = {
                if (title != null) {
                    IconButton(onClick = { go("edit/$kind/$id") }) {
                        Icon(Icons.Outlined.Edit, "编辑")
                    }
                    IconButton(onClick = { delete = true }) {
                        Icon(Icons.Outlined.DeleteOutline, "删除")
                    }
                }
            },
        )
        if (title == null) Empty("这条记录已不存在", "可能已被删除，返回后查看其他记录。")
        else
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                @Composable
                fun detail(label: String, content: String) {
                    if (content.isNotBlank()) {
                        Section(label)
                        Text(content, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                @Composable
                fun linked(ids: List<String>) {
                    ids.distinct()
                        .mapNotNull { target -> s.activeMemories.find { it.id == target } }
                        .forEach { RecordCard(it.title, it.date, { go("detail/memory/${it.id}") }) }
                }
                when (kind) {
                    "memory" ->
                        if (m != null) {
                            Text(
                                listOf(m.type, m.date, m.time, m.location)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row {
                                TextButton(
                                    onClick = {
                                        vm.action(if (m.favorite) "已取消收藏" else "已收藏") {
                                            vm.repo.updateMemory(
                                                m.copy(
                                                    favorite = !m.favorite,
                                                    updatedAt = System.currentTimeMillis(),
                                                )
                                            )
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (m.favorite) Icons.Outlined.Star
                                        else Icons.Outlined.StarOutline,
                                        null,
                                    )
                                    Text(if (m.favorite) "已收藏" else "收藏")
                                }
                                if (s.media.any { it.memoryId == id })
                                    TextButton(
                                        onClick = {
                                            vm.action("") {
                                                val uris =
                                                    ArrayList(
                                                        s.media
                                                            .filter { it.memoryId == id }
                                                            .sortedBy { it.position }
                                                            .map {
                                                                FileProvider.getUriForFile(
                                                                    context,
                                                                    "${context.packageName}.files",
                                                                    vm.repo.images.temporary(
                                                                        it.path,
                                                                        true,
                                                                    ),
                                                                )
                                                            }
                                                    )
                                                val intent =
                                                    Intent(Intent.ACTION_SEND_MULTIPLE)
                                                        .setType("image/*")
                                                        .putParcelableArrayListExtra(
                                                            Intent.EXTRA_STREAM,
                                                            uris,
                                                        )
                                                        .addFlags(
                                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        )
                                                intent.clipData =
                                                    ClipData.newUri(
                                                            context.contentResolver,
                                                            "图片",
                                                            uris.first(),
                                                        )
                                                        .apply {
                                                            uris.drop(1).forEach {
                                                                addItem(ClipData.Item(it))
                                                            }
                                                        }
                                                kotlinx.coroutines.withContext(
                                                    kotlinx.coroutines.Dispatchers.Main
                                                ) {
                                                    vm.beginSharing()
                                                    context.startActivity(
                                                        Intent.createChooser(intent, "分享图片")
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Outlined.Share, null)
                                        Text("分享图片")
                                    }
                            }
                            s.media
                                .filter { it.memoryId == id }
                                .sortedBy { it.position }
                                .forEachIndexed { i, image ->
                                    Photo(
                                        image.path,
                                        vm,
                                        Modifier.fillMaxWidth()
                                            .height(300.dp)
                                            .padding(vertical = 6.dp)
                                            .clickable { go("viewer/$id/$i") },
                                        false,
                                    )
                                }
                            detail("回忆", m.description)
                            detail("聊天摘录", m.excerpt)
                            detail("聊天背景", m.context)
                            detail("收藏理由", m.reason)
                            detail("个人感受", m.feeling)
                            detail(
                                "分类与心情",
                                listOf(m.category, m.mood)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                            )
                            detail(
                                "标签与关键词",
                                (s.tagsFor(id) + m.keywords)
                                    .filter(String::isNotBlank)
                                    .joinToString(" · "),
                            )
                            if (s.profileRefs.any { it.memoryId == id }) Section("关联档案")
                            val entries =
                                s.profileRefs
                                    .filter { it.memoryId == id }
                                    .mapNotNull { ref -> s.entries.find { it.id == ref.profileId } }

                            entries.forEach {
                                RecordCard(it.title, it.category, { go("detail/entry/${it.id}") })
                            }
                            if (s.links.any { it.fromId == id || it.toId == id }) Section("关联回忆")
                            linked(
                                s.links
                                    .filter { it.fromId == id || it.toId == id }
                                    .map { if (it.fromId == id) it.toId else it.fromId }
                            )
                            detail(
                                "创建时间",
                                Instant.ofEpochMilli(m.createdAt)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                                    .withNano(0)
                                    .toString()
                                    .replace('T', ' '),
                            )
                            detail(
                                "修改时间",
                                Instant.ofEpochMilli(m.updatedAt)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDateTime()
                                    .withNano(0)
                                    .toString()
                                    .replace('T', ' '),
                            )
                        }
                    "entry" ->
                        if (e != null) {
                            Text(
                                "${e.category} · ${importanceNames[e.importance]}${if (e.pinned) " · 置顶" else ""}"
                            )
                            detail("详细内容", e.content)
                            detail("标签", e.tags)
                            Section("关联回忆")
                            linked(s.profileRefs.filter { it.profileId == id }.map { it.memoryId })
                            detail(
                                "添加日期",
                                Instant.ofEpochMilli(e.createdAt)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .toString(),
                            )
                            detail(
                                "修改日期",
                                Instant.ofEpochMilli(e.updatedAt)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .toString(),
                            )
                        }
                    "wish" ->
                        if (w != null) {
                            Text("${w.category} · ${wishStates[w.status]}")
                            if (w.image.isNotBlank())
                                Photo(w.image, vm, Modifier.fillMaxWidth().height(300.dp), false)
                            detail("心愿", w.description)
                            detail("计划日期", w.plannedDate)
                            detail("备注", w.notes)
                            detail(
                                "记录日期",
                                Instant.ofEpochMilli(w.createdAt)
                                    .atZone(ZoneId.systemDefault())
                                    .toLocalDate()
                                    .toString(),
                            )
                            Section("更新状态")
                            Chips(wishStates, wishStates[w.status]) { state ->
                                vm.action {
                                    val status = wishStates.indexOf(state)
                                    vm.repo.updateWish(
                                        w.copy(
                                            status = status,
                                            updatedAt = System.currentTimeMillis(),
                                            completedAt =
                                                if (status == 2)
                                                    w.completedAt ?: System.currentTimeMillis()
                                                else null,
                                        )
                                    )
                                }
                            }
                            Section("关联回忆")
                            linked(listOfNotNull(w.memoryId))
                        }
                    "anniversary" ->
                        if (a != null) {
                            Text(a.date)
                            detail(
                                "重复与提醒",
                                "${if (a.annual) "每年重复" else "不重复"} · 提前 ${a.advanceDays} 天\n下次：${nextDate(a) ?: "已过去"}",
                            )
                            detail("备注", a.notes)
                            Section("关联回忆")
                            linked(listOfNotNull(a.memoryId))
                        }
                    "collection" ->
                        if (c != null) {
                            detail("关于合集", c.description)
                            val memories =
                                s.collectionRefs
                                    .filter { it.collectionId == id }
                                    .mapNotNull { ref ->
                                        s.activeMemories.find { it.id == ref.memoryId }
                                    }
                            if (memories.isEmpty()) Empty("合集还是空的", "编辑合集，添加已有回忆。")
                            else
                                memories.forEach {
                                    MemoryCard(it, s, vm, { go("detail/memory/${it.id}") })
                                    Spacer(Modifier.height(12.dp))
                                }
                        }
                }
                Spacer(Modifier.height(30.dp))
            }
    }
    if (delete)
        ConfirmDelete(
            "删除这条记录？",
            if (kind == "memory") "移入回收站，保留 30 天。" else "删除后无法恢复，其他记录会保留。",
            dismiss = { delete = false },
        ) {
            delete = false
            if (kind == "memory") vm.deleteMemory(id, back)
            else vm.action("已删除", back) { vm.repo.delete(kind, id) }
        }
}

@Composable
fun ViewerScreen(vm: AppViewModel, memory: String, index: Int, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    val photos = s.media.filter { it.memoryId == memory }.sortedBy { it.position }
    val pager =
        rememberPagerState(
            initialPage = index.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
            pageCount = { photos.size },
        )
    Column(Modifier.fillMaxSize()) {
        Header(
            "${if (photos.isEmpty()) 0 else pager.currentPage + 1} / ${photos.size}",
            "原尺寸左右滑动 · 双指缩放 · 离开页面重置",
            back,
        )
        if (photos.isEmpty()) Empty("没有可显示的图片", "返回后重新选择。")
        else
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                var scale by remember(page) { mutableFloatStateOf(1f) }
                var x by remember(page) { mutableFloatStateOf(0f) }
                var y by remember(page) { mutableFloatStateOf(0f) }
                var size by remember { mutableStateOf(IntSize(1, 1)) }
                val transform = rememberTransformableState { zoom, pan, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    x = (x + pan.x).coerceIn(-size.width * (scale - 1), size.width * (scale - 1))
                    y = (y + pan.y).coerceIn(-size.height * (scale - 1), size.height * (scale - 1))
                }
                Photo(
                    photos[page].path,
                    vm,
                    Modifier.fillMaxSize()
                        .onSizeChanged { size = it }
                        .transformable(state = transform, canPan = { scale > 1f })
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = x,
                            translationY = y,
                        ),
                    false,
                )
            }
    }
}
