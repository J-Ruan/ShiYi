package com.shiyi.memory.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shiyi.memory.data.*
import java.time.LocalDate

@Composable
fun Header(
    title: String,
    subtitle: String = "",
    back: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.Outlined.ArrowBack, "返回") }
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle.isNotBlank())
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        actions()
    }
}

@Composable
fun Section(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank())
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }
        action?.invoke()
    }
}

@Composable
fun Empty(title: String = "这里，等待一段回忆", detail: String = "用右下角的「＋」，留下值得珍藏的瞬间。") {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Outlined.AutoAwesome,
            null,
            Modifier.size(30.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            detail,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun Photo(path: String, vm: AppViewModel, modifier: Modifier = Modifier, crop: Boolean = true) {
    Box(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Image, "图片占位", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        if (path.isNotBlank())
            AsyncImage(
                model = vm.repo.file(path),
                contentDescription = "收藏的原始图片",
                contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
    }
}

@Composable
fun MemoryCard(
    memory: Memory,
    snapshot: Snapshot,
    vm: AppViewModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
    ) {
        if (memory.coverPath.isNotBlank())
            Photo(memory.coverPath, vm, Modifier.fillMaxWidth().height(180.dp))
        Column(Modifier.padding(16.dp)) {
            Text(
                "${memory.type}  ·  ${memory.date}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                (if (memory.pinned) "置顶 · " else "") +
                    memory.title +
                    if (memory.favorite) "  ☆" else "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )
            if (memory.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    memory.description,
                    maxLines = 3,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val tags = snapshot.tagsFor(memory.id)
            if (tags.isNotEmpty())
                Text(
                    tags.take(4).joinToString("  ") { "#$it" },
                    Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
        }
    }
}

@Composable
fun RecordCard(title: String, detail: String, onClick: () -> Unit, trailing: String = "") {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (detail.isNotBlank())
                    Text(
                        detail,
                        Modifier.padding(top = 5.dp),
                        maxLines = 3,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            if (trailing.isNotBlank())
                Text(
                    trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
fun Choices(label: String, value: String, values: List<String>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            "$label：${value.ifBlank { "未选择" }}",
            Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(Icons.Outlined.ExpandMore, null)
    }
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    values.distinct().forEach { item ->
                        TextButton(
                            onClick = {
                                onChange(item)
                                open = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(item.ifBlank { "未设置" })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } },
        )
}

@Composable
fun Chips(values: List<String>, value: String, change: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(values) { item ->
            FilterChip(selected = item == value, onClick = { change(item) }, label = { Text(item) })
        }
    }
}

@Composable
fun Field(label: String, value: String, change: (String) -> Unit, multiline: Boolean = false) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    OutlinedTextField(
        value,
        change,
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        minLines = if (multiline) 3 else 1,
        maxLines = if (multiline) 10 else 1,
        singleLine = !multiline,
        keyboardOptions =
            androidx.compose.foundation.text.KeyboardOptions(
                imeAction =
                    if (multiline) androidx.compose.ui.text.input.ImeAction.Default
                    else androidx.compose.ui.text.input.ImeAction.Done
            ),
        keyboardActions =
            androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus() }),
    )
}

@Composable
fun DateField(label: String, value: String, change: (String) -> Unit, optional: Boolean = false) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value,
            change,
            Modifier.weight(1f).padding(vertical = 5.dp),
            label = { Text("$label · YYYY-MM-DD${if (optional) "（可选）" else ""}") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )
        IconButton(
            onClick = {
                val d = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
                DatePickerDialog(
                        context,
                        { _, y, m, day -> change(LocalDate.of(y, m + 1, day).toString()) },
                        d.year,
                        d.monthValue - 1,
                        d.dayOfMonth,
                    )
                    .show()
            }
        ) {
            Icon(Icons.Outlined.CalendarMonth, "选择$label")
        }
    }
}

@Composable
fun Toggle(label: String, value: Boolean, change: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f))
        Switch(value, change)
    }
}

@Composable
fun MultiSelect(
    label: String,
    items: List<Pair<String, String>>,
    selected: Set<String>,
    change: (Set<String>) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text("$label · 已选 ${selected.size}")
    }
    if (open)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = {
                if (items.isEmpty()) Text("暂无内容，请先创建记录。")
                else
                    Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                        items.forEach { (id, title) ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    change(if (id in selected) selected - id else selected + id)
                                },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    id in selected,
                                    { change(if (it) selected + id else selected - id) },
                                )
                                Text(title, Modifier.weight(1f))
                            }
                        }
                    }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("完成") } },
        )
}

@Composable
fun ConfirmDelete(
    title: String,
    detail: String = "此操作无法撤销，关联关系会移除，其他原始记录会保留。",
    dismiss: () -> Unit,
    confirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title) },
        text = { Text(detail) },
        confirmButton = {
            TextButton(onClick = confirm) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } },
    )
}
