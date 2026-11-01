package com.shiyi.memory.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiyi.memory.data.*
import java.time.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun TrashScreen(vm: AppViewModel, back: () -> Unit) {
    val s by vm.data.collectAsStateWithLifecycle()
    val records = s.memories.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    var permanent by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(Space.page)) {
        item {
            Header(
                "回收站",
                back = back,
                inset = false,
                actions = {
                    TextButton(onClick = { permanent = "all" }, enabled = records.isNotEmpty()) {
                        Text("清空")
                    }
                },
            )
        }
        if (records.isEmpty()) item { Empty("回收站为空") }
        items(records, key = { it.id }) { memory ->
            Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(memory.title, style = MaterialTheme.typography.titleMedium)
                val days =
                    ((memory.deletedAt!! + 30L * 86400000 - System.currentTimeMillis() + 86399999) /
                            86400000)
                        .coerceIn(0, 30)
                Text("剩余 $days 天", style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(
                        onClick = { vm.action("已恢复") { vm.repo.restoreMemory(memory.id) } }
                    ) {
                        Text("恢复")
                    }
                    TextButton(onClick = { permanent = memory.id }) {
                        Text("永久删除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (permanent != null)
        ConfirmDelete(
            if (permanent == "all") "清空回收站？" else "永久删除回忆？",
            "删除后无法恢复。其他记录引用的图片将保留。",
            { permanent = null },
        ) {
            val id = permanent
            permanent = null
            vm.action("已永久删除") {
                if (id == "all") vm.repo.emptyTrash() else vm.repo.permanentDelete(id!!)
            }
        }
}

@Composable
fun StorageScreen(vm: AppViewModel, back: () -> Unit) {
    var report by remember { mutableStateOf<StorageReport?>(null) }
    var preview by remember { mutableStateOf(false) }
    var revision by remember { mutableIntStateOf(0) }
    val settings by vm.settings.collectAsStateWithLifecycle()
    LaunchedEffect(revision) {
        try {
            report = withContext(Dispatchers.IO) { vm.repo.storageReport() }
        } catch (e: Exception) {
            vm.error.value = "无法读取存储空间，请重试"
        }
    }
    fun size(bytes: Long) = "%.2f MB".format(bytes / 1048576.0)
    LazyColumn(contentPadding = PaddingValues(Space.page)) {
        item { Header("存储空间", back = back, inset = false) }
        val r = report
        if (r == null) item { CircularProgressIndicator() }
        else {
            items(
                listOf(
                    "图片数量" to "${r.count}",
                    "图片空间" to size(r.total),
                    "回忆图片" to size(r.memories),
                    "档案图片" to size(r.profile),
                    "心愿图片" to size(r.wishes),
                    "未使用图片" to "${r.unreferencedCount}",
                    "可释放空间" to size(r.unused.sumOf { it.bytes }),
                    "最近备份" to
                        if (settings.lastBackup == 0L) "暂无"
                        else
                            Instant.ofEpochMilli(settings.lastBackup)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                                .toString(),
                )
            ) { (label, value) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                    Text(label, Modifier.weight(1f))
                    Text(value)
                }
            }
            item {
                Text(
                    "清理保留回收站、分享草稿和最近 24 小时导入的图片。分类空间可能包含共同引用的文件。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = { preview = true },
                    enabled = r.unused.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text("预览清理")
                }
            }
        }
    }
    if (preview && report != null)
        AlertDialog(
            onDismissRequest = { preview = false },
            title = { Text("清理 ${report!!.unused.size} 个文件？") },
            text = {
                LazyColumn(Modifier.heightIn(max = 300.dp)) {
                    item { Text("预计释放 ${size(report!!.unused.sumOf {it.bytes})}") }
                    items(report!!.unused) {
                        Text(
                            "${it.name} · ${size(it.bytes)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val files = report!!.unused
                        preview = false
                        vm.action("清理完成", { revision++ }) { vm.repo.cleanReviewed(files) }
                    }
                ) {
                    Text("确认清理")
                }
            },
            dismissButton = { TextButton(onClick = { preview = false }) { Text("取消") } },
        )
}
